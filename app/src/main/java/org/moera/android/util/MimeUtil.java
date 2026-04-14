package org.moera.android.util;

import java.util.Map;

import android.webkit.MimeTypeMap;

public class MimeUtil {

    private static final Map<String, String> ADDITIONAL_MIME_TYPES = Map.of(
        ".pjp", "image/pjpeg",
        ".pjpeg", "image/pjpeg",
        ".jfif", "image/pjpeg",
        ".apng", "image/apng"
    );

    public static boolean isImagesOnly(String[] acceptTypes) {
        if (acceptTypes == null || acceptTypes.length == 0) {
            return false;
        }
        for (String type : acceptTypes) {
            String mimeType = extensionToMimeType(type);
            if (mimeType == null || !mimeType.startsWith("image/")) {
                return false;
            }
        }
        return true;
    }

    public static String extensionToMimeType(String extensionOrType) {
        if (extensionOrType == null || !extensionOrType.startsWith(".")) {
            return extensionOrType;
        }
        var mimeType = ADDITIONAL_MIME_TYPES.get(extensionOrType);
        if (mimeType != null) {
            return mimeType;
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOrType.substring(1));
    }

}
