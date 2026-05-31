package com.tss.grc.module.evidence.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
 * <p>Config (base {@code application.yml} / env):
 * <ul>
 *   <li>{@code STORAGE_BASE_PATH} (default {@code ./uploads}) — base dir; files go under {@code <base>/evidence/org-<id>/}.</li>
 *   <li>{@code grc.evidence.max-file-size-mb} (default 50) — per-file cap, also enforced by Spring multipart.</li>
 * </ul>
 */
@Service
public class EvidenceStorageService {

    /** Conservative allow-list of evidence document/file extensions. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff",
        "txt", "csv", "log", "md", "json", "xml",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods",
        "zip", "7z", "eml", "msg");

    private final Path root;
    private final long maxBytes;

    public EvidenceStorageService(
            @Value("${STORAGE_BASE_PATH:./uploads}") String basePath,
            @Value("${grc.evidence.max-file-size-mb:50}") long maxFileSizeMb) {
        this.root = Paths.get(basePath, "evidence").toAbsolutePath().normalize();
        this.maxBytes = maxFileSizeMb * 1024L * 1024L;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create evidence storage directory: " + root, e);
        }
    }

    /** Validated, stored result. */
    public record StoredFile(String originalName, String relativePath, long size,
                             String mimeType, String sha256) {}

    /**
     * Validate and persist the uploaded file. Throws {@link IllegalArgumentException}
     * (→ HTTP 400) for an empty file, an over-size file, or a disallowed
     * extension; {@link IllegalStateException} (→ 500) only on a real I/O failure.
     */
    public StoredFile store(Long orgId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided, or the file is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                "File exceeds the maximum allowed size of " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String original = StringUtils.cleanPath(
            Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        String ext = extensionOf(original);
        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                "File type '" + (ext.isEmpty() ? "(none)" : "." + ext) + "' is not allowed. "
                    + "Allowed: " + ALLOWED_EXTENSIONS);
        }

        String safeName = UUID.randomUUID() + "." + ext;
        Path orgDir = root.resolve("org-" + orgId).normalize();
        Path target = orgDir.resolve(safeName).normalize();
        // Defence-in-depth: the resolved target must stay under the root.
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Resolved storage path escapes the storage root.");
        }

        try {
            Files.createDirectories(orgDir);
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            String sha256 = sha256Hex(bytes);
            String relative = root.relativize(target).toString().replace('\\', '/');
            return new StoredFile(original, relative, (long) bytes.length, file.getContentType(), sha256);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store evidence file.", e);
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JVM — unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
