package org.moera.android.js;

import static android.content.Context.MODE_PRIVATE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.moera.android.BuildConfig;
import org.moera.android.Preferences;
import org.moera.android.R;
import org.moera.android.media.MediaUploadOperations;
import org.moera.android.media.WebClientCapabilities;
import org.moera.android.settings.Settings;
import org.moera.android.util.MimeUtil;
import org.json.JSONException;

public class JsInterface {

    private static class MediaStorageException extends Exception {

        public MediaStorageException(String message) {
            super(message);
        }

    }

    private static final String TAG = JsInterface.class.getSimpleName();

    private static final String IMAGE_DIRECTORY = Environment.DIRECTORY_PICTURES + File.separator + "Moera";
    private static final String VIDEO_DIRECTORY = Environment.DIRECTORY_MOVIES + File.separator + "Moera";
    private static final String APP_FLAVOR_GOOGLE_PLAY = "google-play";
    private static final String APP_FLAVOR_APK = "apk";
    private static final int API_VERSION = 3;

    private final Context context;
    private final Settings settings;
    private final JsInterfaceCallback callback;
    private final WebClientCapabilities webClientCapabilities;
    private final MediaUploadOperations mediaUploadOperations;

    public JsInterface(
        Context context,
        Settings settings,
        JsInterfaceCallback callback,
        WebClientCapabilities webClientCapabilities,
        MediaUploadOperations mediaUploadOperations
    ) {
        this.context = context;
        this.settings = settings;
        this.callback = callback;
        this.webClientCapabilities = webClientCapabilities;
        this.mediaUploadOperations = mediaUploadOperations;
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
        // The gallery also calls this bridge method for videos.
        String fileName = MimeUtil.isImageType(mimeType)
            ? UUID.randomUUID() + "." + getImageExtension(url)
            : URLUtil.guessFileName(url, null, MimeUtil.toMediaStoreMimeType(mimeType));
        saveFile(url, fileName, mimeType);
    }

    @JavascriptInterface
    public void saveFile(String url, String fileName, String mimeType) {
        boolean video = MimeUtil.isVideoType(mimeType);
        if (video || MimeUtil.isImageType(mimeType)) {
            saveMedia(url, fileName, mimeType, video);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permittedSaveFileQ(url, fileName, mimeType);
        } else if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
        ) {
            permittedSaveFile(url, fileName, mimeType);
        } else {
            if (callback != null) {
                callback.withWritePermission(() -> permittedSaveFile(url, fileName, mimeType));
            }
        }
    }

    private void saveMedia(String url, String fileName, String mimeType, boolean video) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permittedSaveMediaQ(url, fileName, mimeType, video);
        } else if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
        ) {
            permittedSaveMedia(url, fileName, mimeType, video);
        } else if (callback != null) {
            callback.withWritePermission(() -> permittedSaveMedia(url, fileName, mimeType, video));
        }
    }

    private void permittedSaveMedia(String url, String fileName, String mimeType, boolean video) {
        new Thread(() -> {
            File file = null;
            try {
                URL in = new URL(url);
                File directory = Environment.getExternalStoragePublicDirectory(
                    video ? VIDEO_DIRECTORY : IMAGE_DIRECTORY
                );
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                String name = new File(fileName).getName();
                File candidate = new File(directory, name);
                file = candidate.createNewFile() ? candidate : File.createTempFile("moera-", "-" + name, directory);
                IOUtils.copy(in, file);

                ContentResolver resolver = context.getContentResolver();

                ContentValues details = new ContentValues();
                details.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
                details.put(MediaStore.MediaColumns.MIME_TYPE, MimeUtil.toMediaStoreMimeType(mimeType));
                details.put(MediaStore.MediaColumns.DATA, file.getPath());

                Uri collection = video
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                if (resolver.insert(collection, details) == null) {
                    throw new MediaStorageException("resolver.insert() returned null");
                }

                if (callback != null) {
                    callback.toast(context.getString(video ? R.string.save_video_success : R.string.save_image_success));
                }
            } catch (IOException | MediaStorageException | IllegalArgumentException | SecurityException e) {
                if (file != null && !FileUtils.deleteQuietly(file) && BuildConfig.DEBUG) {
                    Log.e(TAG, "Cannot remove incomplete media: " + file);
                }
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Media saving failed", e);
                }
                if (callback != null) {
                    callback.toast(context.getString(video ? R.string.save_video_failure : R.string.save_image_failure));
                }
            }
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void permittedSaveMediaQ(String url, String fileName, String mimeType, boolean video) {
        new Thread(() -> {
            ContentResolver resolver = context.getContentResolver();
            Uri media = null;
            try {
                String mediaMimeType = MimeUtil.toMediaStoreMimeType(mimeType);
                ContentValues details = new ContentValues();
                details.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                details.put(MediaStore.MediaColumns.MIME_TYPE, mediaMimeType);
                details.put(MediaStore.MediaColumns.RELATIVE_PATH, video ? VIDEO_DIRECTORY : IMAGE_DIRECTORY);
                details.put(MediaStore.MediaColumns.IS_PENDING, 1);

                Uri collection = video
                    ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
                try {
                    media = resolver.insert(collection, details);
                } catch (IllegalArgumentException e) {
                    if (video && !MimeTypeMap.getSingleton().hasMimeType(mediaMimeType)) {
                        // Some Android versions do not register rare video types such as RealMedia.
                        permittedSaveFileQ(url, fileName, mimeType);
                        return;
                    }
                    throw e;
                }

                URL in = new URL(url);
                if (media == null) {
                    throw new MediaStorageException("resolver.insert() returned null");
                }
                try (OutputStream out = resolver.openOutputStream(media)) {
                    if (out == null) {
                        throw new MediaStorageException("Cannot open output stream");
                    }
                    IOUtils.copy(in, out);
                }

                details.clear();
                details.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(media, details, null, null);

                if (callback != null) {
                    callback.toast(context.getString(video ? R.string.save_video_success : R.string.save_image_success));
                }
            } catch (IOException | MediaStorageException | IllegalArgumentException | SecurityException e) {
                if (media != null) {
                    try {
                        resolver.delete(media, null, null);
                    } catch (RuntimeException cleanupException) {
                        if (BuildConfig.DEBUG) {
                            Log.e(TAG, "Cannot remove incomplete media", cleanupException);
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Media saving failed", e);
                }
                if (callback != null) {
                    callback.toast(context.getString(video ? R.string.save_video_failure : R.string.save_image_failure));
                }
            }
        }).start();
    }

    private void permittedSaveFile(String url, String fileName, String mimeType) {
        new Thread(() -> {
            try {
                URL in = new URL(url);
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                File file = new File(directory, fileName);
                IOUtils.copy(in, file);

                if (callback != null) {
                    callback.toast(context.getString(R.string.save_file_success));
                }

                if ("application/pdf".equals(mimeType)) {
                    Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                    openFile(fileUri, mimeType);
                }
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "File saving failed", e);
                }
                if (callback != null) {
                    callback.toast(context.getString(R.string.save_file_failure));
                }
            }
        }).start();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void permittedSaveFileQ(String url, String fileName, String mimeType) {
        new Thread(() -> {
            try {
                ContentResolver resolver = context.getContentResolver();

                ContentValues details = new ContentValues();
                details.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                details.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                details.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                details.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri downloadsCollection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
                Uri media = resolver.insert(downloadsCollection, details);

                URL in = new URL(url);
                if (media == null) {
                    throw new MediaStorageException("resolver.insert() returned null");
                }
                try (OutputStream out = resolver.openOutputStream(media)) {
                    if (out == null) {
                        throw new MediaStorageException("Cannot open output stream");
                    }
                    IOUtils.copy(in, out);
                }

                details.clear();
                details.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(media, details, null, null);

                if (callback != null) {
                    callback.toast(context.getString(R.string.save_file_success));
                }

                if ("application/pdf".equals(mimeType)) {
                    openFile(media, mimeType);
                }
            } catch (IOException | MediaStorageException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "File saving failed", e);
                }
                if (callback != null) {
                    callback.toast(context.getString(R.string.save_file_failure));
                }
            }
        }).start();
    }

    private void openFile(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Cannot open file", e);
            }
        }
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
    public void setWebClientCapabilities(String json) {
        try {
            webClientCapabilities.set(json);
        } catch (JSONException e) {
            webClientCapabilities.reset();
        }
    }

    @JavascriptInterface
    public void startMediaUpload(String id, boolean downsize, String draftId) {
        if (webClientCapabilities.isNativeMediaUploadEnabled()) {
            mediaUploadOperations.start(
                id,
                downsize,
                draftId,
                webClientCapabilities.getClientId(),
                callback::requestUploadNotificationPermission
            );
        }
    }

    @JavascriptInterface
    public void assignMediaUploadsToDraft(String draftId) {
        if (webClientCapabilities.isNativeMediaUploadEnabled()) {
            mediaUploadOperations.assignToDraft(draftId);
        }
    }

    @JavascriptInterface
    public void cancelMediaUpload(String id) {
        mediaUploadOperations.cancel(id);
    }

    @JavascriptInterface
    public void discardSelectedMedia(String id) {
        mediaUploadOperations.discardSelected(id);
    }

    @JavascriptInterface
    public void requestMediaUploadStates() {
        if (webClientCapabilities.isNativeMediaUploadEnabled()) {
            mediaUploadOperations.requestStates();
        }
    }

    @JavascriptInterface
    public void acknowledgeMediaUpload(String id) {
        mediaUploadOperations.acknowledge(id);
    }

    @JavascriptInterface
    public void abandonDraft(String draftId) {
        mediaUploadOperations.abandonDraft(draftId);
    }

    @JavascriptInterface
    public void log(String text) {
        Log.i(TAG, text);
    }

    @JavascriptInterface
    public String getContentUriMimeType(String uriString) {
        try {
            return context.getContentResolver().getType(Uri.parse(uriString));
        } catch (Exception e) {
            return null;
        }
    }

    @JavascriptInterface
    public String getContentUriFileName(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            try (
                Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null
                )
            ) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @JavascriptInterface
    public String readContentUri(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return null;
                }
                byte[] bytes = IOUtils.toByteArray(in);
                return Base64.encodeToString(bytes, Base64.NO_WRAP);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "readContentUri() failed", e);
            }
            return null;
        }
    }

    @JavascriptInterface
    public void changeLanguage(String lang) {
        SharedPreferences.Editor prefsEditor = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefsEditor.putString(Preferences.LANG, lang);
        prefsEditor.apply();

        callback.changeLanguage(lang);
        callback.updatePushRelay();
    }

}
