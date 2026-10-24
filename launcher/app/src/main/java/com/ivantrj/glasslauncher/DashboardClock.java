package com.ivantrj.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Calendar;
import java.util.TimeZone;

/** Quiet analog clock; refreshed by Home's existing minute/time-change receiver. */
public class DashboardClock extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TimeZone zone = TimeZone.getDefault();
    void setTimeZone(String name) { zone = TimeZone.getTimeZone(name); invalidate(); }
    public DashboardClock(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth()/2f, cy = getHeight()/2f;
        float radius = Math.min(cx, cy) * 0.88f;
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i*30-90);
            float inner = radius * (i%3 == 0 ? 0.84f : 0.91f);
            paint.setColor(i%3 == 0 ? 0xD9B8C8F2 : 0x80B8C8F2);
            paint.setStrokeWidth(radius*0.016f);
            canvas.drawLine(cx+(float)Math.cos(angle)*inner, cy+(float)Math.sin(angle)*inner,
                    cx+(float)Math.cos(angle)*radius, cy+(float)Math.sin(angle)*radius, paint);
        }
        Calendar time = Calendar.getInstance(zone);
        float minute = time.get(Calendar.MINUTE);
        float hour = time.get(Calendar.HOUR) + minute/60f;
        hand(canvas, cx, cy, radius*0.52f, hour*30, radius*0.045f, 0xFFB4DBF2);
        hand(canvas, cx, cy, radius*0.76f, minute*6, radius*0.025f, 0xFFA8E5CF);
        paint.setColor(0xFFCAD5F4);
        canvas.drawCircle(cx, cy, radius*0.052f, paint);
        paint.setColor(0xFF6F91A9);
        canvas.drawCircle(cx, cy, radius*0.022f, paint);
    }
    private void hand(Canvas canvas, float x, float y, float length, float degrees, float width, int color) {
        double angle = Math.toRadians(degrees-90);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        canvas.drawLine(x-(float)Math.cos(angle)*length*0.12f, y-(float)Math.sin(angle)*length*0.12f,
                x+(float)Math.cos(angle)*length, y+(float)Math.sin(angle)*length, paint);
    }
}
