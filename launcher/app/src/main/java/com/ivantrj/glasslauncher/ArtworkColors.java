package com.ivantrj.glasslauncher;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Tiny off-thread palette sampler. Never keeps or modifies the source artwork. */
final class ArtworkColors {
    static int[] dock(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        float saturation = Math.max(0.25f, hsv[1]);
        return new int[] {
            Color.HSVToColor(new float[] {hsv[0], saturation, 0.85f}),
            Color.HSVToColor(new float[] {(hsv[0] + 18) % 360, saturation * 0.8f, 0.65f}),
            Color.HSVToColor(new float[] {(hsv[0] + 342) % 360, saturation, 0.5f})
        };
    }
    static int[] sample(Bitmap artwork) {
        if (artwork == null || artwork.isRecycled()) return dock(0xFF1DB954);
        Bitmap sample = Bitmap.createScaledBitmap(artwork, 32, 32, true);
        if (sample.getConfig() == Bitmap.Config.HARDWARE) {
            Bitmap readable = sample.copy(Bitmap.Config.ARGB_8888, false);
            if (sample != artwork) sample.recycle();
            sample = readable;
        }
        double[] weight = new double[36], red = new double[36], green = new double[36], blue = new double[36];
        float[] hsv = new float[3];
        for (int y = 0; y < sample.getHeight(); y++) for (int x = 0; x < sample.getWidth(); x++) {
            int color = sample.getPixel(x, y);
            Color.colorToHSV(color, hsv);
            if (Color.alpha(color) < 128 || hsv[2] < 0.12f) continue;
            int bucket = Math.min(11, (int) (hsv[0] / 30)) * 3 + Math.min(2, (int) (hsv[1] * 3));
            double w = (0.1 + hsv[1] * hsv[1]) * hsv[2];
            weight[bucket] += w;
            red[bucket] += Color.red(color) * w;
            green[bucket] += Color.green(color) * w;
            blue[bucket] += Color.blue(color) * w;
        }
        if (sample != artwork) sample.recycle();
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            int best = -1;
            for (int j = 0; j < weight.length; j++) if (weight[j] > 0 && (best < 0 || weight[j] > weight[best])) best = j;
            if (best < 0) {
                result[i] = i == 0 ? 0xFF7D8798 : result[0];
                continue;
            }
            result[i] = Color.rgb((int) (red[best] / weight[best]), (int) (green[best] / weight[best]), (int) (blue[best] / weight[best]));
            int hue = best / 3;
            // Encourage distinct hues without inventing a complementary colour.
            for (int j = 0; j < weight.length; j++) {
                int difference = Math.abs(j / 3 - hue);
                if (Math.min(difference, 12 - difference) <= 1) weight[j] *= 0.08;
            }
        }
        return result;
    }
    private ArtworkColors() {}
}
