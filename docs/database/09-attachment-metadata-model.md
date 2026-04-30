# Attachment Metadata Model

## Purpose
Defines how file attachments are stored and managed in the TSS GRC Platform. The database stores metadata only; file content is stored on the filesystem or object storage.

---

## Core Design Principle

**The database stores file metadata. The file content lives on storage.**

This separation allows:
- Storage backend to be swapped (local → NAS → Azure Blob) without schema changes
- Database stays lean and fast
- Files can be streamed directly without loading into memory as BLOBs

---

## platform.attachments Table

```sql
CREATE TABLE platform.attachments (
    id                   BIGINT IDENTITY(1,1)   NOT NULL,
    external_id          UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    -- Polymorphic entity reference
    entity_type          NVARCHAR(200)           NOT NULL,  -- e.g., 'risk.risks'
    entity_id            BIGINT                  NOT NULL,
    -- File metadata
    file_name            NVARCHAR(500)           NOT NULL,  -- UUID-based storage filename
    original_file_name   NVARCHAR(500)           NOT NULL,  -- User-provided filename
    file_extension       NVARCHAR(20)            NULL,
    mime_type            NVARCHAR(200)           NULL,
    file_size_bytes      BIGINT                  NULL,
    -- Storage
    storage_type         NVARCHAR(50)            NOT NULL DEFAULT 'LOCAL',
    storage_path         NVARCHAR(1000)          NOT NULL,  -- Relative path on storage
    checksum_sha256      NVARCHAR(128)           NULL,      -- For integrity verification
    -- Description
    description          NVARCHAR(500)           NULL,
    -- Upload tracking
    uploaded_by          BIGINT                  NOT NULL,
    uploaded_at          DATETIME2               NOT NULL DEFAULT SYSUTCDATETIME(),
    -- Virus scanning
    virus_scan_status    NVARCHAR(50)            NOT NULL DEFAULT 'PENDING',
    virus_scanned_at     DATETIME2               NULL,
    virus_scan_result    NVARCHAR(200)           NULL,
    -- Soft delete
    is_deleted           BIT                     NOT NULL DEFAULT 0,
    deleted_at           DATETIME2               NULL,
    deleted_by           BIGINT                  NULL,
    CONSTRAINT PK_attachments PRIMARY KEY CLUSTERED (id ASC)
);
```

---

## Polymorphic Reference

Any entity can have attachments via the `entity_type` + `entity_id` pattern:

| entity_type | entity_id | Description |
|------------|-----------|-------------|
| `risk.risks` | 42 | Attachment on a risk record |
| `compliance.evidence_items` | 15 | Evidence document |
| `workflow.workflow_instance_steps` | 7 | Supporting document for approval |
| `bcm.bcm_plans` | 3 | BCP document attachment |
| `privacy.breach_records` | 9 | Breach notification document |

---

## Storage Backends

| Type | storage_type Value | Phase |
|------|-------------------|-------|
| Local filesystem | `LOCAL` | Phase 1 |
| Network Attached Storage | `NAS` | Phase 2 |
| Azure Blob Storage | `AZURE_BLOB` | Future |
| AWS S3 | `S3` | Future |

### Spring StorageService Interface

```java
public interface FileStorageService {
    AttachmentMetadata store(MultipartFile file, String entityType, Long entityId);
    Resource load(Long attachmentId);
    void delete(Long attachmentId);
    String getPublicUrl(Long attachmentId);
}
```

Local implementation stores files at:
```
{storage.base-path}/{entity_type}/{entity_id}/{uuid}{.ext}
```

---

## Security Controls

| Control | Implementation |
|---------|--------------|
| File type validation | Whitelist of allowed MIME types/extensions (PDF, DOCX, XLSX, PNG, JPG, ZIP) |
| File size limit | Configurable max (default 50 MB per file) |
| Stored filename | UUID-based (prevents path traversal, filename collisions) |
| Download authorization | Spring Security checks user permission before streaming file |
| Web root isolation | Storage directory NOT under web root (no direct URL access) |
| Virus scanning | Integration point: ClamAV daemon or commercial AV; status tracked |

---

## File Download Flow

```
User clicks Download → AttachmentController.download(id)
    → Check user has READ permission on entity_type
    → Load attachment metadata from DB
    → Verify is_deleted = 0
    → Verify virus_scan_status = 'CLEAN' (or bypass if PENDING and old upload)
    → Load file from FileStorageService
    → Stream to browser with Content-Disposition: attachment; filename="{original_file_name}"
```

---

## Orphan Cleanup

Spring `@Scheduled` job runs nightly:
- Find attachments where `is_deleted = 1` and `deleted_at < NOW() - 30 days`
- Delete physical file from storage
- Hard delete metadata record
- Log to audit
