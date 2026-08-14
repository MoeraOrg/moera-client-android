package org.moera.android.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaUploadSchedulerTest {

    @Test
    public void uidtIsUsedStartingWithAndroid14() {
        assertFalse(MediaUploadScheduler.usesJobs(26));
        assertFalse(MediaUploadScheduler.usesJobs(33));
        assertTrue(MediaUploadScheduler.usesJobs(34));
        assertTrue(MediaUploadScheduler.usesJobs(36));
    }

    @Test
    public void jobIdIsStableAndStaysInReservedRange() {
        int jobId = MediaUploadScheduler.jobId("media-id");

        assertEquals(jobId, MediaUploadScheduler.jobId("media-id"));
        assertTrue(jobId >= 0x60000000);
        assertTrue(jobId <= 0x6fffffff);
    }

}
