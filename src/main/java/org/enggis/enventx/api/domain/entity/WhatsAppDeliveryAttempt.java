package org.enggis.enventx.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enggis.enventx.api.domain.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "whatsapp_delivery_attempts",
        indexes = {
                @Index(name = "idx_wa_attempts_announcement_id", columnList = "announcement_id"),
                @Index(name = "idx_wa_attempts_attempted_at", columnList = "attempted_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    /** Provider message ids of the accepted messages, comma separated. */
    @Column(name = "provider_message_id", length = 1000)
    private String providerMessageId;

    @Column(length = 1000)
    private String error;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempted_by", nullable = false)
    private User attemptedBy;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    void onCreate() {
        attemptedAt = Instant.now();
    }
}
