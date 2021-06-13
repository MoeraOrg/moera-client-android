package org.moera.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;

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
    public void connectedToHome(String url) {
        Log.i(JsInterface.class.getSimpleName(), "Home: " + url);

        SharedPreferences.Editor prefs =
                context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.HOME_LOCATION, url);
        prefs.apply();

        Intent intent = new Intent(context, PushService.class);
        intent.setData(Uri.parse(url));
        context.startService(intent);
    }

}
