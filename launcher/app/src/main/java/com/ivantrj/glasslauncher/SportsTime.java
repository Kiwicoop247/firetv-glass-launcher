package com.ivantrj.glasslauncher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Local calendar-day boundaries also work on the 23/25-hour DST days. */
final class SportsTime {
    static LocalDate today(long now, String zone) {
        return Instant.ofEpochMilli(now).atZone(ZoneId.of(zone)).toLocalDate();
    }
    static boolean isToday(long start, long now, String zone) {
        return today(start,zone).equals(today(now,zone));
    }
    static String queryDates(long now, String zone) {
        LocalDate date=today(now,zone);
        ZoneId local=ZoneId.of(zone);
        DateTimeFormatter fmt=DateTimeFormatter.BASIC_ISO_DATE;
        String first=date.atStartOfDay(local).withZoneSameInstant(ZoneId.of("UTC")).toLocalDate().format(fmt);
        String last=date.plusDays(1).atStartOfDay(local).minusNanos(1)
                .withZoneSameInstant(ZoneId.of("UTC")).toLocalDate().format(fmt);
        return first.equals(last)?first:first+"-"+last;
    }
    static String clock(long start, String zone) {
        return DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT)
                .format(Instant.ofEpochMilli(start).atZone(ZoneId.of(zone)));
    }
    static String greeting(long now, String zone) {
        int hour=Instant.ofEpochMilli(now).atZone(ZoneId.of(zone)).getHour();
        return "GOOD "+(hour<5?"NIGHT":hour<12?"MORNING":hour<18?"AFTERNOON":hour<22?"EVENING":"NIGHT")+" BOYS";
    }
    private SportsTime() {}
}
