package com.tss.grc.module.ethics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Qualified entity name avoids collision with the Sprint 13 AML sanctions
// screening entity at com.tss.grc.module.aml.sanctions.entity.ScreeningHit
// (PR #215). Two @Entity classes named ScreeningHit in different packages
// share the same JPA entity name unless qualified.
@Entity(name = "EthicsScreeningHit")
@Table(schema = "ethics", name = "screening_hits")
@Getter @Setter @NoArgsConstructor
public class ScreeningHit {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "list_source", nullable = false, length = 60)
    private String listSource;
    // OFAC_SDN | UN_CONSOLIDATED | EU_CONSOLIDATED | SAMA_LOCAL | INTERPOL | PEP_GLOBAL | ADVERSE_MEDIA

    @Column(name = "list_entry_ref", length = 100)
    private String listEntryRef;

    @Column(name = "match_score", nullable = false)
    private Integer matchScore;

    @Column(name = "match_summary", columnDefinition = "NVARCHAR(MAX)")
    private String matchSummary;

    /**
     * When the screening run that produced this match was performed.
     *
     * <p>F2-286 — this was {@code insertable = false}, which meant no API caller and no
     * Hibernate write could ever state it: the column could only ever hold {@code
     * sysutcdatetime()} at the moment the row was inserted. Measured on the live register, all
     * 12 rows are microsecond-precision instants clustered into three seed-insert batches on one
     * day, while {@code decided_at} beside them holds round business times — so two hits were
     * recorded as decided 370 days <em>before</em> they were screened. The precision is the tell:
     * a column whose every value is a microsecond instant in insert-shaped clusters was filled by
     * a default, not by anybody stating a fact.
     *
     * <p>It stays {@code updatable = false} because when a screening ran is a fact about a past
     * event, not a field to edit — {@code disposition()} records what was decided about the match
     * and leaves this alone. {@code recordHit} supplies the clock when the caller does not, which
     * is not optional: with the column NOT NULL, an omitted value would now reach the INSERT as
     * null.
     */
    @Column(name = "screened_at", nullable = false, updatable = false)
    private LocalDateTime screenedAt;

    @Column(name = "disposition", nullable = false, length = 30)
    private String disposition = "NEEDS_REVIEW";
    // TRUE_MATCH | FALSE_POSITIVE | NEEDS_REVIEW | ESCALATED

    @Column(name = "disposition_notes", columnDefinition = "NVARCHAR(MAX)")
    private String dispositionNotes;

    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "sar_filed", nullable = false)
    private Boolean sarFiled = false;

    @Column(name = "sar_reference", length = 100)
    private String sarReference;
}
