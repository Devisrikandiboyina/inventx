package org.enggis.enventx.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.config.OpenApiConfig;
import org.enggis.enventx.api.dto.AttachmentDto;
import org.enggis.enventx.api.dto.SignedLinkDto;
import org.enggis.enventx.api.security.UserPrincipal;
import org.enggis.enventx.api.service.IdeaFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_IDEAS)
public class IdeaFileController {

    private final IdeaFileService files;

    @PostMapping(value = "/ideas/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Owner-only upload (pdf, docx, pptx, xlsx or image, up to 20 MB, 10 per idea)")
    public AttachmentDto addAttachment(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                                       @RequestParam("file") MultipartFile file) {
        return files.addAttachment(id, me.id(), file);
    }

    @GetMapping("/ideas/{id}/attachments")
    @Operation(summary = "Attachment metadata for an idea the caller may see")
    public List<AttachmentDto> attachments(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return files.listAttachments(id, me.id());
    }

    @GetMapping("/ideas/{id}/attachments/{aid}/link")
    @Operation(summary = "Short-lived signed download URL, issued after the visibility check")
    public SignedLinkDto attachmentLink(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                                        @PathVariable UUID aid) {
        return files.attachmentLink(id, aid, me.id());
    }

    @PostMapping(value = "/ideas/{id}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Owner-only poster upload (png, jpg, gif or webp); replaces any previous poster")
    public SignedLinkDto uploadPoster(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id,
                                      @RequestParam("file") MultipartFile file) {
        return files.uploadPoster(id, me.id(), file);
    }

    @GetMapping("/ideas/{id}/poster/link")
    @Operation(summary = "Short-lived signed URL for the idea poster")
    public SignedLinkDto posterLink(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return files.posterLink(id, me.id());
    }

    @GetMapping("/guidelines/{id}/file-link")
    @Tag(name = OpenApiConfig.TAG_GUIDELINES)
    @Operation(summary = "Short-lived signed URL for an active guideline's file")
    public SignedLinkDto guidelineFileLink(@PathVariable UUID id) {
        return files.guidelineFileLink(id);
    }
}
