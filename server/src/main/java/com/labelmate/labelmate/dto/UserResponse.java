package com.labelmate.labelmate.dto;

import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String username,
        Role role,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getCreatedAt());
    }
}
