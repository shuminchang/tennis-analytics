package com.shuminapp.tennis.prediction;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import static com.shuminapp.tennis.prediction.MatchupRepository.*;

public final class MatchupMath {
    private MatchupMath() { }
    public static final double[] STANDARD={Math.log(10),0,0,0,0,0};
    public static boolean experienced(Inputs p,LocalDate asOf) {return p.overall()!=null && p.overall().matches()>=10 && ChronoUnit.DAYS.between(p.overall().date(),asOf)<=365;}
    public static boolean physical(Inputs p) {return p.age()!=null && p.height()!=null;}
    public static double[] features(Inputs first,Inputs second,LocalDate asOf,int bestOf) {
        double overall=(first.overall().value()-second.overall().value())/400;
        boolean surface=first.surface()!=null && second.surface()!=null && Math.min(first.surface().matches(),second.surface().matches())>=5 && ChronoUnit.DAYS.between(first.surface().date(),asOf)<=730 && ChronoUnit.DAYS.between(second.surface().date(),asOf)<=730;
        boolean ages=first.age()!=null && second.age()!=null,heights=first.height()!=null && second.height()!=null;
        return new double[]{overall,surface?(first.surface().value()-second.surface().value())/400:0,ages?(first.age()-second.age())/10:0,ages?(Math.pow(first.age()-27,2)-Math.pow(second.age()-27,2))/100:0,heights?(first.height()-second.height())/10:0,bestOf==5?overall:0};
    }
    public static double probability(double[] features,double[] coefficients) {
        if(features.length!=6 || coefficients.length!=6)throw new IllegalArgumentException();double score=0;
        for(int i=0;i<6;i++){if(!Double.isFinite(features[i]) || !Double.isFinite(coefficients[i]))throw new IllegalArgumentException();score+=features[i]*coefficients[i];}
        return 1/(1+Math.exp(-Math.max(-35,Math.min(35,score))));
    }
}
