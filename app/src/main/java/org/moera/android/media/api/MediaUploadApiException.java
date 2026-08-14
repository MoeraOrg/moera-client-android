package org.moera.android.media.api;

import java.io.IOException;

public class MediaUploadApiException extends IOException {

    private final String code;
    private final int status;
    private final boolean retryable;
    private final boolean responseReceived;

    public MediaUploadApiException(
        String code, String message, int status, boolean retryable, boolean responseReceived
    ) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
        this.responseReceived = responseReceived;
    }

    public MediaUploadApiException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        status = 0;
        this.retryable = retryable;
        responseReceived = false;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isResponseReceived() {
        return responseReceived;
    }

}
