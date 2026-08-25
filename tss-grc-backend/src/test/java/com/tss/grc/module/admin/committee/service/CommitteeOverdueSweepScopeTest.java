package com.tss.grc.module.admin.committee.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-361 — a committee action item that was DEFERRED and then missed its revised deadline was
 * never swept to OVERDUE, so it silently escaped escalation and the next agenda's matters-arising.
 *
 * <p>{@code defer(id, newDueDate)} reschedules the deadline ("revisit by newDueDate") and moves the
 * item to DEFERRED; {@code startProgress} cannot resume a DEFERRED item (it accepts only
 * OPEN/OVERDUE); and {@code markOverdue} — the writer that flips an item to OVERDUE — bars only
 * COMPLETED/CANCELLED/OVERDUE, so it ACCEPTS DEFERRED as an overdue source. Yet
 * {@code findShouldBeOverdue} (the query feeding the scheduled sweep) selected only
 * {@code IN ('OPEN','IN_PROGRESS')}. So when a deferred item's revised deadline passed, the sweep
 * never selected it, {@code markOverdue} was never called on it, and it never returned to
 * matters-arising (which lists OVERDUE). Deferral became a permanent escape from accountability —
 * a CMA/SAMA committee-governance blind spot. The sweep's QUERY disagreed with its own WRITER
 * (F2-177), and "should-be-overdue" is the complement of the closed/already-overdue states, not a
 * hand-typed inclusion list (F2-196).
 *
 * <p>This gate pins the agreement hermetically (F2-343 pattern; no DB — the @Query's row selection
 * is SQL and only a live probe could count rows, F2-320/F2-325): the sweep query's status set must
 * equal the set {@code markOverdue} accepts = the declared vocabulary minus the states it bars.
 * Both halves are DERIVED from source, so a new status added to the machine forces this assertion
 * (F2-299) — the @Query, a necessary second copy of the vocabulary, cannot silently drift.
 */
class CommitteeOverdueSweepScopeTest {

    private static final Path SERVICE = Path.of(
        "src/main/java/com/tss/grc/module/admin/committee/service/CommitteeActionItemService.java");
    private static final Path REPO = Path.of(
        "src/main/java/com/tss/grc/module/admin/committee/repository/CommitteeActionItemRepository.java");

    /** Every status the service ASSIGNS — the action-item vocabulary, derived not hand-typed. */
    private static Set<String> vocabulary(String svc) {
        Set<String> v = new TreeSet<>();
        Matcher m = Pattern.compile("\\.setStatus\\(\"([A-Z_]+)\"\\)").matcher(svc);
        while (m.find()) v.add(m.group(1));
        // The create path assigns the initial state via requireInitial(x, INITIAL_STATUS, …),
        // where INITIAL_STATUS is a declared constant — resolve its literal value.
        Matcher init = Pattern.compile("INITIAL_STATUS\\s*=\\s*\"([A-Z_]+)\"").matcher(svc);
        while (init.find()) v.add(init.group(1));
        return v;
    }

    /** The states markOverdue REFUSES to flip (its own guard) — scoped to that method's body. */
    private static Set<String> barredByMarkOverdue(String svc) {
        String body = methodBody(svc, "public CommitteeActionItem markOverdue(");
        Set<String> barred = new TreeSet<>();
        Matcher m = Pattern.compile("\"([A-Z_]+)\"\\.equals\\(prev\\)").matcher(body);
        while (m.find()) barred.add(m.group(1));
        return barred;
    }

    /** The status IN (...) tokens of findShouldBeOverdue's @Query text block. */
    private static Set<String> sweepQueryStatuses(String repo) {
        int m = repo.indexOf("findShouldBeOverdue");
        assertThat(m).as("findShouldBeOverdue present").isGreaterThanOrEqualTo(0);
        int q = repo.lastIndexOf("@Query", m);
        assertThat(q).as("@Query precedes findShouldBeOverdue").isGreaterThanOrEqualTo(0);
        int open = repo.indexOf("\"\"\"", q) + 3;
        int close = repo.indexOf("\"\"\"", open);
        String jpql = repo.substring(open, close);
        Matcher in = Pattern.compile("status\\s+IN\\s*\\(([^)]*)\\)").matcher(jpql);
        assertThat(in.find()).as("findShouldBeOverdue filters status IN (...)").isTrue();
        Set<String> out = new LinkedHashSet<>();
        Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(in.group(1));
        while (tok.find()) out.add(tok.group(1));
        return out;
    }

    /** Brace-balanced body of the method whose signature contains {@code needle}. */
    private static String methodBody(String src, String needle) {
        int s = src.indexOf(needle);
        assertThat(s).as(needle + " present").isGreaterThanOrEqualTo(0);
        int open = src.indexOf('{', s);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return src.substring(open, i + 1); }
        }
        throw new AssertionError("unbalanced braces after " + needle);
    }

    @Test
    void theSweepSelectsExactlyTheStatesMarkOverdueAccepts() throws Exception {
        String svc = Files.readString(SERVICE);
        String repo = Files.readString(REPO);

        Set<String> vocab = vocabulary(svc);
        Set<String> barred = barredByMarkOverdue(svc);

        // anti-vacuity (F2-195): the parses must have actually resolved.
        assertThat(vocab).as("action-item vocabulary derived from setStatus literals")
            .contains("OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED", "DEFERRED", "OVERDUE");
        assertThat(barred).as("markOverdue bars the closed/already-overdue states")
            .containsExactlyInAnyOrder("COMPLETED", "CANCELLED", "OVERDUE");

        Set<String> accepted = new TreeSet<>(vocab);
        accepted.removeAll(barred);   // states markOverdue would flip to OVERDUE = should-be-overdue set

        Set<String> sweep = new TreeSet<>(sweepQueryStatuses(repo));

        assertThat(sweep)
            .as("findShouldBeOverdue must select exactly the states markOverdue accepts (%s); "
                + "a mismatch means an outstanding item silently escapes overdue escalation "
                + "(F2-361: DEFERRED was the missing one) or a closed item is wrongly re-swept",
                accepted)
            .isEqualTo(accepted);
    }

    @Test
    void deferredIsSweepableAndAcceptedByTheWriter() throws Exception {
        // The specific F2-361 fix + its rationale, stated directly so a revert names DEFERRED.
        String svc = Files.readString(SERVICE);
        assertThat(barredByMarkOverdue(svc))
            .as("markOverdue accepts DEFERRED as an overdue source (it is not barred)")
            .doesNotContain("DEFERRED");
        assertThat(sweepQueryStatuses(Files.readString(REPO)))
            .as("the scheduled sweep must select DEFERRED so a lapsed deferral escalates")
            .contains("DEFERRED");
    }
}
