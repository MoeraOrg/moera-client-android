package org.moera.android.settings.type;

import org.moera.android.settings.SettingTypeModifiers;
import org.moera.android.settings.exception.DeserializeSettingValueException;
import org.moera.android.util.Util;

public class StringSettingType extends SettingTypeBase {

    @Override
    public String getTypeName() {
        return "string";
    }

    @Override
    public StringSettingTypeModifiers parseTypeModifiers(SettingTypeModifiers modifiers) {
        StringSettingTypeModifiers stringMods = new StringSettingTypeModifiers();
        if (modifiers.getMultiline() != null) {
            Boolean multiline = Util.toBoolean(modifiers.getMultiline());
            if (multiline == null) {
                throw new DeserializeSettingValueException("bool", modifiers.getMultiline());
            }
            stringMods.setMultiline(multiline);
        }
        return stringMods;
    }

    @Override
    public String getString(Object value) {
        return (String) value;
    }

    protected Object accept(Object value) {
        return value.toString();
    }

}
