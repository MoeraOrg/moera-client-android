package org.moera.android;

import android.util.Log;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;

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
        Log.i(TAG, "Arrived event: " + event);
        Log.i(TAG, "Data: " + messageEvent.getData());
    }

    @Override
    public void onComment(String comment) {
    }

    @Override
    public void onError(Throwable t) {
        Log.i(TAG, "Error: " + t.getMessage());
    }

}
