package com.ivantrj.glasslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import java.util.Locale;

/** Draws time from the latest playback event; never queries Spotify on a timer. */
public class PlaybackProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private PlaybackState state;
    private long duration;
    private ValueAnimator clock;
    private boolean visible;
    public PlaybackProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setTypeface(Fonts.outfit(context, 400));
        paint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
    }
    public void setPlayback(PlaybackState state, long duration) {
        this.state = state;
        this.duration = Math.max(0, duration);
        restart();
    }
    @Override public void onVisibilityAggregated(boolean value) {
        super.onVisibilityAggregated(value);
        visible = value;
        restart();
    }
    @Override protected void onDetachedFromWindow() {
        if (clock != null) clock.cancel();
        super.onDetachedFromWindow();
    }
    private void restart() {
        if (clock != null) clock.cancel();
        invalidate();
        if (!visible || state == null || duration <= 0) return;
        clock = ValueAnimator.ofFloat(0, 1);
        clock.setDuration(1000);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.addUpdateListener(a -> invalidate());
        clock.start();
    }
    private long position() {
        if (state == null) return 0;
        long position = Math.max(0, state.getPosition());
        long updated = state.getLastPositionUpdateTime();
        if (state.getState() == PlaybackState.STATE_PLAYING && updated > 0) {
            position += (long) (Math.max(0, SystemClock.elapsedRealtime() - updated) * state.getPlaybackSpeed());
        }
        return duration > 0 ? Math.min(duration, Math.max(0, position)) : Math.max(0, position);
    }
    private static String time(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long position = position();
        float bar = 2 * density;
        paint.setColor(0x33FFFFFF);
        canvas.drawRoundRect(0, 0, getWidth(), bar, bar, bar, paint);
        paint.setColor(0xFF1ED760);
        if (duration > 0) canvas.drawRoundRect(0, 0, getWidth() * (float) position / duration, bar, bar, bar, paint);
        paint.setColor(0x99FFFFFF);
        float baseline = Math.min(17 * density, getHeight() - 2 * density);
        canvas.drawText(time(position), 0, baseline, paint);
        String total = duration > 0 ? time(duration) : "--:--";
        canvas.drawText(total, getWidth() - paint.measureText(total), baseline, paint);
    }
}
