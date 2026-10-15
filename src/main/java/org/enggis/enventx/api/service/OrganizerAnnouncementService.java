package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.Announcement;
import org.enggis.enventx.api.domain.enums.AnnouncementState;
import org.enggis.enventx.api.domain.enums.AuditAction;
import org.enggis.enventx.api.dto.AnnouncementInputDto;
import org.enggis.enventx.api.dto.AnnouncementPatchDto;
import org.enggis.enventx.api.dto.OrganizerAnnouncementDto;
import org.enggis.enventx.api.repository.AnnouncementRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizerAnnouncementService {

    private final AnnouncementRepository announcements;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<OrganizerAnnouncementDto> list() {
        return announcements.findAllByOrderByCreatedAtDesc().stream().map(OrganizerAnnouncementDto::from).toList();
    }

    @Transactional
    public OrganizerAnnouncementDto create(AnnouncementInputDto input) {
        var a = new Announcement();
        a.setTitle(TextSanitizer.required(input.title()));
        a.setContent(TextSanitizer.required(input.content()));
        a.setPinned(Boolean.TRUE.equals(input.pinned()));
        a.setPublishAt(input.publishAt());
        a.setState(AnnouncementState.DRAFT);
        return OrganizerAnnouncementDto.from(announcements.save(a));
    }

    @Transactional
    public OrganizerAnnouncementDto update(UUID id, AnnouncementPatchDto patch) {
        var a = find(id);
        if (a.getState() == AnnouncementState.ARCHIVED) {
            throw error(HttpStatus.CONFLICT, "ANNOUNCEMENT_ARCHIVED");
        }
        if (patch.title() != null) {
            if (patch.title().isBlank()) {
                throw error(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED");
            }
            a.setTitle(TextSanitizer.required(patch.title()));
        }
        if (patch.content() != null) {
            if (patch.content().isBlank()) {
                throw error(HttpStatus.BAD_REQUEST, "CONTENT_REQUIRED");
            }
            a.setContent(TextSanitizer.required(patch.content()));
        }
        if (patch.pinned() != null) {
            a.setPinned(patch.pinned());
        }
        if (patch.publishAt() != null) {
            a.setPublishAt(patch.publishAt());
        }
        return OrganizerAnnouncementDto.from(announcements.saveAndFlush(a));
    }

    /**
     * DRAFT to PUBLISHED, PUBLISHED to DRAFT (unpublish), either to ARCHIVED, and ARCHIVED back to DRAFT only.
     * Requesting the current state is a no-op. Publishing keeps a future publishAt, otherwise sets it to now.
     */
    @Transactional
    public OrganizerAnnouncementDto changeState(UUID id, UUID actorId, AnnouncementState target) {
        var actor = users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        var a = find(id);
        var current = a.getState();
        if (current == target) {
            return OrganizerAnnouncementDto.from(a);
        }
        var allowed = switch (target) {
            case DRAFT -> current == AnnouncementState.PUBLISHED || current == AnnouncementState.ARCHIVED;
            case PUBLISHED -> current == AnnouncementState.DRAFT;
            case ARCHIVED -> current == AnnouncementState.DRAFT || current == AnnouncementState.PUBLISHED;
        };
        if (!allowed) {
            throw error(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION");
        }
        if (target == AnnouncementState.PUBLISHED) {
            var now = Instant.now();
            if (a.getPublishAt() == null || a.getPublishAt().isBefore(now)) {
                a.setPublishAt(now);
            }
        }
        a.setState(target);
        announcements.saveAndFlush(a);
        audit.record(actor, AuditAction.ANNOUNCEMENT_STATE_CHANGED, "ANNOUNCEMENT", a.getId().toString(),
                current + " -> " + target + ", publishAt=" + a.getPublishAt());
        return OrganizerAnnouncementDto.from(a);
    }

    private Announcement find(UUID id) {
        return announcements.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND"));
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
