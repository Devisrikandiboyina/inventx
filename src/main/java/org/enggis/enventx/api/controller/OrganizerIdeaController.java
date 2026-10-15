package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.IdeaDto;
import org.enggis.enventx.api.dto.IdeaSummaryDto;
import org.enggis.enventx.api.dto.OrganizerIdeaPatchDto;
import org.enggis.enventx.api.dto.PagedResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.enggis.enventx.api.dto.StageTransitionRequest;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/ideas")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZER)
public class OrganizerIdeaController {

    private final IdeaService ideaService;

    @GetMapping
    @Operation(summary = "All ideas including DRAFT and ARCHIVED; filter by status and ownerId")
    public PagedResponse<IdeaSummaryDto> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ideaService.listAllForOrganizer(me.id(), q, category, company, stage, status, ownerId, sort, page, size);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Recategorize, set admin notes and tags; omitted fields are unchanged")
    public IdeaDto update(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                          @Valid @RequestBody OrganizerIdeaPatchDto patch) {
        return ideaService.organizerUpdateIdea(id, me.id(), patch);
    }

    @PostMapping("/{id}/stage-transitions")
    @Operation(summary = "PROMOTE, APPROVE, REQUEST_CHANGES, REJECT, ARCHIVE or RESTORE; reason is required and a history row is written")
    public IdeaDto transition(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                              @Valid @RequestBody StageTransitionRequest request) {
        return ideaService.executeStageTransition(id, me.id(), request);
    }
}
