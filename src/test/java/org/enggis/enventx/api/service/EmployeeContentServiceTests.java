package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.Announcement;
import org.enggis.enventx.api.domain.entity.AppSetting;
import org.enggis.enventx.api.domain.entity.Guideline;
import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.Notification;
import org.enggis.enventx.api.domain.entity.StageConfig;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.repository.AnnouncementRepository;
import org.enggis.enventx.api.repository.AppSettingRepository;
import org.enggis.enventx.api.repository.GuidelineRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.NotificationRepository;
import org.enggis.enventx.api.repository.StageConfigRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({DashboardService.class, ContentService.class, NotificationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmployeeContentServiceTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @Autowired DashboardService dashboard;
    @Autowired ContentService content;
    @Autowired NotificationService notificationService;
    @Autowired IdeaRepository ideas;
    @Autowired UserRepository users;
    @Autowired StageConfigRepository stages;
    @Autowired GuidelineRepository guidelines;
    @Autowired AnnouncementRepository announcements;
    @Autowired AppSettingRepository settings;
    @Autowired NotificationRepository notifications;

    User alice;
    User bob;

    @BeforeEach
    void seed() {
        notifications.deleteAll();
        ideas.deleteAll();
        announcements.deleteAll();
        guidelines.deleteAll();
        stages.deleteAll();
        settings.deleteAll();
        users.deleteAll();
        alice = user("alice");
        bob = user("bob");
    }

    private User user(String name) {
        var u = new User();
        u.setName(name);
        u.setEmail(name + "@enggis.com");
        u.setPasswordHash("x");
        u.setRole(Role.EMPLOYEE);
        return users.save(u);
    }

    private Idea idea(User owner, CategoryCode category, Stage stage, IdeaStatus status, long likes) {
        var i = new Idea();
        i.setTitle("t");
        i.setSummary("s");
        i.setDescription("d");
        i.setCategory(category);
        i.setStage(stage);
        i.setStatus(status);
        i.setOwner(owner);
        i.setLikeCount(likes);
        return ideas.save(i);
    }

    @Test
    void dashboardListsEveryCategoryAndStageIncludingZeros() {
        var stats = dashboard.statsFor(alice.getId());

        assertEquals(CategoryCode.values().length, stats.byCategory().size());
        assertTrue(stats.byCategory().values().stream().allMatch(c -> c == 0));
        assertEquals(Stage.values().length, stats.byStage().size());
        assertEquals(0, stats.myIdeas());
    }

    @Test
    void dashboardCountsOnlyIdeasTheCallerMaySee() {
        idea(alice, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION, IdeaStatus.DRAFT, 0);
        idea(alice, CategoryCode.ROBOTICS, Stage.SCREENING, IdeaStatus.SUBMITTED, 4);
        idea(alice, CategoryCode.AR_VR, Stage.SCREENING, IdeaStatus.ARCHIVED, 1);
        idea(bob, CategoryCode.ROBOTICS, Stage.SCREENING, IdeaStatus.SUBMITTED, 2);
        idea(bob, CategoryCode.DIGITAL_TWINS, Stage.DEMO, IdeaStatus.DRAFT, 9);

        var forAlice = dashboard.statsFor(alice.getId());
        assertEquals(3, forAlice.myIdeas());
        assertEquals(2, forAlice.myActiveIdeas());
        assertEquals(5, forAlice.likesReceived());
        assertEquals(4, forAlice.totalIdeas());
        assertEquals(3L, forAlice.byCategory().get(CategoryCode.ROBOTICS));
        assertEquals(0L, forAlice.byCategory().get(CategoryCode.DIGITAL_TWINS));
        assertEquals(3L, forAlice.byStage().get(Stage.SCREENING));

        var forBob = dashboard.statsFor(bob.getId());
        assertEquals(1L, forBob.byCategory().get(CategoryCode.DIGITAL_TWINS));
        assertEquals(1L, forBob.byStage().get(Stage.DEMO));
        assertEquals(2L, forBob.byCategory().get(CategoryCode.ROBOTICS));
    }

    @Test
    void stagesAndGuidelinesAreOrderedAndFilteredToEnabledOrActive() {
        stage(Stage.SCREENING, 2, true);
        stage(Stage.IDEA_SUBMISSION, 1, true);
        stage(Stage.DEMO, 4, false);
        guideline("second", 2, true);
        guideline("first", 1, true);
        guideline("hidden", 3, false);

        assertEquals(List.of(Stage.IDEA_SUBMISSION, Stage.SCREENING),
                content.stages().stream().map(s -> s.stage()).toList());
        assertEquals(List.of("first", "second"), content.guidelines().stream().map(g -> g.title()).toList());
    }

    @Test
    void announcementsAreActivePublishedAndPinnedFirst() {
        var now = Instant.now();
        announcement("old", now.minus(3, ChronoUnit.DAYS), false, true);
        announcement("pinned-older", now.minus(5, ChronoUnit.DAYS), true, true);
        announcement("newest", now.minus(1, ChronoUnit.HOURS), false, true);
        announcement("future", now.plus(1, ChronoUnit.DAYS), false, true);
        announcement("inactive", now.minus(1, ChronoUnit.DAYS), false, false);
        announcement("unpublished", null, false, true);

        assertEquals(List.of("pinned-older", "newest", "old"),
                content.announcements().stream().map(a -> a.title()).toList());
    }

    @Test
    void settingsExposeLimitsAndOnlyJudgingKeys() {
        setting("judging.innovation", "40");
        setting("judging.feasibility", "60");
        setting("smtp.password", "secret");

        var s = content.settings();
        assertEquals(5, s.maxActiveIdeasPerUser());
        assertEquals(4, s.maxTeamSize());
        assertEquals(3, s.maxTeamMembersBesidesOwner());
        assertEquals(20, s.maxUploadSizeMb());
        assertEquals(java.util.Map.of("innovation", "40", "feasibility", "60"), s.judgingCriteria());
    }

    @Test
    void notificationsAreScopedToCallerAndReadAllIsIdempotent() {
        notification(alice, "a1");
        notification(alice, "a2");
        notification(bob, "b1");

        assertEquals(2, notificationService.latest(alice.getId()).size());
        assertEquals(2, notificationService.markAllRead(alice.getId()).updated());
        assertEquals(0, notificationService.markAllRead(alice.getId()).updated());
        assertTrue(notificationService.latest(alice.getId()).stream().allMatch(n -> n.read()));
        assertFalse(notificationService.latest(bob.getId()).getFirst().read());
    }

    private void stage(Stage stage, int order, boolean enabled) {
        var s = new StageConfig();
        s.setStage(stage);
        s.setDisplayName(stage.name());
        s.setSequenceOrder(order);
        s.setEnabled(enabled);
        stages.save(s);
    }

    private void guideline(String title, int order, boolean active) {
        var g = new Guideline();
        g.setTitle(title);
        g.setContent("c");
        g.setSequenceOrder(order);
        g.setActive(active);
        guidelines.save(g);
    }

    private void announcement(String title, Instant publishedAt, boolean pinned, boolean active) {
        var a = new Announcement();
        a.setTitle(title);
        a.setContent("c");
        a.setPublishAt(publishedAt);
        a.setPinned(pinned);
        a.setState(active ? org.enggis.enventx.api.domain.enums.AnnouncementState.PUBLISHED : org.enggis.enventx.api.domain.enums.AnnouncementState.DRAFT);
        announcements.save(a);
    }

    private void setting(String key, String value) {
        var s = new AppSetting();
        s.setKey(key);
        s.setValue(value);
        settings.save(s);
    }

    private void notification(User recipient, String message) {
        var n = new Notification();
        n.setRecipient(recipient);
        n.setType("TEST");
        n.setMessage(message);
        notifications.save(n);
    }
}
