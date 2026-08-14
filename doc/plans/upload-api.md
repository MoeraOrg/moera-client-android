# Native Android Media and Attachment Upload Plan

## Goal

Move image, video, and attached-document bytes selected through Android out of the WebView upload path. Android should
retain the selected `content://` URI, expose only an opaque local ID plus metadata and an optional small thumbnail to
JavaScript, and stream the source directly to the existing Moera chunked media upload API.

The first release should use this path for every image, video, and document selected through the API version 3 Android
picker, including PDF, office, archive, and arbitrary binary attachments. Route every picker result natively regardless
of its type or size; this keeps one representation for an Android selection and automatically covers large files
without coupling selection to a changeable size threshold. Browser uploads and sources already materialized inside the
WebView, such as clipboard, drag-and-drop, and URL uploads, keep their existing web transport.

The current web client uploads documents immediately after selection and uploads visual media after its selection
dialog is confirmed, rather than waiting for the user to submit the post. Preserve that behavior in the first
implementation. In particular, do not delay native upload until an initially empty composer has a server-side draft:
such a draft is not created until the user enters text or the first completed media is added. Start the native transfer
with a nullable draft association instead. Deferring attachment upload until posting submission is a separate product
change.

A native upload may therefore begin with `draftId = null`. Once the post/comment composer obtains a server draft ID,
the web client assigns that ID to every still-unassigned native upload for the current home. An assigned upload remains
owned by that draft. After a restart, an unassigned upload may be recovered into any composer; an assigned upload may be
recovered only into its owning draft.

## Current Flow and Constraints

The relevant current flow is:

1. `MainActivity.onShowFileChooser()` launches an Android picker.
2. Android sends selected `content://` URI strings in a `content-selected` message.
3. `RichTextEditorMedia` calls `Android.readContentUri()` for every URI.
4. `JsInterface.readContentUri()` reads the entire object into a `byte[]`, converts it to Base64, and returns it to JS.
5. JS decodes the Base64 into another byte array and constructs a `File`.
6. `src/state/mediaupload/media-upload.ts` either sends that `File` directly or divides it into chunks and uploads four chunks in
   parallel.

This duplicates the selected image, video, or document in the Java and JS heaps and still makes Chromium own the
eventual upload body.

The existing server flow already has the endpoints needed by the native uploader:

- `POST /media/upload` creates an upload and returns the server-selected `chunkSize`.
- `GET /media/upload/{id}` returns `uploadedChunks` and supports resume.
- `PUT /media/upload/{id}/{chunk}` accepts one exact-sized chunk. Chunks may be sent out of order and duplicate chunk
  requests are allowed while the upload is pending.
- `POST /media/private?upload={id}&downsize={boolean}` consumes the completed upload and returns
  `PrivateMediaFileInfo`.
- `DELETE /media/upload/{id}` cancels and removes a pending upload.

The generated Java client is useful for the JSON types, but its upload methods accept only a filesystem `Path`. It
cannot stream a range from a `content://` URI without first copying that range to a temporary file. Implement the native
media HTTP client with OkHttp request bodies backed by `ContentResolver`, while continuing to use generated Moera types
for request and response JSON.

Two server limitations must be explicit:

- `MediaUploadAttributes.fileSize`, the generated Java field, and the node database column are currently 32-bit
  integers. The Android client must reject a source larger than `Integer.MAX_VALUE` for now. Supporting attachments
  larger than 2 GiB requires a public Node API and database migration to a 64-bit size first.
- Consuming a completed upload is not idempotent: the node deletes the upload record after creating the private media
  owner. If the server commits that operation but its response is lost, the client cannot safely retry or discover the
  resulting media ID. The MVP must report this narrow case as `completion-unknown` and must not retry it automatically.
  A later server change should make upload consumption idempotent before claiming exactly-once completion across
  arbitrary process/network failure.

## Target Architecture

```text
RichTextEditorMedia
        |
        | file chooser
        v
MainActivity -> Android media/document picker
        |
        | persist read grant, query metadata, create thumbnail when applicable
        v
SelectedMediaStore
  local media ID -> content URI (app-private only)
        |
        | media-selected {id, name, mimeType, size, thumbnail?}
        v
WebView selection UI / upload controller
        |
        | startMediaUpload(id, downsize, draftId)
        v
MediaUploadScheduler
        |
        +-- Android 14+: user-initiated data transfer JobService
        |
        +-- Android 8-13: foreground WorkManager worker
        |
        v
MediaUploadRunner -> ContentResolver -> streaming OkHttp RequestBody -> Moera node
        |
        | progress / completed / failed events (no source URI or bytes)
        v
WebView receives PrivateMediaFileInfo and continues the existing caption/lease flow
```

The uploader, scheduler, and database must use the application context and must not hold a `MainActivity`, `WebView`, or
`JsMessages` reference. `MainActivity` should subscribe while it is alive and replay persisted state only in response to
`requestMediaUploadStates()` from a capability-registered web client.

## Bridge Contract

### Versioning and rollout handshake

Bump `JsInterface.API_VERSION` from 2 to 3, but do not use the version alone to select the new picker result format. The
web client and APK are deployed independently, so a new APK may temporarily host an old/cached web client.

Add a capability handshake that new JS calls during application startup:

```ts
window.Android?.setWebClientCapabilities(JSON.stringify({
    nativeMediaUpload: 1,
    clientId: Browser.clientId
}));
```

The Android side should enable the new selection event only after this handshake. Without it, retain the existing
`content-selected` behavior so an old web client continues to work. Deploy the web-client support before publishing the
APK that emits native media references. Reset the capability state at the start of every top-level WebView navigation;
each newly loaded document must opt in again before Android sends it native IDs or replays upload state. Once
`nativeMediaUpload: 1` is active, every Android picker result uses `media-selected`; do not choose the legacy URI/Base64
path item-by-item based on MIME type or size.

The handshake installs the message listener, but it must not by itself claim or attach persisted upload results. Call
`requestMediaUploadStates()` once both the handshake and the composer are ready, regardless of which finishes first. A
composer with a loaded draft accepts rows assigned to that draft plus unassigned rows; an empty composer with no
`draftId` accepts only unassigned rows. Upload state must never be inserted into an unrelated assigned draft.

### Selection event

Add a `media-selected` message that preserves picker order and supports mixed images, videos, and documents:

```json
{
  "source": "moera-android",
  "action": "media-selected",
  "items": [
    {
      "id": "513e82ae-...",
      "name": "IMG_20260813_074100.jpg",
      "mimeType": "image/jpeg",
      "size": 6842931,
      "thumbnail": "data:image/jpeg;base64,..."
    },
    {
      "id": "74f8c72b-...",
      "name": "VID_20260813_074242.mp4",
      "mimeType": "video/mp4",
      "size": 734829234,
      "thumbnail": "data:image/jpeg;base64,..."
    },
    {
      "id": "0aa331f0-...",
      "name": "project-materials.zip",
      "mimeType": "application/zip",
      "size": 1254098734,
      "thumbnail": null
    }
  ]
}
```

The `items` array contains only native descriptors, so it needs no discriminator field. No `content://` URI or legacy
item may appear in this message. Images and videos should carry a bounded visual thumbnail; documents normally have
`thumbnail: null` and use the existing generic file presentation.

If metadata or the persistent grant cannot be obtained for an item that requires native upload, emit a selection error
rather than silently returning its URI to JS. Call the original `ValueCallback` with `null` for all items handled by
this side channel so Chromium never receives them.

### JS-to-native methods

Add asynchronous bridge entry points:

```ts
interface AndroidJsInterface {
    abandonDraft(draftId: string): void;
    setWebClientCapabilities(json: string): void;
    startMediaUpload(id: string, downsize: boolean, draftId: string | null): void;
    assignMediaUploadsToDraft(draftId: string): void;
    cancelMediaUpload(id: string): void;
    discardSelectedMedia(id: string): void;
    requestMediaUploadStates(): void;
    acknowledgeMediaUpload(id: string): void;
}
```

`abandonDraft(draftId)` removes all retained uploads owned by that draft: cancel unfinished transfers and acknowledge
completed results. It must not affect rows whose `draftId` is null or belongs to another draft. The web draft-deletion
sagas call this bridge method directly after the node draft has been deleted; this operation does not depend on any
mounted composer or uploader hook.

`startMediaUpload` receives the local media ID, downsize flag, and current draft ID directly:

```ts
window.Android.startMediaUpload("74f8c72b-...", true, "2f37dc85-...");
```

The local media ID is also the correlation ID. Starting it twice must be idempotent: return/replay the existing
transfer state rather than scheduling a second upload. When present, `draftId` is the ID of the server-side post or
comment draft and is immutable once stored. Reject an attempt to reuse the same `id` with a different non-null
`draftId`.

For an empty composer that has no draft ID yet, pass `null`:

```ts
window.Android.startMediaUpload("74f8c72b-...", true, null);
```

Android must persist that transfer with `draftId = null`; it must not invent a temporary, page-local, posting, or
comment ID.

When the composer later receives a draft ID, call:

```ts
window.Android.assignMediaUploadsToDraft(draftId);
```

This command atomically sets `draftId` on every retained `MediaUpload` whose `draftId` is null and whose `homeLocation`
matches the current home. It includes queued, active, failed, finalizing, and completed-but-unacknowledged rows. It must
be idempotent and fill null values only: it must never move an upload that is already assigned to another draft. After
the transaction commits, Android should emit the current `media-upload-state` for every affected row so the editor's
uploader hook can update its ownership map.

Serialize `assignMediaUploadsToDraft` and `requestMediaUploadStates` on the same native command executor. If JS calls
assignment followed by state request, the request must observe the committed assignment. Uploads started after the
composer has a draft ID include it directly and do not depend on the bulk assignment.

Do not accept a source URI, arbitrary upload URL, bearer token, or arbitrary headers from JavaScript. Android already
receives the current home location and token through `connectedToHome()`. Keeping endpoint resolution and authorization
inside native code prevents the bridge from becoming a general-purpose primitive for sending a selected private file
to an arbitrary host.

`discardSelectedMedia` is used when an item is removed or the selection dialog is canceled before upload.
`acknowledgeMediaUpload` is sent only after JS has validated the completed result and confirmed that the attachment is
already stored in the saved server draft; it allows native cleanup without losing a result during a WebView reload or
a failed draft autosave. Upload tracking ends at this point and does not follow later post/comment submission.

### Native-to-JS upload events

Add these messages to `JsMessages` and the TypeScript `AndroidMessage` union:

```json
{
  "source": "moera-android",
  "action": "media-upload-progress",
  "id": "74f8c72b-...",
  "draftId": null,
  "loaded": 183500800,
  "total": 734829234
}
```

```json
{
  "source": "moera-android",
  "action": "media-upload-completed",
  "id": "74f8c72b-...",
  "draftId": "2f37dc85-...",
  "media": { "id": "...", "mimeType": "video/mp4" }
}
```

The top-level `id` is the same local correlation ID used by selection, start, progress, state, failure, cancellation,
and completion messages. Keep this field name identical in every message. `media.id` is a different value: it is the
server-assigned private media ID.

`draftId` is nullable in every post-start upload event. It may make the single forward transition from null to the
assigned draft ID while a transfer is running; after assignment, every subsequent live or replayed event must carry
that ID. The assignment operation emits a fresh state immediately, so JS does not have to wait for the next progress
boundary to observe the change.

The `media` object in the abbreviated example above must be the complete `PrivateMediaFileInfo` returned by
`POST /media/private?upload={serverUploadId}&downsize={boolean}`, not a native reconstruction or a selected subset of
fields. Persist and forward the whole validated response, including all required fields and any optional direct paths,
previews, grants, attachment/malware flags, compression fields, and operations supplied by the node.

The TypeScript contract should therefore be explicit:

```ts
interface AndroidMessageMediaUploadCompleted {
    source: "moera-android";
    action: "media-upload-completed";
    id: string;
    draftId: string | null;
    media: PrivateMediaFileInfo;
}
```

```json
{
  "source": "moera-android",
  "action": "media-upload-failed",
  "id": "74f8c72b-...",
  "draftId": "2f37dc85-...",
  "error": {
    "code": "network",
    "message": "Upload interrupted",
    "retryable": true,
    "completionUnknown": false
  }
}
```

Also define `media-upload-state` for replay after `requestMediaUploadStates()`:

```ts
interface AndroidMessageMediaUploadState {
    source: "moera-android";
    action: "media-upload-state";
    id: string;
    draftId: string | null;
    state: "QUEUED" | "CREATING" | "UPLOADING" | "RETRY_WAIT" | "FINALIZING" | "COMPLETED" | "FAILED";
    name: string;
    mimeType: string;
    thumbnail: string | null;
    loaded: number;
    total: number;
    media?: PrivateMediaFileInfo;
    error?: AndroidMediaUploadError;
}
```

The state message must contain enough durable selection metadata to recreate an in-progress attachment placeholder
after the original JavaScript objects have been destroyed. `media` is required exactly for `COMPLETED`; `error` is
required for `FAILED`. The top-level `id` keeps the same name and value in all messages.

`requestMediaUploadStates()` remains parameterless. Android should replay all retained, non-canceled transfer rows for
the current home, each carrying its nullable `draftId`. Every editor-local uploader hook filters the replay immediately:
it accepts rows whose `draftId` equals the hook's loaded draft ID. A composer without a draft ID accepts only null-owned
rows. A composer with a draft ID calls `assignMediaUploadsToDraft(draftId)` before requesting the replay, so previously
unassigned rows are emitted again with that ID. Results assigned to other drafts must not be retained, inserted into the
editor, or acknowledged by that hook.

Throttle live progress messages (for example, at most four per second and always on a confirmed-chunk boundary). Always
post to the WebView on its UI thread. A background job should write state to the database even when there is no active
WebView; the current `MainActivity` subscriber may forward it when present.

## Android Implementation

### 1. Picker and metadata

Refactor the picker callbacks in `MainActivity` into a small media-selection coordinator.

- Use `PickVisualMedia.ImageOnly`, `VideoOnly`, or `ImageAndVideo` according to the file input accept types. The current
  code uses the Photo Picker only for image-only inputs and falls back to `GetContent` for a combined image/video input.
- Replace `GetContent`/`GetMultipleContents` with `OpenDocument`/`OpenMultipleDocuments` for generic selection so a
  provider can issue a persistable grant. Pass the complete accepted MIME-type list rather than only the first accept
  value.
- Classify every selected image, video, and generic/document item as native whenever the API version 3 capability is
  active. A missing provider MIME type should fall back to `application/octet-stream` and use the native document path.
- Immediately call `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` for every native attachment.
- Query MIME type, `OpenableColumns.DISPLAY_NAME`, and `OpenableColumns.SIZE`. If size is missing, try
  `AssetFileDescriptor.getLength()`/`ParcelFileDescriptor` metadata. The chunked API needs an exact positive size; if it
  remains unknown, reject the native item rather than copying it merely to discover a length.
- Validate that the URI scheme is `content`, the item matches the file input's accepted types, and the size is no
  greater than the current 32-bit server limit.
- Generate a bounded thumbnail off the main thread for images and videos. Reuse the existing Glide dependency, with a
  focused `MediaMetadataRetriever` fallback for video if necessary, constrain the result to roughly 256 px, encode it
  as a small JPEG/PNG data image, and store it in app-private storage. Preserve image orientation and use PNG when a
  preview needs transparency. Documents should use a nullable thumbnail unless a bounded preview is added deliberately
  later; thumbnail generation must never copy or decode the entire original attachment into the Java heap.
- Insert the durable selected-media row before emitting `media-selected`.
- Preserve the original selection order in the native descriptors.

On a persistable-grant failure, return a structured `media-selection-failed` event. Do not promise background upload
using a temporary grant.

### 2. Durable model

Add Room and keep selection data separate from transfer data so a selected item can exist before upload begins.

Suggested `SelectedMedia` fields:

- `id` (UUID primary key)
- `uri`
- `displayName`
- `mimeType`
- `size` (`long`, even though the server currently has a smaller limit)
- `thumbnailPath` (nullable for documents)
- `createdAt`
- `grantPersisted`

Suggested `MediaUpload` fields:

- `mediaId` (primary key referencing the selected-media ID; do not enforce a database foreign key if the source row is
  removed as soon as finalization succeeds)
- `draftId` (nullable server-side post/comment draft ID; assignable once from null and immutable afterward)
- `homeLocation` (snapshot used to prevent cross-account upload)
- `clientId` (non-secret request correlation value captured from the handshake)
- `serverUploadId`
- `serverChunkSize`
- `serverDeadline`
- `confirmedBytes`
- `state`
- `downsize`
- `resultJson`
- `lastErrorCode`, `lastErrorMessage`, `retryable`, `completionUnknown`
- `createdAt`, `updatedAt`
- scheduler identifiers (`jobId` and/or WorkManager ID)

Use states such as `SELECTED`, `QUEUED`, `CREATING`, `UPLOADING`, `RETRY_WAIT`, `FINALIZING`, `COMPLETED`, `FAILED`, and
`CANCELED`. State transitions and server response persistence must be transactions. The server's `uploadedChunks` list
is authoritative after a restart; `confirmedBytes` is a cached UI value derived from it, not the resume source of truth.

Add a DAO transaction equivalent to `UPDATE media_upload SET draft_id = :draftId WHERE draft_id IS NULL AND
home_location = :currentHome`. Return the affected IDs so their states can be replayed after commit. Run this transaction
and bridge state queries on one ordered executor to close the race between draft assignment and recovery requests.

Do not persist an authentication token in either table. Read the current token at execution time and verify that the
current home location still equals the row's `homeLocation`. If the user changes or disconnects the home node, stop with
a non-retryable `home-changed`/`authentication-required` state instead of uploading to a different account.

### 3. Streaming HTTP client

Add a focused `MediaUploadApi` using one application-wide OkHttp client and generated Moera request/response types.
Declare OkHttp as a direct dependency instead of relying on it transitively through `moeralib-android`.

Every request should mirror the web client:

- `Accept: application/json`
- `Content-Type: application/json` for creation and the source MIME type for chunks
- `Authorization: Bearer token:{homeToken}`
- `Client-ID: {clientId}` from the capability handshake when available
- `Content-Disposition` with an RFC 5987 UTF-8 filename where applicable
- an app-specific `User-Agent`

Resolve the API root only from the stored home location, require HTTP(S), and use HTTPS except for explicitly configured
developer/local endpoints. Parse normal Moera `Result` errors into a structured native error rather than exposing raw
response bodies.

Implement a `ContentUriRequestBody` that:

- opens the selected URI through `ContentResolver`;
- seeks to `chunk * chunkSize` through a file descriptor when the provider supports seeking;
- has a correct `contentLength()` equal to the exact expected chunk length;
- copies through a small fixed buffer directly into the OkHttp sink;
- stops after the requested range and fails if EOF occurs early;
- reports bytes written without accumulating them;
- closes every descriptor/stream on success, failure, and cancellation.

Use up to four parallel chunk requests, matching the current web client, only when independent seekable descriptors can
be opened safely. Fall back to sequential streaming for non-seekable providers. A retry may reopen and seek/skip to the
start of its chunk, but must never materialize the chunk as a full `byte[]` or temporary file.

### 4. Upload state machine

For every native image, video, or document, use the resumable server API even when it fits in one chunk. The extra
create/finalize requests buy one consistent, restartable implementation and avoid a second direct-body code path.

The runner should:

1. Verify the selected-media row, persisted grant, current home identity, token, exact size, URI readability, and that
   any non-null draft association has not changed. A null `draftId` is valid and does not block transfer.
2. If `serverUploadId` is absent, call `POST /media/upload` with MIME type, title, and size. Persist the returned ID,
   chunk size, deadline, and empty uploaded set before starting any PUT.
3. If `serverUploadId` exists, call `GET /media/upload/{id}` and rebuild the missing chunk set from
   `uploadedChunks`.
4. Upload missing chunks with bounded concurrency. After each successful PUT, merge the returned `uploadedChunks`
   monotonically in a transaction and recompute confirmed progress; an out-of-order response must not regress local
   state. Reconcile once more with GET before finalization when parallel requests were used.
5. Once the server reports `completedAt`, set `FINALIZING` and call
   `POST /media/private?upload={id}&downsize={downsize}`.
6. Serialize and persist the complete `PrivateMediaFileInfo` response without dropping optional fields before notifying
   listeners. Mark `COMPLETED`, release the URI grant, and remove the thumbnail/source row, but retain the completed
   transfer row until JS acknowledges it.
7. On cancellation, cancel active OkHttp calls and scheduled work, best-effort `DELETE` the server upload, release the
   grant, delete the thumbnail, and mark/remove the local rows.

`downsize` should retain its current meaning for images and videos. Always pass `false` for generic document
attachments; document bytes must not enter image/video compression logic.

Retry chunk GET/PUT failures caused by connectivity, HTTP 429, and transient 5xx responses with bounded exponential
backoff and jitter. Chunk PUT is retryable because the node accepts duplicate chunks. Do not blindly retry validation,
authorization, source-access, or home-change failures.

Creation and finalization POSTs are not currently idempotent after an ambiguous network failure. A failed creation may
be retried by creating a fresh upload, because an unknown earlier upload contains no chunks and will expire; bound the
number of such attempts to avoid excessive temporary server allocation. Finalization is different: if no response was
received, stop with `completion-unknown` and do not retry automatically, because a private media item may already have
been created.

### 5. Background scheduling and notifications

All uploads originate from a visible user action, so schedule the durable job as part of `startMediaUpload()` while the
activity is visible.

- On Android 14+ (API 34+), use a user-initiated data transfer `JobService`: declare
  `RUN_USER_INITIATED_JOBS`, protect the service with `BIND_JOB_SERVICE`, call `setUserInitiated(true)`, require an
  internet-capable network, provide the remaining upload size through `setEstimatedNetworkBytes()`, and publish/update
  the required notification through `JobService.setNotification()`.
- On Android 8-13, use the already included WorkManager as a long-running foreground worker. Call
  `setForegroundAsync()` before network work, declare the merged WorkManager foreground service as `dataSync`, and add
  the foreground-service permissions required by the target SDK.
- Persist jobs across reboot where supported and add `RECEIVE_BOOT_COMPLETED` if `JobInfo.setPersisted(true)` is used.
- Use a unique scheduler identity per local media ID so repeat bridge calls do not create duplicate jobs.
- Give the notification progress and Cancel actions. Notification clicks should open `MainActivity` with the media ID,
  after which JS can request/replay the transfer state.
- Ask for `POST_NOTIFICATIONS` in the upload UX independently of the existing push-notification setting. A denied
  notification permission should be handled according to platform behavior and should not silently corrupt upload
  state.

The runner must checkpoint after every confirmed chunk because the system can stop a UIDT job for constraints, thermal
pressure, or low memory. `onStopJob()`/Worker cancellation should cancel in-flight calls and leave the durable state
resumable.

Android's official references for these choices are:

- [Photo Picker: persist media file access](https://developer.android.com/training/data-storage/shared/photo-picker#persist-media-file-access)
- [User-initiated data transfer jobs](https://developer.android.com/develop/background-work/background-tasks/uidt)
- [Long-running WorkManager workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

### 6. Cleanup and lifecycle

- Release the persisted URI permission only after successful finalization, explicit discard/cancel, or expiration of an
  abandoned selection.
- Periodically remove `SELECTED` rows that never started (for example after 24 hours), releasing their grants and
  thumbnails.
- Retain unacknowledged completed results so their assigned draft, or any composer for an unassigned result, can recover
  them. Delete them after acknowledgement, explicit upload discard, or a conservative safety expiry aligned with the
  server's maximum draft lifetime; do not use a short generic timer that can expire while recovery is still possible.
- Implement `AndroidJsInterface.abandonDraft(draftId)` as one native upload operation. When the web client deletes a draft,
  it calls this method so Android cancels/discards unfinished transfers owned by that draft and acknowledges
  completed-but-unacknowledged transfers. It must not delete unassigned transfers merely because an unrelated draft was
  deleted.
- At startup, reconcile Room state with JobScheduler/WorkManager and re-enqueue resumable work that has no scheduler
  entry.
- Never log URI strings, tokens, thumbnail Base64, or complete server response bodies. Logs and notifications should use
  only the local media ID and sanitized display name.

#### Completion while the WebView is unavailable

Treat the absence of an Activity or WebView as an expected background-upload state, not as an error. It can occur when
the user backgrounds or closes the Activity, Android reclaims it, the WebView reloads, or its renderer process exits. A
UIDT `JobService` or WorkManager worker may also run in a process that has no UI at all.

When finalization succeeds in this state:

1. Persist `COMPLETED` and the complete `PrivateMediaFileInfo` atomically before attempting UI delivery.
2. Release the persisted source URI grant and delete only source-side thumbnail/selection data, because the source bytes
   are no longer needed.
3. If there is no active WebView subscriber, do not queue an in-memory callback and do not treat delivery as complete.
   Leave the transfer result unacknowledged in Room and update the system notification to Completed.
4. After a new document performs the capability handshake and calls `requestMediaUploadStates()`, replay a
   `media-upload-state` message with the same top-level `id`, its nullable `draftId`, state `COMPLETED`, and the same
   complete `PrivateMediaFileInfo` that would have been sent in `media-upload-completed`.
5. Delete the durable result only after `acknowledgeMediaUpload(id)` or bounded retention expiry. Replaying state must
   never repeat server finalization.

The web client already stores non-empty drafts on the node and persists their ID in the page URL. After application
restart, it restores the composer and loads the server draft when one exists. Once the bridge and composer are ready:

- If a draft was loaded, call `assignMediaUploadsToDraft(draft.id)` and then `requestMediaUploadStates()`. The ordered
  native command executor makes the replay include the newly assigned rows.
- If the composer is still empty and has no draft ID, call only `requestMediaUploadStates()` and process unassigned
  rows. Do not create a draft merely to start or restore their byte transfer.
- Whenever `COMPOSE_DRAFT_SAVED` or `COMMENT_DRAFT_SAVED` changes the current composer from no draft ID to a real ID,
  immediately call `assignMediaUploadsToDraft(draft.id)`. This also covers the draft created by autosaving the first
  completed native media.

Process eligible states as follows:

1. For `COMPLETED`, validate the full `PrivateMediaFileInfo`, deduplicate by its server media identity against the
   media already present in the loaded draft/editor, and pass new results through the existing caption, lease, and
   rich-text attachment insertion flow.
2. Trigger the normal draft autosave after inserting recovered media. Call `acknowledgeMediaUpload(id)` after the
   attachment is already present in the loaded `DraftInfo` or a subsequent draft save succeeds. If saving fails, keep
   the native completion unacknowledged and retry without inserting a duplicate.
3. For `QUEUED`, `CREATING`, `UPLOADING`, `RETRY_WAIT`, or `FINALIZING`, reconstruct an attachment placeholder from the
   replayed name, MIME type, thumbnail, and byte counts, bind it to the existing native upload ID, and continue updating
   it from live progress/completion/failure events. Do not start a second upload.
4. For `FAILED`, restore the failed placeholder and its retry/cancel controls when the error is still actionable.

No separate completion-recovery UI is needed. An assigned result whose draft is not currently loaded remains durable
and unacknowledged until that draft is restored. An unassigned result may be restored into any current composer and is
claimed as soon as that composer obtains or already has a draft ID. Upload states assigned to a different draft must
never be attached to or acknowledged from the active editor.

## Web Client Changes (`../moera-client-react`)

### 1. Add a source abstraction

Define a serializable native attachment descriptor, for example:

```ts
export interface AndroidMedia {
    id: string;
    name: string;
    type: string;
    size: number;
    thumbnail: string | null;
}

export type LocalMediaUploadSource = File | AndroidMedia;
export type MediaUploadSource = LocalMediaUploadSource | string;
```

Define `AndroidMedia` alongside the Android upload contract in `src/api/android/media-upload.ts`. Keep the source unions,
type guard, and bridge-message conversion helpers in `src/state/mediaupload/media-source.ts`.

Use `LocalMediaUploadSource` in `RichTextImageValues`, `SelectedImages`, `RichTextEditorMedia`, and the rich-text media
upload action. `AndroidMedia` mirrors a `media-selected.items` entry without adding a synthetic discriminator; distinguish
it from `File` with a focused type guard such as `source instanceof File`. Keep URL strings separate because their
progress semantics remain 0-100 rather than byte-based.

Update `RichTextImageValues.files`, `SelectedImages`, and related dialog props to accept `LocalMediaUploadSource[]`.
`SelectedImages` should use `AndroidMedia.thumbnail` directly for native images and videos; it should continue using
the `File`/object-URL and `createVideoThumbnail(File)` paths only for browser-originated or compatibility-mode files. A
native document with no thumbnail should use the existing generic file icon/presentation and should not enter the
image/video selection dialog when `attachmentType === "file"`. Size, MIME checks, delete behavior, image/video badges,
compression choice, and captions should work from the common `name`/`type`/`size` fields without reading native bytes.

When a native selection is removed or the dialog is canceled, call `discardSelectedMedia(id)` through the editor's
uploader hook. Ensure ownership is transferred to that hook before closing a confirmed dialog so the same cleanup path
does not discard an active upload.

### 2. Route native sources through the Android uploader

Extend `src/state/mediaupload/media-upload.ts`:

- Keep the current URL and `File` branches unchanged for browsers, clipboard, drag-and-drop, and compatibility with old
  Android bridge events/APKs. They are not the Android picker path after the version 3 handshake.
- For every picker-produced `AndroidMedia`, whether image, video, or document, retain the existing client-side
  `media.max-size` check, call the editor-local uploader, and map native progress events to the existing
  `(loaded, total)` callback.
- Pass the selected compression/downsize value for images and videos and force it to `false` for document attachments,
  matching the current `attachmentType === "file"` behavior.
- Validate the native completion payload with the generated `PrivateMediaFileInfo` schema before resolving the promise.
- Resolve with the same `PrivateMediaFileInfo` type so the current rich-text saga's caption creation, remote media lease,
  and `onSuccess` behavior remain untouched.
- Reject with a focused `NativeMediaUploadError` that the existing error flow can render without leaking internal
  details.

Call `useAndroidUploader(...)` directly from each `RichTextEditorMedia`. Pass `draftId`, `draftReady`, `draftMediaIds`,
the picker-result callback, and the restore callback as hook parameters; use the returned `AndroidUploaderHandle`
for selection expectation, upload, cancel, and discard operations. Each hook owns only that editor's
native references, pending promises, progress callbacks, restored states, and one-shot picker expectation. There is no
singleton upload manager and no registry of active drafts. Multiple mounted editors therefore have independent hook
instances; each instance rejects bridge state whose `draftId` does not match its own before retaining it. Implement this
state directly with React refs, effects, and callbacks; do not wrap or instantiate a legacy uploader manager/controller
class inside the hook.

The hook performs the capability handshake, listens for Android messages, correlates accepted events by local media ID,
and tolerates duplicate/replayed state. Picker results and restored media enter the owning editor through the callbacks
passed to the hook, without using `RichTextEditorMediaContext` as an uploader transport. The context remains only for
normal communication with nested editor UI. The rich-text upload action carries the returned handle's upload callback so
a saga never looks up global uploader state. Request states only after that composer is ready. A completion is
acknowledged only after full `PrivateMediaFileInfo` schema validation and the hook observes that the same draft was saved
with the resulting media ID. It then forgets the upload and does not track later publication.

Keep recovery orchestration in a subordinate `useAndroidUploadRecovery(...)` hook. It owns the minimal completed-upload
records awaiting acknowledgement, restore deduplication and batching, draft assignment/state requests, and
acknowledgement after autosave. The main uploader remains the only Android message listener and owns selection, live
progress, pending promises, completion validation, cancel, and discard. It passes normalized states to recovery through
`observe(state, shouldRestore)` and removes recovery bookkeeping through `forget(id)`; recovery calls back only when an
acknowledged upload must be removed from the main uploader's maps. Do not pass the uploader's maps into the recovery hook
or create a second bridge listener.

Store only `draftId`, state, byte counts, completion media, and failure information in the main uploader's state map.
The map key already supplies the local upload ID, while source names, MIME types, sizes, and thumbnails belong exclusively
to the separate source map; protocol `source` and `action` fields are not retained after message processing.

### 3. Restore uploads with server-side drafts

Integrate native upload recovery with both post and comment draft flows:

- Start upload immediately. Pass the current `DraftInfo.id` to `startMediaUpload` when available and `null` for an empty
  composer.
- After `COMPOSE_DRAFT_LOADED` or `COMMENT_DRAFT_LOADED` has populated the editor, enqueue
  `assignMediaUploadsToDraft(draft.id)` followed by `requestMediaUploadStates()`. When an empty composer becomes ready,
  request states without assignment so it can recover unassigned uploads.
- On the first `COMPOSE_DRAFT_SAVED` or `COMMENT_DRAFT_SAVED` that supplies an ID, call
  `assignMediaUploadsToDraft(draft.id)`. For new post drafts, the existing `COMPOSE_DRAFT_SAVED` → `updateLocation` flow
  still puts the ID into the URL.
- Merge completed media and rebuild in-progress placeholders for rows assigned to the current draft and for unassigned
  rows. Ignore rows assigned to any other draft. Restore their confirmed `loaded`/`total` values and live event
  subscriptions.
- Make replay idempotent. If the loaded draft already contains the completed server media, do not append it again; just
  acknowledge the corresponding native upload. If an autosave is pending, keep a per-upload restore marker until save
  success so repeated state responses cannot duplicate it.
- Call one `useAndroidUploader(...)` per editor. When an editor is detached, dispose only its JavaScript state and
  listeners; do not cancel its native transfers. Assigned transfers remain with their draft, and a later hook for that
  draft requests states again and resumes its UI.
- On explicit draft deletion, the post/comment saga calls `window.Android.abandonDraft(draftId)` directly after deleting
  the node draft. Android cancels unfinished associated uploads and discards/acknowledges terminal rows without touching
  null-owned or other-draft rows. Post/comment submission requires no uploader-hook action.

### 4. Keep backward compatibility

The new web client must continue handling the old `content-selected` event and the old synchronous
`getContentUri*`/`readContentUri` methods for APK API versions 1-2. The new Android app must retain those methods and emit
the old event for all picker types when the capability handshake is absent, so an old/cached web client continues to
work.

The URI/Base64 picker path is compatibility-only. A capability-enabled API version 3 web client running in the new app
must never use it for an image, video, or document. In the new Android app, this path exists only for an old/cached web
client that did not perform the handshake. The new web client's handler remains solely so it can run against an older
APK that does not implement native upload. Remove the handler and native fallback only after those compatibility
requirements are intentionally dropped.

## Security and Authentication

- Continue using the home location and admin token already synchronized by `connectedToHome()`. Do not add credentials
  to bridge events or upload rows.
- Snapshot and validate home identity for every transfer. A local media ID selected for one home must never be uploaded
  after the user switches homes.
- Scope bulk draft assignment to the current home. It must neither expose nor modify unassigned uploads created for a
  different home.
- Restrict the native API client to the configured home API root and to the fixed media endpoints listed above.
- Treat local IDs as unguessable UUIDs, validate every state transition, and reject IDs that are unknown, expired, or
  already discarded.
- Keep source URIs only in the app-private database. Only bounded visual thumbnails are intentionally exposed to JS;
  documents without a generated preview expose metadata only.
- Preserve the existing target-origin restriction in `JsMessages`; ensure progress replay is not sent before the
  trusted web client completes the capability handshake.

An upload-specific token would further reduce credential scope, but the Node API does not currently issue such a
credential. Reusing the token that Android already stores is the minimal compatible implementation. A temporary scoped
upload credential can be designed as a separate Node API change later.

## Expected Repository Changes

In `moera-client-android`:

- Refactor `MainActivity.java` media/document picker routing and connect/disconnect the selection and upload-state
  observers.
- Extend `js/JsInterface.java`, bump its API version, and keep the API 1-2 URI methods for compatibility.
- Add `assignMediaUploadsToDraft`, serialize it with upload-state requests, and implement the current-home-scoped Room
  transaction that fills only null draft IDs.
- Extend `js/JsMessages.java` with selection, progress, state, completion, and failure messages; centralize posting on
  the WebView UI thread.
- Add a focused package (for example `org.moera.android.media`) containing the Room entities/DAO/database, selection
  coordinator, thumbnail generator, upload operations/state machine, scheduler, UIDT service, WorkManager worker,
  notification helper, `MediaUploadApi`, and `ContentUriRequestBody`.
- Update `app/build.gradle` with direct OkHttp, Room runtime/compiler, and MockWebServer/test dependencies.
- Update `AndroidManifest.xml` with UIDT, foreground-service, reboot, JobService, and notification components and
  permissions.
- Add upload channel, progress, cancel, retry, selection failure, and terminal-state strings to all currently supported
  Android locales.
- Add JVM tests under `app/src/test` and provider/WebView/lifecycle tests under `app/src/androidTest`.

In `moera-client-react`:

- Extend `src/react-app-env.d.ts` with bridge methods, selection item types, and upload event types.
- Add the shared `AndroidMedia`/source types and an editor-local `useAndroidUploader(...)` hook.
- Update `src/state/mediaupload/media-upload.ts`, `state/richtexteditor/actions.ts`, and
  `state/richtexteditor/sagas.ts` to accept
  the source union and delegate only native refs to Android.
- Update `RichTextEditorMedia.tsx`, `RichTextImageDialog.tsx`, and `SelectedImages.tsx` for mixed selections, native
  image/video thumbnails, generic document presentation, discard, and capability registration.
- Integrate the uploader hook with post/comment draft creation, URL restoration, draft-loaded events, autosave success,
  and nullable draft ownership/assignment. Add direct `window.Android.abandonDraft(draftId)` calls to draft-deletion
  sagas.
- Keep the compatibility-only Base64 imports and `content-selected` handler until old APK/web-client support is
  intentionally removed; never invoke that path for a capability-enabled picker result.

No Moera node or generated API change is required for the MVP. The 64-bit size and idempotent-finalization follow-ups
would be separate cross-repository public API changes.

## Testing

### Android unit and integration tests

- Picker MIME classification for image-only, video-only, mixed media, document-only, mixed document types, missing MIME
  types, and generic file inputs.
- Capability-enabled selections emitting only native descriptors with no URI for images, videos, and documents, plus
  handshake-absent compatibility selections emitting the existing event for an old web client.
- Metadata fallbacks, unknown/zero/over-2-GiB sizes, persistable grant success/failure, and abandoned-grant cleanup.
- Image/video thumbnail generation that preserves orientation, stays within the configured dimensions/size, handles
  transparency deliberately, nullable document thumbnails, and proof that no original source is copied.
- Room state transitions, upload start with a null draft ID, atomic assignment of every current-home null row, no
  reassignment of owned rows, no cross-home assignment, duplicate `startMediaUpload`, acknowledgement, cancellation,
  home changes, ordered state replay, and startup reconciliation.
- `ContentUriRequestBody` range boundaries, early EOF, exact `Content-Length`, seekable and non-seekable providers,
  cancellation, and progress throttling.
- MockWebServer coverage for auth and `Client-ID` headers, create, GET resume, missing chunks, duplicate PUT, finalization,
  DELETE, 429/5xx backoff, malformed JSON, validation/auth failures, and ambiguous POST failures.
- Scheduler selection and manifest behavior on API 26, 33, 34, and 36.
- Instrumentation with a test `ContentProvider` and a large generated/streamed source, verifying that memory use does not
  scale with source size.

### Web-client tests

Keep tests in a separate `tests/` tree mirroring the structure under `src/`; do not colocate `*.test.*` files with
production sources. Use a test-specific TypeScript project so the production `tsconfig.json` continues to include only
`src/`.

- Type guards and mixed `File`/`AndroidMedia` selection order.
- Native image/video thumbnail and document-icon rendering, size validation, deletion/discard, and compression
  selection without reading original native bytes.
- Progress correlation, duplicate/replayed events, cancel, retryable failure, completion validation, and acknowledgement.
- Completion with no active WebView, replay of a schema-equivalent field-complete `PrivateMediaFileInfo`, draft-ID
  filtering, no duplicate insertion/finalization, and cleanup only after the recovered draft is saved and acknowledged.
- Post and comment draft restoration that merges completed uploads, restores confirmed progress for running uploads,
  rebinds live events, and leaves uploads owned by other drafts untouched.
- Empty-composer restoration that accepts null-owned uploads without forcing draft creation, followed by bulk assignment
  when the first draft save returns an ID.
- Unchanged browser `File`, URL, drag/drop, and paste paths, plus compatibility handling of legacy `content-selected`.
- API version 3 Android image selection producing `AndroidMedia` and never invoking `readContentUri` or constructing
  a `File` from Base64.
- The existing caption and remote-lease flow receiving a native `PrivateMediaFileInfo`.

### End-to-end scenarios

- Upload an image, a several-hundred-megabyte video, and a large PDF/archive through the same native source path without
  a large Java/JS heap increase and without exposing their URIs to JS.
- Background the app, turn off the screen, recreate the activity/WebView, interrupt the network, and resume from the
  server-confirmed chunk set.
- Cancel from the notification and from the editor; verify server cleanup and URI grant release.
- Kill the process between chunks and after the server reports upload completion.
- Start an upload from an empty composer, verify it is persisted with `draftId = null`, then type text while it is
  running and verify that the newly created draft ID is assigned to all current-home unassigned uploads.
- Restart with unassigned active and completed uploads, open a composer with or without an existing draft, and verify
  they can be restored there and become assigned when that composer has or obtains a draft ID.
- Finish upload with no Activity/WebView, restart on the draft-bearing URL, load the post/comment draft, and verify that
  the completed media is inserted, autosaved, and acknowledged exactly once.
- Restart while an upload is in progress, load its draft, and verify that the placeholder resumes at the persisted
  confirmed byte count and continues receiving progress without scheduling a duplicate transfer.
- Open a different draft while another draft owns active/completed uploads; verify that none are shown or acknowledged
  in the wrong composer.
- Switch or disconnect the home account during a queued transfer and verify that no bytes go to the new home.
- Verify old web/new APK and new web/old APK compatibility during staged rollout.

## Implementation Sequence

1. **Web compatibility and draft layer:** add API version 3 types, capability handshake, `AndroidMedia`, mixed
   selection support, nullable draft ownership and bulk assignment, per-editor `useAndroidUploader(...)`,
   composer-ready replay/merge, direct native draft abandonment, and tests while retaining the legacy path.
2. **Android selection layer (implemented):** add capability tracking, native image/video/document selection records,
   persisted grants, bounded image/video thumbnails, the `media-selected` event, discard/cleanup, and bridge tests.
3. **Native transport (implemented and verified):** Room, `MediaUploadApi`, `ContentUriRequestBody`, the resumable
   runner, bridge integration, and MockWebServer coverage are implemented, and the complete flow has been verified
   against a real Moera node. All Room instrumentation tests pass on a physical Pixel 10 Pro XL running Android 16.
4. **Durable execution (implemented and verified):** API 34+ uses a persisted UIDT `JobService`; Android 8-13 uses
   a foreground WorkManager worker. Scheduler identities and resumable state are stored in Room, startup reconciliation
   restores missing work, and progress/completion/failure notifications include cancellation. Unit tests, lint, and all
   nine instrumentation tests pass on a physical Pixel 10 Pro XL running Android 16, including scheduler-state and
   merged-manifest checks.
5. **Staged enablement:** deploy the web client first, publish the APK second, monitor native failure categories and
   grant cleanup, then enable the native image/video/document path by default.
6. **Follow-ups:** make upload creation/consumption idempotent and migrate file sizes to 64-bit if >2-GiB attachments are
   required.

## Acceptance Criteria

- The original URI and original bytes never enter the WebView for any API version 3 Android image, video, or document
  selection.
- Upload memory remains bounded by buffers, optional thumbnails, and the configured number of in-flight chunks, not
  attachment size.
- No full-size temporary copy of a selected image, video, or document is created.
- Progress, completion, failure, cancel, compression, captions, and remote leases behave like the current web upload.
- A chunked transfer resumes from server-confirmed chunks after activity, WebView, process, and transient network
  interruption.
- Completion without a WebView persists the full `PrivateMediaFileInfo`; the next WebView receives it under the same
  top-level `id`, and native state is not deleted before explicit acknowledgement or expiry.
- A native upload can start with `draftId = null`. When the composer receives a draft ID, one idempotent operation
  assigns that ID to every current-home unassigned upload without changing any already assigned row.
- After composer restoration, eligible completed media is merged and autosaved exactly once, then acknowledged; active
  media reappears with confirmed progress and continues without a duplicate upload. Unassigned media is eligible for
  any composer, while assigned media is eligible only for its owning draft.
- Upload state assigned to one draft is never inserted into or acknowledged by a different post/comment composer.
- Persisted URI grants are released after success, discard/cancel, or bounded expiration.
- Upload endpoints and credentials cannot be redirected by JavaScript.
- Browser uploads and old APK/web-client combinations continue to work during rollout.
- The URI/Base64 picker path is used only when the version 3 capability is unavailable; it is never selected by MIME
  type or size after the handshake.
- Current 32-bit size and non-idempotent finalization limitations are surfaced explicitly rather than hidden behind
  unsafe automatic retries.
