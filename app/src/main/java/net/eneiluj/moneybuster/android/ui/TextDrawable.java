/*
 * ownCloud Android client application
 *
 * @author Andy Scherzinger
 * @author Tobias Kaminsiky
 * @author Chris Narkiewicz
 * Copyright (C) 2016 ownCloud Inc.
 * Copyright (C) 2018 Andy Scherzinger
 * Copyright (C) 2019 Chris Narkiewicz <hello@ezaquarii.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.eneiluj.moneybuster.android.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.apache.commons.codec.binary.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * A Drawable object that draws text (1 character) on top of a circular/filled background.
 */
public class TextDrawable extends Drawable {
    private static final int INDEX_RED = 0;
    private static final int INDEX_GREEN = 1;
    private static final int INDEX_BLUE = 2;
    private static final int INDEX_HUE = 0;
    private static final int INDEX_SATURATION = 1;
    private static final int INDEX_LUMINATION = 2;

    /**
     * the text to be rendered.
     */
    private String mText;

    /**
     * the text paint to be rendered.
     */
    private Paint mTextPaint;

    /**
     * the background to be rendered.
     */
    private Paint mBackground;

    /**
     * the radius of the circular background to be rendered.
     */
    private float mRadius;

    /**
     * Create a TextDrawable with the given radius.
     *
     * @param text   the text to be rendered
     * @param r      rgb red value
     * @param g      rgb green value
     * @param b      rgb blue value
     * @param radius circle radius
     */
    private TextDrawable(String text, int r, int g, int b, float radius) {
        mRadius = radius;
        mText = text;

        mBackground = new Paint();
        mBackground.setStyle(Paint.Style.FILL);
        mBackground.setAntiAlias(true);
        mBackground.setColor(Color.rgb(r, g, b));

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(radius);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * creates an avatar in form of a TextDrawable with the first letter of a name in a circle with the
     * given radius.
     *
     * @param name       the name
     * @param radiusInDp the circle's radius
     * @return the avatar as a TextDrawable
     * @throws NoSuchAlgorithmException     if the specified algorithm is not available when calculating the color values
     */
    @NonNull
    public static TextDrawable createNamedAvatar(String name, float radiusInDp) throws NoSuchAlgorithmException {
        int[] hsl = calculateHSL(name);
        int[] rgb = HSLtoRGB(hsl[0], hsl[1], hsl[2], 1);

        return new TextDrawable(name.substring(0, 1).toUpperCase(Locale.getDefault()), rgb[0], rgb[1], rgb[2],
                radiusInDp);
    }

    /**
     * Draw in its bounds (set via setBounds) respecting optional effects such as alpha (set via setAlpha) and color
     * filter (set via setColorFilter) a circular background with a user's first character.
     *
     * @param canvas The canvas to draw into
     */
    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawCircle(mRadius, mRadius, mRadius, mBackground);
        canvas.drawText(mText, mRadius, mRadius - ((mTextPaint.descent() + mTextPaint.ascent()) / 2), mTextPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mTextPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mTextPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /**
     * calculates the RGB value based on a given account name.
     *
     * @param name The name
     * @return corresponding RGB color
     * @throws NoSuchAlgorithmException     if the specified algorithm is not available
     */
    private static int[] calculateHSL(String name) throws NoSuchAlgorithmException {
        // using adapted algorithm from https://github.com/nextcloud/server/blob/master/core/js/placeholder.js#L126

        String[] result = new String[]{"0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"};
        double[] rgb = new double[]{0, 0, 0};
        int sat = 70;
        int lum = 68;
        int modulo = 16;

        String hash = name.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");

        if (!hash.matches("^[0-9a-f]{32}")) {
            hash = md5(hash);
        }

        // Splitting evenly the string
        for (int i = 0; i < hash.length(); i++) {
            result[i % modulo] = result[i % modulo] + Integer.parseInt(hash.substring(i, i + 1), 16);
        }

        // Converting our data into a usable rgb format
        // Start at 1 because 16%3=1 but 15%3=0 and makes the repartition even
        for (int count = 1; count < modulo; count++) {
            rgb[count % 3] += Integer.parseInt(result[count]);
        }

        // Reduce values bigger than rgb requirements
        rgb[INDEX_RED] = rgb[INDEX_RED] % 255;
        rgb[INDEX_GREEN] = rgb[INDEX_GREEN] % 255;
        rgb[INDEX_BLUE] = rgb[INDEX_BLUE] % 255;

        double[] hsl = rgbToHsl(rgb[INDEX_RED], rgb[INDEX_GREEN], rgb[INDEX_BLUE]);

        // Classic formula to check the brightness for our eye
        // If too bright, lower the sat
        double bright = Math.sqrt(0.299 * Math.pow(rgb[INDEX_RED], 2) + 0.587 * Math.pow(rgb[INDEX_GREEN], 2) + 0.114
                * Math.pow(rgb[INDEX_BLUE], 2));

        if (bright >= 200) {
            sat = 60;
        }

        return new int[]{(int) (hsl[INDEX_HUE] * 360), sat, lum};
    }

    /**
     *  Convert HSL values to a RGB Color.
     *
     *  @param h Hue is specified as degrees in the range 0 - 360.
     *  @param s Saturation is specified as a percentage in the range 1 - 100.
     *  @param l Luminance is specified as a percentage in the range 1 - 100.
     *  @param alpha  the alpha value between 0 - 1
     *  adapted from https://svn.codehaus.org/griffon/builders/gfxbuilder/tags/GFXBUILDER_0.2/
     *  gfxbuilder-core/src/main/com/camick/awt/HSLColor.java
     */
    @SuppressWarnings("PMD.MethodNamingConventions")
    private static int[] HSLtoRGB(float h, float s, float l, float alpha) {
        if (s < 0.0f || s > 100.0f) {
            String message = "Color parameter outside of expected range - Saturation";
            throw new IllegalArgumentException(message);
        }

        if (l < 0.0f || l > 100.0f) {
            String message = "Color parameter outside of expected range - Luminance";
            throw new IllegalArgumentException(message);
        }

        if (alpha < 0.0f || alpha > 1.0f) {
            String message = "Color parameter outside of expected range - Alpha";
            throw new IllegalArgumentException(message);
        }

        //  Formula needs all values between 0 - 1.

        h = h % 360.0f;
        h /= 360f;
        s /= 100f;
        l /= 100f;

        float q;

        if (l < 0.5) {
            q = l * (1 + s);
        } else {
            q = (l + s) - (s * l);
        }

        float p = 2 * l - q;

        int r = Math.round(Math.max(0, HueToRGB(p, q, h + (1.0f / 3.0f)) * 256));
        int g = Math.round(Math.max(0, HueToRGB(p, q, h) * 256));
        int b = Math.round(Math.max(0, HueToRGB(p, q, h - (1.0f / 3.0f)) * 256));

        return new int[]{r, g, b};
    }

    @SuppressWarnings("PMD.MethodNamingConventions")
    private static float HueToRGB(float p, float q, float h) {
        if (h < 0) {
            h += 1;
        }

        if (h > 1) {
            h -= 1;
        }

        if (6 * h < 1) {
            return p + ((q - p) * 6 * h);
        }

        if (2 * h < 1) {
            return q;
        }

        if (3 * h < 2) {
            return p + ((q - p) * 6 * (2.0f / 3.0f - h));
        }

        return p;
    }

    private static double[] rgbToHsl(double rUntrimmed, double gUntrimmed, double bUntrimmed) {
        double r = rUntrimmed / 255;
        double g = gUntrimmed / 255;
        double b = bUntrimmed / 255;

        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double h = (max + min) / 2;
        double s;
        double l = (max + min) / 2;

        if (max == min) {
            h = s = 0; // achromatic
        } else {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);

            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else if (max == b) {
                h = (r - g) / d + 4;
            }
            h /= 6;
        }

        double[] hsl = new double[]{0.0, 0.0, 0.0};
        hsl[INDEX_HUE] = h;
        hsl[INDEX_SATURATION] = s;
        hsl[INDEX_LUMINATION] = l;

        return hsl;
    }

    private static String md5(String string) throws NoSuchAlgorithmException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(string.getBytes());

        return new String(Hex.encodeHex(md5.digest()));
    }
}
