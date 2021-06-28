package org.moera.android.settings.exception;

public class UnknownSettingTypeException extends RuntimeException {

    public UnknownSettingTypeException(String type) {
        super(String.format("Unknown setting type '%s'", type));
    }

}
