package org.moera.android.util;

import android.os.Build;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Util {

    public static Boolean toBoolean(String value) {
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(String.format("\"%s\" is not a valid value for boolean", value));
    }

    public static String ue(Object s) {
        if (s == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return URLEncoder.encode(s.toString(), StandardCharsets.UTF_8);
        } else {
            try {
                return URLEncoder.encode(s.toString(), StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                // practically impossible
                return null;
            }
        }
    }

    public static String rfc5987Encode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            int character = current & 0xff;
            if (
                character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9'
                || "!#$&+-.^_`|~".indexOf(character) >= 0
            ) {
                encoded.append((char) character);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(character >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(character & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

}
