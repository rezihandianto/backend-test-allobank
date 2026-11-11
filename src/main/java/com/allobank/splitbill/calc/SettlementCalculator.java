package com.allobank.splitbill.calc;

import com.allobank.splitbill.domain.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class SettlementCalculator {

    public record Transfer(String from, String to, BigDecimal amount) {
    }

    private record Entry(String name, BigDecimal amount) {
    }

    private static final Comparator<Entry> LARGEST_FIRST =
            Comparator.comparing(Entry::amount).reversed().thenComparing(Entry::name);

    public List<Transfer> minimize(Map<String, BigDecimal> netBalances) {
        BigDecimal sum = netBalances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() != 0) {
            throw new IllegalStateException("Net balances must sum to zero but sum to " + sum.toPlainString());
        }

        PriorityQueue<Entry> creditors = new PriorityQueue<>(LARGEST_FIRST);
        PriorityQueue<Entry> debtors = new PriorityQueue<>(LARGEST_FIRST);
        netBalances.forEach((name, net) -> {
            if (net.signum() > 0) {
                creditors.add(new Entry(name, net));
            } else if (net.signum() < 0) {
                debtors.add(new Entry(name, net.negate()));
            }
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Entry creditor = creditors.poll();
            Entry debtor = debtors.poll();
            BigDecimal amount = creditor.amount().min(debtor.amount());
            transfers.add(new Transfer(debtor.name(), creditor.name(), Money.of(amount)));

            BigDecimal creditLeft = creditor.amount().subtract(amount);
            BigDecimal debtLeft = debtor.amount().subtract(amount);
            if (creditLeft.signum() > 0) {
                creditors.add(new Entry(creditor.name(), creditLeft));
            }
            if (debtLeft.signum() > 0) {
                debtors.add(new Entry(debtor.name(), debtLeft));
            }
        }
        return transfers;
    }
}
