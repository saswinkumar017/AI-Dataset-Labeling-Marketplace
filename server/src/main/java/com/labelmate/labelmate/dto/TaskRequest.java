package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.Size;

public record TaskRequest(
        @Size(max = 5000, message = "itemData must be at most 5000 characters")
        String itemData,

        Integer itemIndex) {
}
