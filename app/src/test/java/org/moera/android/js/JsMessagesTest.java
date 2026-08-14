package org.moera.android.js;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.moera.android.media.MediaUploadState;
import org.moera.android.media.SelectedMediaInfo;
import org.moera.android.media.WebClientCapabilities;
import org.moera.android.media.database.MediaUploadEntity;

public class JsMessagesTest {

    @Test
    public void mediaSelectedContainsOrderedUriFreeDescriptors() throws Exception {
        List<String> sent = new ArrayList<>();
        WebClientCapabilities capabilities = enabledCapabilities();
        JsMessages messages = new JsMessages(sent::add, capabilities);

        messages.mediaSelected(List.of(
            new SelectedMediaInfo("one", "picture.png", "image/png", 123, "data:image/png;base64,AA=="),
            new SelectedMediaInfo("two", "archive.zip", "application/zip", 456, null)
        ), capabilities.getWebClientGeneration(), () -> {
        });

        JSONObject message = new JSONObject(sent.get(0));
        assertEquals("moera-android", message.getString("source"));
        assertEquals("media-selected", message.getString("action"));
        JSONArray items = message.getJSONArray("items");
        assertEquals("one", items.getJSONObject(0).getString("id"));
        assertEquals("two", items.getJSONObject(1).getString("id"));
        assertTrue(items.getJSONObject(1).isNull("thumbnail"));
        assertFalse(message.toString().contains("content://"));
    }

    @Test
    public void selectionFailureIsStructured() throws Exception {
        List<String> sent = new ArrayList<>();
        WebClientCapabilities capabilities = enabledCapabilities();
        JsMessages messages = new JsMessages(sent::add, capabilities);

        messages.mediaSelectionFailed(
            "grant-failed", "Cannot retain access", capabilities.getWebClientGeneration()
        );

        JSONObject message = new JSONObject(sent.get(0));
        assertEquals("media-selection-failed", message.getString("action"));
        assertEquals("grant-failed", message.getJSONObject("error").getString("code"));
        assertEquals("Cannot retain access", message.getJSONObject("error").getString("message"));
    }

    @Test
    public void staleDocumentDoesNotReceiveSelection() throws Exception {
        List<String> sent = new ArrayList<>();
        AtomicBoolean rejected = new AtomicBoolean();
        WebClientCapabilities capabilities = enabledCapabilities();
        JsMessages messages = new JsMessages(sent::add, capabilities);
        long webClientGeneration = capabilities.getWebClientGeneration();
        capabilities.reset();

        messages.mediaSelected(
            List.of(new SelectedMediaInfo("one", "picture.png", "image/png", 123, null)),
            webClientGeneration,
            () -> rejected.set(true)
        );

        assertTrue(sent.isEmpty());
        assertTrue(rejected.get());
    }

    @Test
    public void unavailableTransportFailureKeepsNullableDraft() throws Exception {
        List<String> sent = new ArrayList<>();
        JsMessages messages = new JsMessages(sent::add, enabledCapabilities());

        messages.mediaUploadFailed("media-id", null, "native-upload-unavailable", "Unavailable", false, false);

        JSONObject message = new JSONObject(sent.get(0));
        assertEquals("media-id", message.getString("id"));
        assertTrue(message.isNull("draftId"));
        assertFalse(message.getJSONObject("error").getBoolean("retryable"));
    }

    @Test
    public void uploadStateContainsDurableRecoveryFields() throws Exception {
        List<String> sent = new ArrayList<>();
        JsMessages messages = new JsMessages(sent::add, enabledCapabilities());
        MediaUploadEntity upload = upload(MediaUploadState.UPLOADING);
        upload.confirmedBytes = 456;

        messages.mediaUploadState(upload);

        JSONObject message = new JSONObject(sent.get(0));
        assertEquals("media-upload-state", message.getString("action"));
        assertEquals("media-id", message.getString("id"));
        assertTrue(message.isNull("draftId"));
        assertEquals("UPLOADING", message.getString("state"));
        assertEquals("video.mp4", message.getString("name"));
        assertEquals("video/mp4", message.getString("mimeType"));
        assertEquals(456, message.getLong("loaded"));
        assertEquals(1234, message.getLong("total"));
    }

    @Test
    public void uploadCompletionReturnsFullPrivateMediaObject() throws Exception {
        List<String> sent = new ArrayList<>();
        JsMessages messages = new JsMessages(sent::add, enabledCapabilities());
        MediaUploadEntity upload = upload(MediaUploadState.COMPLETED);
        upload.draftId = "draft-id";
        upload.resultJson = "{\"id\":\"private-id\",\"mimeType\":\"video/mp4\","
            + "\"directDownloadPath\":\"download\",\"attachment\":true}";

        messages.mediaUploadCompleted(upload);

        JSONObject message = new JSONObject(sent.get(0));
        assertEquals("media-upload-completed", message.getString("action"));
        assertEquals("media-id", message.getString("id"));
        assertEquals("draft-id", message.getString("draftId"));
        assertEquals("private-id", message.getJSONObject("media").getString("id"));
        assertEquals("download", message.getJSONObject("media").getString("directDownloadPath"));
        assertTrue(message.getJSONObject("media").getBoolean("attachment"));
    }

    @Test
    public void disabledCapabilityDoesNotReceiveUploadEvents() throws Exception {
        List<String> sent = new ArrayList<>();
        WebClientCapabilities capabilities = enabledCapabilities();
        JsMessages messages = new JsMessages(sent::add, capabilities);
        capabilities.reset();

        messages.mediaUploadState(upload(MediaUploadState.UPLOADING));

        assertTrue(sent.isEmpty());
    }

    private static MediaUploadEntity upload(MediaUploadState state) {
        return new MediaUploadEntity(
            "media-id",
            null,
            "https://home/moera",
            "client-id",
            "video.mp4",
            "video/mp4",
            1234,
            "data:image/jpeg;base64,AA==",
            state.name(),
            true,
            1,
            1
        );
    }

    private static WebClientCapabilities enabledCapabilities() throws JSONException {
        WebClientCapabilities capabilities = new WebClientCapabilities();
        capabilities.set("{\"nativeMediaUpload\":1,\"clientId\":\"client-id\"}");
        return capabilities;
    }

}
