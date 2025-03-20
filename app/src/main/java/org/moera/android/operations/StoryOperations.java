package org.moera.android.operations;

import android.content.Context;
import android.util.Log;

import org.moera.android.BuildConfig;
import org.moera.android.api.NodeApi;
import org.moera.lib.node.exception.MoeraNodeException;

public class StoryOperations {

    private static final String TAG = StoryOperations.class.getSimpleName();

    public static void storyMarkAsRead(Context context, String storyId) {
        new Thread(() -> {
            NodeApi nodeApi = new NodeApi(context);
            try {
                nodeApi.putStory(storyId, true, true);
            } catch (MoeraNodeException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Node API exception", e);
                }
            }
        }).start();
    }

}
