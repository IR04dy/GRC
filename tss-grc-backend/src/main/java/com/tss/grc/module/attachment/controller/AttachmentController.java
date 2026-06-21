package com.tss.grc.module.attachment.controller;

import com.tss.grc.module.attachment.dto.AttachmentResponse;
import com.tss.grc.module.attachment.dto.AttachmentUpdateRequest;
import com.tss.grc.module.attachment.service.AttachmentService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Generic, typed attachments — any entity can own files of a given doc_type.
 * Reusable across every register/form ("attach plan / report / policy / …").
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService service;

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List attachments for an entity")
    public List<AttachmentResponse> list(@PathVariable String entityType, @PathVariable Long entityId) {
        return service.list(entityType, entityId);
    }

    @PostMapping(value = "/{entityType}/{entityId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Upload a typed attachment for an entity (multipart)")
    public AttachmentResponse upload(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "docType", defaultValue = "OTHER") String docType,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "validTo", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
            @AuthenticationPrincipal GrcUserDetails me) {
        return service.upload(entityType, entityId, docType, title, validTo, file,
            me != null ? me.getUserId() : null);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "Download an attachment's binary")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        AttachmentService.DownloadPayload p = service.download(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + p.fileName() + "\"")
            .contentType(MediaType.parseMediaType(p.mimeType()))
            .body(p.bytes());
    }

    @PostMapping(value = "/{id}/version", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Upload a new version that supersedes this attachment (multipart)")
    public AttachmentResponse newVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "validTo", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo,
            @AuthenticationPrincipal GrcUserDetails me) {
        return service.replaceVersion(id, docType, title, validTo, file,
            me != null ? me.getUserId() : null);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "Version history of this attachment's document (newest first)")
    public List<AttachmentResponse> history(@PathVariable Long id) {
        return service.history(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('EVIDENCE', 'WRITE')")
    @Operation(summary = "Edit attachment metadata (docType / title / expiry) — binary unchanged")
    public AttachmentResponse update(@PathVariable Long id, @RequestBody AttachmentUpdateRequest req) {
        return service.update(id, req.docType(), req.titleEn(), req.validTo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('EVIDENCE', 'DELETE')")
    @Operation(summary = "Delete an attachment (soft)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
