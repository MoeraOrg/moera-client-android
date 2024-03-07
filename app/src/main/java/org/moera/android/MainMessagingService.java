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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
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

import org.moera.android.settings.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
public class MainMessagingService extends FirebaseMessagingService {

    private static final String TAG = MainMessagingService.class.getSimpleName();
    private static final String CHANNEL_ID = "org.moera.NotificationsChannel";
    private static final int MAX_AVATAR_LOAD_ERRORS = 5;
    private Settings settings;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        if (isAppInForeground()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !checkNotificationsPermission()) {
            return;
        }
        if (!loadSettings()) {
            return;
        }

        if (!message.getData().isEmpty()) {
            if (message.getData().size() == 1 && message.getData().containsKey("tag")) {
                cancelNotification(message.getData().get("tag"));
            } else {
                postNotification(message.getData());
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Message data is empty");
            }
        }
    }

    private boolean loadSettings() {
        try {
            settings = new Settings(this);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Cannot load settings", e);
            }
            return false;
        }
        return true;
    }

    public static void createNotificationChannel(Context context) {
        NotificationChannelCompat.Builder builder = new NotificationChannelCompat.Builder(
                CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_name));
        List<NotificationChannelCompat> channels = new ArrayList<>();
        channels.add(builder.build());
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.createNotificationChannelsCompat(channels);
    }

    public static void cancelAllNotifications(Context context) {
        getNotificationManager(context).cancelAll();
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

    private void postNotification(Map<String, String> data) {
        postNotification(data, null, 0);
    }

    private void postNotification(Map<String, String> data, @NonNull Bitmap avatar) {
        postNotification(data, avatar, 0);
    }

    @SuppressLint({"DiscouragedApi", "MissingPermission"})
    private void postNotification(Map<String, String> data, @Nullable Bitmap avatar, int avatarLoadErrors) {
        if (!settings.getBool("mobile.notifications.enabled")) {
            return;
        }

        String summary = "";
        String tag = "";
        Integer smallIcon = null;
        int color = 0xffadb5bd;
        String url = null;
        String markAsReadId = null;

        String avatarUrl = data.get("avatarUrl");
        if (avatarUrl != null && avatar == null) {
            if (avatarUrl.isEmpty()) {
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
                    .asBitmap()
                    .load(avatarUrl)
                    .apply(options)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            postNotification(data, resource);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            if (avatarLoadErrors < MAX_AVATAR_LOAD_ERRORS) {
                                postNotification(data, null, avatarLoadErrors + 1);
                            } else {
                                postNotification(data, getDefaultAvatar());
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
            return;
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
                case "markAsReadId":
                    markAsReadId = entry.getValue();
                    break;
            }
        }

        if (Objects.equals(tag, "news") && !settings.getBool("mobile.notifications.news.enabled")) {
            return;
        }

        Bitmap largeIcon = Objects.equals(tag, "news")
                ? getBitmap(R.drawable.newspaper)
                : avatarWithIcon(avatar, smallIcon, color);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xffff6600)
                .setContentText(summary)
                .setLargeIcon(largeIcon)
                .setContentIntent(getTapIntent(url, markAsReadId))
                .setCategory(CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        if (markAsReadId != null) {
            builder.addAction(0, getString(R.string.mark_as_read), getMarkAsReadIntent(markAsReadId, tag));
        }
        NotificationManagerCompat notificationManager = getNotificationManager();
        notificationManager.notify(tag, 0, builder.build());
    }

    private Bitmap getBitmap(int id) {
        Drawable drawable = ResourcesCompat.getDrawable(getResources(), id, null);
        assert drawable instanceof BitmapDrawable;
        return ((BitmapDrawable) drawable).getBitmap();
    }

    private Bitmap getDefaultAvatar() {
        return getBitmap(R.drawable.avatar);
    }

    private Bitmap avatarWithIcon(Bitmap avatar, Integer icon, int color) {
        if (avatar == null || icon == null) {
            if (BuildConfig.DEBUG) {
                if (avatar == null) {
                    Log.e(TAG, "Avatar is null");
                }
                if (icon == null) {
                    Log.e(TAG, "Icon is null");
                }
            }
            return avatar;
        }

        int width = avatar.getWidth();
        int height = avatar.getHeight();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Avatar dimensions %d x %d", width, height));
        }

        Bitmap bitmap = Bitmap.createBitmap(width * 9 / 8, height * 9 / 8, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(avatar, null, new Rect(0, 0, width, height), null);

        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        float radius = Math.min(width, height) / 4f;
        float circleX = width - radius / 2;
        float circleY = height - radius / 2;
        canvas.drawCircle(circleX, circleY, radius, paint);

        Drawable drawable = ResourcesCompat.getDrawable(getResources(), icon, null);
        if (drawable == null) {
            return avatar;
        }
        ColorFilter colorFilter = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        drawable.setColorFilter(colorFilter);
        radius *= .6f;
        drawable.setBounds(Math.round(circleX - radius), Math.round(circleY - radius),
                Math.round(circleX + radius), Math.round(circleY + radius));
        drawable.draw(canvas);

        return bitmap;
    }

    private PendingIntent getTapIntent(String url, String storyId) {
        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setData(Uri.parse(url))
                .putExtra(Actions.EXTRA_STORY_ID, storyId);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getMarkAsReadIntent(String storyId, String tag) {
        Intent intent = new Intent(this, MainReceiver.class)
                .setAction(Actions.ACTION_MARK_AS_READ)
                .setData(Uri.parse(tag))
                .putExtra(Actions.EXTRA_STORY_ID, storyId);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void cancelNotification(String tag) {
        getNotificationManager().cancel(tag, 0);
    }

    private NotificationManagerCompat getNotificationManager() {
        return getNotificationManager(this);
    }

    private static NotificationManagerCompat getNotificationManager(Context context) {
        return NotificationManagerCompat.from(context);
    }

}
