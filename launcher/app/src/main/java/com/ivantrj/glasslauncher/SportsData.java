package com.ivantrj.glasslauncher;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public ESPN schedule feeds, refreshed only while Home is visible. No account/key. */
final class SportsData {
    static final class Event {
        final String title, league, state, score;
        final long start;
        final boolean timeKnown;
        Event(String title,String league,String state,long start,boolean timeKnown,String score) {
            this.title=title; this.league=league; this.state=state; this.start=start; this.timeKnown=timeKnown;
            this.score=score;
        }
        String timing(String zone) {
            return (timeKnown?SportsTime.clock(start,zone):"Time TBA")+" · "+league
                    +(state.isEmpty()?"":" · "+state);
        }
    }
    interface Listener { void changed(List<Event> events,String status); }
    private static final String[][] FEEDS={
            {"soccer/eng.1","Premier League"},{"soccer/ind.1","ISL"},
            {"soccer/uefa.champions","Champions League"},{"racing/f1","F1"}};
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final String zone;
    private volatile boolean running;
    private boolean busy;
    private volatile int generation;
    private long next;
    private String day="";
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now=System.currentTimeMillis();
            String localDay=SportsTime.today(now,zone).toString();
            if (!localDay.equals(day)) {
                day=localDay; next=0;
                listener.changed(new ArrayList<>(),"Loading today's schedule…");
            }
            if (!busy && android.os.SystemClock.elapsedRealtime()>=next) fetch(now,localDay);
            main.postDelayed(this,1000);
        }
    };
    SportsData(String zone,Listener listener) { this.zone=zone; this.listener=listener; }
    void start() { if (running) return; running=true; main.post(tick); }
    void stop() { running=false; generation++; busy=false; main.removeCallbacks(tick); }
    void destroy() { stop(); worker.shutdownNow(); }
    private void fetch(long now,String requestDay) {
        busy=true;
        next=android.os.SystemClock.elapsedRealtime()+120000;
        int request=generation;
        worker.execute(() -> {
            List<Event> events=new ArrayList<>();
            List<String> failed=new ArrayList<>();
            for (String[] feed:FEEDS) {
                if (Thread.currentThread().isInterrupted() || !running || request!=generation) return;
                boolean racing=feed[0].startsWith("racing");
                // Football rejects range queries. Fetch each UTC date intersecting the local day.
                // Racing returns the whole weekend; its sessions are filtered individually below.
                String[] dates=racing?new String[]{SportsTime.today(now,zone).toString().replace("-","")}
                        :SportsTime.queryDates(now,zone).split("-");
                boolean unavailable=false;
                for (String date:dates) {
                    if (!running || request!=generation) return;
                    try {
                        JSONObject json=DashboardData.read("https://site.api.espn.com/apis/site/v2/sports/"
                                +feed[0]+"/scoreboard?dates="+date+"&limit=100");
                        for (Event event:parse(json,feed[1],now,zone,racing)) {
                            boolean duplicate=false;
                            for (Event old:events) if (old.start==event.start && old.league.equals(event.league)
                                    && old.title.equals(event.title)) { duplicate=true; break; }
                            if (!duplicate) events.add(event);
                        }
                    } catch (Exception failure) { unavailable=true; }
                }
                if (unavailable) failed.add(feed[1]);
            }
            events.sort(Comparator.comparingLong(e -> e.start));
            String status=failed.isEmpty()?"ESPN · local start times":"Unavailable: "+android.text.TextUtils.join(", ",failed);
            main.post(() -> {
                if (request!=generation) return;
                busy=false;
                if (!running || !requestDay.equals(SportsTime.today(System.currentTimeMillis(),zone).toString())) return;
                if (!failed.isEmpty()) next=android.os.SystemClock.elapsedRealtime()+60000;
                listener.changed(events,status);
            });
        });
    }
    static List<Event> parse(JSONObject json,String league,long now,String zone,boolean racing) throws Exception {
        List<Event> events=new ArrayList<>();
        JSONArray all=json.getJSONArray("events");
        for (int i=0;i<all.length();i++) {
            JSONObject event=all.getJSONObject(i);
            JSONArray competitions=event.getJSONArray("competitions");
            for (int j=0;j<competitions.length();j++) {
                JSONObject c=competitions.getJSONObject(j);
                long start=java.time.OffsetDateTime.parse(c.optString("date",event.getString("date"))).toInstant().toEpochMilli();
                if (!SportsTime.isToday(start,now,zone)) continue;
                JSONObject status=c.optJSONObject("status");
                if (status==null) status=event.optJSONObject("status");
                JSONObject type=status==null?null:status.optJSONObject("type");
                String state=type==null?"":type.optString("state");
                String label=type==null?"":type.optString("shortDetail",type.optString("description"));
                String flag="in".equals(state)?"LIVE":"post".equals(state)?"Finished":"";
                if (label.toLowerCase(java.util.Locale.ROOT).contains("postpon")
                        || label.toLowerCase(java.util.Locale.ROOT).contains("cancel")) flag=label;
                String title,score="";
                if (racing) {
                    JSONObject session=c.optJSONObject("type");
                    String abbreviation=session==null?"Race":session.optString("abbreviation","Race");
                    title=event.optString("shortName",event.optString("name","Grand Prix"))+" · "+abbreviation;
                } else {
                    JSONArray teams=c.getJSONArray("competitors");
                    String home="",away="",homeScore="",awayScore="";
                    for (int k=0;k<teams.length();k++) {
                        JSONObject team=teams.getJSONObject(k);
                        JSONObject info=team.getJSONObject("team");
                        String name=info.optString("shortDisplayName",info.optString("displayName","Team"));
                        Object raw=team.opt("score");
                        String goals=raw instanceof JSONObject?((JSONObject)raw).optString("displayValue",
                                ((JSONObject)raw).optString("value","")):raw==null?"":raw.toString();
                        if ("home".equals(team.optString("homeAway"))) { home=name; homeScore=goals; }
                        else if ("away".equals(team.optString("homeAway"))) { away=name; awayScore=goals; }
                    }
                    title=home.isEmpty()||away.isEmpty()?event.optString("name","Match"):home+" v "+away;
                    if (!home.isEmpty() && !away.isEmpty()) score=SportsScore.format(state,homeScore,awayScore);
                }
                events.add(new Event(title,league,flag,start,c.optBoolean("timeValid",true),score));
                if (!racing) break;
            }
        }
        return events;
    }
}
