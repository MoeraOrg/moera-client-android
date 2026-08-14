package org.moera.android.media.database;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

@Dao
public interface MediaUploadDao {

    @Insert
    void insert(MediaUploadEntity upload);

    @Update
    void update(MediaUploadEntity upload);

    @Nullable
    @Query("SELECT * FROM media_upload WHERE media_id = :mediaId")
    MediaUploadEntity get(String mediaId);

    @Query("SELECT * FROM media_upload WHERE home_location = :homeLocation AND state != 'CANCELED'")
    List<MediaUploadEntity> getRetained(String homeLocation);

    @Query("SELECT media_id FROM media_upload WHERE draft_id IS NULL AND home_location = :homeLocation")
    List<String> getUnassignedIds(String homeLocation);

    @Query(
        "UPDATE media_upload SET draft_id = :draftId, updated_at = :updatedAt "
        + "WHERE draft_id IS NULL AND home_location = :homeLocation"
    )
    void assignUnassigned(String draftId, String homeLocation, long updatedAt);

    @Query(
        "UPDATE media_upload SET draft_id = :draftId, updated_at = :updatedAt "
        + "WHERE media_id = :mediaId AND draft_id IS NULL"
    )
    void assignOneIfUnassigned(String mediaId, String draftId, long updatedAt);

    @Transaction
    default List<String> assignUnassignedAndReturnIds(String draftId, String homeLocation, long updatedAt) {
        List<String> ids = getUnassignedIds(homeLocation);
        assignUnassigned(draftId, homeLocation, updatedAt);
        return ids;
    }

    @Query("SELECT * FROM media_upload WHERE draft_id = :draftId")
    List<MediaUploadEntity> getByDraft(String draftId);

    @Query(
        "SELECT * FROM media_upload WHERE home_location = :homeLocation "
        + "AND state IN ('QUEUED', 'CREATING', 'UPLOADING', 'RETRY_WAIT')"
    )
    List<MediaUploadEntity> getResumable(String homeLocation);

    @Query("SELECT * FROM media_upload WHERE home_location = :homeLocation AND state = 'FINALIZING'")
    List<MediaUploadEntity> getFinalizing(String homeLocation);

    @Nullable
    @Query("SELECT * FROM media_upload WHERE job_id = :jobId")
    MediaUploadEntity getByJobId(int jobId);

    @Query("UPDATE media_upload SET job_id = NULL WHERE media_id = :mediaId AND job_id = :jobId")
    void clearJobId(String mediaId, int jobId);

    @Query("UPDATE media_upload SET work_id = NULL WHERE media_id = :mediaId AND work_id = :workId")
    void clearWorkId(String mediaId, String workId);

    @Query("DELETE FROM media_upload WHERE media_id = :mediaId")
    int delete(String mediaId);

}
