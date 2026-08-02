package com.tss.grc.module.platform.licensing;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which ratified SKU owns each package on disk — <b>ADR-018 §3 and §4, as data</b>.
 *
 * <p>This is the map F2-04 needed and the register asked for: "extend {@code KNOWN_MODULES} to all
 * 58 codes, sourced from the F2-01 module registry so the two cannot diverge". ADR-018 changed the
 * shape of that answer — 58 packages do not become 58 codes, they become <b>15 SKUs plus a
 * non-licensable Base</b> — but the reason for holding it as data survived: a package→SKU decision
 * that lives only in 197 scattered annotations cannot be audited, and a new package added without a
 * decision is invisible.
 *
 * <p><b>Why this is main code and not a test constant.</b> Three consumers need it, and two do not
 * exist yet: {@code EntitlementCoverageRatchetTest} today; F2-01's module registry, which ADR-018 R6
 * says must key on <i>entitlement unit</i> rather than package; and the frontend, which needs a
 * route→SKU map to hide what a tenant has not bought (F2-04b). Holding it in a test would mean
 * transcribing it again for each.
 *
 * <h3>R6 — the entitlement unit is not always a package</h3>
 * <p>Two SKUs are carve-outs of kernel packages rather than packages of their own, so a
 * package-keyed lookup alone gets them wrong: {@code admin.committee} → {@code BOARD} (the generic
 * committee engine, 51 files, sold to the Board Secretary) and {@code iam.governance} →
 * {@code ACCESS_GOV} (22 files, no top-level package at all). {@link #CARVE_OUTS} is therefore
 * consulted <b>before</b> {@link #PACKAGE_TO_SKU}, and {@code admin} and {@code iam} are Base.
 *
 * <h3>Base is not a SKU and must never be annotated</h3>
 * <p>ADR-018 R4: Base has no licence row, no code and no {@code @RequireModule}. A guard naming it
 * would be unsatisfiable for every tenant — the F2-23 shape. {@link #requiredSkuFor} returns empty
 * for Base, and the ratchet asserts those controllers carry no annotation at all, in <b>both</b>
 * directions: set containment has two, and checking one is how ADR-017 D-10c was got wrong.
 */
public final class SkuRegistry {

    private SkuRegistry() {}

    /** ADR-018 §3 — top-level package → the SKU that sells it. */
    public static final Map<String, String> PACKAGE_TO_SKU = Map.ofEntries(
        Map.entry("risk", "RISK"),
        Map.entry("risk_lc", "RISK"),
        Map.entry("ccm", "RISK"),
        Map.entry("compliance", "COMPLIANCE"),
        Map.entry("policy", "COMPLIANCE"),
        Map.entry("nca_ecc", "COMPLIANCE"),
        Map.entry("regchange", "COMPLIANCE"),
        Map.entry("reginspect", "COMPLIANCE"),
        Map.entry("regreporting", "COMPLIANCE"),
        Map.entry("audit_lc", "AUDIT_LC"),
        Map.entry("audit_eng", "AUDIT_LC"),
        Map.entry("audit_committee", "AUDIT_LC"),
        Map.entry("cyber", "CYBER"),
        Map.entry("cyber_lc", "CYBER"),
        Map.entry("socops", "SOCOPS"),
        Map.entry("incident", "SOCOPS"),
        Map.entry("bcm", "BCM"),
        Map.entry("bcm_lc", "BCM"),
        Map.entry("opres", "BCM"),
        Map.entry("privacy", "PDPL"),
        Map.entry("privacy_lc", "PDPL"),
        Map.entry("data_officer", "PDPL"),
        Map.entry("dataclass", "PDPL"),
        Map.entry("tprm", "TPRM"),
        Map.entry("scm", "TPRM"),
        Map.entry("outsourcing", "TPRM"),
        Map.entry("sust", "ESG"),
        Map.entry("ethics", "ETHICS"),
        Map.entry("consumer", "ETHICS"),
        Map.entry("aml", "ETHICS"),
        Map.entry("fraud", "ETHICS"),
        Map.entry("sharia", "SAUDI_FS"),
        Map.entry("openbanking", "SAUDI_FS"),
        Map.entry("rrp", "SAUDI_FS"),
        Map.entry("cpty", "SAUDI_FS"),
        Map.entry("ndi", "DATA_AI_GOV"),
        Map.entry("ai", "DATA_AI_GOV"),
        Map.entry("board_pack", "BOARD"),
        Map.entry("change_mgmt", "CHANGE"),
        // S5/CE-1 — the adapters that bridge Change Enablement to admin.committee and
        // incident. Homed to CHANGE, not Base: they exist only to serve this SKU, they ship
        // and are licensed with it, and Base must not carry change-specific glue. Creating a
        // module package IS an entitlement decision here, because requiredSkuForPackage
        // THROWS for an unhomed one (ADR-018 R1 — the homing is total and disjoint).
        Map.entry("change_adapters", "CHANGE"));

    /**
     * ADR-018 R6 — {@code "<top>.<sub>"} → SKU, consulted before {@link #PACKAGE_TO_SKU} because
     * the parent package is Base in both cases.
     */
    public static final Map<String, String> CARVE_OUTS = Map.of(
        "admin.committee", "BOARD",
        "iam.governance", "ACCESS_GOV");

    /** ADR-018 §4 — the 18 packages that are never a SKU. Mandatory, row-less, never annotated. */
    public static final Set<String> BASE_PACKAGES = Set.of(
        "platform", "iam", "auth", "admin", "evidence", "attachment", "signature",
        "workflow", "workflow_submission", "approvals", "intake", "capa", "dashboard",
        "reporting", "integrations", "knowledge", "controlmap", "governance");

    /**
     * ADR-018 §4 — recorded as unassigned rather than quietly filed under Base, "because quietly
     * filed is how the other seven went missing". {@code datagov} holds one {@code package-info.java}
     * and no code; delete it or fill it.
     */
    public static final Set<String> UNASSIGNED_PACKAGES = Set.of("datagov");

    /**
     * The SKU whose {@code @RequireModule} this class must carry, or empty when it must carry none.
     *
     * @return the SKU code for a class in a sold package; {@link Optional#empty()} for Base, for
     *         {@code datagov}, and for anything outside {@code com.tss.grc.module} (a class in
     *         {@code common} or {@code security} is infrastructure, not a product surface)
     * @throws IllegalStateException if the class sits in a {@code module} package that ADR-018 never
     *         homed. R1 makes the homing total and disjoint, so an unhomed package is a decision
     *         nobody took — failing loudly is the point, since silently treating it as Base is
     *         exactly how seven packages became unsold revenue.
     */
    public static Optional<String> requiredSkuFor(Class<?> type) {
        return requiredSkuForPackage(type.getPackageName());
    }

    /** @param packageName a fully-qualified package, e.g. {@code com.tss.grc.module.risk.controller} */
    public static Optional<String> requiredSkuForPackage(String packageName) {
        final String prefix = "com.tss.grc.module.";
        if (packageName == null || !packageName.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] parts = packageName.substring(prefix.length()).split("\\.");
        String top = parts[0];
        if (parts.length > 1) {
            String carve = CARVE_OUTS.get(top + "." + parts[1]);
            if (carve != null) {
                return Optional.of(carve);
            }
        }
        if (BASE_PACKAGES.contains(top) || UNASSIGNED_PACKAGES.contains(top)) {
            return Optional.empty();
        }
        String sku = PACKAGE_TO_SKU.get(top);
        if (sku == null) {
            throw new IllegalStateException(
                "Package '" + top + "' is not homed by ADR-018. Every package on disk belongs to "
                + "exactly one of {Base, a SKU, explicitly unassigned} (R1). Amend "
                + "TSS-GRC-Platform-Bible/Product/SKU_CATALOGUE.md and ADR-018 together, then add "
                + "it to PACKAGE_TO_SKU, BASE_PACKAGES or UNASSIGNED_PACKAGES here.");
        }
        return Optional.of(sku);
    }
}
