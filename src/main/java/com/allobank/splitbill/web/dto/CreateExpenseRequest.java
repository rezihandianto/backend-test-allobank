package com.allobank.splitbill.web.dto;

import com.allobank.splitbill.domain.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateExpenseRequest(
        @NotBlank @Size(max = 200) String description,
        @Size(max = 50) String category,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotBlank String paidBy,
        SplitType splitType,
        @Valid @Size(max = 50) List<SplitEntry> splits) {

    public record SplitEntry(
            @NotBlank String participant,
            @Digits(integer = 13, fraction = 4) BigDecimal value) {
    }
}
