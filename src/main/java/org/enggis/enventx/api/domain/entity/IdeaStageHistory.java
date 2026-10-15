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
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.domain.enums.StageDecision;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "idea_stage_history",
        indexes = {
                @Index(name = "idx_idea_stage_history_idea_id", columnList = "idea_id"),
                @Index(name = "idx_idea_stage_history_changed_at", columnList = "changed_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class IdeaStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 40)
    private Stage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 40)
    private Stage toStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private IdeaStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 40)
    private IdeaStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StageDecision decision;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }
}
