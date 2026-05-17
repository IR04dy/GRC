package com.tss.grc.module.ndi.reports.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(schema = "ndi", name = "report_definition")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiReportDefinition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "report_code", length = 40) private String reportCode;
    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(name = "name_ar", length = 255)                   private String nameAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "report_type", nullable = false, length = 40) private String reportType;
    @Column(nullable = false, length = 10) private String format;
    @Column(nullable = false, length = 5)  private String language;

    @Column(name = "filters_json", columnDefinition = "NVARCHAR(MAX)") private String filtersJson;
    @Column(name = "schedule_cron", length = 100) private String scheduleCron;
    @Column(columnDefinition = "NVARCHAR(MAX)")   private String recipients;

    @Column(name = "last_run_at")            private LocalDateTime lastRunAt;
    @Column(name = "last_run_status", length = 20) private String lastRunStatus;
    @Column(name = "last_run_message", columnDefinition = "NVARCHAR(MAX)") private String lastRunMessage;
    @Column(name = "last_run_size_bytes")    private Long lastRunSizeBytes;

    @Column(nullable = false, length = 20) private String status;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (format == null)    format = "JSON";
        if (language == null)  language = "EN";
        if (status == null)    status = "ACTIVE";
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
