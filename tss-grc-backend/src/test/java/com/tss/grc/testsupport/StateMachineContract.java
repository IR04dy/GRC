package com.tss.grc.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * F2-03's shared state-machine contract: a lifecycle service must permit exactly the transitions
 * its own map declares, and refuse every other pair.
 *
 * <h3>Why this is shared rather than written per module</h3>
 * F2-03 covers seven regulator-facing modules and the first one alone had four machines. Writing
 * the loop seven times would produce seven chances to write it subtly differently — and the first
 * version, shipped for {@code outsourcing}, already contained the defect this class exists to fix.
 *
 * <h3>⚠ The universe of states is keys ∪ values, and getting that wrong makes the check go blind</h3>
 * The first version iterated {@code allowed.keySet()} for both the source and the target state. That
 * is only complete when every state appears as a key. It held for {@code outsourcing} <b>by
 * accident</b> — all four of its maps happen to declare terminal states as explicit
 * {@code Set.of()} entries — and it fails immediately for {@code nca_ecc}, whose map has four keys
 * while six states exist: {@code ACKNOWLEDGED} and {@code CANCELLED} appear only as targets. Under
 * the old loop neither would have been tested as a target, and neither would have been tested as a
 * source, so <b>terminal behaviour on the two states that actually end an NCA filing would have
 * been unverified while the test reported green</b>. A gate that can pass while the property it
 * stands for is unchecked is not a gate — the same argument that earned
 * {@code BASELINE_MAPPED_SCHEMAS_WITHOUT_MIGRATION} its own separate ratchet.
 *
 * <p>So the universe is every key plus every value, and a state reachable only as a target is
 * covered from the moment it appears in the map.
 */
public final class StateMachineContract {

    private StateMachineContract() {}

    /**
     * @param permitted number of declared transitions exercised and accepted
     * @param refused   number of undeclared pairs exercised and refused
     * @param states    the full state universe that was derived
     */
    public record Result(int permitted, int refused, Set<String> states) {}

    /**
     * Drive every ordered pair of distinct states through the service and compare the outcome with
     * the map.
     *
     * @param allowed        the service's own transition map, read by reflection — never restated,
     *                       because a restatement is a second authority that can drift (RC-A)
     * @param seedAtState    put the subject into the given state (typically: build an entity and
     *                       stub the repository lookup)
     * @param attempt        ask the service to transition to the given state
     * @param refusalMarker  a substring the refusal message must contain, so a refusal for an
     *                       unrelated reason (a null, a missing mock) is not mistaken for the
     *                       machine working
     * @param label          the machine's name, for failure messages
     */
    public static Result assertHonoursItsMap(Map<String, Set<String>> allowed,
                                            Consumer<String> seedAtState,
                                            Consumer<String> attempt,
                                            String refusalMarker,
                                            String label) {
        return assertHonoursItsMap(allowed, seedAtState, attempt, Set.of(refusalMarker), label);
    }

    /**
     * As above, but accepting <b>several</b> legitimate refusal messages.
     *
     * <p>⚠ Added for {@code rrp}, the seventh module through this harness, and the reason generalises.
     * A service may refuse an undeclared pair for more than one correct reason: {@code RrpService}
     * intercepts {@code to == SUBMITTED} <em>before</em> consulting its map and answers <em>"Use the
     * dedicated /submit endpoint to lodge with SAMA"</em>, because lodging captures a regulator
     * acknowledgement the generic DTO cannot carry. That is a correct refusal with a different
     * message, and the single-marker form reported it as <b>"refused for the WRONG reason"</b> — a
     * false positive that would have pushed a test author to weaken the marker to something vacuous.
     *
     * <p>Insisting on <em>a</em> recognised reason rather than <em>one</em> reason keeps the check
     * strict: an unrelated failure (a null, a missing mock) still fails, because its message matches
     * none of them.
     */
    public static Result assertHonoursItsMap(Map<String, Set<String>> allowed,
                                            Consumer<String> seedAtState,
                                            Consumer<String> attempt,
                                            Set<String> refusalMarkers,
                                            String label) {
        assertThat(allowed).as("%s: the transition map must not be empty", label).isNotEmpty();
        assertThat(refusalMarkers).as("%s: at least one refusal marker is required, or the refusal half of this contract checks nothing", label).isNotEmpty();

        Set<String> states = new TreeSet<>(allowed.keySet());
        allowed.values().forEach(states::addAll);          // ← the fix; see the class javadoc
        assertThat(states).as("%s: derived state universe", label).hasSizeGreaterThan(2);

        List<String> wrong = new ArrayList<>();
        int permitted = 0;
        int refused = 0;

        for (String from : states) {
            for (String to : states) {
                if (from.equals(to)) {
                    continue;                              // self-transition is not modelled
                }
                boolean declared = allowed.getOrDefault(from, Set.of()).contains(to);
                seedAtState.accept(from);
                if (declared) {
                    permitted++;
                    try {
                        attempt.accept(to);
                    } catch (RuntimeException ex) {
                        wrong.add(from + " -> " + to + " is DECLARED but was refused: "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                } else {
                    refused++;
                    try {
                        attempt.accept(to);
                        wrong.add(from + " -> " + to + " is NOT declared but was PERMITTED");
                    } catch (IllegalStateException expected) {
                        // IllegalTransitionException extends IllegalStateException, so both the
                        // structured and the plain refusal land here — but the message still has to
                        // say it was the machine that refused.
                        String msg = String.valueOf(expected.getMessage());
                        if (refusalMarkers.stream().noneMatch(msg::contains)) {
                            wrong.add(from + " -> " + to + " was refused for the WRONG reason "
                                + "(expected a message containing one of " + refusalMarkers + "): "
                                + msg);
                        }
                    }
                }
            }
        }

        assertThat(wrong).as(
            "%s: %d declared transitions and %d undeclared pairs checked over states %s.%n  %s",
            label, permitted, refused, states, String.join("\n  ", wrong)).isEmpty();
        assertThat(permitted).as(
            "%s: no declared transition was exercised — the map read empty and this contract would "
            + "pass while checking nothing", label).isGreaterThan(0);
        assertThat(refused).as(
            "%s: no undeclared pair was exercised, so the refusal half of the machine — which is "
            + "the half a hand-written test set omits — is unverified", label).isGreaterThan(0);

        return new Result(permitted, refused, states);
    }
}
