package org.moera.android.media.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.MediaType;
import okio.Buffer;
import org.junit.Test;

public class ContentUriRequestBodyTest {

    @Test
    public void streamsOnlyRequestedRangeAndReportsProgress() throws Exception {
        byte[] source = "0123456789".getBytes();
        AtomicLong progress = new AtomicLong();
        ContentUriRequestBody body = new ContentUriRequestBody(
            offset -> new ByteArrayInputStream(Arrays.copyOfRange(source, (int) offset, source.length)),
            MediaType.get("video/mp4"),
            3,
            4,
            () -> false,
            progress::set
        );
        Buffer sink = new Buffer();

        body.writeTo(sink);

        assertEquals(4, body.contentLength());
        assertArrayEquals("3456".getBytes(), sink.readByteArray());
        assertEquals(4, progress.get());
    }

    @Test
    public void failsWhenSourceEndsBeforeDeclaredChunkLength() {
        ContentUriRequestBody body = new ContentUriRequestBody(
            offset -> new ByteArrayInputStream(new byte[]{1, 2}),
            MediaType.get("application/octet-stream"),
            0,
            3,
            () -> false,
            null
        );

        assertThrows(IOException.class, () -> body.writeTo(new Buffer()));
    }

    @Test
    public void observesCancellationWithoutReadingWholeSource() {
        ContentUriRequestBody body = new ContentUriRequestBody(
            offset -> new ByteArrayInputStream(new byte[1024]),
            MediaType.get("application/octet-stream"),
            0,
            1024,
            () -> true,
            null
        );

        assertThrows(IOException.class, () -> body.writeTo(new Buffer()));
    }

}
