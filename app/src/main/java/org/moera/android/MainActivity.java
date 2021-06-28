package org.moera.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebMessage;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.moera.android.push.PushEventHandler;
import org.moera.android.push.PushWorker;
import org.moera.android.settings.Settings;

import java.io.IOException;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private class PermissionCallback implements ActivityResultCallback<Boolean> {

        private String acceptType;

        public void setAcceptType(String acceptType) {
            this.acceptType = acceptType;
        }

        @Override
        public void onActivityResult(Boolean isGranted) {
            if (isGranted) {
                getContentLauncher.launch(acceptType);
            }
        }

    }

    private static class FileChooserCallback implements ActivityResultCallback<Uri> {

        private ValueCallback<Uri[]> callback;

        public void setCallback(ValueCallback<Uri[]> callback) {
            this.callback = callback;
        }

        @Override
        public void onActivityResult(Uri uri) {
            if (uri != null) {
                callback.onReceiveValue(new Uri[]{uri});
            }
        }

    }

    private static final String TAG = MainActivity.class.getSimpleName();

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> getContentLauncher;
    private final PermissionCallback permissionCallback = new PermissionCallback();
    private final FileChooserCallback fileChooserCallback = new FileChooserCallback();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Settings settings;
        try {
            settings = new Settings(this);
        } catch (IOException e) {
            Log.e(TAG, "Cannot load settings", e);
            finish();
            return;
        }

        PushEventHandler.createNotificationChannel(this);
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                permissionCallback);
        getContentLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                fileChooserCallback);

        setContentView(R.layout.activity_main);

        WebView webView = getWebView();

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(webView::reload);

        webView.getSettings().setJavaScriptEnabled(true);
        JsInterfaceCallback jsCallback = new JsInterfaceCallback() {

            @Override
            public void onLocationChanged(String location) {
                runOnUiThread(
                        () -> swipeRefreshLayout.setEnabled(swipeRefreshEnabled(location))
                );
            }

            @Override
            public void onBack() {
                runOnUiThread(
                        () -> pressBack()
                );
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
                return getIntent().getType().equals("text/html") ? "html" : "text";
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
                if (request.getUrl().getHost().equalsIgnoreCase(clientHost)) {
                    return false;
                }

                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(MainActivity.this, request.getUrl());

                return true;
            }

        });
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                String acceptType = acceptTypes != null && acceptTypes.length > 0
                        ? acceptTypes[0] : null;
                if (StringUtils.isEmpty(acceptType)) {
                    acceptType = "*/*";
                }
                fileChooserCallback.setCallback(filePathCallback);

                if (ContextCompat.checkSelfPermission(
                        MainActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    getContentLauncher.launch(acceptType);
                } else {
                    permissionCallback.setAcceptType(acceptType);
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                return true;
            }

        });

        webView.loadUrl(getWebViewUrl());

        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String homePage = prefs.getString(Preferences.HOME_LOCATION, null);
        String homeToken = prefs.getString(Preferences.HOME_TOKEN, null);
        PushWorker.schedule(this, homePage, homeToken, settings, true);
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
            Log.e(TAG, "Error building JSON", e);
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

    private boolean swipeRefreshEnabled(String location) {
        return !location.startsWith("/profile") && !location.startsWith("/settings");
    }

}
