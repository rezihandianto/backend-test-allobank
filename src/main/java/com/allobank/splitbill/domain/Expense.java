package com.allobank.splitbill.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record Expense(
        UUID id,
        String description,
        String category,
        BigDecimal amount,
        String paidBy,
        SplitType splitType,
        Map<String, BigDecimal> shares,
        Instant createdAt) {

    public Expense {
        shares = Collections.unmodifiableMap(new LinkedHashMap<>(shares));
    }
}
