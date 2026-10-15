package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.IdeaStageHistory;
import org.enggis.enventx.api.domain.entity.IdeaTeamMember;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.domain.enums.StageDecision;
import org.enggis.enventx.api.dto.IdeaDto;
import org.enggis.enventx.api.dto.IdeaInputDto;
import org.enggis.enventx.api.dto.IdeaPatchDto;
import org.enggis.enventx.api.dto.IdeaSummaryDto;
import org.enggis.enventx.api.dto.OrganizerIdeaPatchDto;
import org.enggis.enventx.api.dto.PagedResponse;
import org.enggis.enventx.api.repository.IdeaSort;
import org.enggis.enventx.api.repository.IdeaSpecifications;
import org.springframework.data.domain.PageRequest;
import org.enggis.enventx.api.dto.LikeStatusDto;
import org.enggis.enventx.api.dto.StageTransitionRequest;
import org.enggis.enventx.api.dto.TeamMemberDto;
import org.enggis.enventx.api.repository.IdeaLikeRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.IdeaStageHistoryRepository;
import org.enggis.enventx.api.repository.IdeaTeamMemberRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdeaService {

    public static final int MAX_ACTIVE_IDEAS = 5;
    public static final int MAX_TEAM_MEMBERS = 3;
    public static final int MAX_PAGE_SIZE = 100;

    private final IdeaRepository ideas;
    private final UserRepository users;
    private final IdeaTeamMemberRepository teamMembers;
    private final IdeaLikeRepository likes;
    private final IdeaStageHistoryRepository history;
    private final IdeaLikeService likeService;

    private sealed interface Outcome permits Allowed, Denied {
    }

    private record Allowed(Stage toStage, IdeaStatus toStatus) implements Outcome {
    }

    private record Denied(String message) implements Outcome {
    }

    /**
     * Locks the owner's user row first, which serialises concurrent creations by the same user; the count that
     * follows is therefore race-free (Hibernate ignores a lock hint on an aggregate query, so the row lock is on
     * the user, not on the count).
     */
    @Transactional
    public IdeaDto createIdea(UUID ownerId, IdeaInputDto input) {
        var owner = users.findByIdForUpdate(ownerId)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        requireCapacity(ownerId);
        var team = normalizeTeam(input.teamMembers(), owner.getEmail());
        var category = parseCategory(input.category());

        var idea = new Idea();
        idea.setTitle(TextSanitizer.required(input.title()));
        idea.setSummary(TextSanitizer.required(input.summary()));
        idea.setDescription(TextSanitizer.required(input.description()));
        idea.setCategory(category);
        idea.setTargetCustomer(TextSanitizer.clean(input.targetCustomer()));
        idea.setBusinessImpact(TextSanitizer.clean(input.businessImpact()));
        idea.setOwner(owner);
        idea.setStatus(IdeaStatus.DRAFT);
        idea.setStage(Stage.IDEA_SUBMISSION);
        ideas.save(idea);

        teamMembers.saveAll(team.stream().map(member -> toEntity(idea, member)).toList());

        return toDto(idea, ownerId, isOrganizer(owner));
    }

    @Transactional(readOnly = true)
    public PagedResponse<IdeaSummaryDto> listIdeas(
            UUID viewerId, String q, String category, String company, String stage, String sort, int page, int size) {
        var spec = IdeaSpecifications.filter(
                viewerId, q, category == null || category.isBlank() ? null : parseCategory(category),
                company, parseStage(stage));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                IdeaSort.fromParam(sort).toSort());
        var result = ideas.findAll(spec, pageable);
        var liked = likedIds(viewerId, result.getContent());
        return PagedResponse.from(result, idea -> IdeaSummaryDto.from(idea, liked.contains(idea.getId())));

    }

    public static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 30;
    private static final java.util.regex.Pattern TAG_PATTERN =
            java.util.regex.Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} _.-]*");

    @Transactional(readOnly = true)
    public PagedResponse<IdeaSummaryDto> listAllForOrganizer(
            UUID actorId, String q, String category, String company, String stage, String status,
            UUID ownerId, String sort, int page, int size) {
        requireOrganizer(users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND")));
        var spec = IdeaSpecifications.organizerFilter(
                q, category == null || category.isBlank() ? null : parseCategory(category), company,
                parseStage(stage), parseStatus(status), ownerId);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                IdeaSort.fromParam(sort).toSort());
        var result = ideas.findAll(spec, pageable);
        var liked = likedIds(actorId, result.getContent());
        return PagedResponse.from(result, idea -> IdeaSummaryDto.from(idea, liked.contains(idea.getId())));
    }

    /** Null fields are left unchanged; a blank adminNotes clears the notes. */
    @Transactional
    public IdeaDto organizerUpdateIdea(UUID ideaId, UUID actorId, OrganizerIdeaPatchDto patch) {
        var actor = users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        requireOrganizer(actor);
        var idea = lockIdea(ideaId);
        if (patch.category() != null) {
            idea.setCategory(parseCategory(patch.category()));
        }
        if (patch.adminNotes() != null) {
            var notes = TextSanitizer.clean(patch.adminNotes());
            idea.setAdminNotes(notes.isBlank() ? null : notes);
        }
        if (patch.tags() != null) {
            var normalized = normalizeTags(patch.tags());
            idea.getTags().clear();
            idea.getTags().addAll(normalized);
        }
        ideas.saveAndFlush(idea);
        return toDto(idea, actorId, true);
    }

    static Set<String> normalizeTags(List<String> raw) {
        var result = new java.util.LinkedHashSet<String>();
        for (var tag : raw) {
            var value = tag == null ? "" : tag.strip().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.length() > MAX_TAG_LENGTH || !TAG_PATTERN.matcher(value).matches()) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_TAG");
            }
            result.add(value);
        }
        if (result.size() > MAX_TAGS) {
            throw error(HttpStatus.BAD_REQUEST, "TOO_MANY_TAGS");
        }
        return result;
    }

    private static IdeaStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return IdeaStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
    }

    @Transactional(readOnly = true)
    public List<IdeaSummaryDto> listMine(UUID ownerId) {
        var mine = ideas.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        var liked = likedIds(ownerId, mine);
        return mine.stream().map(idea -> IdeaSummaryDto.from(idea, liked.contains(idea.getId()))).toList();
    }

    /** Other users' DRAFT/ARCHIVED ideas are reported as missing so their existence is not disclosed. */
    @Transactional(readOnly = true)
    public IdeaDto getIdea(UUID ideaId, UUID viewerId) {
        var viewer = users.findById(viewerId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        var idea = ideas.findById(ideaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));
        var privileged = isOrganizer(viewer) || idea.getOwner().getId().equals(viewerId);
        var hidden = switch (idea.getStatus()) {
            case DRAFT, ARCHIVED -> true;
            case SUBMITTED, UNDER_REVIEW, CHANGES_REQUESTED, APPROVED, REJECTED -> false;
        };
        if (hidden && !privileged) {
            throw error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND");
        }
        return toDto(idea, viewerId, isOrganizer(viewer));
    }

    @Transactional
    public IdeaDto updateIdea(UUID ideaId, UUID userId, IdeaPatchDto patch) {
        var idea = lockIdea(ideaId);
        var owner = idea.getOwner();
        if (!owner.getId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "NOT_OWNER");
        }
        switch (idea.getStatus()) {
            case DRAFT, CHANGES_REQUESTED -> {
            }
            case SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, ARCHIVED ->
                    throw error(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        var team = patch.teamMembers() == null ? null : normalizeTeam(patch.teamMembers(), owner.getEmail());
        var category = patch.category() == null ? null : parseCategory(patch.category());

        if (patch.title() != null) idea.setTitle(TextSanitizer.required(patch.title()));
        if (patch.summary() != null) idea.setSummary(TextSanitizer.required(patch.summary()));
        if (patch.description() != null) idea.setDescription(TextSanitizer.required(patch.description()));
        if (category != null) idea.setCategory(category);
        if (patch.targetCustomer() != null) idea.setTargetCustomer(TextSanitizer.clean(patch.targetCustomer()));
        if (patch.businessImpact() != null) idea.setBusinessImpact(TextSanitizer.clean(patch.businessImpact()));
        if (team != null) {
            teamMembers.deleteByIdeaId(ideaId);
            teamMembers.flush();
            teamMembers.saveAll(team.stream().map(member -> toEntity(idea, member)).toList());
        }
        ideas.saveAndFlush(idea);
        return toDto(idea, userId, isOrganizer(owner));
    }

    @Transactional
    public IdeaDto submitIdea(UUID ideaId, UUID userId) {
        var idea = lockIdea(ideaId);
        if (!idea.getOwner().getId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "NOT_OWNER");
        }
        switch (idea.getStatus()) {
            case DRAFT, CHANGES_REQUESTED -> idea.setStatus(IdeaStatus.SUBMITTED);
            case SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, ARCHIVED ->
                    throw error(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
        return toDto(idea, userId, isOrganizer(idea.getOwner()));
    }

    /**
     * Flips the caller's like. Deliberately not {@code @Transactional}: {@link IdeaLikeService} owns its
     * transactions and resolves duplicate-insert races after they roll back. Each flip is itself idempotent, so
     * concurrent duplicate requests never double count.
     */
    public LikeStatusDto toggleLike(UUID ideaId, UUID userId) {
        return likes.existsByIdeaIdAndUserId(ideaId, userId)
                ? likeService.unlike(ideaId, userId)
                : likeService.like(ideaId, userId);
    }

    @Transactional
    public IdeaDto executeStageTransition(UUID ideaId, UUID actorId, StageTransitionRequest request) {
        var actor = users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        requireOrganizer(actor);
        if (request.reason() == null || request.reason().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }

        var idea = lockIdea(ideaId);
        var allowed = switch (decide(idea, request)) {
            case Denied(var message) -> throw error(HttpStatus.CONFLICT, "INVALID_TRANSITION: " + message);
            case Allowed a -> a;
        };

        if (request.decision() == StageDecision.RESTORE) {
            // restoring re-activates the idea, so the owner's 5-active-ideas limit applies again
            users.findByIdForUpdate(idea.getOwner().getId());
            requireCapacity(idea.getOwner().getId());
        }

        var entry = new IdeaStageHistory();
        entry.setIdea(idea);
        entry.setFromStage(idea.getStage());
        entry.setToStage(allowed.toStage());
        entry.setFromStatus(idea.getStatus());
        entry.setToStatus(allowed.toStatus());
        entry.setDecision(request.decision());
        entry.setReason(TextSanitizer.required(request.reason()));
        entry.setChangedBy(actor);
        history.save(entry);

        idea.setStage(allowed.toStage());
        idea.setStatus(allowed.toStatus());
        return toDto(idea, actorId, true);
    }

    private Outcome decide(Idea idea, StageTransitionRequest request) {
        var stage = idea.getStage();
        var status = idea.getStatus();
        var requestedStage = request.toStage();
        return switch (request.decision()) {
            case PROMOTE -> {
                if (status != IdeaStatus.APPROVED) {
                    yield new Denied("PROMOTE requires status APPROVED but the idea is " + status);
                }
                yield nextStage(stage)
                        .<Outcome>map(next -> requestedStage == null || requestedStage == next
                                ? new Allowed(next, IdeaStatus.UNDER_REVIEW)
                                : new Denied("toStage must be " + next + "; stages cannot be skipped"))
                        .orElseGet(() -> new Denied("the idea is already at the final stage " + stage));
            }
            case APPROVE -> keepStage(request, idea,
                    EnumSet.of(IdeaStatus.SUBMITTED, IdeaStatus.UNDER_REVIEW), () -> IdeaStatus.APPROVED);
            case REQUEST_CHANGES -> keepStage(request, idea,
                    EnumSet.of(IdeaStatus.SUBMITTED, IdeaStatus.UNDER_REVIEW), () -> IdeaStatus.CHANGES_REQUESTED);
            case REJECT -> keepStage(request, idea,
                    EnumSet.of(IdeaStatus.SUBMITTED, IdeaStatus.UNDER_REVIEW, IdeaStatus.APPROVED),
                    () -> IdeaStatus.REJECTED);
            case ARCHIVE -> keepStage(request, idea,
                    EnumSet.complementOf(EnumSet.of(IdeaStatus.ARCHIVED)), () -> IdeaStatus.ARCHIVED);
            case RESTORE -> keepStage(request, idea,
                    EnumSet.of(IdeaStatus.ARCHIVED), () -> statusBeforeArchive(idea));
        };
    }

    private Outcome keepStage(StageTransitionRequest request, Idea idea, Set<IdeaStatus> allowedFrom,
                              Supplier<IdeaStatus> target) {
        if (!allowedFrom.contains(idea.getStatus())) {
            return new Denied(request.decision() + " is not allowed while the idea is " + idea.getStatus());
        }
        if (request.toStage() != null && request.toStage() != idea.getStage()) {
            return new Denied(request.decision() + " does not change the stage; toStage must be "
                    + idea.getStage() + " or omitted");
        }
        return new Allowed(idea.getStage(), target.get());
    }

    private IdeaStatus statusBeforeArchive(Idea idea) {
        return history.findFirstByIdeaIdAndDecisionOrderByChangedAtDesc(idea.getId(), StageDecision.ARCHIVE)
                .map(IdeaStageHistory::getFromStatus)
                .filter(previous -> previous != null && previous != IdeaStatus.ARCHIVED)
                .orElse(IdeaStatus.DRAFT);
    }

    private static Optional<Stage> nextStage(Stage stage) {
        return switch (stage) {
            case IDEA_SUBMISSION -> Optional.of(Stage.SCREENING);
            case SCREENING -> Optional.of(Stage.PRESENTATION);
            case PRESENTATION -> Optional.of(Stage.DEMO);
            case DEMO -> Optional.empty();
        };
    }

    private static IdeaTeamMember toEntity(Idea idea, TeamMemberDto member) {
        var entity = new IdeaTeamMember();
        entity.setIdea(idea);
        entity.setName(member.name());
        entity.setEmail(member.email());
        entity.setDepartment(member.department());
        entity.setRoleInTeam(member.roleInTeam());
        return entity;
    }

    private Set<UUID> likedIds(UUID userId, List<Idea> list) {
        return list.isEmpty()
                ? Set.of()
                : likes.findLikedIdeaIds(userId, list.stream().map(Idea::getId).toList());
    }

    private static Stage parseStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        try {
            return Stage.valueOf(stage.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_STAGE");
        }
    }

    private static boolean isOrganizer(User user) {
        return switch (user.getRole()) {
            case ORGANIZER, ADMIN -> true;
            case EMPLOYEE, REVIEWER -> false;
        };
    }

    private static void requireOrganizer(User actor) {
        switch (actor.getRole()) {
            case ORGANIZER, ADMIN -> {
            }
            case EMPLOYEE, REVIEWER -> throw error(HttpStatus.FORBIDDEN, "ORGANIZER_ONLY");
        }
    }

    private void requireCapacity(UUID ownerId) {
        if (ideas.countActiveIdeas(ownerId) >= MAX_ACTIVE_IDEAS) {
            throw error(HttpStatus.CONFLICT, "IDEA_LIMIT");
        }
    }

    private Idea lockIdea(UUID ideaId) {
        return ideas.findByIdForUpdate(ideaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));
    }

    private static List<TeamMemberDto> normalizeTeam(List<TeamMemberDto> team, String ownerEmail) {
        if (team == null || team.isEmpty()) {
            return List.of();
        }
        if (team.size() > MAX_TEAM_MEMBERS) {
            throw error(HttpStatus.BAD_REQUEST, "TEAM_SIZE_EXCEEDED");
        }
        var owner = ownerEmail.strip().toLowerCase(Locale.ROOT);
        var seen = new HashSet<String>();
        var normalized = new ArrayList<TeamMemberDto>();
        for (var member : team) {
            var email = member.email().strip().toLowerCase(Locale.ROOT);
            if (email.equals(owner)) {
                throw error(HttpStatus.BAD_REQUEST, "OWNER_IN_TEAM");
            }
            if (!seen.add(email)) {
                throw error(HttpStatus.BAD_REQUEST, "DUPLICATE_TEAM_MEMBER");
            }
            normalized.add(new TeamMemberDto(TextSanitizer.required(member.name()), email, TextSanitizer.clean(member.department()),
                TextSanitizer.clean(member.roleInTeam())));
        }
        return normalized;
    }

    private static CategoryCode parseCategory(String category) {
        try {
            return CategoryCode.valueOf(category.strip());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY");
        }
    }

    private IdeaDto toDto(Idea idea, UUID viewerId, boolean includeAdminNotes) {
        var members = teamMembers.findByIdeaIdOrderByCreatedAtAsc(idea.getId()).stream()
                .map(m -> new TeamMemberDto(m.getName(), m.getEmail(), m.getDepartment(), m.getRoleInTeam()))
                .toList();
        return new IdeaDto(
                idea.getId(), idea.getTitle(), idea.getSummary(), idea.getDescription(), idea.getCategory(),
                idea.getTargetCustomer(), idea.getBusinessImpact(), idea.getStage(), idea.getStatus(),
                idea.getOwner().getId(), idea.getOwner().getName(), idea.getLikeCount(),
                likes.existsByIdeaIdAndUserId(idea.getId(), viewerId), members,
                idea.getCreatedAt(), idea.getUpdatedAt(), includeAdminNotes ? idea.getAdminNotes() : null,
                idea.getTags().stream().sorted().toList());
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
