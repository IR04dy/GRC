package com.tss.grc.module.ethics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
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

    @Column(name = "screened_at", nullable = false, insertable = false, updatable = false)
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
