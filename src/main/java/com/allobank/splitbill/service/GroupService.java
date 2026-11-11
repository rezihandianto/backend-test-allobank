package com.allobank.splitbill.service;

import com.allobank.splitbill.calc.BalanceCalculator;
import com.allobank.splitbill.calc.BalanceCalculator.ParticipantBalance;
import com.allobank.splitbill.calc.ServiceChargeCalculator;
import com.allobank.splitbill.calc.SettlementCalculator;
import com.allobank.splitbill.calc.SplitCalculator;
import com.allobank.splitbill.calc.SplitCalculator.ShareInput;
import com.allobank.splitbill.config.SplitBillProperties;
import com.allobank.splitbill.domain.BillGroup;
import com.allobank.splitbill.domain.Expense;
import com.allobank.splitbill.domain.Money;
import com.allobank.splitbill.domain.Payment;
import com.allobank.splitbill.domain.SplitType;
import com.allobank.splitbill.exception.BusinessRuleException;
import com.allobank.splitbill.exception.InvalidRequestException;
import com.allobank.splitbill.exception.NotFoundException;
import com.allobank.splitbill.repository.GroupRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GroupService {

    private static final String UNCATEGORISED = "uncategorised";

    private final GroupRepository repository;
    private final Clock clock;
    private final SplitCalculator splitCalculator = new SplitCalculator();
    private final BalanceCalculator balanceCalculator = new BalanceCalculator();
    private final SettlementCalculator settlementCalculator = new SettlementCalculator();
    private final int serviceChargePct;

    public GroupService(GroupRepository repository, Clock clock, SplitBillProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.serviceChargePct = ServiceChargeCalculator.percentageFor(properties.githubUsername());
    }

    public BillGroup createGroup(String name, List<String> participants) {
        List<String> cleaned = participants.stream().map(String::trim).toList();
        Set<String> seen = new HashSet<>();
        for (String participant : cleaned) {
            if (participant.isEmpty()) {
                throw new InvalidRequestException("Participant names must not be blank");
            }
            if (!seen.add(participant.toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Duplicate participant: '" + participant + "'");
            }
        }
        if (cleaned.size() < 2) {
            throw new InvalidRequestException("A group needs at least 2 participants");
        }
        return repository.save(new BillGroup(UUID.randomUUID(), name.trim(), cleaned, clock.instant()));
    }

    public BillGroup getGroup(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Group " + id + " not found"));
    }

    public Expense addExpense(UUID groupId, NewExpenseCommand command) {
        BillGroup group = getGroup(groupId);
        BigDecimal amount = requireValidAmount(command.amount());
        SplitType type = command.splitType() == null ? SplitType.EQUAL : command.splitType();

        synchronized (group) {
            requireParticipant(group, command.paidBy(), "paid_by");
            List<ShareInput> inputs = resolveSplits(group, type, command.splits());
            Map<String, BigDecimal> shares = splitCalculator.calculate(amount, type, inputs);

            Expense expense = new Expense(UUID.randomUUID(), command.description().trim(), normaliseCategory(command.category()),
                    amount, command.paidBy(), type, shares, clock.instant());
            group.addExpense(expense);
            return expense;
        }
    }

    public List<Expense> listExpenses(UUID groupId) {
        BillGroup group = getGroup(groupId);
        synchronized (group) {
            return group.expenses();
        }
    }

    public Payment recordPayment(UUID groupId, NewPaymentCommand command) {
        BillGroup group = getGroup(groupId);
        BigDecimal amount = requireValidAmount(command.amount());

        synchronized (group) {
            requireParticipant(group, command.from(), "from");
            requireParticipant(group, command.to(), "to");
            if (command.from().equals(command.to())) {
                throw new InvalidRequestException("A participant cannot pay themselves");
            }

            Map<String, BigDecimal> nets = netBalances(group);
            BigDecimal outstandingDebt = nets.get(command.from()).negate().max(Money.ZERO);
            BigDecimal outstandingCredit = nets.get(command.to()).max(Money.ZERO);
            BigDecimal maxAllowed = outstandingDebt.min(outstandingCredit);
            if (amount.compareTo(maxAllowed) > 0) {
                throw new BusinessRuleException("Payment of " + amount.toPlainString() + " exceeds the outstanding amount; maximum allowed is "
                        + maxAllowed.toPlainString());
            }

            Payment payment = new Payment(UUID.randomUUID(), command.from(), command.to(), amount,
                    command.note() == null ? null : command.note().trim(), clock.instant());
            group.addPayment(payment);
            return payment;
        }
    }

    public List<Payment> listPayments(UUID groupId) {
        BillGroup group = getGroup(groupId);
        synchronized (group) {
            return group.payments();
        }
    }

    public SettlementResult settlement(UUID groupId) {
        BillGroup group = getGroup(groupId);
        List<Expense> expenses;
        List<Payment> payments;
        synchronized (group) {
            expenses = group.expenses();
            payments = group.payments();
        }

        List<ParticipantBalance> balances = balanceCalculator.compute(group.participants(), expenses, payments);
        Map<String, BigDecimal> nets = new LinkedHashMap<>();
        balances.forEach(b -> nets.put(b.participant(), b.net()));

        BigDecimal total = expenses.stream().map(Expense::amount).reduce(Money.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        expenses.forEach(e -> byCategory.merge(e.category(), e.amount(), BigDecimal::add));

        var transfers = settlementCalculator.minimize(nets);
        return new SettlementResult(group.id(), group.name(), total, serviceChargePct,
                ServiceChargeCalculator.amountFor(total, serviceChargePct), transfers.isEmpty(),
                balances, transfers, byCategory);
    }

    private Map<String, BigDecimal> netBalances(BillGroup group) {
        Map<String, BigDecimal> nets = new LinkedHashMap<>();
        balanceCalculator.compute(group.participants(), group.expenses(), group.payments())
                .forEach(b -> nets.put(b.participant(), b.net()));
        return nets;
    }

    private List<ShareInput> resolveSplits(BillGroup group, SplitType type, List<ShareInput> splits) {
        if (splits == null || splits.isEmpty()) {
            if (type != SplitType.EQUAL) {
                throw new InvalidRequestException("splits are required for split_type " + type);
            }
            return group.participants().stream().map(p -> new ShareInput(p, null)).toList();
        }
        splits.forEach(s -> requireParticipant(group, s.participant(), "splits.participant"));
        return splits;
    }

    private static BigDecimal requireValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("amount must be greater than 0");
        }
        if (Money.hasSubCentPrecision(amount)) {
            throw new InvalidRequestException("amount must have at most 2 decimal places");
        }
        return Money.of(amount);
    }

    private static void requireParticipant(BillGroup group, String participant, String field) {
        if (participant == null || !group.hasParticipant(participant)) {
            throw new InvalidRequestException("Unknown participant for " + field + ": '" + participant
                    + "'. Group participants: " + group.participants());
        }
    }

    private static String normaliseCategory(String category) {
        return category == null || category.isBlank() ? UNCATEGORISED : category.trim().toLowerCase(Locale.ROOT);
    }
}
