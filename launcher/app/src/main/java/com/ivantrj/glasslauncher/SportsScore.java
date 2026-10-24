package com.ivantrj.glasslauncher;

/** Keep unknown or pre-match scores distinct from a genuine 0-0. */
final class SportsScore {
    static String format(String state,String home,String away) {
        if (!("in".equals(state) || "post".equals(state)) || home==null || away==null) return "";
        home=home.trim(); away=away.trim();
        if (!home.matches("[0-9]{1,3}") || !away.matches("[0-9]{1,3}")) return "";
        return Integer.parseInt(home)+" – "+Integer.parseInt(away);
    }
    private SportsScore() {}
}
