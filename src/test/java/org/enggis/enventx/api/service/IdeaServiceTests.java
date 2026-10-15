package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.domain.enums.StageDecision;
import org.enggis.enventx.api.dto.IdeaDto;
import org.enggis.enventx.api.dto.IdeaInputDto;
import org.enggis.enventx.api.dto.StageTransitionRequest;
import org.enggis.enventx.api.dto.TeamMemberDto;
import org.enggis.enventx.api.repository.IdeaLikeRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.IdeaStageHistoryRepository;
import org.enggis.enventx.api.repository.IdeaTeamMemberRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({IdeaService.class, IdeaLikeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdeaServiceTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @Autowired IdeaService service;
    @Autowired IdeaRepository ideas;
    @Autowired UserRepository users;
    @Autowired IdeaLikeRepository likes;
    @Autowired IdeaTeamMemberRepository teamMembers;
    @Autowired IdeaStageHistoryRepository history;

    User owner;
    User other;
    User organizer;
    User admin;
    User reviewer;

    @BeforeEach
    void seed() {
        history.deleteAll();
        likes.deleteAll();
        teamMembers.deleteAll();
        ideas.deleteAll();
        users.deleteAll();
        owner = user("owner", Role.EMPLOYEE);
        other = user("other", Role.EMPLOYEE);
        organizer = user("organizer", Role.ORGANIZER);
        admin = user("admin", Role.ADMIN);
        reviewer = user("reviewer", Role.REVIEWER);
    }

    private User user(String name, Role role) {
        var u = new User();
        u.setName(name);
        u.setEmail(name + "@enggis.com");
        u.setPasswordHash("x");
        u.setRole(role);
        return users.save(u);
    }

    private IdeaInputDto input(String title, List<TeamMemberDto> team) {
        return new IdeaInputDto(title, "summary", "description", "ROBOTICS", "customers", "impact", team);
    }

    private IdeaDto create(String title) {
        return service.createIdea(owner.getId(), input(title, List.of()));
    }

    private IdeaDto submitted(String title) {
        var idea = create(title);
        return service.submitIdea(idea.id(), owner.getId());
    }

    private IdeaDto transition(UUID ideaId, User actor, StageDecision decision, String reason, Stage toStage) {
        return service.executeStageTransition(ideaId, actor.getId(), new StageTransitionRequest(decision, reason, toStage));
    }

    private IdeaDto transition(UUID ideaId, StageDecision decision) {
        return transition(ideaId, organizer, decision, "because of reasons", null);
    }

    private static ResponseStatusException failure(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    private static void assertFailure(int status, String reasonPrefix, ResponseStatusException e) {
        assertEquals(status, e.getStatusCode().value());
        assertTrue(e.getReason().startsWith(reasonPrefix), e.getReason());
    }

    @Test
    void createIdeaStartsAsDraftInFirstStageAndPersistsTeam() {
        var team = List.of(new TeamMemberDto(" Tess ", " TESS@Enggis.com ", "Eng", "Dev"));
        var idea = service.createIdea(owner.getId(), input("  My idea ", team));

        assertEquals(IdeaStatus.DRAFT, idea.status());
        assertEquals(Stage.IDEA_SUBMISSION, idea.stage());
        assertEquals("My idea", idea.title());
        assertEquals(owner.getId(), idea.ownerId());
        assertEquals(0, idea.likeCount());
        assertEquals("tess@enggis.com", idea.teamMembers().getFirst().email());
        assertEquals(1, teamMembers.count());
    }

    @Test
    void sixthActiveIdeaIsRejectedAndArchivedIdeasDoNotCount() {
        var created = new ArrayList<IdeaDto>();
        for (int i = 0; i < 5; i++) {
            created.add(create("idea " + i));
        }
        assertFailure(409, "IDEA_LIMIT", failure(() -> create("idea 6")));
        assertEquals(5, ideas.count());

        transition(created.getFirst().id(), StageDecision.ARCHIVE);
        assertEquals(IdeaStatus.DRAFT, create("idea 6").status());
    }

    @Test
    void otherUsersIdeasDoNotCountTowardsTheLimit() {
        for (int i = 0; i < 5; i++) {
            create("idea " + i);
        }
        assertEquals(IdeaStatus.DRAFT, service.createIdea(other.getId(), input("mine", List.of())).status());
    }

    @Test
    void teamRulesAreEnforced() {
        var member = new TeamMemberDto("M", "m@enggis.com", "Eng", "Dev");
        var four = List.of(member,
                new TeamMemberDto("N", "n@enggis.com", null, "Dev"),
                new TeamMemberDto("O", "o@enggis.com", null, "Dev"),
                new TeamMemberDto("P", "p@enggis.com", null, "Dev"));
        assertFailure(400, "TEAM_SIZE_EXCEEDED", failure(() -> service.createIdea(owner.getId(), input("t", four))));

        var withOwner = List.of(new TeamMemberDto("Me", "OWNER@enggis.com", null, "Lead"));
        assertFailure(400, "OWNER_IN_TEAM", failure(() -> service.createIdea(owner.getId(), input("t", withOwner))));

        var duplicates = List.of(member, new TeamMemberDto("Again", "M@enggis.com", null, "Dev"));
        assertFailure(400, "DUPLICATE_TEAM_MEMBER", failure(() -> service.createIdea(owner.getId(), input("t", duplicates))));

        var badCategory = new IdeaInputDto("t", "s", "d", "NOT_A_CATEGORY", null, null, List.of());
        assertFailure(400, "INVALID_CATEGORY", failure(() -> service.createIdea(owner.getId(), badCategory)));

        assertEquals(0, ideas.count());
        assertEquals(3, service.createIdea(owner.getId(), input("ok", List.of(member,
                new TeamMemberDto("N", "n@enggis.com", null, "Dev"),
                new TeamMemberDto("O", "o@enggis.com", null, "Dev")))).teamMembers().size());
    }

    @Test
    void concurrentCreatesNeverExceedTheLimit() throws Exception {
        var succeeded = new AtomicInteger();
        var limited = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                var title = "concurrent " + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        create(title);
                        succeeded.incrementAndGet();
                    } catch (ResponseStatusException e) {
                        assertEquals("IDEA_LIMIT", e.getReason());
                        limited.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }
        assertEquals(5, succeeded.get());
        assertEquals(3, limited.get());
        assertEquals(5, ideas.countActiveIdeas(owner.getId()));
    }

    @Test
    void submitRequiresOwnerAndAnEditableStatus() {
        var idea = create("to submit");
        assertFailure(403, "NOT_OWNER", failure(() -> service.submitIdea(idea.id(), other.getId())));
        assertFailure(404, "IDEA_NOT_FOUND", failure(() -> service.submitIdea(UUID.randomUUID(), owner.getId())));

        assertEquals(IdeaStatus.SUBMITTED, service.submitIdea(idea.id(), owner.getId()).status());
        assertFailure(409, "INVALID_STATUS", failure(() -> service.submitIdea(idea.id(), owner.getId())));

        transition(idea.id(), StageDecision.REQUEST_CHANGES);
        assertEquals(IdeaStatus.CHANGES_REQUESTED, ideas.findById(idea.id()).orElseThrow().getStatus());
        assertEquals(IdeaStatus.SUBMITTED, service.submitIdea(idea.id(), owner.getId()).status());

        transition(idea.id(), StageDecision.APPROVE);
        assertFailure(409, "INVALID_STATUS", failure(() -> service.submitIdea(idea.id(), owner.getId())));
    }

    @Test
    void toggleLikeFlipsAndReturnsCountAndLikedByMe() {
        var idea = submitted("likeable");

        var first = service.toggleLike(idea.id(), other.getId());
        assertEquals(1, first.likeCount());
        assertTrue(first.likedByMe());

        var second = service.toggleLike(idea.id(), other.getId());
        assertEquals(0, second.likeCount());
        assertFalse(second.likedByMe());

        assertEquals(404, failure(() -> service.toggleLike(UUID.randomUUID(), other.getId())).getStatusCode().value());
    }

    @Test
    void onlyOrganizersAndAdminsMayTransition() {
        var idea = submitted("guarded");
        assertFailure(403, "ORGANIZER_ONLY", failure(() ->
                transition(idea.id(), owner, StageDecision.APPROVE, "valid reason", null)));
        assertFailure(403, "ORGANIZER_ONLY", failure(() ->
                transition(idea.id(), reviewer, StageDecision.APPROVE, "valid reason", null)));
        assertEquals(IdeaStatus.APPROVED,
                transition(idea.id(), admin, StageDecision.APPROVE, "valid reason", null).status());
        assertEquals(1, history.findByIdeaIdOrderByChangedAtAsc(idea.id()).size());
    }

    @Test
    void reasonIsMandatoryAndNothingChangesWithoutIt() {
        var idea = submitted("needs reason");
        for (var reason : new String[]{null, "", "   "}) {
            assertFailure(400, "REASON_REQUIRED", failure(() ->
                    transition(idea.id(), organizer, StageDecision.APPROVE, reason, null)));
        }
        assertEquals(IdeaStatus.SUBMITTED, ideas.findById(idea.id()).orElseThrow().getStatus());
        assertEquals(0, history.count());
    }

    @Test
    void fullPromotionPathIsRecordedInHistory() {
        var idea = submitted("journey");
        var expectedStages = List.of(Stage.SCREENING, Stage.PRESENTATION, Stage.DEMO);

        transition(idea.id(), StageDecision.APPROVE);
        for (var expected : expectedStages) {
            var promoted = transition(idea.id(), organizer, StageDecision.PROMOTE, "ready for " + expected, expected);
            assertEquals(expected, promoted.stage());
            assertEquals(IdeaStatus.UNDER_REVIEW, promoted.status());
            transition(idea.id(), StageDecision.APPROVE);
        }
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(idea.id(), StageDecision.PROMOTE)));

        var entries = history.findByIdeaIdOrderByChangedAtAsc(idea.id());
        assertEquals(7, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.getReason() != null && !e.getReason().isBlank()));
        assertTrue(entries.stream().allMatch(e -> e.getChangedBy().getId().equals(organizer.getId())));
        var promotions = entries.stream().filter(e -> e.getDecision() == StageDecision.PROMOTE).toList();
        assertEquals(3, promotions.size());
        assertTrue(promotions.stream().allMatch(e -> e.getFromStatus() == IdeaStatus.APPROVED
                && e.getToStage().ordinal() == e.getFromStage().ordinal() + 1));
    }

    @Test
    void invalidTransitionsAreRejectedWithoutSideEffects() {
        var draft = create("still a draft");
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(draft.id(), StageDecision.APPROVE)));
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(draft.id(), StageDecision.PROMOTE)));
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(draft.id(), StageDecision.RESTORE)));

        var idea = submitted("not approved yet");
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(idea.id(), StageDecision.PROMOTE)));

        transition(idea.id(), StageDecision.APPROVE);
        assertFailure(409, "INVALID_TRANSITION", failure(() ->
                transition(idea.id(), organizer, StageDecision.PROMOTE, "skip ahead", Stage.DEMO)));
        assertFailure(409, "INVALID_TRANSITION", failure(() ->
                transition(idea.id(), organizer, StageDecision.REJECT, "wrong stage", Stage.DEMO)));

        transition(idea.id(), StageDecision.REJECT);
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(idea.id(), StageDecision.APPROVE)));
        assertFailure(409, "INVALID_TRANSITION", failure(() -> transition(idea.id(), StageDecision.REQUEST_CHANGES)));

        var persisted = ideas.findById(idea.id()).orElseThrow();
        assertEquals(IdeaStatus.REJECTED, persisted.getStatus());
        assertEquals(Stage.IDEA_SUBMISSION, persisted.getStage());
    }

    @Test
    void restoreReturnsThePreviousStatusAndRespectsTheActiveLimit() {
        var idea = submitted("archive me");
        transition(idea.id(), StageDecision.APPROVE);
        transition(idea.id(), StageDecision.ARCHIVE);
        assertEquals(IdeaStatus.ARCHIVED, ideas.findById(idea.id()).orElseThrow().getStatus());

        assertEquals(IdeaStatus.APPROVED, transition(idea.id(), StageDecision.RESTORE).status());

        transition(idea.id(), StageDecision.ARCHIVE);
        for (int i = 0; i < 5; i++) {
            create("filler " + i);
        }
        assertFailure(409, "IDEA_LIMIT", failure(() -> transition(idea.id(), StageDecision.RESTORE)));
        assertEquals(IdeaStatus.ARCHIVED, ideas.findById(idea.id()).orElseThrow().getStatus());
    }

    @Test
    void concurrentPromotionsApplyExactlyOnce() throws Exception {
        var idea = submitted("race");
        transition(idea.id(), StageDecision.APPROVE);

        var succeeded = new AtomicInteger();
        var rejected = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        transition(idea.id(), StageDecision.PROMOTE);
                        succeeded.incrementAndGet();
                    } catch (ResponseStatusException e) {
                        assertTrue(e.getReason().startsWith("INVALID_TRANSITION"), e.getReason());
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }
        assertEquals(1, succeeded.get());
        assertEquals(5, rejected.get());
        assertEquals(Stage.SCREENING, ideas.findById(idea.id()).orElseThrow().getStage());
        assertEquals(1, history.findAll().stream().filter(e -> e.getDecision() == StageDecision.PROMOTE).count());
    }
}
