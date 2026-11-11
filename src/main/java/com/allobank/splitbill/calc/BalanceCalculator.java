package com.allobank.splitbill.calc;

import com.allobank.splitbill.domain.Expense;
import com.allobank.splitbill.domain.Money;
import com.allobank.splitbill.domain.Payment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BalanceCalculator {

    public record ParticipantBalance(
            String participant,
            BigDecimal paid,
            BigDecimal owed,
            BigDecimal paymentsMade,
            BigDecimal paymentsReceived,
            BigDecimal net) {
    }

    public List<ParticipantBalance> compute(List<String> participants, List<Expense> expenses, List<Payment> payments) {
        Map<String, BigDecimal> paid = zeroed(participants);
        Map<String, BigDecimal> owed = zeroed(participants);
        Map<String, BigDecimal> made = zeroed(participants);
        Map<String, BigDecimal> received = zeroed(participants);

        for (Expense expense : expenses) {
            paid.merge(expense.paidBy(), expense.amount(), BigDecimal::add);
            expense.shares().forEach((who, share) -> owed.merge(who, share, BigDecimal::add));
        }
        for (Payment payment : payments) {
            made.merge(payment.from(), payment.amount(), BigDecimal::add);
            received.merge(payment.to(), payment.amount(), BigDecimal::add);
        }

        List<ParticipantBalance> result = new ArrayList<>();
        for (String participant : participants) {
            BigDecimal net = paid.get(participant)
                    .subtract(owed.get(participant))
                    .add(made.get(participant))
                    .subtract(received.get(participant));
            result.add(new ParticipantBalance(participant, paid.get(participant), owed.get(participant),
                    made.get(participant), received.get(participant), net));
        }
        return result;
    }

    private static Map<String, BigDecimal> zeroed(List<String> participants) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        participants.forEach(p -> map.put(p, Money.ZERO));
        return map;
    }
}
