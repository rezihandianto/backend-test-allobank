package com.allobank.splitbill.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        String from,
        String to,
        BigDecimal amount,
        String note,
        Instant createdAt) {
}
