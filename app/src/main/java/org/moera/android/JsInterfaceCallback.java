package org.moera.android;

public interface JsInterfaceCallback {

    void onLocationChanged(String location);

    void onBack();

    String getSharedText();

    String getSharedTextType();

    void withWritePermission(Runnable runnable);

    void onImageSaved();

    void onImageSavingFailed();

}
