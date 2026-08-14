package org.moera.android.media.api;

import java.io.IOException;

import androidx.annotation.Nullable;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.moera.android.util.Util;
import org.moera.lib.node.NodeApiClient;
import org.moera.lib.node.types.MediaUploadAttributes;
import org.moera.lib.node.types.MediaUploadInfo;
import org.moera.lib.node.types.PrivateMediaFileInfo;
import org.moera.lib.node.types.Result;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Focused synchronous client for the resumable Moera media-upload protocol. */
public class MediaUploadApi {

    public record Credentials(String homeLocation, String token, @Nullable String clientId, String mediaId) {
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String userAgent;
    private final boolean allowInsecureHttp;

    public MediaUploadApi(OkHttpClient client, String userAgent, boolean allowInsecureHttp) {
        this.client = client;
        this.userAgent = userAgent;
        this.allowInsecureHttp = allowInsecureHttp;
        objectMapper = new ObjectMapper();
    }

    public MediaUploadInfo create(
        Credentials credentials, MediaUploadAttributes attributes
    ) throws MediaUploadApiException {
        try {
            RequestBody body = RequestBody.create(objectMapper.writeValueAsBytes(attributes), JSON);
            return execute(request(credentials, "media", "upload").post(body).build(), MediaUploadInfo.class);
        } catch (JacksonException e) {
            throw new MediaUploadApiException("invalid-request", "Cannot encode upload request", false, e);
        }
    }

    public MediaUploadInfo get(Credentials credentials, String uploadId) throws MediaUploadApiException {
        return execute(request(credentials, "media", "upload", uploadId).get().build(), MediaUploadInfo.class);
    }

    public MediaUploadInfo putChunk(
        Credentials credentials,
        String uploadId,
        int chunk,
        String fileName,
        RequestBody body
    ) throws MediaUploadApiException {
        Request request = request(credentials, "media", "upload", uploadId, Integer.toString(chunk))
            .header("Content-Disposition", contentDisposition(fileName))
            .put(body)
            .build();
        return execute(request, MediaUploadInfo.class);
    }

    public PrivateMediaFileInfo finalizeUpload(
        Credentials credentials, String uploadId, boolean downsize
    ) throws MediaUploadApiException {
        HttpUrl url = url(credentials.homeLocation(), "media", "private").newBuilder()
            .addQueryParameter("upload", uploadId)
            .addQueryParameter("downsize", Boolean.toString(downsize))
            .build();
        RequestBody body = RequestBody.create(new byte[0], JSON);
        return execute(baseRequest(credentials, url).post(body).build(), PrivateMediaFileInfo.class);
    }

    public void delete(Credentials credentials, String uploadId) throws MediaUploadApiException {
        execute(request(credentials, "media", "upload", uploadId).delete().build(), Result.class);
    }

    public void cancel(String mediaId) {
        for (Call call : client.dispatcher().queuedCalls()) {
            if (mediaId.equals(call.request().tag(String.class))) {
                call.cancel();
            }
        }
        for (Call call : client.dispatcher().runningCalls()) {
            if (mediaId.equals(call.request().tag(String.class))) {
                call.cancel();
            }
        }
    }

    private Request.Builder request(Credentials credentials, String... path) throws MediaUploadApiException {
        return baseRequest(credentials, url(credentials.homeLocation(), path));
    }

    private Request.Builder baseRequest(Credentials credentials, HttpUrl url) throws MediaUploadApiException {
        if (credentials.token() == null || credentials.token().isBlank()) {
            throw new MediaUploadApiException(
                "authentication-required", "Authentication is required", 0, false, false
            );
        }
        Request.Builder request = new Request.Builder()
            .url(url)
            .tag(String.class, credentials.mediaId())
            .header("Accept", "application/json")
            .header("Authorization", "Bearer token:" + credentials.token())
            .header("User-Agent", userAgent);
        if (credentials.clientId() != null && !credentials.clientId().isBlank()) {
            request.header("Client-ID", credentials.clientId());
        }
        return request;
    }

    private HttpUrl url(String homeLocation, String... path) throws MediaUploadApiException {
        String root = NodeApiClient.moeraRoot(homeLocation) + "/api/";
        HttpUrl base = HttpUrl.parse(root);
        if (base == null || !(base.isHttps() || base.scheme().equals("http"))) {
            throw new MediaUploadApiException("invalid-home", "Home node URL is invalid", 0, false, false);
        }
        if (!base.isHttps() && !allowInsecureHttp) {
            throw new MediaUploadApiException("insecure-home", "HTTPS is required for media upload", 0, false, false);
        }
        HttpUrl.Builder result = base.newBuilder();
        for (String segment : path) {
            result.addPathSegment(segment);
        }
        return result.build();
    }

    private <T> T execute(Request request, Class<T> type) throws MediaUploadApiException {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String json = responseBody != null ? responseBody.string() : "";
            if (!response.isSuccessful()) {
                throw responseException(response.code(), json);
            }
            if (json.isBlank()) {
                throw new MediaUploadApiException(
                    "invalid-response", "Node returned an empty response", response.code(), false, true
                );
            }
            try {
                return objectMapper.readValue(json, type);
            } catch (JacksonException e) {
                throw new MediaUploadApiException(
                    "invalid-response", "Node returned an invalid response", response.code(), false, true
                );
            }
        } catch (MediaUploadApiException e) {
            throw e;
        } catch (IOException e) {
            throw new MediaUploadApiException("network", "Cannot reach the home node", true, e);
        }
    }

    private MediaUploadApiException responseException(int status, String body) {
        String code = "http-" + status;
        String message = "Media upload request failed";
        try {
            Result result = objectMapper.readValue(body, Result.class);
            if (result.getErrorCode() != null && !result.getErrorCode().isBlank()) {
                code = result.getErrorCode();
            }
            if (result.getMessage() != null && !result.getMessage().isBlank()) {
                message = result.getMessage();
            }
        } catch (JacksonException ignored) {
            // Do not expose an arbitrary response body to the bridge or logs.
        }
        boolean retryable = status == 429 || status >= 500;
        return new MediaUploadApiException(code, message, status, retryable, true);
    }

    static String contentDisposition(String fileName) {
        return "attachment; filename*=UTF-8''" + Util.rfc5987Encode(fileName);
    }

}
