package com.tss.grc.module.sharia.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;

/**
 * Sharia Supervisory Board (SSB) member.
 *
 * <p>Per SAMA's Implementing Regulation on Sharia Governance §3, every
 * Saudi licensed bank operating Islamic-banking activities must
 * maintain an SSB of at least 3 scholars with documented fiqh
 * al-mu'amalat credentials. The Chairman is typically a senior
 * scholar drawn from Hai'at Kibar al-Ulama (Council of Senior
 * Scholars) or the Saudi Islamic Fiqh Academy.
 */
@Entity
@Table(schema = "sharia", name = "sharia_scholar")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ShariaScholar extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, e.g. {@code SCH-AL-MUTLAQ-001}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "full_name_en", nullable = false, length = 256)
    private String fullNameEn;

    @Column(name = "full_name_ar", nullable = false, length = 256)
    private String fullNameAr;

    /** Role on the SSB: {@code CHAIRMAN | MEMBER | SECRETARY | EXTERNAL_ADVISOR}. */
    @Column(name = "ssb_role", nullable = false, length = 32)
    private String ssbRole = "MEMBER";

    /** Status: {@code ACTIVE | TERM_EXPIRING | TERM_EXPIRED | RESIGNED | REMOVED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** Term start date — typically 3-year renewable per SAMA §3.4. */
    @Column(name = "term_start")
    private LocalDate termStart;

    @Column(name = "term_end")
    private LocalDate termEnd;

    @Column(name = "credentials_en", columnDefinition = "NVARCHAR(MAX)")
    private String credentialsEn;

    @Column(name = "credentials_ar", columnDefinition = "NVARCHAR(MAX)")
    private String credentialsAr;

    /** Date of latest annual conflict-of-interest declaration. */
    @Column(name = "coi_declared_at")
    private LocalDate coiDeclaredAt;

    @Column(name = "coi_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String coiNotesEn;

    @Column(name = "coi_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String coiNotesAr;

    /** Annual fee (SAR). */
    @Column(name = "annual_fee_sar", precision = 18, scale = 2,
            columnDefinition = "DECIMAL(18, 2)")
    private java.math.BigDecimal annualFeeSar;

    /** Meetings attended in the current fiscal year (used by AR-260 cadence rule). */
    @Column(name = "meetings_attended_ytd")
    private Integer meetingsAttendedYtd = 0;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;
}
