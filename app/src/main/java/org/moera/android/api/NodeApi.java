package org.moera.android.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.moera.android.Preferences;
import org.moera.lib.node.MoeraNode;
import org.moera.lib.node.exception.MoeraNodeException;
import org.moera.lib.node.types.PushRelayClientAttributes;
import org.moera.lib.node.types.PushRelayType;
import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.StoryAttributes;
import org.moera.lib.node.types.StoryInfo;

public class NodeApi {

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

    public StoryInfo putStory(String id, Boolean read, Boolean viewed) throws MoeraNodeException {
        StoryAttributes storyAttributes = new StoryAttributes();
        storyAttributes.setRead(read);
        storyAttributes.setViewed(viewed);

        homeNode.token(getHomeToken());
        homeNode.authAdmin();
        return homeNode.updateStory(id, storyAttributes);
    }

    public Result registerAtPushRelay(String clientId, String lang) throws MoeraNodeException {
        PushRelayClientAttributes attributes = new PushRelayClientAttributes();
        attributes.setType(PushRelayType.FCM);
        attributes.setClientId(clientId);
        attributes.setLang(lang);

        homeNode.token(getHomeToken());
        homeNode.authAdmin();
        return homeNode.registerAtPushRelay(attributes);
    }

}
