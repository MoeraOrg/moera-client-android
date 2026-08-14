package org.moera.android.media.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {SelectedMediaEntity.class, MediaUploadEntity.class},
    version = 1
)
public abstract class MediaDatabase extends RoomDatabase {

    private static volatile MediaDatabase instance;

    public abstract SelectedMediaDao selectedMediaDao();

    public abstract MediaUploadDao mediaUploadDao();

    public static MediaDatabase getInstance(Context context) {
        MediaDatabase result = instance;
        if (result == null) {
            synchronized (MediaDatabase.class) {
                result = instance;
                if (result == null) {
                    result = Room.databaseBuilder(
                        context.getApplicationContext(), MediaDatabase.class, "media-upload.db"
                    ).build();
                    instance = result;
                }
            }
        }
        return result;
    }

}
