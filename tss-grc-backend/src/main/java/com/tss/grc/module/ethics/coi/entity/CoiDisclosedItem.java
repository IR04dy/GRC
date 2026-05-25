package com.tss.grc.module.ethics.coi.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One disclosed item (line) inside a {@link CoiDeclaration} — Sprint 13.
 *
 * <p>{@link #itemType} drives the auto-flag rules in {@code CoiService}:
 * <ul>
 *   <li>RELATED_PARTY / FAMILY_RELATIONSHIP / POLITICAL_OFFICE → RECUSAL_REQUIRED</li>
 *   <li>DIRECTORSHIP / OUTSIDE_EMPLOYMENT → MITIGATION_REQUIRED</li>
 *   <li>{@link #financialValueSar} &gt;= 100,000 → RECUSAL_REQUIRED</li>
 *   <li>{@link #financialValueSar} &gt;= 50,000  → MITIGATION_REQUIRED</li>
 * </ul>
 */
@Entity
@Table(schema = "ethics", name = "coi_disclosed_item")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CoiDisclosedItem extends BaseEntity {

    @Column(name = "declaration_id", nullable = false)
    private Long declarationId;

    /**
     * DIRECTORSHIP | SHAREHOLDING | RELATED_PARTY | GIFT_HOSPITALITY |
     * OUTSIDE_EMPLOYMENT | POLITICAL_OFFICE | FAMILY_RELATIONSHIP | OTHER.
     */
    @Column(name = "item_type", nullable = false, length = 64)
    private String itemType;

    @Column(name = "counterparty_en", nullable = false, length = 256)
    private String counterpartyEn;

    @Column(name = "counterparty_ar", nullable = false, length = 256)
    private String counterpartyAr;

    @Column(name = "relationship_description_en", columnDefinition = "NVARCHAR(MAX)")
    private String relationshipDescriptionEn;

    @Column(name = "relationship_description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String relationshipDescriptionAr;

    @Column(name = "financial_value_sar", precision = 18, scale = 2)
    private BigDecimal financialValueSar;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "mitigation_action_en", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationActionEn;

    @Column(name = "mitigation_action_ar", columnDefinition = "NVARCHAR(MAX)")
    private String mitigationActionAr;
}
