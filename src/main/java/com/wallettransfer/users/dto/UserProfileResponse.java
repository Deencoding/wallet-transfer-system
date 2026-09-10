package com.wallettransfer.users.dto;

import com.wallettransfer.users.model.RoleName;
import com.wallettransfer.users.model.User;
import com.wallettransfer.users.model.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id, String email, UserStatus status, Set<RoleName> roles, Instant createdAt, Instant updatedAt) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus(),
                user.getRoleNames(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
