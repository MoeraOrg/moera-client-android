package org.moera.android.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MimeUtilTest {

    private static final String[] APPLICATION_VIDEO_MIME_TYPES = {
        "application/mp4",
        "application/ogg",
        "application/x-matroska",
        "application/vnd.rn-realmedia",
        "application/vnd.ms-asf"
    };

    @Test
    public void recognizesPhotoPickerMediaCombinations() {
        assertTrue(MimeUtil.isImageType("image/png"));
        assertTrue(MimeUtil.isImageType("IMAGE/JPEG"));
        assertFalse(MimeUtil.isImageType(null));
        assertTrue(MimeUtil.isImagesOnly(new String[]{"image/*"}));
        assertTrue(MimeUtil.isVideosOnly(new String[]{"video/mp4"}));
        assertTrue(MimeUtil.isImagesAndVideosOnly(new String[]{"image/*, video/*"}));
        assertFalse(MimeUtil.isImagesAndVideosOnly(new String[]{"image/*", "application/pdf"}));
    }

    @Test
    public void recognizesApplicationVideoMimeTypes() {
        for (String mimeType : APPLICATION_VIDEO_MIME_TYPES) {
            assertTrue(mimeType, MimeUtil.isVideoType(mimeType));
            assertTrue(mimeType, MimeUtil.isVideosOnly(new String[]{mimeType}));
            assertTrue(mimeType, MimeUtil.isImagesAndVideosOnly(new String[]{"image/*", mimeType}));
            assertTrue(mimeType, MimeUtil.matches(mimeType, new String[]{"video/*"}));
        }
    }

    @Test
    public void passesEveryAcceptedMimeTypeToOpenDocument() {
        assertArrayEquals(
            new String[]{"application/pdf", "application/zip"},
            MimeUtil.acceptedMimeTypes(new String[]{"application/pdf,application/zip"})
        );
        assertArrayEquals(new String[]{"*/*"}, MimeUtil.acceptedMimeTypes(new String[0]));
    }

    @Test
    public void matchesExactAndWildcardTypes() {
        assertTrue(MimeUtil.matches("video/mp4", new String[]{"video/*"}));
        assertTrue(MimeUtil.matches("application/pdf", new String[]{"application/pdf"}));
        assertFalse(MimeUtil.matches("application/zip", new String[]{"application/pdf"}));
    }

}
