package org.moera.android.media;

import org.moera.android.media.database.MediaUploadEntity;

public interface MediaUploadListener {

    void onState(MediaUploadEntity upload);

    void onProgress(MediaUploadEntity upload);

    void onCompleted(MediaUploadEntity upload);

    void onFailed(MediaUploadEntity upload);

    void onTransientFailure(String id, String draftId, String code, String message);

}
