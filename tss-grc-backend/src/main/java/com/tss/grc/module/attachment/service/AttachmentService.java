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

    private final AttachmentRepository repository;
    private final AttachmentStorageService storage;

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repository
            .findByOrganisationIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(orgId, entityType, entityId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AttachmentResponse upload(String entityType, Long entityId, String docType,
                                     String titleEn, LocalDate validTo, MultipartFile file, Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        String type = (docType == null || docType.isBlank()) ? "OTHER" : docType.toUpperCase();
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
        a.setUploadedBy(userId);
        return toResponse(repository.save(a));
    }

    /** Bytes + metadata for streaming a download. */
    public record DownloadPayload(String fileName, String mimeType, byte[] bytes) {}

    @Transactional(readOnly = true)
    public DownloadPayload download(Long id) {
        Attachment a = mustGet(id);
        return new DownloadPayload(a.getFileName(),
            a.getMimeType() == null ? "application/octet-stream" : a.getMimeType(),
            storage.load(a.getFilePath()));
    }

    @Transactional
    public void delete(Long id) {
        Attachment a = mustGet(id);
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
            a.getValidTo(), a.getUploadedBy(), a.getUploadedAt(),
            "/api/v1/attachments/" + a.getId() + "/download");
    }
}
