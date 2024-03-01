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

import org.moera.android.api.model.StoryInfo;

import java.util.ArrayList;
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
        Integer smallIcon = null;
        int color = 0xffadb5bd;
        String url = null;
        String markAsReadId = null;

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
                case "markAsReadId":
                    markAsReadId = entry.getValue();
                    break;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xffff6600)
                .setContentText(summary)
                .setLargeIcon(avatarWithIcon(avatar, smallIcon, color))
                .setContentIntent(getTapIntent(url))
                .setCategory(CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        if (markAsReadId != null) {
            builder.addAction(0, getString(R.string.mark_as_read), getMarkAsReadIntent(markAsReadId, tag));
        }
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

    private Bitmap avatarWithIcon(Bitmap avatar, Integer icon, int color) {
        if (avatar == null || icon == null) {
            return avatar;
        }

        int width = avatar.getWidth();
        int height = avatar.getHeight();

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
        radius *= .6;
        drawable.setBounds(Math.round(circleX - radius), Math.round(circleY - radius),
                Math.round(circleX + radius), Math.round(circleY + radius));
        drawable.draw(canvas);

        return bitmap;
    }

    private PendingIntent getTapIntent(String url) {
        Intent intent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setData(Uri.parse(url));
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getMarkAsReadIntent(String id, String tag) {
        Log.i(TAG, "The tag = " + tag);
        Intent intent = new Intent(this, MainReceiver.class)
                .setAction(Actions.ACTION_MARK_AS_READ)
                .setData(Uri.parse(tag))
                .putExtra(Actions.EXTRA_STORY_ID, id);
        return PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private NotificationManagerCompat getNotificationManager() {
        return NotificationManagerCompat.from(this);
    }

}
