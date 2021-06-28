package org.moera.android.settings.exception;

public class UnsuitableSettingTypeException extends SettingValueException {

    public UnsuitableSettingTypeException(String settingType, Class<?> valueType) {
        super(String.format("Value of type %s is unsuitable for setting of type '%s'",
                valueType.getSimpleName(), settingType));
    }

}
