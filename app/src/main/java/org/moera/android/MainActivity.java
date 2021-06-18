package org.moera.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String WEB_CLIENT_URL = "https://web.moera.org";
    public static final String CHANNEL_ID = "org.moera.NotificationsChannel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createNotificationChannel();

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

        });

        SharedPreferences prefs = getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);

        String url = prefs.getString(Preferences.CURRENT_URL, WEB_CLIENT_URL);
        webView.loadUrl(url);

        String homePage = prefs.getString(Preferences.HOME_LOCATION, null);
        String homeToken = prefs.getString(Preferences.HOME_TOKEN, null);
        PushWorker.schedule(this, homePage, homeToken, true); // FIXME replace
    }

    @Override
    protected void onResume() {
        super.onResume();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Test")
                .setContentText("Hi!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(0, builder.build());
    }

    private void createNotificationChannel() {
        NotificationChannelCompat.Builder builder = new NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(getString(R.string.channel_name));
        List<NotificationChannelCompat> channels = new ArrayList<>();
        channels.add(builder.build());
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.createNotificationChannelsCompat(channels);
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
