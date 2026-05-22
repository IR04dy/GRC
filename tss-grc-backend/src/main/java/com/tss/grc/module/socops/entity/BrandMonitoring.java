package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Brand / dark-web monitoring alert. Recorded Future / Digital Shadows /
 * SOCRadar / Flashpoint feed entries — leaked credentials, fake-domain
 * registrations, dark-forum mentions, exposed code repos, brand abuse.
 */
@Entity
@Table(schema = "socops", name = "brand_monitoring")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BrandMonitoring extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, length = 120) private String externalId;
    @Column(name = "source_system", nullable = false, length = 40) private String sourceSystem;
    @Column(name = "alert_type", nullable = false, length = 40) private String alertType;
    @Column(name = "detected_at", nullable = false) private LocalDateTime detectedAt;
    @Column(length = 20) private String severity;
    @Column(length = 20) private String confidence;
    @Column(name = "affected_asset", length = 500) private String affectedAsset;
    @Column(name = "indicator_value", length = 500) private String indicatorValue;
    @Column(name = "indicator_context", columnDefinition = "NVARCHAR(MAX)") private String indicatorContext;
    @Column(name = "raw_payload", columnDefinition = "NVARCHAR(MAX)") private String rawPayload;
    @Column(nullable = false, length = 30) private String status = "NEW";
    @Column(name = "takedown_requested_at") private LocalDateTime takedownRequestedAt;
    @Column(name = "takedown_completed_at") private LocalDateTime takedownCompletedAt;
    @Column(name = "action_notes", columnDefinition = "NVARCHAR(MAX)") private String actionNotes;
}
