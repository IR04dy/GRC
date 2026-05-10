package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "risk", name = "appetite_breach_events")
@Getter @Setter @NoArgsConstructor
public class AppetiteBreachEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appetite_id", nullable = false)
    private Long appetiteId;

    @Column(name = "observed_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal observedValue;

    @Column(name = "breach_severity", nullable = false, length = 20)
    private String breachSeverity; // WITHIN_TOLERANCE | EXCEEDED | CRITICAL

    @Column(name = "breached_at", nullable = false)
    private LocalDateTime breachedAt;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by_id")
    private Long acknowledgedById;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";
}
