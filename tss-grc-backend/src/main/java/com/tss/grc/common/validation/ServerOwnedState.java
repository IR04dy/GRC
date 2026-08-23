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
        return requireInitial(supplied, initial, register, route, "status");
    }

    /**
     * F2-296 — as {@link #requireInitial(String, String, String, String)}, naming the field the
     * caller actually sent.
     *
     * <p>⚠⚠ <b>The 4-argument form hard-coded {@code "status: "}, and three callers do not write a
     * field called {@code status}</b> — {@code BcmComplianceMappingService} sets
     * {@code complianceStatus}, {@code ContinuityStrategyService} {@code approvalStatus},
     * {@code ControlLibraryService} {@code workflowStatus}. So a caller who sent
     * {@code approvalStatus=APPROVED} to {@code POST /api/v1/bcm/strategies} read a 400 whose first
     * token named {@code status}: a field that request does not carry and the DTO does not declare.
     * F2-210's shape — a diagnostic that sends the reader somewhere wrong — in the message of the
     * primitive built to send them somewhere right.
     *
     * <p>🔑 <b>That is the recorded reason the fourth copy of this rule exists.</b>
     * {@code BcmCycleService.requireInitialState}'s own javadoc documents its {@code field}
     * parameter as <i>"the field name, so the message names what the caller actually sent"</i> — so
     * the divergence had a cause, written down, in the copy. A primitive that cannot express what a
     * caller needs is a primitive callers route around (F2-190).
     *
     * <p>⚠ <b>Comparison is case-INSENSITIVE, and that is a widening.</b> Every column this guards
     * collates {@code Latin1_General_100_CI_AS_SC_UTF8}, so a bare {@code equals} is not a
     * comparison (F2-179): {@code 'planned'} was refused with a 400 while the column would have
     * stored it verbatim. It now returns the CANONICAL {@code initial}, so the boundary normalises
     * on write rather than refusing a spelling the database accepts — the same rule
     * {@code ClosedVocabulary.require} follows, and the reasoning
     * {@code DisclosureService.requireStatusIsNotBodySet} had already written down for the update
     * half while this create half kept a bare equals (F2-215's two-of-N, across one question).
     *
     * @param field the request field being guarded, so the 400 names what the caller sent
     */
    public static String requireInitial(String supplied, String initial, String register,
                                        String route, String field) {
        return requireInitial(supplied, initial, register, route, field, STATE_MACHINE_AND_ACTOR);
    }

    /**
     * What the route guarantees, for the refusal below. This is what almost every caller's route
     * does, so it is the default the five-argument form passes.
     */
    public static final String STATE_MACHINE_AND_ACTOR =
        "which checks the state machine and records who made the change";

    /**
     * ⚠⚠ F2-302 — the six-argument form exists because the clause after the route is a <b>claim
     * about the caller's own route</b>, and the shared sentence was false for the first register
     * that did not have a machine. {@code ndi.gap} has no transition endpoint, no declared edges and
     * its PUT records no actor, so telling an operator the state "has to be reached through PUT
     * …/{id}, which checks the state machine and records who made the change" sends them to a
     * mechanism that does not exist — [[F2-210]]'s defect inverted, and the same shape [[F2-296]]
     * found one phrase over when this primitive hard-coded {@code "status: "} and three callers
     * emitted a 400 naming a field their request never carried.
     *
     * <p>🔑 <b>A primitive that cannot express what a caller needs is a primitive callers route
     * around</b> ([[F2-190]]), and the divergence is then invisible. Pass the clause; do not
     * hand-roll a twenty-ninth refusal.
     */
    public static String requireInitial(String supplied, String initial, String register,
                                        String route, String field, String routeGuarantee) {
        if (supplied == null || supplied.isBlank() || same(supplied, initial)) {
            return initial;
        }
        throw new IllegalArgumentException(
            field + ": " + register + " is created in " + initial + ". '" + supplied + "' has to be "
            + "reached through " + route + ", " + routeGuarantee
            + ". Send " + field + "=" + initial + ", or omit it.");
    }

    /**
     * F2-296 — the UPDATE half: a lifecycle field an update may <b>echo</b> and may not
     * <b>change</b>.
     *
     * <p>This rule was written twice before it was written once. {@code DisclosureService} and
     * {@code EthicsCaseService} each hand-rolled it — same module, same package — and
     * {@code compliance.audit_engagements} had no copy at all, so its entity-bound {@code PUT}
     * copied whatever status the caller sent, with <b>no vocabulary check and no direction check</b>,
     * beside an {@code /advance} route that is explicitly forward-only. F2-294 closed that
     * register's create and filed it as having <i>"closed its only open writers"</i>; the update was
     * still open, which is why the third register needed the rule and not a third copy of it.
     *
     * <h2>Why an echo has to be tolerated</h2>
     *
     * <p>Every one of these screens seeds its edit modal from the row — {@code engagementToReq(e)}
     * carries {@code status: e.status}, {@code reqFromDto(c)} spreads the whole DTO — and the form
     * submits its whole seeded state. None of the three forms offers a status INPUT, so the value
     * arriving is always the stored one. Refusing a present value therefore breaks the screen that
     * works today, which is the one failure a fix must never have (F2-214); refusing a CHANGE is the
     * whole point, because that is the unguarded transition.
     *
     * <p>Silently dropping a change instead would answer 200 to a caller who asked to move the
     * record, which is worse than refusing (F2-44, F2-184's shape at a response).
     *
     * <h2>Case-insensitively, for the reason the two copies disagreed about</h2>
     *
     * <p>{@code DisclosureService} compared through a {@code normalise} helper and explained why:
     * the column collates CI and the table carries <b>zero CHECK constraints</b>, so
     * {@code 'approved'} is storable and a bare equals would refuse an echo differing only in case.
     * {@code EthicsCaseService}, in the same package, used a bare {@code equals} on both
     * {@code status} and {@code outcome}. 🔑 <b>The later slice reasoned the question out, applied
     * the answer to its own copy, and left the sibling's — F2-215's two-of-N, where the reasoning is
     * visibly present in the module and covers one of the two files.</b> Measured 2026-08-23: 0
     * non-canonical values across all four guarded columns in dev and prod, so the disagreement was
     * <b>latent</b> — a fact about today, not a property of the control (F2-182).
     *
     * @param supplied what arrived on the request; {@code null} or blank means "not stated"
     * @param current  the value on the loaded row — or the register's initial state on a create,
     *                 or {@code null} for a field a create may not state at all
     * @param field    the request field, so the 400 names what the caller sent
     * @param record   how to refer to the record in the message, id included where there is one
     *                 ("Engagement 7", "a new ethics case")
     * @param route    the endpoint that CAN perform this move, and what it records
     * @param allowed  optional trailing sentence naming the legal targets from {@code current};
     *                 {@code null} to omit it
     * @throws IllegalArgumentException naming the field, both states, and the route that owns the
     *         move
     */
    public static void requireUnchanged(String supplied, String current, String field,
                                        String record, String route, String allowed) {
        if (supplied == null || supplied.isBlank() || same(supplied, current)) {
            return;
        }
        // ⚠ The wording is the two hand-rolled copies' wording, deliberately. Both are asserted by
        // tests (EntityBoundPartialUpdateTest, EthicsCaseServiceTest) and a caller's integration may
        // be matching on it, so extracting a shared rule must not silently reword the 400 it emits:
        // that would be a behaviour change smuggled into a de-duplication.
        throw new IllegalArgumentException(
            field + ": " + record
            + (current == null || current.isBlank()
                ? " has no " + field + " yet and an update cannot set one"
                : " is at " + current + " and cannot be moved to " + supplied + " by an update")
            + ". That move goes through " + route + "."
            + (allowed == null || allowed.isBlank() ? "" : " " + allowed));
    }

    /**
     * Case-insensitive, blank-tolerant comparison of two stored lifecycle tokens.
     *
     * <p>Not {@code equalsIgnoreCase} on the raw values: a stored token can carry surrounding
     * whitespace, and every caller here is comparing a request value against a column value.
     */
    private static boolean same(String a, String b) {
        return normalise(a).equals(normalise(b));
    }

    /**
     * F2-296 — the same comparison, exposed for the create halves that keep their own message.
     *
     * <p>It is public so that a caller which cannot use {@link #requireInitial} (because its 400 text
     * is asserted, or because it refuses for a second reason as well) still does not hand-roll a
     * {@code trim().toUpperCase()} helper. Two of those existed before this slice and they disagreed
     * with a third bare {@code equals} — which is how a rule ends up with three implementations and
     * no owner (F2-177).
     */
    public static boolean sameState(String a, String b) {
        return same(a, b);
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toUpperCase(java.util.Locale.ROOT);
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
        // F2-296 — matched case-insensitively and returned in its CANONICAL spelling, for the same
        // reason as requireInitial and requireUnchanged. Leaving this one case-sensitive while its
        // two siblings normalise would ship F2-215's two-of-N inside the class whose whole subject
        // is "one question, one answer" — and the caller's own column collates CI, so a bare
        // contains() would refuse a spelling the database stores (F2-179).
        for (String member : creatable) {
            if (same(supplied, member)) {
                return member;
            }
        }
        throw new IllegalArgumentException(
            "status: " + register + " is created in " + new TreeSet<>(creatable) + ". '" + supplied
            + "' has to be reached through " + route + ", which checks the state machine and "
            + "records who made the change. Send status=" + initial + ", or omit it.");
    }
}
