package com.ivantrj.glasslauncher;

final class PanelNavigation {
    static final int HOME=0, RECENTS=1, DRAWER=2, SETTINGS=3;
    static int right(int state) {
        if (state==HOME) return RECENTS;
        if (state==RECENTS) return DRAWER;
        return state;
    }
    static int left(int state) { return HOME; }
    static int home(int state) { return HOME; }
    static int down(int state) { return state==HOME ? SETTINGS : state; }
    static int up(int state) { return state==SETTINGS ? HOME : state; }
    private PanelNavigation() {}
}
