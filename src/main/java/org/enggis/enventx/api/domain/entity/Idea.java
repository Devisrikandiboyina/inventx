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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ideas",
        indexes = {
                @Index(name = "idx_ideas_owner_id", columnList = "owner_id"),
                @Index(name = "idx_ideas_status", columnList = "status"),
                @Index(name = "idx_ideas_created_at", columnList = "created_at"),
                @Index(name = "idx_ideas_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_customer", length = 500)
    private String targetCustomer;

    @Column(name = "business_impact", columnDefinition = "TEXT")
    private String businessImpact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CategoryCode category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IdeaStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @jakarta.persistence.ElementCollection(fetch = FetchType.LAZY)
    @jakarta.persistence.CollectionTable(
            name = "idea_tags",
            joinColumns = @JoinColumn(name = "idea_id"),
            indexes = @Index(name = "idx_idea_tags_tag", columnList = "tag"))
    @Column(name = "tag", nullable = false, length = 30)
    private java.util.Set<String> tags = new java.util.LinkedHashSet<>();

    @Column(name = "poster_url", length = 2048)
    private String posterUrl;

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
