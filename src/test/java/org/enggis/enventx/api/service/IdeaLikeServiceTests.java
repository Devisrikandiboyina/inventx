package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.repository.IdeaLikeRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(IdeaLikeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdeaLikeServiceTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @Autowired IdeaLikeService service;
    @Autowired IdeaRepository ideas;
    @Autowired IdeaLikeRepository likes;
    @Autowired UserRepository users;

    Idea idea;
    User owner;

    @BeforeEach
    void seed() {
        likes.deleteAll();
        ideas.deleteAll();
        users.deleteAll();
        owner = newUser("owner");
        idea = newIdea(IdeaStatus.SUBMITTED);
    }

    private User newUser(String name) {
        var u = new User();
        u.setName(name);
        u.setEmail(name + "-" + UUID.randomUUID() + "@enggis.com");
        u.setPasswordHash("x");
        u.setRole(Role.EMPLOYEE);
        return users.save(u);
    }

    private Idea newIdea(IdeaStatus status) {
        var i = new Idea();
        i.setTitle("Idea");
        i.setSummary("s");
        i.setDescription("d");
        i.setCategory(CategoryCode.ROBOTICS);
        i.setStage(Stage.IDEA_SUBMISSION);
        i.setStatus(status);
        i.setOwner(owner);
        return ideas.save(i);
    }

    private long count(UUID ideaId) {
        return ideas.findLikeCountById(ideaId).orElseThrow();
    }

    @Test
    void likeIsIdempotentAndUnlikeIsIdempotent() {
        var fan = newUser("fan");

        assertEquals(1, service.like(idea.getId(), fan.getId()).likeCount());
        var again = service.like(idea.getId(), fan.getId());
        assertTrue(again.likedByMe());
        assertEquals(1, again.likeCount());
        assertEquals(1, likes.count());

        var removed = service.unlike(idea.getId(), fan.getId());
        assertFalse(removed.likedByMe());
        assertEquals(0, removed.likeCount());
        assertEquals(0, service.unlike(idea.getId(), fan.getId()).likeCount());
        assertEquals(0, count(idea.getId()));
    }

    @Test
    void draftArchivedAndMissingIdeasCannotBeLiked() {
        var fan = newUser("fan");
        var draft = newIdea(IdeaStatus.DRAFT);
        var archived = newIdea(IdeaStatus.ARCHIVED);

        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.like(draft.getId(), fan.getId())).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.like(archived.getId(), fan.getId())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.like(UUID.randomUUID(), fan.getId())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.unlike(UUID.randomUUID(), fan.getId())).getStatusCode().value());
        assertEquals(0, likes.count());
    }

    @Test
    void concurrentLikesFromDifferentUsersAreAllCounted() throws Exception {
        var fans = new ArrayList<User>();
        for (int i = 0; i < 12; i++) {
            fans.add(newUser("fan" + i));
        }
        runConcurrently(fans.stream().map(f -> (Runnable) () -> service.like(idea.getId(), f.getId())).toList());

        assertEquals(12, count(idea.getId()));
        assertEquals(12, likes.count());
    }

    @Test
    void concurrentDuplicateLikesFromOneUserCountOnce() throws Exception {
        var fan = newUser("fan");
        var tasks = new ArrayList<Runnable>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> service.like(idea.getId(), fan.getId()));
        }
        runConcurrently(tasks);

        assertEquals(1, count(idea.getId()));
        assertEquals(1, likes.count());
    }

    @Test
    void recalculateRepairsDrift() {
        var fan = newUser("fan");
        service.like(idea.getId(), fan.getId());
        var drifted = ideas.findById(idea.getId()).orElseThrow();
        drifted.setLikeCount(99);
        ideas.saveAndFlush(drifted);
        assertEquals(99, count(idea.getId()));

        ideas.recalculateAllLikeCounts();
        assertEquals(1, count(idea.getId()));
    }

    private void runConcurrently(List<Runnable> tasks) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = tasks.stream().map(task -> executor.submit(() -> {
                start.await();
                task.run();
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }
    }
}
