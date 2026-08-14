package org.moera.android.media.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

/** Streams one exact content-URI range into OkHttp without materializing the chunk. */
public class ContentUriRequestBody extends RequestBody {

    static final int BUFFER_SIZE = 64 * 1024;

    interface Source {

        InputStream open(long offset) throws IOException;

    }

    private record ContentResolverSource(ContentResolver resolver, Uri uri, boolean seekable) implements Source {

        @Override
        public InputStream open(long offset) throws IOException {
            ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r");
            if (descriptor == null) {
                throw new IOException("Cannot open content URI");
            }
            try {
                if (seekable) {
                    Os.lseek(descriptor.getFileDescriptor(), offset, OsConstants.SEEK_SET);
                }
                InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                descriptor = null;
                try {
                    if (!seekable) {
                        skipFully(input, offset);
                    }
                    return input;
                } catch (IOException e) {
                    input.close();
                    throw e;
                }
            } catch (ErrnoException e) {
                throw new IOException("Cannot seek content URI", e);
            } finally {
                if (descriptor != null) {
                    descriptor.close();
                }
            }
        }

    }

    private final Source source;
    private final MediaType mediaType;
    private final long offset;
    private final long length;
    private final BooleanSupplier cancelled;
    private final LongConsumer progress;

    public ContentUriRequestBody(
        ContentResolver resolver,
        Uri uri,
        String mimeType,
        long offset,
        long length,
        boolean seekable,
        BooleanSupplier cancelled,
        @Nullable LongConsumer progress
    ) {
        this(
            new ContentResolverSource(resolver, uri, seekable),
            MediaType.parse(mimeType),
            offset,
            length,
            cancelled,
            progress
        );
    }

    ContentUriRequestBody(
        Source source,
        MediaType mediaType,
        long offset,
        long length,
        BooleanSupplier cancelled,
        @Nullable LongConsumer progress
    ) {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid content range");
        }
        this.source = source;
        this.mediaType = mediaType;
        this.offset = offset;
        this.length = length;
        this.cancelled = cancelled;
        this.progress = progress != null ? progress : ignored -> {
        };
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long written = 0;
        try (InputStream input = source.open(offset)) {
            while (written < length) {
                if (cancelled.getAsBoolean()) {
                    throw new IOException("Upload cancelled");
                }
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, length - written));
                if (count < 0) {
                    throw new IOException("Unexpected end of content URI");
                }
                if (count == 0) {
                    continue;
                }
                sink.write(buffer, 0, count);
                written += count;
                progress.accept(written);
            }
        }
    }

    public static boolean isSeekable(ContentResolver resolver, Uri uri) {
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                return false;
            }
            Os.lseek(descriptor.getFileDescriptor(), 0, OsConstants.SEEK_CUR);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void skipFully(InputStream input, long offset) throws IOException {
        long skipped = 0;
        while (skipped < offset) {
            long count = input.skip(offset - skipped);
            if (count > 0) {
                skipped += count;
                continue;
            }
            if (input.read() < 0) {
                throw new IOException("Unexpected end of content URI while seeking");
            }
            skipped++;
        }
    }

}
