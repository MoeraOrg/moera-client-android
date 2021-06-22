package org.moera.android;

import android.content.Context;
import android.util.Log;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;

import org.moera.android.model.PushContent;
import org.moera.android.model.StoryInfo;

import java.util.ArrayList;
import java.util.List;

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
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
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

    private NotificationManagerCompat getNotificationManager() {
        return NotificationManagerCompat.from(context);
    }

    @Override
    public void onComment(String comment) {
    }

    @Override
    public void onError(Throwable t) {
        Log.i(TAG, "Error: " + t.getMessage());
    }

}
