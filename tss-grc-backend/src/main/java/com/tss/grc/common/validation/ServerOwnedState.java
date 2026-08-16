package com.tss.grc.common.validation;

import java.util.Set;
import java.util.TreeSet;

/**
 * F2-218 — a record is created in its register's <b>initial state</b>, and never anywhere else.
 *
 * <p>This is F2-217's {@code initialStatusOnly}, promoted out of {@code AuditLifecycleService} the
 * first time a second register needed it. The alternative was a second copy, and this codebase's
 * most repeated defect is exactly that: five CAPA vocabularies, four DSR registers, three ECC
 * control registers — each one a correct idea written down twice and then allowed to drift.
 *
 * <h2>Why a create naming its own state is a defect</h2>
 *
 * <p>Every register here guards its transitions: a permission on the {@code /approve} route, a
 * state machine that answers 409 for an illegal edge, a maker/checker check, an actor stamped from
 * the authenticated principal, an audit row naming the from- and to-states. A create that accepts
 * {@code status} from the caller reaches the same end state with none of it — so the guard is not a
 * control, it is a route that happens to be guarded, and the unguarded route is the one nobody is
 * looking at.
 *
 * <p>Measured live 2026-08-15 as {@code ranalyst} (holds {@code NDI:WRITE}, <b>not</b>
 * {@code NDI:APPROVE}): {@code POST /api/ndi/evidence/1/approve} answered <b>403</b>, and
 * {@code POST /api/ndi/evidence} with {@code status=ACTIVE} plus a chosen {@code reviewerName} and
 * a 2001 {@code reviewedAt} answered <b>200</b> and stored all three — on a register whose
 * {@code approve()} enforces that the evidence owner may not approve their own item.
 *
 * <h2>Refuse, do not coerce</h2>
 *
 * <p>F2-182's test — <i>what is already refusing this?</i> — settles the choice. The create already
 * rejects anything outside the register's vocabulary (a CHECK constraint, a validator, or both), so
 * every value that succeeds today is a declared state of that register. The only requests this
 * newly refuses are the ones naming a <b>non-initial</b> declared state, i.e. requests performing a
 * transition the {@code /transition} route would have adjudicated, attributed and recorded.
 * <b>A request that performs an unguarded transition is not a working request.</b>
 *
 * <p>Coercing instead — quietly rewriting the state to the initial one — would answer 200 to a
 * caller who asked to approve something, which is the worse failure: it looks like it worked.
 *
 * <h2>What it is not</h2>
 *
 * <p>Server-<b>derived</b> state is untouched. A CAPA whose target date has passed may be created
 * OVERDUE, and NDI evidence whose expiry date has passed may be created EXPIRED, because in both
 * cases the server computed the state from data the caller legitimately supplied. The rule is that
 * the <i>caller</i> may not name the state, not that the create always lands in the initial one —
 * a distinction that is easy to lose and is asserted in both directions by the callers' tests.
 */
public final class ServerOwnedState {

    private ServerOwnedState() { }

    /**
     * Returns {@code initial}, or refuses a caller trying to start the record somewhere else.
     *
     * @param supplied what arrived on the request; {@code null} or blank means "not stated"
     * @param initial  the register's declared initial state — the SAME constant the create uses as
     *                 its default, so the value the server picks and the value it accepts cannot
     *                 drift apart. Passing a literal here instead of that constant reintroduces the
     *                 two-literals defect this exists to remove.
     * @param register a human name for the thing being created, carrying its own article
     *                 ("an annual plan", "a working paper"). F2-217 built the article in
     *                 separately and produced "a annual plan" in a 400 body, which is the first
     *                 thing the caller reads.
     * @throws IllegalArgumentException naming the field, the state, and the route that can perform
     *         the transition the caller was attempting
     */
    public static String requireInitial(String supplied, String initial, String register) {
        return requireInitial(supplied, initial, register, "the transition route for this register");
    }

    /**
     * As {@link #requireInitial(String, String, String)}, naming the route that <i>can</i> perform
     * the transition the caller was attempting.
     *
     * <p>Worth the extra argument wherever the route is known: a 400 that says only "you may not do
     * that" leaves the caller to search, and the whole reason this refuses rather than coerces is
     * to send them somewhere. F2-180's rule — a wrong-but-specific diagnostic does not slow the
     * reader down, it sends them somewhere — applies twice over to a right one.
     *
     * @param route e.g. {@code "POST /{externalId}/transition"} or {@code "POST /{id}/approve"}
     */
    public static String requireInitial(String supplied, String initial, String register,
                                        String route) {
        if (supplied == null || supplied.isBlank() || supplied.equals(initial)) {
            return initial;
        }
        throw new IllegalArgumentException(
            "status: " + register + " is created in " + initial + ". '" + supplied + "' has to be "
            + "reached through " + route + ", which checks the state machine and records who made "
            + "the change. Send status=" + initial + ", or omit it.");
    }

    /**
     * F2-220 — as {@link #requireInitial}, for the register where <b>more than one</b> state is a
     * legitimate starting point.
     *
     * <p>⚠ Reach for this only when {@code requireInitial} would refuse a request that works today.
     * There is exactly one such register: {@code platform.authority_documents} is a <i>library</i>
     * of regulatory instruments whose create defaults to ACTIVE — most documents are simply in
     * force — while its machine's root is DRAFT, for one being prepared. Both are entry states and
     * both are accepted by the API today; DEPRECATED and SUPERSEDED are lifecycle <i>outcomes</i>,
     * and SUPERSEDED's transition demands a successor document it verifies exists and is not the
     * document itself. Forcing one initial state there would have broken a working create, which is
     * the failure F2-182's test exists to prevent — so the rule bends for the measurement, not the
     * measurement for the rule.
     *
     * <p>🔑 This is deliberately <b>not</b> the general form. A per-register list of allowed states
     * is exactly the artefact F2-217 found measuring its own list rather than the codebase; the
     * default is one initial state, and every use of this overload owes a recorded reason.
     *
     * @param creatable states this register may be created in; must contain {@code initial}
     */
    public static String requireCreatable(String supplied, Set<String> creatable, String initial,
                                          String register, String route) {
        if (!creatable.contains(initial)) {
            throw new IllegalStateException(
                "Register '" + register + "' declares initial state " + initial
                + ", which is not in its creatable set " + new TreeSet<>(creatable)
                + ". The state the server picks and the states it accepts cannot disagree.");
        }
        if (supplied == null || supplied.isBlank()) {
            return initial;
        }
        if (creatable.contains(supplied)) {
            return supplied;
        }
        throw new IllegalArgumentException(
            "status: " + register + " is created in " + new TreeSet<>(creatable) + ". '" + supplied
            + "' has to be reached through " + route + ", which checks the state machine and "
            + "records who made the change. Send status=" + initial + ", or omit it.");
    }
}
