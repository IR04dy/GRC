package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Software Bill of Materials component. Flat row per component instance —
 * one app having {@code react@18.2.0} produces one row.
 *
 * <p>Required by NCA for critical national infrastructure; aligned to the
 * CISA SBOM Sharing Lifecycle and the {@code purl} package-URL spec.
 */
@Entity
@Table(schema = "socops", name = "sbom_component")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SbomComponent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "application_id") private Long applicationId;
    @Column(name = "application_name", nullable = false, length = 300) private String applicationName;
    @Column(name = "component_name", nullable = false, length = 300) private String componentName;
    @Column(name = "component_version", length = 120) private String componentVersion;
    @Column(name = "package_ecosystem", length = 40) private String packageEcosystem;
    @Column(length = 500) private String purl;
    @Column(length = 120) private String license;
    @Column(length = 300) private String supplier;
    @Column(name = "has_known_cves", nullable = false) private Boolean hasKnownCves = false;
    @Column(name = "highest_cve_severity", length = 20) private String highestCveSeverity;
    @Column(name = "upgrade_available", nullable = false) private Boolean upgradeAvailable = false;
    @Column(name = "last_scanned_at") private LocalDateTime lastScannedAt;
}
