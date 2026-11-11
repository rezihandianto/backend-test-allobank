package com.allobank.splitbill.calc;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ServiceChargeCalculatorTest {

    @Test
    void matchesWorkedExampleFromTheBrief() {
        // johndoe47 -> unicode sum 850 -> 850 % 10 = 0
        assertThat(ServiceChargeCalculator.percentageFor("johndoe47")).isZero();
    }

    @Test
    void isCaseInsensitive() {
        assertThat(ServiceChargeCalculator.percentageFor("ReziHandianto"))
                .isEqualTo(ServiceChargeCalculator.percentageFor("rezihandianto"));
    }

    @Test
    void computesPercentageForConfiguredUsername() {
        // sum of code points of "rezihandianto" is 1392 -> 2
        assertThat(ServiceChargeCalculator.percentageFor("rezihandianto")).isEqualTo(2);
    }

    @Test
    void appliesPercentageToTotalWithTwoDecimalRounding() {
        assertThat(ServiceChargeCalculator.amountFor(new BigDecimal("390.00"), 2)).isEqualByComparingTo("7.80");
        assertThat(ServiceChargeCalculator.amountFor(new BigDecimal("0.33"), 5)).isEqualByComparingTo("0.02");
        assertThat(ServiceChargeCalculator.amountFor(new BigDecimal("390.00"), 0)).isEqualByComparingTo("0.00");
    }
}
