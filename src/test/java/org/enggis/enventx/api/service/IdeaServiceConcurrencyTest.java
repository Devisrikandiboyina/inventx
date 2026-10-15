package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.dto.IdeaInputDto;
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
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.threads.virtual.enabled=true", "spring.datasource.url=jdbc:h2:mem:conc;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa", "spring.datasource.password="})
class IdeaServiceConcurrencyTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    @Import({IdeaService.class, IdeaLikeService.class})
    static class TestApp {
    }

    @Autowired IdeaService service;
    @Autowired IdeaRepository ideas;
    @Autowired UserRepository users;
    @Autowired IdeaLikeRepository likes;
    @Autowired IdeaTeamMemberRepository teamMembers;
    @Autowired IdeaStageHistoryRepository history;

    User owner;

    @BeforeEach
    void seed() {
        history.deleteAll();
        likes.deleteAll();
        teamMembers.deleteAll();
        ideas.deleteAll();
        users.deleteAll();
        var u = new User();
        u.setName("owner");
        u.setEmail("owner@enggis.com");
        u.setPasswordHash("x");
        u.setRole(Role.EMPLOYEE);
        owner = users.save(u);
    }

    @Test
    void sixConcurrentCreatesYieldExactlyFiveIdeasAndOneConflict() throws Exception {
        int attempts = 6;
        var startGate = new CountDownLatch(1);
        List<Future<Object>> futures;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = java.util.stream.IntStream.range(0, attempts)
                    .<Future<Object>>mapToObj(i -> executor.submit(() -> {
                        startGate.await();
                        try {
                            return service.createIdea(owner.getId(), new IdeaInputDto(
                                    "Concurrent idea " + i, "summary", "description",
                                    "ROBOTICS", "customers", "impact", List.of()));
                        } catch (ResponseStatusException e) {
                            return e;
                        }
                    }))
                    .toList();
            startGate.countDown();
        }

        var conflicts = new java.util.ArrayList<ResponseStatusException>();
        int created = 0;
        for (var future : futures) {
            switch (future.get()) {
                case ResponseStatusException e -> conflicts.add(e);
                default -> created++;
            }
        }

        assertEquals(5, created);
        assertEquals(1, conflicts.size());
        var conflict = assertInstanceOf(ResponseStatusException.class, conflicts.getFirst());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("IDEA_LIMIT", conflict.getReason());
        assertEquals(5, ideas.countActiveIdeas(owner.getId()));
    }
}
