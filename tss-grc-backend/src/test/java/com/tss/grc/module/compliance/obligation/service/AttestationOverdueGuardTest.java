package com.tss.grc.module.compliance.obligation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-191 — the attestation guard declared OVERDUE unreachable while three writers reached it daily.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@code guardAttest} was a {@code switch} whose arms lumped {@code DUE}, {@code OVERDUE} and
 * {@code IN_PROGRESS} together and allowed <b>none</b> of them to become {@code OVERDUE}. Meanwhile
 * three writers set it directly, none of them calling the guard: {@code markOverdueForCampaign},
 * {@code sweepOverdueForOrg} (daily, via {@code ComplianceDeadlineScheduler}) and
 * {@code ObligationCampaignService#escalateOverdue}. Three artefacts, three answers — and the class
 * javadoc's own state machine, which says {@code DUE → … | OVERDUE}, was the only one that was right.
 *
 * <p>Two consequences that reading one file could not show:
 *
 * <ul>
 *   <li><b>The event lied about the previous status.</b> Both sweeps called
 *       {@code emit("OBLIGATION_OVERDUE", a, "DUE")} with the literal {@code "DUE"}, while their own
 *       candidate query selects {@code DUE} <em>or</em> {@code IN_PROGRESS}. So a cycle an officer had
 *       started was reported to the rule engine as never begun.</li>
 *   <li><b>The campaign path was silent.</b> Its copy of the loop emitted no per-attestation event at
 *       all — only the campaign-level {@code OBLIGATION_CAMPAIGN_ESCALATED} — so an F8 rule keyed on
 *       the row-level trigger saw nothing for campaign-driven escalation.</li>
 * </ul>
 *
 * <p>This is the fifth writer-versus-declared-lifecycle instance in this programme (F2-175, F2-188,
 * F2-189, F2-190), and the first found by asking the question the fourth one ended on: <i>how many
 * things write this status, and how many of them ask?</i>
 */
class AttestationOverdueGuardTest {

    // ── the table now says what the javadoc says ─────────────────────────────────────────────────

    @Test
    @DisplayName("DUE and IN_PROGRESS may become OVERDUE — the edges the guard omitted")
    void theOverdueEdgesExist() {
        assertThat(ObligationAttestationService.mayAttest("DUE", "OVERDUE")).isTrue();
        assertThat(ObligationAttestationService.mayAttest("IN_PROGRESS", "OVERDUE")).isTrue();
    }

    @Test
    @DisplayName("nothing else may become OVERDUE, and that is what makes the guard worth having")
    void nothingElseBecomesOverdue() {
        Set<String> illegal = new TreeSet<>();
        for (String from : ObligationAttestationService.ATTEST_STATES) {
            if (from.equals("DUE") || from.equals("IN_PROGRESS")) {
                continue;
            }
            if (ObligationAttestationService.mayAttest(from, "OVERDUE")) {
                illegal.add(from);
            }
        }
        assertThat(illegal)
            .as("An attested or not-applicable cycle is not overdue, and OVERDUE -> OVERDUE is not a "
                + "state change — the sweeps skip such a row rather than declaring a self-loop")
            .isEmpty();
    }

    /**
     * The javadoc is the specification here, so the two must agree. Parsing it is deliberate: a prose
     * state machine that nothing reads is what F2-190 found in the evidence bridge, and the cheapest
     * defence is to make the prose fail the build when it drifts.
     */
    @Test
    @DisplayName("the class javadoc's state machine and ATTEST_TRANSITIONS agree, edge for edge")
    void theProseAndTheTableAgree() throws Exception {
        Path src = Path.of("src/main/java/com/tss/grc/module/compliance/obligation/service/"
            + "ObligationAttestationService.java");
        assertThat(Files.isRegularFile(src))
            .as("%s not found — this test is vacuous without it", src.toAbsolutePath()).isTrue();
        String text = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);

        // Lines of the form:  *   DUE   → A | B | C
        Map<String, Set<String>> prose = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?m)^\\s*\\*\\s+([A-Z_]+)\\s+→\\s+([A-Z_ |]+)$").matcher(text);
        while (m.find()) {
            Set<String> targets = new TreeSet<>();
            for (String t : m.group(2).split("\\|")) {
                if (!t.isBlank()) {
                    targets.add(t.trim());
                }
            }
            prose.put(m.group(1), targets);
        }
        System.out.println("[F2-191] javadoc state machine parsed = " + prose);
        assertThat(prose)
            .as("the javadoc state machine did not parse — three arrow lines were measured, and a "
                + "comparison against nothing is the reassuring wrong answer")
            .hasSizeGreaterThanOrEqualTo(3);

        Map<String, Set<String>> mismatches = new LinkedHashMap<>();
        prose.forEach((from, targets) -> {
            Set<String> declared = ObligationAttestationService.ATTEST_TRANSITIONS
                .getOrDefault(from, Set.of());
            if (!declared.equals(targets)) {
                mismatches.put(from, targets);
            }
        });
        assertThat(mismatches)
            .as("The documented per-cycle state machine and ATTEST_TRANSITIONS disagree. The javadoc "
                + "was the only artefact that was right before F2-191 — keep them together, or the "
                + "prose becomes the thing a reader trusts while the code does something else. "
                + "(States the javadoc lists with no arrow — ATTESTED, ATTESTED_WITH_EXCEPTIONS, "
                + "NOT_APPLICABLE — are not compared here; their transitions are reviewer decisions.)")
            .isEmpty();
    }

    // ── the writers ──────────────────────────────────────────────────────────────────────────────

    /**
     * There is one OVERDUE writer now. A source assertion rather than a behaviour one because the
     * defect was structural: three loops that agreed on the easy part (the row ends up OVERDUE) and
     * differed on the guard, the event and the previous status.
     */
    @Test
    @DisplayName("only the attestation service's own helper writes OVERDUE")
    void oneWriterOnly() throws Exception {
        Path dir = Path.of("src/main/java/com/tss/grc/module/compliance/obligation");
        assertThat(Files.isDirectory(dir)).as("%s missing", dir.toAbsolutePath()).isTrue();
        Map<String, Integer> writers = new LinkedHashMap<>();
        try (var files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String code = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
                int n = code.split(Pattern.quote("setAttestationStatus(\"OVERDUE\")"), -1).length - 1;
                if (n > 0) {
                    writers.put(p.getFileName().toString(), n);
                }
            }
        }
        System.out.println("[F2-191] setAttestationStatus(\"OVERDUE\") sites = " + writers);
        assertThat(writers)
            .as("Was three (ObligationAttestationService x2 + ObligationCampaignService). The campaign "
                + "now calls markOverdueForCampaign, and the two sweeps share flipToOverdue, which is "
                + "the only place that consults the guard and the only place that knows the row's real "
                + "previous status.")
            .containsExactly(Map.entry("ObligationAttestationService.java", 1));
    }

    @Test
    @DisplayName("the campaign escalation no longer holds its own copy of the transition")
    void theCampaignAsksTheService() throws Exception {
        String code = new String(Files.readAllBytes(Path.of(
            "src/main/java/com/tss/grc/module/compliance/obligation/service/"
                + "ObligationCampaignService.java")), StandardCharsets.UTF_8)
            .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        assertThat(code)
            .as("the campaign decides WHEN to escalate; what OVERDUE does to a cycle belongs to the "
                + "service that owns the lifecycle")
            // ⚠ Scoped to the OVERDUE literal, not to the setter. The campaign legitimately sets DUE
            // when it materialises the cycles — that is entity creation, not a transition, and it is
            // the same distinction that made setStatus("DRAFT") a false positive in F2-189's sweep.
            .doesNotContain("setAttestationStatus(\"OVERDUE\")")
            .contains("attestationService.markOverdueForCampaign(");
    }

    /**
     * Anti-vacuity for the two source scans above: they are worthless if the file moved or the setter
     * was renamed, and both failures would report a clean surface.
     */
    @Test
    @DisplayName("the scans found real code, and the guard is reachable from a test")
    void theInstrumentIsNotVacuous() {
        assertThat(ObligationAttestationService.ATTEST_TRANSITIONS)
            .as("all six states must be declared, including the terminal one — an absent key reads as "
                + "'no transitions allowed', which is right for NOT_APPLICABLE and wrong for the rest")
            .hasSize(6)
            .containsKeys("DUE", "IN_PROGRESS", "OVERDUE", "ATTESTED", "ATTESTED_WITH_EXCEPTIONS",
                "NOT_APPLICABLE");
        assertThat(ObligationAttestationService.ATTEST_TRANSITIONS.keySet())
            .as("the table's states and the flat ATTEST_STATES vocabulary must be the same set")
            .isEqualTo(new TreeSet<>(ObligationAttestationService.ATTEST_STATES));
        assertThat(ObligationAttestationService.mayAttest(null, "OVERDUE"))
            .as("a null source state must not be permissive")
            .isFalse();
        assertThat(ObligationAttestationService.mayAttest("NOT_APPLICABLE", "IN_PROGRESS"))
            .as("NOT_APPLICABLE is terminal")
            .isFalse();
    }
}
