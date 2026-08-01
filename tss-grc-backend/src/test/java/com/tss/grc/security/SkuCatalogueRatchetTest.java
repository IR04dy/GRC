package com.tss.grc.security;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-35 SKU-catalogue RATCHET — the entitlement code a controller demands must be a code the
 * business actually sells.
 *
 * <p><b>The defect this test exists to prevent from recurring.</b> The platform's SKU catalogue
 * existed in three contradictory versions at once, and none of them knew about the others:
 * {@code MODULE_SEPARABILITY_AND_SKU_MODEL.md} §6 said Base + 13, the product strategy said 18
 * packages, and {@code LicensingService.KNOWN_MODULES} held 20 code-shaped strings whose own
 * comment called itself "the source of truth". The three disagreed on a <em>commercial</em>
 * question — two documents put committees, board packs and access governance in the free
 * mandatory Base while a third sold them — and on grain, by roughly threefold. Both W2-0 (the
 * grandfather licence backfill) and F2-04 (the {@code @RequireModule} sweep) consume the
 * catalogue, so both were unwritable. Seven packages appeared in <em>no</em> catalogue at all,
 * including {@code ndi} at 7,717 LoC and 127 endpoints. ADR-018 ratified one list; this class is
 * what stops the code drifting away from it again.
 *
 * <p><b>The rule, stated mechanically so it is checkable.</b> Three invariants, each of which
 * failed at least once in the history above:
 * <ol>
 *   <li>Every {@code @RequireModule("X")} in the codebase names a <b>ratified</b> SKU code. An
 *       annotation against an unratified code is the F2-23 shape — a guard no tenant can
 *       satisfy, because no licence row will ever carry that code.</li>
 *   <li>{@code KNOWN_MODULES} still contains every <b>carried-forward</b> code. Codes are opaque
 *       identifiers (ADR-018 R2): dropping one silently un-licenses every tenant holding it.</li>
 *   <li>Every code in {@code KNOWN_MODULES} has a <b>ratified destination</b> in the catalogue's
 *       §5 mapping. This is the direction that actually drifts: someone adds a code in a
 *       migration, adds it here to make the validation pass, and the catalogue never hears about
 *       it. That is exactly how {@code SOCOPS} came to exist in code and be absent from the
 *       annotation's own Javadoc.</li>
 * </ol>
 *
 * <p><b>Why this test does not import the catalogue.</b> There is no machine-readable registry
 * yet — that is F2-01, and it is a later wave. Until it exists the ratified list is duplicated
 * here on purpose, because a ratchet that reads the same file the code reads proves nothing. When
 * F2-01 lands, {@link #RATIFIED} and {@link #LEGACY_DESTINATION} move into the registry and this
 * class asserts against it instead.
 *
 * @see <a href="../../../../../../../../TSS-GRC-Platform-Bible/Product/SKU_CATALOGUE.md">SKU_CATALOGUE.md — the ratified list</a>
 * @see ExportAuthorizationRatchetTest the sibling ratchet for file-returning endpoints
 */
class SkuCatalogueRatchetTest {

    /**
     * The 15 product SKU codes ratified by ADR-018. Base is deliberately absent: ADR-018 R4 makes
     * the Base Platform non-licensable, so no code represents it and no {@code @RequireModule}
     * may name it.
     */
    private static final Set<String> RATIFIED = Set.of(
        "RISK", "COMPLIANCE", "AUDIT_LC", "CYBER", "SOCOPS", "BCM", "PDPL", "TPRM",
        "ESG", "ETHICS", "SAUDI_FS", "DATA_AI_GOV", "BOARD", "ACCESS_GOV", "CHANGE"
    );

    /**
     * Codes that pre-date ratification and are kept unchanged because tenants hold licence rows
     * for them (ADR-018 R2 — a code is never renamed for cosmetics). {@code AUDIT_LC} reads like
     * a package name and stays anyway; {@code SOCOPS} is load-bearing, being the only code the
     * platform actually enforces today.
     */
    private static final Set<String> CARRIED_FORWARD = Set.of(
        "RISK", "COMPLIANCE", "AUDIT_LC", "CYBER", "SOCOPS", "BCM", "PDPL", "TPRM"
    );

    /**
     * Catalogue §5: every legacy code's destination. {@code "(Base)"} means the code is retired
     * into the mandatory Base Platform, which is safe by construction because Base is universal —
     * de-licensing a capability every tenant must have can only widen access.
     */
    private static final Map<String, String> LEGACY_DESTINATION = Map.ofEntries(
        Map.entry("RISK", "RISK"),
        Map.entry("COMPLIANCE", "COMPLIANCE"),
        Map.entry("AUDIT_LC", "AUDIT_LC"),
        Map.entry("CYBER", "CYBER"),
        Map.entry("SOCOPS", "SOCOPS"),
        Map.entry("BCM", "BCM"),
        Map.entry("PDPL", "PDPL"),
        Map.entry("TPRM", "TPRM"),
        Map.entry("POLICY", "COMPLIANCE"),
        Map.entry("REGULATORY", "COMPLIANCE"),
        Map.entry("SCM", "TPRM"),
        Map.entry("INCIDENT", "SOCOPS"),
        Map.entry("NDI", "DATA_AI_GOV"),
        Map.entry("EVIDENCE", "(Base)"),
        Map.entry("REPORTING", "(Base)"),
        Map.entry("AUDIT_LOG", "(Base)"),
        Map.entry("PLATFORM_ADMIN", "(Base)"),
        Map.entry("CONTROL_LIB", "(Base)"),
        Map.entry("POSTURE", "(Base)"),
        Map.entry("AUTOMATION", "(Base)")
    );

    /**
     * <b>197</b> measured 2026-08-01, after F2-04 swept entitlement across the platform — all
     * class-level, on every controller in a package ADR-018 sells, and no service-level sites at
     * all. Was <b>22</b> (2026-07-30), all {@code socops}: F2-04's whole finding was that a complete
     * subsystem had reached 8.6% of the surface.
     *
     * <p>The count remains a lesson about how it is measured. At 22, a {@code grep} for
     * {@code @RequireModule} returned 23 because {@code JwtTokenProvider}'s javadoc names the
     * annotation in prose. Sweeping to 197 produced <b>three more</b> phantoms of the same family,
     * each by a different mechanism — {@code @RestControllerAdvice} containing the substring
     * {@code @RestController}, a {@code package-info.java} describing its own layout, and
     * {@code RequireModule}'s own usage example. <b>Four phantoms, four mechanisms, zero from
     * reflection.</b> {@code EntitlementCoverageRatchetTest} holds the complementary assertion:
     * this one says every code named is ratified, that one says every controller that should name
     * one does.
     */
    private static final int ENTITLEMENT_SURFACE_FLOOR = 197;

    @Test
    void everyRequireModuleValueIsARatifiedSkuCode() {
        List<String> offenders = new ArrayList<>();
        for (String[] hit : scanRequireModuleSites()) {
            if (!RATIFIED.contains(hit[1])) {
                offenders.add(hit[0] + "  code=" + hit[1]);
            }
        }
        Collections.sort(offenders);
        offenders.forEach(s -> System.out.println("  - " + s));

        assertThat(offenders)
            .as("@RequireModule names a code that is not a ratified SKU. A tenant can never hold "
                + "a licence row for an unratified code, so the guard is unsatisfiable — the "
                + "F2-23 shape. Either use a ratified code, or amend "
                + "TSS-GRC-Platform-Bible/Product/SKU_CATALOGUE.md and ADR-018 together and add "
                + "the code to RATIFIED here. Never one without the others.")
            .isEmpty();
    }

    @Test
    void knownModulesStillCarriesEveryCodeTenantsHold() {
        Set<String> missing = new TreeSet<>(CARRIED_FORWARD);
        missing.removeAll(LicensingService.KNOWN_MODULES);

        assertThat(missing)
            .as("A carried-forward module code was removed from LicensingService.KNOWN_MODULES. "
                + "Codes are opaque identifiers (ADR-018 R2): organisation_licenses rows already "
                + "name these, and LicensingService.assign() rejects codes not in this set — so "
                + "dropping one makes an existing entitlement unassignable. Retire a code only "
                + "via a migration that grants its successor to every organisation holding it "
                + "(R3), and measure the holders first.")
            .isEmpty();
    }

    /**
     * Every assignable code is accounted for in the catalogue — either as one of the 15 ratified
     * SKUs, or as a legacy code with a §5 destination.
     *
     * <p>⚠ This assertion used to be {@code KNOWN_MODULES ⊆ LEGACY_DESTINATION.keySet()}, which was
     * right while every assignable code was a legacy one and <b>wrong the moment W2-0 added the
     * seven ratified codes no tenant had yet</b> (ESG, ETHICS, SAUDI_FS, DATA_AI_GOV, BOARD,
     * ACCESS_GOV, CHANGE). It failed on them, correctly by its own wording and incorrectly by its
     * purpose: they are not legacy, they are the catalogue. Widened to the union, which keeps the
     * drift it exists to catch — a code appearing in neither set is still a code the catalogue has
     * never heard of.
     */
    @Test
    void everyKnownModuleCodeIsAccountedForInTheCatalogue() {
        Set<String> accounted = new TreeSet<>(RATIFIED);
        accounted.addAll(LEGACY_DESTINATION.keySet());

        Set<String> unaccounted = new TreeSet<>(LicensingService.KNOWN_MODULES);
        unaccounted.removeAll(accounted);

        assertThat(unaccounted)
            .as("A code exists in LicensingService.KNOWN_MODULES that is neither a ratified SKU nor "
                + "a legacy code with a §5 destination. This is the direction that drifts: a "
                + "migration introduces a code, the Set.of grows to make validation pass, and "
                + "the catalogue never hears about it — which is how SOCOPS came to be enforced "
                + "in 22 places while the annotation's own Javadoc listed 19 codes without it. "
                + "Add the code to SKU_CATALOGUE.md — §3 if it is sellable, §5 if it is retiring.")
            .isEmpty();
    }

    /**
     * The service's retirement map and the catalogue's §5 must agree. Two independent
     * transcriptions of the same table, asserted equal — which is the only way a transcription
     * catches drift. {@link LicensingService#RETIRED_DESTINATION} exists because the service quotes
     * the destination in its refusal message ("grant COMPLIANCE instead"), and a wrong destination
     * in that message sends an operator to license the wrong SKU.
     *
     * <p>The two sets are deliberately not derived from each other: {@link #LEGACY_DESTINATION} is
     * this test's transcription of the markdown, and the map in the service is the code's. If one
     * ever imported the other, this assertion would compare a value with itself.
     */
    @Test
    void theServicesRetirementMapMatchesTheCatalogue() {
        Map<String, String> fromCatalogue = new TreeMap<>();
        LEGACY_DESTINATION.forEach((code, dest) -> {
            // A code whose destination is itself is carried forward, not retired.
            if (!code.equals(dest)) fromCatalogue.put(code, dest);
        });

        assertThat(new TreeMap<>(LicensingService.RETIRED_DESTINATION))
            .as("LicensingService.RETIRED_DESTINATION disagrees with SKU_CATALOGUE.md §5. The "
                + "service quotes these destinations back to an operator who tried to license a "
                + "retired code, so a wrong entry tells them to buy the wrong SKU. Amend the "
                + "catalogue, ADR-018 and both maps together.")
            .isEqualTo(fromCatalogue);
    }

    /**
     * The sellable set and the retired set must not overlap, and together they must be exactly the
     * validation set. A code in both would be simultaneously offerable and refused — and the
     * refusal wins, so the platform would silently stop selling a ratified SKU.
     */
    @Test
    void sellableAndRetiredPartitionTheAssignableCodes() {
        Set<String> overlap = new TreeSet<>(LicensingService.SELLABLE_MODULES);
        overlap.retainAll(LicensingService.RETIRED_DESTINATION.keySet());
        assertThat(overlap)
            .as("A code is both sellable and retired. upsert() refuses new rows for retired codes, "
                + "so the refusal wins and a ratified SKU becomes unsellable with no error anywhere "
                + "except the operator's screen.")
            .isEmpty();

        Set<String> union = new TreeSet<>(LicensingService.SELLABLE_MODULES);
        union.addAll(LicensingService.RETIRED_DESTINATION.keySet());
        assertThat(new TreeSet<>(LicensingService.KNOWN_MODULES))
            .as("KNOWN_MODULES is meant to be exactly SELLABLE_MODULES ∪ RETIRED_DESTINATION's keys")
            .isEqualTo(union);

        assertThat(LicensingService.SELLABLE_MODULES)
            .as("the sellable set must be the 15 ratified SKUs — no more, no fewer")
            .containsExactlyInAnyOrderElementsOf(RATIFIED);
    }

    /**
     * The population is asserted, because
     * {@link #everyRequireModuleValueIsARatifiedSkuCode} passes vacuously if the scan stops
     * seeing annotations — and a dead ratchet reads exactly like a clean one.
     */
    @Test
    void theScanStillFindsTheEntitlementSurface() {
        int found = scanRequireModuleSites().size();
        System.out.println("[F2-35 SKU ratchet] @RequireModule sites discovered = " + found
            + " (floor " + ENTITLEMENT_SURFACE_FLOOR + ")");

        assertThat(found)
            .as("The scan found %d @RequireModule sites; %d were measured when ADR-018 shipped. "
                + "A drop means the detector broke or entitlement was removed, not that the "
                + "annotations left. F2-04 raises this floor as it sweeps the remaining ~2,138 "
                + "endpoints.", found, ENTITLEMENT_SURFACE_FLOOR)
            .isGreaterThanOrEqualTo(ENTITLEMENT_SURFACE_FLOOR);
    }

    /** @return {@code [SimpleName(#method), module code]} for every {@code @RequireModule} site. */
    private static List<String[]> scanRequireModuleSites() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String[]> hits = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            RequireModule onType = c.getAnnotation(RequireModule.class);
            if (onType != null) {
                hits.add(new String[] { c.getSimpleName(), onType.value() });
            }
            for (Method m : c.getDeclaredMethods()) {
                RequireModule onMethod = m.getAnnotation(RequireModule.class);
                if (onMethod != null) {
                    hits.add(new String[] { c.getSimpleName() + "#" + m.getName(), onMethod.value() });
                }
            }
        }
        return hits;
    }
}
