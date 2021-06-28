package org.moera.android.settings.exception;

public class InvalidSettingTypeConversionException extends SettingValueException {

    public InvalidSettingTypeConversionException(String settingType, Class<?> askedType) {
        super(String.format("Value of setting of type '%s' cannot be converted to %s",
                settingType, askedType.getSimpleName()));
    }

}
