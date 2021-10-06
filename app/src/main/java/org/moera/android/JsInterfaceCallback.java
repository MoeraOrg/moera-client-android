package org.moera.android;

public interface JsInterfaceCallback {

    void onLocationChanged(String location);

    void onBack();

    String getSharedText();

    String getSharedTextType();

}
