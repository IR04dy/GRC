package com.tss.grc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tss.grc.module.audit_lc.service.FindingService;
import com.tss.grc.module.capa.service.CapaTaskService;

/**
 * F2-370 — the governance dashboards must derive each register's "closed / terminal" set from that
 * register's ONE declared state machine, not hand-type a copy (F2-196/F2-336).
 *
 * <p>Both {@code ThreeLinesService} (3LoD) and {@code AuditCommitteeDashboardService} read
 * {@code capa.tasks} and each used to hand-type {@code List.of("CLOSED", "CANCELLED")} for its
 * "open CAPA" filter — two more copies of exactly the second list {@link CapaTaskService}'s own
 * comment brands "F2-196's defect". {@code ThreeLinesService} likewise hand-typed
 * {@code List.of("CLOSED")} for {@code audit_lc.findings}, a copy of {@link FindingService}'s
 * machine. They now read {@link CapaTaskService#TERMINAL} and {@link FindingService#TERMINAL}, both
 * DERIVED from their {@code ALLOWED} machines, so a new terminal state is reflected in every reader
 * at once rather than silently dropped by a stale copy.
 *
 * <p>Behaviour-identical today (both derived sets equal the values that were hand-typed), so this
 * is drift-proofing, not a count change (F2-283 latent-in-code). RISK stays hand-typed in both
 * dashboards (no {@code Map<state,edges>} machine to derive from) and {@code AuditCommittee}'s
 * FINDING set reads {@code compliance.findings}, which has no declared machine — both filed.
 *
 * <p>Hermetic (F2-320/F2-325): the two derived constants are public and read directly; the wiring
 * is a source scan (F2-205 wiring / F2-216 removal), comments stripped (F2-193). No Spring, no DB.
 */
class GovernanceDashboardTerminalSetTest {

    private static final Path THREE_LINES = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "governance", "threelines", "service", "ThreeLinesService.java");
    private static final Path AUDIT_CTE = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "audit_committee", "dashboard", "service", "AuditCommitteeDashboardService.java");
    private static final Path CAPA_SVC = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "capa", "service", "CapaTaskService.java");
    private static final Path FINDING_SVC = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "audit_lc", "service", "FindingService.java");

    /** Read a source file with line + block comments stripped in ONE alternation (F2-198 order). */
    private static String stripped(Path p) {
        try {
            assertThat(Files.exists(p)).as("%s must exist", p).isTrue();
            return Files.readString(p).replaceAll("(?<!:)//[^\\n]*|/\\*(?s:.)*?\\*/", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── value pins: each derived set equals what the dashboards used to hand-type ──────────────
    //    (F2-225: these are tripwires whose failure is in the FUTURE — if a machine gains a
    //     terminal state, the pin fails and forces a human to confirm the dashboards still read.)

    @Test
    @DisplayName("CapaTaskService.TERMINAL is derived and equals the hand-typed {CLOSED, CANCELLED}")
    void capaTerminalIsDerivedAndBehaviourIdentical() {
        assertThat(CapaTaskService.TERMINAL)
            .as("capa.tasks terminal — the value the two dashboards used to hand-type")
            .containsExactly("CANCELLED", "CLOSED");   // ALLOWED-derived, .sorted()
        assertThat(stripped(CAPA_SVC))
            .as("TERMINAL must be DERIVED from the ALLOWED machine, never a second hand-typed list")
            .containsPattern("public static final List<String> TERMINAL = ALLOWED\\.entrySet\\(\\)")
            .doesNotContainPattern("TERMINAL\\s*=\\s*List\\.of\\(");
    }

    @Test
    @DisplayName("FindingService.TERMINAL is derived over keys∪targets and equals {CLOSED}")
    void findingTerminalIsDerivedAndBehaviourIdentical() {
        assertThat(FindingService.TERMINAL)
            .as("audit_lc.findings terminal — the value ThreeLinesService used to hand-type")
            .containsExactly("CLOSED");
        // Derived over keys∪targets because ALLOWED omits terminal states as keys (a terminal is a
        // target with no entry) and AuditFindingMachineParityTest pins ALLOWED at 4 keys.
        assertThat(stripped(FINDING_SVC))
            .as("TERMINAL must derive over the state vocabulary, not be a hand-typed list")
            .containsPattern("public static final List<String> TERMINAL = java\\.util\\.stream\\.Stream\\.concat")
            .doesNotContainPattern("TERMINAL\\s*=\\s*List\\.of\\(");
    }

    // ── the wiring: the discriminating leg (the pre-fix control drives THIS red) ────────────────

    @Test
    @DisplayName("both dashboards read the derived terminal set, not a hand-typed CAPA/FINDING copy")
    void dashboardsReadTheDerivedTerminalNotAHandTypedCopy() {
        String threeLines = stripped(THREE_LINES);
        assertThat(threeLines)
            .as("3LoD open-CAPA filter must read the machine's derived terminal")
            .contains("CapaTaskService.TERMINAL");
        assertThat(threeLines)
            .as("3LoD open-findings filter must read the machine's derived terminal")
            .contains("FindingService.TERMINAL");
        assertThat(threeLines)
            .as("the hand-typed CAPA copy must be gone (F2-216 removal)")
            .doesNotContain("CAPA_CLOSED_STATUSES");
        assertThat(threeLines)
            .as("the hand-typed FINDING copy must be gone")
            .doesNotContain("FINDING_CLOSED_STATUSES");
        assertThat(threeLines)
            .as("the dead DSR_CLOSED_STATUSES constant (F2-196 orphaned it) must be gone")
            .doesNotContain("DSR_CLOSED_STATUSES");

        String auditCte = stripped(AUDIT_CTE);
        assertThat(auditCte)
            .as("Audit Committee open-CAPA filter must read the machine's derived terminal")
            .contains("CapaTaskService.TERMINAL");
        assertThat(auditCte)
            .as("the hand-typed CAPA copy must be gone here too")
            .doesNotContain("CAPA_CLOSED_STATUSES");
    }

    // ── the boundary: what stays hand-typed, and why (F2-188 filed, asserted so it is not lost) ──

    @Test
    @DisplayName("RISK and compliance.findings stay hand-typed because they have no derivable machine")
    void registersWithoutADeclaredMachineStayHandTyped() {
        // RISK: risk has no Map<state,edges> ALLOWED (its lifecycle is hand-coded transition
        // methods), so there is no single terminal set to derive. Both dashboards keep a hand-typed
        // RISK_CLOSED_STATUSES — asserted here so a future reader sees it is a decision, not an
        // oversight (the boundary of this slice).
        assertThat(stripped(THREE_LINES))
            .as("RISK stays hand-typed in 3LoD")
            .contains("RISK_CLOSED_STATUSES");
        // AuditCommittee's FINDING set reads compliance.findings (AuditFinding), which has no
        // declared machine — whether RESOLVED is terminal is an owner/data question (filed).
        assertThat(stripped(AUDIT_CTE))
            .as("compliance.findings closed-set stays hand-typed in Audit Committee")
            .contains("FINDING_CLOSED_STATUSES");
    }
}
