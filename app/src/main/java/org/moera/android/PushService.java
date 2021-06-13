package org.moera.android;

import android.app.Service;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;

import java.util.Objects;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

public class PushService extends Service {

    private HandlerThread serviceThread;
    private PushHandler serviceHandler;

    private String homePage;

    public PushService() {
    }

    @Override
    public void onCreate() {
        serviceThread = new HandlerThread("MoeraPushService",
                THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();

        serviceHandler = new PushHandler(serviceThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String newHomePage = intent.getData() != null ? intent.getData().toString() : null;
        if (!Objects.equals(homePage, newHomePage)) {
            if (homePage != null) {
                serviceThread.interrupt();
            }
            homePage = newHomePage;
            if (homePage != null) {
                Message msg = serviceHandler.obtainMessage();
                msg.getData().putString(PushHandler.HOME_PAGE, homePage);
                serviceHandler.sendMessage(msg);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
