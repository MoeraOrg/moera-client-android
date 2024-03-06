package org.moera.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import org.moera.android.operations.StoryOperations;

import java.util.Objects;

public class MainReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Actions.ACTION_MARK_AS_READ)) {
            String storyId = intent.getStringExtra(Actions.EXTRA_STORY_ID);
            String tag = intent.getDataString();
            NotificationManagerCompat.from(context).cancel(tag, 0);
            StoryOperations.storyMarkAsRead(context, storyId);
        }
    }

}
