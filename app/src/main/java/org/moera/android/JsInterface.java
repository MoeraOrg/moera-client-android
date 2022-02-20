package org.moera.android;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.moera.android.push.PushWorker;
import org.moera.android.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class JsInterface {

    private static final String TAG = JsInterface.class.getSimpleName();

    private final Context context;
    private final Settings settings;
    private final JsInterfaceCallback callback;

    public JsInterface(Context context, Settings settings, JsInterfaceCallback callback) {
        this.context = context;
        this.settings = settings;
        this.callback = callback;
    }

    @JavascriptInterface
    public void locationChanged(String url, String location) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.CURRENT_URL, url);
        prefs.apply();
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

        PushWorker.schedule(context, url, token, settings, true);
    }

    @JavascriptInterface
    public String loadSettingsMeta() {
        try {
            return IOUtils.toString(context.getResources().openRawResource(R.raw.settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public String loadSettings() {
        return settings.toString();
    }

    @JavascriptInterface
    public void storeSettings(String data) {
        settings.update(data);

        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String url = prefs.getString(Preferences.HOME_LOCATION, null);
        String token = prefs.getString(Preferences.HOME_TOKEN, null);
        PushWorker.schedule(context, url, token, settings, true);
    }

    @JavascriptInterface
    public void share(String url, String title) {
        String text = StringUtils.isEmpty(title) ? url : title + " " + url;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");

        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_to)));
    }

    @JavascriptInterface
    public void saveImage(String url, String mimeType) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            permittedSaveImage(url, mimeType);
        } else {
            if (callback != null) {
                callback.withWritePermission(() -> permittedSaveImage(url, mimeType));
            }
        }
    }

    private void permittedSaveImage(String url, String mimeType) {
        new Thread(() -> {
            try {
                URL urlU = new URL(url);
                String fileName = UUID.randomUUID().toString() + "." + getImageExtension(url);
                File directory = getImageDirectory();
                if (!directory.exists()) {
                    directory.mkdir();
                }
                File file = new File(directory, fileName);
                IOUtils.copy(urlU, file);

                ContentResolver resolver = context.getContentResolver();

                ContentValues details = new ContentValues();
                details.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                details.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    details.put(MediaStore.Images.Media.DATA, file.getPath());
                }

                Uri imagesCollection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                resolver.insert(imagesCollection, details);

                if (callback != null) {
                    callback.toast(context.getString(R.string.save_image_success));
                }
            } catch (IOException e) {
                Log.e(TAG, "Image saving failed", e);
                if (callback != null) {
                    callback.toast(context.getString(R.string.save_image_failure));
                }
            }
        }).start();
    }

    private static File getImageDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + File.separator
                + "Moera");
    }

    private String getImageExtension(String url) {
        String path = Uri.parse(url).getPath();
        if (path != null) {
            int pos = path.lastIndexOf(".");
            if (pos >= 0 && pos < path.length() - 1) {
                return path.substring(pos + 1);
            }
        }
        return "jpg";
    }

    @JavascriptInterface
    public String getSharedText() {
        return callback != null ? callback.getSharedText() : null;
    }

    @JavascriptInterface
    public String getSharedTextType() {
        return callback != null ? callback.getSharedTextType() : null;
    }

    @JavascriptInterface
    public void back() {
        if (callback != null) {
            callback.onBack();
        }
    }

    @JavascriptInterface
    public void toast(String text) {
        if (callback != null) {
            callback.toast(text);
        }
    }

    @JavascriptInterface
    public void setSwipeRefreshEnabled(boolean enabled) {
        if (callback != null) {
            callback.setSwipeRefreshEnabled(enabled);
        }
    }

    @JavascriptInterface
    public void log(String text) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, text);
        }
    }

}
