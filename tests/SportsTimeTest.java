package com.ivantrj.glasslauncher;
import java.time.Instant;
public class SportsTimeTest {
    static long at(String when) { return Instant.parse(when).toEpochMilli(); }
    static void eq(Object actual,Object expected) {
        if (!expected.equals(actual)) throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args) {
        String zone="Europe/Berlin";
        long now=at("2026-10-24T12:00:00Z");
        eq(SportsTime.isToday(at("2026-10-23T22:00:00Z"),now,zone),true);
        eq(SportsTime.isToday(at("2026-10-23T21:59:59Z"),now,zone),false);
        eq(SportsTime.isToday(at("2026-10-24T22:00:00Z"),now,zone),false);
        eq(SportsTime.queryDates(at("2026-10-25T12:00:00Z"),zone),"20261024-20261025");
        eq(SportsTime.isToday(at("2026-10-25T22:59:59Z"),at("2026-10-25T12:00:00Z"),zone),true);
        eq(SportsTime.isToday(at("2026-10-25T23:00:00Z"),at("2026-10-25T12:00:00Z"),zone),false);
        eq(SportsTime.clock(at("2026-10-25T00:30:00Z"),zone),"02:30");
        eq(SportsTime.clock(at("2026-10-25T01:30:00Z"),zone),"02:30");
        eq(SportsTime.greeting(at("2026-10-24T02:59:00Z"),zone),"GOOD NIGHT BOYS");
        eq(SportsTime.greeting(at("2026-10-24T03:00:00Z"),zone),"GOOD MORNING BOYS");
        eq(SportsTime.greeting(at("2026-10-24T10:00:00Z"),zone),"GOOD AFTERNOON BOYS");
        eq(SportsTime.greeting(at("2026-10-24T16:00:00Z"),zone),"GOOD EVENING BOYS");
        eq(SportsTime.greeting(at("2026-10-24T20:00:00Z"),zone),"GOOD NIGHT BOYS");
        System.out.println("13 sports local-day/DST and greeting checks passed");
    }
}
