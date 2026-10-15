package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.*;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.OrganizerDashboardService;
import org.enggis.enventx.api.service.OrganizerSettingsService;
import org.enggis.enventx.api.service.OrganizerStageService;
import org.enggis.enventx.api.service.OrganizerUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZER)
public class OrganizerAdminController {

    private final OrganizerDashboardService dashboard;
    private final OrganizerStageService stages;
    private final OrganizerUserService userService;
    private final OrganizerSettingsService settings;

    @GetMapping("/dashboard")
    @Operation(summary = "Platform-wide counts by status, stage and category (zeros included)")
    public OrganizerDashboardDto dashboard() {
        return dashboard.stats();
    }

    @GetMapping("/stages/{code}")
    public OrganizerStageDto stage(@PathVariable String code) {
        return stages.get(code);
    }

    @PatchMapping("/stages/{code}")
    @Operation(summary = "Set stage dates; confirmation must be true; the change is audited")
    public OrganizerStageDto updateStage(@AuthenticationPrincipal UserPrincipal me, @PathVariable String code,
                                         @Valid @RequestBody StageDatesRequest request) {
        return stages.update(code, me.id(), request);
    }

    @GetMapping("/users")
    public PagedResponse<UserDto> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.list(q, role, page, size);
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role; not your own, and only an ADMIN may grant or revoke ADMIN")
    public UserDto changeRole(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                              @Valid @RequestBody RoleChangeRequest request) {
        return userService.changeRole(id, me.id(), request.role());
    }

    @PutMapping("/settings")
    @Operation(summary = "Replace the judging criteria; weights must sum to 100")
    public AppSettingsDto updateSettings(@AuthenticationPrincipal UserPrincipal me,
                                         @Valid @RequestBody UpdateSettingsRequest request) {
        return settings.update(me.id(), request);
    }


    @GetMapping("/overview")
    public OrganizerOverviewDto overview() {
        return dashboard.overview();
    }
}
