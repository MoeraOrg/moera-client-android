package org.moera.android.media.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_upload")
public class MediaUploadEntity {

    @PrimaryKey
    @ColumnInfo(name = "media_id")
    @NonNull
    public String mediaId;

    @ColumnInfo(name = "draft_id")
    @Nullable
    public String draftId;

    @ColumnInfo(name = "home_location")
    @NonNull
    public String homeLocation;

    @ColumnInfo(name = "client_id")
    @Nullable
    public String clientId;

    @ColumnInfo(name = "display_name")
    @NonNull
    public String displayName;

    @ColumnInfo(name = "mime_type")
    @NonNull
    public String mimeType;

    public long size;

    @Nullable
    public String thumbnail;

    @ColumnInfo(name = "server_upload_id")
    @Nullable
    public String serverUploadId;

    @ColumnInfo(name = "server_chunk_size")
    @Nullable
    public Integer serverChunkSize;

    @ColumnInfo(name = "server_deadline")
    @Nullable
    public Long serverDeadline;

    @ColumnInfo(name = "uploaded_chunks_json")
    @NonNull
    public String uploadedChunksJson;

    @ColumnInfo(name = "confirmed_bytes")
    public long confirmedBytes;

    @NonNull
    public String state;

    public boolean downsize;

    @ColumnInfo(name = "result_json")
    @Nullable
    public String resultJson;

    @ColumnInfo(name = "last_error_code")
    @Nullable
    public String lastErrorCode;

    @ColumnInfo(name = "last_error_message")
    @Nullable
    public String lastErrorMessage;

    public boolean retryable;

    @ColumnInfo(name = "completion_unknown")
    public boolean completionUnknown;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "job_id")
    @Nullable
    public Integer jobId;

    @ColumnInfo(name = "work_id")
    @Nullable
    public String workId;

    public MediaUploadEntity(
        @NonNull String mediaId,
        @Nullable String draftId,
        @NonNull String homeLocation,
        @Nullable String clientId,
        @NonNull String displayName,
        @NonNull String mimeType,
        long size,
        @Nullable String thumbnail,
        @NonNull String state,
        boolean downsize,
        long createdAt,
        long updatedAt
    ) {
        this.mediaId = mediaId;
        this.draftId = draftId;
        this.homeLocation = homeLocation;
        this.clientId = clientId;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.size = size;
        this.thumbnail = thumbnail;
        this.state = state;
        this.downsize = downsize;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        uploadedChunksJson = "[]";
    }

}
