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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stage_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage_configs_stage", columnNames = "stage"),
        indexes = {
                @Index(name = "idx_stage_configs_created_at", columnList = "created_at"),
                @Index(name = "idx_stage_configs_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class StageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Stage stage;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "updated_by")
    private User updatedBy;

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
