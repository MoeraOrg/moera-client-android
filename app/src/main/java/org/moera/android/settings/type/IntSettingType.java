package org.moera.android.settings.type;

import org.moera.android.settings.SettingTypeModifiers;
import org.moera.android.settings.exception.DeserializeSettingValueException;
import org.moera.android.settings.exception.UnsuitableSettingValueException;

public class IntSettingType extends SettingTypeBase {

    @Override
    public String getTypeName() {
        return "int";
    }

    @Override
    public IntSettingTypeModifiers parseTypeModifiers(SettingTypeModifiers modifiers) {
        IntSettingTypeModifiers intMods = new IntSettingTypeModifiers();
        if (modifiers != null && modifiers.getMin() != null) {
            intMods.setMin((Long) deserializeValue(modifiers.getMin()));
        } else {
            intMods.setMin(Long.MIN_VALUE);
        }
        if (modifiers != null && modifiers.getMax() != null) {
            intMods.setMax((Long) deserializeValue(modifiers.getMax()));
        } else {
            intMods.setMax(Long.MAX_VALUE);
        }
        return intMods;
    }

    private long parse(String value, Consumer<String> invalidValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            invalidValue.accept(value);
        }
        return 0; // unreachable
    }

    @Override
    public Object deserializeValue(String value) {
        return parse(value, v -> {
            throw new DeserializeSettingValueException(getTypeName(), v);
        });
    }

    @Override
    public Integer getInt(Object value, Object typeModifiers) {
        Long longValue = (Long) value;
        if (longValue == null) {
            return null;
        }
        if (typeModifiers == null || !((IntSettingTypeModifiers) typeModifiers).isFitsIntoInt()) {
            return super.getInt(value, typeModifiers);
        }
        return longValue.intValue();
    }

    @Override
    public Long getLong(Object value) {
        return (Long) value;
    }

    @Override
    public Object accept(Object value, Object typeModifiers) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Long) {
            return value;
        }
        if (value instanceof String) {
            return acceptString((String) value, (IntSettingTypeModifiers) typeModifiers);
        }
        return super.accept(value);
    }

    private Object acceptString(String value, IntSettingTypeModifiers typeModifiers) {
        long longValue = parse(value, v -> {
            throw new UnsuitableSettingValueException(v);
        });
        if (typeModifiers != null) {
            if (longValue < typeModifiers.getMin() || longValue > typeModifiers.getMax()) {
                throw new UnsuitableSettingValueException(value);
            }
        }
        return longValue;
    }

}
