package org.moera.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moera.android.push.PushEventHandler;
import org.moera.android.push.PushWorker;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PushEventHandler.createNotificationChannel(this);

        setContentView(R.layout.activity_main);

        WebView webView = getWebView();

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(webView::reload);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JsInterface(this), "Android");
        webView.getSettings().setDomStorageEnabled(true);
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
                String clientHost = Uri.parse(getString(R.string.web_client_url)).getHost();
                if (request.getUrl().getHost().equalsIgnoreCase(clientHost)) {
                    return false;
                }

                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(MainActivity.this, request.getUrl());

                return true;
            }
        });

        webView.loadUrl(getWebViewUrl());

        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String homePage = prefs.getString(Preferences.HOME_LOCATION, null);
        String homeToken = prefs.getString(Preferences.HOME_TOKEN, null);
        PushWorker.schedule(this, homePage, homeToken, true);
    }

    private String getWebViewUrl() {
        if (Objects.equals(getIntent().getAction(), Intent.ACTION_VIEW)
                && getIntent().getData() != null) {
            Uri.Builder builder = Uri.parse(getString(R.string.web_client_url)).buildUpon();
            Uri intentUri = getIntent().getData();
            return builder.encodedPath(intentUri.getEncodedPath())
                    .encodedQuery(intentUri.getEncodedQuery())
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
