package org.moera.android.settings.type;

import org.moera.android.settings.exception.DeserializeSettingValueException;
import org.moera.android.settings.exception.UnsuitableSettingValueException;
import org.moera.android.util.Util;

public class BoolSettingType extends SettingTypeBase {

    @Override
    public String getTypeName() {
        return "bool";
    }

    private boolean parse(String value, Consumer<String> invalidValue) {
        Boolean boolValue = Util.toBoolean(value);
        if (boolValue != null) {
            return boolValue;
        }
        invalidValue.accept(value);
        return false; // unreachable
    }

    @Override
    public Object deserializeValue(String value) {
        return parse(value, v -> {
            throw new DeserializeSettingValueException(getTypeName(), v);
        });
    }

    @Override
    public Boolean getBool(Object value) {
        return (Boolean) value;
    }

    @Override
    public Integer getInt(Object value, Object typeModifiers) {
        return ((Boolean) value) ? 1 : 0;
    }

    @Override
    public Long getLong(Object value) {
        return ((Boolean) value) ? 1L : 0;
    }

    @Override
    protected Object accept(Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer) {
            return ((Integer) value) != 0;
        }
        if (value instanceof Long) {
            return ((Long) value) != 0;
        }
        if (value instanceof String) {
            return parse((String) value, v -> {
                throw new UnsuitableSettingValueException(v);
            });
        }
        return super.accept(value);
    }

}
