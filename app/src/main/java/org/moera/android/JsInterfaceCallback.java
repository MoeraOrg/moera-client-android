package org.moera.android;

public interface JsInterfaceCallback {

    void onBack();

    String getSharedText();

    String getSharedTextType();

    void withWritePermission(Runnable runnable);

    void toast(String text);

    void setSwipeRefreshEnabled(boolean enabled);

}
