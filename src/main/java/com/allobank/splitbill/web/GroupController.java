package com.allobank.splitbill.web;

import com.allobank.splitbill.calc.SplitCalculator.ShareInput;
import com.allobank.splitbill.domain.BillGroup;
import com.allobank.splitbill.service.GroupService;
import com.allobank.splitbill.service.NewExpenseCommand;
import com.allobank.splitbill.service.NewPaymentCommand;
import com.allobank.splitbill.web.dto.CreateExpenseRequest;
import com.allobank.splitbill.web.dto.CreateGroupRequest;
import com.allobank.splitbill.web.dto.CreatePaymentRequest;
import com.allobank.splitbill.web.dto.Responses.ExpenseResponse;
import com.allobank.splitbill.web.dto.Responses.GroupResponse;
import com.allobank.splitbill.web.dto.Responses.PaymentResponse;
import com.allobank.splitbill.web.dto.Responses.SettlementResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        BillGroup group = service.createGroup(request.name(), request.participants());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(group.id()).toUri();
        return ResponseEntity.created(location).body(GroupResponse.from(group));
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@PathVariable UUID groupId) {
        return GroupResponse.from(service.getGroup(groupId));
    }

    @PostMapping("/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse addExpense(@PathVariable UUID groupId, @Valid @RequestBody CreateExpenseRequest request) {
        List<ShareInput> splits = request.splits() == null ? null
                : request.splits().stream().map(s -> new ShareInput(s.participant(), s.value())).toList();
        var command = new NewExpenseCommand(request.description(), request.category(), request.amount(),
                request.paidBy(), request.splitType(), splits);
        return ExpenseResponse.from(service.addExpense(groupId, command));
    }

    @GetMapping("/{groupId}/expenses")
    public List<ExpenseResponse> listExpenses(@PathVariable UUID groupId) {
        return service.listExpenses(groupId).stream().map(ExpenseResponse::from).toList();
    }

    @PostMapping("/{groupId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse recordPayment(@PathVariable UUID groupId, @Valid @RequestBody CreatePaymentRequest request) {
        var command = new NewPaymentCommand(request.from(), request.to(), request.amount(), request.note());
        return PaymentResponse.from(service.recordPayment(groupId, command));
    }

    @GetMapping("/{groupId}/payments")
    public List<PaymentResponse> listPayments(@PathVariable UUID groupId) {
        return service.listPayments(groupId).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{groupId}/settlement")
    public SettlementResponse settlement(@PathVariable UUID groupId) {
        return SettlementResponse.from(service.settlement(groupId));
    }
}
