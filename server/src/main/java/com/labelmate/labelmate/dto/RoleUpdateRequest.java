package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull Role role) {
}
