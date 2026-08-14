package org.moera.android.js;

import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebMessage;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.moera.android.BuildConfig;
import org.moera.android.media.SelectedMediaInfo;
import org.moera.android.media.WebClientCapabilities;
import org.moera.android.media.database.MediaUploadEntity;

public class JsMessages {

    private interface MessageInitializer {

        void initialize(JSONObject message) throws JSONException;

    }

    private interface MessageTarget {

        void send(String message, BooleanSupplier condition, Runnable rejected);

    }

    private static final String TAG = JsMessages.class.getSimpleName();

    private final MessageTarget target;
    private final WebClientCapabilities webClientCapabilities;

    public JsMessages(WebView webView, Uri webClientUri, WebClientCapabilities webClientCapabilities) {
        this.webClientCapabilities = webClientCapabilities;
        target = (message, condition, rejected) -> {
            boolean posted = webView.post(() -> {
                if (condition.getAsBoolean()) {
                    webView.postWebMessage(new WebMessage(message), webClientUri);
                } else {
                    rejected.run();
                }
            });
            if (!posted) {
                rejected.run();
            }
        };
    }

    JsMessages(Consumer<String> target, WebClientCapabilities webClientCapabilities) {
        this.webClientCapabilities = webClientCapabilities;
        this.target = (message, condition, rejected) -> {
            if (condition.getAsBoolean()) {
                target.accept(message);
            } else {
                rejected.run();
            }
        };
    }

    private void sendMessage(MessageInitializer initializer) {
        sendMessage(initializer, () -> true, () -> {
        });
    }

    private void sendMessage(MessageInitializer initializer, BooleanSupplier condition, Runnable rejected) {
        try {
            JSONObject message = new JSONObject();
            message.put("source", "moera-android");
            initializer.initialize(message);
            target.send(message.toString(), condition, rejected);
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building JSON", e);
            }
        }
    }

    private void sendNativeUploadMessage(MessageInitializer initializer) {
        sendMessage(initializer, webClientCapabilities::isNativeMediaUploadEnabled, () -> {
        });
    }

    private void sendNativeDocumentMessage(
        MessageInitializer initializer, long webClientGeneration, Runnable rejected
    ) {
        sendMessage(
            initializer,
            () -> webClientCapabilities.isCurrentNativeDocument(webClientGeneration),
            rejected
        );
    }

    public void back() {
        sendMessage(message -> {
            message.put("action", "back");
        });
    }

    public void callReturn(int callId, Object value) {
        sendMessage(message -> {
            message.put("action", "call-return");
            message.put("callId", callId);
            message.put("value", value);
        });
    }

    public void networkChanged() {
        sendMessage(message -> {
            message.put("action", "network-changed");
        });
    }

    public void contentSelected(Collection<String> uris) {
        sendMessage(message -> {
            message.put("action", "content-selected");
            message.put("uris", new JSONArray(uris));
        });
    }

    public void mediaSelected(
        Collection<SelectedMediaInfo> items, long webClientGeneration, Runnable rejected
    ) {
        sendNativeDocumentMessage(message -> {
            message.put("action", "media-selected");
            JSONArray jsonItems = new JSONArray();
            for (SelectedMediaInfo item : items) {
                JSONObject jsonItem = new JSONObject();
                jsonItem.put("id", item.id());
                jsonItem.put("name", item.name());
                jsonItem.put("mimeType", item.mimeType());
                jsonItem.put("size", item.size());
                jsonItem.put("thumbnail", item.thumbnail() != null ? item.thumbnail() : JSONObject.NULL);
                jsonItems.put(jsonItem);
            }
            message.put("items", jsonItems);
        }, webClientGeneration, rejected);
    }

    public void mediaSelectionFailed(String code, String text, long webClientGeneration) {
        sendNativeDocumentMessage(message -> {
            message.put("action", "media-selection-failed");
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", text);
            message.put("error", error);
        }, webClientGeneration, () -> {
        });
    }

    public void mediaUploadFailed(
        String id, String draftId, String code, String text, boolean retryable, boolean completionUnknown
    ) {
        sendNativeUploadMessage(message -> {
            message.put("action", "media-upload-failed");
            message.put("id", id);
            message.put("draftId", draftId != null ? draftId : JSONObject.NULL);
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", text);
            error.put("retryable", retryable);
            error.put("completionUnknown", completionUnknown);
            message.put("error", error);
        });
    }

    public void mediaUploadProgress(MediaUploadEntity upload) {
        sendNativeUploadMessage(message -> {
            message.put("action", "media-upload-progress");
            putUploadIdentity(message, upload);
            message.put("loaded", upload.confirmedBytes);
            message.put("total", upload.size);
        });
    }

    public void mediaUploadCompleted(MediaUploadEntity upload) {
        sendNativeUploadMessage(message -> {
            message.put("action", "media-upload-completed");
            putUploadIdentity(message, upload);
            message.put("media", new JSONObject(upload.resultJson));
        });
    }

    public void mediaUploadFailed(MediaUploadEntity upload) {
        sendNativeUploadMessage(message -> {
            message.put("action", "media-upload-failed");
            putUploadIdentity(message, upload);
            JSONObject error = new JSONObject();
            error.put("code", upload.lastErrorCode);
            error.put("message", upload.lastErrorMessage);
            error.put("retryable", upload.retryable);
            error.put("completionUnknown", upload.completionUnknown);
            message.put("error", error);
        });
    }

    public void mediaUploadState(MediaUploadEntity upload) {
        sendNativeUploadMessage(message -> {
            message.put("action", "media-upload-state");
            putUploadIdentity(message, upload);
            message.put("state", upload.state);
            message.put("name", upload.displayName);
            message.put("mimeType", upload.mimeType);
            message.put("thumbnail", upload.thumbnail != null ? upload.thumbnail : JSONObject.NULL);
            message.put("loaded", upload.confirmedBytes);
            message.put("total", upload.size);
            if (upload.resultJson != null) {
                message.put("media", new JSONObject(upload.resultJson));
            }
            if (upload.lastErrorCode != null) {
                JSONObject error = new JSONObject();
                error.put("code", upload.lastErrorCode);
                error.put("message", upload.lastErrorMessage);
                error.put("retryable", upload.retryable);
                error.put("completionUnknown", upload.completionUnknown);
                message.put("error", error);
            }
        });
    }

    private static void putUploadIdentity(JSONObject message, MediaUploadEntity upload) throws JSONException {
        message.put("id", upload.mediaId);
        message.put("draftId", upload.draftId != null ? upload.draftId : JSONObject.NULL);
    }

}
