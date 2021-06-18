package org.moera.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

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
    public void connectedToHome(String url, String token) {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);

        String prevUrl = prefs.getString(Preferences.HOME_LOCATION, null);
        String prevToken = prefs.getString(Preferences.HOME_TOKEN, null);

        if (Objects.equals(url, prevUrl) && Objects.equals(token, prevToken)) {
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Preferences.HOME_LOCATION, url);
        editor.putString(Preferences.HOME_TOKEN, token);
        editor.apply();

        PushWorker.schedule(context, url, token, true);
    }

}
