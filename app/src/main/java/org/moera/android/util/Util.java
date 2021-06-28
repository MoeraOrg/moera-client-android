package org.moera.android.util;

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

}
