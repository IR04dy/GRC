package com.tss.grc.module.platform.help.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * Contextual help topic — Sprint 12 Embedded Help.
 *
 * <p>Every topic targets a {@link #routePattern} (exact path or
 * wildcard regex) so the floating help drawer can resolve the right
 * tip for the page the user is currently on. The {@link #module}
 * column drives the admin "coverage gaps" widget and is used by the
 * drawer to surface "related wizards" filtered to the current
 * module.
 *
 * <p>Multi-tenancy: {@link #organisationId} is nullable. {@code NULL}
 * means platform-global (the 30 baseline rows seeded by V191); a
 * non-null value scopes the row to one tenant and overrides any
 * global row matching the same {@code routePattern}.
 *
 * <p>Saudi regulatory anchor lives in {@link #regulatoryAnchor} +
 * {@link #regulatorLinkUrl} so every topic that touches a regulated
 * capability can cite its source. The drawer renders this as a
 * highlighted callout — the explicit anchor is what makes the help
 * surface count as a documented training touchpoint under NCA ECC-2
 * 1-5 (Cybersecurity Awareness and Training).
 */
@Entity
@Table(schema = "platform", name = "help_topic")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class HelpTopic extends BaseEntity {

    /** Nullable — {@code NULL} == platform-global. */
    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "topic_code", nullable = false, length = 64)
    private String topicCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    @Column(name = "summary_en", length = 500)
    private String summaryEn;

    @Column(name = "summary_ar", length = 500)
    private String summaryAr;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "body_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    /**
     * URL path or wildcard pattern this topic applies to.
     * Examples: {@code /risk/register}, {@code /cyber/attack-surface/*},
     * {@code /bcm/exercises/run/*&#47;observe}.
     */
    @Column(name = "route_pattern", nullable = false, length = 255)
    private String routePattern;

    /**
     * RISK / CYBER / COMPLIANCE / BCM / PRIVACY / TPRM / ESG / AUDIT /
     * AI_GOVERNANCE / CCM / GENERAL / ADMIN.
     */
    @Column(name = "module", nullable = false, length = 40)
    private String module;

    @Column(name = "regulatory_anchor", length = 500)
    private String regulatoryAnchor;

    @Column(name = "regulator_link_url", length = 500)
    private String regulatorLinkUrl;

    @Column(name = "related_kb_article_id")
    private Long relatedKbArticleId;

    /** Comma-separated wizard codes. */
    @Column(name = "related_workflow_codes", length = 500)
    private String relatedWorkflowCodes;

    /** INFO / TIP / WARNING — drives drawer header tone. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "INFO";

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 100;
}
