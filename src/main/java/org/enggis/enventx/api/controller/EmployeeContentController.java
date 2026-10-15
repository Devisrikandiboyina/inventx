package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.AnnouncementDto;
import org.enggis.enventx.api.dto.AppSettingsDto;
import org.enggis.enventx.api.dto.DashboardStatsDto;
import org.enggis.enventx.api.dto.GuidelineDto;
import org.enggis.enventx.api.dto.NotificationDto;
import org.enggis.enventx.api.dto.ReadAllResultDto;
import org.enggis.enventx.api.dto.StageConfigDto;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.ContentService;
import org.enggis.enventx.api.service.DashboardService;
import org.enggis.enventx.api.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmployeeContentController {

    private final DashboardService dashboard;
    private final ContentService content;
    private final NotificationService notifications;

    @GetMapping("/dashboard")
    @Tag(name = OpenApiConfig.TAG_IDEAS)
    @Operation(summary = "Counts for the caller; byStage and byCategory include every value, zeros too")
    public DashboardStatsDto dashboard(@AuthenticationPrincipal UserPrincipal me) {
        return dashboard.statsFor(me.id());
    }

    @GetMapping("/stages")
    @Tag(name = OpenApiConfig.TAG_GUIDELINES)
    @Operation(summary = "Enabled stages in order")
    public List<StageConfigDto> stages() {
        return content.stages();
    }

    @GetMapping("/guidelines")
    @Tag(name = OpenApiConfig.TAG_GUIDELINES)
    @Operation(summary = "Active (published) guidelines only")
    public List<GuidelineDto> guidelines() {
        return content.guidelines();
    }

    @GetMapping("/announcements")
    @Tag(name = OpenApiConfig.TAG_ANNOUNCEMENTS)
    @Operation(summary = "Active announcements already published, pinned first")
    public List<AnnouncementDto> announcements() {
        return content.announcements();
    }

    @GetMapping("/notifications")
    @Tag(name = OpenApiConfig.TAG_NOTIFICATIONS)
    @Operation(summary = "The caller's 50 most recent notifications")
    public List<NotificationDto> notifications(@AuthenticationPrincipal UserPrincipal me) {
        return notifications.latest(me.id());
    }

    @PostMapping("/notifications/read-all")
    @Tag(name = OpenApiConfig.TAG_NOTIFICATIONS)
    @Operation(summary = "Mark all of the caller's notifications as read")
    public ReadAllResultDto readAll(@AuthenticationPrincipal UserPrincipal me) {
        return notifications.markAllRead(me.id());
    }

    @GetMapping("/settings")
    @Tag(name = OpenApiConfig.TAG_GUIDELINES)
    @Operation(summary = "Business limits and judging criteria")
    public AppSettingsDto settings() {
        return content.settings();
    }
}
