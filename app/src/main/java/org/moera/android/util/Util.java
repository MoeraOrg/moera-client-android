package org.moera.android.util;

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

        try {
            return URLEncoder.encode(s.toString(), StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            // practically impossible
            return null;
        }
    }

}
