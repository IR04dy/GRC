package com.tss.grc.common.storage;

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
 * Shared base for local-filesystem storage backends (evidence-item binaries
 * and generic entity attachments). Centralises the security-critical pieces
 * those backends had duplicated byte-for-byte (QUAL-IE-003):
 *
 * <ul>
 *   <li>the conservative {@link #ALLOWED_EXTENSIONS} allow-list,</li>
 *   <li>the per-file size cap,</li>
 *   <li>{@link #validate(MultipartFile) validation} (empty / over-size /
 *       disallowed-extension → {@link IllegalArgumentException}),</li>
 *   <li>{@link #extensionOf(String) extension extraction},</li>
 *   <li>the {@link #guardWithinRoot(Path) path-traversal guard}, and</li>
 *   <li>{@link #sha256Hex(byte[]) SHA-256} hashing.</li>
 * </ul>
 *
 * <p>What stays per-subclass — because the two backends genuinely differ — is
 * the on-disk layout: evidence stores at {@code <base>/evidence/org-<id>/…}
 * while attachments store at {@code <base>/attachments/<entityType>/org-<id>/…}.
 * Each subclass therefore owns its own {@code store(...)} signature and builds
 * its own target path; this base supplies {@link #safeFileName(String)} and the
 * {@link #write(Path, MultipartFile)} primitive (which re-applies the
 * path-traversal guard, writes the bytes, hashes them, and returns a
 * {@link StoredFile}).
 *
 * <p>The allow-list and the size-cap source are identical across both current
 * subclasses (both wire the cap from {@code grc.evidence.max-file-size-mb}, and
 * both share the same 29-extension allow-list), so they are constants here.
 * Should a subclass ever need to diverge, override {@link #allowedExtensions()}
 * and pass a different cap to the constructor rather than editing the shared
 * default.
 */
public abstract class LocalFileStorageSupport {

    /**
     * Conservative allow-list of document/file extensions accepted by every
     * local storage backend. Shared verbatim with what the evidence and
     * attachment services historically each declared.
     */
    protected static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff",
        "txt", "csv", "log", "md", "json", "xml",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods",
        "zip", "7z", "eml", "msg");

    /** Absolute, normalised storage root for this backend (e.g. {@code <base>/evidence}). */
    protected final Path root;

    /** Per-file size cap in bytes. */
    protected final long maxBytes;

    /**
     * @param basePath           the configured storage base (e.g. {@code ./uploads})
     * @param subDir             this backend's sub-directory under the base
     *                           (e.g. {@code "evidence"} or {@code "attachments"})
     * @param maxFileSizeMb      per-file cap in MB
     * @param createErrorLabel   label used in the directory-creation error
     *                           message (e.g. {@code "evidence"} →
     *                           "Cannot create evidence storage directory")
     */
    protected LocalFileStorageSupport(String basePath, String subDir,
                                      long maxFileSizeMb, String createErrorLabel) {
        this.root = Paths.get(basePath, subDir).toAbsolutePath().normalize();
        this.maxBytes = maxFileSizeMb * 1024L * 1024L;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Cannot create " + createErrorLabel + " storage directory: " + root, e);
        }
    }

    /** Validated, stored result — shared by every backend. */
    public record StoredFile(String originalName, String relativePath, long size,
                             String mimeType, String sha256) {}

    /** Extensions this backend accepts. Subclasses may override to diverge. */
    protected Set<String> allowedExtensions() {
        return ALLOWED_EXTENSIONS;
    }

    /**
     * Validate the uploaded file and return its cleaned original filename.
     * Throws {@link IllegalArgumentException} (→ HTTP 400) for an empty file,
     * an over-size file, or a disallowed extension.
     *
     * @return the cleaned original filename (path components stripped)
     */
    protected final String validate(MultipartFile file) {
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
        if (ext.isEmpty() || !allowedExtensions().contains(ext)) {
            throw new IllegalArgumentException(
                "File type '" + (ext.isEmpty() ? "(none)" : "." + ext) + "' is not allowed. "
                    + "Allowed: " + allowedExtensions());
        }
        return original;
    }

    /** Server-generated random filename: {@code <uuid>.<lowercased-ext-of-original>}. */
    protected final String safeFileName(String originalFilename) {
        return UUID.randomUUID() + "." + extensionOf(originalFilename);
    }

    /**
     * Defence-in-depth path-traversal guard: the (already-normalised) target
     * must stay under {@link #root}. Returns the target for fluent use.
     */
    protected final Path guardWithinRoot(Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Resolved storage path escapes the storage root.");
        }
        return target;
    }

    /**
     * Write the upload's bytes to {@code target} and project the result.
     * Re-applies the path-traversal guard, creates parent directories, hashes
     * the bytes, and computes the root-relative path (forward-slash normalised).
     *
     * @param target       the fully-resolved, normalised destination path
     * @param originalName the cleaned original filename (as returned by
     *                     {@link #validate(MultipartFile)}) — stored verbatim
     * @param file         the validated upload
     * @param ioError      message for the {@link IllegalStateException} on I/O failure
     */
    protected final StoredFile write(Path target, String originalName,
                                     MultipartFile file, String ioError) {
        guardWithinRoot(target);
        try {
            Files.createDirectories(target.getParent());
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            String sha256 = sha256Hex(bytes);
            String relative = root.relativize(target).toString().replace('\\', '/');
            return new StoredFile(originalName, relative, (long) bytes.length, file.getContentType(), sha256);
        } catch (IOException e) {
            throw new IllegalStateException(ioError, e);
        }
    }

    /** Lower-cased extension of {@code filename}, or {@code ""} if none. */
    protected static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Lower-case hex SHA-256 of {@code bytes}. */
    protected static String sha256Hex(byte[] bytes) {
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
