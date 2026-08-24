package com.tss.grc.module.fraud.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-343 — the fraud dashboard's "Open Cases" headline counted a typed inclusion list
 * {@code status IN ('DETECTED','TRIAGE','INVESTIGATING')} instead of the complement of the
 * machine's sole terminal state. That list (a) carried a DEAD token — 'TRIAGE' is never
 * persisted, {@code FraudService.triage()} sets INVESTIGATING/FALSE_POSITIVE — proving it was
 * not derived from the machine, and (b) dropped the non-terminal outcome states
 * (CONFIRMED/CLEARED/FALSE_POSITIVE, each still awaiting CLOSE), so a confirmed fraud awaiting
 * recovery/SAR read as NOT open — flatteringly under-counting a SAMA fraud dashboard headline.
 *
 * <p>Platform-ratified rule (F2-196 / F2-290 ethics cases / F2-341 CAPAs): "open" is the
 * COMPLEMENT of a declared terminal set, never a second inclusion list. This gate pins that
 * contract hermetically:
 *   (A) the machine's terminal set (derived from ALLOWED_TRANSITIONS) is exactly {CLOSED};
 *   (B) countOpenByOrg's @Query counts the complement (an exclusion of that terminal set),
 *       never an inclusion list, and references no status the machine cannot produce.
 * If a terminal state is later added to the machine, (A) changes and (C) fails until the query
 * is updated — so the @Query (a necessary second copy of the vocabulary) cannot silently drift
 * from the machine (F2-299).
 *
 * <p>Hermetic — reflects the machine constant and reads the repository source; no DB
 * (the @Query's row-selection is SQL and only a live probe could count rows, F2-320/F2-325).
 */
class FraudOpenCountContractTest {

    private static final Path REPO_SRC =
        Path.of("src/main/java/com/tss/grc/module/fraud/repository/FraudCaseRepository.java");

    /** The @Query text block for countOpenByOrg. */
    private static String openCountQuery() throws Exception {
        String src = Files.readString(REPO_SRC);
        int m = src.indexOf("countOpenByOrg");
        assertThat(m).as("countOpenByOrg present in repository").isGreaterThanOrEqualTo(0);
        int q = src.lastIndexOf("@Query", m);
        assertThat(q).as("@Query annotation precedes countOpenByOrg").isGreaterThanOrEqualTo(0);
        int open = src.indexOf("\"\"\"", q) + 3;
        int close = src.indexOf("\"\"\"", open);
        assertThat(close).isGreaterThan(open);
        return src.substring(open, close);
    }

    @Test
    void theMachineHasExactlyOneTerminalState_CLOSED() {
        // Anchors the whole fix: "open = not CLOSED" is correct BECAUSE CLOSED is the sole
        // terminal. Derived from the machine, so a new terminal is forced through this assertion.
        assertThat(FraudService.TERMINAL_STATUSES).containsExactly("CLOSED");
    }

    @Test
    void openCountExcludesExactlyTheTerminalSet_notAnInclusionList() throws Exception {
        String query = openCountQuery();

        // Must be the COMPLEMENT (an exclusion of the terminal), never a bare inclusion list.
        assertThat(query)
            .as("countOpenByOrg must exclude the terminal state, not list 'open' states")
            .doesNotContain("IN ('DETECTED'")
            .doesNotContain("'TRIAGE'");           // dead token — never a persisted status
        boolean bareInclusion = Pattern.compile("status\\s+IN\\s*\\(").matcher(query).find()
            && !Pattern.compile("status\\s+NOT\\s+IN\\s*\\(").matcher(query).find();
        assertThat(bareInclusion).as("status must not be gated by a bare inclusion list").isFalse();

        // The excluded token(s) parsed from the query == the machine's terminal set (drift guard).
        Set<String> excluded = new LinkedHashSet<>();
        Matcher ne = Pattern.compile("status\\s*(?:<>|!=)\\s*'([A-Z_]+)'").matcher(query);
        while (ne.find()) excluded.add(ne.group(1));
        Matcher notIn = Pattern.compile("status\\s+NOT\\s+IN\\s*\\(([^)]*)\\)").matcher(query);
        while (notIn.find()) {
            Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(notIn.group(1));
            while (tok.find()) excluded.add(tok.group(1));
        }
        assertThat(excluded)
            .as("the statuses the query excludes must equal the machine's terminal set")
            .isEqualTo(FraudService.TERMINAL_STATUSES);
    }

    @Test
    void selfTest_extractionFindsANonEmptyStatusQuery() throws Exception {
        String query = openCountQuery();
        assertThat(query).contains("FraudCase").contains("status").contains("CLOSED");
    }
}
