package org.moera.android.settings.exception;

public class DeserializeSettingValueException extends SettingValueException {

    public DeserializeSettingValueException(String typeName, String value) {
        super(String.format("Invalid value of type '%s' for setting: %s", typeName, value));
    }

}
