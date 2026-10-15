package org.enggis.enventx.api.repository;

import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.show-sql=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserRowLockTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void secondTransactionBlocksUntilFirstCommits() throws Exception {
        var template = new TransactionTemplate(transactionManager);
        var user = new User();
        user.setName("Locked");
        user.setEmail("locked@enggis.com");
        user.setPasswordHash("x");
        user.setRole(Role.EMPLOYEE);
        var id = template.execute(status -> users.save(user)).getId();

        var holderHasLock = new CountDownLatch(1);
        var holderReleasedAt = new AtomicLong();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> template.executeWithoutResult(status -> {
                users.findByIdForUpdate(id).orElseThrow();
                holderHasLock.countDown();
                sleep(1_000);
                holderReleasedAt.set(System.nanoTime());
            }));
            assertTrue(holderHasLock.await(10, TimeUnit.SECONDS));

            var waiterAcquiredAt = new AtomicLong();
            template.executeWithoutResult(status -> {
                users.findByIdForUpdate(id).orElseThrow();
                waiterAcquiredAt.set(System.nanoTime());
            });

            holder.get(10, TimeUnit.SECONDS);
            assertTrue(waiterAcquiredAt.get() >= holderReleasedAt.get(),
                    "second transaction acquired the row lock before the first one finished");
        } finally {
            executor.shutdownNow();
            template.executeWithoutResult(status -> users.deleteById(id));
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
