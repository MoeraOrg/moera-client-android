package org.moera.android;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
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
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.messaging.FirebaseMessaging;
import org.moera.android.js.JsInterface;
import org.moera.android.js.JsInterfaceCallback;
import org.moera.android.js.JsMessages;
import org.moera.android.operations.PushRelayOperations;
import org.moera.android.operations.StoryOperations;
import org.moera.android.settings.Settings;
import org.moera.android.util.Debounced;
import org.moera.android.util.MimeUtil;
import org.moera.lib.UniversalLocation;

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
        protected JsMessages jsMessages;

        public void setCallback(ValueCallback<Uri[]> callback) {
            this.callback = callback;
        }

        public void setJsMessages(JsMessages jsMessages) {
            this.jsMessages = jsMessages;
        }

    }

    private static class PickFileCallback extends UriCallback implements ActivityResultCallback<Uri> {

        @Override
        public void onActivityResult(Uri uri) {
            if (uri == null) {
                callback.onReceiveValue(null);
                return;
            }
            if (Objects.equals(uri.getScheme(), "content")) {
                callback.onReceiveValue(null);
                jsMessages.contentSelected(List.of(uri.toString()));
            } else {
                callback.onReceiveValue(new Uri[]{uri});
            }
        }

    }

    private static class PickFilesCallback extends UriCallback implements ActivityResultCallback<List<Uri>> {

        @Override
        public void onActivityResult(List<Uri> uris) {
            if (uris == null || uris.isEmpty()) {
                callback.onReceiveValue(null);
                return;
            }

            List<String> contentUris = new ArrayList<>();
            List<Uri> otherUris = new ArrayList<>();
            for (Uri uri : uris) {
                if (Objects.equals(uri.getScheme(), "content")) {
                    contentUris.add(uri.toString());
                } else {
                    otherUris.add(uri);
                }
            }

            if (!contentUris.isEmpty()) {
                jsMessages.contentSelected(contentUris);
            }
            callback.onReceiveValue(otherUris.isEmpty() ? null : otherUris.toArray(new Uri[0]));
        }

    }

    private static final String TAG = MainActivity.class.getSimpleName();

    private ActivityResultLauncher<String> writePermissionLauncher;
    private ActivityResultLauncher<String> notificationsPermissionLauncher;
    private ActivityResultLauncher<String> pickFileLauncher;
    private ActivityResultLauncher<String> pickFilesLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImagesLauncher;
    private final WritePermissionCallback writePermissionCallback = new WritePermissionCallback();
    private final NotificationPermissionCallback notificationPermissionCallback
            = new NotificationPermissionCallback(this);
    private final PickFileCallback pickFileCallback = new PickFileCallback();
    private final PickFilesCallback pickFilesCallback = new PickFilesCallback();
    private Settings settings;
    private JsMessages jsMessages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!loadSettings()) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        initLocale();
        GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(this);
        initPermissions();
        markStoryAsRead();
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.swipeRefreshLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets total = Insets.max(systemBars, cutoutInsets);
            v.setPadding(total.left, total.top, total.right, total.bottom);
            return insets;
        });
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
            pickFileCallback
        );
        pickImagesLauncher = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(getPickImagesLimit()),
            pickFilesCallback
        );
        pickFileLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            pickFileCallback
        );
        pickFilesLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            pickFilesCallback
        );
    }

    private static int getPickImagesLimit() {
        int pickImagesLimit = 20;
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
        ) {
            pickImagesLimit = MediaStore.getPickImagesMaxLimit();
        }
        return pickImagesLimit;
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

    private void registerWithFcm() {
        FirebaseMessaging.getInstance().register().addOnCompleteListener(
            registrationTask -> {
                if (!registrationTask.isSuccessful()) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "FCM registration failed", registrationTask.getException());
                    }
                }
            }
        );
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
                        MainActivity.this::registerWithFcm
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
        webView.addJavascriptInterface(new JsInterface(this, settings, jsCallback), "Android");
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isRedirect()) {
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
                String[] acceptTypes = fileChooserParams.getAcceptTypes();

                var callback = multi ? pickFilesCallback : pickFileCallback;
                callback.setCallback(filePathCallback);
                callback.setJsMessages(jsMessages);

                try {
                    if (MimeUtil.isImagesOnly(acceptTypes)) {
                        var launcher = multi ? pickImagesLauncher : pickImageLauncher;
                        launcher.launch(
                            new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()
                        );
                    } else {
                        var launcher = multi ? pickFilesLauncher : pickFileLauncher;
                        String mimeType = acceptTypes != null && acceptTypes.length > 0 && !acceptTypes[0].isEmpty()
                                ? MimeUtil.extensionToMimeType(acceptTypes[0]) : null;
                        if (mimeType == null) {
                            mimeType = "*/*";
                        }
                        launcher.launch(mimeType);
                    }
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, getString(R.string.url_no_handler), Toast.LENGTH_SHORT).show();
                    filePathCallback.onReceiveValue(null);
                }

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
        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);

        String webViewUrl;

        final String webClientUrl = getString(R.string.web_client_url);
        final String webClientDevUrl = getString(R.string.web_client_dev_url);

        if (Objects.equals(getIntent().getAction(), Intent.ACTION_VIEW) && getIntent().getData() != null) {
            Uri.Builder builder = getWebClientUri().buildUpon();
            Uri intentUri = getIntent().getData();
            webViewUrl = builder
                .encodedPath(intentUri.getEncodedPath())
                .encodedQuery(intentUri.getEncodedQuery())
                .build()
                .toString();
        } else if (Objects.equals(getIntent().getAction(), Intent.ACTION_SEND)) {
            Uri homeUri = Uri.parse(prefs.getString(Preferences.HOME_LOCATION, null));
            String homeOwnerName = prefs.getString(Preferences.HOME_OWNER_NAME, null);
            UniversalLocation uni = new UniversalLocation(
                homeOwnerName, homeUri.getScheme(), homeUri.getAuthority(), "/compose", null, null
            );
            webViewUrl = getWebClientUri().buildUpon()
                .encodedPath(uni.toString())
                .build()
                .toString();
        } else if (getIntent().getData() != null) {
            webViewUrl = getIntent().getData().toString();
        } else if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("url")) {
            webViewUrl = getIntent().getExtras().getString("url", "");
        } else {
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
        PushRelayOperations.refresh(this);
    }

    private Uri getWebClientUri() {
        boolean developer = settings.getBool("mobile.developer");
        return Uri.parse(getString(developer ? R.string.web_client_dev_url : R.string.web_client_url));
    }

    private WebView getWebView() {
        return findViewById(R.id.webView);
    }

}
