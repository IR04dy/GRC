package com.tss.grc.security;

import com.tss.grc.module.platform.licensing.SkuRegistry;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-04 — the check that would have caught the finding: <b>is entitlement actually wired to the
 * platform, or to one module?</b>
 *
 * <p>F2-04 measured a complete, well-built entitlement subsystem adopted by <b>22 of 252
 * controllers, all of them {@code socops}</b>. Nothing failed when a controller lacked
 * {@code @RequireModule}, nothing failed when a package had no code, and nothing failed when the
 * frontend gate helper had no callers — "classic pilot-without-rollout". This class removes the
 * first of those three silences: a new controller in a sold package now fails the build until
 * someone decides which SKU sells it.
 *
 * <h3>Why reflection and not a source scan</h3>
 * <p>Measuring this with a regex over {@code *.java} was tried first, and it was wrong <b>three
 * times by three different mechanisms</b> — all of them inside one pattern:
 * <ol>
 *   <li>{@code @RestControllerAdvice} <i>contains</i> the substring {@code @RestController}, so
 *       {@code GlobalExceptionHandler} counted as an endpoint surface;</li>
 *   <li>{@code risk_lc/package-info.java} names {@code @RestController} in prose, so a package
 *       summary counted as one;</li>
 *   <li>{@code RequireModule.java}'s own javadoc <i>usage example</i> counted as one — the
 *       annotation being rolled out was itself miscounted as a thing to roll it out to.</li>
 * </ol>
 * A source scan said 255 where there are 252, and a ratchet built on it would have been permanently
 * three off in the safe-looking direction. This is the fourth time in this programme a regex has
 * miscounted a security surface; reflection sees annotations, not text.
 *
 * @see SkuRegistry the package→SKU map these assertions are made against
 * @see SkuCatalogueRatchetTest which holds the complementary half — that every code an annotation
 *      <i>names</i> is a ratified SKU. Together: the values are legal, and the coverage is complete.
 */
class EntitlementCoverageRatchetTest {

    /**
     * Anti-vacuity floors. Every offenders-is-empty assertion below passes trivially if the scan
     * returns nothing, which is precisely what happens when someone renames a base package or the
     * classpath provider stops resolving. Measured 2026-08-01, immediately after the F2-04 sweep.
     */
    private static final int CONTROLLER_FLOOR = 250;
    private static final int SOLD_CONTROLLER_FLOOR = 197;
    private static final int BASE_CONTROLLER_FLOOR = 55;

    @Test
    void everyControllerInASoldPackageCarriesItsSkuGuard() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : controllers()) {
            Optional<String> required = SkuRegistry.requiredSkuFor(c);
            if (required.isEmpty()) {
                continue;
            }
            RequireModule ann = c.getAnnotation(RequireModule.class);
            if (ann == null) {
                offenders.add(c.getName() + "  MISSING @RequireModule(\"" + required.get() + "\")");
            } else if (!required.get().equals(ann.value())) {
                offenders.add(c.getName() + "  has=\"" + ann.value()
                              + "\" but its package is sold as \"" + required.get() + "\"");
            }
        }
        Collections.sort(offenders);
        offenders.forEach(o -> System.out.println("  - " + o));

        assertThat(offenders)
            .as("A controller in a package ADR-018 sells must name that SKU. Without the guard the "
                + "endpoint is reachable by a tenant that never bought the module — which is F2-04 "
                + "itself, and it was true of 176 controllers until 2026-08-01. If the controller "
                + "genuinely belongs to a different SKU, move the package in "
                + "SkuRegistry.PACKAGE_TO_SKU and amend SKU_CATALOGUE.md + ADR-018 together.")
            .isEmpty();
    }

    /**
     * ADR-018 R4, the other direction. Base is mandatory, has no licence row and no code, so a
     * guard naming one is unsatisfiable for every tenant — the F2-23 shape, where a permission
     * nobody can hold reads as a working control.
     *
     * <p>Checking only the direction above would let a well-meaning sweep annotate
     * {@code WorkflowRequestController} with something, and every one of the 14 modules that adopted
     * the workflow engine would start 403-ing. <b>Set containment has two directions and this
     * programme has now got that wrong twice</b> (ADR-017 D-10c, then F2-61(a)).
     */
    @Test
    void noBasePlatformControllerCarriesAnEntitlementGuard() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> c : controllers()) {
            if (SkuRegistry.requiredSkuFor(c).isPresent()) {
                continue;
            }
            RequireModule ann = c.getAnnotation(RequireModule.class);
            if (ann != null) {
                offenders.add(c.getName() + "  carries @RequireModule(\"" + ann.value()
                              + "\") but its package is Base — never licensable (R4)");
            }
        }
        Collections.sort(offenders);
        offenders.forEach(o -> System.out.println("  - " + o));

        assertThat(offenders)
            .as("Base Platform is mandatory and row-less (ADR-018 R4): no tenant can ever hold a "
                + "licence for it, so this guard would deny everyone. Either the package belongs to "
                + "a SKU — move it in SkuRegistry — or the annotation must go.")
            .isEmpty();
    }

    /**
     * ADR-018 R1: every package on disk belongs to exactly one of {Base, a SKU, explicitly
     * unassigned}, "checked against the filesystem — not asserted". F2-35 existed because seven
     * packages were unhomed, one of them 7,717 LoC of SDAIA/NDMO differentiator that appeared in no
     * catalogue at all.
     *
     * <p>Scans every class under {@code com.tss.grc.module}, not just the ones with controllers, so
     * a new package is caught on the day it gets its first file rather than on the day it gets its
     * first endpoint.
     */
    @Test
    void everyModulePackageIsHomedByTheCatalogue() {
        Set<String> unhomed = new TreeSet<>();
        for (String pkg : allModulePackages()) {
            try {
                SkuRegistry.requiredSkuForPackage(pkg);
            } catch (IllegalStateException e) {
                unhomed.add(pkg.replace("com.tss.grc.module.", "").split("\\.")[0]);
            }
        }
        unhomed.forEach(p -> System.out.println("  - unhomed package: " + p));

        assertThat(unhomed)
            .as("ADR-018 R1 — an unhomed package is either unsold revenue or dead code, and "
                + "'quietly filed under Base' is how seven of them went missing. Decide it in "
                + "SKU_CATALOGUE.md + ADR-018, then record it in SkuRegistry.")
            .isEmpty();
    }

    /**
     * A SKU with no enforceable surface cannot be sold — the buyer pays and the server denies
     * nothing. This is the {@code datagov} failure mode pointed at a price list instead of a
     * package: {@code ACCESS_GOV} and {@code BOARD} in particular exist only as carve-outs (R6), so
     * if the carve-out prefix ever stops matching they silently become unenforceable while still
     * appearing on every invoice.
     */
    @Test
    void everyRatifiedSkuHasAtLeastOneGuardedController() {
        Set<String> enforced = new TreeSet<>();
        for (Class<?> c : controllers()) {
            RequireModule ann = c.getAnnotation(RequireModule.class);
            if (ann != null) {
                enforced.add(ann.value());
            }
        }
        Set<String> unenforced = new TreeSet<>(LicensingService.SELLABLE_MODULES);
        unenforced.removeAll(enforced);
        System.out.println("  enforced SKUs: " + enforced);

        assertThat(unenforced)
            .as("These SKUs are sellable but no controller enforces them, so a tenant who did not "
                + "buy one is denied nothing. Either a package moved out from under the SKU, or a "
                + "carve-out prefix in SkuRegistry.CARVE_OUTS stopped matching.")
            .isEmpty();
    }

    /**
     * Ties the registry to the ratified catalogue without transcribing the 15 codes a fourth time.
     * {@code SELLABLE_MODULES} is already asserted against ADR-018's list in
     * {@link SkuCatalogueRatchetTest}, so registry ⊆⊇ SELLABLE_MODULES closes the chain:
     * ADR-018 ↔ SELLABLE_MODULES ↔ SkuRegistry ↔ 197 annotations.
     */
    @Test
    void theRegistrySellsExactlyTheRatifiedSkus() {
        Set<String> inRegistry = new TreeSet<>(SkuRegistry.PACKAGE_TO_SKU.values());
        inRegistry.addAll(SkuRegistry.CARVE_OUTS.values());

        assertThat(inRegistry)
            .as("SkuRegistry must sell exactly what LicensingService.SELLABLE_MODULES sells. A code "
                + "here and not there is unsatisfiable (no tenant can hold it); a code there and "
                + "not here is sold but unenforced.")
            .isEqualTo(new TreeSet<>(LicensingService.SELLABLE_MODULES));
    }

    /**
     * The floors. Without these, every assertion above is green on an empty scan — and an empty scan
     * is the most likely way this file stops working, because it depends on a classpath provider
     * resolving package names at runtime.
     */
    @Test
    void theScanStillSeesTheWholeControllerSurface() {
        List<Class<?>> all = controllers();
        long sold = all.stream().filter(c -> SkuRegistry.requiredSkuFor(c).isPresent()).count();
        long base = all.size() - sold;
        System.out.printf("  controllers=%d  sold=%d  base=%d%n", all.size(), sold, base);

        assertThat(all.size()).as("total @RestController classes (floor %d)", CONTROLLER_FLOOR)
            .isGreaterThanOrEqualTo(CONTROLLER_FLOOR);
        assertThat(sold).as("controllers in a sold package (floor %d)", SOLD_CONTROLLER_FLOOR)
            .isGreaterThanOrEqualTo(SOLD_CONTROLLER_FLOOR);
        assertThat(base).as("Base controllers (floor %d) — if this collapses, the Base half of the "
                + "R4 assertion is checking nothing", BASE_CONTROLLER_FLOOR)
            .isGreaterThanOrEqualTo(BASE_CONTROLLER_FLOOR);
    }

    // ─── scanning ────────────────────────────────────────────────────────────

    private static List<Class<?>> controllers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                out.add(Class.forName(bd.getBeanClassName()));
            } catch (Throwable ignored) {
                // A class that will not load cannot be a live endpoint either.
            }
        }
        return out;
    }

    /** Every package under {@code com.tss.grc.module} that contains at least one class. */
    private static Set<String> allModulePackages() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        Set<String> pkgs = new TreeSet<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module")) {
            String name = bd.getBeanClassName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                pkgs.add(name.substring(0, dot));
            }
        }
        return pkgs;
    }
}
