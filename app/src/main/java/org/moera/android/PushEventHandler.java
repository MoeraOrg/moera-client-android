package org.moera.android;

import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;

import org.moera.android.model.PushContent;

public class PushEventHandler implements EventHandler {

    private static final String TAG = PushEventHandler.class.getSimpleName();

    private long openedAt;

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
                    Log.i(TAG, "Story added: " + pushContent.getStory().getSummary());
                    break;
                case STORY_DELETED:
                    Log.i(TAG, "Story deleted: " + pushContent.getId());
                    break;
            }
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Error parsing push message: " + messageEvent.getData());
            Log.e(TAG, "Exception:", e);
        }
    }

    @Override
    public void onComment(String comment) {
    }

    @Override
    public void onError(Throwable t) {
        Log.i(TAG, "Error: " + t.getMessage());
    }

}
