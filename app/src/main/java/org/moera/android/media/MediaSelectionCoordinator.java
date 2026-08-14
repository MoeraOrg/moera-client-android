package org.moera.android.media;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.moera.android.js.JsMessages;
import org.moera.android.util.MimeUtil;

/** Converts picker results into persisted, URI-free descriptors for the web client. */
public class MediaSelectionCoordinator {

    private static final long MAX_MEDIA_SIZE = Integer.MAX_VALUE;

    private record Metadata(String name, String mimeType, long size) {
    }

    private final Context context;
    private final SelectedMediaStore store;
    private final ThumbnailGenerator thumbnailGenerator;
    private final JsMessages messages;
    private final WebClientCapabilities webClientCapabilities;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MediaSelectionCoordinator(
        Context context,
        SelectedMediaStore store,
        JsMessages messages,
        WebClientCapabilities webClientCapabilities
    ) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.messages = messages;
        this.webClientCapabilities = webClientCapabilities;
        thumbnailGenerator = new ThumbnailGenerator(this.context, store);
    }

    public void select(List<Uri> uris, String[] acceptTypes) {
        if (uris.isEmpty() || !webClientCapabilities.isNativeMediaUploadEnabled()) {
            return;
        }
        long webClientGeneration = webClientCapabilities.getWebClientGeneration();

        List<Uri> granted = new ArrayList<>();
        try {
            for (Uri uri : uris) {
                if (!Objects.equals(ContentResolver.SCHEME_CONTENT, uri.getScheme())) {
                    throw new MediaSelectionException("invalid-uri", "The selected item is not a content URI");
                }
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                granted.add(uri);
            }
        } catch (RuntimeException e) {
            releaseGrants(granted);
            messages.mediaSelectionFailed(
                "grant-failed",
                "Cannot retain access to the selected media",
                webClientGeneration
            );
            return;
        } catch (MediaSelectionException e) {
            releaseGrants(granted);
            messages.mediaSelectionFailed(
                e.getCode(),
                e.getMessage(),
                webClientGeneration
            );
            return;
        }

        String[] accepted = acceptTypes != null ? acceptTypes.clone() : new String[0];
        executor.execute(() -> process(granted, accepted, webClientGeneration));
    }

    public void close() {
        executor.shutdown();
    }

    private void process(List<Uri> uris, String[] acceptTypes, long webClientGeneration) {
        List<String> storedIds = new ArrayList<>();
        List<SelectedMediaInfo> items = new ArrayList<>();
        try {
            for (Uri uri : uris) {
                Metadata metadata = readMetadata(uri);
                if (!MimeUtil.matches(metadata.mimeType(), acceptTypes)) {
                    throw new MediaSelectionException("type-not-accepted", "The selected media type is not accepted");
                }
                if (metadata.size() <= 0) {
                    throw new MediaSelectionException("size-unknown", "Cannot determine the selected media size");
                }
                if (metadata.size() > MAX_MEDIA_SIZE) {
                    throw new MediaSelectionException(
                        "file-too-large", "The selected media exceeds the current 32-bit upload limit"
                    );
                }

                String id = UUID.randomUUID().toString();
                var thumbnail = thumbnailGenerator.create(id, uri, metadata.mimeType());
                var media = new SelectedMedia(
                    id,
                    uri,
                    metadata.name(),
                    metadata.mimeType(),
                    metadata.size(),
                    thumbnail != null ? thumbnail.path() : null,
                    System.currentTimeMillis(),
                    true
                );
                try {
                    store.put(media);
                } catch (RuntimeException e) {
                    if (thumbnail != null) {
                        //noinspection ResultOfMethodCallIgnored
                        new File(thumbnail.path()).delete();
                    }
                    throw e;
                }
                storedIds.add(id);
                items.add(new SelectedMediaInfo(
                    id,
                    metadata.name(),
                    metadata.mimeType(),
                    metadata.size(),
                    thumbnail != null ? thumbnail.dataUri() : null
                ));
            }
            if (!webClientCapabilities.isCurrentNativeDocument(webClientGeneration)) {
                cleanupFailedSelection(uris, storedIds);
                return;
            }
            messages.mediaSelected(
                items,
                webClientGeneration,
                () -> cleanupFailedSelection(uris, storedIds)
            );
        } catch (MediaSelectionException e) {
            cleanupFailedSelection(uris, storedIds);
            messages.mediaSelectionFailed(e.getCode(), e.getMessage(), webClientGeneration);
        } catch (RuntimeException e) {
            cleanupFailedSelection(uris, storedIds);
            messages.mediaSelectionFailed(
                "selection-failed", "Cannot retain the selected media", webClientGeneration
            );
        }
    }

    private Metadata readMetadata(Uri uri) throws MediaSelectionException {
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolver.getType(uri);
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }

        String name = null;
        long size = -1;
        try (Cursor cursor = resolver.query(
            uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                    name = cursor.getString(nameColumn);
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    size = cursor.getLong(sizeColumn);
                }
            }
        } catch (RuntimeException ignored) {
            // Some document providers do not implement metadata queries completely.
        }

        if (name == null || name.isBlank()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isBlank()) {
            name = "attachment";
        }
        if (size <= 0) {
            size = readDescriptorSize(resolver, uri);
        }

        return new Metadata(name, mimeType, size);
    }

    private static long readDescriptorSize(ContentResolver resolver, Uri uri) throws MediaSelectionException {
        try (AssetFileDescriptor descriptor = resolver.openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null && descriptor.getLength() > 0) {
                return descriptor.getLength();
            }
        } catch (Exception ignored) {
            // Fall through to ParcelFileDescriptor metadata.
        }
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            return descriptor != null ? descriptor.getStatSize() : -1;
        } catch (Exception e) {
            throw new MediaSelectionException("source-unavailable", "Cannot open the selected media", e);
        }
    }

    private void cleanupFailedSelection(List<Uri> granted, List<String> storedIds) {
        for (String id : storedIds) {
            store.discard(id);
        }
        releaseGrants(granted);
    }

    private void releaseGrants(List<Uri> uris) {
        for (Uri uri : uris) {
            try {
                context.getContentResolver().releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (RuntimeException ignored) {
                // The provider or the system may already have revoked the grant.
            }
        }
    }

}
