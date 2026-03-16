package org.moera.android.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import androidx.annotation.ColorInt;
import androidx.core.content.res.ResourcesCompat;
import org.moera.android.BuildConfig;
import org.moera.lib.Rules;
import org.moera.lib.naming.NodeName;

public class AvatarUtil {

    private static final String TAG = AvatarUtil.class.getSimpleName();

    public static Bitmap getBitmap(Resources resources, int id) {
        Drawable drawable = ResourcesCompat.getDrawable(resources, id, null);
        assert drawable instanceof BitmapDrawable;
        return ((BitmapDrawable) drawable).getBitmap();
    }

    public static Bitmap avatarWithLetters(String nodeName) {
        boolean anonymous = nodeName == null || nodeName.equals(Rules.ANONYMOUS_NODE_NAME);
        float angle = nameAngle(nodeName);

        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        @ColorInt int circleColor = !anonymous ? rotateColor(0xfffecba1, angle) : 0xffe8e8e8;
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(circleColor);
        circlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(100, 100, 100, circlePaint);

        @ColorInt int textColor = !anonymous ? rotateColor(0xffca6510, angle) : 0xff808080;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(!anonymous ? 66.6f : 133.3f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        String shortName = !anonymous ? NodeName.shorten(nodeName) : "\u2205";
        String letters = shortName.substring(0, Math.min(2, shortName.length())).toUpperCase();

        Rect bounds = new Rect();
        textPaint.getTextBounds(letters, 0, letters.length(), bounds);
        float y = 100f + bounds.height() / 2f - bounds.bottom;

        canvas.drawText(letters, 100f, y, textPaint);

        return bitmap;
    }

    private static float nameAngle(String nodeName) {
        if (nodeName == null) {
            return 0;
        }

        int angle = 0;
        for (int i = 0; i < nodeName.length(); i++) {
            angle = (angle + nodeName.charAt(i)) % 12;
        }

        return angle * 360f / 12f;
    }

    private static @ColorInt int rotateColor(@ColorInt int color, float angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        float r = Color.red(color);
        float g = Color.green(color);
        float b = Color.blue(color);
        int a = Color.alpha(color);

        float nr =
            (0.213f + cos * 0.787f - sin * 0.213f) * r +
            (0.715f - cos * 0.715f - sin * 0.715f) * g +
            (0.072f - cos * 0.072f + sin * 0.928f) * b;

        float ng =
            (0.213f - cos * 0.213f + sin * 0.143f) * r +
            (0.715f + cos * 0.285f + sin * 0.140f) * g +
            (0.072f - cos * 0.072f - sin * 0.283f) * b;

        float nb =
            (0.213f - cos * 0.213f - sin * 0.787f) * r +
            (0.715f - cos * 0.715f + sin * 0.715f) * g +
            (0.072f + cos * 0.928f + sin * 0.072f) * b;

        return Color.argb(
            a,
            clampToByte(nr),
            clampToByte(ng),
            clampToByte(nb)
        );
    }

    private static int clampToByte(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    public static Bitmap avatarWithIcon(Bitmap avatar, Resources resources, Integer icon, @ColorInt int color) {
        if (avatar == null || icon == null || icon == 0) {
            if (BuildConfig.DEBUG) {
                if (avatar == null) {
                    Log.e(TAG, "Avatar is null");
                }
                if (icon == null) {
                    Log.e(TAG, "Icon is null");
                } else if (icon == 0) {
                    Log.e(TAG, "Icon does not exist");
                }
            }
            return avatar;
        }

        int width = avatar.getWidth();
        int height = avatar.getHeight();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Avatar dimensions %d x %d", width, height));
        }

        Bitmap bitmap = Bitmap.createBitmap(width * 9 / 8, height * 9 / 8, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(avatar, null, new Rect(0, 0, width, height), null);

        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        float radius = Math.min(width, height) / 4f;
        float circleX = width - radius / 2;
        float circleY = height - radius / 2;
        canvas.drawCircle(circleX, circleY, radius, paint);

        try {
            Drawable drawable = ResourcesCompat.getDrawable(resources, icon, null);
            if (drawable == null) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Icon is not found");
                }
                return avatar;
            }
            ColorFilter colorFilter = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            drawable.setColorFilter(colorFilter);
            radius *= .6f;
            drawable.setBounds(
                Math.round(circleX - radius), Math.round(circleY - radius),
                Math.round(circleX + radius), Math.round(circleY + radius)
            );
            drawable.draw(canvas);
        } catch (Resources.NotFoundException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Icon is not found");
            }
            return avatar;
        }

        return bitmap;
    }

}
