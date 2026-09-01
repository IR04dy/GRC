package com.tss.grc.module.attachment.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.attachment.dto.AttachmentResponse;
import com.tss.grc.module.attachment.entity.Attachment;
import com.tss.grc.module.attachment.repository.AttachmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** Document purposes the same reusable field serves. */
    public static final Set<String> DOC_TYPES = Set.of(
        "PLAN", "REPORT", "POLICY", "CERTIFICATE", "EVIDENCE",
        "CONTRACT", "MINUTES", "QUESTIONNAIRE", "SCREENSHOT", "OTHER");

    /** NDMO-aligned data classifications an evidence artifact may carry. */
    public static final Set<String> CLASSIFICATIONS = Set.of(
        "PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");

    /** Normalize + validate a classification; null/blank stays null (unclassified). */
    private static String normClassification(String c) {
        if (c == null || c.isBlank()) return null;
        String up = c.toUpperCase(Locale.ROOT);
        if (!CLASSIFICATIONS.contains(up)) {
            throw new IllegalArgumentException("Unknown classification '" + c + "'. Valid: " + CLASSIFICATIONS);
        }
        return up;
    }

    private final AttachmentRepository repository;
    private final AttachmentStorageService storage;
    private final AttachmentAccessPolicy access;

    /**
     * SCR-004 — the id-keyed operations resolve the parent's entity type from the row and then
     * require that the caller can see that parent, exactly as
     * {@link com.tss.grc.module.attachment.controller.AttachmentController} does for the two
     * path-keyed ones. An attachment id was previously a tenant-global object reference protected
     * only by the coarse {@code EVIDENCE} permission, so {@code /{id}/download} handed over the
     * bytes of records the caller could not open.
     *
     * <p>{@link #list} is deliberately NOT checked here: it is called internally by
     * {@code ObligationController} and {@code PolicyController} from endpoints that already gate the
     * parent, one of them by role rather than by {@code COMPLIANCE:READ}. The generic HTTP path into
     * {@code list} is checked in the controller instead, so both callers get the authority they
     * should and neither gets one it should not.
     */
    private void requireParentVisible(Attachment a, String operation) {
        access.requireAccess(a.getEntityType(), operation);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repository
            .findByOrganisationIdAndEntityTypeAndEntityIdAndSupersededFalseOrderByUploadedAtDesc(orgId, entityType, entityId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AttachmentResponse upload(String entityType, Long entityId, String docType,
                                     String titleEn, LocalDate validTo, String classification,
                                     MultipartFile file, Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        String type = (docType == null || docType.isBlank()) ? "OTHER" : docType.toUpperCase(Locale.ROOT);
        if (!DOC_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown docType '" + docType + "'. Valid: " + DOC_TYPES);
        }
        AttachmentStorageService.StoredFile stored = storage.store(orgId, entityType, file);
        Attachment a = new Attachment();
        a.setOrganisationId(orgId);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setDocType(type);
        a.setTitleEn(titleEn);
        a.setFileName(stored.originalName());
        a.setFilePath(stored.relativePath());
        a.setFileSizeBytes(stored.size());
        a.setMimeType(stored.mimeType() == null ? "application/octet-stream" : stored.mimeType());
        a.setSha256Hash(stored.sha256());
        a.setValidTo(validTo);
        a.setClassification(normClassification(classification));
        a.setUploadedBy(userId);
        a.setVersion(1);
        a.setSuperseded(false);
        Attachment saved = repository.save(a);
        // The first version anchors its own version chain.
        saved.setVersionGroupId(saved.getId());
        return toResponse(repository.save(saved));
    }

    /**
     * Upload a new version that supersedes an existing attachment. The new row
     * inherits the entity/doc-type/title (overridable) and bumps the version;
     * the prior row is flagged superseded (kept for history). Returns the new
     * current version.
     */
    @Transactional
    public AttachmentResponse replaceVersion(Long oldId, String docType, String titleEn,
                                             LocalDate validTo, String classification,
                                             MultipartFile file, Long userId) {
        Attachment old = mustGet(oldId);
        requireParentVisible(old, "new version");
        Long orgId = old.getOrganisationId();
        String type = (docType == null || docType.isBlank()) ? old.getDocType() : docType.toUpperCase(Locale.ROOT);
        if (!DOC_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown docType '" + docType + "'. Valid: " + DOC_TYPES);
        }
        AttachmentStorageService.StoredFile stored = storage.store(orgId, old.getEntityType(), file);
        Attachment a = new Attachment();
        a.setOrganisationId(orgId);
        a.setEntityType(old.getEntityType());
        a.setEntityId(old.getEntityId());
        a.setDocType(type);
        a.setTitleEn(titleEn != null ? (titleEn.isBlank() ? null : titleEn) : old.getTitleEn());
        a.setFileName(stored.originalName());
        a.setFilePath(stored.relativePath());
        a.setFileSizeBytes(stored.size());
        a.setMimeType(stored.mimeType() == null ? "application/octet-stream" : stored.mimeType());
        a.setSha256Hash(stored.sha256());
        a.setValidTo(validTo != null ? validTo : old.getValidTo());
        // A new version inherits the prior classification unless the caller overrides it.
        a.setClassification(classification != null && !classification.isBlank()
            ? normClassification(classification) : old.getClassification());
        a.setUploadedBy(userId);
        a.setVersion(old.getVersion() + 1);
        a.setVersionGroupId(old.getVersionGroupId() != null ? old.getVersionGroupId() : old.getId());
        a.setSuperseded(false);
        Attachment saved = repository.save(a);
        old.setSuperseded(true);
        repository.save(old);
        return toResponse(saved);
    }

    /** Full version history of the document this attachment belongs to (newest first). */
    @Transactional(readOnly = true)
    public List<AttachmentResponse> history(Long id) {
        Attachment a = mustGet(id);
        requireParentVisible(a, "history");
        Long group = a.getVersionGroupId() != null ? a.getVersionGroupId() : a.getId();
        return repository.findByOrganisationIdAndVersionGroupIdOrderByVersionDesc(a.getOrganisationId(), group)
            .stream().map(this::toResponse).toList();
    }

    /**
     * Metadata-only edit — re-type / rename / re-date an attachment without
     * deleting + re-uploading. Null fields are left unchanged; a blank title
     * clears it.
     */
    @Transactional
    public AttachmentResponse update(Long id, String docType, String titleEn, LocalDate validTo,
                                     String classification) {
        Attachment a = mustGet(id);
        requireParentVisible(a, "metadata edit");
        if (docType != null && !docType.isBlank()) {
            String type = docType.toUpperCase(Locale.ROOT);
            if (!DOC_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unknown docType '" + docType + "'. Valid: " + DOC_TYPES);
            }
            a.setDocType(type);
        }
        if (titleEn != null) a.setTitleEn(titleEn.isBlank() ? null : titleEn);
        if (validTo != null) a.setValidTo(validTo);
        // Blank string clears the classification; null leaves it unchanged.
        if (classification != null) a.setClassification(classification.isBlank() ? null : normClassification(classification));
        return toResponse(repository.save(a));
    }

    /** Bytes + metadata for streaming a download. */
    public record DownloadPayload(String fileName, String mimeType, byte[] bytes) {}

    @Transactional(readOnly = true)
    public DownloadPayload download(Long id) {
        Attachment a = mustGet(id);
        requireParentVisible(a, "download");
        return new DownloadPayload(a.getFileName(),
            a.getMimeType() == null ? "application/octet-stream" : a.getMimeType(),
            storage.load(a.getFilePath()));
    }

    @Transactional
    public void delete(Long id) {
        Attachment a = mustGet(id);
        requireParentVisible(a, "delete");
        a.setIsDeleted(true);
        repository.save(a);
    }

    private Attachment mustGet(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repository.findById(id)
            .filter(a -> a.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + id));
    }

    private AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(
            a.getId(), a.getEntityType(), a.getEntityId(), a.getDocType(), a.getTitleEn(),
            a.getFileName(), a.getFileSizeBytes(), a.getMimeType(), a.getSha256Hash(),
            a.getValidTo(), a.getClassification(), a.getUploadedBy(), a.getUploadedAt(),
            a.getVersion(), a.getVersionGroupId(),
            "/api/v1/attachments/" + a.getId() + "/download");
    }
}
