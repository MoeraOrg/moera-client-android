package org.moera.android.push;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.res.ResourcesCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.moera.android.Actions;
import org.moera.android.MainActivity;
import org.moera.android.MainReceiver;
import org.moera.android.Preferences;
import org.moera.android.R;
import org.moera.android.api.model.FeedWithStatus;
import org.moera.android.api.model.PushContent;
import org.moera.android.api.model.StoryInfo;
import org.moera.android.api.model.StoryType;
import org.moera.android.util.NodeLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static android.content.Context.MODE_PRIVATE;
import static androidx.core.app.NotificationCompat.CATEGORY_SOCIAL;

public class PushEventHandler implements EventHandler {

    private static final String TAG = PushEventHandler.class.getSimpleName();

    private static final String CHANNEL_ID = "org.moera.NotificationsChannel";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Context context;
    private final boolean enabled;
    private final boolean newsEnabled;
    private long openedAt;

    public PushEventHandler(Context context, boolean enabled, boolean newsEnabled) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.context = context;
        this.enabled = enabled;
        this.newsEnabled = newsEnabled;
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

    @Override
    public void onOpen() {
        Log.i(TAG, "Opened channel");
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void onClosed() {
        long time = (System.currentTimeMillis() - openedAt) / 1000;
        Log.i(TAG, "Closed channel after " + time + "s");
    }

    @Override
    public void onMessage(String event, MessageEvent messageEvent) {
        try {
            PushContent pushContent = objectMapper.readValue(messageEvent.getData(),
                    PushContent.class);
            switch (pushContent.getType()) {
                case STORY_ADDED:
                    addStory(pushContent.getStory());
                    break;
                case STORY_DELETED:
                    deleteStory(pushContent.getId());
                    break;
                case FEED_UPDATED:
                    displayFeedStatus(pushContent.getFeedStatus());
                    break;
            }
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Error parsing push message: " + messageEvent.getData());
            Log.e(TAG, "Exception:", e);
        }
        storeLastSeenMoment(messageEvent.getLastEventId());
    }

    private void addStory(StoryInfo story) {
        if (isAppInForeground() || !enabled) {
            return;
        }

        Bitmap defaultAvatar = getAvatar();
        if (story.getSummaryAvatar() == null
                || StringUtils.isEmpty(story.getSummaryAvatar().getPath())) {
            addStory(story, defaultAvatar);
        }

        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String homeRootPage = prefs.getString(Preferences.HOME_LOCATION, "");
        Uri avatarUri = Uri.parse(homeRootPage).buildUpon()
                .appendPath("media")
                .appendEncodedPath(story.getSummaryAvatar().getPath())
                .build();
        RequestOptions options;
        if (Objects.equals(story.getSummaryAvatar().getShape(), "circle")) {
            options = RequestOptions.centerCropTransform();
        } else {
            options = RequestOptions.bitmapTransform(new RoundedCorners(10));
        }
        Glide.with(context)
                .load(avatarUri)
                .apply(options)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource,
                                                @Nullable Transition<? super Drawable> transition) {
                        addStory(story, drawableToBitmap(resource, defaultAvatar));
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        addStory(story, defaultAvatar);
                    }
                });
    }

    private void addStory(StoryInfo story, Bitmap avatar) {
        String summary = htmlToPlainText(story.getSummary());

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(story.getStoryType().getTitle());
        bigTextStyle.bigText(summary);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(story.getStoryType().getTitle())
                .setContentText(summary)
                .setLargeIcon(avatar)
                .setContentIntent(getStoryTapIntent(story))
                .addAction(0, context.getString(R.string.mark_as_read),
                        getMarkAsReadIntent(story))
                .setCategory(CATEGORY_SOCIAL)
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = getNotificationManager();
        notificationManager.notify(story.getId(), 0, builder.build());
    }

    private Bitmap getAvatar() {
        Resources res = context.getResources();
        Drawable drawable = ResourcesCompat.getDrawable(res, R.drawable.avatar, null);
        return drawableToBitmap(drawable, null);
    }

    private Bitmap drawableToBitmap(Drawable drawable, Bitmap defaultBitmap) {
        return drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap() : defaultBitmap;
    }

    private String htmlToPlainText(String html) {
        String text = html.replaceAll("<[^>]+>", "");
        return StringEscapeUtils.unescapeHtml4(text);
    }

    private PendingIntent getStoryTapIntent(StoryInfo story) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setData(getTarget(story));
        return PendingIntent.getActivity(context, 0, intent, 0);
    }

    private Uri getTarget(StoryInfo story) {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String homeRootPage = prefs.getString(Preferences.HOME_LOCATION, "");
        String homeOwnerName = prefs.getString(Preferences.HOME_OWNER_NAME, "");

        NodeLocation targetLocation = StoryType.getTarget(story);
        String nodeName = targetLocation.getNodeName().equals(":")
                ? homeOwnerName : targetLocation.getNodeName();

        return Uri.parse(homeRootPage).buildUpon()
                .appendPath("gotoname")
                .appendQueryParameter("client", context.getString(R.string.web_client_url))
                .appendQueryParameter("name", nodeName)
                .appendQueryParameter("location", targetLocation.getHref())
                .appendQueryParameter("trackingId", story.getTrackingId())
                .build();
    }

    private PendingIntent getMarkAsReadIntent(StoryInfo story) {
        Intent intent = new Intent(context, MainReceiver.class);
        intent.setAction(Actions.ACTION_MARK_AS_READ);
        intent.putExtra(Actions.EXTRA_STORY_ID, story.getId());
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void deleteStory(String id) {
        NotificationManagerCompat notificationManager = getNotificationManager();
        notificationManager.cancel(id, 0);
    }

    private void displayFeedStatus(FeedWithStatus feedStatus) {
        if (isAppInForeground() || !enabled || !newsEnabled) {
            return;
        }
        if (!feedStatus.getFeedName().equalsIgnoreCase("news")) {
            return;
        }

        NotificationManagerCompat notificationManager = getNotificationManager();

        if (feedStatus.getNotViewed() <= 0) {
            notificationManager.cancel(null, 1);
            return;
        }

        String summary;
        if (feedStatus.getNotViewed() == 1) {
            summary = "You have a new post in your News feed";
        } else {
            summary = String.format(Locale.ENGLISH,
                    "You have %d new posts in your News feed", feedStatus.getNotViewed());
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentText(summary)
                .setContentIntent(getFeedTapIntent())
                .setCategory(CATEGORY_SOCIAL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        notificationManager.notify(null, 1, builder.build());
    }

    private PendingIntent getFeedTapIntent() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setData(getNewsFeed());
        return PendingIntent.getActivity(context, 0, intent, 0);
    }

    private Uri getNewsFeed() {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        Uri homeUri = Uri.parse(prefs.getString(Preferences.HOME_LOCATION, null));
        String composeUri = homeUri.buildUpon()
                .appendPath("news")
                .build()
                .toString();
        return getWebClientUri().buildUpon()
                .appendQueryParameter("href", composeUri)
                .build();
    }

    private Uri getWebClientUri() {
        return Uri.parse(context.getString(R.string.web_client_url));
    }

    private NotificationManagerCompat getNotificationManager() {
        return NotificationManagerCompat.from(context);
    }

    private void storeLastSeenMoment(String moment) {
        SharedPreferences.Editor prefs =
                context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.LAST_SEEN_MOMENT, moment);
        prefs.apply();
    }

    private boolean isAppInForeground() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            if (processInfo.importance ==
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                for (String activeProcess : processInfo.pkgList) {
                    if (activeProcess.equals(context.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onComment(String comment) {
    }

    @Override
    public void onError(Throwable t) {
        Log.i(TAG, "Error: " + t.getMessage());
    }

}
