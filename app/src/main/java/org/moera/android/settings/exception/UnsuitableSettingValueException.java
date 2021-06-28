package org.moera.android.settings.exception;

public class UnsuitableSettingValueException extends SettingValueException {

    public UnsuitableSettingValueException(String value) {
        super(String.format("Value '%s' is unsuitable for the setting", value));
    }

}
