package com.allobank.splitbill.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BillGroup {

    private final UUID id;
    private final String name;
    private final List<String> participants;
    private final Instant createdAt;
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();

    public BillGroup(UUID id, String name, List<String> participants, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.participants = List.copyOf(participants);
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<String> participants() {
        return participants;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean hasParticipant(String participant) {
        return participants.contains(participant);
    }

    public void addExpense(Expense expense) {
        expenses.add(expense);
    }

    public void addPayment(Payment payment) {
        payments.add(payment);
    }

    public List<Expense> expenses() {
        return List.copyOf(expenses);
    }

    public List<Payment> payments() {
        return List.copyOf(payments);
    }
}
