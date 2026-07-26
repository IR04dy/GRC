package com.tss.grc.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Common identity, audit and soft-delete columns for nearly every entity in the platform.
 *
 * <h2>F2-22a — why every field here is {@code READ_ONLY} to Jackson</h2>
 *
 * <p><b>214 write endpoints bind a JPA entity straight from the request body</b>
 * ({@code create(@RequestBody Risk body)} and friends — measured by reflection, pinned by
 * {@code MassAssignmentRatchetTest}). On every one of them the caller could set any field the
 * entity exposes, and because almost every entity extends this class, that included:
 *
 * <ul>
 *   <li>{@code id} — a "create" retargeted at an existing row</li>
 *   <li>{@code createdBy} / {@code updatedBy} — <b>forged audit attribution</b>. This is
 *       exactly DF-3 (committee vote provenance) and DF-4 (CAB approval provenance), both of
 *       which were fixed one endpoint at a time, except here it applied to every entity in the
 *       platform at once</li>
 *   <li>{@code createdAt} / {@code updatedAt} — backdated records</li>
 *   <li>{@code isDeleted} / {@code deletedAt} / {@code deletedBy} — an ordinary update could
 *       soft-delete or resurrect a record, bypassing whatever guard the DELETE endpoint
 *       carries, and forge who did it. The soft-delete flag is the platform's deletion
 *       mechanism, so this is a data-destruction vector, not a cosmetic one</li>
 *   <li>{@code rowVersion} — optimistic locking defeated by supplying a stale version</li>
 * </ul>
 *
 * <p><b>{@code READ_ONLY}, not {@code @JsonIgnore}.</b> The remediation plan specified
 * {@code @JsonIgnore}; that would have been a worse defect than the one it fixed.
 * {@code @JsonIgnore} suppresses a property in both directions, so {@code id} would have
 * disappeared from every API <em>response</em> — and the entire frontend resolves records by
 * id (every list row, every detail route, every row action). {@code READ_ONLY} keeps
 * serialisation exactly as it was and refuses deserialisation, which is the actual
 * requirement. {@code MassAssignmentRatchetTest} asserts both halves so the distinction
 * cannot be "simplified" away later.
 *
 * <p>Server-side assignment is unaffected: Spring Data's {@link AuditingEntityListener} sets
 * {@code createdBy}/{@code updatedBy}/timestamps, {@link #softDelete(Long)} sets the delete
 * trio, and Hibernate owns {@code id} and {@code rowVersion}. Only the JSON wire is closed.
 *
 * <p>This closes the platform-wide field exposure in one place. It does <em>not</em> make the
 * 214 endpoints safe on its own — a per-entity field such as {@code organisationId},
 * {@code status} or {@code approvedBy} still binds from the body, which is why the ratchet
 * keeps counting them and F2-22b converts them to DTOs.
 */
@Getter @Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private byte[] rowVersion;

    public void softDelete(Long deletedByUserId) {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }
}
