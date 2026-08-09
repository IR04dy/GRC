package com.tss.grc.module.intake.spi;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Target → {@link RegisterPromotionPort} lookup, and the place a promotion is allowed to fail
 * loudly — F2-172.
 *
 * <p>Two failure modes are made explicit here rather than left to each adapter, because both of
 * them are silent by nature and both are the F2-171 defect wearing a different hat:
 *
 * <ol>
 *   <li><b>No port for the target.</b> Six intake kinds route to {@code CONTROL_TEST}, and the
 *       platform has no register for a periodic regulatory submission — there is nothing to
 *       create. That must be a 409 naming the target, not a promotion that flips the status and
 *       writes nothing. The whole finding is that "promoted" meant "nothing happened".</li>
 *   <li><b>A port that returns nothing.</b> {@link RegisterPromotionPort#promote} is documented as
 *       returning the id of a row that exists, and an adapter returning {@code null} or blank
 *       would satisfy the compiler while re-creating the defect exactly. Checked, not trusted.</li>
 * </ol>
 *
 * <p>Duplicate targets fail at <b>startup</b>. Two beans claiming {@code "RISK"} is not a runtime
 * condition to route around — it means promotions land in whichever bean Spring ordered last, and
 * which one that is can change with an unrelated refactor.
 */
@Component
public class PromotionRegistry {

    private final Map<String, RegisterPromotionPort> byTarget;

    public PromotionRegistry(List<RegisterPromotionPort> ports) {
        Map<String, RegisterPromotionPort> map = new LinkedHashMap<>();
        for (RegisterPromotionPort p : ports) {
            String t = p.target();
            if (t == null || t.isBlank()) {
                throw new IllegalStateException(
                    "RegisterPromotionPort " + p.getClass().getName() + " declares no target");
            }
            RegisterPromotionPort clash = map.putIfAbsent(t, p);
            if (clash != null) {
                throw new IllegalStateException("Two RegisterPromotionPort beans claim target '" + t
                    + "': " + clash.getClass().getName() + " and " + p.getClass().getName());
            }
        }
        this.byTarget = Map.copyOf(map);
    }

    /** Targets a promotion can actually be performed for, sorted — used by tests and diagnostics. */
    public Set<String> supportedTargets() {
        return new TreeSet<>(byTarget.keySet());
    }

    public boolean supports(String target) {
        return byTarget.containsKey(target);
    }

    /**
     * The {@code "MODULE:ACTION"} the caller must hold to promote into {@code target}, or null when
     * no port serves it — in which case the caller should let {@link #promote} raise the real error
     * rather than reporting a permission problem for a capability that does not exist.
     */
    public String requiredPermissionFor(String target) {
        RegisterPromotionPort p = byTarget.get(target);
        return p == null ? null : p.requiredPermission();
    }

    /**
     * Create the downstream record and return its id.
     *
     * @throws IllegalStateException (→ 409) when no port serves the target, or when the port
     *         returns no id. Both messages name the target, because the triager reading the toast
     *         needs to know which register could not take the submission.
     */
    public String promote(String target, IntakePromotionContext ctx) {
        RegisterPromotionPort port = byTarget.get(target);
        if (port == null) {
            throw new IllegalStateException(
                "Intake " + ctx.referenceCode() + " cannot be promoted: this platform has no "
                + "register for '" + target + "'. Supported targets: " + supportedTargets()
                + ". Reject or close the submission instead — marking it promoted would record a "
                + "record that does not exist.");
        }
        String id = port.promote(ctx);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                "Promotion of " + ctx.referenceCode() + " to '" + target + "' returned no record id ("
                + port.getClass().getName() + "). The submission has NOT been promoted.");
        }
        return id;
    }
}
