package com.allobank.splitbill.calc;

import com.allobank.splitbill.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class ServiceChargeCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ServiceChargeCalculator() {
    }

    public static int percentageFor(String githubUsername) {
        int sum = githubUsername.trim().toLowerCase(Locale.ROOT).codePoints().sum();
        return sum % 10;
    }

    public static BigDecimal amountFor(BigDecimal totalExpenses, int percentage) {
        return Money.of(totalExpenses
                .multiply(BigDecimal.valueOf(percentage))
                .divide(HUNDRED, Money.SCALE, RoundingMode.HALF_UP));
    }
}
