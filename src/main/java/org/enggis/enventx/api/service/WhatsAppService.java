package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.WhatsAppDeliveryAttempt;
import org.enggis.enventx.api.domain.enums.AnnouncementState;
import org.enggis.enventx.api.domain.enums.AuditAction;
import org.enggis.enventx.api.domain.enums.DeliveryStatus;
import org.enggis.enventx.api.dto.WhatsAppAttemptDto;
import org.enggis.enventx.api.dto.WhatsAppIntegrationStatusDto;
import org.enggis.enventx.api.repository.AnnouncementRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.enggis.enventx.api.repository.WhatsAppDeliveryAttemptRepository;
import org.enggis.enventx.api.whatsapp.WhatsAppClient;
import org.enggis.enventx.api.whatsapp.WhatsAppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Provider calls happen outside any database transaction; the attempt row (and audit entry) is written afterwards.
 * Nothing is faked: without configuration an attempt is recorded as NOT_CONFIGURED.
 */
@Service
public class WhatsAppService {

    public static final Duration DUPLICATE_GUARD = Duration.ofSeconds(60);
    private static final int MAX_TEXT_LENGTH = 900;
    private static final int MAX_ERROR_LENGTH = 900;

    private final AnnouncementRepository announcements;
    private final WhatsAppDeliveryAttemptRepository attempts;
    private final UserRepository users;
    private final AuditService audit;
    private final WhatsAppProperties properties;
    private final WhatsAppClient client;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public WhatsAppService(AnnouncementRepository announcements, WhatsAppDeliveryAttemptRepository attempts,
                           UserRepository users, AuditService audit, WhatsAppProperties properties,
                           WhatsAppClient client, PlatformTransactionManager manager) {
        this.announcements = announcements;
        this.attempts = attempts;
        this.users = users;
        this.audit = audit;
        this.properties = properties;
        this.client = client;
        this.tx = new TransactionTemplate(manager);
        this.readTx = new TransactionTemplate(manager);
        this.readTx.setReadOnly(true);
    }

    public WhatsAppIntegrationStatusDto status() {
        return readTx.execute(s -> new WhatsAppIntegrationStatusDto(
                properties.configured(),
                "WHATSAPP_CLOUD_API",
                false,
                "TEMPLATE_TO_OPTED_IN_NUMBERS",
                properties.recipients().size(),
                properties.templateName(),
                properties.adminWorkflowUrl(),
                properties.missingSettings(),
                attempts.findFirstByOrderByAttemptedAtDesc().map(WhatsAppAttemptDto::from).orElse(null)));
    }

    public WhatsAppAttemptDto send(UUID announcementId, UUID actorId) {
        record Snapshot(String title, String content) {
        }
        var snapshot = readTx.execute(s -> {
            var a = announcements.findById(announcementId)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND"));
            if (a.getState() != AnnouncementState.PUBLISHED
                    || a.getPublishAt() == null || a.getPublishAt().isAfter(Instant.now())) {
                throw error(HttpStatus.CONFLICT, "ANNOUNCEMENT_NOT_PUBLISHED");
            }
            if (attempts.existsByAnnouncementIdAndStatusInAndAttemptedAtAfter(
                    announcementId, EnumSet.of(DeliveryStatus.SENT, DeliveryStatus.PARTIAL),
                    Instant.now().minus(DUPLICATE_GUARD))) {
                throw error(HttpStatus.CONFLICT, "RECENTLY_SENT");
            }
            return new Snapshot(a.getTitle(), a.getContent());
        });

        var outcome = properties.configured()
                ? deliver(snapshot.title(), snapshot.content())
                : new Outcome(DeliveryStatus.NOT_CONFIGURED, null, "WhatsApp Cloud API is not configured", 0, 0);

        return tx.execute(s -> {
            var actor = users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
            var attempt = new WhatsAppDeliveryAttempt();
            attempt.setAnnouncement(announcements.getReferenceById(announcementId));
            attempt.setStatus(outcome.status());
            attempt.setProviderMessageId(outcome.messageIds());
            attempt.setError(outcome.error());
            attempt.setRecipientCount(properties.recipients().size());
            attempt.setDeliveredCount(outcome.delivered());
            attempt.setAttemptedBy(actor);
            attempts.saveAndFlush(attempt);
            audit.record(actor, AuditAction.WHATSAPP_NOTIFICATION, "ANNOUNCEMENT", announcementId.toString(),
                    "status=" + outcome.status() + ", delivered=" + outcome.delivered() + "/"
                            + properties.recipients().size());
            return WhatsAppAttemptDto.from(attempt);
        });
    }

    private record Outcome(DeliveryStatus status, String messageIds, String error, int total, int delivered) {
    }

    private Outcome deliver(String title, String content) {
        var parameters = List.of(clean(title, 200), clean(content, MAX_TEXT_LENGTH));
        var ids = new ArrayList<String>();
        String firstError = null;
        for (var number : properties.recipients()) {
            try {
                ids.add(client.sendTemplate(number, properties.templateName(), properties.languageCode(), parameters));
            } catch (RuntimeException e) {
                if (firstError == null) {
                    firstError = e.getMessage();
                }
            }
        }
        var failed = properties.recipients().size() - ids.size();
        var status = failed == 0 ? DeliveryStatus.SENT : ids.isEmpty() ? DeliveryStatus.FAILED : DeliveryStatus.PARTIAL;
        var error = failed == 0 ? null : truncate(failed + " of " + properties.recipients().size()
                + " messages failed: " + firstError);
        return new Outcome(status, ids.isEmpty() ? null : truncate(String.join(",", ids)), error,
                properties.recipients().size(), ids.size());
    }

    /** Template variables may not contain newlines, tabs or runs of spaces. */
    private static String clean(String text, int max) {
        var collapsed = text.replaceAll("\\s+", " ").strip();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max - 3) + "...";
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
