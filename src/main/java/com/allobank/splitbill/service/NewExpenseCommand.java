package com.allobank.splitbill.service;

import com.allobank.splitbill.calc.SplitCalculator.ShareInput;
import com.allobank.splitbill.domain.SplitType;
import java.math.BigDecimal;
import java.util.List;

public record NewExpenseCommand(
        String description,
        String category,
        BigDecimal amount,
        String paidBy,
        SplitType splitType,
        List<ShareInput> splits) {
}
