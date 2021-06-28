package org.moera.android.settings.type;

import org.moera.android.settings.SettingTypeModifiers;
import org.moera.android.settings.exception.InvalidSettingTypeConversionException;
import org.moera.android.settings.exception.UnsuitableSettingTypeException;

public abstract class SettingTypeBase {

    public abstract String getTypeName();

    public Object parseTypeModifiers(SettingTypeModifiers modifiers) {
        return modifiers;
    }

    public String serializeValue(Object value) {
        return value.toString();
    }

    public Object deserializeValue(String value) {
        return value;
    }

    public String getString(Object value) {
        return value.toString();
    }

    public Boolean getBool(Object value) {
        throw new InvalidSettingTypeConversionException(getTypeName(), Boolean.class);
    }

    public Integer getInt(Object value, Object typeModifiers) {
        throw new InvalidSettingTypeConversionException(getTypeName(), Integer.class);
    }

    public Long getLong(Object value) {
        throw new InvalidSettingTypeConversionException(getTypeName(), Long.class);
    }

    public Object accept(Object value, Object typeModifiers) {
        return accept(value);
    }

    protected Object accept(Object value) {
        if (value == null) {
            return null;
        }
        throw new UnsuitableSettingTypeException(getTypeName(), value.getClass());
    }

}
