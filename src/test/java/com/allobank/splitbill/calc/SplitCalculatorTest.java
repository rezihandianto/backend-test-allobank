package com.allobank.splitbill.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allobank.splitbill.calc.SplitCalculator.ShareInput;
import com.allobank.splitbill.domain.SplitType;
import com.allobank.splitbill.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

    private final SplitCalculator calculator = new SplitCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static ShareInput in(String name, String value) {
        return new ShareInput(name, value == null ? null : d(value));
    }

    private static BigDecimal sum(Map<String, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void equalSplitDistributesRemainderCentsWithoutLosingAny() {
        Map<String, BigDecimal> shares = calculator.calculate(d("100.00"), SplitType.EQUAL,
                List.of(in("A", null), in("B", null), in("C", null)));

        assertThat(shares).containsExactly(
                Map.entry("A", d("33.34")),
                Map.entry("B", d("33.33")),
                Map.entry("C", d("33.33")));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");
    }

    @Test
    void percentageSplitSumsExactlyToAmount() {
        Map<String, BigDecimal> shares = calculator.calculate(d("100.01"), SplitType.PERCENTAGE,
                List.of(in("A", "33.33"), in("B", "33.33"), in("C", "33.34")));

        assertThat(sum(shares)).isEqualByComparingTo("100.01");
    }

    @Test
    void exactSplitKeepsGivenAmounts() {
        Map<String, BigDecimal> shares = calculator.calculate(d("90.00"), SplitType.EXACT,
                List.of(in("A", "30"), in("C", "60.00")));

        assertThat(shares).containsExactly(Map.entry("A", d("30.00")), Map.entry("C", d("60.00")));
    }

    @Test
    void rejectsExactAmountsThatDoNotAddUp() {
        assertThatThrownBy(() -> calculator.calculate(d("90.00"), SplitType.EXACT, List.of(in("A", "30.00"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsPercentagesThatDoNotTotalHundred() {
        assertThatThrownBy(() -> calculator.calculate(d("90.00"), SplitType.PERCENTAGE, List.of(in("A", "50"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThatThrownBy(() -> calculator.calculate(d("10.00"), SplitType.EQUAL, List.of(in("A", null), in("A", null))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void equalSplitNeverLosesOrInventsACent() {
        Random random = new Random(7);
        for (int run = 0; run < 2_000; run++) {
            int people = 1 + random.nextInt(9);
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(10_000_000), 2);
            List<ShareInput> inputs = new ArrayList<>();
            for (int i = 0; i < people; i++) {
                inputs.add(in("P" + i, null));
            }

            assertThat(sum(calculator.calculate(amount, SplitType.EQUAL, inputs))).isEqualByComparingTo(amount);
        }
    }
}
