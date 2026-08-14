package org.moera.android.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.webkit.MimeTypeMap;

public class MimeUtil {

    private static final Map<String, String> ADDITIONAL_MIME_TYPES = Map.of(
        ".pjp", "image/pjpeg",
        ".pjpeg", "image/pjpeg",
        ".jfif", "image/pjpeg",
        ".apng", "image/apng",
        ".rm", "application/vnd.rn-realmedia"
    );
    private static final Set<String> APPLICATION_VIDEO_MIME_TYPES = Set.of(
        "application/mp4",
        "application/ogg",
        "application/x-matroska",
        "application/vnd.rn-realmedia",
        "application/vnd.ms-asf"
    );

    public static boolean isImageType(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    public static boolean isVideoType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("video/") || APPLICATION_VIDEO_MIME_TYPES.contains(normalized);
    }

    public static boolean isImagesOnly(String[] acceptTypes) {
        List<String> mimeTypes = acceptedTypesToMimeTypesList(acceptTypes);
        return !mimeTypes.isEmpty() && mimeTypes.stream().allMatch(MimeUtil::isImageType);
    }

    public static boolean isVideosOnly(String[] acceptTypes) {
        List<String> mimeTypes = acceptedTypesToMimeTypesList(acceptTypes);
        return !mimeTypes.isEmpty() && mimeTypes.stream().allMatch(MimeUtil::isVideoType);
    }

    public static boolean isImagesAndVideosOnly(String[] acceptTypes) {
        List<String> mimeTypes = acceptedTypesToMimeTypesList(acceptTypes);
        if (mimeTypes.isEmpty()) {
            return false;
        }
        boolean image = false;
        boolean video = false;
        for (String mimeType : mimeTypes) {
            if (isImageType(mimeType)) {
                image = true;
            } else if (isVideoType(mimeType)) {
                video = true;
            } else {
                return false;
            }
        }
        return image && video;
    }

    public static String[] acceptedMimeTypes(String[] acceptTypes) {
        List<String> mimeTypes = acceptedTypesToMimeTypesList(acceptTypes);
        return mimeTypes.isEmpty() ? new String[]{"*/*"} : mimeTypes.toArray(new String[0]);
    }

    public static boolean matches(String mimeType, String[] acceptTypes) {
        List<String> accepted = acceptedTypesToMimeTypesList(acceptTypes);
        if (accepted.isEmpty()) {
            return true;
        }
        String actual = mimeType.toLowerCase(Locale.ROOT);
        for (String candidate : accepted) {
            if (candidate.equals("*/*") || candidate.equals(actual)) {
                return true;
            }
            if (candidate.equals("video/*") && isVideoType(actual)) {
                return true;
            }
            int slash = candidate.indexOf('/');
            if (slash >= 0 && candidate.endsWith("/*") && actual.startsWith(candidate.substring(0, slash + 1))) {
                return true;
            }
        }
        return false;
    }

    public static String extensionToMimeType(String extensionOrType) {
        if (extensionOrType == null) {
            return null;
        }
        extensionOrType = extensionOrType.trim().toLowerCase(Locale.ROOT);
        if (!extensionOrType.startsWith(".")) {
            return extensionOrType;
        }
        var mimeType = ADDITIONAL_MIME_TYPES.get(extensionOrType);
        if (mimeType != null) {
            return mimeType;
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOrType.substring(1));
    }

    private static List<String> acceptedTypesToMimeTypesList(String[] acceptTypes) {
        if (acceptTypes == null || acceptTypes.length == 0) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String acceptType : acceptTypes) {
            if (acceptType == null) {
                continue;
            }
            for (String item : acceptType.split(",")) {
                String mimeType = extensionToMimeType(item);
                if (mimeType != null && !mimeType.isBlank()) {
                    result.add(mimeType);
                }
            }
        }
        return new ArrayList<>(result);
    }

}
