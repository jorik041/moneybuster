package net.eneiluj.moneybuster.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import net.eneiluj.moneybuster.R;

public class ThemeUtils {

    public static int primaryColor(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(
                        context.getString(R.string.pref_key_color),
                        ContextCompat.getColor(context, R.color.primary)
                );
    }

    public static int primaryColorTransparent(Context context) {
        return manipulateColor(primaryColor(context), 1, 150);
    }

    public static int primaryDarkColor(Context context) {
        return manipulateColor(primaryColor(context), 0.7f);
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
}
