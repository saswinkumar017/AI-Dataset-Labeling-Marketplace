package com.labelmate.labelmate.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record AutoLabelRequest(
        @DecimalMin(value = "0", message = "confidence must be between 0 and 100")
        @DecimalMax(value = "100", message = "confidence must be between 0 and 100")
        BigDecimal confidence) {
}
