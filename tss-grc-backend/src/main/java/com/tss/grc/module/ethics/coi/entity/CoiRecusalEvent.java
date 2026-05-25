package com.tss.grc.module.ethics.coi.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Recusal event recorded against a specific meeting and agenda item —
 * Sprint 13.
 *
 * <p>The Board Secretariat records a recusal whenever a director,
 * committee member, or senior executive abstains from voting on
 * an agenda item because of a declared (or just-realised) conflict
 * of interest. The {@link #recordedInMinutes} flag is ticked once
 * the official minutes confirm the recusal was applied — the
 * Compliance Officer relies on this for SAMA Article 30 reporting.
 */
@Entity
@Table(schema = "ethics", name = "coi_recusal_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CoiRecusalEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Optional FK — recusal can be ad-hoc without a prior declaration. */
    @Column(name = "declaration_id")
    private Long declarationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "meeting_reference", nullable = false, length = 128)
    private String meetingReference;

    @Column(name = "committee_code", length = 32)
    private String committeeCode;

    @Column(name = "recusal_date", nullable = false)
    private LocalDateTime recusalDate = LocalDateTime.now();

    @Column(name = "recusal_reason_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String recusalReasonEn;

    @Column(name = "recusal_reason_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String recusalReasonAr;

    /** Pipe-delimited list of agenda items, e.g. "3.2|4.1|5". */
    @Column(name = "agenda_items", columnDefinition = "NVARCHAR(MAX)")
    private String agendaItems;

    @Column(name = "recorded_in_minutes", nullable = false)
    private Boolean recordedInMinutes = true;
}
