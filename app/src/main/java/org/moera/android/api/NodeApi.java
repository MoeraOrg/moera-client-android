package org.moera.android.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.moera.android.BuildConfig;
import org.moera.android.Preferences;
import org.moera.lib.node.MoeraNode;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.PushRelayClientAttributes;
import org.moera.lib.node.types.PushRelayType;
import org.moera.lib.node.types.StoryAttributes;

public class NodeApi {

    private static final String TAG = NodeApi.class.getSimpleName();

    private final Context context;
    private final MoeraNode homeNode;

    public NodeApi(Context context) {
        this.context = context;
        homeNode = new MoeraNode(getHomeLocation());
    }

    private String getHomeLocation() {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, Context.MODE_PRIVATE);
        return prefs.getString(Preferences.HOME_LOCATION, "");
    }

    private String getHomeToken() {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, Context.MODE_PRIVATE);
        return prefs.getString(Preferences.HOME_TOKEN, "");
    }

    public void putStory(String id, Boolean read, Boolean viewed) {
        StoryAttributes storyAttributes = new StoryAttributes();
        storyAttributes.setRead(read);
        storyAttributes.setViewed(viewed);

        homeNode.token(getHomeToken());
        homeNode.authAdmin();
        try {
            homeNode.updateStory(id, storyAttributes);
        } catch (MoeraNodeException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Node API exception", e);
            }
        }
    }

    public void registerAtPushRelay(String clientId, String lang) {
        PushRelayClientAttributes attributes = new PushRelayClientAttributes();
        attributes.setType(PushRelayType.FCM);
        attributes.setClientId(clientId);
        attributes.setLang(lang);

        homeNode.token(getHomeToken());
        homeNode.authAdmin();
        try {
            homeNode.registerAtPushRelay(attributes);
        } catch (MoeraNodeException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Node API exception", e);
            }
        }
    }

}
