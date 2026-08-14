package org.moera.android.media;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import org.moera.android.media.api.ContentUriRequestBody;
import org.moera.android.media.api.MediaUploadApi;
import org.moera.android.media.api.MediaUploadApiException;
import org.moera.android.media.database.MediaUploadEntity;
import org.moera.lib.node.types.MediaUploadAttributes;
import org.moera.lib.node.types.MediaUploadInfo;
import org.moera.lib.node.types.PrivateMediaFileInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Executes one resumable upload and checkpoints every server-confirmed chunk. */
public class MediaUploadRunner {

    private static final int PARALLEL_CHUNKS = 4;
    private static final int CREATION_ATTEMPTS = 3;
    private static final int TRANSFER_ATTEMPTS = 5;

    @FunctionalInterface
    private interface ApiOperation<T> {

        T execute() throws MediaUploadApiException, MediaUploadFailure;

    }

    private final MediaUploadOperations mediaUploadOperations;
    private final Context context;
    private final MediaUploadApi api;
    private final ObjectMapper objectMapper;

    public MediaUploadRunner(
        MediaUploadOperations mediaUploadOperations, Context context, MediaUploadApi api, ObjectMapper objectMapper
    ) {
        this.mediaUploadOperations = mediaUploadOperations;
        this.context = context.getApplicationContext();
        this.api = api;
        this.objectMapper = objectMapper;
    }

    public void run(String id) {
        MediaUploadEntity upload = mediaUploadOperations.get(id);
        if (upload == null) {
            return;
        }
        try {
            SelectedMedia selectedMedia = validateSource(upload);
            boolean seekable = ContentUriRequestBody.isSeekable(context.getContentResolver(), selectedMedia.uri());
            MediaUploadInfo serverInfo;
            if (upload.serverUploadId == null) {
                upload = mediaUploadOperations.updateState(id, MediaUploadState.CREATING);
                mediaUploadOperations.emitState(upload);
                MediaUploadEntity creatingUpload = upload;
                serverInfo = retry(
                    id,
                    CREATION_ATTEMPTS,
                    () -> createUpload(creatingUpload, selectedMedia)
                );
            } else {
                String serverUploadId = upload.serverUploadId;
                MediaUploadEntity existingUpload = upload;
                serverInfo = retry(
                    id,
                    TRANSFER_ATTEMPTS,
                    () -> api.get(mediaUploadOperations.credentials(existingUpload), serverUploadId)
                );
            }
            upload = mediaUploadOperations.applyServerInfo(id, serverInfo, true);
            mediaUploadOperations.emitProgress(upload);

            if (serverInfo.getCompletedAt() == null) {
                uploadMissingChunks(upload, selectedMedia, seekable);
                MediaUploadEntity current = requireUpload(id);
                String serverUploadId = current.serverUploadId;
                serverInfo = retry(
                    id,
                    TRANSFER_ATTEMPTS,
                    () -> api.get(mediaUploadOperations.credentials(current), serverUploadId)
                );
                upload = mediaUploadOperations.applyServerInfo(id, serverInfo, true);
                mediaUploadOperations.emitProgress(upload);
            }
            if (serverInfo.getCompletedAt() == null) {
                throw new MediaUploadFailure("upload-incomplete", "Node did not confirm all upload chunks", true);
            }

            upload = mediaUploadOperations.updateState(id, MediaUploadState.FINALIZING);
            mediaUploadOperations.emitState(upload);

            PrivateMediaFileInfo media;
            try {
                media = api.finalizeUpload(
                    mediaUploadOperations.credentials(upload), upload.serverUploadId, upload.downsize
                );
            } catch (MediaUploadApiException e) {
                if (!e.isResponseReceived()) {
                    throw new MediaUploadFailure(
                        "completion-unknown",
                        "Cannot determine whether media finalization completed",
                        false,
                        true,
                        e
                    );
                }
                throw new MediaUploadFailure(
                    e.getCode(), e.getMessage(), false, false, e
                );
            }

            try {
                upload.resultJson = objectMapper.writeValueAsString(media);
            } catch (JacksonException e) {
                throw new MediaUploadFailure("storage", "Cannot store completed media", false, false, e);
            }
            upload.state = MediaUploadState.COMPLETED.name();
            upload.confirmedBytes = upload.size;
            upload.retryable = false;
            upload.completionUnknown = false;
            upload.lastErrorCode = null;
            upload.lastErrorMessage = null;
            mediaUploadOperations.complete(upload);
        } catch (MediaUploadFailure e) {
            mediaUploadOperations.fail(upload, e);
        } catch (RuntimeException e) {
            mediaUploadOperations.fail(
                upload,
                new MediaUploadFailure("internal", "Native media upload failed", false, false, e)
            );
        }
    }

    private SelectedMedia validateSource(MediaUploadEntity upload) throws MediaUploadFailure {
        checkCancelled(upload.mediaId);
        mediaUploadOperations.credentials(upload);
        SelectedMedia media = mediaUploadOperations.selectedMedia(upload.mediaId);
        if (media == null) {
            throw new MediaUploadFailure("source-missing", "Selected media is no longer available", false);
        }
        if (!ContentResolver.SCHEME_CONTENT.equals(media.uri().getScheme())) {
            throw new MediaUploadFailure("source-invalid", "Selected media URI is invalid", false);
        }
        if (!media.grantPersisted() || !hasPersistedReadGrant(media.uri())) {
            throw new MediaUploadFailure("source-permission", "Selected media permission was lost", false);
        }
        if (media.size() <= 0 || media.size() > Integer.MAX_VALUE || media.size() != upload.size) {
            throw new MediaUploadFailure("source-size", "Selected media size is invalid", false);
        }
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(media.uri(), "r")) {
            if (descriptor == null) {
                throw new FileNotFoundException();
            }
        } catch (IOException | SecurityException e) {
            throw new MediaUploadFailure("source-unavailable", "Selected media cannot be opened", false, false, e);
        }
        return media;
    }

    private boolean hasPersistedReadGrant(Uri uri) {
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission() && permission.getUri().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    private MediaUploadInfo createUpload(
        MediaUploadEntity upload, SelectedMedia media
    ) throws MediaUploadApiException, MediaUploadFailure {
        MediaUploadAttributes attributes = new MediaUploadAttributes();
        attributes.setMimeType(media.mimeType());
        attributes.setTitle(media.displayName());
        attributes.setFileSize((int) media.size());
        return api.create(mediaUploadOperations.credentials(upload), attributes);
    }

    private void uploadMissingChunks(
        MediaUploadEntity upload, SelectedMedia media, boolean seekable
    ) throws MediaUploadFailure {
        if (upload.serverChunkSize == null || upload.serverUploadId == null) {
            throw new MediaUploadFailure("invalid-response", "Node did not provide upload parameters", false);
        }
        int chunkCount = (int) ((media.size() + upload.serverChunkSize - 1) / upload.serverChunkSize);
        Set<Integer> uploaded = mediaUploadOperations.readUploadedChunks(upload.uploadedChunksJson);
        List<Integer> missing = new ArrayList<>();
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            if (!uploaded.contains(chunk)) {
                missing.add(chunk);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        if (!seekable || missing.size() == 1) {
            for (int chunk : missing) {
                uploadChunk(upload.mediaId, media, chunk, seekable);
            }
            return;
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(PARALLEL_CHUNKS, missing.size()))) {
            List<Future<?>> futures = new ArrayList<>();
            for (int stream = 0; stream < PARALLEL_CHUNKS; stream++) {
                int start = stream;
                futures.add(executor.submit(() -> {
                    for (int index = start; index < missing.size(); index += PARALLEL_CHUNKS) {
                        uploadChunk(upload.mediaId, media, missing.get(index), true);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new MediaUploadFailure("interrupted", "Media upload was interrupted", true, false, e);
                } catch (ExecutionException e) {
                    api.cancel(upload.mediaId);
                    Throwable cause = e.getCause();
                    if (cause instanceof MediaUploadFailure failure) {
                        throw failure;
                    }
                    throw new MediaUploadFailure("internal", "Chunk upload failed", false, false, cause);
                }
            }
        }
    }

    private void uploadChunk(String id, SelectedMedia media, int chunk, boolean seekable) throws MediaUploadFailure {
        MediaUploadEntity upload = requireUpload(id);
        if (upload.serverChunkSize == null) {
            throw new MediaUploadFailure("invalid-response", "Node did not provide upload parameters", false);
        }
        long offset = (long) chunk * upload.serverChunkSize;
        long length = Math.min(upload.serverChunkSize, media.size() - offset);
        ContentUriRequestBody body = new ContentUriRequestBody(
            context.getContentResolver(),
            media.uri(),
            media.mimeType(),
            offset,
            length,
            seekable,
            () -> mediaUploadOperations.isCancelled(id)
                || Thread.currentThread().isInterrupted()
                || mediaUploadOperations.isStopped(id)
                || !mediaUploadOperations.isCurrentHome(upload.homeLocation),
            null
        );
        MediaUploadInfo response = retry(
            id,
            TRANSFER_ATTEMPTS,
            () -> {
                MediaUploadEntity current = requireUpload(id);
                return api.putChunk(
                    mediaUploadOperations.credentials(current),
                    current.serverUploadId,
                    chunk,
                    media.displayName(),
                    body
                );
            }
        );
        MediaUploadEntity checkpoint = mediaUploadOperations.applyServerInfo(id, response, false);
        mediaUploadOperations.emitProgress(checkpoint);
    }

    private <T> T retry(String id, int attempts, ApiOperation<T> operation) throws MediaUploadFailure {
        MediaUploadApiException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            checkCancelled(id);
            try {
                return operation.execute();
            } catch (MediaUploadApiException e) {
                last = e;
                if (!e.isRetryable() || attempt + 1 >= attempts) {
                    throw fromApi(e, false);
                }
                MediaUploadEntity upload = mediaUploadOperations.updateState(id, MediaUploadState.RETRY_WAIT);
                mediaUploadOperations.emitState(upload);
                long backoff = 500L << attempt;
                sleep(id, backoff + ThreadLocalRandom.current().nextLong(backoff / 2 + 1));
                upload = requireUpload(id);
                upload = mediaUploadOperations.updateState(
                    id,
                    upload.serverUploadId == null ? MediaUploadState.CREATING : MediaUploadState.UPLOADING
                );
                mediaUploadOperations.emitState(upload);
            }
        }
        throw fromApi(last, false);
    }

    private void sleep(String id, long millis) throws MediaUploadFailure {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaUploadFailure("interrupted", "Media upload was interrupted", true, false, e);
        }
        checkCancelled(id);
    }

    private MediaUploadEntity requireUpload(String id) throws MediaUploadFailure {
        checkCancelled(id);
        MediaUploadEntity upload = mediaUploadOperations.get(id);
        if (upload == null) {
            throw new MediaUploadFailure("cancelled", "Media upload was cancelled", false);
        }
        return upload;
    }

    private void checkCancelled(String id) throws MediaUploadFailure {
        if (mediaUploadOperations.isCancelled(id)) {
            throw new MediaUploadFailure("cancelled", "Media upload was cancelled", false);
        }
        if (mediaUploadOperations.isStopped(id) || Thread.currentThread().isInterrupted()) {
            throw new MediaUploadFailure("interrupted", "Media upload was interrupted", true);
        }
    }

    private static MediaUploadFailure fromApi(MediaUploadApiException e, boolean completionUnknown) {
        return new MediaUploadFailure(
            e.getCode(), e.getMessage(), e.isRetryable() && !completionUnknown, completionUnknown, e
        );
    }

}
