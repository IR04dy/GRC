package com.tss.grc.module.ethics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-290 — the behaviour of the one open/closed predicate for {@code ethics.cases}.
 *
 * <p><b>Why this is a static test and not a service test.</b> The predicate the readers call is
 * {@code EthicsCaseRepository.countOpen}, a {@code default} method — and <b>a Mockito mock does not
 * execute default methods</b> (F2-282 found that the hard way: a decision placed on a mockable
 * interface is a decision every test that mocks that interface silently loses). So the split here is
 * deliberate and is the reconciliation of two recorded rules that pull in opposite directions:
 *
 * <ul>
 *   <li>the <b>decision</b> — which states are terminal — lives in {@link EthicsCaseLifecycle},
 *       a pure static, so it is mock-immune and is asserted here;</li>
 *   <li>the <b>query</b> — count the rows not in that set — lives on the repository default,
 *       because it must touch the database and because F2-196 established that importing a
 *       business module's lifecycle into {@code module/reporting} is an ADR-002 inversion
 *       {@code ModuleGraphRatchetTest} correctly refuses. That default holds no judgement: it
 *       names the constant and delegates.</li>
 * </ul>
 *
 * <p>The delegation is asserted structurally by {@code EthicsCaseOpenPredicateContractTest} and
 * end-to-end by the live probe, which is the only thing that can see a repository default at all.
 */
class EthicsCaseLifecycleTest {

    @Test
    @DisplayName("the terminal set is exactly the states with no outgoing edges")
    void terminalIsDerived() {
        assertThat(EthicsCaseLifecycle.TERMINAL).containsExactlyInAnyOrder("CLOSED", "DISMISSED");
        for (String s : EthicsCaseLifecycle.STATES) {
            boolean noEdges = EthicsCaseLifecycle.ALLOWED.getOrDefault(s, Set.of()).isEmpty();
            assertThat(EthicsCaseLifecycle.isTerminal(s))
                .as("%s has %s outgoing edges, so isTerminal must be %s",
                    s, noEdges ? "no" : "some", noEdges)
                .isEqualTo(noEdges);
        }
    }

    @Test
    @DisplayName("an unrecognised status counts as OPEN, not as closed")
    void anUnrecognisedStatusIsOpen() {
        // The live register holds INVESTIGATING (8 rows) and TRIAGED (3), near-synonyms of two
        // declared states, under no CHECK constraint. Counting them as open is the safe direction
        // for a misconduct caseload: an inclusion list dropped them and reported 10 of 26.
        assertThat(EthicsCaseLifecycle.isOpen("INVESTIGATING")).isTrue();
        assertThat(EthicsCaseLifecycle.isOpen("TRIAGED")).isTrue();
        assertThat(EthicsCaseLifecycle.isOpen("SOMETHING_NOBODY_DECLARED")).isTrue();
        assertThat(EthicsCaseLifecycle.isOpen(null))
            .as("a null status is not a closure either")
            .isTrue();
    }

    @Test
    @DisplayName("the comparison normalises casing, because the column is case-insensitive")
    void comparisonNormalisesCasing() {
        // Latin1_General_100_CI_AS_SC_UTF8 with no CHECK, so 'closed' is storable and a bare
        // equals would call it open — the F2-179 family.
        assertThat(EthicsCaseLifecycle.isTerminal("closed")).isTrue();
        assertThat(EthicsCaseLifecycle.isTerminal(" Dismissed ")).isTrue();
        assertThat(EthicsCaseLifecycle.allowedTargets("intake")).contains("TRIAGE");
    }

    @Test
    @DisplayName("a terminal or unrecognised state offers no transition — the stuck rows STAY stuck")
    void terminalAndUnknownStatesOfferNothing() {
        assertThat(EthicsCaseLifecycle.allowedTargets("CLOSED")).isEmpty();
        assertThat(EthicsCaseLifecycle.allowedTargets("DISMISSED")).isEmpty();

        // Asserted so nobody reads F2-290's disclosure as having fixed F2-221's finding. The 11
        // rows holding INVESTIGATING / TRIAGED still cannot be advanced by ANY route: they are now
        // COUNTED and SURFACED, which is what lets an owner see the population, and collapsing the
        // synonyms rewrites live rows in two real databases, which is the owner's decision.
        assertThat(EthicsCaseLifecycle.allowedTargets("INVESTIGATING")).isEmpty();
        assertThat(EthicsCaseLifecycle.allowedTargets("TRIAGED")).isEmpty();
    }

    @Test
    @DisplayName("every target the machine names is also a declared state")
    void theMachineIsClosedOverItsOwnTargets() {
        for (Set<String> targets : EthicsCaseLifecycle.ALLOWED.values()) {
            assertThat(EthicsCaseLifecycle.ALLOWED.keySet())
                .as("a target with no entry of its own would be silently terminal")
                .containsAll(targets);
        }
        assertThat(EthicsCaseLifecycle.STATES).hasSize(EthicsCaseLifecycle.ALLOWED.size());
        assertThat(EthicsCaseLifecycle.INITIAL).isIn(EthicsCaseLifecycle.STATES);
        assertThat(EthicsCaseLifecycle.isOpen(EthicsCaseLifecycle.INITIAL))
            .as("a newly filed case must be open")
            .isTrue();
    }
}
