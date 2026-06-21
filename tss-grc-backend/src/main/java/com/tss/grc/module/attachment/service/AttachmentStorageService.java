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
 * {@code <base>/attachments/<entityType>/org-<id>/}.
 */
@Service
public class AttachmentStorageService extends LocalFileStorageSupport {

    public AttachmentStorageService(
            @Value("${STORAGE_BASE_PATH:./uploads}") String basePath,
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
