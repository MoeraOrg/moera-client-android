package org.moera.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import org.moera.android.api.NodeApi;
import org.moera.android.api.NodeApiException;

import java.util.Objects;

public class MainReceiver extends BroadcastReceiver {

    private static final String TAG = MainReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Actions.ACTION_MARK_AS_READ)) {
            String storyId = intent.getStringExtra(Actions.EXTRA_STORY_ID);
            NotificationManagerCompat.from(context).cancel(storyId, 0);
            new Thread(() -> {
                NodeApi nodeApi = new NodeApi(context);
                try {
                    nodeApi.putStory(storyId, true, true);
                } catch (NodeApiException e) {
                    Log.e(TAG, "Node API exception", e);
                }
            }).start();
        }
    }

}
