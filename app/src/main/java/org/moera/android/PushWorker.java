package org.moera.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.launchdarkly.eventsource.ConnectionErrorHandler;
import com.launchdarkly.eventsource.EventSource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.HttpUrl;

import static android.content.Context.MODE_PRIVATE;

public class PushWorker extends Worker {

    public static final String HOME_PAGE = "homePage";
    public static final String HOME_TOKEN = "homeToken";

    private static final String TAG = PushWorker.class.getSimpleName();

    private static Thread thread;
    private static String clientId;

    public PushWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private String getClientId() {
        if (clientId != null) {
            return clientId;
        }

        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        clientId = prefs.getString(Preferences.CLIENT_ID, null);
        if (clientId == null) {
            SharedPreferences.Editor editor = prefs.edit();
            clientId = UUID.randomUUID().toString();
            editor.putString(Preferences.CLIENT_ID, clientId);
            editor.apply();
        }
        return clientId;
    }

    private HttpUrl getPushUrl(String homePage) {
        HttpUrl url = HttpUrl.parse(homePage);
        if (url == null) {
            return null;
        }
        return url
                .newBuilder()
                .addPathSegments("api/push")
                .addPathSegment(getClientId())
                .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        String homePage = getInputData().getString(HOME_PAGE);
        HttpUrl pushUrl = getPushUrl(homePage);
        if (pushUrl == null) {
            Log.i(TAG, "Unknown or malformed URL: " + homePage);
            return Result.failure();
        }
        Log.i(TAG, "Getting notifications from " + pushUrl);

        thread = Thread.currentThread();
        try {
            Headers headers = new Headers.Builder()
                    .add("Authorization",
                            "Bearer " + getInputData().getString(HOME_TOKEN))
                    .build();
            EventSource.Builder eventSourceBuilder =
                    new EventSource.Builder(new PushEventHandler(getApplicationContext()), pushUrl);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                eventSourceBuilder = setEventSourceTimeouts(eventSourceBuilder);
            }
            EventSource eventSource = eventSourceBuilder
                    .headers(headers)
                    .lastEventId(getLastSeenMoment())
                    .connectionErrorHandler(t -> {
                        Log.e(TAG, "Connection error: " + t.getClass().getCanonicalName());
                        return ConnectionErrorHandler.Action.PROCEED;
                    })
                    .build();
            eventSource.start();
            try {
                Thread.sleep(15 * 60 * 1000);
            } catch (InterruptedException e) {
                // just exit
            } finally {
                eventSource.close();
            }
        } finally {
            thread = null;
        }

        return Result.success();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private EventSource.Builder setEventSourceTimeouts(EventSource.Builder eventSourceBuilder) {
        return eventSourceBuilder
                .readTimeout(Duration.ofMinutes(30))
                .reconnectTime(Duration.ofMinutes(30))
                .maxReconnectTime(Duration.ofMinutes(30));
    }

    private String getLastSeenMoment() {
        SharedPreferences prefs =
                getApplicationContext().getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        return prefs.getString(Preferences.LAST_SEEN_MOMENT, "0");
    }

    @Override
    public void onStopped() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    public static void schedule(Context context, String homePage, String homeToken,
                                boolean replace) {
        if (homePage == null || homeToken == null) {
            if (replace) {
                WorkManager.getInstance(context).cancelUniqueWork(TAG);
            }
            return;
        }

        Data data = new Data.Builder()
                .putString(HOME_PAGE, homePage)
                .putString(HOME_TOKEN, homeToken)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(PushWorker.class,
                1, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(data)
                .build();
        ExistingPeriodicWorkPolicy existingPolicy =
                replace ? ExistingPeriodicWorkPolicy.REPLACE : ExistingPeriodicWorkPolicy.KEEP;
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, existingPolicy, workRequest);

    }

}
