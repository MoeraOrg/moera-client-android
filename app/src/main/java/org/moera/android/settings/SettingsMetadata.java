package org.moera.android.settings;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.moera.android.BuildConfig;
import org.moera.android.R;
import org.moera.android.settings.exception.UnknownSettingTypeException;
import org.moera.android.settings.type.BoolSettingType;
import org.moera.android.settings.type.IntSettingType;
import org.moera.android.settings.type.SettingTypeBase;
import org.moera.android.settings.type.StringSettingType;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsMetadata {

    private static final String TAG = SettingsMetadata.class.getSimpleName();

    private final Map<String, SettingTypeBase> types = new HashMap<>();
    private final Map<String, SettingDescriptor> descriptors = new HashMap<>();
    private final Map<String, Object> typeModifiers = new HashMap<>();

    public SettingsMetadata(Context context) throws IOException {
        types.put("bool", new BoolSettingType());
        types.put("int", new IntSettingType());
        types.put("string", new StringSettingType());

        ObjectMapper mapper = new ObjectMapper();
        List<SettingDescriptor> data = mapper.readValue(
                context.getResources().openRawResource(R.raw.settings),
                new TypeReference<>() {
                });
        for (SettingDescriptor descriptor : data) {
            descriptors.put(descriptor.getName(), descriptor);
        }
        for (SettingDescriptor descriptor : data) {
            SettingTypeBase type = types.get(descriptor.getType());
            if (descriptor.getModifiers() != null && type != null) {
                typeModifiers.put(descriptor.getName(),
                        type.parseTypeModifiers(descriptor.getModifiers()));
            }
        }
    }

    public SettingTypeBase getType(String type) {
        SettingTypeBase settingType = types.get(type);
        if (settingType == null) {
            throw new UnknownSettingTypeException(type);
        }
        return settingType;
    }

    private static SettingDescriptor clientDescriptor(String name) {
        SettingDescriptor descriptor = new SettingDescriptor();
        descriptor.setName(name);
        descriptor.setType("string");
        return descriptor;
    }

    public SettingDescriptor getDescriptor(String name) {
        SettingDescriptor desc = descriptors.get(name);
        if (desc == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Unknown setting: " + name);
            }
            return null;
        }
        return desc;
    }

    public SettingTypeBase getSettingType(String name) {
        return getType(getDescriptor(name).getType());
    }

    public Map<String, SettingDescriptor> getDescriptors() {
        return descriptors;
    }

    public Object getSettingTypeModifiers(String name) {
        return typeModifiers.get(name);
    }

}
