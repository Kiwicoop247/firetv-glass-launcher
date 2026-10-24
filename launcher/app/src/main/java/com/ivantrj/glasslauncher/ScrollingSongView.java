package com.ivantrj.glasslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Long titles scroll right to left; titles that fit stay still. */
public class ScrollingSongView extends View {
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stampPaint = new Paint();
    private Bitmap lettering;
    private float distance;
    private boolean scrollNeeded;
    private String song = "";
    private float offset;
    private ValueAnimator animator;
    private boolean visible;
    public ScrollingSongView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xE6FFFFFF);
        paint.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(Fonts.outfit(context, 400));
    }
    public void setSong(String value) {
        if (song.equals(value)) return;
        song = value;
        setContentDescription(value);
        renderLettering();
        restart();
    }
    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        restart();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        renderLettering();
        restart();
    }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
    private void restart() {
        if (animator != null) animator.cancel();
        offset = 0;
        invalidate();
        if (!visible || !scrollNeeded || song.isEmpty() || getWidth() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        animator = ValueAnimator.ofFloat(0, distance);
        animator.setDuration(Math.round(distance / (18 * density) * 1000));
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            float next = Math.round((float) a.getAnimatedValue());
            if (next != offset) { offset = next; invalidate(); }
        });
        animator.start();
    }
    private void renderLettering() {
        if (getHeight() == 0 || song.isEmpty()) { lettering = null; scrollNeeded = false; return; }
        // Rasterize the letter shapes once. Scrolling only moves this pixel-aligned strip.
        String rendered = TextUtils.ellipsize(song, paint, 4092, TextUtils.TruncateAt.END).toString();
        int width = Math.max(1, (int) Math.ceil(paint.measureText(rendered)) + 2);
        scrollNeeded = width > getWidth();
        lettering = Bitmap.createBitmap(width, getHeight(), Bitmap.Config.ARGB_8888);
        Canvas strip = new Canvas(lettering);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = Math.round((getHeight() - metrics.bottom - metrics.top) / 2);
        strip.drawText(rendered, 0, baseline, paint);
        float density = getResources().getDisplayMetrics().density;
        distance = Math.round(Math.max(width + 48 * density, getWidth() + 48 * density));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (lettering == null) return;
        canvas.drawBitmap(lettering, -offset, 0, stampPaint);
        if (scrollNeeded) canvas.drawBitmap(lettering, distance - offset, 0, stampPaint);
    }
}
