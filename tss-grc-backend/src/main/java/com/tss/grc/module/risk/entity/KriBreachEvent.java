package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "risk", name = "kri_breach_events")
@Getter @Setter @NoArgsConstructor
public class KriBreachEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kri_id", nullable = false)
    private Long kriId;

    @Column(name = "reading_id", nullable = false)
    private Long readingId;

    @Column(name = "breach_band", nullable = false, length = 10)
    private String breachBand; // AMBER | RED

    @Column(name = "reading_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal readingValue;

    @Column(name = "threshold_crossed", nullable = false, precision = 18, scale = 4)
    private BigDecimal thresholdCrossed;

    @Column(name = "breached_at", nullable = false)
    private LocalDateTime breachedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by_id")
    private Long acknowledgedById;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "NVARCHAR(MAX)")
    private String resolutionNotes;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN"; // OPEN | ACKNOWLEDGED | RESOLVED
}
