package com.tss.grc.module.attachment.controller;

import com.tss.grc.module.attachment.dto.AttachmentResponse;
import com.tss.grc.module.attachment.dto.AttachmentUpdateRequest;
import com.tss.grc.module.attachment.service.AttachmentAccessPolicy;
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
 *
 * <p><b>SCR-004 — two authorities, not one.</b> {@code EVIDENCE:READ/WRITE/DELETE} below says the
 * caller may work with evidence <em>at all</em>; {@link AttachmentAccessPolicy} says they may work
 * with <em>this record's</em> evidence, by inheriting the parent record's own read guard. Before
 * that second check existed, a persona holding {@code EVIDENCE_READ} could download the attachments
 * of a policy the platform refused to show them (reproduced live: parent 403, bytes 200).
 *
 * <p>The two path-keyed endpoints check here, where {@code entityType} arrives from the client. The
 * id-keyed endpoints ({@code /{id}/…}) cannot: their entity type lives on the row, so the check
 * happens in {@link AttachmentService} once the row is loaded. Deliberately <em>not</em> hoisted
 * into {@code AttachmentService.list} — {@code ObligationController} and {@code PolicyController}
 * call that method internally from endpoints that already gate the parent, and one of them is
 * guarded by role rather than by {@code COMPLIANCE:READ}, so checking there would 403 a working
 * evidence-package endpoint for {@code RISK_MANAGER} and {@code BCM_MANAGER}.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService service;
    private final AttachmentAccessPolicy access;

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasPermission('EVIDENCE', 'READ')")
    @Operation(summary = "List attachments for an entity")
    public List<AttachmentResponse> list(@PathVariable String entityType, @PathVariable Long entityId) {
        access.requireAccess(entityType, "list");
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
            @RequestParam(value = "classification", required = false) String classification,
            @AuthenticationPrincipal GrcUserDetails me) {
        access.requireAccess(entityType, "upload");
        return service.upload(entityType, entityId, docType, title, validTo, classification, file,
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
            @RequestParam(value = "classification", required = false) String classification,
            @AuthenticationPrincipal GrcUserDetails me) {
        return service.replaceVersion(id, docType, title, validTo, classification, file,
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
        return service.update(id, req.docType(), req.titleEn(), req.validTo(), req.classification());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission('EVIDENCE', 'DELETE')")
    @Operation(summary = "Delete an attachment (soft)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
