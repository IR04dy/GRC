package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Plan-template catalog row — audit Tier-3 #15.
 *
 * <p>System-defined starter content for BCP/DRP plans. A new tenant can pick
 * a template and clone it to a fresh draft plan pre-populated with the
 * canonical sections + default RTO/RPO/MTPD for that scenario.
 *
 * <p>Templates are global (no {@code organisation_id}) — the catalog is the
 * same for every tenant. Per-tenant edits land on the cloned {@link BcpPlan}
 * after creation, not back on the template.
 */
@Entity
@Table(schema = "bcm", name = "plan_templates")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpPlanTemplate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    @Column(name = "template_name_en", nullable = false, length = 255)
    private String templateNameEn;

    @Column(name = "template_name_ar", length = 255)
    private String templateNameAr;

    /** BCP | DRP */
    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType;

    /** CYBER | INFRASTRUCTURE | SUPPLIER | WORKFORCE | REGIONAL | CLOUD | TELECOM | FACILITY */
    @Column(name = "scenario_category", nullable = false, length = 50)
    private String scenarioCategory;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "default_rto_hours")  private Integer defaultRtoHours;
    @Column(name = "default_rpo_hours")  private Integer defaultRpoHours;
    @Column(name = "default_mtpd_hours") private Integer defaultMtpdHours;

    /** Section bodies as JSON: { sectionKey: { en, ar } } */
    @Column(name = "sections_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String sectionsJson;

    @Column(name = "tags_json", columnDefinition = "NVARCHAR(MAX)")
    private String tagsJson;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
