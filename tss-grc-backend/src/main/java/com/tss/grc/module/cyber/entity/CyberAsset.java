package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_assets")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(name = "asset_type")
    private String assetType;

    @Column(name = "business_owner")
    private String businessOwner;

    @Column(name = "technical_owner")
    private String technicalOwner;

    private String classification;

    private String criticality;

    @Column(name = "ip_address")
    private String ipAddress;

    private String hostname;

    @Column(name = "operating_system")
    private String operatingSystem;

    @Column(name = "linked_vulnerabilities_count")
    private int linkedVulnerabilitiesCount = 0;

    @Column(name = "linked_incidents_count")
    private int linkedIncidentsCount = 0;

    @Column(name = "linked_controls_count")
    private int linkedControlsCount = 0;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
