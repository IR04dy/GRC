package com.tss.grc.module.platform.persistence.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Where;

/**
 * Generic key/value row used by the production-persistence adapter.
 *
 * One physical table backs every "store" the frontend wants to persist
 * (chain fires, dispatch log, designer overlays, …). Each row keeps a
 * `storeKey` (e.g. "grc.chains.fires.v1") + an opaque JSON `payload`.
 *
 * Why generic, not per-store:
 *   • Frontend stores have evolving shapes (Sprint 4 → 6); locking each
 *     into its own table would force a Flyway migration on every shape
 *     change.
 *   • Audit / GDPR-style queries only ever look up by storeKey + tenant.
 *   • Keeps the migration path simple — frontend uploads its localStorage
 *     state directly without server-side schema mapping.
 *
 * Indexed by (organisation_id, store_key) for tenant-scoped lookup.
 */
@Entity
@Table(name = "persistence_row",
       indexes = {
           @Index(name = "idx_persistence_org_key", columnList = "organisation_id, store_key"),
           @Index(name = "idx_persistence_created", columnList = "created_at"),
       })
@Where(clause = "is_deleted = 0")
public class PersistenceRow extends BaseEntity {

    /** Tenant scope — populated from `TenantContext` by the controller. */
    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Logical store identifier (matches frontend localStorage key). */
    @Column(name = "store_key", nullable = false, length = 200)
    private String storeKey;

    /** Per-store row sequence — preserves insertion order for append-only logs. */
    @Column(name = "row_seq", nullable = false)
    private Long rowSeq;

    /** Opaque JSON payload — the row body as the frontend serialised it. */
    @Column(name = "payload", nullable = false, columnDefinition = "nvarchar(max)")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    /** Source metadata — useful for support / migration audits. */
    @Column(name = "source_origin", length = 32)
    private String sourceOrigin; // "MIGRATION_UPLOAD" / "API_APPEND" / "API_PUT"

    public PersistenceRow() {}

    public PersistenceRow(Long organisationId, String storeKey, Long rowSeq, String payload, String sourceOrigin) {
        this.organisationId = organisationId;
        this.storeKey = storeKey;
        this.rowSeq = rowSeq;
        this.payload = payload;
        this.sourceOrigin = sourceOrigin;
    }

    public Long getOrganisationId() { return organisationId; }
    public void setOrganisationId(Long organisationId) { this.organisationId = organisationId; }
    public String getStoreKey() { return storeKey; }
    public void setStoreKey(String storeKey) { this.storeKey = storeKey; }
    public Long getRowSeq() { return rowSeq; }
    public void setRowSeq(Long rowSeq) { this.rowSeq = rowSeq; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getSourceOrigin() { return sourceOrigin; }
    public void setSourceOrigin(String sourceOrigin) { this.sourceOrigin = sourceOrigin; }
}
