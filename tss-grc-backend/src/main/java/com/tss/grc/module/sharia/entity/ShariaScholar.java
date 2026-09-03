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

    /**
     * Meetings attended in the current fiscal year.
     *
     * <p><b>F2-459 -- nothing writes this, and nothing resets it.</b> The javadoc here used to
     * read "used by AR-260 cadence rule", which is false in the way that is hardest to check:
     * AR-260 exists, and it triggers on {@code SHARIA_SCHOLAR_TERM_EXPIRING} and reads
     * {@link #termEnd}. No automation rule's {@code condition_json} or {@code action_json}
     * mentions this column (measured 0 of 214 live rules), no service, scheduler, native
     * {@code @Modifying} query, trigger or computed column writes it, and neither
     * {@code CreateScholarRequest} nor {@code UpdateScholarRequest} declares it -- so no caller
     * can set it either. Its value is whatever the seed wrote (1..5 across 5 scholars,
     * byte-identical in dev and the deployed database) and it is frozen there for ever.
     *
     * <p>It is therefore <b>NOT on the wire</b>: {@code ScholarResponse} no longer carries it and
     * the scholars register renders an em-dash with a reason, because a frozen figure named for a
     * rolling window is worse than an absent one -- it is plausible, and "0" would assert that a
     * scholar attended nothing (F2-197).
     *
     * <p>The mapping is kept rather than removed so the column stays visible to a reader and has
     * a home if SSB attendance is ever recorded. There is no attendance register in
     * {@code sharia} today; the platform's only attendance data is
     * {@code admin.committee_meeting_attendee}, which would need the SSB modelled as a committee
     * and scholars linked to committee members -- a feature, filed rather than smuggled in.
     */
    @Column(name = "meetings_attended_ytd")
    private Integer meetingsAttendedYtd = 0;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;
}
