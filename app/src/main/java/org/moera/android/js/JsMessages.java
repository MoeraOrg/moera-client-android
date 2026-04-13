package org.moera.android.js;

import java.util.Collection;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebMessage;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.moera.android.BuildConfig;

public class JsMessages {

    private interface MessageInitializer {

        void initialize(JSONObject message) throws JSONException;

    }

    private static final String TAG = JsMessages.class.getSimpleName();

    private final WebView webView;
    private final Uri webClientUri;

    public JsMessages(WebView webView, Uri webClientUri) {
        this.webView = webView;
        this.webClientUri = webClientUri;
    }

    private void sendMessage(MessageInitializer initializer) {
        try {
            JSONObject message = new JSONObject();
            message.put("source", "moera-android");
            initializer.initialize(message);
            webView.postWebMessage(new WebMessage(message.toString()), webClientUri);
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building JSON", e);
            }
        }
    }

    public void back() {
        sendMessage(message -> {
            message.put("action", "back");
        });
    }

    public void callReturn(int callId, Object value) {
        sendMessage(message -> {
            message.put("action", "call-return");
            message.put("callId", callId);
            message.put("value", value);
        });
    }

    public void networkChanged() {
        sendMessage(message -> {
            message.put("action", "network-changed");
        });
    }

    public void contentSelected(Collection<String> uris) {
        sendMessage(message -> {
            message.put("action", "content-selected");
            message.put("uris", new JSONArray(uris));
        });
    }

}
