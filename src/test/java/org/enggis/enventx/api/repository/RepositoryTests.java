package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.Announcement;
import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.IdeaLike;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.domain.enums.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.show-sql=true")
@Transactional
class RepositoryTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @Autowired UserRepository users;
    @Autowired IdeaRepository ideas;
    @Autowired IdeaLikeRepository likes;
    @Autowired AnnouncementRepository announcements;

    User alice;
    User bob;

    @BeforeEach
    void seed() {
        alice = users.save(user("Alice", "alice@enggis.com", "Enggis"));
        bob = users.save(user("Bob", "bob@nexsys.co.in", "Nexsys"));
    }

    private User user(String name, String email, String company) {
        var u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setCompany(company);
        u.setRole(Role.EMPLOYEE);
        return u;
    }

    private Idea idea(User owner, String title, IdeaStatus status, CategoryCode category, Stage stage, long likeCount) {
        var i = new Idea();
        i.setTitle(title);
        i.setSummary("summary of " + title);
        i.setDescription("description");
        i.setCategory(category);
        i.setStage(stage);
        i.setStatus(status);
        i.setOwner(owner);
        i.setLikeCount(likeCount);
        return ideas.save(i);
    }

    @Test
    void userLookups() {
        assertTrue(users.findByEmail("alice@enggis.com").isPresent());
        assertTrue(users.existsByEmail("bob@nexsys.co.in"));
        assertFalse(users.existsByEmail("nobody@enggis.com"));
    }

    @Test
    void countActiveIdeasIgnoresArchived() {
        idea(alice, "A1", IdeaStatus.DRAFT, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION, 0);
        idea(alice, "A2", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION, 0);
        idea(alice, "A3", IdeaStatus.ARCHIVED, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION, 0);
        idea(bob, "B1", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.IDEA_SUBMISSION, 0);

        assertEquals(2, ideas.countActiveIdeas(alice.getId()));
    }

    @Test
    void filterHidesOtherUsersDraftsAndArchived() {
        idea(alice, "Alice draft", IdeaStatus.DRAFT, CategoryCode.AR_VR, Stage.IDEA_SUBMISSION, 0);
        idea(alice, "Alice archived", IdeaStatus.ARCHIVED, CategoryCode.AR_VR, Stage.IDEA_SUBMISSION, 0);
        idea(alice, "Alice live", IdeaStatus.SUBMITTED, CategoryCode.AR_VR, Stage.IDEA_SUBMISSION, 0);

        var asBob = ideas.findAll(IdeaSpecifications.filter(bob.getId(), null, null, null, null));
        assertEquals(List.of("Alice live"), asBob.stream().map(Idea::getTitle).toList());

        var asAlice = ideas.findAll(IdeaSpecifications.filter(alice.getId(), null, null, null, null));
        assertEquals(3, asAlice.size());

        var anonymous = ideas.findAll(IdeaSpecifications.filter(null, null, null, null, null));
        assertEquals(1, anonymous.size());
    }

    @Test
    void filterBySearchCategoryCompanyAndStage() {
        idea(alice, "Robot arm", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING, 1);
        idea(alice, "Digital twin plant", IdeaStatus.SUBMITTED, CategoryCode.DIGITAL_TWINS, Stage.SCREENING, 1);
        idea(bob, "Robot vision", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.DEMO, 1);
        idea(bob, "100% yield", IdeaStatus.SUBMITTED, CategoryCode.QUALITY_RELIABILITY, Stage.DEMO, 1);

        assertEquals(2, ideas.findAll(IdeaSpecifications.filter(null, "ROBOT", null, null, null)).size());
        assertEquals(2, ideas.findAll(IdeaSpecifications.filter(null, null, CategoryCode.ROBOTICS, null, null)).size());
        assertEquals(2, ideas.findAll(IdeaSpecifications.filter(null, null, null, "nexsys", null)).size());
        assertEquals(2, ideas.findAll(IdeaSpecifications.filter(null, null, null, null, Stage.SCREENING)).size());
        assertEquals(1, ideas.findAll(IdeaSpecifications.filter(null, "robot", CategoryCode.ROBOTICS, "Nexsys", Stage.DEMO)).size());
        assertEquals(1, ideas.findAll(IdeaSpecifications.filter(null, "100%", null, null, null)).size());
        // '%' must be treated literally, not as a wildcard
        assertEquals(1, ideas.findAll(IdeaSpecifications.filter(null, "%", null, null, null)).size());
    }

    @Test
    void sortingModes() {
        var older = idea(alice, "Zebra", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING, 5);
        var middle = idea(alice, "Apple", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING, 50);
        var newer = idea(alice, "Mango", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING, 10);
        ideas.flush();
        setCreatedAt(older, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(middle, Instant.parse("2026-02-01T00:00:00Z"));
        setCreatedAt(newer, Instant.parse("2026-03-01T00:00:00Z"));

        var spec = IdeaSpecifications.filter(null, null, null, null, null);
        assertEquals(List.of("Mango", "Apple", "Zebra"), titles(spec, IdeaSort.NEWEST));
        assertEquals(List.of("Zebra", "Apple", "Mango"), titles(spec, IdeaSort.OLDEST));
        assertEquals(List.of("Apple", "Mango", "Zebra"), titles(spec, IdeaSort.POPULAR));
        assertEquals(List.of("Apple", "Mango", "Zebra"), titles(spec, IdeaSort.TITLE));
        assertEquals(IdeaSort.NEWEST, IdeaSort.fromParam("bogus"));
        assertEquals(IdeaSort.POPULAR, IdeaSort.fromParam("popular"));
    }

    private List<String> titles(org.springframework.data.jpa.domain.Specification<Idea> spec, IdeaSort sort) {
        return ideas.findAll(spec, PageRequest.of(0, 10, sort.toSort())).stream().map(Idea::getTitle).toList();
    }

    private void setCreatedAt(Idea idea, Instant createdAt) {
        // createdAt is not updatable through the entity, so write it directly
        em.createNativeQuery("update ideas set created_at = ?1 where id = ?2")
                .setParameter(1, java.sql.Timestamp.from(createdAt))
                .setParameter(2, idea.getId())
                .executeUpdate();
        em.clear();
    }

    @Autowired jakarta.persistence.EntityManager em;

    @Test
    void likeExistsAndIdempotentDelete() {
        var i = idea(alice, "Liked", IdeaStatus.SUBMITTED, CategoryCode.ROBOTICS, Stage.SCREENING, 0);
        var like = new IdeaLike();
        like.setIdea(i);
        like.setUser(bob);
        likes.saveAndFlush(like);

        assertTrue(likes.existsByIdeaIdAndUserId(i.getId(), bob.getId()));
        assertFalse(likes.existsByIdeaIdAndUserId(i.getId(), alice.getId()));
        assertEquals(1, likes.deleteByIdeaIdAndUserId(i.getId(), bob.getId()));
        assertEquals(0, likes.deleteByIdeaIdAndUserId(i.getId(), bob.getId()));
        assertFalse(likes.existsByIdeaIdAndUserId(i.getId(), bob.getId()));
    }

    @Test
    void announcementsPublishedActivePinnedFirst() {
        var now = Instant.now();
        announcements.save(announcement("old unpinned", now.minus(5, ChronoUnit.DAYS), true, false));
        announcements.save(announcement("new unpinned", now.minus(1, ChronoUnit.DAYS), true, false));
        announcements.save(announcement("old pinned", now.minus(10, ChronoUnit.DAYS), true, true));
        announcements.save(announcement("inactive", now.minus(1, ChronoUnit.DAYS), false, true));
        announcements.save(announcement("future", now.plus(1, ChronoUnit.DAYS), true, true));
        announcements.save(announcement("unpublished", null, true, true));

        var result = announcements.findAllPublishedAndActive(LocalDateTime.now());
        assertEquals(List.of("old pinned", "new unpinned", "old unpinned"),
                result.stream().map(Announcement::getTitle).toList());
    }

    private Announcement announcement(String title, Instant publishedAt, boolean active, boolean pinned) {
        var a = new Announcement();
        a.setTitle(title);
        a.setContent("content");
        a.setPublishAt(publishedAt);
        a.setState(active ? org.enggis.enventx.api.domain.enums.AnnouncementState.PUBLISHED : org.enggis.enventx.api.domain.enums.AnnouncementState.DRAFT);
        a.setPinned(pinned);
        return a;
    }
}
