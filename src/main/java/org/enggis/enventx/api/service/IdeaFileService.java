package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.IdeaAttachment;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.dto.AttachmentDto;
import org.enggis.enventx.api.dto.SignedLinkDto;
import org.enggis.enventx.api.repository.GuidelineRepository;
import org.enggis.enventx.api.repository.IdeaAttachmentRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.enggis.enventx.api.storage.FileStorage;
import org.enggis.enventx.api.storage.FileTypes;
import org.enggis.enventx.api.storage.FileTypes.Kind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Files are validated first, uploaded outside any database transaction (so a slow upload never holds an idea row
 * lock), and the metadata row is written afterwards; if that fails the uploaded object is removed again.
 */
@Service
public class IdeaFileService {

    public static final int MAX_ATTACHMENTS_PER_IDEA = 10;
    public static final Duration LINK_TTL = Duration.ofMinutes(5);

    private final IdeaRepository ideas;
    private final UserRepository users;
    private final IdeaAttachmentRepository attachments;
    private final GuidelineRepository guidelines;
    private final FileStorage storage;
    private final org.enggis.enventx.api.scan.VirusScanner scanner;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public IdeaFileService(IdeaRepository ideas, UserRepository users, IdeaAttachmentRepository attachments,
                           GuidelineRepository guidelines, FileStorage storage, PlatformTransactionManager manager,
                           org.springframework.beans.factory.ObjectProvider<org.enggis.enventx.api.scan.VirusScanner> scanner) {
        this.ideas = ideas;
        this.users = users;
        this.attachments = attachments;
        this.guidelines = guidelines;
        this.storage = storage;
        this.scanner = scanner.getIfAvailable(org.enggis.enventx.api.scan.VirusScanner::disabled);
        this.tx = new TransactionTemplate(manager);
        this.readTx = new TransactionTemplate(manager);
        this.readTx.setReadOnly(true);
    }

    public AttachmentDto addAttachment(UUID ideaId, UUID userId, MultipartFile file) {
        readTx.executeWithoutResult(s -> requireOwnerOfActiveIdea(findIdea(ideaId), userId));
        var upload = prepare(Kind.ATTACHMENT, file);
        var key = "ideas/" + ideaId + "/attachments/" + UUID.randomUUID();
        upload(key, file, upload.contentType());
        try {
            return tx.execute(s -> {
                var idea = ideas.findByIdForUpdate(ideaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));
                requireOwnerOfActiveIdea(idea, userId);
                if (attachments.countByIdeaId(ideaId) >= MAX_ATTACHMENTS_PER_IDEA) {
                    throw error(HttpStatus.CONFLICT, "ATTACHMENT_LIMIT");
                }
                var entity = new IdeaAttachment();
                entity.setIdea(idea);
                entity.setFileName(upload.fileName());
                entity.setContentType(upload.contentType());
                entity.setSizeBytes(file.getSize());
                entity.setObjectKey(key);
                entity.setUploadedBy(idea.getOwner());
                return AttachmentDto.from(attachments.save(entity));
            });
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    public SignedLinkDto uploadPoster(UUID ideaId, UUID userId, MultipartFile file) {
        readTx.executeWithoutResult(s -> requireOwnerOfActiveIdea(findIdea(ideaId), userId));
        var upload = prepare(Kind.POSTER, file);
        var key = "ideas/" + ideaId + "/poster/" + UUID.randomUUID();
        upload(key, file, upload.contentType());
        String previous;
        try {
            previous = tx.execute(s -> {
                var idea = ideas.findByIdForUpdate(ideaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));
                requireOwnerOfActiveIdea(idea, userId);
                var old = idea.getPosterUrl();
                idea.setPosterUrl(key);
                return old;
            });
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
        if (previous != null) {
            storage.delete(previous);
        }
        return link(key, "poster." + FileTypes.extensionOf(upload.fileName()));
    }

    public List<AttachmentDto> listAttachments(UUID ideaId, UUID viewerId) {
        return readTx.execute(s -> {
            requireVisible(findIdea(ideaId), viewerId);
            return attachments.findByIdeaIdOrderByCreatedAtAsc(ideaId).stream().map(AttachmentDto::from).toList();
        });
    }

    public SignedLinkDto attachmentLink(UUID ideaId, UUID attachmentId, UUID viewerId) {
        var found = readTx.execute(s -> {
            requireVisible(findIdea(ideaId), viewerId);
            return attachments.findByIdAndIdeaId(attachmentId, ideaId)
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND"));
        });
        return link(found.getObjectKey(), found.getFileName());
    }

    public SignedLinkDto posterLink(UUID ideaId, UUID viewerId) {
        var key = readTx.execute(s -> {
            var idea = findIdea(ideaId);
            requireVisible(idea, viewerId);
            return idea.getPosterUrl();
        });
        if (key == null) {
            throw error(HttpStatus.NOT_FOUND, "POSTER_NOT_FOUND");
        }
        return link(key, "poster");
    }

    public SignedLinkDto guidelineFileLink(UUID guidelineId) {
        var guideline = guidelines.findById(guidelineId)
                .filter(g -> g.isActive() && g.getFileKey() != null)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GUIDELINE_FILE_NOT_FOUND"));
        return link(guideline.getFileKey(), guideline.getFileName());
    }

    /** Replaces the guideline's file (pdf/docx); the previous object is removed once the new one is recorded. */
    public void replaceGuidelineFile(UUID guidelineId, MultipartFile file) {
        readTx.executeWithoutResult(s -> guidelines.findById(guidelineId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GUIDELINE_NOT_FOUND")));
        var upload = prepare(Kind.GUIDELINE, file);
        var key = "guidelines/" + guidelineId + "/" + UUID.randomUUID();
        upload(key, file, upload.contentType());
        String previous;
        try {
            previous = tx.execute(s -> {
                var guideline = guidelines.findById(guidelineId)
                        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GUIDELINE_NOT_FOUND"));
                var old = guideline.getFileKey();
                guideline.setFileKey(key);
                guideline.setFileName(upload.fileName());
                return old;
            });
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
        if (previous != null) {
            storage.delete(previous);
        }
    }

    private record Prepared(String fileName, String contentType) {
    }

    private Prepared prepare(Kind kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "FILE_EMPTY");
        }
        var name = FileTypes.cleanFileName(file.getOriginalFilename());
        try (var in = file.getInputStream()) {
            var contentType = FileTypes.validate(kind, name, in.readNBytes(16), file.getSize());
            try (var again = file.getInputStream()) {
                scanner.scan(again);
            }
            return new Prepared(name, contentType);
        } catch (IOException e) {
            throw error(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE");
        }
    }

    private void upload(String key, MultipartFile file, String contentType) {
        try (var in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw error(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE");
        }
    }

    private SignedLinkDto link(String key, String downloadName) {
        var url = storage.presignedGetUrl(key, LINK_TTL, FileTypes.headerSafeName(downloadName));
        return new SignedLinkDto(url, Instant.now().plus(LINK_TTL));
    }

    private Idea findIdea(UUID ideaId) {
        return ideas.findById(ideaId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND"));
    }

    private static void requireOwnerOfActiveIdea(Idea idea, UUID userId) {
        if (!idea.getOwner().getId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "NOT_OWNER");
        }
        if (idea.getStatus() == IdeaStatus.ARCHIVED) {
            throw error(HttpStatus.CONFLICT, "INVALID_STATUS");
        }
    }

    /** Same visibility rule as the idea itself: others' DRAFT/ARCHIVED ideas look nonexistent. */
    private void requireVisible(Idea idea, UUID viewerId) {
        var viewer = users.findById(viewerId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        var organizer = switch (viewer.getRole()) {
            case ORGANIZER, ADMIN -> true;
            case EMPLOYEE, REVIEWER -> false;
        };
        var hidden = switch (idea.getStatus()) {
            case DRAFT, ARCHIVED -> true;
            case SUBMITTED, UNDER_REVIEW, CHANGES_REQUESTED, APPROVED, REJECTED -> false;
        };
        if (hidden && !organizer && !idea.getOwner().getId().equals(viewerId)) {
            throw error(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
