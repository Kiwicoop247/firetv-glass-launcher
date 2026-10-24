package com.ivantrj.glasslauncher;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class DashboardTime {
    static String indiaLine(long now, String localZone) {
        TimeZone india = TimeZone.getTimeZone("Asia/Kolkata");
        TimeZone local = TimeZone.getTimeZone(localZone);
        SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.ROOT);
        time.setTimeZone(india);
        int delta = (india.getOffset(now)-local.getOffset(now))/60000;
        String difference = String.format(Locale.ROOT, "%s%dh%02d", delta>=0 ? "+" : "−", Math.abs(delta)/60, Math.abs(delta)%60);
        return "IST  " + time.format(new Date(now)) + "  (" + difference + ")";
    }
    private DashboardTime() {}
}
