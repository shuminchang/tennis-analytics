package com.shuminapp.tennis.prediction;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static com.shuminapp.tennis.prediction.MatchupRepository.*;
class MatchupMathTest {
    @Test void standardEloAndSwappingAreComplementary() {
        double[] x={1,.4,.2,-.3,1,0};assertThat(MatchupMath.probability(x,MatchupMath.STANDARD)).isCloseTo(10d/11,within(1e-12));
        double[] beta={2,.5,.1,-.2,.03,.3},reverse=java.util.Arrays.stream(x).map(v->-v).toArray();
        assertThat(MatchupMath.probability(x,beta)+MatchupMath.probability(reverse,beta)).isCloseTo(1,within(1e-12));assertThat(MatchupMath.probability(new double[6],beta)).isEqualTo(.5);
    }
    @Test void featuresUseOnlyQualifiedSurfaceAndPreserveSymmetry() {
        var day=LocalDate.of(2024,2,1);var a=new Inputs("ATP",1,"A",new Rating(1700,20,day.minusDays(30)),new Rating(1600,4,day.minusDays(30)),20d,180d,day.minusDays(30),day.minusDays(30));
        var b=new Inputs("ATP",2,"B",new Rating(1500,20,day.minusDays(30)),new Rating(1500,10,day.minusDays(30)),30d,170d,day.minusDays(30),day.minusDays(30));
        assertThat(MatchupMath.features(a,b,day,5)).containsExactly(.5,0,-1,.4,1,.5);
        assertThat(MatchupMath.features(b,a,day,5)).containsExactly(-.5,0,1,-.4,-1,-.5);
        assertThat(MatchupMath.experienced(a,day.plusYears(2))).isFalse();
    }
}
