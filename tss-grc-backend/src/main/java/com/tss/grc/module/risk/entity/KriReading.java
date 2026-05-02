package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "risk", name = "kri_readings")
@Getter @Setter @NoArgsConstructor
public class KriReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kri_id", nullable = false)
    private Long kriId;

    @Column(name = "reading_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal readingValue;

    @Column(name = "reading_at", nullable = false)
    private LocalDateTime readingAt;

    @Column(name = "band", length = 10)
    private String band; // GREEN | AMBER | RED

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "recorded_by")
    private Long recordedBy;
}
