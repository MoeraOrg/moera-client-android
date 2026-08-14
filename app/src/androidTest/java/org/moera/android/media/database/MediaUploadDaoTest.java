package org.moera.android.media.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.moera.android.media.MediaUploadState;

@RunWith(AndroidJUnit4.class)
public class MediaUploadDaoTest {

    private Context context;
    private MediaDatabase database;
    private MediaUploadDao dao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase.class)
            .allowMainThreadQueries()
            .build();
        dao = database.mediaUploadDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void assignmentFillsOnlyNullDraftsAndReturnsAffectedIds() {
        dao.insert(upload("one", null));
        dao.insert(upload("two", "other-draft"));
        dao.insert(upload("three", null, "https://another-home/moera"));

        List<String> assigned = dao.assignUnassignedAndReturnIds("new-draft", "https://home/moera", 10);

        assertEquals(List.of("one"), assigned);
        assertEquals("new-draft", dao.get("one").draftId);
        assertEquals("other-draft", dao.get("two").draftId);
        assertNull(dao.get("three").draftId);
    }

    @Test
    public void uploadAndSelectionRowsHaveIndependentLifetimes() {
        SelectedMediaDao selectedMediaDao = database.selectedMediaDao();
        selectedMediaDao.insert(new SelectedMediaEntity(
            "one", "content://provider/one", "file.bin", "application/octet-stream", 12, null, 1, true
        ));
        dao.insert(upload("one", null));

        selectedMediaDao.delete("one");

        assertNull(selectedMediaDao.get("one"));
        assertEquals("one", dao.get("one").mediaId);
    }

    @Test
    public void selectionCleanupDoesNotExpireAnActiveUploadSource() {
        SelectedMediaDao selectedMediaDao = database.selectedMediaDao();
        selectedMediaDao.insert(new SelectedMediaEntity(
            "one", "content://provider/one", "file.bin", "application/octet-stream", 12, null, 1, true
        ));
        selectedMediaDao.insert(new SelectedMediaEntity(
            "two", "content://provider/two", "unused.bin", "application/octet-stream", 12, null, 1, true
        ));
        dao.insert(upload("one", null));

        List<SelectedMediaEntity> expired = selectedMediaDao.getOlderThan(2);

        assertEquals(1, expired.size());
        assertEquals("two", expired.get(0).id);
    }

    @Test
    public void startupStatesSeparateResumableAndAmbiguousFinalization() {
        MediaUploadEntity queued = upload("queued", null);
        MediaUploadEntity uploading = upload("uploading", null);
        uploading.state = MediaUploadState.UPLOADING.name();
        MediaUploadEntity finalizing = upload("finalizing", null);
        finalizing.state = MediaUploadState.FINALIZING.name();
        MediaUploadEntity completed = upload("completed", null);
        completed.state = MediaUploadState.COMPLETED.name();
        dao.insert(queued);
        dao.insert(uploading);
        dao.insert(finalizing);
        dao.insert(completed);

        List<MediaUploadEntity> resumable = dao.getResumable("https://home/moera");
        List<MediaUploadEntity> ambiguous = dao.getFinalizing("https://home/moera");

        assertEquals(
            Set.of("queued", "uploading"),
            resumable.stream().map(row -> row.mediaId).collect(Collectors.toSet())
        );
        assertEquals(
            Set.of("finalizing"),
            ambiguous.stream().map(row -> row.mediaId).collect(Collectors.toSet())
        );
    }

    @Test
    public void schedulerIdsAreClearedOnlyByMatchingExecution() {
        MediaUploadEntity upload = upload("one", null);
        upload.jobId = 123;
        upload.workId = "work-one";
        dao.insert(upload);

        assertEquals("one", dao.getByJobId(123).mediaId);
        dao.clearJobId("one", 456);
        dao.clearWorkId("one", "work-two");
        assertEquals(Integer.valueOf(123), dao.get("one").jobId);
        assertEquals("work-one", dao.get("one").workId);

        dao.clearJobId("one", 123);
        dao.clearWorkId("one", "work-one");
        assertNull(dao.get("one").jobId);
        assertNull(dao.get("one").workId);
    }

    @Test
    public void resumableCheckpointSurvivesDatabaseReopen() {
        String databaseName = "media-upload-restart-test.db";
        context.deleteDatabase(databaseName);
        MediaDatabase persistent = null;
        try {
            persistent = Room.databaseBuilder(context, MediaDatabase.class, databaseName)
                .allowMainThreadQueries()
                .build();
            MediaUploadEntity upload = upload("one", "draft");
            upload.state = MediaUploadState.UPLOADING.name();
            upload.serverUploadId = "server-upload";
            upload.uploadedChunksJson = "[0,1]";
            upload.confirmedBytes = 8;
            upload.jobId = 123;
            persistent.mediaUploadDao().insert(upload);
            persistent.close();

            persistent = Room.databaseBuilder(context, MediaDatabase.class, databaseName)
                .allowMainThreadQueries()
                .build();
            MediaUploadEntity restored = persistent.mediaUploadDao().get("one");

            assertEquals(MediaUploadState.UPLOADING.name(), restored.state);
            assertEquals("server-upload", restored.serverUploadId);
            assertEquals("[0,1]", restored.uploadedChunksJson);
            assertEquals(8, restored.confirmedBytes);
            assertEquals(Integer.valueOf(123), restored.jobId);
        } finally {
            if (persistent != null && persistent.isOpen()) {
                persistent.close();
            }
            context.deleteDatabase(databaseName);
        }
    }

    private static MediaUploadEntity upload(String id, String draftId) {
        return upload(id, draftId, "https://home/moera");
    }

    private static MediaUploadEntity upload(String id, String draftId, String homeLocation) {
        return new MediaUploadEntity(
            id,
            draftId,
            homeLocation,
            "client",
            "file.bin",
            "application/octet-stream",
            12,
            null,
            MediaUploadState.QUEUED.name(),
            false,
            1,
            1
        );
    }

}
