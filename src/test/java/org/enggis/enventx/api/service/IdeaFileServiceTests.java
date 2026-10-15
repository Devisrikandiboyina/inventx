package org.enggis.enventx.api.service;

import org.enggis.enventx.api.domain.entity.Guideline;
import org.enggis.enventx.api.domain.entity.Idea;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.CategoryCode;
import org.enggis.enventx.api.domain.enums.IdeaStatus;
import org.enggis.enventx.api.domain.enums.Role;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.repository.GuidelineRepository;
import org.enggis.enventx.api.repository.IdeaAttachmentRepository;
import org.enggis.enventx.api.repository.IdeaRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({IdeaFileService.class, IdeaFileServiceTests.StorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdeaFileServiceTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("org.enggis.enventx.api.domain.entity")
    @EnableJpaRepositories("org.enggis.enventx.api.repository")
    static class TestApp {
    }

    static class StorageConfig {
        @Bean
        InMemoryFileStorage storage() {
            return new InMemoryFileStorage();
        }
    }

    static final byte[] PDF = "%PDF-1.7 some body bytes".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 1, 2};

    @Autowired IdeaFileService service;
    @Autowired InMemoryFileStorage storage;
    @Autowired IdeaRepository ideas;
    @Autowired UserRepository users;
    @Autowired IdeaAttachmentRepository attachments;
    @Autowired GuidelineRepository guidelines;

    User owner;
    User other;
    User organizer;

    @BeforeEach
    void seed() {
        attachments.deleteAll();
        ideas.deleteAll();
        guidelines.deleteAll();
        users.deleteAll();
        storage.objects.clear();
        storage.deleted.clear();
        owner = user("owner", Role.EMPLOYEE);
        other = user("other", Role.EMPLOYEE);
        organizer = user("organizer", Role.ORGANIZER);
    }

    private User user(String name, Role role) {
        var u = new User();
        u.setName(name);
        u.setEmail(name + "@enggis.com");
        u.setPasswordHash("x");
        u.setRole(role);
        return users.save(u);
    }

    private Idea idea(IdeaStatus status) {
        var i = new Idea();
        i.setTitle("t");
        i.setSummary("s");
        i.setDescription("d");
        i.setCategory(CategoryCode.ROBOTICS);
        i.setStage(Stage.IDEA_SUBMISSION);
        i.setStatus(status);
        i.setOwner(owner);
        return ideas.save(i);
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", PDF);
    }

    private static int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test
    void ownerUploadStoresObjectUnderRandomKeyAndRecordsMetadata() {
        var idea = idea(IdeaStatus.DRAFT);

        var dto = service.addAttachment(idea.getId(), owner.getId(), pdf("../../My Plan.pdf"));

        assertEquals("My Plan.pdf", dto.fileName());
        assertEquals("application/pdf", dto.contentType());
        assertEquals(PDF.length, dto.sizeBytes());
        var key = attachments.findById(dto.id()).orElseThrow().getObjectKey();
        assertTrue(key.startsWith("ideas/" + idea.getId() + "/attachments/"));
        assertFalse(key.contains("Plan"));
        assertEquals(1, storage.objects.size());
    }

    @Test
    void nonOwnersCannotUploadEvenOrganizers() {
        var idea = idea(IdeaStatus.DRAFT);
        assertEquals(403, status(() -> service.addAttachment(idea.getId(), other.getId(), pdf("a.pdf"))));
        assertEquals(403, status(() -> service.addAttachment(idea.getId(), organizer.getId(), pdf("a.pdf"))));
        assertEquals(403, status(() -> service.uploadPoster(idea.getId(), other.getId(),
                new MockMultipartFile("file", "p.png", "image/png", PNG))));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void invalidFilesAreRejectedBeforeAnythingIsStored() {
        var idea = idea(IdeaStatus.DRAFT);
        assertEquals(415, status(() -> service.addAttachment(idea.getId(), owner.getId(),
                new MockMultipartFile("file", "fake.pdf", "application/pdf", PNG))));
        assertEquals(400, status(() -> service.addAttachment(idea.getId(), owner.getId(),
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))));
        assertEquals(415, status(() -> service.uploadPoster(idea.getId(), owner.getId(), pdf("poster.pdf"))));
        assertEquals(404, status(() -> service.addAttachment(UUID.randomUUID(), owner.getId(), pdf("a.pdf"))));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void archivedIdeasRejectUploads() {
        var idea = idea(IdeaStatus.ARCHIVED);
        assertEquals(409, status(() -> service.addAttachment(idea.getId(), owner.getId(), pdf("a.pdf"))));
    }

    @Test
    void attachmentLimitIsEnforcedAndTheExtraObjectIsRemoved() {
        var idea = idea(IdeaStatus.DRAFT);
        for (int i = 0; i < IdeaFileService.MAX_ATTACHMENTS_PER_IDEA; i++) {
            service.addAttachment(idea.getId(), owner.getId(), pdf("f" + i + ".pdf"));
        }
        assertEquals(409, status(() -> service.addAttachment(idea.getId(), owner.getId(), pdf("extra.pdf"))));
        assertEquals(IdeaFileService.MAX_ATTACHMENTS_PER_IDEA, storage.objects.size());
        assertEquals(1, storage.deleted.size());
    }

    @Test
    void posterReplacementDeletesThePreviousObject() {
        var idea = idea(IdeaStatus.DRAFT);
        var png = new MockMultipartFile("file", "p.png", "image/png", PNG);

        var first = service.uploadPoster(idea.getId(), owner.getId(), png);
        var firstKey = ideas.findById(idea.getId()).orElseThrow().getPosterUrl();
        service.uploadPoster(idea.getId(), owner.getId(), png);
        var secondKey = ideas.findById(idea.getId()).orElseThrow().getPosterUrl();

        assertNotNull(first.url());
        assertFalse(firstKey.equals(secondKey));
        assertEquals(java.util.List.of(firstKey), storage.deleted);
        assertEquals(1, storage.objects.size());
    }

    @Test
    void linksAreShortLivedAndIssuedOnlyToThoseWhoMaySeeTheIdea() {
        var draft = idea(IdeaStatus.DRAFT);
        var att = service.addAttachment(draft.getId(), owner.getId(), pdf("a.pdf"));

        var link = service.attachmentLink(draft.getId(), att.id(), owner.getId());
        assertTrue(link.url().contains("ttl=300"));
        assertTrue(link.expiresAt().isAfter(java.time.Instant.now()));
        service.attachmentLink(draft.getId(), att.id(), organizer.getId());
        assertEquals(404, status(() -> service.attachmentLink(draft.getId(), att.id(), other.getId())));
        assertEquals(404, status(() -> service.listAttachments(draft.getId(), other.getId())));

        var stored = ideas.findById(draft.getId()).orElseThrow();
        stored.setStatus(IdeaStatus.SUBMITTED);
        ideas.save(stored);
        service.attachmentLink(draft.getId(), att.id(), other.getId());
        assertEquals(1, service.listAttachments(draft.getId(), other.getId()).size());
        assertEquals(404, status(() -> service.attachmentLink(draft.getId(), UUID.randomUUID(), other.getId())));
    }

    @Test
    void attachmentOfAnotherIdeaIsNotReachableThroughThisOne() {
        var a = idea(IdeaStatus.SUBMITTED);
        var b = idea(IdeaStatus.SUBMITTED);
        var att = service.addAttachment(a.getId(), owner.getId(), pdf("a.pdf"));
        assertEquals(404, status(() -> service.attachmentLink(b.getId(), att.id(), other.getId())));
    }

    @Test
    void posterLinkRequiresAPoster() {
        var idea = idea(IdeaStatus.SUBMITTED);
        assertEquals(404, status(() -> service.posterLink(idea.getId(), other.getId())));
        service.uploadPoster(idea.getId(), owner.getId(), new MockMultipartFile("file", "p.png", "image/png", PNG));
        assertTrue(service.posterLink(idea.getId(), other.getId()).url().contains("poster"));
    }

    @Test
    void guidelineLinkNeedsAnActiveGuidelineWithAFile() {
        var g = new Guideline();
        g.setTitle("Rules");
        g.setContent("c");
        g.setFileKey("guidelines/abc");
        g.setFileName("rules.pdf");
        g = guidelines.save(g);
        assertTrue(service.guidelineFileLink(g.getId()).url().contains("guidelines/abc"));

        g.setActive(false);
        guidelines.save(g);
        var id = g.getId();
        assertEquals(404, status(() -> service.guidelineFileLink(id)));
        assertEquals(404, status(() -> service.guidelineFileLink(UUID.randomUUID())));
    }
}
