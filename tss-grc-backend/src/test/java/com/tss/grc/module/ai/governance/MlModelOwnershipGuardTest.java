package com.tss.grc.module.ai.governance;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-159 — <b>which</b> AI-governance endpoints may admit a model's owner, and which may not.
 *
 * <h3>Why this is a test and not a comment</h3>
 * <p>Every endpoint under {@code /api/v1/ai/models/{externalId}/…} carries the record id, so every one of
 * them <em>could</em> take the ownership leg. Ten can; five deliberately do not, because the action is a
 * judgement about the model rather than work on it:
 * <ul>
 *   <li>{@code transition} and {@code decommission} move the lifecycle stage — the owner driving that is
 *       self-approval on a governance gate;</li>
 *   <li>{@code MlModelAuditController#record} files the fairness / bias / AIA result — the owner auditing
 *       their own model;</li>
 *   <li>{@code MlModelDriftController#record} and {@code #acknowledge} are the drift verdict.</li>
 * </ul>
 *
 * <p>That is exactly the distinction V367 enforced when it took {@code SHARIA:APPROVE} away from the
 * Sharia auditor, and F2-156 is the standing proof that <b>an instruction with no enforcement gets read
 * past</b>: {@code AttachmentAccessPolicy} told two slices in a comment to move its entry with the parent
 * and both ignored it. "It has an id, so bind it" is a plausible simplification of this slice, it would
 * pass every other test in the suite, and it would hand the model owner approval of their own model. So
 * the boundary is asserted by name, in both directions.
 *
 * <p>Reflection over the compiled annotation, per this repository's standing rule: a guard expression
 * assembled from constants and spanning source lines is invisible to a regex, which has under-counted an
 * authorisation surface here four times.
 */
class MlModelOwnershipGuardTest {

    private static final String OWNERSHIP_LEG = "hasPermission(#externalId, 'ML_MODEL', 'OWNER')";

    /** Endpoints that may admit the record's owner: reading it, and editing its own metadata. */
    private static final Set<String> MAY_ADMIT_OWNER = Set.of(
        "MlModelController#get",
        "MlModelController#update",
        "MlModelAuditController#list",
        "MlModelAuditController#latest",
        "MlModelDriftController#list");

    /**
     * Endpoints that carry an {@code externalId} and must <b>not</b> admit the owner. Each is a judgement
     * about the model; the owner is the subject of that judgement, not its author.
     */
    private static final Set<String> MUST_NOT_ADMIT_OWNER = Set.of(
        "MlModelController#transition",
        "MlModelController#decommission",
        "MlModelAuditController#record",
        "MlModelDriftController#record",
        "MlModelDriftController#acknowledge");

    private static TreeMap<String, String> guardsOfAiControllers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        TreeMap<String, String> out = new TreeMap<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module.ai")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {                                              // NOPMD
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa != null) out.put(c.getSimpleName() + "#" + m.getName(), pa.value());
            }
        }
        return out;
    }

    @Test
    void onlyTheDesignatedEndpointsAdmitTheModelsOwner() {
        TreeMap<String, String> guards = guardsOfAiControllers();

        // Anti-vacuity: a scan that found no guards would satisfy every assertion below.
        assertThat(guards.size())
            .as("scanned %d guarded methods in com.tss.grc.module.ai; the three model controllers alone "
                + "carry more than 15. A collapsed scan proves nothing.", guards.size())
            .isGreaterThanOrEqualTo(15);

        List<String> missing = new ArrayList<>();
        for (String site : MAY_ADMIT_OWNER) {
            String g = guards.get(site);
            if (g == null) {
                missing.add(site + " — NOT FOUND (renamed or removed; this test must follow it, because "
                    + "a check that cannot find its subject passes silently)");
            } else if (!g.contains(OWNERSHIP_LEG)) {
                missing.add(site + " — lost the ownership leg: " + g);
            }
        }
        assertThat(missing).as(
            "These endpoints are supposed to admit the model's owner via %s. Without it the owner is "
            + "refused their own model — the defect F2-159 fixed, and dev proves it is not theoretical: "
            + "`ranalyst` owns 6 of the 25 models and could open none of them. Offenders: %s",
            OWNERSHIP_LEG, missing).isEmpty();

        List<String> overreach = new ArrayList<>();
        for (String site : MUST_NOT_ADMIT_OWNER) {
            String g = guards.get(site);
            if (g == null) {
                overreach.add(site + " — NOT FOUND (see above: a missing subject is a silent pass)");
            } else if (g.contains(OWNERSHIP_LEG)) {
                overreach.add(site + " — gained the ownership leg: " + g);
            }
        }
        assertThat(overreach).as("""
            These endpoints must NOT admit the model's owner. Each is a judgement about the model — a \
            lifecycle transition, the fairness/bias audit result, or a drift verdict — and the owner is \
            its subject. Admitting them is the self-review defect V367 removed from `sharia`, \
            transplanted into AI governance.

            Every one of these carries an externalId, so binding it is a one-word change that no other \
            test would notice. If the policy genuinely changes, change it HERE and say who decided.

            Offenders: %s""".formatted(overreach)).isEmpty();
    }

    /**
     * The ownership leg is an <em>addition</em> to the oversight roles, never a replacement.
     *
     * <p>Rewriting a guard as the ownership check alone would look like a tightening and would lock the
     * risk manager, CISO, DPO and auditor out of a model they are accountable for — the mirror image of
     * the defect this slice fixed, and the direction F2-53 recorded as the more likely one when a role
     * guard is converted.
     */
    @Test
    void everyOwnershipLegSitsBesideTheOversightRoles() {
        List<String> lonely = new ArrayList<>();
        guardsOfAiControllers().forEach((site, guard) -> {
            if (guard.contains(OWNERSHIP_LEG) && !guard.contains("hasAnyRole(")) {
                lonely.add(site + " -> " + guard);
            }
        });
        assertThat(lonely).as(
            "An ownership check that REPLACED the role list would read as a tightening and would refuse "
            + "the roles accountable for the model. It is always an additional `or` leg. Offenders: %s",
            lonely).isEmpty();
    }
}
