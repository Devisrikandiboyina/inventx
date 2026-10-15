package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.WhatsAppDeliveryAttempt;
import org.enggis.enventx.api.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppDeliveryAttemptRepository extends JpaRepository<WhatsAppDeliveryAttempt, UUID> {

    Optional<WhatsAppDeliveryAttempt> findFirstByOrderByAttemptedAtDesc();

    boolean existsByAnnouncementIdAndStatusInAndAttemptedAtAfter(
            UUID announcementId, Collection<DeliveryStatus> statuses, Instant after);
}
