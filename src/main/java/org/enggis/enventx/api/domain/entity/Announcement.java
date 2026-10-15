package org.enggis.enventx.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enggis.enventx.api.domain.enums.AnnouncementState;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "announcements",
        indexes = {
                @Index(name = "idx_announcements_state_publish_at", columnList = "state, publish_at"),
                @Index(name = "idx_announcements_created_at", columnList = "created_at"),
                @Index(name = "idx_announcements_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementState state = AnnouncementState.DRAFT;

    /** Visible to employees only once the state is PUBLISHED and this instant has passed. */
    @Column(name = "publish_at")
    private Instant publishAt;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
