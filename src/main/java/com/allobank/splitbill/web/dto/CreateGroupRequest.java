package com.allobank.splitbill.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Size(min = 2, max = 50) List<@NotBlank @Size(max = 50) String> participants) {
}
