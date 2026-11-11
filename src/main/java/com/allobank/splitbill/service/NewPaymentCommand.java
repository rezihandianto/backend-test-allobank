package com.allobank.splitbill.service;

import java.math.BigDecimal;

public record NewPaymentCommand(String from, String to, BigDecimal amount, String note) {
}
