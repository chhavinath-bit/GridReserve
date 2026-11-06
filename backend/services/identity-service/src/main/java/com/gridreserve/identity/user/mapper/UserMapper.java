package com.gridreserve.identity.user.mapper;

import com.gridreserve.identity.user.dto.UserResponse;
import com.gridreserve.identity.user.entity.AppUser;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }
}
