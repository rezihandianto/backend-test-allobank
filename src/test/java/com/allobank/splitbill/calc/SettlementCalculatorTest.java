package com.allobank.splitbill.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allobank.splitbill.calc.SettlementCalculator.Transfer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    private final SettlementCalculator calculator = new SettlementCalculator();

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Map<String, BigDecimal> nets(Object... pairs) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], d((String) pairs[i + 1]));
        }
        return map;
    }

    @Test
    void settlesSimpleGroupDinner() {
        // Alice paid 300 for dinner shared equally by Alice, Bob, Carol
        List<Transfer> transfers = calculator.minimize(nets("Alice", "200.00", "Bob", "-100.00", "Carol", "-100.00"));

        assertThat(transfers).containsExactly(
                new Transfer("Bob", "Alice", d("100.00")),
                new Transfer("Carol", "Alice", d("100.00")));
    }

    @Test
    void collapsesDebtChainIntoSingleTransfer() {
        // A owes B 10 and B owes C 10 -> A should simply pay C 10
        List<Transfer> transfers = calculator.minimize(nets("A", "-10.00", "B", "0.00", "C", "10.00"));

        assertThat(transfers).containsExactly(new Transfer("A", "C", d("10.00")));
    }

    @Test
    void returnsNoTransfersWhenEveryoneIsSquare() {
        assertThat(calculator.minimize(nets("A", "0.00", "B", "0.00"))).isEmpty();
    }

    @Test
    void splitsLargeDebtAcrossMultipleCreditors() {
        List<Transfer> transfers = calculator.minimize(nets("A", "-70.00", "B", "50.00", "C", "20.00"));

        assertThat(transfers).containsExactly(
                new Transfer("A", "B", d("50.00")),
                new Transfer("A", "C", d("20.00")));
    }

    @Test
    void rejectsBalancesThatDoNotSumToZero() {
        assertThatThrownBy(() -> calculator.minimize(nets("A", "10.00", "B", "-5.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void randomisedBalancesAreFullySettledWithAtMostNMinusOneTransfers() {
        Random random = new Random(42);
        for (int run = 0; run < 2_000; run++) {
            int people = 2 + random.nextInt(8);
            Map<String, BigDecimal> balances = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < people - 1; i++) {
                BigDecimal value = BigDecimal.valueOf(random.nextInt(200_001) - 100_000, 2);
                balances.put("P" + i, value);
                sum = sum.add(value);
            }
            balances.put("P" + (people - 1), sum.negate());

            Map<String, BigDecimal> remaining = new LinkedHashMap<>(balances);
            List<Transfer> transfers = calculator.minimize(balances);

            assertThat(transfers).hasSizeLessThanOrEqualTo(people - 1);
            for (Transfer transfer : transfers) {
                assertThat(transfer.amount().signum()).isPositive();
                remaining.merge(transfer.from(), transfer.amount(), BigDecimal::add);
                remaining.merge(transfer.to(), transfer.amount().negate(), BigDecimal::add);
            }
            assertThat(remaining.values()).allSatisfy(value -> assertThat(value.signum()).isZero());
        }
    }
}
