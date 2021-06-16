package org.moera.android;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class PushWorker extends Worker {

    public static final String HOME_PAGE = "homePage";

    private static final String TAG = PushWorker.class.getSimpleName();

    private static Thread thread;

    public PushWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Getting notifications from " + getInputData().getString(HOME_PAGE));

        thread = Thread.currentThread();
        int i = 0;
        try {
            while (!isStopped()) {
                Log.i(TAG, "Passed " + (i++) + " minutes");
                Thread.sleep(60000);
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Interrupted!");
            // just exit
        }
        thread = null;

        return Result.success();
    }

    @Override
    public void onStopped() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    public static void schedule(Context context, String homePage, boolean replace) {
        if (homePage == null) {
            if (replace) {
                WorkManager.getInstance(context).cancelUniqueWork(TAG);
            }
            return;
        }

        Data data = new Data.Builder()
                .putString(HOME_PAGE, homePage)
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
