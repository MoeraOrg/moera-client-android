package org.moera.android.operations;

import android.content.Context;
import android.content.SharedPreferences;

import org.moera.android.Preferences;
import org.moera.android.api.NodeApi;

public class PushRelayOperations {

    private static final long REFRESH_INTERVAL = 7 * 24 * 60 * 60; // 7 days in seconds

    public static void registerNow(Context context, String clientId) {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, Context.MODE_PRIVATE);
        String homeLocation = prefs.getString(Preferences.HOME_LOCATION, null);
        if (homeLocation == null) {
            return;
        }
        String lang = prefs.getString(Preferences.LANG, null);
        registerNow(context, clientId, lang, prefs);
    }

    public static void refresh(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, Context.MODE_PRIVATE);
        long updatedAt = prefs.getLong(Preferences.PUSH_RELAY_UPDATED_AT, 0);
        if (System.currentTimeMillis() / 1000 - updatedAt < REFRESH_INTERVAL) {
            return;
        }
        String homeLocation = prefs.getString(Preferences.HOME_LOCATION, null);
        String clientId = prefs.getString(Preferences.PUSH_RELAY_CLIENT_ID, null);
        if (homeLocation == null || clientId == null) {
            return;
        }
        String lang = prefs.getString(Preferences.PUSH_RELAY_LANG, null);
        Context applicationContext = context.getApplicationContext();
        new Thread(() -> registerNow(applicationContext, clientId, lang, prefs)).start();
    }

    private static void registerNow(
        Context context, String clientId, String lang, SharedPreferences prefs
    ) {
        NodeApi nodeApi = new NodeApi(context);
        nodeApi.registerAtPushRelay(clientId, lang);

        SharedPreferences.Editor editPrefs = prefs.edit();
        String homeLocation = prefs.getString(Preferences.HOME_LOCATION, null);
        editPrefs.putString(Preferences.PUSH_RELAY_HOME_LOCATION, homeLocation);
        editPrefs.putString(Preferences.PUSH_RELAY_CLIENT_ID, clientId);
        editPrefs.putString(Preferences.PUSH_RELAY_LANG, lang);
        editPrefs.putLong(Preferences.PUSH_RELAY_UPDATED_AT, System.currentTimeMillis() / 1000);
        editPrefs.apply();
    }

}
