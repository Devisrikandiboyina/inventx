package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.Announcement;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.AnnouncementState;
import org.enggis.enventx.api.domain.enums.DeliveryStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.repository.AnnouncementRepository;
import org.enggis.enventx.api.repository.AuditLogRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.enggis.enventx.api.repository.WhatsAppDeliveryAttemptRepository;
import org.enggis.enventx.api.whatsapp.WhatsAppClient;
import org.enggis.enventx.api.whatsapp.WhatsAppProperties;
import org.enggis.enventx.api.whatsapp.WhatsAppSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(AuditService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WhatsAppServiceTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    @Autowired AnnouncementRepository announcements;
    @Autowired WhatsAppDeliveryAttemptRepository attempts;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository audit;
    @Autowired AuditService auditService;
    @Autowired PlatformTransactionManager tx;

    User organizer;
    Announcement published;

    static class FakeClient implements WhatsAppClient {
        final List<String> sentTo = new ArrayList<>();
        final List<List<String>> params = new ArrayList<>();
        String failFor;

        @Override
        public String sendTemplate(String to, String template, String language, List<String> body) {
            if (to.equals(failFor)) {
                throw new WhatsAppSendException("HTTP 400: bad number");
            }
            sentTo.add(to);
            params.add(body);
            return "wamid." + to;
        }
    }

    @BeforeEach
    void seed() {
        attempts.deleteAll();
        audit.deleteAll();
        announcements.deleteAll();
        users.deleteAll();
        var u = new User();
        u.setName("olga");
        u.setEmail("olga@enggis.com");
        u.setPasswordHash("x");
        u.setRole(Role.ORGANIZER);
        organizer = users.save(u);
        published = announcement(AnnouncementState.PUBLISHED, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private Announcement announcement(AnnouncementState state, Instant publishAt) {
        var a = new Announcement();
        a.setTitle("Big\nnews");
        a.setContent("Line one\n\n   line   two");
        a.setState(state);
        a.setPublishAt(publishAt);
        return announcements.save(a);
    }

    private WhatsAppProperties props(String... recipients) {
        return new WhatsAppProperties(null, "123", "token", "announcement", "en", List.of(recipients), null);
    }

    private WhatsAppService service(WhatsAppProperties p, WhatsAppClient client) {
        return new WhatsAppService(announcements, attempts, users, auditService, p, client, tx);
    }

    @Test
    void unconfiguredIntegrationRecordsNotConfiguredAndSendsNothing() {
        var client = new FakeClient();
        var service = service(new WhatsAppProperties(null, null, null, null, null, null, null), client);

        var attempt = service.send(published.getId(), organizer.getId());

        assertEquals(DeliveryStatus.NOT_CONFIGURED, attempt.status());
        assertTrue(client.sentTo.isEmpty());
        assertEquals(1, attempts.count());
        assertEquals(1, audit.count());

        var status = service.status();
        assertFalse(status.configured());
        assertFalse(status.supportsGroupDelivery());
        assertEquals(4, status.missingSettings().size());
        assertEquals(DeliveryStatus.NOT_CONFIGURED, status.lastAttempt().status());
    }

    @Test
    void sendsCleanedTemplateToEveryRecipientAndRecordsMessageIds() {
        var client = new FakeClient();
        var service = service(props("919876543210", "919876543211"), client);

        var attempt = service.send(published.getId(), organizer.getId());

        assertEquals(DeliveryStatus.SENT, attempt.status());
        assertEquals(2, attempt.deliveredCount());
        assertEquals("wamid.919876543210,wamid.919876543211", attempt.providerMessageId());
        assertNull(attempt.error());
        assertEquals(List.of("Big news", "Line one line two"), client.params.getFirst());
        assertEquals(organizer.getId(), attempt.attemptedById());
    }

    @Test
    void partialAndTotalFailuresAreReportedHonestly() {
        var client = new FakeClient();
        client.failFor = "919876543211";
        var partial = service(props("919876543210", "919876543211"), client).send(published.getId(), organizer.getId());
        assertEquals(DeliveryStatus.PARTIAL, partial.status());
        assertEquals(1, partial.deliveredCount());
        assertTrue(partial.error().contains("1 of 2"));

        var other = announcement(AnnouncementState.PUBLISHED, Instant.now().minusSeconds(60));
        var failed = service(props("919876543211"), client).send(other.getId(), organizer.getId());
        assertEquals(DeliveryStatus.FAILED, failed.status());
        assertNull(failed.providerMessageId());
    }

    @Test
    void onlyLivePublishedAnnouncementsCanBeSent() {
        var service = service(props("919876543210"), new FakeClient());
        var draft = announcement(AnnouncementState.DRAFT, null);
        var scheduled = announcement(AnnouncementState.PUBLISHED, Instant.now().plus(1, ChronoUnit.DAYS));

        assertEquals("ANNOUNCEMENT_NOT_PUBLISHED", reason(() -> service.send(draft.getId(), organizer.getId())));
        assertEquals("ANNOUNCEMENT_NOT_PUBLISHED", reason(() -> service.send(scheduled.getId(), organizer.getId())));
        assertEquals("ANNOUNCEMENT_NOT_FOUND", reason(() -> service.send(UUID.randomUUID(), organizer.getId())));
        assertEquals(0, attempts.count());
    }

    @Test
    void immediateResendAfterSuccessIsBlocked() {
        var client = new FakeClient();
        var service = service(props("919876543210"), client);
        service.send(published.getId(), organizer.getId());

        assertEquals("RECENTLY_SENT", reason(() -> service.send(published.getId(), organizer.getId())));
        assertEquals(1, client.sentTo.size());
    }

    private static String reason(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getReason();
    }
}
