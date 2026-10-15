package org.enggis.enventx.api.dto;

import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.Role;

import java.util.UUID;

public record UserDto(
        UUID id,
        String name,
        String email,
        String company,
        Role role,
        String department,
        String designation
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCompany(),
                user.getRole(),
                user.getDepartment(),
                user.getDesignation()
        );
    }
}
