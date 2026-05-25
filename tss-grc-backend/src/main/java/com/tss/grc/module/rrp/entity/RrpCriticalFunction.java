package com.tss.grc.module.rrp.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * One critical function in the bank's RRP critical-functions inventory.
 *
 * <p>SAMA's Recovery Planning Rules define a "critical function" as an
 * economic function the bank provides whose sudden withdrawal would
 * materially harm the wider financial system or real economy in the
 * Kingdom. Each one must keep running through any resolution scenario.
 *
 * <p>Typical Saudi-bank critical functions:
 * <ul>
 *   <li>Retail deposit access (ATM + branch + mobile)</li>
 *   <li>Domestic payments (SADAD bill payments, IPS instant transfers)</li>
 *   <li>Government salary payment processing</li>
 *   <li>Trade-finance letter-of-credit continuity (oil import/export chains)</li>
 *   <li>Mortgage payment collection (REDF mortgages)</li>
 *   <li>Treasury / FX market-making for SAR-USD pair</li>
 * </ul>
 */
@Entity
@Table(schema = "rrp", name = "rrp_critical_function")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RrpCriticalFunction extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    /** Function code within the cycle. e.g. {@code CF-001}. */
    @Column(name = "function_code", nullable = false, length = 32)
    private String functionCode;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * Materiality band:
     * {@code SYSTEMIC | HIGH | MEDIUM | LOW}.
     * SYSTEMIC = withdrawal harms national financial system.
     */
    @Column(name = "materiality", nullable = false, length = 16)
    private String materiality = "MEDIUM";

    /**
     * Maximum tolerable downtime — hours. The continuity ceiling SAMA
     * expects in resolution.
     */
    @Column(name = "max_tolerable_downtime_hours")
    private Integer maxTolerableDowntimeHours;

    /** Customer count served, approximate. */
    @Column(name = "customer_count_estimate")
    private Long customerCountEstimate;

    /** Average daily volume (count of operations). */
    @Column(name = "daily_volume_estimate")
    private Long dailyVolumeEstimate;

    /** Critical service-provider dependency narrative. */
    @Column(name = "critical_provider_dependencies_en", columnDefinition = "NVARCHAR(MAX)")
    private String criticalProviderDependenciesEn;

    @Column(name = "critical_provider_dependencies_ar", columnDefinition = "NVARCHAR(MAX)")
    private String criticalProviderDependenciesAr;

    /** Resolution-continuity strategy summary. */
    @Column(name = "continuity_strategy_en", columnDefinition = "NVARCHAR(MAX)")
    private String continuityStrategyEn;

    @Column(name = "continuity_strategy_ar", columnDefinition = "NVARCHAR(MAX)")
    private String continuityStrategyAr;

    /** Owner role (typically a business-line head). */
    @Column(name = "owner_role", length = 64)
    private String ownerRole;
}
