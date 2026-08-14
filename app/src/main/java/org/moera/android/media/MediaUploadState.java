package org.moera.android.media;

public enum MediaUploadState {
    SELECTED,
    QUEUED,
    CREATING,
    UPLOADING,
    RETRY_WAIT,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELED
}
