package com.example.ridehailing.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ridehailing.service.SurgePricing;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;

class SurgePropertiesTest {
    @Property
    void multiplierStaysBetweenOneAndCap(@ForAll @LongRange(min = 0, max = 100_000) long demand,
                                         @ForAll @LongRange(min = 0, max = 100_000) long supply,
                                         @ForAll @DoubleRange(min = 0, max = 5) double sensitivity,
                                         @ForAll @DoubleRange(min = 1, max = 10) double max) {
        double m = SurgePricing.multiplier(demand, supply, sensitivity, max);
        assertThat(m).isBetween(1.0, max + 0.005);
    }

    @Property
    void moreDemandNeverLowersPriceAndMoreSupplyNeverRaisesIt(@ForAll @LongRange(min = 0, max = 10_000) long demand,
                                                              @ForAll @LongRange(min = 0, max = 10_000) long supply,
                                                              @ForAll @LongRange(min = 1, max = 1_000) long delta) {
        assertThat(SurgePricing.multiplier(demand + delta, supply, 0.5, 3.0))
                .isGreaterThanOrEqualTo(SurgePricing.multiplier(demand, supply, 0.5, 3.0));
        assertThat(SurgePricing.multiplier(demand, supply + delta, 0.5, 3.0))
                .isLessThanOrEqualTo(SurgePricing.multiplier(demand, supply, 0.5, 3.0));
    }

    @Property
    void noSurgeWhenSupplyAtLeastDemand(@ForAll @LongRange(min = 0, max = 10_000) long demand,
                                        @ForAll @LongRange(min = 0, max = 10_000) long extra) {
        assertThat(SurgePricing.multiplier(demand, demand + extra, 0.5, 3.0)).isEqualTo(1.0);
    }

    @Property
    void cellCenterAlwaysMapsBackToSameCell(@ForAll @DoubleRange(min = -89.9, max = 89.9) double lat,
                                            @ForAll @DoubleRange(min = -179.9, max = 179.9) double lon) {
        double[] c = SurgePricing.cellCenter(lat, lon);
        assertThat(SurgePricing.cellId(c[0], c[1])).isEqualTo(SurgePricing.cellId(lat, lon));
        assertThat(Math.abs(c[0] - lat)).isLessThanOrEqualTo(SurgePricing.CELL_DEG);
        assertThat(Math.abs(c[1] - lon)).isLessThanOrEqualTo(SurgePricing.CELL_DEG);
    }

    @Property
    void fareIsMonotoneInDistanceAndMultiplier(@ForAll @DoubleRange(min = 0, max = 100_000) double meters,
                                               @ForAll @DoubleRange(min = 0, max = 10_000) double more,
                                               @ForAll @DoubleRange(min = 1, max = 3) double mult) {
        assertThat(SurgePricing.fare(meters + more, 2.5, 1.2, mult))
                .isGreaterThanOrEqualTo(SurgePricing.fare(meters, 2.5, 1.2, mult));
        assertThat(SurgePricing.fare(meters, 2.5, 1.2, mult)).isGreaterThanOrEqualTo(SurgePricing.fare(meters, 2.5, 1.2, 1.0));
    }
}
