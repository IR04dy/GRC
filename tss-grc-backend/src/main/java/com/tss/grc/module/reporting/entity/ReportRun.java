package com.tss.grc.module.reporting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "reporting", name = "report_runs")
@Getter @Setter @NoArgsConstructor
public class ReportRun {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "triggered_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "triggered_by", nullable = false, length = 40)
    private String triggeredBy = "CRON"; // CRON | MANUAL

    @Column(name = "status", nullable = false, length = 20)
    private String status; // SUCCESS | FAILED | SKIPPED

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "bytes")
    private Long bytes;

    @Column(name = "recipients", columnDefinition = "NVARCHAR(MAX)")
    private String recipients;

    @Column(name = "message", columnDefinition = "NVARCHAR(MAX)")
    private String message;
}
