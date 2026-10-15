package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    /** Single UPDATE, so it is idempotent and never touches another user's rows. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllRead(@Param("recipientId") UUID recipientId);
}
