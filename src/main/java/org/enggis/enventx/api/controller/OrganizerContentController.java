package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.AnnouncementInputDto;
import org.enggis.enventx.api.dto.AnnouncementPatchDto;
import org.enggis.enventx.api.dto.AnnouncementStateRequest;
import org.enggis.enventx.api.dto.GuidelineInputDto;
import org.enggis.enventx.api.dto.GuidelinePatchDto;
import org.enggis.enventx.api.dto.OrganizerAnnouncementDto;
import org.enggis.enventx.api.dto.OrganizerGuidelineDto;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaFileService;
import org.enggis.enventx.api.service.OrganizerAnnouncementService;
import org.enggis.enventx.api.service.OrganizerGuidelineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizer")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ORGANIZER)
public class OrganizerContentController {

    private final OrganizerGuidelineService guidelines;
    private final IdeaFileService files;
    private final OrganizerAnnouncementService announcements;

    @GetMapping("/guidelines")
    @Operation(summary = "All guidelines, published or not")
    public List<OrganizerGuidelineDto> guidelines() {
        return guidelines.list();
    }

    @PostMapping("/guidelines")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerGuidelineDto createGuideline(@Valid @RequestBody GuidelineInputDto input) {
        return guidelines.create(input);
    }

    @PatchMapping("/guidelines/{id}")
    public OrganizerGuidelineDto updateGuideline(@PathVariable UUID id, @Valid @RequestBody GuidelinePatchDto patch) {
        return guidelines.update(id, patch);
    }

    @DeleteMapping("/guidelines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGuideline(@PathVariable UUID id) {
        guidelines.delete(id);
    }

    @PostMapping(value = "/guidelines/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace the guideline file (PDF or DOCX, up to 20 MB)")
    public OrganizerGuidelineDto uploadGuidelineFile(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        files.replaceGuidelineFile(id, file);
        return guidelines.get(id);
    }

    @GetMapping("/announcements")
    @Operation(summary = "All announcements in every state")
    public List<OrganizerAnnouncementDto> announcements() {
        return announcements.list();
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a DRAFT; publish it with the state endpoint")
    public OrganizerAnnouncementDto createAnnouncement(@Valid @RequestBody AnnouncementInputDto input) {
        return announcements.create(input);
    }

    @PatchMapping("/announcements/{id}")
    public OrganizerAnnouncementDto updateAnnouncement(@PathVariable UUID id,
                                                       @Valid @RequestBody AnnouncementPatchDto patch) {
        return announcements.update(id, patch);
    }

    @PostMapping("/announcements/{id}/state")
    @Operation(summary = "Publish, unpublish (DRAFT) or archive; every change is audited")
    public OrganizerAnnouncementDto changeAnnouncementState(
            @AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
            @Valid @RequestBody AnnouncementStateRequest request) {
        return announcements.changeState(id, me.id(), request.state());
    }


    @PostMapping(
            value = "/guidelines/with-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a guideline with an optional PDF or DOCX file")
    public OrganizerGuidelineDto createGuidelineWithFile(
            @Valid @RequestPart("input") GuidelineInputDto input,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        return guidelines.createWithFile(input, file);
    }

}
