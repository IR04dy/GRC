package com.tss.grc.module.sust.finance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Green-Finance uplift G1 — the use-of-proceeds calculation rules of plan §4.2
 * (حساب الصكوك الخضراء), pure and unit-tested:
 *
 * <pre>
 * net_proceeds        = amount − issuance_costs
 * allocation_ratio    = allocated / net_proceeds          (× 100 for %)
 * unallocated         = net_proceeds − allocated          (never negative-silently — callers block over-allocation)
 * refinancing_share   = refinanced / allocated            (× 100 for %)
 * project_share       = allocation_amount / project_total_cost   (pro-rata attribution, capped at 1)
 * attributed_impact   = project_share × project_actual_impact
 * intensity           = impact / allocated                (per SAR mn)
 * </pre>
 */
public final class GreenFinanceMath {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GreenFinanceMath() {}

    /** amount − issuance costs (floored at zero). */
    public static BigDecimal netProceeds(BigDecimal amount, BigDecimal issuanceCosts) {
        BigDecimal a = nz(amount), c = nz(issuanceCosts);
        BigDecimal net = a.subtract(c);
        return net.signum() < 0 ? BigDecimal.ZERO : net;
    }

    /** allocated / netProceeds × 100, 4 dp; 0 when netProceeds is 0. */
    public static BigDecimal allocationRatioPct(BigDecimal allocated, BigDecimal netProceeds) {
        if (nz(netProceeds).signum() == 0) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        return nz(allocated).divide(netProceeds, MC).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    /** netProceeds − allocated (may be negative only if callers failed to block over-allocation). */
    public static BigDecimal unallocated(BigDecimal netProceeds, BigDecimal allocated) {
        return nz(netProceeds).subtract(nz(allocated)).setScale(4, RoundingMode.HALF_UP);
    }

    /** True when an additional amount would exceed net proceeds (the hard block of §4.2). */
    public static boolean wouldOverAllocate(BigDecimal alreadyAllocated, BigDecimal additional, BigDecimal netProceeds) {
        return nz(alreadyAllocated).add(nz(additional)).compareTo(nz(netProceeds)) > 0;
    }

    /** refinanced / allocated × 100, 4 dp; 0 when allocated is 0. */
    public static BigDecimal refinancingSharePct(BigDecimal refinanced, BigDecimal allocated) {
        if (nz(allocated).signum() == 0) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        return nz(refinanced).divide(allocated, MC).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    /** allocation / projectTotalCost, capped at 1 (an instrument can never own >100 % of a project). */
    public static BigDecimal projectShare(BigDecimal allocationAmount, BigDecimal projectTotalCost) {
        if (nz(projectTotalCost).signum() == 0) return BigDecimal.ZERO;
        BigDecimal share = nz(allocationAmount).divide(projectTotalCost, MC);
        return share.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : share;
    }

    /** share × actual project impact (null-safe: null impact contributes 0). */
    public static BigDecimal attributedImpact(BigDecimal share, BigDecimal projectActualImpact) {
        return nz(share).multiply(nz(projectActualImpact), MC).setScale(4, RoundingMode.HALF_UP);
    }

    /** impact / allocated (per SAR mn), 6 dp; 0 when allocated is 0. */
    public static BigDecimal intensityPerSarMn(BigDecimal impact, BigDecimal allocated) {
        if (nz(allocated).signum() == 0) return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        return nz(impact).divide(allocated, MC).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
