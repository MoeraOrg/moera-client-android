package org.moera.android;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.work.Configuration;

/** Application-level configuration shared by background components. */
public class MainApplication extends Application implements Configuration.Provider {

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
            .setJobSchedulerJobIdRange(0, 0x0fffffff)
            .build();
    }

}
