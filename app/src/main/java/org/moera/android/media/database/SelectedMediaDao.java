package org.moera.android.media.database;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SelectedMediaDao {

    @Insert
    void insert(SelectedMediaEntity media);

    @Nullable
    @Query("SELECT * FROM selected_media WHERE id = :id")
    SelectedMediaEntity get(String id);

    @Query(
        "SELECT * FROM selected_media AS selected "
        + "WHERE created_at < :timestamp "
        + "AND NOT EXISTS (SELECT 1 FROM media_upload WHERE media_id = selected.id)"
    )
    List<SelectedMediaEntity> getOlderThan(long timestamp);

    @Query("DELETE FROM selected_media WHERE id = :id")
    int delete(String id);

}
