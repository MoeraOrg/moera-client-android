package org.moera.android.media;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import org.moera.android.util.MimeUtil;

/** Creates and stores small previews without copying the selected source. */
public class ThumbnailGenerator {

    private static final int THUMBNAIL_SIZE = 200;
    private static final int JPEG_QUALITY = 82;

    public record Thumbnail(String path, String dataUri) {
    }

    private final Context context;
    private final SelectedMediaStore store;

    public ThumbnailGenerator(Context context, SelectedMediaStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    @Nullable
    public Thumbnail create(String id, Uri uri, String mimeType) throws MediaSelectionException {
        boolean image = MimeUtil.isImageType(mimeType);
        boolean video = MimeUtil.isVideoType(mimeType);
        if (!image && !video) {
            return null;
        }

        Bitmap bitmap = video ? loadVideoFrame(uri) : loadWithGlide(uri);
        if (bitmap == null) {
            throw new MediaSelectionException("thumbnail-failed", "Cannot create media thumbnail");
        }

        Bitmap bounded = bound(bitmap);
        boolean png = image && bounded.hasAlpha();
        Bitmap.CompressFormat format = png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        String extension = png ? ".png" : ".jpg";

        try (var encoded = new ByteArrayOutputStream()) {
            if (!bounded.compress(format, png ? 100 : JPEG_QUALITY, encoded)) {
                throw new IOException("Bitmap compression failed");
            }
            byte[] bytes = encoded.toByteArray();
            File file = new File(store.getThumbnailDirectory(), id + extension);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(bytes);
            }

            String thumbnailMimeType = png ? "image/png" : "image/jpeg";
            return new Thumbnail(
                file.getAbsolutePath(),
                "data:" + thumbnailMimeType + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            );
        } catch (IOException e) {
            throw new MediaSelectionException("thumbnail-failed", "Cannot store media thumbnail", e);
        } finally {
            if (bounded != bitmap) {
                bounded.recycle();
            }
            bitmap.recycle();
        }
    }

    @Nullable
    private Bitmap loadWithGlide(Uri uri) {
        FutureTarget<Bitmap> target = Glide.with(context).asBitmap().load(uri).submit(THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        try {
            Bitmap bitmap = target.get();
            Bitmap.Config config = bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
            return bitmap.copy(config, false);
        } catch (Exception ignored) {
            return null;
        } finally {
            Glide.with(context).clear(target);
        }
    }

    @Nullable
    private Bitmap loadVideoFrame(Uri uri) {
        try (var retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, uri);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                return null;
            }
            return retriever.getScaledFrameAtTime(
                -1,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                THUMBNAIL_SIZE,
                THUMBNAIL_SIZE
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap bound(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= THUMBNAIL_SIZE && height <= THUMBNAIL_SIZE) {
            return bitmap;
        }
        float scale = Math.min((float) THUMBNAIL_SIZE / width, (float) THUMBNAIL_SIZE / height);
        return Bitmap.createScaledBitmap(
            bitmap, Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)), true
        );
    }

}
