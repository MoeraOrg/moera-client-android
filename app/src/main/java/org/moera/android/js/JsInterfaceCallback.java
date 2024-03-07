package org.moera.android.js;

public interface JsInterfaceCallback {

    void updatePushRelay();

    void onBack();

    String getSharedText();

    String getSharedTextType();

    void withWritePermission(Runnable runnable);

    void toast(String text);

    void setSwipeRefreshEnabled(boolean enabled);

    void changeLanguage(String lang);

}
