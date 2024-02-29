package org.moera.android;

import static androidx.core.app.NotificationCompat.CATEGORY_SOCIAL;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.res.ResourcesCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.moera.android.api.model.StoryInfo;

import java.util.List;
import java.util.Map;

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
public class MainMessagingService extends FirebaseMessagingService {

    private static final String TAG = MainMessagingService.class.getSimpleName();
    private static final String CHANNEL_ID = "org.moera.NotificationsChannel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        if (isAppInForeground()) { // TODO check if enabled
            return;
        }

        if (!message.getData().isEmpty()) {
            postNotification(message.getData(), null);
        } else {
            Log.i(TAG, "Message data is empty");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private boolean checkNotificationsPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isAppInForeground() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            if (processInfo.importance ==
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                for (String activeProcess : processInfo.pkgList) {
                    if (activeProcess.equals(getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressLint("DiscouragedApi")
    private void postNotification(Map<String, String> data, Bitmap avatar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !checkNotificationsPermission()) {
            return;
        }

        String summary = "";
        String tag = "";
        int smallIcon = R.drawable.ic_notification;
        int color = 0xffadb5bd;
        String url = null;

        String avatarUrl = data.get("avatarUrl");
        if (avatarUrl != null && avatar == null) {
            if (avatarUrl.equals("")) {
                postNotification(data, getDefaultAvatar());
                return;
            }

            String avatarShape = data.get("avatarShape");
            if (avatarShape == null) {
                avatarShape = "square";
            }
            RequestOptions options;
            if (avatarShape.equals("circle")) {
                options = RequestOptions.circleCropTransform();
            } else {
                options = RequestOptions.bitmapTransform(new RoundedCorners(10));
            }
            Glide.with(this)
                    .load(avatarUrl)
                    .apply(options)
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource,
                                                    @Nullable Transition<? super Drawable> transition) {
                            postNotification(data, drawableToBitmap(resource, getDefaultAvatar()));
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            postNotification(data, getDefaultAvatar());
                        }
                    });
        }

        for (var entry : data.entrySet()) {
            switch (entry.getKey()) {
                case "color":
                    try {
                        color = Color.parseColor(entry.getValue());
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    break;
                case "smallIcon":
                    smallIcon = getResources().getIdentifier(entry.getValue(), "drawable", getPackageName());
                    break;
                case "summary":
                    summary = entry.getValue();
                    break;
                case "tag":
                    tag = entry.getValue();
                    break;
                case "url":
                    url = entry.getValue();
                    break;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setColor(color)
                .setContentText(summary)
                .setLargeIcon(avatar)
                .setContentIntent(getTapIntent(url))
//                .addAction(0, context.getString(R.string.mark_as_read),
//                        getMarkAsReadIntent(story))
                .setCategory(CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = getNotificationManager();
        notificationManager.notify(tag, 0, builder.build());
    }

    private Bitmap getDefaultAvatar() {
        Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.avatar, null);
        return drawableToBitmap(drawable, null);
    }

    private Bitmap drawableToBitmap(Drawable drawable, Bitmap defaultBitmap) {
        return drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap() : defaultBitmap;
    }

    private PendingIntent getTapIntent(String url) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setData(Uri.parse(url));
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private NotificationManagerCompat getNotificationManager() {
        return NotificationManagerCompat.from(this);
    }

}
