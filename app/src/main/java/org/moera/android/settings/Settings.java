package org.moera.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.moera.android.BuildConfig;
import org.moera.android.Preferences;
import org.moera.android.settings.exception.DeserializeSettingValueException;
import org.moera.android.settings.type.SettingTypeBase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

public class Settings {

    private static final String TAG = Settings.class.getSimpleName();

    private final Context context;
    private final SettingsMetadata settingsMetadata;
    private final Map<String, Object> values = new HashMap<>();

    public Settings(Context context) throws IOException {
        this.context = context;
        settingsMetadata = new SettingsMetadata(context);
        load();
    }

    private void load() {
        SharedPreferences prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
        String data = prefs.getString(Preferences.SETTINGS, null);
        load(data);
    }

    private void load(String data) {
        if (data == null) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Setting> settings = mapper.readValue(
                data,
                new TypeReference<>() {
                }
            );
            for (Setting setting : settings) {
                putValue(setting.getName(), setting.getValue());
            }
        } catch (JsonProcessingException e) {
            // ignore
        }
    }

    private String serializeValue(String type, Object value) {
        if (value == null) {
            return null;
        }
        return settingsMetadata.getType(type).serializeValue(value);
    }

    private Object deserializeValue(String type, String value) {
        if (value == null) {
            return null;
        }
        return settingsMetadata.getType(type).deserializeValue(value);
    }

    private void putValue(String name, String value) {
        try {
            SettingDescriptor desc = settingsMetadata.getDescriptor(name);
            if (desc == null) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "No metadata for setting " + name);
                }
                return;
            }
            values.put(name, deserializeValue(desc.getType(), value));
        } catch (DeserializeSettingValueException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage() + ": " + name);
            }
        }
    }

    private <T> T forName(String name, SettingMapper<T> mapper) {
        SettingTypeBase optionType = settingsMetadata.getSettingType(name);
        if (optionType == null) {
            return null;
        }
        Object value = values.containsKey(name)
            ? values.get(name)
            : optionType.deserializeValue(settingsMetadata.getDescriptor(name).getDefaultValue());
        return mapper.map(value, optionType);
    }

    public String getString(String name) {
        return forName(name, (value, settingType) -> settingType.getString(value));
    }

    public Boolean getBool(String name) {
        return forName(name, (value, settingType) -> settingType.getBool(value));
    }

    public Integer getInt(String name) {
        return forName(
            name,
            (value, settingType) -> settingType.getInt(value, settingsMetadata.getSettingTypeModifiers(name))
        );
    }

    public Long getLong(String name) {
        return forName(name, (value, settingType) -> settingType.getLong(value));
    }

    public void update(String data) {
        load(data);
        SharedPreferences.Editor prefs = context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE).edit();
        prefs.putString(Preferences.SETTINGS, toString());
        prefs.apply();
    }

    @NonNull
    @Override
    public String toString() {
        List<Setting> settings = new ArrayList<>();
        for (String name : values.keySet()) {
            SettingDescriptor desc = settingsMetadata.getDescriptor(name);
            if (desc == null) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "No metadata for setting " + name);
                }
                continue;
            }
            settings.add(new Setting(name, serializeValue(desc.getType(), values.get(name))));
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

}
