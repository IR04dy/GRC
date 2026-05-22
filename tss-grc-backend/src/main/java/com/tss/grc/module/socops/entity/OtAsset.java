package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * OT/ICS asset — industrial control system asset record. Distinguished from
 * the IT-side {@code cyber.cyber_assets} table by Purdue level, industrial
 * protocol set, safety integrity level (SIL per IEC 61511), and physical
 * site code. Required for OTCC (NCA's OT cybersecurity controls)
 * compliance evidencing at Aramco / SABIC / Ma'aden / SEC / NWC scale.
 */
@Entity
@Table(schema = "socops", name = "ot_asset")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OtAsset extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "asset_code", nullable = false, length = 80) private String assetCode;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "purdue_level", nullable = false) private Integer purdueLevel;
    @Column(name = "asset_class", nullable = false, length = 60) private String assetClass;
    @Column(length = 120) private String vendor;
    @Column(length = 120) private String model;
    @Column(name = "firmware_version", length = 120) private String firmwareVersion;
    @Column(name = "serial_number", length = 120) private String serialNumber;
    @Column(name = "site_code", length = 80) private String siteCode;
    @Column(name = "site_name", length = 300) private String siteName;
    @Column(name = "process_function", length = 300) private String processFunction;
    @Column(name = "industrial_protocols", length = 500) private String industrialProtocols;
    @Column(name = "safety_integrity_level", length = 20) private String safetyIntegrityLevel;
    @Column(nullable = false, length = 20) private String criticality = "MEDIUM";
    @Column(name = "last_patched_at") private LocalDateTime lastPatchedAt;
    @Column(name = "patch_window", length = 200) private String patchWindow;
    @Column(name = "remote_access_enabled", nullable = false) private Boolean remoteAccessEnabled = false;
    @Column(name = "network_segment", length = 120) private String networkSegment;
    @Column(nullable = false, length = 30) private String status = "ACTIVE";
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
