package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.Announcement;
import org.enggis.enventx.api.domain.entity.Guideline;
import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.AnnouncementState;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.dto.AnnouncementInputDto;
import org.enggis.enventx.api.dto.AnnouncementPatchDto;
import org.enggis.enventx.api.dto.GuidelineInputDto;
import org.enggis.enventx.api.dto.GuidelinePatchDto;
import org.enggis.enventx.api.dto.OrganizerIdeaPatchDto;
import org.enggis.enventx.api.dto.StageDatesRequest;
import org.enggis.enventx.api.dto.UpdateSettingsRequest;
import org.enggis.enventx.api.repository.AnnouncementRepository;
import org.enggis.enventx.api.repository.AppSettingRepository;
import org.enggis.enventx.api.repository.AuditLogRepository;
import org.enggis.enventx.api.repository.GuidelineRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.StageConfigRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.enggis.enventx.api.storage.InMemoryFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({AuditService.class, OrganizerDashboardService.class, OrganizerStageService.class,
        OrganizerGuidelineService.class, OrganizerAnnouncementService.class, OrganizerUserService.class,
        OrganizerSettingsService.class, ContentService.class, IdeaService.class, IdeaLikeService.class,
        IdeaFileService.class, OrganizerServicesTests.Storage.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganizerServicesTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    static class Storage {
        @Bean
        InMemoryFileStorage inMemoryFileStorage() {
            return new InMemoryFileStorage();
        }

    }

    @Autowired OrganizerDashboardService dashboard;
    @Autowired OrganizerStageService stageService;
    @Autowired OrganizerGuidelineService guidelineService;
    @Autowired OrganizerAnnouncementService announcementService;
    @Autowired OrganizerUserService userService;
    @Autowired OrganizerSettingsService settingsService;
    @Autowired ContentService content;
    @Autowired IdeaService ideaService;
    @Autowired IdeaFileService fileService;
    @Autowired InMemoryFileStorage storage;
    @Autowired UserRepository users;
    @Autowired IdeaRepository ideas;
    @Autowired StageConfigRepository stages;
    @Autowired GuidelineRepository guidelines;
    @Autowired AnnouncementRepository announcements;
    @Autowired AppSettingRepository settings;
    @Autowired AuditLogRepository audit;

    User organizer;
    User admin;
    User employee;

    @BeforeEach
    void seed() {
        audit.deleteAll();
        ideas.deleteAll();
        announcements.deleteAll();
        guidelines.deleteAll();
        stages.deleteAll();
        settings.deleteAll();
        users.deleteAll();
        storage.objects.clear();
        storage.deleted.clear();
        organizer = user("olga", Role.ORGANIZER);
        admin = user("adam", Role.ADMIN);
        employee = user("eve", Role.EMPLOYEE);
    }

    private User user(String name, Role role) {
        var u = new User();
        u.setName(name);
        u.setEmail(name + "@enggis.com");
        u.setPasswordHash("x");
        u.setCompany("ENGGIS");
        u.setRole(role);
        return users.save(u);
    }

    private Idea idea(User owner, IdeaStatus status, CategoryCode category, Stage stage) {
        var i = new Idea();
        i.setTitle("Idea " + status);
        i.setSummary("s");
        i.setDescription("d");
        i.setCategory(category);
        i.setStage(stage);
        i.setStatus(status);
        i.setOwner(owner);
        return ideas.save(i);
    }

    private static ResponseStatusException thrown(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run);
    }

    // ---- organizer ideas ----

    @Test
    void organizerListIncludesDraftsAndArchivedAndFilters() {
        idea(employee, IdeaStatus.DRAFT, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION);
        idea(employee, IdeaStatus.ARCHIVED, CategoryCode.AR_VR, Stage.SCREENING);
        idea(admin, IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING);

        var all = ideaService.listAllForOrganizer(organizer.getId(), null, null, null, null, null, null, "newest", 0, 20);
        assertEquals(3, all.totalElements());
        var drafts = ideaService.listAllForOrganizer(organizer.getId(), null, null, null, null, "draft", null, "newest", 0, 20);
        assertEquals(1, drafts.totalElements());
        var byOwner = ideaService.listAllForOrganizer(organizer.getId(), null, null, null, null, null, employee.getId(), "newest", 0, 20);
        assertEquals(2, byOwner.totalElements());
        assertEquals(400, thrown(() -> ideaService.listAllForOrganizer(
                organizer.getId(), null, null, null, null, "bogus", null, "newest", 0, 20)).getStatusCode().value());
    }

    @Test
    void organizerListIsRefusedForEmployees() {
        assertEquals(403, thrown(() -> ideaService.listAllForOrganizer(
                employee.getId(), null, null, null, null, null, null, "newest", 0, 20)).getStatusCode().value());
    }

    @Test
    void organizerPatchSetsCategoryNotesAndNormalisedTags() {
        var idea = idea(employee, IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING);

        var dto = ideaService.organizerUpdateIdea(idea.getId(), organizer.getId(),
                new OrganizerIdeaPatchDto("DIGITAL_TWINS", " check budget ", List.of(" Smart-Factory ", "AI", "ai")));

        assertEquals(CategoryCode.DIGITAL_TWINS, dto.category());
        assertEquals("check budget", dto.adminNotes());
        assertEquals(List.of("ai", "smart-factory"), dto.tags());

        var cleared = ideaService.organizerUpdateIdea(idea.getId(), organizer.getId(),
                new OrganizerIdeaPatchDto(null, "  ", null));
        assertNull(cleared.adminNotes());
        assertEquals(List.of("ai", "smart-factory"), cleared.tags());
        assertEquals(CategoryCode.DIGITAL_TWINS, cleared.category());
    }

    @Test
    void organizerPatchRejectsBadTagsAndNonOrganizers() {
        var idea = idea(employee, IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING);
        assertEquals("INVALID_TAG", thrown(() -> ideaService.organizerUpdateIdea(idea.getId(), organizer.getId(),
                new OrganizerIdeaPatchDto(null, null, List.of("<script>")))).getReason());
        var many = java.util.stream.IntStream.range(0, 11).mapToObj(i -> "t" + i).toList();
        assertEquals("TOO_MANY_TAGS", thrown(() -> ideaService.organizerUpdateIdea(idea.getId(), organizer.getId(),
                new OrganizerIdeaPatchDto(null, null, many))).getReason());
        assertEquals(403, thrown(() -> ideaService.organizerUpdateIdea(idea.getId(), employee.getId(),
                new OrganizerIdeaPatchDto("ROBOTICS", null, null))).getStatusCode().value());
        assertEquals("INVALID_CATEGORY", thrown(() -> ideaService.organizerUpdateIdea(idea.getId(), organizer.getId(),
                new OrganizerIdeaPatchDto("NOPE", null, null))).getReason());
    }

    @Test
    void organizerDashboardCountsEverythingWithZeros() {
        idea(employee, IdeaStatus.DRAFT, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION);
        idea(employee, IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING);
        idea(employee, IdeaStatus.UNDER_REVIEW, CategoryCode.AR_VR, Stage.SCREENING);

        var stats = dashboard.stats();
        assertEquals(3, stats.totalIdeas());
        assertEquals(2, stats.awaitingReview());
        assertEquals(3, stats.totalUsers());
        assertEquals(IdeaStatus.values().length, stats.byStatus().size());
        assertEquals(CategoryCode.values().length, stats.byCategory().size());
        assertEquals(2L, stats.byCategory().get(CategoryCode.ROBOTICS));
        assertEquals(0L, stats.byCategory().get(CategoryCode.DIGITAL_TWINS));
        assertEquals(0L, stats.byStage().get(Stage.DEMO));
    }

    // ---- stages ----

    @Test
    void stageDatesAreUpsertedAndAudited() {
        var defaults = stageService.get("screening");
        assertEquals(Stage.SCREENING, defaults.stage());
        assertNull(defaults.startDate());

        var saved = stageService.update("SCREENING", organizer.getId(),
                new StageDatesRequest(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), true));
        assertEquals(LocalDate.of(2026, 3, 1), saved.startDate());
        assertEquals(organizer.getId(), saved.updatedById());
        assertEquals(1, stages.count());

        stageService.update("SCREENING", organizer.getId(), new StageDatesRequest(LocalDate.of(2026, 4, 1), null, true));
        assertEquals(1, stages.count());
        assertEquals(LocalDate.of(2026, 4, 1), stageService.get("SCREENING").startDate());
        assertEquals(2, audit.count());
        assertTrue(audit.findAll().getFirst().getAction().equals("STAGE_DATES_UPDATED"));
        assertEquals(LocalDate.of(2026, 4, 1), content.stages().getFirst().startDate());
    }

    @Test
    void stageDateRulesAreEnforced() {
        var req = new StageDatesRequest(LocalDate.of(2026, 3, 1), null, false);
        assertEquals("CONFIRMATION_REQUIRED", thrown(() -> stageService.update("DEMO", organizer.getId(), req)).getReason());
        var reversed = new StageDatesRequest(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 1), true);
        assertEquals("END_BEFORE_START", thrown(() -> stageService.update("DEMO", organizer.getId(), reversed)).getReason());
        assertEquals("STAGE_NOT_FOUND", thrown(() -> stageService.get("NOPE")).getReason());
        assertEquals(0, audit.count());
    }

    // ---- guidelines ----

    @Test
    void guidelineCrudAndVisibility() {
        var created = guidelineService.create(new GuidelineInputDto(" Rules ", "Be nice", null, null));
        assertEquals("Rules", created.title());
        assertEquals(1, created.sequenceOrder());
        assertEquals(false, created.published());
        assertEquals(0, content.guidelines().size());

        var published = guidelineService.update(created.id(), new GuidelinePatchDto(null, null, null, true));
        assertTrue(published.published());
        assertEquals(1, content.guidelines().size());
        assertEquals("Be nice", published.content());

        assertEquals("TITLE_REQUIRED", thrown(() -> guidelineService.update(
                created.id(), new GuidelinePatchDto(" ", null, null, null))).getReason());
        assertEquals(1, guidelineService.list().size());
    }

    @Test
    void guidelineFileUploadReplaceAndDeleteCleanUpStorage() {
        var g = guidelineService.create(new GuidelineInputDto("Rules", "c", 3, true));
        var pdf = new MockMultipartFile("file", "rules.pdf", "application/pdf", "%PDF-1.7 hello".getBytes());

        fileService.replaceGuidelineFile(g.id(), pdf);
        var first = guidelines.findById(g.id()).orElseThrow();
        assertTrue(first.getFileKey().startsWith("guidelines/" + g.id() + "/"));
        assertEquals("rules.pdf", first.getFileName());
        assertTrue(guidelineService.get(g.id()).hasFile());
        assertEquals(1, storage.objects.size());

        fileService.replaceGuidelineFile(g.id(), new MockMultipartFile("file", "v2.pdf", "application/pdf", "%PDF-1.7 two".getBytes()));
        assertEquals(1, storage.objects.size());
        assertTrue(storage.deleted.contains(first.getFileKey()));
        var second = guidelines.findById(g.id()).orElseThrow().getFileKey();

        guidelineService.delete(g.id());
        assertEquals(0, guidelines.count());
        assertTrue(storage.deleted.contains(second));
        assertEquals(404, thrown(() -> guidelineService.get(g.id())).getStatusCode().value());
    }

    @Test
    void guidelineFileRejectsWrongTypesAndMissingGuideline() {
        var g = guidelineService.create(new GuidelineInputDto("Rules", "c", null, null));
        var png = new MockMultipartFile("file", "x.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0});
        assertEquals(415, thrown(() -> fileService.replaceGuidelineFile(g.id(), png)).getStatusCode().value());
        var fake = new MockMultipartFile("file", "x.pdf", "application/pdf", "not a pdf at all".getBytes());
        assertEquals("FILE_CONTENT_MISMATCH", thrown(() -> fileService.replaceGuidelineFile(g.id(), fake)).getReason());
        assertEquals(404, thrown(() -> fileService.replaceGuidelineFile(
                java.util.UUID.randomUUID(), new MockMultipartFile("file", "x.pdf", "application/pdf", "%PDF-1".getBytes())))
                .getStatusCode().value());
        assertTrue(storage.objects.isEmpty());
    }

    // ---- announcements ----

    @Test
    void announcementLifecycleControlsEmployeeVisibilityAndIsAudited() {
        var a = announcementService.create(new AnnouncementInputDto(" Hello ", "World", true, null));
        assertEquals(AnnouncementState.DRAFT, a.state());
        assertEquals("Hello", a.title());
        assertEquals(0, content.announcements().size());

        var published = announcementService.changeState(a.id(), organizer.getId(), AnnouncementState.PUBLISHED);
        assertEquals(AnnouncementState.PUBLISHED, published.state());
        assertTrue(published.publishAt() != null);
        assertEquals(1, content.announcements().size());

        announcementService.changeState(a.id(), organizer.getId(), AnnouncementState.PUBLISHED);
        assertEquals(1, audit.count());

        announcementService.changeState(a.id(), organizer.getId(), AnnouncementState.DRAFT);
        assertEquals(0, content.announcements().size());
        announcementService.changeState(a.id(), organizer.getId(), AnnouncementState.ARCHIVED);
        assertEquals("ANNOUNCEMENT_ARCHIVED", thrown(() -> announcementService.update(
                a.id(), new AnnouncementPatchDto("x", null, null, null))).getReason());
        assertEquals("INVALID_STATE_TRANSITION", thrown(() -> announcementService.changeState(
                a.id(), organizer.getId(), AnnouncementState.PUBLISHED)).getReason());
        announcementService.changeState(a.id(), organizer.getId(), AnnouncementState.DRAFT);
        assertEquals(4, audit.count());
    }

    @Test
    void scheduledAnnouncementStaysHiddenUntilItsPublishTime() {
        var future = Instant.now().plus(1, ChronoUnit.DAYS);
        var a = announcementService.create(new AnnouncementInputDto("Later", "c", false, future));
        var published = announcementService.changeState(a.id(), organizer.getId(), AnnouncementState.PUBLISHED);
        assertEquals(future.truncatedTo(ChronoUnit.MILLIS), published.publishAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(0, content.announcements().size());
    }

    @Test
    void announcementPatchChangesOnlyProvidedFields() {
        var a = announcementService.create(new AnnouncementInputDto("T", "C", false, null));
        var patched = announcementService.update(a.id(), new AnnouncementPatchDto(null, "C2", true, null));
        assertEquals("T", patched.title());
        assertEquals("C2", patched.content());
        assertTrue(patched.pinned());
        assertEquals(1, announcementService.list().size());
    }

    // ---- users ----

    @Test
    void usersCanBeListedAndFiltered() {
        assertEquals(3, userService.list(null, null, 0, 20).totalElements());
        assertEquals(1, userService.list("EVE@", null, 0, 20).totalElements());
        assertEquals(1, userService.list(null, "organizer", 0, 20).totalElements());
        assertEquals(400, thrown(() -> userService.list(null, "king", 0, 20)).getStatusCode().value());
    }

    @Test
    void organizerCanPromoteEmployeeButNotToAdminAndNotSelf() {
        var dto = userService.changeRole(employee.getId(), organizer.getId(), Role.REVIEWER);
        assertEquals(Role.REVIEWER, dto.role());
        assertEquals(Role.REVIEWER, users.findById(employee.getId()).orElseThrow().getRole());
        assertEquals(1, audit.count());

        assertEquals("CANNOT_CHANGE_OWN_ROLE", thrown(() -> userService.changeRole(
                organizer.getId(), organizer.getId(), Role.ADMIN)).getReason());
        assertEquals("ADMIN_REQUIRED", thrown(() -> userService.changeRole(
                employee.getId(), organizer.getId(), Role.ADMIN)).getReason());
        assertEquals("ADMIN_REQUIRED", thrown(() -> userService.changeRole(
                admin.getId(), organizer.getId(), Role.EMPLOYEE)).getReason());
        assertEquals(Role.ADMIN, users.findById(admin.getId()).orElseThrow().getRole());
    }

    @Test
    void adminCanGrantAdminAndSameRoleIsANoOp() {
        userService.changeRole(employee.getId(), admin.getId(), Role.ADMIN);
        assertEquals(Role.ADMIN, users.findById(employee.getId()).orElseThrow().getRole());
        userService.changeRole(employee.getId(), admin.getId(), Role.ADMIN);
        assertEquals(1, audit.count());
        assertEquals(404, thrown(() -> userService.changeRole(
                java.util.UUID.randomUUID(), admin.getId(), Role.REVIEWER)).getStatusCode().value());
    }

    @Test
    void actorRoleIsCheckedAgainstTheDatabaseNotTheToken() {
        assertEquals(403, thrown(() -> userService.changeRole(
                organizer.getId(), employee.getId(), Role.EMPLOYEE)).getStatusCode().value());
        assertEquals(Role.ORGANIZER, users.findById(organizer.getId()).orElseThrow().getRole());
    }

    // ---- settings ----

    @Test
    void judgingWeightsMustSumTo100AndReplaceThePreviousSet() {
        var first = settingsService.update(organizer.getId(),
                new UpdateSettingsRequest(Map.of("innovation", 40, "feasibility", 35, "impact", 25)));
        assertEquals(Map.of("innovation", "40", "feasibility", "35", "impact", "25"), first.judgingCriteria());

        var second = settingsService.update(organizer.getId(),
                new UpdateSettingsRequest(Map.of("innovation", 60, "impact", 40)));
        assertEquals(Map.of("innovation", "60", "impact", "40"), second.judgingCriteria());
        assertEquals(2, settings.count());
        assertEquals(2, audit.count());

        assertEquals("JUDGING_WEIGHTS_MUST_SUM_TO_100", thrown(() -> settingsService.update(organizer.getId(),
                new UpdateSettingsRequest(Map.of("innovation", 60, "impact", 30)))).getReason());
        assertEquals(2, settings.count());
        assertEquals(HttpStatus.BAD_REQUEST.value(), 400);
    }
}