package org.enggis.enventx.api.repository;

import jakarta.persistence.LockModeType;
import org.enggis.enventx.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    /** Cheap per-request lookup so role changes apply immediately instead of when the token expires. */
    @Query("SELECT u.role FROM User u WHERE u.id = :id")
    Optional<org.enggis.enventx.api.domain.enums.Role> findRoleById(@Param("id") UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Row lock (SELECT ... FOR UPDATE) used as a per-user mutex; call inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
