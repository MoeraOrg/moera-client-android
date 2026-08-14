package org.moera.android.media.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.moera.lib.node.types.MediaUploadAttributes;

public class MediaUploadApiTest {

    private MockWebServer server;
    private MediaUploadApi api;
    private MediaUploadApi.Credentials credentials;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = new MediaUploadApi(new OkHttpClient(), "test-agent", true);
        credentials = new MediaUploadApi.Credentials(
            server.url("/moera").toString(), "secret", "client-id", "local-id"
        );
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void createsUploadWithMoeraHeadersAndGeneratedType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody(uploadInfo("upload-id", "[]", null)));
        MediaUploadAttributes attributes = new MediaUploadAttributes();
        attributes.setMimeType("video/mp4");
        attributes.setTitle("movie.mp4");
        attributes.setFileSize(12);

        assertEquals("upload-id", api.create(credentials, attributes).getId());

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/moera/api/media/upload", request.getPath());
        assertEquals("Bearer token:secret", request.getHeader("Authorization"));
        assertEquals("client-id", request.getHeader("Client-ID"));
        assertEquals("test-agent", request.getHeader("User-Agent"));
        assertTrue(request.getBody().readUtf8().contains("\"fileSize\":12"));
    }

    @Test
    public void uploadsChunkWithoutBufferingAndEncodesUtf8Filename() throws Exception {
        server.enqueue(new MockResponse().setBody(uploadInfo("upload-id", "[0]", 1L)));
        RequestBody body = RequestBody.create("chunk".getBytes(StandardCharsets.UTF_8), MediaType.get("video/mp4"));

        api.putChunk(credentials, "upload-id", 0, "мой файл.mp4", body);

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("PUT", request.getMethod());
        assertEquals("/moera/api/media/upload/upload-id/0", request.getPath());
        assertEquals("video/mp4", request.getHeader("Content-Type"));
        assertTrue(request.getHeader("Content-Disposition").startsWith("attachment; filename*=UTF-8''"));
        assertFalse(request.getHeader("Content-Disposition").substring(
            request.getHeader("Content-Disposition").indexOf("''") + 2
        ).contains(" "));
        assertEquals("chunk", request.getBody().readUtf8());
    }

    @Test
    public void finalizesUploadAndReturnsCompleteJsonType() throws Exception {
        server.enqueue(new MockResponse().setBody(
            "{\"id\":\"media-id\",\"hash\":\"hash\",\"digest\":\"digest\",\"path\":\"path\","
                + "\"mimeType\":\"video/mp4\",\"width\":1,\"height\":1,\"orientation\":1,\"size\":12}"
        ));

        assertEquals("media-id", api.finalizeUpload(credentials, "upload-id", true).getId());

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/moera/api/media/private?upload=upload-id&downsize=true", request.getPath());
    }

    @Test
    public void executesResumeDeleteAndFinalizeProtocol() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody(uploadInfo("upload-id", "[]", null)));
        server.enqueue(new MockResponse().setBody(uploadInfo("upload-id", "[0]", null)));
        server.enqueue(new MockResponse().setBody(uploadInfo("upload-id", "[0]", null)));
        server.enqueue(new MockResponse().setBody("{\"message\":\"Deleted\"}"));
        MediaUploadAttributes attributes = new MediaUploadAttributes();
        attributes.setMimeType("video/mp4");
        attributes.setFileSize(12);

        api.create(credentials, attributes);
        api.putChunk(
            credentials,
            "upload-id",
            0,
            "movie.mp4",
            new ContentUriRequestBody(
                offset -> new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                MediaType.get("video/mp4"),
                0,
                5,
                () -> false,
                null
            )
        );
        assertEquals(1, api.get(credentials, "upload-id").getUploadedChunks().size());
        api.delete(credentials, "upload-id");

        assertEquals("/moera/api/media/upload", server.takeRequest().getPath());
        RecordedRequest chunk = server.takeRequest();
        assertEquals("/moera/api/media/upload/upload-id/0", chunk.getPath());
        assertEquals("hello", chunk.getBody().readUtf8());
        assertEquals("/moera/api/media/upload/upload-id", server.takeRequest().getPath());
        RecordedRequest delete = server.takeRequest();
        assertEquals("DELETE", delete.getMethod());
        assertEquals("/moera/api/media/upload/upload-id", delete.getPath());
    }

    @Test
    public void reportsAmbiguousNetworkFailureWithoutClaimingAResponse() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        MediaUploadAttributes attributes = new MediaUploadAttributes();
        attributes.setMimeType("video/mp4");
        attributes.setFileSize(12);

        MediaUploadApiException error = assertThrows(
            MediaUploadApiException.class, () -> api.create(credentials, attributes)
        );

        assertEquals("network", error.getCode());
        assertTrue(error.isRetryable());
        assertFalse(error.isResponseReceived());
    }

    @Test
    public void rejectsMalformedSuccessfulResponse() {
        server.enqueue(new MockResponse().setBody("not-json"));

        MediaUploadApiException error = assertThrows(
            MediaUploadApiException.class, () -> api.get(credentials, "upload-id")
        );

        assertEquals("invalid-response", error.getCode());
        assertFalse(error.isRetryable());
        assertTrue(error.isResponseReceived());
    }

    @Test
    public void convertsMoeraErrorWithoutExposingResponseBody() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody(
            "{\"errorCode\":\"too-many-requests\",\"message\":\"Try later\",\"secret\":\"hidden\"}"
        ));

        MediaUploadApiException error = assertThrows(
            MediaUploadApiException.class, () -> api.get(credentials, "upload-id")
        );

        assertEquals("too-many-requests", error.getCode());
        assertEquals("Try later", error.getMessage());
        assertTrue(error.isRetryable());
    }

    private static String uploadInfo(String id, String chunks, Long completedAt) {
        return "{\"id\":\"" + id + "\",\"mimeType\":\"video/mp4\",\"title\":\"movie.mp4\","
            + "\"fileSize\":12,\"chunkSize\":12,\"uploadedChunks\":" + chunks
            + ",\"deadline\":9999999999"
            + (completedAt != null ? ",\"completedAt\":" + completedAt : "") + "}";
    }

}
