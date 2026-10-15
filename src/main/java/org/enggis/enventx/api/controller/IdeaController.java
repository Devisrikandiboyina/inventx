package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.IdeaDto;
import org.enggis.enventx.api.dto.IdeaInputDto;
import org.enggis.enventx.api.dto.IdeaPatchDto;
import org.enggis.enventx.api.dto.IdeaSummaryDto;
import org.enggis.enventx.api.dto.LikeStatusDto;
import org.enggis.enventx.api.dto.PagedResponse;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaLikeService;
import org.enggis.enventx.api.service.IdeaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_IDEAS)
public class IdeaController {

    private final IdeaService ideaService;
    private final IdeaLikeService likeService;

    @GetMapping
    @Operation(summary = "Search ideas; other users' drafts and archived ideas are excluded")
    public PagedResponse<IdeaSummaryDto> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String stage,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ideaService.listIdeas(me.id(), q, category, company, stage, sort, page, size);
    }

    @GetMapping("/mine")
    @Operation(summary = "The caller's own ideas, including drafts and archived")
    public List<IdeaSummaryDto> mine(@AuthenticationPrincipal UserPrincipal me) {
        return ideaService.listMine(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a DRAFT idea; 409 IDEA_LIMIT when the caller already owns 5 active ideas")
    public IdeaDto create(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody IdeaInputDto input) {
        return ideaService.createIdea(me.id(), input);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Idea details; adminNotes only for organizers")
    public IdeaDto get(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return ideaService.getIdea(id, me.id());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Owner-only partial update while DRAFT or CHANGES_REQUESTED")
    public IdeaDto update(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                          @Valid @RequestBody IdeaPatchDto patch) {
        return ideaService.updateIdea(id, me.id(), patch);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Owner-only; DRAFT or CHANGES_REQUESTED becomes SUBMITTED")
    public IdeaDto submit(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return ideaService.submitIdea(id, me.id());
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Idempotent like")
    public LikeStatusDto like(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return likeService.like(id, me.id());
    }

    @DeleteMapping("/{id}/like")
    @Operation(summary = "Idempotent unlike")
    public LikeStatusDto unlike(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return likeService.unlike(id, me.id());
    }
}
