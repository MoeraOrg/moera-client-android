package org.moera.android.js;

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

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.moera.android.BuildConfig;
import org.moera.android.Preferences;
import org.moera.android.R;
import org.moera.android.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class JsInterface {

    private static class MediaStorageException extends Exception {

        public MediaStorageException(String message) {
            super(message);
        }

    }

    private static final String TAG = JsInterface.class.getSimpleName();

    private static final String IMAGE_DIRECTORY = Environment.DIRECTORY_PICTURES + File.separator + "Moera";
    private static final String APP_FLAVOR_GOOGLE_PLAY = "google-play";
    private static final String APP_FLAVOR_APK = "apk";
    private static final int API_VERSION = 2;

    private final Context context;
    private final Settings settings;
    private final JsInterfaceCallback callback;
    private final JsMessages messages;

    public JsInterface(Context context, Settings settings, JsInterfaceCallback callback, JsMessages messages) {
        this.context = context;
        this.settings = settings;
        this.callback = callback;
        this.messages = messages;
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

        if (
            !Objects.equals(url, prevUrl)
            || !Objects.equals(token, prevToken)
            || !Objects.equals(ownerName, prevOwnerName)
        ) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Preferences.HOME_LOCATION, url);
            editor.putString(Preferences.HOME_TOKEN, token);
            editor.putString(Preferences.HOME_OWNER_NAME, ownerName);
            editor.apply();
        }

        callback.updatePushRelay();
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
    }

    @JavascriptInterface
    public void share(String url, String title) {
        String text = StringUtils.isEmpty(title) ? url : title + " " + url;

        Intent sendIntent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text)
                .setType("text/plain");

        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_to)));
    }

    @JavascriptInterface
    public void saveImage(String url, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permittedSaveImageQ(url, mimeType);
        } else if (ActivityCompat.checkSelfPermission(
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
                URL in = new URL(url);
                String fileName = UUID.randomUUID().toString() + "." + getImageExtension(url);
                File directory = Environment.getExternalStoragePublicDirectory(IMAGE_DIRECTORY);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                File file = new File(directory, fileName);
                IOUtils.copy(in, file);

                ContentResolver resolver = context.getContentResolver();

                ContentValues details = new ContentValues();
                details.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                details.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                details.put(MediaStore.Images.Media.DATA, file.getPath());

                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details);

                if (callback != null) {
                    callback.toast(context.getString(R.string.save_image_success));
                }
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Image saving failed", e);
                }
                if (callback != null) {
                    callback.toast(context.getString(R.string.save_image_failure));
                }
            }
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void permittedSaveImageQ(String url, String mimeType) {
        new Thread(() -> {
            try {
                String fileName = UUID.randomUUID().toString() + "." + getImageExtension(url);

                ContentResolver resolver = context.getContentResolver();

                ContentValues details = new ContentValues();
                details.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                details.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                details.put(MediaStore.Images.Media.RELATIVE_PATH, IMAGE_DIRECTORY);
                details.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri imagesCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
                Uri media = resolver.insert(imagesCollection, details);

                URL in = new URL(url);
                if (media == null) {
                    throw new MediaStorageException("resolver.insert() returned null");
                }
                OutputStream out = resolver.openOutputStream(media);
                if (out == null) {
                    throw new MediaStorageException("Cannot open output stream");
                }
                IOUtils.copy(in, out);

                details.clear();
                details.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(media, details, null, null);

                if (callback != null) {
                    callback.toast(context.getString(R.string.save_image_success));
                }
            } catch (IOException | MediaStorageException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Image saving failed", e);
                }
                if (callback != null) {
                    callback.toast(context.getString(R.string.save_image_failure));
                }
            }
        }).start();
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
    public String getFlavor() {
        return APP_FLAVOR_GOOGLE_PLAY;
    }

    @JavascriptInterface
    public int getApiVersion() {
        return API_VERSION;
    }

    @JavascriptInterface
    public void log(String text) {
        Log.i(TAG, text);
    }

    @JavascriptInterface
    public void changeLanguage(String lang) {
        SharedPreferences.Editor prefsEditor = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefsEditor.putString(Preferences.LANG, lang);
        prefsEditor.apply();

        callback.updatePushRelay();
    }

}
