package com.allobank.splitbill.calc;

import com.allobank.splitbill.domain.Money;
import com.allobank.splitbill.domain.SplitType;
import com.allobank.splitbill.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class SplitCalculator {

    public record ShareInput(String participant, BigDecimal value) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Map<String, BigDecimal> calculate(BigDecimal amount, SplitType type, List<ShareInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new InvalidRequestException("An expense must be shared by at least one participant");
        }
        requireUnique(inputs);
        BigDecimal total = Money.of(amount);
        return switch (type) {
            case EQUAL -> allocate(total, inputs, input -> BigDecimal.ONE);
            case PERCENTAGE -> percentage(total, inputs);
            case EXACT -> exact(total, inputs);
        };
    }

    private Map<String, BigDecimal> percentage(BigDecimal total, List<ShareInput> inputs) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ShareInput input : inputs) {
            BigDecimal value = requirePositiveValue(input, "percentage");
            if (value.compareTo(HUNDRED) > 0) {
                throw new InvalidRequestException("Percentage for '" + input.participant() + "' cannot exceed 100");
            }
            sum = sum.add(value);
        }
        if (sum.compareTo(HUNDRED) != 0) {
            throw new InvalidRequestException("Percentages must total 100 but total " + sum.stripTrailingZeros().toPlainString());
        }
        return allocate(total, inputs, ShareInput::value);
    }

    private Map<String, BigDecimal> exact(BigDecimal total, List<ShareInput> inputs) {
        BigDecimal sum = BigDecimal.ZERO;
        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        for (ShareInput input : inputs) {
            BigDecimal value = requirePositiveValue(input, "amount");
            if (Money.hasSubCentPrecision(value)) {
                throw new InvalidRequestException("Amount for '" + input.participant() + "' has more than 2 decimal places");
            }
            BigDecimal share = Money.of(value);
            shares.put(input.participant(), share);
            sum = sum.add(share);
        }
        if (sum.compareTo(total) != 0) {
            throw new InvalidRequestException("Exact amounts must total " + total.toPlainString() + " but total " + sum.toPlainString());
        }
        return shares;
    }

    private Map<String, BigDecimal> allocate(BigDecimal total, List<ShareInput> inputs, Function<ShareInput, BigDecimal> weightOf) {
        BigDecimal totalCents = total.movePointRight(Money.SCALE).setScale(0, RoundingMode.UNNECESSARY);
        List<BigDecimal> weights = inputs.stream().map(weightOf).toList();
        BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        int n = inputs.size();
        BigDecimal[] cents = new BigDecimal[n];
        BigDecimal[] remainders = new BigDecimal[n];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal exactCents = totalCents.multiply(weights.get(i)).divide(totalWeight, 20, RoundingMode.DOWN);
            cents[i] = exactCents.setScale(0, RoundingMode.DOWN);
            remainders[i] = exactCents.subtract(cents[i]);
            allocated = allocated.add(cents[i]);
        }

        int leftover = totalCents.subtract(allocated).intValueExact();
        List<Integer> byRemainderDesc = new ArrayList<>(IntStream.range(0, n).boxed().toList());
        byRemainderDesc.sort(Comparator.<Integer, BigDecimal>comparing(i -> remainders[i]).reversed().thenComparing(i -> i));
        for (int k = 0; k < leftover; k++) {
            int idx = byRemainderDesc.get(k);
            cents[idx] = cents[idx].add(BigDecimal.ONE);
        }

        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            shares.put(inputs.get(i).participant(), Money.of(cents[i].movePointLeft(Money.SCALE)));
        }
        return shares;
    }

    private static BigDecimal requirePositiveValue(ShareInput input, String label) {
        if (input.value() == null) {
            throw new InvalidRequestException("A " + label + " is required for '" + input.participant() + "'");
        }
        if (input.value().signum() <= 0) {
            throw new InvalidRequestException("The " + label + " for '" + input.participant() + "' must be greater than 0");
        }
        return input.value();
    }

    private static void requireUnique(List<ShareInput> inputs) {
        Set<String> seen = new HashSet<>();
        for (ShareInput input : inputs) {
            if (!seen.add(input.participant())) {
                throw new InvalidRequestException("Participant '" + input.participant() + "' is listed more than once in splits");
            }
        }
    }
}
