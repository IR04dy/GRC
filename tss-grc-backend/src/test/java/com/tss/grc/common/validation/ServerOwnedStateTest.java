package com.tss.grc.common.validation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The refusal primitive itself — F2-218 introduced it, F2-220 gave it a second overload and this
 * test.
 *
 * <p>It had none before, which is worth saying out loud: {@code ServerStampedFieldContractTest}
 * exempts any value passing through {@code ServerOwnedState.require*} from its whole measurement,
 * so a primitive that stopped refusing would empty the population of both gates and every
 * remaining site would read as closed. 🔑 <b>An exemption keyed on a named primitive is only as
 * sound as that primitive, and nothing was checking it.</b> The gate's own
 * {@code theRefusalPrimitiveIsResolvedNotAssumed} checks the class exists and throws; these are
 * the semantics.
 */
class ServerOwnedStateTest {

    private static final String ROUTE = "POST /{id}/transition";

    // ── requireInitial: one initial state, used by eight of F2-220's nine registers ──

    @Test
    void anAbsentStateBecomesTheInitialOne() {
        assertThat(ServerOwnedState.requireInitial(null, "DRAFT", "a plan", ROUTE)).isEqualTo("DRAFT");
        assertThat(ServerOwnedState.requireInitial("", "DRAFT", "a plan", ROUTE)).isEqualTo("DRAFT");
        assertThat(ServerOwnedState.requireInitial("   ", "DRAFT", "a plan", ROUTE)).isEqualTo("DRAFT");
    }

    @Test
    void namingTheInitialStateExplicitlyIsStillAWorkingRequest() {
        assertThat(ServerOwnedState.requireInitial("DRAFT", "DRAFT", "a plan", ROUTE))
            .as("a client that echoes the default back must not start failing — the only requests "
                + "this may newly refuse are ones performing an unguarded transition")
            .isEqualTo("DRAFT");
    }

    @Test
    void anyOtherStateIsRefusedAndTheMessageSaysWhereToGo() {
        assertThatThrownBy(
            () -> ServerOwnedState.requireInitial("PUBLISHED", "DRAFT", "a continuity plan", ROUTE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PUBLISHED")
            .hasMessageContaining("DRAFT")
            .hasMessageContaining("a continuity plan")
            .as("refusing rather than coercing is only defensible if the 400 sends the caller "
                + "somewhere — F2-180's rule about a specific diagnostic")
            .hasMessageContaining(ROUTE);
    }

    @Test
    void theDefaultOverloadNamesAGenericRouteRatherThanNone() {
        assertThatThrownBy(() -> ServerOwnedState.requireInitial("CLOSED", "OPEN", "an issue"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transition route");
    }

    // ── requireCreatable: F2-220, for the register with two entry states ────

    @Test
    void aCreatableStateIsKeptAndAnAbsentOneBecomesTheDefault() {
        Set<String> creatable = Set.of("DRAFT", "ACTIVE");
        assertThat(ServerOwnedState.requireCreatable(null, creatable, "ACTIVE", "a doc", ROUTE))
            .isEqualTo("ACTIVE");
        assertThat(ServerOwnedState.requireCreatable("DRAFT", creatable, "ACTIVE", "a doc", ROUTE))
            .as("the whole reason this overload exists: DRAFT is a legitimate create on the "
                + "authority-document library and requireInitial would have refused it")
            .isEqualTo("DRAFT");
    }

    @Test
    void aStateOutsideTheCreatableSetIsRefused() {
        assertThatThrownBy(() -> ServerOwnedState.requireCreatable(
            "SUPERSEDED", Set.of("DRAFT", "ACTIVE"), "ACTIVE", "an authority document", ROUTE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SUPERSEDED")
            .hasMessageContaining(ROUTE);
    }

    /**
     * The self-check M16 targets.
     *
     * <p>F2-217's rule is that the value the server picks and the values it accepts come from one
     * place so they cannot drift. This overload takes them as two arguments, which reopens exactly
     * that gap — so it closes it itself, loudly, rather than silently defaulting a register into a
     * state it would then refuse on the very next request.
     */
    @Test
    void aDefaultOutsideItsOwnCreatableSetIsAProgrammingErrorAndSaysSo() {
        assertThatThrownBy(() -> ServerOwnedState.requireCreatable(
            null, Set.of("DRAFT"), "ACTIVE", "an authority document", ROUTE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACTIVE")
            .hasMessageContaining("cannot disagree");
    }
}
