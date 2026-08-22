package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "privacy", name = "data_breaches")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataBreach {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    /**
     * F2-271 (V387) — Arabic title. House rule 7: a user-visible record carries {@code *_en}
     * AND {@code *_ar}, and Arabic is the PRIMARY language, so a PDPL Article 20 breach that
     * could only be titled in English was the worse half of that rule — the column did not
     * exist, so the Arabic could not be recorded at all.
     *
     * <p>⚠ Deliberately NOT {@code @NotBlank}, and the reason is F2-30's: bean validation
     * fires on UPDATE as well as insert, so requiring Arabic would make every breach that
     * predates V387 uneditable until somebody supplies it — and nothing can invent Arabic
     * governance text. Its existence is what makes the pair VISIBLE to
     * {@code BilingualInvariantRatchetTest}, which is the mechanism by which the constraint
     * can be added later once the rows carry Arabic.
     */
    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    /** F2-271 (V387) — Arabic description; see {@link #titleAr} for why it is not mandatory. */
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "breach_type", nullable = false, length = 100)
    private String breachType;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(name = "data_subjects_affected_count")
    private Integer dataSubjectsAffectedCount;

    @Column(name = "pdpc_notification_required", nullable = false)
    private Boolean pdpcNotificationRequired = false;

    @Column(name = "pdpc_notified_at")
    private LocalDateTime pdpcNotifiedAt;

    /**
     * SDAIA notification workflow status (Open Issue #006).
     *
     * <p>PDPL Article 31 requires notification of personal-data breaches to
     * SDAIA (formerly PDPC) within 72 hours of awareness. SDAIA has no
     * machine-readable API — the workflow is manual through the SDAIA portal
     * at {@code https://www.sdaia.gov.sa/}. This field tracks the state of
     * that manual submission.
     *
     * <p>Values: {@code NOT_REPORTED | IN_PREPARATION | SUBMITTED | ACKNOWLEDGED}.
     */
    @Column(name = "sdaia_notification_status", nullable = false, length = 40)
    private String sdaiaNotificationStatus = "NOT_REPORTED";

    /** Reference number returned by the SDAIA portal acknowledgement. */
    @Column(name = "sdaia_submission_ref", length = 255)
    private String sdaiaSubmissionRef;

    /**
     * Timestamp the submission was posted to SDAIA. Discharges the PDPL
     * Article 31 72-hour clock measured from {@link #discoveredAt}.
     */
    @Column(name = "sdaia_submitted_at")
    private LocalDateTime sdaiaSubmittedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "OPEN";

    /*
     * ── cyber → PDPL provenance (F2-285) ────────────────────────────────────
     * The BREACH end of the bridge F2-281 mapped the incident end of. Written by
     * one writer, {@code privacy.bridge.CyberPdplBridgeService}, in native SQL,
     * and — measured 2026-08-22 — read by NOTHING: zero references in the whole
     * backend or frontend outside that service.
     *
     * <p>The bridge's own audit line has always said "created and
     * <b>bidirectionally</b> linked to incident INC-00001". It writes both
     * directions; only one was readable. After F2-281 an incident carries
     * {@code linkedDataBreachCode} on its wire, so the asymmetry became visible:
     * the incident can name its breach and the breach could not name its
     * incident. Nor could the bridge log answer it — all three of its finders key
     * on {@code cyberIncidentId} and none on {@code dataBreachId}, so a report
     * keyed by incident cannot answer a question asked from a breach (F2-178).
     *
     * <p>⚠⚠ Mapped {@code insertable = false, updatable = false} because this
     * register's endpoints BIND THE ENTITY — {@code createBreach(@RequestBody
     * DataBreach req)} — so a writable mapping would let any caller forge
     * "this breach was auto-generated from cyber incident N", a provenance claim
     * on a PDPL Article 20 statutory record. {@code ServerOwnedFieldsModule}
     * covers {@code BaseEntity}'s nine and cannot help here.
     *
     * <p>🔑 {@link com.tss.grc.module.privacy.service.PrivacyService#createBreach}
     * already hand-scrubs four fields for exactly this reason, and its javadoc
     * quotes F2-215's headline — "fixing the field you noticed leaves the
     * remainder looking covered, because the mechanism that covers them is
     * visibly present." These five ARE that remainder. They are invisible only
     * because they are unmapped, so mapping them the naive way would have created
     * the defect that comment warns about.
     *
     * <p>{@code auto_generated} is {@code smallint NOT NULL DEFAULT 0}, so leaving
     * it out of Hibernate's INSERT is what lets the database default apply — and
     * that default is correct: a breach somebody records by hand is not
     * auto-generated (F2-280's kind 1, where the default decides and here decides
     * rightly).
     *
     * <p>Each carries {@code @Setter(AccessLevel.NONE)} on F2-281's precedent: a
     * Lombok setter over a non-updatable column compiles, runs and silently does
     * nothing, which is F2-184's decoy. Suppressing it makes the write a COMPILE
     * error.
     */

    /** 1 when the cyber → PDPL bridge created this breach rather than a person. */
    @Column(name = "auto_generated", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Short autoGenerated;

    /** The bridge rule that created it, e.g. {@code CYBER_TO_PDPL_V1}. */
    @Column(name = "auto_generation_rule", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String autoGenerationRule;

    @Column(name = "auto_generated_at", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime autoGeneratedAt;

    /** {@code cyber.incidents.id} this breach was raised from, if any. */
    @Column(name = "source_cyber_incident_id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Long sourceCyberIncidentId;

    /** That incident's display reference, e.g. {@code INC-00001}. */
    @Column(name = "source_cyber_incident_number", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String sourceCyberIncidentNumber;

    /**
     * The one predicate for "did the bridge raise this breach?".
     *
     * <p>The column is {@code smallint NOT NULL DEFAULT 0}, so the test is
     * {@code != 0} rather than a truthiness check, and a null (an unsaved
     * instance) reads as NOT auto-generated — never as auto-generated. Written
     * once here because three surfaces ask it, and F2-282 measured what a second
     * copy of a governance predicate costs.
     */
    public boolean isBridgeRaised() {
        return autoGenerated != null && autoGenerated != 0;
    }

    /**
     * A breach Hibernate creates carries no bridge provenance, and this enforces it
     * on the instance rather than only in the column.
     *
     * <p>⚠⚠ The read-only mapping stops the values being STORED and does not stop
     * them being ECHOED. {@code PrivacyService#createBreach} returns
     * {@code breachRepo.save(req)} — the caller's own deserialised instance — so a
     * POST claiming {@code autoGenerated: 1, sourceCyberIncidentNumber: "INC-FORGED"}
     * came back 201 with exactly that, over a row whose columns were
     * {@code (0, null, null, null, null)}. Probed live 2026-08-22. A client that
     * trusts the response body would render the breach as bridge-raised; only a
     * re-fetch shows the truth. 🔑 <b>Unwritable to the database is not absent from
     * the response</b> — and this slice's own gate leg asserted the opposite,
     * reasoning that a scrub would be decoration because the columns are unwritable.
     * The probe refuted it.
     *
     * <p>Here rather than in the service because the five have {@code
     * @Setter(AccessLevel.NONE)}, so no service CAN clear them; because this fires on
     * the same instance the service returns, fixing the echo; and because it makes
     * the invariant the entity's own, so a future create path cannot forget it —
     * F2-215's remainder problem, pre-empted rather than repeated.
     *
     * <p>It cannot affect the bridge: {@code CyberPdplBridgeService} writes both
     * ends in native SQL and never persists an entity, so the only rows this touches
     * are Hibernate-created ones — exactly the ones that must carry no provenance.
     */
    @PrePersist
    void clearBridgeProvenanceOnCreate() {
        autoGenerated = null;
        autoGenerationRule = null;
        autoGeneratedAt = null;
        sourceCyberIncidentId = null;
        sourceCyberIncidentNumber = null;
    }

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_by")
    private Long deletedBy;
}
