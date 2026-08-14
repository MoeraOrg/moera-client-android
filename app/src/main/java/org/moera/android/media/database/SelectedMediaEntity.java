package org.moera.android.media.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "selected_media")
public class SelectedMediaEntity {

    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String uri;

    @ColumnInfo(name = "display_name")
    @NonNull
    public String displayName;

    @ColumnInfo(name = "mime_type")
    @NonNull
    public String mimeType;

    public long size;

    @ColumnInfo(name = "thumbnail_path")
    @Nullable
    public String thumbnailPath;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "grant_persisted")
    public boolean grantPersisted;

    public SelectedMediaEntity(
        @NonNull String id,
        @NonNull String uri,
        @NonNull String displayName,
        @NonNull String mimeType,
        long size,
        @Nullable String thumbnailPath,
        long createdAt,
        boolean grantPersisted
    ) {
        this.id = id;
        this.uri = uri;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.size = size;
        this.thumbnailPath = thumbnailPath;
        this.createdAt = createdAt;
        this.grantPersisted = grantPersisted;
    }

}
