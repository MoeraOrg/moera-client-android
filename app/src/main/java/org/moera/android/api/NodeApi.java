package org.moera.android.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.moera.android.BuildConfig;
import org.moera.android.Preferences;
import org.moera.android.api.model.PushRelayClientAttributes;
import org.moera.android.api.model.PushRelayType;
import org.moera.android.api.model.Result;
import org.moera.android.api.model.StoryAttributes;
import org.moera.android.api.model.StoryInfo;

import java.io.IOException;
import java.util.Objects;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NodeApi {

    private static final String TAG = NodeApi.class.getSimpleName();

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient();
    private final Context context;

    public NodeApi(Context context) {
        this.context = context;
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private HttpUrl.Builder getHomeLocation() {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL,
                Context.MODE_PRIVATE);
        return Objects.requireNonNull(HttpUrl.parse(prefs.getString(Preferences.HOME_LOCATION, "")))
                .newBuilder()
                .addPathSegment("api");
    }

    private String getHomeToken() {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL,
                Context.MODE_PRIVATE);
        return prefs.getString(Preferences.HOME_TOKEN, "");
    }

    private String buildBody(Object body) throws NodeApiException {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new NodeApiException("Error converting to JSON", e);
        }
    }

    private Request buildRequest(String method, HttpUrl url, Object body) throws NodeApiException {
        return new Request.Builder()
                .url(url)
                .method(method, RequestBody.create(buildBody(body), MEDIA_TYPE_JSON))
                .addHeader("Authorization", "bearer " + getHomeToken())
                .build();
    }

    private <T> T parseResponse(ResponseBody body, Class<T> klass) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.readValue(body.string(), klass);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error parsing JSON response", e);
            }
            return null;
        }
    }

    private <T> T call(String method, HttpUrl url, Object body, Class<T> result)
            throws NodeApiException {

        Request request = buildRequest(method, url, body);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorInfo = response.body() != null
                        ? response.body().string() : response.toString();
                throw new NodeApiException("Request failed: " + errorInfo);
            }
            return parseResponse(response.body(), result);
        } catch (IOException e) {
            throw new NodeApiException("Request failed", e);
        }
    }

    public StoryInfo putStory(String id, Boolean read, Boolean viewed) throws NodeApiException {
        HttpUrl url = getHomeLocation().addPathSegment("stories").addPathSegment(id).build();

        StoryAttributes storyAttributes = new StoryAttributes();
        storyAttributes.setRead(read);
        storyAttributes.setViewed(viewed);

        return call("PUT", url, storyAttributes, StoryInfo.class);
    }

    public Result registerAtPushRelay(String clientId, String lang) throws NodeApiException {
        HttpUrl url = getHomeLocation().addPathSegment("push-relay").build();

        PushRelayClientAttributes attributes = new PushRelayClientAttributes();
        attributes.setType(PushRelayType.FCM);
        attributes.setClientId(clientId);
        attributes.setLang(lang);

        return call("POST", url, attributes, Result.class);
    }

}
