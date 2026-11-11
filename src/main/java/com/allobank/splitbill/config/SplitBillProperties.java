package com.allobank.splitbill.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "splitbill")
public record SplitBillProperties(@NotBlank String githubUsername) {
}
