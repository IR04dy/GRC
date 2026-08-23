package com.tss.grc.module.ethics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Conflict of interest declarations + gifts/hospitality register + outside
 * business activities + related-party + political/charitable donations.
 *
 * Workflow: DRAFT → SUBMITTED → REVIEW → APPROVED|REJECTED|RECUSED|MITIGATED.
 */
@Entity
@Table(schema = "ethics", name = "disclosures")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Disclosure {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // F2-286 — the column is insertable = false, so the DATABASE refuses a caller's value
    // and the create hands back the instance Jackson bound: without this the response
    // echoed a value the row does not carry, and only a re-fetch showed the truth. This
    // says the mapping refuses it, not that the caller should never state it — where the
    // caller should, the fix is to make the column insertable (F2-286 did that for
    // ethics.screening_hits.screened_at, whose value nothing could ever state).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "disclosure_code", length = 50)
    private String disclosureCode;

    @Column(name = "disclosure_type", nullable = false, length = 40)
    private String disclosureType;
    // CONFLICT_OF_INTEREST | GIFT_RECEIVED | GIFT_GIVEN | HOSPITALITY
    //   | OUTSIDE_BUSINESS | RELATED_PARTY | POLITICAL_DONATION | CHARITABLE

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "discloser_name", nullable = false, length = 255)
    private String discloserName;

    @Column(name = "discloser_email", length = 255)
    private String discloserEmail;

    @Column(name = "discloser_employee_id")
    private Long discloserEmployeeId;

    @Column(name = "discloser_department", length = 255)
    private String discloserDepartment;

    @Column(name = "discloser_role", length = 255)
    private String discloserRole;

    @Column(name = "counterparty_name", length = 500)
    private String counterpartyName;

    @Column(name = "counterparty_relationship", length = 100)
    private String counterpartyRelationship;

    @Column(name = "monetary_value", precision = 18, scale = 2)
    private BigDecimal monetaryValue;

    // ⚠⚠ F2-292 removed `= "SAR"`. LATENT, and closed anyway: all 18 live rows in both databases
    // hold SAR, so what made it harmless was the DATA and not the code (F2-182). `DisclosureService`
    // owns the default on the create path.
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    // ⚠⚠ F2-292 removed `= "DRAFT"`. `DisclosureController` binds this entity on PUT, so Jackson
    // deserialised an omitted `status` into this initialiser, `apply`'s
    // `if (src.getStatus() != null)` was true on EVERY request, and a partial update reverted the
    // disclosure to DRAFT. Probed live: a body carrying only `titleEn` returned 200 and stored
    // DRAFT over APPROVED. 18 of 18 live rows in dev and prod hold a non-DRAFT status — APPROVED 11,
    // REVIEW 4, SUBMITTED 2, REJECTED 1 — so every one of them was destroyable this way.
    //
    // ⚠ API-REACHABLE, NOT SCREEN-REACHABLE, and saying which is the point (F2-283). `EthicsPage`'s
    // disclosure form declares no status field, but `reqFromDto` spreads the whole DTO into the edit
    // modal, so the screen's PUT ECHOED the stored status and those 18 rows survived. The reachable
    // caller is anything that sends a genuinely partial body — which is what the client's own
    // `Partial<DisclosureDto>` type invites, and where machine callers live (F2-179). That echo is
    // also why the fix must TOLERATE one rather than refuse the field outright (F2-214).
    //
    // 🔑 APPROVED, RECUSED and MITIGATED are TERMINAL in `DisclosureService.TRANSITIONS`, so the
    // reset escaped a terminal state by a route the machine cannot see — and `DRAFT → SUBMITTED`
    // exists, so the whole NAZAHA / SAMA review cycle became re-runnable on a decided disclosure.
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "decision", length = 40)
    private String decision;

    @Column(name = "decision_notes", columnDefinition = "NVARCHAR(MAX)")
    private String decisionNotes;

    @Column(name = "decided_by_name", length = 255)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String decidedByName;

    @Column(name = "decided_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime decidedAt;

    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
