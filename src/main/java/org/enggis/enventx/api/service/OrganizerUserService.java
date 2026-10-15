package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.AuditAction;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.PagedResponse;
import org.enggis.enventx.api.dto.UserDto;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class OrganizerUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> list(String q, String role, int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            var pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\')));
        }
        if (role != null && !role.isBlank()) {
            var parsed = parseRole(role);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), parsed));
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("name").ascending().and(Sort.by("id")));
        return PagedResponse.from(users.findAll(spec, pageable), UserDto::from);
    }

    /**
     * Both rows are locked in id order so two simultaneous changes cannot interleave (for example two admins
     * demoting each other). The actor's role is re-read under the lock, never taken from the token.
     */
    @Transactional
    public UserDto changeRole(UUID targetId, UUID actorId, Role newRole) {
        if (actorId.equals(targetId)) {
            throw error(HttpStatus.FORBIDDEN, "CANNOT_CHANGE_OWN_ROLE");
        }
        var locked = Stream.of(actorId, targetId).sorted()
                .map(id -> users.findByIdForUpdate(id).orElse(null)).toList();
        var actor = locked.stream().filter(u -> u != null && u.getId().equals(actorId)).findFirst()
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        var target = locked.stream().filter(u -> u != null && u.getId().equals(targetId)).findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        if (actor.getRole() != Role.ORGANIZER && actor.getRole() != Role.ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        if ((target.getRole() == Role.ADMIN || newRole == Role.ADMIN) && actor.getRole() != Role.ADMIN) {
            throw error(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED");
        }
        var previous = target.getRole();
        if (previous == newRole) {
            return UserDto.from(target);
        }
        target.setRole(newRole);
        users.saveAndFlush(target);
        audit.record(actor, AuditAction.ROLE_CHANGED, "USER", target.getId().toString(),
                target.getEmail() + ": " + previous + " -> " + newRole);
        return UserDto.from(target);
    }

    private static Role parseRole(String role) {
        try {
            return Role.valueOf(role.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ROLE");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
