package org.moera.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.os.LocaleListCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.messaging.FirebaseMessaging;

import org.moera.android.api.NodeApi;
import org.moera.android.js.JsInterface;
import org.moera.android.js.JsInterfaceCallback;
import org.moera.android.js.JsMessages;
import org.moera.android.operations.StoryOperations;
import org.moera.android.settings.Settings;
import org.moera.android.util.Consumer;
import org.moera.android.util.Debounced;
import org.moera.lib.node.exception.MoeraNodeException;

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

    private static class NotificationPermissionCallback implements ActivityResultCallback<Boolean> {

        Context context;
        Runnable runnable;

        public NotificationPermissionCallback(Context context) {
            this.context = context;
        }

        public void setRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void onActivityResult(Boolean isGranted) {
            if (isGranted) {
                runnable.run();
                return;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return; // Will never be used in this situation anyway
            }

            SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
            long delay = prefs.getLong(Preferences.NOTIFICATION_PERMISSION_ASK_DELAY, 0);
            delay = delay != 0 ? (delay <= 64 ? delay * 2 : delay) : 1;
            long next = Instant.now().plus(delay, ChronoUnit.DAYS).getEpochSecond();
            SharedPreferences.Editor editPrefs = prefs.edit();
            editPrefs.putLong(Preferences.NOTIFICATION_PERMISSION_ASK_DELAY, delay);
            editPrefs.putLong(Preferences.NOTIFICATION_PERMISSION_NEXT_ASK, next);
            editPrefs.apply();
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
        public void onActivityResult(Uri uri) {
            callback.onReceiveValue(new Uri[]{uri});
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
    private ActivityResultLauncher<String> notificationsPermissionLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImagesLauncher;
    private final WritePermissionCallback writePermissionCallback = new WritePermissionCallback();
    private final NotificationPermissionCallback notificationPermissionCallback
            = new NotificationPermissionCallback(this);
    private final PickImageCallback pickImageCallback = new PickImageCallback();
    private final PickImagesCallback pickImagesCallback = new PickImagesCallback();
    private Settings settings;
    private JsMessages jsMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!loadSettings()) {
            return;
        }
        initLocale();
        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);
        initPermissions();
        markStoryAsRead();
        setContentView(R.layout.activity_main);
        initWebView();
        initConnectivityMonitor();
        initPush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);
        MainMessagingService.cancelAllNotifications(this);
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

    private void initLocale() {
        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String lang = prefs.getString(Preferences.LANG, null);
        if (lang != null) {
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(lang);
            AppCompatDelegate.setApplicationLocales(appLocale);
        }
    }

    private void initPermissions() {
        writePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            writePermissionCallback
        );
        notificationsPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            notificationPermissionCallback
        );
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            pickImageCallback
        );
        int pickImagesLimit = 20;
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
        ) {
            pickImagesLimit = MediaStore.getPickImagesMaxLimit();
        }
        pickImagesLauncher = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(pickImagesLimit),
            pickImagesCallback
        );
    }

    private void markStoryAsRead() {
        if (getIntent().getExtras() != null) {
            String storyId = getIntent().getStringExtra(Actions.EXTRA_STORY_ID);
            if (storyId != null) {
                StoryOperations.storyMarkAsRead(this, storyId);
            }
        }
    }

    private void withNotificationsPermissions(Runnable ifGranted) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ifGranted.run();
            return;
        }
        if (!settings.getBool("mobile.notifications.enabled")) {
            return;
        }
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
        ) {
            ifGranted.run();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        Instant nextAsk = Instant.ofEpochSecond(prefs.getLong(Preferences.NOTIFICATION_PERMISSION_NEXT_ASK, 0));
        if (nextAsk.isAfter(Instant.now())) {
            return;
        }
        notificationPermissionCallback.setRunnable(ifGranted);
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void withFcmRegistrationToken(Consumer<String> callback) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(
            task -> {
                if (!task.isSuccessful()) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    }
                    return;
                }
                String fcmToken = task.getResult();
                if (fcmToken != null) {
                    callback.accept(fcmToken);
                }
            }
        );
    }

    private void registerAtPushRelay(String fcmToken) {
        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String lang = prefs.getString(Preferences.LANG, null);
        String pushRelayClientId = prefs.getString(Preferences.PUSH_RELAY_CLIENT_ID, null);
        String pushRelayLang = prefs.getString(Preferences.PUSH_RELAY_LANG, null);

        if (!Objects.equals(fcmToken, pushRelayClientId) || !Objects.equals(lang, pushRelayLang)) {
            new Thread(() -> {
                NodeApi nodeApi = new NodeApi(this);
                try {
                    nodeApi.registerAtPushRelay(fcmToken, lang);
                } catch (MoeraNodeException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Node API exception", e);
                    }
                }

                SharedPreferences.Editor editPrefs = prefs.edit();
                editPrefs.putString(Preferences.PUSH_RELAY_CLIENT_ID, fcmToken);
                editPrefs.putString(Preferences.PUSH_RELAY_LANG, lang);
                editPrefs.apply();
            }).start();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebView webView = getWebView();

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(webView::reload);

        webView.getSettings().setJavaScriptEnabled(true);
        JsInterfaceCallback jsCallback = new JsInterfaceCallback() {

            @Override
            public void updatePushRelay() {
                runOnUiThread(
                    () -> withNotificationsPermissions(
                        () -> withFcmRegistrationToken(MainActivity.this::registerAtPushRelay)
                    )
                );
            }

            @Override
            public void onBack() {
                runOnUiThread(() -> {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                });
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

            @Override
            public void changeLanguage(String lang) {
                runOnUiThread(
                    () -> {
                        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(lang);
                        AppCompatDelegate.setApplicationLocales(appLocale);
                    }
                );
            }

        };
        jsMessages = new JsMessages(webView, getWebClientUri());
        webView.addJavascriptInterface(
            new JsInterface(this, settings, jsCallback, jsMessages),
            "Android"
        );
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
                    Toast
                        .makeText(MainActivity.this, getString(R.string.url_no_handler), Toast.LENGTH_SHORT)
                        .show();
                }

                return true;
            }

        });
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams
            ) {
                boolean multi = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                var callback = multi ? pickImagesCallback : pickImageCallback;
                callback.setCallback(filePathCallback);
                var launcher = multi ? pickImagesLauncher : pickImageLauncher;
                launcher.launch(
                    new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()
                );
                return true;
            }

        });

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackPressed() {
                jsMessages.back();
            }

        });

        webView.loadUrl(getWebViewUrl());
    }

    private String getWebViewUrl() {
        String webViewUrl;

        String webClientUrl = getString(R.string.web_client_url);
        String webClientDevUrl = getString(R.string.web_client_dev_url);

        if (Objects.equals(getIntent().getAction(), Intent.ACTION_VIEW) && getIntent().getData() != null) {
            Uri.Builder builder = getWebClientUri().buildUpon();
            Uri intentUri = getIntent().getData();
            webViewUrl = builder
                .encodedPath(intentUri.getEncodedPath())
                .encodedQuery(intentUri.getEncodedQuery())
                .build()
                .toString();
        } else if (Objects.equals(getIntent().getAction(), Intent.ACTION_SEND)) {
            SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
            Uri homeUri = Uri.parse(prefs.getString(Preferences.HOME_LOCATION, null));
            String composeUri = homeUri.buildUpon()
                .appendPath("compose")
                .build()
                .toString();
            webViewUrl = getWebClientUri().buildUpon()
                .appendQueryParameter("href", composeUri)
                .build()
                .toString();
        } else if (getIntent().getData() != null) {
            webViewUrl = getIntent().getData().toString();
        } else if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("url")) {
            webViewUrl = getIntent().getExtras().getString("url", "");
        } else {
            SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
            webViewUrl = prefs.getString(Preferences.CURRENT_URL, webClientUrl);
        }

        if (settings.getBool("mobile.developer")) {
            webViewUrl = webViewUrl.replace(webClientUrl, webClientDevUrl);
        } else {
            webViewUrl = webViewUrl.replace(webClientDevUrl, webClientUrl);
        }

        return webViewUrl;
    }

    private void initConnectivityMonitor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        try {
            ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
            connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {

                private final Debounced networkChanged = new Debounced(
                    () -> runOnUiThread(jsMessages::networkChanged),
                    2000
                );

                @Override
                public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
                    networkChanged.execute();
                }

            });
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Cannot register ConnectivityManager callback", e);
            }
        }
    }

    private void initPush() {
        MainMessagingService.cancelAllNotifications(this);
        MainMessagingService.createNotificationChannel(this);
    }

    private Uri getWebClientUri() {
        boolean developer = settings.getBool("mobile.developer");
        return Uri.parse(getString(developer ? R.string.web_client_dev_url : R.string.web_client_url));
    }

    private WebView getWebView() {
        return findViewById(R.id.webView);
    }

}
