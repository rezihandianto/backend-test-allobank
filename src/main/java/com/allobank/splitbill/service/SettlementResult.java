package com.allobank.splitbill.service;

import com.allobank.splitbill.calc.BalanceCalculator.ParticipantBalance;
import com.allobank.splitbill.calc.SettlementCalculator.Transfer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SettlementResult(
        UUID groupId,
        String groupName,
        BigDecimal totalExpenses,
        int serviceChargePct,
        BigDecimal serviceChargeAmount,
        boolean settled,
        List<ParticipantBalance> balances,
        List<Transfer> transfers,
        Map<String, BigDecimal> categorySummary) {
}
