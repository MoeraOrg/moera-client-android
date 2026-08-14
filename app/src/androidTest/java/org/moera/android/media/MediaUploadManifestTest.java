package org.moera.android.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.impl.foreground.SystemForegroundService;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaUploadManifestTest {

    @Test
    public void durableUploadPermissionsAreDeclared() throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
            context.getPackageName(),
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS)
        );

        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.RECEIVE_BOOT_COMPLETED));
        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.RUN_USER_INITIATED_JOBS));
        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.FOREGROUND_SERVICE));
        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC));
    }

    @Test
    public void uidtServiceIsPrivateAndProtected() throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceInfo info = context.getPackageManager().getServiceInfo(
            new ComponentName(context, MediaUploadJobService.class),
            PackageManager.ComponentInfoFlags.of(0)
        );

        assertFalse(info.exported);
        assertEquals("android.permission.BIND_JOB_SERVICE", info.permission);
    }

    @Test
    public void workManagerForegroundServiceUsesDataSyncType()
        throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceInfo info = context.getPackageManager().getServiceInfo(
            new ComponentName(context, SystemForegroundService.class),
            PackageManager.ComponentInfoFlags.of(0)
        );

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            info.getForegroundServiceType() & ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );
    }

}
