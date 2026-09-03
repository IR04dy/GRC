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
 *   <li>{@code grc.storage.base-path} (default {@code ./uploads}) — base dir; files go
 *       under {@code <base>/evidence/org-<id>/}. Set it with {@code STORAGE_BASE_PATH}
 *       or, under {@code prod}, {@code FILE_STORAGE_PATH}: the yml maps both onto this
 *       one property, which is what this class reads.</li>
 *   <li>{@code grc.evidence.max-file-size-mb} (default 50) — per-file cap, also enforced by Spring multipart.</li>
 * </ul>
 */
@Service
public class EvidenceStorageService extends LocalFileStorageSupport {

    public EvidenceStorageService(
            // F2-456: read the PROPERTY, never the bare env key. This was
            // @Value("${STORAGE_BASE_PATH:./uploads}"), which resolves the pre-profile
            // key, so it is invisible to
            // every override the yml expresses -- and application-prod.yml re-points
            // grc.storage.base-path at ${FILE_STORAGE_PATH}. The two spellings AGREE in
            // dev and DIVERGE in prod, which is why no amount of local testing saw it:
            // binder PDFs went to the operator's configured root while attachments and
            // evidence went to ./uploads, relative to the service working directory.
            @Value("${grc.storage.base-path:./uploads}") String basePath,
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

    /**
     * Read the raw bytes of a stored evidence file by its root-relative {@code filePath}
     * (as persisted on {@code EvidenceItem.filePath}). Path-traversal-guarded — the resolved
     * path must stay inside the storage root. Mirrors {@code AttachmentStorageService.load}.
     *
     * @throws java.nio.file.NoSuchFileException-wrapped IllegalStateException if the file is absent
     */
    public byte[] load(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Requested path escapes the storage root.");
        }
        try {
            return java.nio.file.Files.readAllBytes(target);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read evidence file: " + relativePath, e);
        }
    }
}
