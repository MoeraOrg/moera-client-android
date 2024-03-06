package org.moera.android.api.model;

import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdConverter;

import org.moera.android.BuildConfig;

import java.io.IOException;

@JsonSerialize(converter = Body.ToStringConverter.class)
@JsonDeserialize(converter = Body.FromStringConverter.class)
public class Body {

    private static final String TAG = Body.class.getSimpleName();

    public static final String EMPTY = "{}";

    private String encoded;
    private BodyDecoded decoded = new BodyDecoded();

    public Body() {
    }

    public Body(String encoded) {
        setEncoded(encoded);
    }

    private void decode() {
        if (encoded == null) {
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            decoded = mapper.readValue(encoded, BodyDecoded.class);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error parsing JSON response", e);
            }
        }
    }

    private void encode() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            encoded = mapper.writeValueAsString(decoded);
        } catch (JsonProcessingException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error parsing JSON response", e);
            }
        }
    }

    public String getEncoded() {
        if (encoded == null) {
            encode();
        }
        return encoded;
    }

    public void setEncoded(String encoded) {
        this.encoded = encoded;
        decode();
    }

    public String getSubject() {
        return decoded.getSubject();
    }

    public void setSubject(String subject) {
        decoded.setSubject(subject);
    }

    public String getText() {
        return decoded.getText();
    }

    public void setText(String text) {
        decoded.setText(text);
        encoded = null;
    }

    public static class ToStringConverter extends StdConverter<Body, String> {

        @Override
        public String convert(Body body) {
            return body.getEncoded();
        }

    }

    public static class FromStringConverter extends StdConverter<String, Body> {

        @Override
        public Body convert(String s) {
            return new Body(s);
        }

    }

}
