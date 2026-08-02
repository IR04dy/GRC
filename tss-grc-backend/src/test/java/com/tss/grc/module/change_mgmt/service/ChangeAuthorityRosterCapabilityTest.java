package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.exception.ChangeAuthorityNotConfiguredException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-96 — the gate predicate must measure whether a committee <em>can decide</em>, not merely
 * whether one is mapped.
 *
 * <p><b>The defect.</b> Measured 2026-08-02 in dev and production alike: committee 13 — the
 * committee org 1's CAB gate points at — had <b>zero ACTIVE voting seats</b> in both databases,
 * and {@code GET /api/v1/changes/governance-health} answered {@code healthy: true}. A committee
 * with no voting seat can never be quorate ({@code computeQuorum} sets {@code quorumMet = 0}
 * whenever {@code totalVoting == 0}), so a referred change routed there is undecidable — while a
 * non-referred one was being recorded as <em>CAB approved</em> in the name of a body with no
 * members. Same shape as A17-D2/F2-87: the report measured the mapping, not the capability.
 *
 * <p><b>Why this test reads SQL text rather than a database.</b> The predicate is a native query
 * string, and the suite is hermetic — {@code mvn -o clean package} runs with no database and no
 * secrets. The live behaviour is proven by probe and by {@code CommitteeGoldenPathIT}; what a unit
 * test can guarantee is the property that made the defect possible in the first place: that there
 * is exactly <b>one</b> definition of "configured", shared by the report and the enforcement path.
 * F2-62 is the precedent — a report and a gate that computed access separately drifted, and each
 * half looked defensible alone.
 */
class ChangeAuthorityRosterCapabilityTest {

    private static final Path SERVICE = Path.of(
        "src/main/java/com/tss/grc/module/change_mgmt/service/ChangeGovernanceHealthService.java");

    private static String source() {
        try {
            return Files.readString(SERVICE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theSharedPredicateRequiresAnActiveVotingSeat() {
        String src = source();
        int at = src.indexOf("CONFIGURED_PREDICATE = ");
        assertThat(at).as("the single shared definition must still exist").isGreaterThan(0);
        String predicate = src.substring(at, src.indexOf("\"\"\";", at));

        assertThat(predicate)
            .as("without a roster clause the predicate answers \"is a committee mapped?\" while the "
              + "screen it feeds claims changes routed there can be authorised (F2-96)")
            .contains("admin.committee_member_terms")
            .contains("renewal_status = 'ACTIVE'")
            .contains("is_voting_member = 1");
        assertThat(predicate)
            .as("the pre-existing clauses must survive — a dissolved committee is still a fault")
            .contains("c.status <> 'DISSOLVED'")
            .contains("g.is_deleted = 0");
    }

    @Test
    void thereIsExactlyOneDefinitionOfConfigured() {
        String src = source();
        // The report (GAP_SQL) and the enforcement path (CONFIGURED_ONE_SQL) must both be formatted
        // FROM the shared constant. Two literal EXISTS blocks would mean two definitions, which is
        // precisely how F2-62's report and gate drifted apart.
        int exists = src.split("EXISTS \\(SELECT 1\\s+FROM admin\\.committee_gate_map", -1).length - 1;
        assertThat(exists)
            .as("the gate-map EXISTS block must appear once, in CONFIGURED_PREDICATE — the report "
              + "and the gate then cannot disagree about what \"configured\" means")
            .isEqualTo(1);
        assertThat(src).contains("CONFIGURED_PREDICATE.formatted(\"m.organisation_id\", \"m.gate\")");
        assertThat(src).contains("CONFIGURED_PREDICATE.formatted(\"?1\", \"?2\")");
    }

    @Test
    void theRosterFaultIsDistinctFromTheOtherTwoBecauseItsRemedyIs() {
        assertThat(ChangeAuthorityNotConfiguredException.ROSTER_EMPTY).isEqualTo("ROSTER_EMPTY");
        assertThat(ChangeAuthorityNotConfiguredException.ROSTER_EMPTY)
            .isNotEqualTo(ChangeAuthorityNotConfiguredException.COMMITTEE_UNAVAILABLE)
            .isNotEqualTo(ChangeAuthorityNotConfiguredException.NOT_MAPPED);

        String src = source();
        assertThat(src)
            .as("the fault must be detected with a narrower query, or a mapped-but-empty committee "
              + "reports as COMMITTEE_UNAVAILABLE and the operator is sent to re-map a correct mapping")
            .contains("COMMITTEE_LIVE_SQL")
            .contains("ROSTER_EMPTY_REMEDY");
        // Matched over concatenation-flattened source: the remedy is built from adjacent string
        // literals, so "Appoint voting " and "members to it" are separated by `" + "` in the file.
        // My first pattern assumed whitespace and failed — the same match-the-wrong-thing mistake
        // this class exists to catch in production code.
        String flat = src.replaceAll("\"\\s*\\+\\s*\"", "");
        assertThat(flat)
            .as("the remedy must name appointing members, and must say re-mapping is not the fix")
            .contains("Appoint voting members to it")
            .contains("Re-mapping the gate is NOT the fix");
    }

    @Test
    void theNarrowFaultIsCheckedBeforeTheBroaderOnes() {
        // Scoped to the method body. COMMITTEE_UNAVAILABLE is also named in the javadoc far above,
        // so searching the whole file compares a comment against code and always "fails".
        String src = source();
        int from = src.indexOf("public String gateConfigurationFault");
        assertThat(from).as("the enforcement predicate's entry point must still exist").isGreaterThan(0);
        String body = src.substring(from, src.indexOf("\n    }", from));

        int roster = body.indexOf("ROSTER_EMPTY");
        int unavailable = body.indexOf("COMMITTEE_UNAVAILABLE");
        assertThat(roster).as("gateConfigurationFault must return ROSTER_EMPTY").isGreaterThan(0);
        assertThat(unavailable).isGreaterThan(0);
        assertThat(roster)
            .as("ROSTER_EMPTY must be returned before COMMITTEE_UNAVAILABLE: every mapped-but-empty "
              + "committee also satisfies \"a row exists\", so the broad test would swallow the "
              + "narrow one and the remedy would name the wrong screen")
            .isLessThan(unavailable);
    }

    @Test
    void theRefusalMessageTellsTheAdministratorWhichOfTheThreeItIs() {
        String changeService = readAll(Path.of(
            "src/main/java/com/tss/grc/module/change_mgmt/service/ChangeManagementService.java"));
        assertThat(changeService)
            .as("requireConfiguredChangeAuthority must branch on the fault. A single message saying "
              + "\"no committee is mapped\" for an empty roster is a dead-end error — the exact "
              + "thing ADR-017 D-2 ruled out when it accepted fail-closed")
            .contains("ChangeAuthorityNotConfiguredException.ROSTER_EMPTY ->")
            .contains("has no ACTIVE voting member");
    }

    private static String readAll(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
