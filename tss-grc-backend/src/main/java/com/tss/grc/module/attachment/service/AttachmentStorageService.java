package com.tss.grc.module.attachment.service;

import com.tss.grc.common.storage.LocalFileStorageSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Local-filesystem storage for generic entity attachments. Mirrors the proven
 * evidence storage (allow-list, size cap, server-generated UUID name,
 * path-traversal guard, SHA-256 — all shared via {@link LocalFileStorageSupport})
 * and adds a safe {@link #load} for download. Files live under
 * {@code <base>/attachments/<entityType>/org-<id>/}, where {@code <base>} is
 * {@code grc.storage.base-path} -- the property, so a profile override reaches it.
 */
@Service
public class AttachmentStorageService extends LocalFileStorageSupport {

    public AttachmentStorageService(
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
        super(basePath, "attachments", maxFileSizeMb, "attachment");
    }

    public StoredFile store(Long orgId, String entityType, MultipartFile file) {
        String original = validate(file);
        String typeDir = entityType == null ? "misc" : entityType.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        Path dir = root.resolve(typeDir).resolve("org-" + orgId).normalize();
        Path target = dir.resolve(safeFileName(original)).normalize();
        return write(target, original, file, "Failed to store attachment file.");
    }

    /** Read an attachment's bytes by its stored relative path (path-traversal safe). */
    public byte[] load(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        // Distinct message preserved from the original load() guard (the
        // shared store-side guard says "Resolved storage path …").
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Requested path escapes the storage root.");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read attachment file.", e);
        }
    }
}
