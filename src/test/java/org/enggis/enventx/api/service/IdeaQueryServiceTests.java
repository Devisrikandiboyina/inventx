package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.IdeaInputDto;
import org.enggis.enventx.api.dto.IdeaPatchDto;
import org.enggis.enventx.api.dto.StageTransitionRequest;
import org.enggis.enventx.api.dto.TeamMemberDto;
import org.enggis.enventx.api.domain.enums.StageDecision;
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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({IdeaService.class, IdeaLikeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdeaQueryServiceTests {

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

    User alice;
    User bob;
    User organizer;

    @BeforeEach
    void seed() {
        history.deleteAll();
        likes.deleteAll();
        teamMembers.deleteAll();
        ideas.deleteAll();
        users.deleteAll();
        alice = user("alice", Role.EMPLOYEE);
        bob = user("bob", Role.EMPLOYEE);
        organizer = user("organizer", Role.ORGANIZER);
    }

    private User user(String name, Role role) {
        var u = new User();
        u.setName(name);
        u.setEmail(name + "@enggis.com");
        u.setCompany("ENGGIS");
        u.setPasswordHash("x");
        u.setRole(role);
        return users.save(u);
    }

    private IdeaInputDto input(String title, String category, List<TeamMemberDto> team) {
        return new IdeaInputDto(title, "summary", "description", category, "customers", "impact", team);
    }

    private UUID draft(User owner, String title) {
        return service.createIdea(owner.getId(), input(title, "ROBOTICS", List.of())).id();
    }

    private UUID submitted(User owner, String title) {
        var id = draft(owner, title);
        service.submitIdea(id, owner.getId());
        return id;
    }

    private static int code(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run).getStatusCode().value();
    }

    @Test
    void listExcludesOthersDraftsAndArchivedButKeepsOwn() {
        draft(alice, "Alice draft");
        var published = submitted(alice, "Alice published");
        var archived = submitted(alice, "Alice archived");
        service.executeStageTransition(archived, organizer.getId(),
                new StageTransitionRequest(StageDecision.ARCHIVE, "no longer relevant", null));

        var forBob = service.listIdeas(bob.getId(), null, null, null, null, "newest", 0, 20);
        assertEquals(List.of(published), forBob.content().stream().map(i -> i.id()).toList());

        var forAlice = service.listIdeas(alice.getId(), null, null, null, null, "newest", 0, 20);
        assertEquals(3, forAlice.totalElements());
    }

    @Test
    void listFiltersSortsAndPaginates() {
        var b = submitted(alice, "Bravo");
        var a = service.createIdea(alice.getId(), input("Alpha", "AR_VR", List.of())).id();
        service.submitIdea(a, alice.getId());
        likes.flush();
        service.toggleLike(b, bob.getId());

        var byTitle = service.listIdeas(bob.getId(), null, null, null, null, "title", 0, 20);
        assertEquals(List.of("Alpha", "Bravo"), byTitle.content().stream().map(i -> i.title()).toList());

        var popular = service.listIdeas(bob.getId(), null, null, null, null, "popular", 0, 20);
        assertEquals("Bravo", popular.content().getFirst().title());
        assertTrue(popular.content().getFirst().likedByMe());
        assertEquals(1, popular.content().getFirst().likeCount());

        var arvr = service.listIdeas(bob.getId(), "alp", "AR_VR", "enggis", "IDEA_SUBMISSION", "newest", 0, 20);
        assertEquals(1, arvr.totalElements());

        var pageTwo = service.listIdeas(bob.getId(), null, null, null, null, "title", 1, 1);
        assertEquals("Bravo", pageTwo.content().getFirst().title());
        assertEquals(2, pageTwo.totalPages());
    }

    @Test
    void listRejectsUnknownCategoryAndStage() {
        assertEquals(400, code(() -> service.listIdeas(bob.getId(), null, "LEGACY", null, null, null, 0, 20)));
        assertEquals(400, code(() -> service.listIdeas(bob.getId(), null, null, null, "NOPE", null, 0, 20)));
    }

    @Test
    void mineIncludesDraftsAndArchived() {
        draft(alice, "d1");
        submitted(bob, "b1");
        assertEquals(1, service.listMine(alice.getId()).size());
        assertEquals("d1", service.listMine(alice.getId()).getFirst().title());
    }

    @Test
    void getHidesOthersDraftsAs404ButShowsThemToOwnerAndOrganizer() {
        var id = draft(alice, "secret");
        assertEquals(404, code(() -> service.getIdea(id, bob.getId())));
        assertEquals("secret", service.getIdea(id, alice.getId()).title());
        assertEquals("secret", service.getIdea(id, organizer.getId()).title());
        assertEquals(404, code(() -> service.getIdea(UUID.randomUUID(), alice.getId())));
    }

    @Test
    void adminNotesAreStrippedForNonOrganizers() {
        var id = submitted(alice, "noted");
        var idea = ideas.findById(id).orElseThrow();
        idea.setAdminNotes("internal only");
        ideas.save(idea);

        assertNull(service.getIdea(id, alice.getId()).adminNotes());
        assertNull(service.getIdea(id, bob.getId()).adminNotes());
        assertEquals("internal only", service.getIdea(id, organizer.getId()).adminNotes());
    }

    @Test
    void updateChangesOnlyProvidedFieldsAndReplacesTeam() {
        var id = service.createIdea(alice.getId(), input("Original", "ROBOTICS",
                List.of(new TeamMemberDto("T One", "t1@enggis.com", null, "dev")))).id();

        var updated = service.updateIdea(id, alice.getId(), new IdeaPatchDto(
                "Renamed", null, null, "DIGITAL_TWINS", null, null,
                List.of(new TeamMemberDto("T Two", "T2@enggis.com", null, "qa"))));

        assertEquals("Renamed", updated.title());
        assertEquals("summary", updated.summary());
        assertEquals("DIGITAL_TWINS", updated.category().name());
        assertEquals(List.of("t2@enggis.com"), updated.teamMembers().stream().map(TeamMemberDto::email).toList());

        var untouchedTeam = service.updateIdea(id, alice.getId(),
                new IdeaPatchDto(null, "new summary", null, null, null, null, null));
        assertEquals(1, untouchedTeam.teamMembers().size());
        assertEquals("new summary", untouchedTeam.summary());
    }

    @Test
    void updateIsOwnerOnlyAndStatusGated() {
        var id = draft(alice, "mine");
        var patch = new IdeaPatchDto("Changed", null, null, null, null, null, null);

        assertEquals(403, code(() -> service.updateIdea(id, bob.getId(), patch)));
        assertEquals(403, code(() -> service.updateIdea(id, organizer.getId(), patch)));

        service.submitIdea(id, alice.getId());
        assertEquals(409, code(() -> service.updateIdea(id, alice.getId(), patch)));

        service.executeStageTransition(id, organizer.getId(),
                new StageTransitionRequest(StageDecision.REQUEST_CHANGES, "please refine", null));
        assertEquals("Changed", service.updateIdea(id, alice.getId(), patch).title());
        assertEquals(IdeaStatus.CHANGES_REQUESTED, ideas.findById(id).orElseThrow().getStatus());
    }

    @Test
    void updateValidatesTeamRules() {
        var id = draft(alice, "mine");
        var owner = new TeamMemberDto("Me", "ALICE@enggis.com", null, "lead");
        var ex = assertThrows(ResponseStatusException.class, () -> service.updateIdea(id, alice.getId(),
                new IdeaPatchDto(null, null, null, null, null, null, List.of(owner))));
        assertEquals("OWNER_IN_TEAM", ex.getReason());
        assertNotNull(ex.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
