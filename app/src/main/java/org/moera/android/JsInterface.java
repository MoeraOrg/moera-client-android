package org.moera.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import org.moera.android.push.PushWorker;

import java.util.Objects;

import static android.content.Context.MODE_PRIVATE;

public class JsInterface {

    private final Context context;

    public JsInterface(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void locationChanged(String url) {
        SharedPreferences.Editor prefs =
                context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.CURRENT_URL, url);
        prefs.apply();
    }

    @JavascriptInterface
    public void connectedToHome(String url, String token, String ownerName) {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);

        String prevUrl = prefs.getString(Preferences.HOME_LOCATION, null);
        String prevToken = prefs.getString(Preferences.HOME_TOKEN, null);
        String prevOwnerName = prefs.getString(Preferences.HOME_OWNER_NAME, null);

        if (Objects.equals(url, prevUrl)
                && Objects.equals(token, prevToken)
                && Objects.equals(ownerName, prevOwnerName)) {
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Preferences.HOME_LOCATION, url);
        editor.putString(Preferences.HOME_TOKEN, token);
        editor.putString(Preferences.HOME_OWNER_NAME, ownerName);
        editor.apply();

        PushWorker.schedule(context, url, token, true);
    }

}
