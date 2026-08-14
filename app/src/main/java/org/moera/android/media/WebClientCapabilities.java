package org.moera.android.media;

import org.json.JSONException;
import org.json.JSONObject;

/** Capabilities negotiated by the currently loaded top-level web document. */
public class WebClientCapabilities {

    private boolean nativeMediaUpload;
    private String clientId;
    private long webClientGeneration;

    public synchronized void set(String json) throws JSONException {
        JSONObject capabilities = new JSONObject(json);
        nativeMediaUpload = capabilities.optInt("nativeMediaUpload", 0) == 1;
        clientId = nativeMediaUpload ? capabilities.optString("clientId", null) : null;
        if (clientId != null && clientId.isBlank()) {
            clientId = null;
        }
    }

    public synchronized void reset() {
        nativeMediaUpload = false;
        clientId = null;
        webClientGeneration++;
    }

    public synchronized boolean isNativeMediaUploadEnabled() {
        return nativeMediaUpload;
    }

    public synchronized String getClientId() {
        return clientId;
    }

    public synchronized long getWebClientGeneration() {
        return webClientGeneration;
    }

    public synchronized boolean isCurrentNativeDocument(long generation) {
        return nativeMediaUpload && webClientGeneration == generation;
    }

}
