package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Threat intelligence record — advisories, IOCs, TTPs. Sourced from
 * Saudi-CERT, NCA bulletins, sector ISACs (FS-ISAC for banking), MISP,
 * or commercial feeds (Recorded Future / Mandiant).
 *
 * <p>An entry is "actioned" once the SOC has done what the bulletin
 * advises — blocked the IOC, scanned for the CVE, briefed exec etc.
 */
@Entity
@Table(schema = "socops", name = "threat_intel")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ThreatIntel extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, length = 120) private String externalId;
    @Column(nullable = false, length = 60) private String source;
    @Column(name = "published_at", nullable = false) private LocalDateTime publishedAt;
    @Column(name = "expires_at") private LocalDateTime expiresAt;
    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", length = 500) private String titleAr;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String summary;
    @Column(name = "ioc_type", length = 40) private String iocType;
    @Column(name = "ioc_value", length = 500) private String iocValue;
    @Column(length = 20) private String severity;
    @Column(length = 20) private String confidence;
    @Column(length = 10) private String tlp;
    @Column(name = "mitre_tactics", length = 500) private String mitreTactics;
    @Column(name = "mitre_techniques", length = 500) private String mitreTechniques;
    @Column(name = "affected_sectors", length = 500) private String affectedSectors;
    @Column(nullable = false) private Boolean actioned = false;
    @Column(name = "actioned_at") private LocalDateTime actionedAt;
    @Column(name = "actioned_by_user_id") private Long actionedByUserId;
    @Column(name = "action_notes", columnDefinition = "NVARCHAR(MAX)") private String actionNotes;
}
