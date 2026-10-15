package org.enggis.enventx.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "idea_team_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idea_team_members_idea_email",
                columnNames = {"idea_id", "email"}
        ),
        indexes = {
                @Index(name = "idx_idea_team_members_created_at", columnList = "created_at"),
                @Index(name = "idx_idea_team_members_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class IdeaTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 150)
    private String department;

    @Column(name = "role_in_team", nullable = false, length = 100)
    private String roleInTeam;

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
