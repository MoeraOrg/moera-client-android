package org.moera.android.media;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import androidx.annotation.RequiresApi;
import org.moera.android.media.database.MediaUploadEntity;

/** Android 14+ user-initiated data-transfer execution for native uploads. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class MediaUploadJobService extends JobService {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<Integer> activeJobs = ConcurrentHashMap.newKeySet();

    @Override
    public boolean onStartJob(JobParameters params) {
        String mediaId = params.getExtras().getString(MediaUploadScheduler.EXTRA_MEDIA_ID);
        if (mediaId == null || mediaId.isBlank()) {
            return false;
        }
        MediaUploadNotifications.createChannel(this);
        String displayName = params.getExtras().getString(MediaUploadScheduler.EXTRA_DISPLAY_NAME);
        long size = params.getExtras().getLong(MediaUploadScheduler.EXTRA_SIZE);
        long confirmedBytes = params.getExtras().getLong(MediaUploadScheduler.EXTRA_CONFIRMED_BYTES);
        setNotification(
            params,
            MediaUploadNotifications.runningNotificationId(mediaId),
            MediaUploadNotifications.buildRunning(this, mediaId, displayName, size, confirmedBytes),
            JOB_END_NOTIFICATION_POLICY_REMOVE
        );
        activeJobs.add(params.getJobId());
        executor.execute(() -> execute(params, mediaId));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        activeJobs.remove(params.getJobId());
        String mediaId = params.getExtras().getString(MediaUploadScheduler.EXTRA_MEDIA_ID);
        if (mediaId == null) {
            return false;
        }
        if (params.getStopReason() == JobParameters.STOP_REASON_USER) {
            executor.execute(() -> MediaUploadOperations.getInstance(this).cancel(mediaId));
            return false;
        }
        executor.execute(() -> MediaUploadOperations.getInstance(this).stopScheduledRun(mediaId));
        return params.getStopReason() != JobParameters.STOP_REASON_CANCELLED_BY_APP;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void execute(JobParameters params, String mediaId) {
        MediaUploadOperations mediaUploadOperations = MediaUploadOperations.getInstance(this);
        MediaUploadEntity upload = mediaUploadOperations.getUpload(mediaId);
        if (upload == null) {
            finish(params, false);
            return;
        }
        if (!activeJobs.contains(params.getJobId())) {
            return;
        }
        MediaUploadNotifications.updateRunning(this, upload);
        MediaUploadListener listener = notificationListener(mediaId);
        mediaUploadOperations.addListener(listener);
        try {
            mediaUploadOperations.runScheduled(mediaId);
        } finally {
            mediaUploadOperations.removeListener(listener);
            boolean reschedule = mediaUploadOperations.isResumable(mediaId);
            if (!reschedule) {
                mediaUploadOperations.clearJobId(mediaId, params.getJobId());
            }
            finish(params, reschedule);
        }
    }

    private void finish(JobParameters params, boolean reschedule) {
        if (activeJobs.remove(params.getJobId())) {
            jobFinished(params, reschedule);
        }
    }

    private MediaUploadListener notificationListener(String mediaId) {
        return new MediaUploadListener() {

            @Override
            public void onState(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.updateRunning(MediaUploadJobService.this, upload);
                }
            }

            @Override
            public void onProgress(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.updateRunning(MediaUploadJobService.this, upload);
                }
            }

            @Override
            public void onCompleted(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.showResult(MediaUploadJobService.this, upload);
                }
            }

            @Override
            public void onFailed(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.showResult(MediaUploadJobService.this, upload);
                }
            }

            @Override
            public void onTransientFailure(String id, String draftId, String code, String message) {
            }

        };
    }

}
