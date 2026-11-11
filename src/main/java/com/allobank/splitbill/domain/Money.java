package com.allobank.splitbill.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** True when the value needs more than 2 decimal places (i.e. rounding would lose information). */
    public static boolean hasSubCentPrecision(BigDecimal value) {
        return value.stripTrailingZeros().scale() > SCALE;
    }
}
