package org.moera.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.apache.commons.lang3.StringUtils;
import org.moera.android.push.PushWorker;

import java.util.Objects;

import static android.content.Context.MODE_PRIVATE;

public class JsInterface {

    private static final String TAG = JsInterface.class.getSimpleName();

    private final Context context;
    private final JsInterfaceCallback callback;

    public JsInterface(Context context, JsInterfaceCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    @JavascriptInterface
    public void locationChanged(String url, String location) {
        SharedPreferences.Editor prefs =
                context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.CURRENT_URL, url);
        prefs.apply();

        if (callback != null) {
            callback.onLocationChanged(location);
        }
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

    @JavascriptInterface
    public void share(String url, String title) {
        String text = StringUtils.isEmpty(title) ? url : title + " " + url;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");

        context.startActivity(Intent.createChooser(sendIntent, "Share to..."));
    }

    @JavascriptInterface
    public void back() {
        if (callback != null) {
            callback.onBack();
        }
    }

    @JavascriptInterface
    public void log(String text) {
        Log.i(TAG, text);
    }

}
