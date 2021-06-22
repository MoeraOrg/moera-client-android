package org.moera.android.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;

import org.moera.android.MainActivity;
import org.moera.android.Preferences;
import org.moera.android.R;
import org.moera.android.model.PushContent;
import org.moera.android.model.StoryInfo;
import org.moera.android.model.StoryType;
import org.moera.android.util.NodeLocation;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static androidx.core.app.NotificationCompat.CATEGORY_SOCIAL;

public class PushEventHandler implements EventHandler {

    private static final String TAG = PushEventHandler.class.getSimpleName();

    private static final String CHANNEL_ID = "org.moera.NotificationsChannel";

    private final Context context;
    private long openedAt;

    public PushEventHandler(Context context) {
        this.context = context;
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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
            }
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Error parsing push message: " + messageEvent.getData());
            Log.e(TAG, "Exception:", e);
        }
        storeLastSeenMoment(messageEvent.getLastEventId());
    }

    private void addStory(StoryInfo story) {
        String summary = htmlToPlainText(story.getSummary());

        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(story.getStoryType().getTitle());
        bigTextStyle.bigText(summary);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(story.getStoryType().getTitle())
                .setContentText(summary)
                .setContentIntent(getIntent(story))
                .setCategory(CATEGORY_SOCIAL)
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = getNotificationManager();
        notificationManager.notify(story.getId(), 0, builder.build());
    }

    private void deleteStory(String id) {
        NotificationManagerCompat notificationManager = getNotificationManager();
        notificationManager.cancel(id, 0);
    }

    private String htmlToPlainText(String html) {
        return html.replaceAll("<[^>]+>", "");
    }

    private PendingIntent getIntent(StoryInfo story) {
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

    private NotificationManagerCompat getNotificationManager() {
        return NotificationManagerCompat.from(context);
    }

    private void storeLastSeenMoment(String moment) {
        SharedPreferences.Editor prefs =
                context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.LAST_SEEN_MOMENT, moment);
        prefs.apply();
    }

    @Override
    public void onComment(String comment) {
    }

    @Override
    public void onError(Throwable t) {
        Log.i(TAG, "Error: " + t.getMessage());
    }

}
