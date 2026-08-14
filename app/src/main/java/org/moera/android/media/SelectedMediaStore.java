package org.moera.android.media;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import org.moera.android.media.database.MediaDatabase;
import org.moera.android.media.database.SelectedMediaDao;
import org.moera.android.media.database.SelectedMediaEntity;

/** Persists selected sources until upload ownership is transferred or the selection is discarded. */
public class SelectedMediaStore {

    private final Context context;
    private final SelectedMediaDao dao;
    private final File thumbnailDirectory;

    public SelectedMediaStore(Context context) {
        this.context = context.getApplicationContext();
        dao = MediaDatabase.getInstance(this.context).selectedMediaDao();
        thumbnailDirectory = new File(this.context.getFilesDir(), "media-thumbnails");
    }

    public File getThumbnailDirectory() throws IOException {
        if (!thumbnailDirectory.exists() && !thumbnailDirectory.mkdirs() && !thumbnailDirectory.isDirectory()) {
            throw new IOException("Cannot create thumbnail directory");
        }
        return thumbnailDirectory;
    }

    public void put(SelectedMedia media) {
        dao.insert(toEntity(media));
    }

    @Nullable
    public SelectedMedia get(String id) {
        SelectedMediaEntity entity = dao.get(id);
        return entity != null ? fromEntity(entity) : null;
    }

    public void discard(String id) {
        SelectedMedia media = get(id);
        if (media == null || dao.delete(id) == 0) {
            return;
        }
        releaseGrant(media);
        deleteThumbnail(media.thumbnailPath());
    }

    public void discardOlderThan(long timestamp) {
        for (SelectedMediaEntity media : dao.getOlderThan(timestamp)) {
            discard(media.id);
        }
    }

    private static SelectedMediaEntity toEntity(SelectedMedia media) {
        return new SelectedMediaEntity(
            media.id(),
            media.uri().toString(),
            media.displayName(),
            media.mimeType(),
            media.size(),
            media.thumbnailPath(),
            media.createdAt(),
            media.grantPersisted()
        );
    }

    private static SelectedMedia fromEntity(SelectedMediaEntity entity) {
        return new SelectedMedia(
            entity.id,
            Uri.parse(entity.uri),
            entity.displayName,
            entity.mimeType,
            entity.size,
            entity.thumbnailPath,
            entity.createdAt,
            entity.grantPersisted
        );
    }

    private void releaseGrant(SelectedMedia media) {
        if (!media.grantPersisted()) {
            return;
        }
        try {
            context.getContentResolver().releasePersistableUriPermission(
                media.uri(), Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (RuntimeException ignored) {
            // The provider or the system may already have revoked the grant.
        }
    }

    private void deleteThumbnail(@Nullable String path) {
        if (path == null) {
            return;
        }
        try {
            File thumbnail = new File(path).getCanonicalFile();
            File directory = thumbnailDirectory.getCanonicalFile();
            if (directory.equals(thumbnail.getParentFile())) {
                //noinspection ResultOfMethodCallIgnored
                thumbnail.delete();
            }
        } catch (IOException ignored) {
            // Cleanup is best effort; no user data is exposed if it fails.
        }
    }

}
