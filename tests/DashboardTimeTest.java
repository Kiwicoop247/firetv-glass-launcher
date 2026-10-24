package com.ivantrj.glasslauncher;
import java.time.Instant;
public class DashboardTimeTest {
    public static void main(String[] args) {
        check("2026-10-24T12:00:00Z", "IST  17:30  (+3h30)");
        check("2026-10-25T00:30:00Z", "IST  06:00  (+3h30)");
        check("2026-10-25T01:30:00Z", "IST  07:00  (+4h30)");
        check("2026-12-24T12:00:00Z", "IST  17:30  (+4h30)");
        check("2026-03-29T00:30:00Z", "IST  06:00  (+4h30)");
        check("2026-03-29T01:30:00Z", "IST  07:00  (+3h30)");
        System.out.println("6 Local/IST daylight-saving boundary checks passed");
    }
    private static void check(String when,String expected) {
        String actual=DashboardTime.indiaLine(Instant.parse(when).toEpochMilli(),"Europe/Berlin");
        if (!expected.equals(actual)) throw new AssertionError(when+": "+actual);
    }
}
