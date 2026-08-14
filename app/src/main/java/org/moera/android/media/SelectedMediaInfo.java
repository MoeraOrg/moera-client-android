package org.moera.android.media;

import androidx.annotation.Nullable;

/** URI-free media descriptor exposed to the web client. */
public record SelectedMediaInfo(
    String id,
    String name,
    String mimeType,
    long size,
    @Nullable String thumbnail
) {
}
