package org.moera.android.media;

public class MediaSelectionException extends Exception {

    private final String code;

    public MediaSelectionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MediaSelectionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
