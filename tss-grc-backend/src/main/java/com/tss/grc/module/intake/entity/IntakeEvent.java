package com.tss.grc.module.intake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IntakeEvent — append-only audit row for every status transition on an
 * IntakeRecord. The intake submitter sees these on the public case-tracker
 * page; auditors use them to reconstruct who did what when.
 */
@Entity
@Table(schema = "intake", name = "intake_events")
@Getter @Setter @NoArgsConstructor
public class IntakeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_record_id", nullable = false)
    private IntakeRecord intakeRecord;

    @Column(name = "at", nullable = false)
    private LocalDateTime at = LocalDateTime.now();

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "actor", nullable = false, length = 254)
    private String actor;

    @Lob
    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;
}
