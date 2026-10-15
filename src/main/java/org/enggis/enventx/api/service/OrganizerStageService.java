package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.StageConfig;
import org.enggis.enventx.api.domain.enums.AuditAction;
import org.enggis.enventx.api.domain.enums.Stage;
import org.enggis.enventx.api.dto.OrganizerStageDto;
import org.enggis.enventx.api.dto.StageDatesRequest;
import org.enggis.enventx.api.repository.StageConfigRepository;
import org.enggis.enventx.api.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizerStageService {

    private final StageConfigRepository stages;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public OrganizerStageDto get(String code) {
        var stage = parse(code);
        return OrganizerStageDto.from(stages.findByStage(stage).orElseGet(() -> defaults(stage)));
    }

    @Transactional
    public OrganizerStageDto update(String code, UUID actorId, StageDatesRequest request) {
        var stage = parse(code);
        if (!request.confirmation()) {
            throw error(HttpStatus.BAD_REQUEST, "CONFIRMATION_REQUIRED");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw error(HttpStatus.BAD_REQUEST, "END_BEFORE_START");
        }
        var actor = users.findById(actorId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
        var config = stages.findByStage(stage).orElseGet(() -> defaults(stage));
        var before = "start=" + config.getStartDate() + ", end=" + config.getEndDate();
        config.setStartDate(request.startDate());
        config.setEndDate(request.endDate());
        config.setUpdatedBy(actor);
        try {
            config = stages.saveAndFlush(config);
        } catch (DataIntegrityViolationException e) {
            throw error(HttpStatus.CONFLICT, "STAGE_CONFLICT");
        }
        audit.record(actor, AuditAction.STAGE_DATES_UPDATED, "STAGE", stage.name(),
                before + " -> start=" + config.getStartDate() + ", end=" + config.getEndDate());
        return OrganizerStageDto.from(config);
    }

    private static StageConfig defaults(Stage stage) {
        var config = new StageConfig();
        config.setStage(stage);
        config.setSequenceOrder(stage.ordinal() + 1);
        config.setEnabled(true);
        var words = stage.name().toLowerCase(Locale.ROOT).split("_");
        var name = new StringBuilder();
        for (var word : words) {
            name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        config.setDisplayName(name.toString());
        return config;
    }

    private static Stage parse(String code) {
        try {
            return Stage.valueOf(code.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw error(HttpStatus.NOT_FOUND, "STAGE_NOT_FOUND");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String code) {
        return new ResponseStatusException(status, code);
    }
}
