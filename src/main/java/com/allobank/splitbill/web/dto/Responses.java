package com.allobank.splitbill.web.dto;

import com.allobank.splitbill.domain.BillGroup;
import com.allobank.splitbill.domain.Expense;
import com.allobank.splitbill.domain.Payment;
import com.allobank.splitbill.domain.SplitType;
import com.allobank.splitbill.service.SettlementResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Responses {

    private Responses() {
    }

    public record GroupResponse(UUID id, String name, List<String> participants, Instant createdAt) {
        public static GroupResponse from(BillGroup group) {
            return new GroupResponse(group.id(), group.name(), group.participants(), group.createdAt());
        }
    }

    public record ShareLine(String participant, BigDecimal amount) {
    }

    public record ExpenseResponse(UUID id, String description, String category, BigDecimal amount, String paidBy,
                                  SplitType splitType, List<ShareLine> shares, Instant createdAt) {
        public static ExpenseResponse from(Expense expense) {
            List<ShareLine> shares = expense.shares().entrySet().stream()
                    .map(e -> new ShareLine(e.getKey(), e.getValue())).toList();
            return new ExpenseResponse(expense.id(), expense.description(), expense.category(), expense.amount(),
                    expense.paidBy(), expense.splitType(), shares, expense.createdAt());
        }
    }

    public record PaymentResponse(UUID id, String from, String to, BigDecimal amount, String note, Instant createdAt) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(payment.id(), payment.from(), payment.to(), payment.amount(), payment.note(),
                    payment.createdAt());
        }
    }

    public record BalanceLine(String participant, BigDecimal paid, BigDecimal owed, BigDecimal paymentsMade,
                              BigDecimal paymentsReceived, BigDecimal net) {
    }

    public record TransferLine(String from, String to, BigDecimal amount) {
    }

    public record SettlementResponse(UUID groupId, String groupName, BigDecimal totalExpenses, int serviceChargePct,
                                     BigDecimal serviceChargeAmount, boolean settled, List<BalanceLine> balances,
                                     List<TransferLine> transfers, Map<String, BigDecimal> categorySummary) {
        public static SettlementResponse from(SettlementResult r) {
            return new SettlementResponse(r.groupId(), r.groupName(), r.totalExpenses(), r.serviceChargePct(),
                    r.serviceChargeAmount(), r.settled(),
                    r.balances().stream().map(b -> new BalanceLine(b.participant(), b.paid(), b.owed(),
                            b.paymentsMade(), b.paymentsReceived(), b.net())).toList(),
                    r.transfers().stream().map(t -> new TransferLine(t.from(), t.to(), t.amount())).toList(),
                    r.categorySummary());
        }
    }
}
