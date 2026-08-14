package org.moera.android.media;

public class MediaUploadFailure extends Exception {

    private final String code;
    private final boolean retryable;
    private final boolean completionUnknown;

    public MediaUploadFailure(String code, String message, boolean retryable) {
        this(code, message, retryable, false, null);
    }

    public MediaUploadFailure(
        String code, String message, boolean retryable, boolean completionUnknown, Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
        this.completionUnknown = completionUnknown;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isCompletionUnknown() {
        return completionUnknown;
    }

}
