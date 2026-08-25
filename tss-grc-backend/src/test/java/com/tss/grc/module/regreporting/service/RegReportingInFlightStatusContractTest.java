package com.tss.grc.module.regreporting.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-364 — the SAMA regulatory-reporting dashboard's "in flight" set must be the COMPLEMENT
 * of the declared terminal set, never a hand-typed subset.
 *
 * <p>{@code IN_FLIGHT_STATUSES} was a hand-typed list that dropped {@code ACKNOWLEDGED} — a
 * declared non-terminal state ({@code ACKNOWLEDGED → CLOSED}) — so a filing the regulator has
 * acknowledged but that is not yet CLOSED was absent from {@code inFlightFilingCount} and could
 * never appear in the {@code inFlightByStatus} / {@code inFlightByRegulator} breakdowns. The
 * platform rule (F2-196/F2-336/F2-343/F2-361): "open/in-flight" is the complement of the
 * declared terminal set, and the terminal set is derived from the machine, never typed twice.
 *
 * <p>Hermetic — reflection over the static constants, no Spring context, no DB (the query's
 * row selection is SQL; this pins the vocabulary the query is handed).
 */
class RegReportingInFlightStatusContractTest {

    @SuppressWarnings("unchecked")
    private static <T> T field(String name) {
        try {
            Field f = RegReportingService.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void terminalSetIsExactlyTheMachinesEmptyOutgoingStates() {
        Map<String, Set<String>> allowed = field("ALLOWED");
        Set<String> terminal = field("TERMINAL_STATUSES");

        Set<String> derivedTerminal = allowed.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        assertThat(terminal)
                .as("TERMINAL_STATUSES must equal the declared states with no outgoing transition")
                .isEqualTo(derivedTerminal);
    }

    @Test
    void inFlightIsExactlyTheComplementOfTheTerminalSet() {
        Map<String, Set<String>> allowed = field("ALLOWED");
        Set<String> terminal = field("TERMINAL_STATUSES");
        Set<String> inFlight = field("IN_FLIGHT_STATUSES");

        Set<String> complement = new HashSet<>(allowed.keySet());
        complement.removeAll(terminal);

        assertThat(inFlight)
                .as("IN_FLIGHT_STATUSES must be every declared state that is not terminal — "
                        + "the complement, never a hand-typed subset that can silently drop a state")
                .isEqualTo(complement);
    }

    @Test
    void acknowledgedIsInFlight_notDropped() {
        Map<String, Set<String>> allowed = field("ALLOWED");
        Set<String> inFlight = field("IN_FLIGHT_STATUSES");
        Set<String> terminal = field("TERMINAL_STATUSES");

        // ACKNOWLEDGED is declared non-terminal (ACKNOWLEDGED → CLOSED), so an acknowledged
        // filing awaiting closure is still open regulatory-reporting work.
        assertThat(allowed.get("ACKNOWLEDGED"))
                .as("ACKNOWLEDGED must be a declared non-terminal state")
                .isNotNull().isNotEmpty();
        assertThat(terminal).doesNotContain("ACKNOWLEDGED");
        assertThat(inFlight)
                .as("an ACKNOWLEDGED-awaiting-closure filing must stay visible in the in-flight view")
                .contains("ACKNOWLEDGED");
    }
}
