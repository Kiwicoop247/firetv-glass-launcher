package com.ivantrj.glasslauncher;
public class PanelNavigationTest {
    public static void main(String[] args) {
        int home=PanelNavigation.HOME, recent=PanelNavigation.RECENTS, drawer=PanelNavigation.DRAWER, settings=PanelNavigation.SETTINGS;
        check(PanelNavigation.right(home),recent);
        check(PanelNavigation.right(recent),drawer);
        check(PanelNavigation.right(drawer),drawer);
        check(PanelNavigation.down(home),settings);
        check(PanelNavigation.down(recent),recent);
        check(PanelNavigation.down(drawer),drawer);
        check(PanelNavigation.up(settings),home);
        for (int state : new int[] {home,recent,drawer,settings}) {
            check(PanelNavigation.left(state),home);
            check(PanelNavigation.home(state),home);
        }
        System.out.println("15 sidebar navigation checks passed");
    }
    private static void check(int actual,int expected) { if(actual!=expected) throw new AssertionError(actual+" != "+expected); }
}
