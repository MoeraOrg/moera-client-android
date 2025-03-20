package org.moera.android.operations;

import android.content.Context;

import org.moera.android.api.NodeApi;

public class StoryOperations {

    public static void storyMarkAsRead(Context context, String storyId) {
        new Thread(() -> {
            NodeApi nodeApi = new NodeApi(context);
            nodeApi.putStory(storyId, true, true);
        }).start();
    }

}
