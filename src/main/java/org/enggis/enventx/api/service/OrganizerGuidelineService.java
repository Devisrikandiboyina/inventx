package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.Guideline;
import org.enggis.enventx.api.dto.GuidelineInputDto;
import org.enggis.enventx.api.dto.GuidelinePatchDto;
import org.enggis.enventx.api.dto.OrganizerGuidelineDto;
import org.enggis.enventx.api.repository.GuidelineRepository;
import org.enggis.enventx.api.storage.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** "published" is the guideline's {@code active} flag; employees only see published guidelines. */
@Service

public class OrganizerGuidelineService {

    private final GuidelineRepository guidelines;
    private final FileStorage storage;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final IdeaFileService files;

    public OrganizerGuidelineService(GuidelineRepository guidelines, FileStorage storage,
                                     PlatformTransactionManager manager,IdeaFileService files) {
        this.guidelines = guidelines;
        this.storage = storage;
        this.tx = new TransactionTemplate(manager);
        this.readTx = new TransactionTemplate(manager);
        this.readTx.setReadOnly(true);
        this.files = files;
    }

    public List<OrganizerGuidelineDto> list() {
        return readTx.execute(s -> guidelines.findAllByOrderBySequenceOrderAsc().stream()
                .map(OrganizerGuidelineDto::from).toList());
    }

    public OrganizerGuidelineDto get(UUID id) {
        return readTx.execute(s -> OrganizerGuidelineDto.from(find(id)));
    }

    public OrganizerGuidelineDto create(GuidelineInputDto input) {
        return tx.execute(s -> {
            var g = new Guideline();
            g.setTitle(TextSanitizer.required(input.title()));
            g.setContent(TextSanitizer.required(input.content()));
            g.setSequenceOrder(input.sequenceOrder() != null ? input.sequenceOrder() : guidelines.maxSequenceOrder() + 1);
            g.setActive(Boolean.TRUE.equals(input.published()));
            return OrganizerGuidelineDto.from(guidelines.save(g));
        });
    }



    @Transactional
    public OrganizerGuidelineDto createWithFile(
            GuidelineInputDto input,
            MultipartFile file) {

        var created = create(input);

        if (file == null || file.isEmpty()) {
            return created;
        }

        try {
            files.replaceGuidelineFile(created.id(), file);
            return get(created.id());
        } catch (RuntimeException e) {
            try {
                delete(created.id());
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    public OrganizerGuidelineDto update(UUID id, GuidelinePatchDto patch) {
        return tx.execute(s -> {
            var g = find(id);
            if (patch.title() != null) {
                if (patch.title().isBlank()) {
                    throw error(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED");
                }
                g.setTitle(TextSanitizer.required(patch.title()));
            }
            if (patch.content() != null) {
                if (patch.content().isBlank()) {
                    throw error(HttpStatus.BAD_REQUEST, "CONTENT_REQUIRED");
                }
                g.setContent(TextSanitizer.required(patch.content()));
            }
            if (patch.sequenceOrder() != null) {
                g.setSequenceOrder(patch.sequenceOrder());
            }
            if (patch.published() != null) {
                g.setActive(patch.published());
            }
            return OrganizerGuidelineDto.from(guidelines.saveAndFlush(g));
        });
    }

    /** Guidelines are not idea data, so they are removed for real; the stored file goes with them. */
    public void delete(UUID id) {
        var key = tx.execute(s -> {
            var g = find(id);
            guidelines.delete(g);
            return g.getFileKey();
        });
        if (key != null) {
            storage.delete(key);
        }
    }

    private Guideline find(UUID id) {
        return guidelines.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GUIDELINE_NOT_FOUND"));
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
