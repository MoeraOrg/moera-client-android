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
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.moera.android.activitycontract.PickImage;
import org.moera.android.push.PushEventHandler;
import org.moera.android.push.PushWorker;
import org.moera.android.settings.Settings;

import java.io.IOException;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private class ReadPermissionCallback implements ActivityResultCallback<Boolean> {

        boolean multi;

        public void setMulti(boolean multi) {
            this.multi = multi;
        }

        @Override
        public void onActivityResult(Boolean isGranted) {
            if (isGranted) {
                pickImagesLauncher.launch(multi);
            }
        }

    }

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

    private static class FileChooserCallback implements ActivityResultCallback<Uri[]> {

        private ValueCallback<Uri[]> callback;

        public void setCallback(ValueCallback<Uri[]> callback) {
            this.callback = callback;
        }

        @Override
        public void onActivityResult(Uri[] uris) {
            callback.onReceiveValue(uris);
        }

    }

    private static final String TAG = MainActivity.class.getSimpleName();

    private ActivityResultLauncher<String> readPermissionLauncher;
    private ActivityResultLauncher<String> writePermissionLauncher;
    private ActivityResultLauncher<Boolean> pickImagesLauncher;
    private final ReadPermissionCallback readPermissionCallback = new ReadPermissionCallback();
    private final WritePermissionCallback writePermissionCallback = new WritePermissionCallback();
    private final FileChooserCallback fileChooserCallback = new FileChooserCallback();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Settings settings;
        try {
            settings = new Settings(this);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Cannot load settings", e);
            }
            finish();
            return;
        }

        PushEventHandler.createNotificationChannel(this);
        readPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                readPermissionCallback);
        writePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                writePermissionCallback);
        pickImagesLauncher = registerForActivityResult(
                new PickImage(),
                fileChooserCallback);

        setContentView(R.layout.activity_main);

        WebView webView = getWebView();

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(webView::reload);

        webView.getSettings().setJavaScriptEnabled(true);
        JsInterfaceCallback jsCallback = new JsInterfaceCallback() {

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
                fileChooserCallback.setCallback(filePathCallback);
                boolean multi = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;

                if (ActivityCompat.checkSelfPermission(
                        MainActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    pickImagesLauncher.launch(multi);
                } else {
                    readPermissionCallback.setMulti(multi);
                    readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
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
