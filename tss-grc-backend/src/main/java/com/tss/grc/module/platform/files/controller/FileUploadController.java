package com.tss.grc.module.platform.files.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.files.entity.FileMetadata;
import com.tss.grc.module.platform.files.repository.FileMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * File upload endpoints — production-grade companion to the frontend
 * `lib/fileUpload.ts` adapter.
 *
 *   POST   /api/v1/files                 multipart upload + virus scan + persist
 *   GET    /api/v1/files?attachedTo=...  list files attached to a record
 *   GET    /api/v1/files/{id}            metadata only (no bytes)
 *   DELETE /api/v1/files/{id}            soft-delete metadata + S3 marker
 *
 * The actual byte-storage adapter is pluggable via an interface; the
 * stub below logs and assigns a deterministic storage_ref. Production
 * wires S3 / MinIO + ClamAV virus scanning.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    /** Hard upper bound for a single upload (25 MiB) — defence-in-depth on top
     *  of {@code spring.servlet.multipart.max-file-size}. */
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    /** MIME allowlist — only document/image/archive types a GRC record attaches. */
    private static final Set<String> ALLOWED_MIME = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/csv",
        "application/json",
        "application/zip",
        "image/png",
        "image/jpeg",
        "image/gif",
        "application/octet-stream"
    );

    private final FileMetadataRepository repo;

    public FileUploadController(FileMetadataRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public ResponseEntity<Map<String, Object>> list(@RequestParam String attachedTo) {
        Long orgId = TenantContext.requireOrganisationId();
        List<FileMetadata> rows = repo.findByOrganisationIdAndAttachedToOrderByUploadedAtDesc(orgId, attachedTo);
        return ResponseEntity.ok(Map.of(
            "attachedTo", attachedTo,
            "count", rows.size(),
            "files", rows
        ));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    @Transactional
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("attachedTo") String attachedTo,
            @RequestParam(value = "crNumber", required = false) String crNumber,
            @RequestParam(value = "residency", defaultValue = "KSA") String residency,
            @RequestParam(value = "retentionDays", defaultValue = "1095") Integer retentionDays) throws Exception {

        Long orgId = TenantContext.requireOrganisationId();
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }

        // 0. Enforce a hard upper bound on file size + a MIME allowlist before
        //    we read any bytes into memory. (multipart.max-file-size also caps
        //    this at the servlet layer; this is the in-code defence-in-depth.)
        if (file.getSize() > MAX_FILE_BYTES) {
            return ResponseEntity.status(413).body(Map.of("error", "File too large"));
        }
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED_MIME.contains(mime)) {
            return ResponseEntity.status(415).body(Map.of("error", "Unsupported media type", "mime", mime));
        }
        String safeName = sanitiseFilename(file.getOriginalFilename());

        // 1. Hash for dedupe + tamper detection.
        byte[] bytes = file.getBytes();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        String sha = bytesToHex(digest);

        // 2. Dedupe — if we already have this hash, reuse the storage_ref.
        Optional<FileMetadata> existing = repo.findByOrganisationIdAndSha256(orgId, sha);

        // 3. Virus scan (stub — production wires ClamAV / Lambda).
        ScanResult scan = scanStub(file, bytes);
        if ("INFECTED".equals(scan.status)) {
            log.warn("File rejected by virus scan org={} sha256={} reason={}", orgId, sha, scan.reason);
            return ResponseEntity.status(422).body(Map.of(
                "error", "infected",
                "reason", scan.reason
            ));
        }

        // 4. Persist (storage adapter would write the bytes here; the stub
        //    assigns a placeholder S3 key). Use the sanitised filename so a
        //    crafted originalFilename cannot traverse / inject into the key.
        String storageRef = existing.map(FileMetadata::getStorageRef)
            .orElseGet(() -> "s3://files/" + orgId + "/" + sha.substring(0, 8) + "/" + safeName);

        FileMetadata meta = new FileMetadata();
        meta.setOrganisationId(orgId);
        meta.setAttachedTo(attachedTo);
        meta.setFilename(safeName);
        meta.setMime(mime);
        meta.setSizeBytes(file.getSize());
        meta.setSha256(sha);
        meta.setStorageRef(storageRef);
        meta.setScanStatus(scan.status);
        meta.setScanReason(scan.reason);
        meta.setUploadedAt(OffsetDateTime.now().toString());
        meta.setUploadedBy(currentUsername());
        meta.setCrNumber(crNumber);
        meta.setResidency(residency);
        meta.setRetentionDays(retentionDays);
        FileMetadata saved = repo.save(meta);

        log.info("File uploaded org={} attachedTo={} filename={} sha256={} dedup={}",
            orgId, attachedTo, safeName, sha, existing.isPresent());

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", saved.getId());
        resp.put("filename", saved.getFilename());
        resp.put("sha256", saved.getSha256());
        resp.put("scanStatus", saved.getScanStatus());
        resp.put("storageRef", saved.getStorageRef());
        resp.put("dedup", existing.isPresent());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public ResponseEntity<FileMetadata> get(@PathVariable Long id) {
        return repo.findById(id)
            .filter(f -> f.getOrganisationId().equals(TenantContext.requireOrganisationId()))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findById(id)
            .filter(f -> f.getOrganisationId().equals(orgId))
            .map(f -> {
                f.setIsDeleted(true);
                f.setDeletedAt(LocalDateTime.now());
                repo.save(f);
                log.info("File deleted org={} id={}", orgId, id);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    /** Resolve username from Spring SecurityContext; "anonymous" if unauthenticated. */
    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    /**
     * Strip path components and dangerous characters from a client-supplied
     * filename before it is stored or used to build the storage key. Prevents
     * path traversal ("../"), embedded separators, and control characters.
     */
    private static String sanitiseFilename(String original) {
        if (original == null || original.isBlank()) return "upload.bin";
        // Drop any directory portion a client may have sent (both separators).
        String base = original.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        // Keep only a safe charset; collapse everything else to '_'.
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        // Strip leading dots so we cannot produce ".." or hidden names.
        base = base.replaceAll("^\\.+", "");
        if (base.isBlank()) base = "upload.bin";
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    // ─── Virus-scan stub ───────────────────────────────────────────────

    private record ScanResult(String status, String reason) {}

    private ScanResult scanStub(MultipartFile file, byte[] bytes) {
        // Look for the EICAR test signature in the first 4KB.
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        int probeLen = Math.min(bytes.length, 4096);
        String probe = new String(bytes, 0, probeLen);
        if (probe.contains(eicar)) {
            return new ScanResult("INFECTED", "EICAR test signature detected");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.matches(".*\\.(exe|scr|bat|cmd|com|pif|js|vbs|ps1|jar)$")) {
            return new ScanResult("SUSPICIOUS", "Executable extension — review before opening");
        }
        return new ScanResult("CLEAN", null);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
