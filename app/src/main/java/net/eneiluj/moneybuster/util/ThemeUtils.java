package net.eneiluj.moneybuster.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import net.eneiluj.moneybuster.R;

public class ThemeUtils {

    public static int primaryColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useServerColor = prefs.getBoolean(context.getString(R.string.pref_key_use_server_color), true);
        int serverColor = prefs.getInt(context.getString(R.string.pref_key_server_color), -1);
        if (useServerColor && serverColor != -1) {
            return serverColor;
        } else {
            return prefs.getInt(
                    context.getString(R.string.pref_key_color),
                    ContextCompat.getColor(context, R.color.primary)
            );
        }
    }

    public static int primaryColorTransparent(Context context) {
        return manipulateColor(primaryColor(context), 1, 150);
    }

    public static int primaryDarkColor(Context context) {
        return manipulateColor(primaryColor(context), 0.6f);
    }

    public static int primaryLightColor(Context context) {
        return manipulateColor(primaryColor(context), 1.4f);
    }

    private static int manipulateColor(int color, float factor) {
        return manipulateColor(color, factor, Color.alpha(color));
    }

    private static int manipulateColor(int color, float factor, int alpha) {
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(alpha,
                Math.min(r,255),
                Math.min(g,255),
                Math.min(b,255));
    }

    public static Bitmap encodeAsBitmap(String str) throws WriterException {
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(str,
                    BarcodeFormat.QR_CODE, 400, 400, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }
        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, 400, 0, 0, w, h);
        return bitmap;
    }

    public static Bitmap getRoundedBitmap(Bitmap input, int pixels) {
        Bitmap rounded = Bitmap.createBitmap(input.getWidth(), input
                .getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(rounded);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, input.getWidth(), input.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, pixels, pixels, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(input, rect, rect, paint);

        return rounded;
    }

    // in case we need it some day
    public static int getPixelsFromDp(int dp, Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                context.getResources().getDimension(dp),
                context.getResources().getDisplayMetrics()
        );
    }

    public static Drawable getMemberAvatarDrawable(Context context, String avatarB64, boolean disabled) {
        byte[] decodedString = Base64.decode(avatarB64, Base64.DEFAULT);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        Bitmap rounded = ThemeUtils.getRoundedBitmap(decodedByte, decodedByte.getWidth() / 2);
        float mRadius = decodedByte.getWidth() / 2;
        if (disabled) {
            Paint mDisabledCircle = new Paint();
            mDisabledCircle.setStyle(Paint.Style.STROKE);
            mDisabledCircle.setStrokeWidth(mRadius * 0.2f);
            mDisabledCircle.setAntiAlias(true);
            mDisabledCircle.setColor(Color.DKGRAY);

            Canvas canvas = new Canvas(rounded);
            canvas.drawCircle(mRadius, mRadius, mRadius * 0.9f, mDisabledCircle);
            canvas.drawLine(mRadius * 0.4f, mRadius * 1.6f, mRadius * 1.6f, mRadius * 0.4f, mDisabledCircle);
        }
        Drawable res = new BitmapDrawable(context.getResources(), rounded);
        return res;
    }

}
