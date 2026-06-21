package com.tss.grc.module.evidence.service;

import com.tss.grc.common.storage.LocalFileStorageSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Local-filesystem storage backend for evidence-item binary attachments.
 *
 * <p>Closes the evidence-lifecycle "no file upload" gap. Validates size and
 * extension against an allow-list, generates its own random filename (never
 * trusts the client name), and writes under a per-org sub-directory that is
 * guaranteed to stay inside the configured root (path-traversal safe). The
 * SHA-256 of the bytes is returned so the caller can anchor it into the
 * EvidenceItem chain-of-custody hash.
 *
 * <p>The shared validation / hashing / path-traversal mechanics live in
 * {@link LocalFileStorageSupport}; this class owns only the evidence-specific
 * on-disk layout ({@code <base>/evidence/org-<id>/<uuid>.<ext>}).
 *
 * <p>Config (base {@code application.yml} / env):
 * <ul>
 *   <li>{@code STORAGE_BASE_PATH} (default {@code ./uploads}) — base dir; files go under {@code <base>/evidence/org-<id>/}.</li>
 *   <li>{@code grc.evidence.max-file-size-mb} (default 50) — per-file cap, also enforced by Spring multipart.</li>
 * </ul>
 */
@Service
public class EvidenceStorageService extends LocalFileStorageSupport {

    public EvidenceStorageService(
            @Value("${STORAGE_BASE_PATH:./uploads}") String basePath,
            @Value("${grc.evidence.max-file-size-mb:50}") long maxFileSizeMb) {
        super(basePath, "evidence", maxFileSizeMb, "evidence");
    }

    /**
     * Validate and persist the uploaded file. Throws {@link IllegalArgumentException}
     * (→ HTTP 400) for an empty file, an over-size file, or a disallowed
     * extension; {@link IllegalStateException} (→ 500) only on a real I/O failure.
     */
    public StoredFile store(Long orgId, MultipartFile file) {
        String original = validate(file);
        Path orgDir = root.resolve("org-" + orgId).normalize();
        Path target = orgDir.resolve(safeFileName(original)).normalize();
        return write(target, original, file, "Failed to store evidence file.");
    }
}
