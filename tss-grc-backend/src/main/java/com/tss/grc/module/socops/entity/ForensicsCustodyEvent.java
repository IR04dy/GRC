package com.tss.grc.module.socops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SOC-D — one immutable row in a forensic artefact's chain of custody.
 *
 * <p>Append-only: a row is written on capture and on every custody transfer and
 * is <b>never</b> updated or deleted (a DB trigger, {@code trg_fce_append_only},
 * blocks UPDATE/DELETE). The ordered {@link #seqNo} sequence per artefact lets the
 * full chain be replayed verbatim — the artefact's {@code current_custodian} is
 * just a denormalised pointer to the latest row here.
 *
 * <p>Deliberately does NOT extend {@code BaseEntity}: an evidentiary ledger has no
 * soft-delete / updated-at semantics — it only ever grows.
 */
@Entity
@Table(schema = "socops", name = "forensics_custody_event")
@Getter @Setter @NoArgsConstructor
public class ForensicsCustodyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "artefact_id", nullable = false)      private Long artefactId;
    /** 1-based position in this artefact's chain (CAPTURE = 1, then each transfer). */
    @Column(name = "seq_no", nullable = false)           private Integer seqNo;
    /** CAPTURE | TRANSFER | VERIFY | DISPOSE | RELEASE. */
    @Column(name = "event_type", nullable = false, length = 30) private String eventType = "TRANSFER";
    @Column(name = "from_custodian", length = 255)       private String fromCustodian;
    @Column(name = "to_custodian", nullable = false, length = 255) private String toCustodian;
    @Column(name = "from_location", length = 300)        private String fromLocation;
    @Column(name = "to_location", length = 300)          private String toLocation;
    /** The artefact's sha256 as recorded at this handoff (integrity at transfer time). */
    @Column(name = "artefact_hash", length = 128)        private String artefactHash;
    @Column(name = "transferred_at", nullable = false)   private LocalDateTime transferredAt;
    /** The authenticated handler who performed the action. */
    @Column(length = 255)                                private String actor;
    @Column(columnDefinition = "NVARCHAR(MAX)")          private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (transferredAt == null) transferredAt = createdAt;
    }
}
