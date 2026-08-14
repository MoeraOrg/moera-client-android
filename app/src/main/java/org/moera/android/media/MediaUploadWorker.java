package org.moera.android.media;

import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.moera.android.media.database.MediaUploadEntity;

/** Android 8-13 foreground WorkManager execution for native uploads. */
public class MediaUploadWorker extends Worker {

    private final String mediaId;

    public MediaUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        mediaId = parameters.getInputData().getString(MediaUploadScheduler.EXTRA_MEDIA_ID);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (mediaId == null || mediaId.isBlank()) {
            return Result.failure();
        }
        MediaUploadOperations mediaUploadOperations = MediaUploadOperations.getInstance(getApplicationContext());
        MediaUploadEntity upload = mediaUploadOperations.getUpload(mediaId);
        if (upload == null) {
            return Result.success();
        }
        MediaUploadNotifications.createChannel(getApplicationContext());
        try {
            setForegroundAsync(foregroundInfo(upload)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (ExecutionException e) {
            return Result.retry();
        }

        MediaUploadListener listener = notificationListener();
        mediaUploadOperations.addListener(listener);
        try {
            mediaUploadOperations.runScheduled(mediaId);
        } finally {
            mediaUploadOperations.removeListener(listener);
        }

        if (mediaUploadOperations.isResumable(mediaId)) {
            return Result.retry();
        }
        mediaUploadOperations.clearWorkId(mediaId, getId().toString());
        return Result.success();
    }

    @Override
    public void onStopped() {
        if (mediaId != null) {
            MediaUploadOperations.getInstance(getApplicationContext()).stopScheduledRun(mediaId);
        }
        super.onStopped();
    }

    private ForegroundInfo foregroundInfo(MediaUploadEntity upload) {
        int foregroundServiceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            : 0;
        return new ForegroundInfo(
            MediaUploadNotifications.runningNotificationId(upload.mediaId),
            MediaUploadNotifications.buildRunning(getApplicationContext(), upload),
            foregroundServiceType
        );
    }

    private MediaUploadListener notificationListener() {
        return new MediaUploadListener() {

            @Override
            public void onState(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.updateRunning(getApplicationContext(), upload);
                }
            }

            @Override
            public void onProgress(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.updateRunning(getApplicationContext(), upload);
                }
            }

            @Override
            public void onCompleted(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.showResult(getApplicationContext(), upload);
                }
            }

            @Override
            public void onFailed(MediaUploadEntity upload) {
                if (mediaId.equals(upload.mediaId)) {
                    MediaUploadNotifications.showResult(getApplicationContext(), upload);
                }
            }

            @Override
            public void onTransientFailure(String id, String draftId, String code, String message) {
            }

        };
    }

}
