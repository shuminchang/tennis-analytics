package com.shuminapp.tennis.visualization;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResearchStatisticsTest {
    @Test void exactPositiveNegativeAndTieAwareRankCorrelations() {
        assertThat(ResearchStatistics.pearson(new double[]{1,2,3},new double[]{2,4,6})).isCloseTo(1,within(1e-12));
        assertThat(ResearchStatistics.pearson(new double[]{1,2,3},new double[]{3,2,1})).isCloseTo(-1,within(1e-12));
        assertThat(ResearchStatistics.spearman(new double[]{1,1,2},new double[]{1,2,3})).isCloseTo(Math.sqrt(3)/2,within(1e-12));
        assertThat(ResearchStatistics.ranks(new double[]{10,5,5})).containsExactly(3,1.5,1.5);
    }
    @Test void constantsAndInsufficientPairsAreNullNotZero() {
        assertThat(ResearchStatistics.pearson(new double[]{1,1,1},new double[]{1,2,3})).isNull();assertThat(ResearchStatistics.spearman(new double[]{1,2},new double[]{1,2})).isNull();
    }
    @Test void typeSevenQuantilesAndTukeyWhiskersUseObservedValues() {
        var box=ResearchStatistics.box("fixture",new double[]{1,2,3,4,100});assertThat(box.q1()).isEqualTo(2);assertThat(box.median()).isEqualTo(3);assertThat(box.q3()).isEqualTo(4);assertThat(box.lowerWhisker()).isEqualTo(1);assertThat(box.upperWhisker()).isEqualTo(4);assertThat(box.outliers()).containsExactly(100.0);
        assertThat(ResearchStatistics.quantile(new double[]{1,2,3,4},.25)).isEqualTo(1.75);assertThat(ResearchStatistics.box("empty",new double[]{}).median()).isNull();
    }
    @Test void normalizedHexagonsPreserveAllCountsAndSixVertices() {
        var hex=ResearchStatistics.hexes(new double[]{1,1,3,5},new double[]{2,2,4,8});assertThat(hex.cells().stream().mapToLong(c->c.count()).sum()).isEqualTo(4);assertThat(hex.cells().stream().anyMatch(c->c.count()==2)).isTrue();for(var cell:hex.cells()) assertThat(cell.vertices()).hasSize(6);
        var constant=ResearchStatistics.hexes(new double[]{1,1,1},new double[]{2,2,2});assertThat(constant.cells()).hasSize(1);assertThat(constant.xDomain().min()).isLessThan(constant.xDomain().max());assertThat(ResearchStatistics.hexes(new double[]{},new double[]{}).cells()).isEmpty();
    }
}
