package org.enggis.enventx.api.security;

import org.enggis.enventx.api.domain.enums.Role;

import java.util.Optional;
import java.util.UUID;

/** Looks up the user's current role; empty means the account no longer exists. */
@FunctionalInterface
public interface RoleResolver {

    Optional<Role> currentRole(UUID userId);
}
