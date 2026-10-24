package com.ivantrj.glasslauncher;
public class SportsScoreTest {
    static void check(String state,String home,String away,String expected) {
        String actual=SportsScore.format(state,home,away);
        if (!actual.equals(expected)) throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args) {
        check("in","0","0","0 – 0");
        check("post","2","1","2 – 1");
        check("pre","0","0","");
        check("in",null,"0","");
        check("post","","1","");
        check("in","-1","0","");
        check("post","N/A","2","");
        check("in"," 3 ","1","3 – 1");
        System.out.println("8 live/final/missing football score checks passed");
    }
}
