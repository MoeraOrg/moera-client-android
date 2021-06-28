package org.moera.android.settings;

import org.moera.android.settings.type.SettingTypeBase;

public interface SettingMapper<T> {

    T map(Object value, SettingTypeBase optionType);

}
