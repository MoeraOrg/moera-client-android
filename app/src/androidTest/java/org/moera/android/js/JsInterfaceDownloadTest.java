package org.moera.android.js;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
public class JsInterfaceDownloadTest {

    private Context context;
    private ContentResolver resolver;
    private JsInterface bridge;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final List<File> sources = new ArrayList<>();
    private final Set<Long> existingIds = new HashSet<>();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        resolver = context.getContentResolver();
        existingIds.addAll(downloadIds());
        JsInterfaceCallback callback = (JsInterfaceCallback) Proxy.newProxyInstance(
            JsInterfaceCallback.class.getClassLoader(),
            new Class<?>[]{JsInterfaceCallback.class},
            (proxy, method, args) -> {
                if (method.getName().equals("toast")) {
                    messages.add((String) args[0]);
                }
                return null;
            }
        );
        bridge = new JsInterface(context, null, callback, null, null);
    }

    @After
    public void tearDown() throws Exception {
        for (long id : newDownloadIds()) {
            resolver.delete(ContentUris.withAppendedId(filesCollection(), id), null, null);
        }
        for (File source : sources) {
            Files.deleteIfExists(source.toPath());
        }
    }

    @Test
    public void savesGalleryVideoToVideoCollection() throws Exception {
        assertDownload("sample.mp4", "video/mp4", true, false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/mp4");
    }

    @Test
    public void infersVideoExtensionWhenUrlHasNone() throws Exception {
        String name = assertDownload("sample.mp4", "video/mp4", true, true,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/mp4");
        assertTrue(name, name.endsWith(".mp4"));
    }

    @Test
    public void savesVideoFromFileBridgeToVideoCollection() throws Exception {
        assertDownload("sample.webm", "video/webm", false, false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/webm");
    }

    @Test
    public void savesGalleryImageToImageCollection() throws Exception {
        assertDownload("sample.png", "image/png", true, false,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/Moera/", "image/png");
    }

    @Test
    public void savesImageFromFileBridgeToImageCollection() throws Exception {
        assertDownload("sample.png", "image/png", false, false,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/Moera/", "image/png");
    }

    @Test
    public void savesOrdinaryFileToDownloads() throws Exception {
        assertDownload(null, "text/plain", false, false,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/", "text/plain");
    }

    @Test
    public void savesNonMediaFromGalleryToDownloads() throws Exception {
        assertDownload(null, "text/plain", true, false,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/", "text/plain");
    }

    @Test
    public void savesApplicationMp4ToVideoCollection() throws Exception {
        assertDownload("sample.mp4", "application/mp4", true, false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/mp4");
    }

    @Test
    public void savesApplicationOggToVideoCollection() throws Exception {
        assertDownload("sample.ogv", "application/ogg", true, false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/ogg");
    }

    @Test
    public void savesApplicationMatroskaToVideoCollection() throws Exception {
        assertDownload("sample.mkv", "application/x-matroska", true, false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/x-matroska");
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    public void savesUnsupportedRealMediaToDownloads() throws Exception {
        assumeFalse(MimeTypeMap.getSingleton().hasMimeType("video/vnd.rn-realvideo"));
        // Android's scanner replaces the supplied MIME type using its own .rm entry.
        String scannedMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("rm");
        String name = assertDownload("sample.rm", "application/vnd.rn-realmedia", true, false,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/",
            scannedMimeType != null ? scannedMimeType : "application/vnd.rn-realmedia");
        assertTrue(name, name.endsWith(".rm"));
    }

    @Test
    public void savesApplicationAsfToVideoCollection() throws Exception {
        assertDownload("sample.asf", "application/vnd.ms-asf", true, false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/Moera/", "video/x-ms-asf");
    }

    @Test
    public void failedVideoDownloadRemovesPendingMedia() throws Exception {
        File missing = new File(context.getCacheDir(), UUID.randomUUID() + ".mp4");

        bridge.saveImage(missing.toURI().toString(), "video/mp4");

        assertNotNull("Saving must report the failure", messages.poll(30, TimeUnit.SECONDS));
        assertTrue("Failed download must not leave pending media", newDownloadIds().isEmpty());
    }

    private String assertDownload(
        String fixture,
        String mimeType,
        boolean fromGallery,
        boolean extensionless,
        Uri collection,
        String directory,
        String storedMimeType
    ) throws Exception {
        String extension = fixture != null ? fixture.substring(fixture.lastIndexOf('.')) : ".txt";
        File source = new File(context.getCacheDir(), UUID.randomUUID() + (extensionless ? "" : extension));
        sources.add(source);
        byte[] content;
        if (fixture != null) {
            // Tiny single-frame media fixtures exercise the real MediaStore scanner.
            try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("downloads/" + fixture)) {
                content = IOUtils.toByteArray(input);
            }
        } else {
            content = "Downloaded document".getBytes(StandardCharsets.UTF_8);
        }
        Files.write(source.toPath(), content);

        if (fromGallery) {
            bridge.saveImage(source.toURI().toString(), mimeType);
        } else {
            bridge.saveFile(source.toURI().toString(), source.getName(), mimeType);
        }

        assertNotNull("Saving must report completion", messages.poll(30, TimeUnit.SECONDS));
        Set<Long> ids = newDownloadIds();
        assertEquals("One download must be created for " + mimeType, 1, ids.size());
        Uri saved = ContentUris.withAppendedId(collection, ids.iterator().next());
        try (Cursor cursor = resolver.query(saved, new String[]{
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.IS_PENDING
        }, null, null, null)) {
            assertNotNull(cursor);
            assertTrue("Download must be in " + collection, cursor.moveToFirst());
            assertEquals(directory, cursor.getString(1));
            assertEquals(storedMimeType, cursor.getString(2));
            assertEquals(0, cursor.getInt(3));
            try (InputStream input = resolver.openInputStream(saved)) {
                assertNotNull(input);
                assertArrayEquals(content, IOUtils.toByteArray(input));
            }
            return cursor.getString(0);
        }
    }

    private Uri filesCollection() {
        return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
    }

    private Set<Long> downloadIds() {
        Set<Long> ids = new HashSet<>();
        try (Cursor cursor = resolver.query(filesCollection(), new String[]{MediaStore.MediaColumns._ID},
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME + " = ?", new String[]{context.getPackageName()}, null)) {
            assertNotNull(cursor);
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        }
        return ids;
    }

    private Set<Long> newDownloadIds() {
        Set<Long> ids = downloadIds();
        ids.removeAll(existingIds);
        return ids;
    }

}
