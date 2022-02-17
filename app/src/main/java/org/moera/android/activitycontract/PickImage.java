package org.moera.android.activitycontract;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PickImage extends ActivityResultContract<Boolean, Uri[]> {

    @CallSuper
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull Boolean input) {
        return new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                .setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, input);
    }

    @Nullable
    @Override
    public final SynchronousResult<Uri[]> getSynchronousResult(@NonNull Context context, @NonNull Boolean input) {
        return null;
    }

    @NonNull
    @Override
    public final Uri[] parseResult(int resultCode, @Nullable Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) {
            return new Uri[0];
        }

        if (intent.getClipData() != null) {
            int count = intent.getClipData().getItemCount();
            Uri[] result = new Uri[count];
            for (int i = 0; i < count; i++) {
                result[i] = intent.getClipData().getItemAt(i).getUri();
            }
            return result;
        }

        if (intent.getData() != null) {
            return new Uri[]{intent.getData()};
        }

        return new Uri[0];
    }

}
