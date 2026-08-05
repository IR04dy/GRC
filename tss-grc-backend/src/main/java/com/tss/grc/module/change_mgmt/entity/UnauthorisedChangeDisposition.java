package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.List;

/**
 * S8f / CE-11 — an investigator's recorded outcome for one reconciliation finding (V355).
 *
 * <p><b>Why this is the only stored half of CE-11.</b> The findings themselves are derived on every
 * read from the matrix, the approval trail and the status, so they cannot drift from the rows they
 * describe (see {@code UnauthorisedChangeFinding}). A disposition is the opposite kind of thing: a
 * person looked at a finding and reached a judgement. Nothing can recompute that, so it is the one
 * part that needs a table.
 *
 * <h3>⚠ A disposition annotates a finding — it never erases one</h3>
 * There is no {@code CLOSED} state and no soft-delete of a finding, because "somebody signed this off"
 * and "this never happened" are different facts and a register that merges them is worth nothing to an
 * auditor. Concretely: the unauthorised-change KPI counts <b>every</b> authority gap, disposed or not.
 * Were it to count only open ones, an operator could reach a clean dashboard by dispositioning rather
 * than by fixing anything — the same failure mode F2-107 had when a percentage was divided by the rows
 * that happened to be convenient.
 *
 * <h3>The CHECK is what makes the state meaningful</h3>
 * {@code ck_ucd_disposed} refuses any row whose state is set without a person and a moment behind it,
 * the same shape as S8e's {@code ck_cmodel_preapproval}: a governance word that anybody can type
 * without saying who typed it is a label, not a control. And as with S8e, the constraint is only
 * sufficient because the request DTO is narrow — {@code disposedBy*} and {@code disposedAt} are set
 * from the authenticated principal and the server clock, never accepted from the caller, so the
 * constraint tests that the fields agree rather than who supplied them.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_unauthorised_disposition")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class UnauthorisedChangeDisposition {

    /** The finding was investigated and the residual risk is knowingly carried. */
    public static final String ACCEPTED_RISK = "ACCEPTED_RISK";
    /** The authority was obtained after the fact and is now on record. */
    public static final String RETROSPECTIVELY_AUTHORISED = "RETROSPECTIVELY_AUTHORISED";
    /** The change was reversed rather than authorised. */
    public static final String REVERSED = "REVERSED";
    /**
     * The reconciliation was wrong about this change.
     *
     * <p>Kept deliberately, and it is not an escape hatch: a finding an investigator can mark
     * incorrect is a finding whose false-positive rate can be measured. A detector with no way to be
     * told it was wrong gets ignored wholesale instead.
     */
    public static final String FALSE_POSITIVE = "FALSE_POSITIVE";

    /** Closed set, mirrored by {@code ck_ucd_state} in V355 and pinned by a test. */
    public static final List<String> STATES = List.of(
        ACCEPTED_RISK, RETROSPECTIVELY_AUTHORISED, REVERSED, FALSE_POSITIVE);

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "change_request_id", nullable = false)
    private Long changeRequestId;

    /**
     * Which reconciliation failure this dispositions.
     *
     * <p>One change can fail for two independent reasons and each is disposed on its own merits, so
     * the natural key is (change, reason) — {@code ux_ucd_change_reason} in V355.
     */
    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    @Column(name = "state", nullable = false, length = 40)
    private String state;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "disposed_by_id")
    private Long disposedById;

    /** Denormalised so the register reads without joining {@code iam.users} per row. */
    @Column(name = "disposed_by_name", nullable = false, length = 255)
    private String disposedByName;

    @Column(name = "disposed_at", nullable = false)
    private LocalDateTime disposedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
