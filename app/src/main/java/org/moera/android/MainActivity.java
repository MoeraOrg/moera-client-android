package org.moera.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebMessage;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.moera.android.push.PushEventHandler;
import org.moera.android.push.PushWorker;
import org.moera.android.settings.Settings;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static class WritePermissionCallback implements ActivityResultCallback<Boolean> {

        Runnable runnable;

        public void setRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void onActivityResult(Boolean isGranted) {
            if (isGranted) {
                runnable.run();
            }
        }

    }

    private static class UriCallback {

        protected ValueCallback<Uri[]> callback;

        public void setCallback(ValueCallback<Uri[]> callback) {
            this.callback = callback;
        }

    }

    private static class PickImageCallback extends UriCallback implements ActivityResultCallback<Uri> {

        @Override
        public void onActivityResult(Uri uris) {
            callback.onReceiveValue(new Uri[]{uris});
        }

    }

    private static class PickImagesCallback extends UriCallback implements ActivityResultCallback<List<Uri>> {

        @Override
        public void onActivityResult(List<Uri> uris) {
            Uri[] urisArray;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                urisArray = uris.toArray(Uri[]::new);
            } else {
                urisArray = uris.toArray(new Uri[0]);
            }
            callback.onReceiveValue(urisArray);
        }

    }

    private static final String TAG = MainActivity.class.getSimpleName();

    private ActivityResultLauncher<String> writePermissionLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImagesLauncher;
    private final WritePermissionCallback writePermissionCallback = new WritePermissionCallback();
    private final PickImageCallback pickImageCallback = new PickImageCallback();
    private final PickImagesCallback pickImagesCallback = new PickImagesCallback();
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!loadSettings())
            return;
        initPermissions();
        setContentView(R.layout.activity_main);
        initWebView();
        initPush();
    }

    private boolean loadSettings() {
        try {
            settings = new Settings(this);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Cannot load settings", e);
            }
            finish();
            return false;
        }
        return true;
    }

    private void initPermissions() {
        writePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                writePermissionCallback);
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                pickImageCallback);
        int pickImagesLimit = 20;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2) {
            pickImagesLimit = MediaStore.getPickImagesMaxLimit();
        }
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(pickImagesLimit),
                pickImagesCallback);

        // TODO invoke when the client is connected to home
        initNotificationsPermissions();
    }

    private void initNotificationsPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (!settings.getBool("mobile.notifications.enabled")) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        Instant nextAsk = Instant.ofEpochSecond(prefs.getLong(Preferences.NOTIFICATION_PERMISSION_NEXT_ASK, 0));
        if (nextAsk.isAfter(Instant.now())) {
            return;
        }
        ActivityResultLauncher<String> notificationsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        return;
                    }

                    int delay = prefs.getInt(Preferences.NOTIFICATION_PERMISSION_ASK_DELAY, 0);
                    delay = delay != 0 ? (delay <= 64 ? delay * 2 : delay) : 1;
                    long next = Instant.now().plus(delay, ChronoUnit.DAYS).getEpochSecond();
                    SharedPreferences.Editor editPrefs = prefs.edit();
                    editPrefs.putLong(Preferences.NOTIFICATION_PERMISSION_ASK_DELAY, delay);
                    editPrefs.putLong(Preferences.NOTIFICATION_PERMISSION_NEXT_ASK, next);
                    editPrefs.apply();
                });
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebView webView = getWebView();

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(webView::reload);

        webView.getSettings().setJavaScriptEnabled(true);
        JsInterfaceCallback jsCallback = new JsInterfaceCallback() {

            @Override
            public void onBack() {
                runOnUiThread(MainActivity.this::pressBack);
            }

            @Override
            public String getSharedText() {
                if (!Objects.equals(getIntent().getAction(), Intent.ACTION_SEND)) {
                    return null;
                }
                return getIntent().getStringExtra(Intent.EXTRA_TEXT);
            }

            @Override
            public String getSharedTextType() {
                if (!Objects.equals(getIntent().getAction(), Intent.ACTION_SEND)) {
                    return null;
                }
                return Objects.equals(getIntent().getType(), "text/html") ? "html" : "text";
            }

            @Override
            public void withWritePermission(Runnable runnable) {
                writePermissionCallback.setRunnable(runnable);
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            @Override
            public void toast(String text) {
                runOnUiThread(
                        () -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void setSwipeRefreshEnabled(boolean enabled) {
                runOnUiThread(
                        () -> swipeRefreshLayout.setEnabled(enabled)
                );
            }

        };
        webView.addJavascriptInterface(new JsInterface(this, settings, jsCallback),
                "Android");
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isRedirect()) {
                    return false;
                }
                String clientHost = getWebClientUri().getHost();
                String requestHost = request.getUrl().getHost();
                if (requestHost != null && requestHost.equalsIgnoreCase(clientHost)) {
                    return false;
                }

                try {
                    CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                    customTabsIntent.launchUrl(MainActivity.this, request.getUrl());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, getString(R.string.url_no_handler), Toast.LENGTH_SHORT)
                            .show();
                }

                return true;
            }

        });
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                boolean multi = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                var callback = multi ? pickImagesCallback : pickImageCallback;
                callback.setCallback(filePathCallback);
                var launcher = multi ? pickImagesLauncher : pickImageLauncher;
                launcher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                return true;
            }

        });

        webView.loadUrl(getWebViewUrl());
    }

    private String getWebViewUrl() {
        if (Objects.equals(getIntent().getAction(), Intent.ACTION_VIEW)
                && getIntent().getData() != null) {
            Uri.Builder builder = getWebClientUri().buildUpon();
            Uri intentUri = getIntent().getData();
            return builder.encodedPath(intentUri.getEncodedPath())
                    .encodedQuery(intentUri.getEncodedQuery())
                    .build()
                    .toString();
        }
        if (Objects.equals(getIntent().getAction(), Intent.ACTION_SEND)) {
            SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
            Uri homeUri = Uri.parse(prefs.getString(Preferences.HOME_LOCATION, null));
            String composeUri = homeUri.buildUpon()
                    .appendPath("compose")
                    .build()
                    .toString();
            return getWebClientUri().buildUpon()
                    .appendQueryParameter("href", composeUri)
                    .build()
                    .toString();
        }
        if (getIntent().getData() != null) {
            return getIntent().getData().toString();
        }
        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        return prefs.getString(Preferences.CURRENT_URL, getString(R.string.web_client_url));
    }

    private void initPush() {
        PushEventHandler.createNotificationChannel(this);
        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String homePage = prefs.getString(Preferences.HOME_LOCATION, null);
        String homeToken = prefs.getString(Preferences.HOME_TOKEN, null);
        PushWorker.schedule(this, homePage, homeToken, settings, true);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            pressBack();
            return;
        }

        WebView webView = getWebView();
        try {
            JSONObject message = new JSONObject();
            message.put("source", "moera-android");
            message.put("action", "back");
            webView.postWebMessage(new WebMessage(message.toString()), getWebClientUri());
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building JSON", e);
            }
        }
    }

    private Uri getWebClientUri() {
        return Uri.parse(getString(R.string.web_client_url));
    }

    public void pressBack() {
        WebView webView = getWebView();
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private WebView getWebView() {
        return findViewById(R.id.webView);
    }

}
