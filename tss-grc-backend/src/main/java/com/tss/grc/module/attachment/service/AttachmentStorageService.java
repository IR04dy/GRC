package com.tss.grc.module.attachment.service;

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
 * Local-filesystem storage for generic entity attachments. Mirrors the proven
 * evidence storage (allow-list, size cap, server-generated UUID name,
 * path-traversal guard, SHA-256) and adds a safe {@link #load} for download.
 * Files live under {@code <base>/attachments/<entityType>/org-<id>/}.
 */
@Service
public class AttachmentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff",
        "txt", "csv", "log", "md", "json", "xml",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "ods",
        "zip", "7z", "eml", "msg");

    private final Path root;
    private final long maxBytes;

    public AttachmentStorageService(
            @Value("${STORAGE_BASE_PATH:./uploads}") String basePath,
            @Value("${grc.evidence.max-file-size-mb:50}") long maxFileSizeMb) {
        this.root = Paths.get(basePath, "attachments").toAbsolutePath().normalize();
        this.maxBytes = maxFileSizeMb * 1024L * 1024L;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create attachment storage directory: " + root, e);
        }
    }

    public record StoredFile(String originalName, String relativePath, long size,
                             String mimeType, String sha256) {}

    public StoredFile store(Long orgId, String entityType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided, or the file is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                "File exceeds the maximum allowed size of " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        String ext = extensionOf(original);
        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                "File type '" + (ext.isEmpty() ? "(none)" : "." + ext) + "' is not allowed. Allowed: " + ALLOWED_EXTENSIONS);
        }
        String typeDir = entityType == null ? "misc" : entityType.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        String safeName = UUID.randomUUID() + "." + ext;
        Path dir = root.resolve(typeDir).resolve("org-" + orgId).normalize();
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Resolved storage path escapes the storage root.");
        }
        try {
            Files.createDirectories(dir);
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            String relative = root.relativize(target).toString().replace('\\', '/');
            return new StoredFile(original, relative, (long) bytes.length, file.getContentType(), sha256Hex(bytes));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store attachment file.", e);
        }
    }

    /** Read an attachment's bytes by its stored relative path (path-traversal safe). */
    public byte[] load(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Requested path escapes the storage root.");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read attachment file.", e);
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
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
