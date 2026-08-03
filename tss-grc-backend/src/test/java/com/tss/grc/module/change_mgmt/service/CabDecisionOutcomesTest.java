package com.tss.grc.module.change_mgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7c / X-2 — <b>a CAB that defers, or asks for more information, can say so on the change</b>; and
 * <b>F2-103</b>: the one decision that never recorded who took it.
 *
 * <p><b>X-2's gap, measured 2026-08-03.</b> {@code CommitteeResolution.outcome} has permitted
 * {@code DEFERRED} since V154 and {@code AR-152} is enabled, so a committee can defer a motion and
 * the secretary is told. The change could not follow:
 *
 * <pre>
 *   NORMAL_TRANSITIONS   CAB_PENDING -> { CAB_APPROVED, CAB_REJECTED }
 *   ck_change_status     14 values, identical in dev and prod
 *   cab_decision_at      stamped only on APPROVED / REJECTED / EMERGENCY_APPROVED
 * </pre>
 *
 * <p>So a deferred change sat at {@code CAB_PENDING} with no decision stamp — <b>byte-identical to
 * a change the committee had never seen</b>. F2-89's shape one level up: the committee reached an
 * outcome the change could not represent.
 *
 * <p><b>F2-103, found while writing the above.</b> {@code stampDecisionAuthor} had exactly two call
 * sites, {@code EMERGENCY_APPROVED} and {@code CAB_REJECTED} — <b>not</b> {@code CAB_APPROVED}.
 * F2-93 added {@code cab_decision_by_id}/{@code _name} because "the record says when the CAB decided
 * and never who", and it was fixed while A17-D3 was in flight, so it landed on the paths that slice
 * touched. Measured: <b>zero</b> rows in dev carry {@code cab_decision_by_id}, and 2 carry
 * {@code cab_decision_at} without it; the only test asserting the column was
 * {@code EmergencyRatificationTest}. The platform's central authorisation act recorded a timestamp
 * and an anonymous approver.
 *
 * <p>These tests read the real static tables by reflection and the real migration text, so they
 * fail if the vocabulary, the transitions, the authority classification or the stamp drift apart.
 */
class CabDecisionOutcomesTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");
    private static final Path SERVICE =
        Path.of("src/main/java/com/tss/grc/module/change_mgmt/service/ChangeManagementService.java");

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> transitions() throws Exception {
        Field f = ChangeManagementService.class.getDeclaredField("NORMAL_TRANSITIONS");
        f.setAccessible(true);
        return (Map<String, Set<String>>) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> decisionTargets() throws Exception {
        Field f = ChangeManagementService.class.getDeclaredField("DECISION_TARGETS");
        f.setAccessible(true);
        return (Set<String>) f.get(null);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("X-2 — a CAB may defer or ask for more information")
    void theCabHasSomewhereToPutANonApproval() throws Exception {
        assertThat(transitions().get("CAB_PENDING"))
            .containsExactlyInAnyOrder("CAB_APPROVED", "CAB_REJECTED",
                                       "CAB_DEFERRED", "MORE_INFO_REQUIRED");
    }

    @Test
    @DisplayName("both new states route BACK into the queue — neither is terminal, neither jumps the gate")
    void theyReturnToTheQueue() throws Exception {
        // A deferred motion is re-tabled (AR-152 already says so) and a change whose questions have
        // been answered goes back before the same CAB. Crucially neither may reach CAB_APPROVED
        // directly: that would be a second route past the approval gate.
        for (String s : List.of("CAB_DEFERRED", "MORE_INFO_REQUIRED")) {
            assertThat(transitions().get(s)).as(s)
                .containsExactlyInAnyOrder("CAB_PENDING", "CANCELLED")
                .doesNotContain("CAB_APPROVED", "SCHEDULED", "IMPLEMENTING", "EMERGENCY_APPROVED");
        }
    }

    @Test
    @DisplayName("both are CAB DECISIONS, so they need CHANGE:APPROVE and not merely CHANGE:WRITE")
    void recordingACommitteeConclusionIsADecision() throws Exception {
        // Asserting what a governance body concluded is a decision. On the silent CHANGE:WRITE
        // default, anyone who can edit a change could record that the CAB deferred it — F2-29's
        // defect reintroduced.
        assertThat(decisionTargets()).contains("CAB_DEFERRED", "MORE_INFO_REQUIRED");
    }

    @Test
    @DisplayName("⚠ F2-103 — every decision branch stamps WHO decided, CAB_APPROVED included")
    void everyDecisionBranchStampsItsAuthor() {
        // The regex reads the real switch. F2-93's column existed and its most important branch did
        // not set it, and no test would have noticed: the assertion lived in EmergencyRatificationTest.
        String src = read(SERVICE);
        Matcher sw = Pattern.compile("switch \\(target\\) \\{(.*?)\\n        \\}", Pattern.DOTALL).matcher(src);
        assertThat(sw.find()).as("could not locate the transition switch — this test is broken, not the code").isTrue();
        String body = sw.group(1);

        // Split the switch into its `case "X" ->` arms.
        Matcher arm = Pattern.compile("case ((?:\"[A-Z_]+\"(?:, )?)+) ->").matcher(body);
        Map<String, String> arms = new java.util.LinkedHashMap<>();
        List<int[]> spans = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        while (arm.find()) { spans.add(new int[]{arm.start(), arm.end()}); labels.add(arm.group(1)); }
        for (int i = 0; i < spans.size(); i++) {
            int from = spans.get(i)[1];
            int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : body.length();
            arms.put(labels.get(i), body.substring(from, to));
        }
        assertThat(arms).as("anti-vacuity: the switch should have several arms").hasSizeGreaterThan(5);

        Set<String> missing = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : arms.entrySet()) {
            // Every target named in this arm that is a decision must have the stamp.
            for (String t : e.getKey().split(",")) {
                String target = t.trim().replace("\"", "");
                boolean isDecision;
                try {
                    isDecision = decisionTargets().contains(target);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
                if (!isDecision) continue;
                if (!e.getValue().contains("stampDecisionAuthor")) missing.add(target);
                if (!e.getValue().contains("setCabDecisionAt")) missing.add(target + " (no cab_decision_at)");
            }
        }
        assertThat(missing)
            .as("""
                These CAB decision branches record the decision without recording who took it. \
                That is F2-103: F2-93 added cab_decision_by_id and CAB_APPROVED never set it, so \
                the platform's central authorisation act had a timestamp and an anonymous \
                approver, and the SoD check had nothing to compare against:

                %s""", missing)
            .isEmpty();

        // And every decision target must actually appear in the switch — a decision with no branch
        // records nothing at all, which is the same gap one step earlier.
        Set<String> inSwitch = new LinkedHashSet<>();
        arms.keySet().forEach(k -> {
            for (String t : k.split(",")) inSwitch.add(t.trim().replace("\"", ""));
        });
        try {
            assertThat(inSwitch)
                .as("a CAB decision target with no switch branch stamps nothing")
                .containsAll(decisionTargets());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("the Java vocabulary matches the latest ck_change_status, and PENDING_RATIFICATION is absent")
    void javaMatchesTheCheckConstraint() throws Exception {
        String latest = null;
        String from = null;
        try (Stream<Path> walk = Files.walk(MIGRATIONS)) {
            for (Path m : walk.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                Matcher mt = Pattern.compile(
                        "CONSTRAINT\\s+ck_change_status\\s+CHECK\\s*\\(\\s*status\\s+IN\\s*\\(([^)]*)\\)",
                        Pattern.CASE_INSENSITIVE).matcher(read(m));
                while (mt.find()) { latest = mt.group(1); from = m.getFileName().toString(); }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(latest).as("no migration declares ck_change_status — the scan is broken").isNotNull();

        Set<String> declared = new LinkedHashSet<>();
        for (String tok : latest.split(",")) {
            String v = tok.trim();
            // strip SQL comments the constraint body carries
            if (v.startsWith("--") || v.isEmpty()) continue;
            v = v.replaceAll("(?s)--[^\\n]*", "").trim();
            v = v.replaceAll("^[Nn]?'", "").replaceAll("'$", "").trim();
            if (!v.isEmpty()) declared.add(v);
        }

        Set<String> reachable = new LinkedHashSet<>();
        transitions().values().forEach(reachable::addAll);
        reachable.addAll(transitions().keySet());

        assertThat(declared)
            .as("""
                %s declares a status vocabulary the state machine does not match. A status the \
                CHECK forbids is a 500 on the transition; a status the CHECK permits and nothing \
                reaches is an unreachable state (X-2's PENDING_RATIFICATION, dropped for exactly \
                that reason).""", from)
            .containsExactlyInAnyOrderElementsOf(reachable);

        assertThat(declared).hasSize(16);
        assertThat(declared).contains("CAB_DEFERRED", "MORE_INFO_REQUIRED");
        assertThat(declared)
            .as("A17-D3 models ratification orthogonally to status, so this value has no producer")
            .doesNotContain("PENDING_RATIFICATION");
    }
}
