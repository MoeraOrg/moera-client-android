package org.moera.android.media;

import java.util.Objects;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.moera.android.Actions;
import org.moera.android.MainActivity;
import org.moera.android.MainReceiver;
import org.moera.android.R;
import org.moera.android.media.database.MediaUploadEntity;

/** Builds and manages user-visible native upload notifications. */
public class MediaUploadNotifications {

    private static final String CHANNEL_ID = "org.moera.MediaUploadChannel";
    private static final int PROGRESS_MAX = 1000;
    private static final int RESULT_NOTIFICATION_PREFIX = 0x40000000;
    private static final int RUNNING_NOTIFICATION_PREFIX = 0x50000000;
    private static final int NOTIFICATION_ID_MASK = 0x0fffffff;

    public static void createChannel(Context context) {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(context.getString(R.string.media_upload_channel_name))
            .setDescription(context.getString(R.string.media_upload_channel_description))
            .build();
        NotificationManagerCompat.from(context).createNotificationChannel(channel);
    }

    public static Notification buildRunning(Context context, MediaUploadEntity upload) {
        return buildRunning(
            context, upload.mediaId, upload.displayName, upload.size, upload.confirmedBytes
        );
    }

    public static Notification buildRunning(
        Context context, String mediaId, @Nullable String displayName, long size, long confirmedBytes
    ) {
        String name = sanitizedName(displayName);
        int progress = size > 0
            ? (int) Math.min(PROGRESS_MAX, confirmedBytes * PROGRESS_MAX / size)
            : 0;
        return baseBuilder(context, mediaId, name)
            .setContentText(context.getString(R.string.media_upload_in_progress, name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(PROGRESS_MAX, progress, size <= 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.media_upload_cancel),
                cancelIntent(context, mediaId)
            )
            .build();
    }

    public static Notification buildResult(Context context, MediaUploadEntity upload) {
        String name = sanitizedName(upload.displayName);
        boolean completed = Objects.equals(upload.state, MediaUploadState.COMPLETED.name());
        return baseBuilder(context, upload.mediaId, name)
            .setContentText(context.getString(
                completed ? R.string.media_upload_completed : R.string.media_upload_failed,
                name
            ))
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .build();
    }

    public static void updateRunning(Context context, MediaUploadEntity upload) {
        notify(context, runningNotificationId(upload.mediaId), buildRunning(context, upload));
    }

    public static void showResult(Context context, MediaUploadEntity upload) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(runningNotificationId(upload.mediaId));
        notify(context, resultNotificationId(upload.mediaId), buildResult(context, upload));
    }

    public static void cancel(Context context, String mediaId) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(runningNotificationId(mediaId));
        manager.cancel(resultNotificationId(mediaId));
    }

    public static void cancelNonUploadNotifications(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (!isUploadNotification(notification.getId())) {
                manager.cancel(notification.getTag(), notification.getId());
            }
        }
    }

    public static boolean isUploadNotification(int notificationId) {
        int prefix = notificationId & 0xf0000000;
        return prefix == RESULT_NOTIFICATION_PREFIX || prefix == RUNNING_NOTIFICATION_PREFIX;
    }

    public static int runningNotificationId(String mediaId) {
        return RUNNING_NOTIFICATION_PREFIX | (mediaId.hashCode() & NOTIFICATION_ID_MASK);
    }

    private static int resultNotificationId(String mediaId) {
        return RESULT_NOTIFICATION_PREFIX | (mediaId.hashCode() & NOTIFICATION_ID_MASK);
    }

    private static NotificationCompat.Builder baseBuilder(
        Context context, String mediaId, String name
    ) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setSubText(name)
            .setContentIntent(contentIntent(context, mediaId))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private static PendingIntent contentIntent(Context context, String mediaId) {
        Intent intent = new Intent(context, MainActivity.class)
            .setData(Uri.parse("moera://media-upload/" + mediaId))
            .putExtra(Actions.EXTRA_MEDIA_UPLOAD_ID, mediaId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
            context,
            runningNotificationId(mediaId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent cancelIntent(Context context, String mediaId) {
        Intent intent = new Intent(context, MainReceiver.class)
            .setAction(Actions.ACTION_CANCEL_MEDIA_UPLOAD)
            .setData(Uri.parse("moera://media-upload/cancel/" + mediaId))
            .putExtra(Actions.EXTRA_MEDIA_UPLOAD_ID, mediaId);
        return PendingIntent.getBroadcast(
            context,
            runningNotificationId(mediaId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void notify(Context context, int notificationId, Notification notification) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification);
        } catch (SecurityException ignored) {
            // A denied notification permission must not change durable upload state.
        }
    }

    private static String sanitizedName(@Nullable String displayName) {
        if (displayName == null) {
            return "attachment";
        }
        String sanitized = displayName.replaceAll("[\\p{Cntrl}\\r\\n\\t]+", " ").trim();
        if (sanitized.isEmpty()) {
            return "attachment";
        }
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 79) + "…";
    }

}
