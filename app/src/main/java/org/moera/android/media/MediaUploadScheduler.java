package org.moera.android.media;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.PersistableBundle;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.RequiresApi;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import org.moera.android.media.database.MediaUploadEntity;

/** Schedules one durable native upload through the platform-appropriate execution API. */
public class MediaUploadScheduler {

    static final String EXTRA_MEDIA_ID = "mediaId";
    static final String EXTRA_DISPLAY_NAME = "displayName";
    static final String EXTRA_SIZE = "size";
    static final String EXTRA_CONFIRMED_BYTES = "confirmedBytes";
    private static final String WORK_NAME_PREFIX = "media-upload-";

    private final Context context;

    public MediaUploadScheduler(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean schedule(MediaUploadEntity upload) {
        MediaUploadNotifications.createChannel(context);
        return usesJobs(Build.VERSION.SDK_INT) ? scheduleJob(upload) : scheduleWorker(upload);
    }

    public boolean isScheduled(MediaUploadEntity upload) {
        return usesJobs(Build.VERSION.SDK_INT) ? isJobScheduled(upload) : isWorkScheduled(upload);
    }

    public void cancel(MediaUploadEntity upload) {
        if (upload.jobId != null) {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler != null) {
                scheduler.cancel(upload.jobId);
            }
        }
        if (upload.workId != null) {
            try {
                WorkManager.getInstance(context).cancelWorkById(UUID.fromString(upload.workId));
            } catch (IllegalArgumentException ignored) {
                // An invalid legacy scheduler ID should not prevent local upload cleanup.
            }
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static boolean usesJobs(int sdk) {
        return sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    public static int jobId(String mediaId) {
        return 0x60000000 | (mediaId.hashCode() & 0x0fffffff);
    }

    public static String newWorkId() {
        return UUID.randomUUID().toString();
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private boolean scheduleJob(MediaUploadEntity upload) {
        if (upload.jobId == null) {
            return false;
        }

        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_MEDIA_ID, upload.mediaId);
        extras.putString(EXTRA_DISPLAY_NAME, upload.displayName);
        extras.putLong(EXTRA_SIZE, upload.size);
        extras.putLong(EXTRA_CONFIRMED_BYTES, upload.confirmedBytes);
        NetworkRequest network = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        long remaining = Math.max(1, upload.size - upload.confirmedBytes);
        JobInfo job = new JobInfo.Builder(
            upload.jobId,
            new ComponentName(context, MediaUploadJobService.class)
        )
            .setExtras(extras)
            .setRequiredNetwork(network)
            .setEstimatedNetworkBytes(0, remaining)
            .setPersisted(true)
            .setUserInitiated(true)
            .build();

        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        return scheduler != null && scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
    }

    private boolean scheduleWorker(MediaUploadEntity upload) {
        if (upload.workId == null) {
            return false;
        }
        UUID workId;
        try {
            workId = UUID.fromString(upload.workId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        Data input = new Data.Builder()
            .putString(EXTRA_MEDIA_ID, upload.mediaId)
            .build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(MediaUploadWorker.class)
            .setId(workId)
            .setConstraints(constraints)
            .setInputData(input)
            .addTag(WORK_NAME_PREFIX + upload.mediaId)
            .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_PREFIX + upload.mediaId,
            ExistingWorkPolicy.KEEP,
            work
        );
        return true;
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private boolean isJobScheduled(MediaUploadEntity upload) {
        if (upload.jobId == null) {
            return false;
        }
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        return scheduler != null && scheduler.getPendingJob(upload.jobId) != null;
    }

    private boolean isWorkScheduled(MediaUploadEntity upload) {
        if (upload.workId == null) {
            return false;
        }
        try {
            WorkInfo info = WorkManager.getInstance(context)
                .getWorkInfoById(UUID.fromString(upload.workId))
                .get();
            return info != null && !info.getState().isFinished();
        } catch (IllegalArgumentException | InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

}
