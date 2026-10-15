package org.enggis.enventx.api.repository;

import jakarta.persistence.criteria.JoinType;
import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class IdeaSpecifications {

    private static final List<IdeaStatus> HIDDEN_FROM_OTHERS = List.of(IdeaStatus.DRAFT, IdeaStatus.ARCHIVED);

    private IdeaSpecifications() {
    }

    /** Everyone sees non-DRAFT/non-ARCHIVED ideas; DRAFT and ARCHIVED are visible only to their owner. */
    public static Specification<Idea> visibleTo(UUID viewerId) {
        return (root, query, cb) -> {
            var publicIdea = cb.not(root.get("status").in(HIDDEN_FROM_OTHERS));
            return viewerId == null
                    ? publicIdea
                    : cb.or(publicIdea, cb.equal(root.get("owner").get("id"), viewerId));
        };
    }

    public static Specification<Idea> matchesTerm(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        var pattern = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("summary")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }

    public static Specification<Idea> hasCategory(CategoryCode category) {
        return category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Idea> hasStage(Stage stage) {
        return stage == null ? null : (root, query, cb) -> cb.equal(root.get("stage"), stage);
    }

    /** Company is stored on the owner, not on the idea. */
    public static Specification<Idea> ownerCompany(String company) {
        if (company == null || company.isBlank()) {
            return null;
        }
        var normalized = company.strip().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(
                cb.lower(root.join("owner", JoinType.INNER).get("company")), normalized);
    }

    public static Specification<Idea> filter(
            UUID viewerId, String q, CategoryCode category, String company, Stage stage) {
        // Specification.and(null) throws in Spring Data JPA 4, so optional filters are skipped explicitly
        var combined = visibleTo(viewerId);
        for (var optional : java.util.Arrays.asList(
                matchesTerm(q), hasCategory(category), ownerCompany(company), hasStage(stage))) {
            if (optional != null) {
                combined = combined.and(optional);
            }
        }
        return combined;
    }

    public static Specification<Idea> hasStatus(IdeaStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Idea> ownedBy(UUID ownerId) {
        return ownerId == null ? null : (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    /** Organizer view: no visibility restriction, so DRAFT and ARCHIVED ideas are included. */
    public static Specification<Idea> organizerFilter(
            String q, CategoryCode category, String company, Stage stage, IdeaStatus status, UUID ownerId) {
        Specification<Idea> combined = (root, query, cb) -> cb.conjunction();
        for (var optional : java.util.Arrays.asList(
                matchesTerm(q), hasCategory(category), ownerCompany(company), hasStage(stage),
                hasStatus(status), ownedBy(ownerId))) {
            if (optional != null) {
                combined = combined.and(optional);
            }
        }
        return combined;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
