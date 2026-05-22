package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Reusable Saudi-regulated committee template. Read-only catalog —
 * organisations don't edit templates, they instantiate live
 * {@link Committee}s from them.
 *
 * <p>Each template carries the full GRC-SME content: mandate, authority,
 * composition, quorum, cadence, escalation chain, regulator citation,
 * and the independence / financial-expert flags drawn from CMA Corporate
 * Governance / SAMA Regulatory Governance Framework / NCA ECC-2 / PDPL /
 * NDMO / SDAIA expectations.
 *
 * <p>19 templates seeded by V154. Tenants can clone any template into a
 * live committee — the clone inherits the template's standing agenda
 * items and applicable form templates by reference, so a tenant
 * upgrading the template version sees the change cascade.
 */
@Entity
@Table(schema = "admin", name = "committee_templates")
@Getter @Setter @NoArgsConstructor
public class CommitteeTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 80, unique = true)
    private String templateCode;

    @Column(name = "committee_type", nullable = false, length = 30)
    private String committeeType;

    @Column(name = "regulatory_domain", nullable = false, length = 40)
    private String regulatoryDomain;

    @Column(name = "regulator_anchors", length = 500)
    private String regulatorAnchors;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, length = 255)
    private String nameAr;

    @Column(name = "mandate_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String mandateEn;

    @Column(name = "mandate_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String mandateAr;

    @Column(name = "authority_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String authorityEn;

    @Column(name = "authority_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String authorityAr;

    @Column(name = "composition_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String compositionEn;

    @Column(name = "composition_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String compositionAr;

    @Column(name = "quorum_pct", nullable = false)
    private Integer quorumPct;

    @Column(nullable = false, length = 20)
    private String cadence;

    @Column(name = "term_years")
    private Integer termYears;

    @Column(name = "reports_to_template_code", length = 80)
    private String reportsToTemplateCode;

    @Column(name = "escalation_en", columnDefinition = "NVARCHAR(MAX)")
    private String escalationEn;

    @Column(name = "escalation_ar", columnDefinition = "NVARCHAR(MAX)")
    private String escalationAr;

    @Column(name = "regulator_citation_en", columnDefinition = "NVARCHAR(MAX)")
    private String regulatorCitationEn;

    @Column(name = "regulator_citation_ar", columnDefinition = "NVARCHAR(MAX)")
    private String regulatorCitationAr;

    @Column(name = "independence_required", nullable = false)
    private Short independenceRequired = 0;

    @Column(name = "financial_expert_required", nullable = false)
    private Short financialExpertRequired = 0;

    @Column(name = "regulatory_reporting_en", columnDefinition = "NVARCHAR(MAX)")
    private String regulatoryReportingEn;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
