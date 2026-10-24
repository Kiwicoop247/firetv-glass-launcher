package com.ivantrj.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** North-up, 50 km relative plot. Positions come only from the current public feed. */
public class FlightRadarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path aircraft = new Path();
    private List<DashboardData.Flight> flights = new ArrayList<>();
    public FlightRadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(Fonts.outfit(context,400));
        aircraft.moveTo(0,-5); aircraft.lineTo(2,0); aircraft.lineTo(6,3);
        aircraft.lineTo(6,4); aircraft.lineTo(1,2); aircraft.lineTo(1,6);
        aircraft.lineTo(-1,6); aircraft.lineTo(-1,2); aircraft.lineTo(-6,4);
        aircraft.lineTo(-6,3); aircraft.lineTo(-2,0); aircraft.close();
    }
    void setFlights(List<DashboardData.Flight> value) { flights = new ArrayList<>(value); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(cx,cy)*0.81f;
        float density=getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density*0.7f);
        paint.setColor(0x407FD6C4);
        for (int i=1;i<=3;i++) canvas.drawCircle(cx,cy,r*i/3,paint);
        canvas.drawLine(cx-r,cy,cx+r,cy,paint);
        canvas.drawLine(cx,cy-r,cx,cy+r,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFB4E7D5);
        canvas.drawCircle(cx,cy,2*density,paint);
        paint.setTextSize(8*density);
        canvas.drawText("N",cx-3*density,cy-r-3*density,paint);
        for (int i=flights.size()-1;i>=0;i--) {
            DashboardData.Flight flight=flights.get(i);
            canvas.save();
            canvas.translate(cx+(float)(flight.east/50*r),cy-(float)(flight.north/50*r));
            canvas.rotate((float)flight.track);
            canvas.scale(density*0.75f,density*0.75f);
            paint.setColor(i==0 ? 0xFFFFDF92 : 0xFF9ACEE8);
            canvas.drawPath(aircraft,paint);
            canvas.restore();
        }
    }
}
