package org.moera.android;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

public class PushHandler extends Handler {

    public static final String HOME_PAGE = "homePage";

    private static final String TAG = PushHandler.class.getSimpleName();

    public PushHandler(@NonNull Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        Log.i(TAG, "Starting to get notifications from " + msg.getData().getString(HOME_PAGE));
        try {
            while (true) {
                Thread.sleep(5000);
                Log.i(TAG, "Waiting for notifications...");
            }
        } catch (InterruptedException e) {
            // just finish
        }
        Log.i(TAG, "Finishing to get notifications");
    }

}
