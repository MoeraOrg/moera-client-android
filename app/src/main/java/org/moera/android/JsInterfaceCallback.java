package org.moera.android;

import android.webkit.JavascriptInterface;

public interface JsInterfaceCallback {

    void onLocationChanged(String location);

    void onBack();

    String getSharedText();

    String getSharedTextType();

}
