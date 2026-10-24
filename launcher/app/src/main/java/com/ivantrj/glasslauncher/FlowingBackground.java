package com.ivantrj.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import java.util.Arrays;
import java.util.Random;

/** Four gradients rendered into a tiny reusable texture at 15 fps while Home is visible. */
public class FlowingBackground extends View {
    private final Matrix matrix = new Matrix();
    private final Paint scrim = new Paint();
    private final Paint texturePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect screen = new Rect();
    private Bitmap texture;
    private Canvas textureCanvas;
    private final double[] phases = new double[4];
    private Paint[] current = paints(ArtworkColors.dock(0xFF6886A8));
    private Paint[] previous;
    private int[] palette;
    private long transition, startedAt = SystemClock.uptimeMillis();
    private float sourceX = 0.5f, sourceY = 0.8f, fromX = sourceX, fromY = sourceY;
    private boolean music, fromMusic, visible, running;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!visible || !running) return;
            invalidate();
            postDelayed(this, 66);
        }
    };

    public FlowingBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
        Random random = new Random();
        for (int i = 0; i < phases.length; i++) phases[i] = random.nextDouble() * Math.PI * 2;
    }
    public void setRunning(boolean value) { running = value; schedule(); }
    @Override public void onVisibilityAggregated(boolean value) {
        super.onVisibilityAggregated(value);
        visible = value;
        schedule();
    }
    private void schedule() {
        removeCallbacks(frame);
        if (running && visible) post(frame);
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }
    public void scene(int[] colors, boolean playing, float x, float y) {
        x = Math.max(0, Math.min(1, x));
        y = Math.max(0, Math.min(1, y));
        if (Arrays.equals(palette, colors) && playing == music && Math.abs(x-sourceX) < 0.01f && Math.abs(y-sourceY) < 0.01f) return;
        float blend = blend();
        fromX += (sourceX - fromX) * blend;
        fromY += (sourceY - fromY) * blend;
        fromMusic = music;
        previous = current;
        current = paints(colors);
        palette = colors.clone();
        sourceX = x;
        sourceY = y;
        music = playing;
        transition = SystemClock.uptimeMillis();
        invalidate();
    }
    private float blend() {
        float value = Math.min(1, (SystemClock.uptimeMillis() - transition) / 2200f);
        return value * value * (3 - 2 * value);
    }
    private static Paint[] paints(int[] colors) {
        Paint[] result = new Paint[4];
        float[] hsv = new float[3];
        for (int i = 0; i < result.length; i++) {
            Color.colorToHSV(colors[i % colors.length], hsv);
            hsv[1] = Math.min(0.85f, Math.max(0.22f, hsv[1]));
            hsv[2] = 0.48f + 0.07f * (i % 2);
            int color = Color.HSVToColor(220, hsv);
            int mid = (color & 0x00FFFFFF) | 0x78000000;
            result[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            result[i].setShader(new RadialGradient(0, 0, 1,
                    new int[] {color, mid, color & 0x00FFFFFF},
                    new float[] {0, 0.42f, 1}, Shader.TileMode.CLAMP));
        }
        return result;
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int height = Math.max(1, Math.round(h * 320f / Math.max(1, w)));
        // Retain old textures until Android releases their recorded display lists.
        texture = Bitmap.createBitmap(320, height, Bitmap.Config.ARGB_8888);
        texture.setHasAlpha(false);
        textureCanvas = new Canvas(texture);
        screen.set(0, 0, w, h);
        scrim.setShader(new LinearGradient(0, 0, 0, height,
                new int[] {0x50000000, 0x18000000, 0x68000000},
                new float[] {0, 0.5f, 1}, Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas) {
        if (textureCanvas == null) return;
        Canvas small = textureCanvas;
        float w = texture.getWidth(), h = texture.getHeight();
        small.drawColor(0xFF0B1018);
        float blend = blend();
        double time = (SystemClock.uptimeMillis() - startedAt) / 1000.0;
        if (previous != null && blend < 1) drawScene(small, previous, fromX, fromY, fromMusic, 1-blend, time, w, h);
        else previous = null;
        drawScene(small, current, fromX + (sourceX-fromX)*blend, fromY + (sourceY-fromY)*blend, music, previous == null ? 1 : blend, time, w, h);
        small.drawRect(0, 0, w, h, scrim);
        canvas.drawBitmap(texture, null, screen, texturePaint);
    }
    private void drawScene(Canvas canvas, Paint[] paints, float anchorX, float anchorY, boolean playing, float opacity, double time, float w, float h) {
        for (int i = 3; i >= 0; i--) {
            double phase = phases[i];
            float driftX = (float) (Math.sin(time * (0.043 + i*0.009) + phase) * 0.08 + Math.sin(time*0.029 + phase*2)*0.035);
            float driftY = (float) (Math.cos(time * (0.038 + i*0.006) + phase) * 0.10);
            float x, y;
            if (playing) {
                // A bright source at the artwork, spreading left and down into the room.
                x = anchorX + (i == 0 ? driftX*0.25f : i == 1 ? -0.23f+driftX : i == 2 ? -0.52f+driftX : 0.22f+driftX);
                y = anchorY + (i == 0 ? driftY*0.25f : i == 1 ? 0.23f+driftY : i == 2 ? 0.43f+driftY : 0.55f+driftY);
            } else {
                x = anchorX + (i == 0 ? driftX : i == 1 ? -0.35f+driftX : i == 2 ? 0.32f+driftX : driftX);
                y = anchorY + (i == 0 ? driftY : i == 1 ? -0.40f+driftY : i == 2 ? -0.25f+driftY : -0.75f+driftY);
            }
            float radiusX = w * (float) (0.58 + 0.05*Math.sin(time*0.031+phase));
            float radiusY = h * (float) (0.78 + 0.08*Math.cos(time*0.025+phase));
            matrix.setScale(radiusX, radiusY);
            matrix.postRotate((float) (Math.sin(time*0.022+phase)*18));
            matrix.postTranslate(x*w, y*h);
            paints[i].getShader().setLocalMatrix(matrix);
            paints[i].setAlpha(Math.round(255*opacity));
            canvas.drawRect(0, 0, w, h, paints[i]);
        }
    }
}
