package com.shuminapp.tennis.features;

public final class EloMath {
    private EloMath() { }
    public static double expected(double rating,double opponent) { return 1/(1+Math.pow(10,(opponent-rating)/400)); }
    public static double k(int matches) { return 250/Math.pow(matches+5,.4); }
    public static double after(double rating,double opponent,int matches,boolean won) { return rating+k(matches)*((won?1:0)-expected(rating,opponent)); }
}
