package org.moera.android.media;

import android.net.Uri;
import androidx.annotation.Nullable;

/** Durable private description of a picker item owned by the native uploader. */
public record SelectedMedia(
    String id,
    Uri uri,
    String displayName,
    String mimeType,
    long size,
    @Nullable String thumbnailPath,
    long createdAt,
    boolean grantPersisted
) {
}
