package org.moera.android;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.webkit.WebView;

public class MainActivity extends AppCompatActivity {

    public static final String WEB_CLIENT_URL = "https://web.moera.org";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WebView webView = getWebView();
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(WEB_CLIENT_URL);
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
