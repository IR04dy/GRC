package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The findings register must be internally consistent — a sibling to
 * {@link FindingsRegisterCoverageTest}, which asks a different question.
 *
 * <p><b>Coverage asks:</b> does every finding the plan names have a register row?
 * <b>This asks:</b> does the register agree with itself, and with what the code already claims?
 * Two instruments, two questions — deliberately not merged, because a ratchet that answers two
 * questions reports one number and hides whichever half is failing.
 *
 * <h3>The three defects that produced this gate, all measured 2026-08-08</h3>
 * <ol>
 *   <li><b>{@code F2-45} existed TWICE</b>, filed 2026-07-26 and written again 2026-08-01 by an
 *       author who recorded that it <i>"had lived only inside W2-0's plan row"</i> — having not found
 *       the row that was already there. The two carried <b>opposite states</b>, {@code ⬜ OPEN} and
 *       {@code ✅ CLOSED}, under one id. So {@code [[F2-45]]} resolved to either answer.</li>
 *   <li><b>{@code F2-120} read {@code 🔴 OPEN} while its own pinning test said CLOSED.</b> It was
 *       closed from the other side: {@code F2-145}'s row records <i>"`sharia` FULLY CLOSED … and
 *       with it [[F2-120]]"</i>, and nothing visited {@code F2-120}. <b>A finding closed as
 *       collateral is the one nobody updates</b> — {@code F2-127}, closed by the same slice for the
 *       module the slice was named after, was updated correctly.</li>
 *   <li><b>{@code F2-131} was the one row a state scan could not read at all</b> — withdrawn, with
 *       the disposition in the heading and no state field in the table.</li>
 * </ol>
 *
 * <h3>⚠ Why "a migration names the finding" is NOT a rule here, though it would have caught most</h3>
 * <p>The obvious third witness is: <i>a packaged migration whose header names {@code F2-nnn} closes
 * it.</i> It was measured and <b>rejected</b>. {@code V359}'s header names {@code F2-130} while
 * fixing {@code F2-81} — it cites F2-130's <em>shape</em> ("a permission nobody holds") as the
 * reason for its own design. {@code F2-130} is genuinely open. So the rule produces a false failure
 * on a correct register, and this programme's standing lesson is that <b>a rule which invents
 * defects gets switched off, and takes the true findings with it</b>. Naming a finding is not a
 * claim to close it. The cost of leaving it out is stated rather than hidden: <b>this gate could not
 * have caught {@code F2-146}</b>, whose remedy shipped as V361 while both the register and the plan
 * kept reading OPEN. Nothing mechanical could — neither document contradicted the other, because
 * both were stale.
 *
 * <p><b>And why not "the plan and the register must agree about a state":</b> the plan tracks
 * findings in more than one shape — {@code FindingsRegisterCoverageTest.REFERENCED}'s javadoc
 * measures 78 of 140 ids in row shape, the rest inside other rows and in the sequencing line — so a
 * plan-side state extractor would report disagreements that are not there.
 *
 * <h3>The two row schemas, and why this test pins the reading rather than unifying them</h3>
 * <p>Measured: <b>62 rows carry {@code **Status**}</b> (the older schema, where {@code Current
 * State} holds a description of the <em>defect</em>) and <b>83 carry {@code **Current State**}</b>
 * as the state. <b>60 carry both.</b> So a script reading "Current State" as the status silently
 * reads a problem description for 62 rows — which is exactly what the first pass of this very slice
 * did, reporting 101 of 146 findings as not-closed. Rewriting 62 rows is a bigger and riskier edit
 * than the defect warrants, so the canonical reading is pinned here instead:
 * <b>{@code Status} wins where both are present.</b> Every future scan can read that one rule
 * instead of re-deriving it and getting it wrong.
 */
class FindingsRegisterConsistencyTest {

    private static final Path REGISTER =
            Path.of("..", "TSS-GRC-Platform-Bible", "Registers", "FINDINGS_REGISTER_V2.md");
    private static final Path TEST_SOURCES = Path.of("src", "test", "java");

    /**
     * A register row heading: {@code ## F2-45 · Title}, {@code ## F2-22b(vi) — Title}.
     *
     * <p>⚠ <b>The obvious closing {@code \b} is wrong here and cost one red run.</b> After the
     * optional {@code (vi)} group the next character is a space, and {@code ")"} → {@code " "} is
     * <em>not</em> a word boundary — so the engine backtracked, dropped the sub-id, and reported all
     * seven {@code F2-22b(…)} rows as seven duplicates of {@code F2-22b}. A lookahead for a real
     * separator is what the shape needs. The failure was in the reassuring direction for once: it
     * invented duplicates rather than hiding them.
     */
    private static final Pattern HEADING = Pattern.compile(
            "(?m)^## ((?:F2|SCR|DF)-\\d+[a-z]?(?:\\([ivx]+\\))?)(?=[\\s·—-]|$)");

    /** A state cell, either schema. */
    private static final Pattern STATE_CELL = Pattern.compile(
            "\\|\\s*\\*\\*(Status|Current State)\\*\\*\\s*\\|(.*?)\\|\\s*\\R");

    /** One row claiming to have closed another: {@code and with it [[F2-120]]}. */
    private static final Pattern CLOSED_BY_PEER = Pattern.compile(
            "(?:and with it|with it|closing|closes)\\s+\\[\\[((?:F2|SCR|DF)-\\d+[a-z]?"
            + "(?:\\([ivx]+\\))?)\\]\\]");

    /** A test javadoc claiming a finding is closed: {@code F2-120 — CLOSED 2026-08-07}. */
    private static final Pattern CLOSED_BY_TEST = Pattern.compile(
            "((?:F2|SCR|DF)-\\d+[a-z]?(?:\\([ivx]+\\))?)([^\\n]{0,60}?)\\b(CLOSED|FIXED)\\b");

    /**
     * Words that turn a closure claim into its opposite. None appear in the 8 live matches; this is
     * defence for the sentence somebody will eventually write ("F2-119 is not CLOSED until…"),
     * because the direction that mistake fails in is a false failure on a correct register.
     */
    private static final Pattern NEGATED = Pattern.compile(
            "\\b(not|never|un|cannot|can't|without|unless|if|would|should|before|until|no)\\b|!");

    private static final Pattern OPEN_MARK = Pattern.compile("⬜|🔴|🟡|🔶|🔁|🔄|⏳|\\bOPEN\\b");
    private static final Pattern CLOSED_MARK = Pattern.compile(
            "✔|✅|🚫|\\bCLOSED\\b|\\bFIXED\\b|\\bSHIPPED\\b|\\bDONE\\b|\\bREFUTED\\b"
            + "|\\bWITHDRAWN\\b|\\bSUPERSEDED\\b|\\bDECIDED\\b");

    /**
     * Liveness floors (F2-69's lesson, and F2-79's: a scan that stops matching reports a tidy zero,
     * and every set difference below it becomes vacuously empty). Measured 2026-08-08: 145 rows,
     * 145 state cells, 3 peer-closure claims, 5 distinct ids claimed closed by a test.
     */
    private static final int MIN_ROWS = 130;
    private static final int MIN_PEER_CLAIMS = 2;
    private static final int MIN_TEST_CLAIMS = 4;

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    private static void assumeRegister() {
        Assumptions.assumeTrue(Files.isRegularFile(REGISTER),
                "register not reachable from " + REGISTER.toAbsolutePath());
    }

    /** Every heading, in file order, with duplicates preserved — the duplicate IS the defect. */
    private static List<String> headings(String register) {
        List<String> out = new ArrayList<>();
        Matcher m = HEADING.matcher(register);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * id → state cell, using the canonical reading: {@code Status} wins where a row carries both.
     * Only the block between one heading and the next is searched, so a row cannot borrow its
     * neighbour's state.
     */
    private static Map<String, String> states(String register) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher h = HEADING.matcher(register);
        List<int[]> spans = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        while (h.find()) {
            spans.add(new int[] {h.start(), h.end()});
            ids.add(h.group(1));
        }
        for (int i = 0; i < ids.size(); i++) {
            int from = spans.get(i)[1];
            int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : register.length();
            Matcher c = STATE_CELL.matcher(register.substring(from, to));
            String status = null;
            String currentState = null;
            while (c.find()) {
                if ("Status".equals(c.group(1))) {
                    if (status == null) status = c.group(2).trim();
                } else if (currentState == null) {
                    currentState = c.group(2).trim();
                }
            }
            String state = status != null ? status : currentState;
            if (state != null) {
                out.putIfAbsent(ids.get(i), state);
            }
        }
        return out;
    }

    /**
     * The <b>first</b> disposition marker in the cell is the state.
     *
     * <p>⚠ <b>The obvious rule — "open if it carries an open marker and no closed marker" — was
     * written first and a mutant SURVIVED it.</b> Reverting {@code F2-120} to {@code 🔴 OPEN} left
     * the gate green, because the rest of that cell narrates the closure and contains the word
     * <i>shipped</i>. So the any-marker rule reads a cell as closed whenever its prose *mentions* a
     * closure — and a state cell in this register is mostly prose. Every row states its disposition
     * <b>first</b>, so position is what carries the meaning.
     *
     * <p>➕ The positional rule is also the more honest one: it moves 7 rows from closed to open
     * ({@code F2-30}, {@code F2-33}, {@code F2-112}, {@code F2-115}, {@code F2-133}, {@code F2-141},
     * {@code F2-148}) — every one a {@code 🟡}/{@code 🔶} row whose leading marker says <i>partly</i>
     * and whose text names the half that closed. Under the any-marker rule a half-closed finding read
     * as finished, which is the same shape as the defect this whole gate exists to catch.
     */
    private static boolean readsOpen(String state) {
        if (state == null) {
            return false;
        }
        Matcher open = OPEN_MARK.matcher(state);
        if (!open.find()) {
            return false;
        }
        Matcher closed = CLOSED_MARK.matcher(state);
        return !closed.find() || open.start() < closed.start();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the register scan still matches — or every assertion below is vacuous")
    void theScanIsNotVacuous() {
        assumeRegister();
        List<String> found = headings(read(REGISTER));
        assertThat(found.size())
                .as("the heading pattern matched %d rows in a register that holds %d+. F2-79's "
                    + "defect was exactly this: a pattern one hash out reported ZERO specs for a "
                    + "file with 54, and the conclusion drawn read like diligence. Fix the pattern; "
                    + "do not let it report a clean register.", found.size(), MIN_ROWS)
                .isGreaterThanOrEqualTo(MIN_ROWS);
        assertThat(states(read(REGISTER)).size())
                .as("state cells found for %d of %d rows — the state-cell pattern broke, and every "
                    + "contradiction check below silently passes when it does",
                        states(read(REGISTER)).size(), found.size())
                .isGreaterThanOrEqualTo(MIN_ROWS);
    }

    @Test
    @DisplayName("one row per finding id — F2-45 was written twice, with opposite states")
    void everyFindingIdAppearsExactlyOnce() {
        assumeRegister();
        Map<String, Integer> counts = new TreeMap<>();
        headings(read(REGISTER)).forEach(id -> counts.merge(id, 1, Integer::sum));
        List<String> dups = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey() + " ×" + e.getValue())
                .toList();

        assertThat(dups)
                .as("%d finding ids have more than one register row: %s. F2-45 had two — one filed "
                    + "2026-07-26 reading OPEN and one worked 2026-08-01 reading CLOSED — so "
                    + "[[F2-45]] resolved to either answer and half its own history read as "
                    + "unfinished. Consolidate into one row and keep whatever the older one said "
                    + "that the newer does not; do not leave two.", dups.size(), dups)
                .isEmpty();
    }

    @Test
    @DisplayName("every row states its disposition in the table, not only in its heading")
    void everyRowExposesAState() {
        assumeRegister();
        String reg = read(REGISTER);
        Set<String> withState = states(reg).keySet();
        List<String> missing = headings(reg).stream().distinct()
                .filter(id -> !withState.contains(id))
                .toList();

        assertThat(missing)
                .as("%d rows carry no **Status** and no **Current State** cell: %s. F2-131 was the "
                    + "one — withdrawn, with the disposition in its heading, which no scan can "
                    + "read. A finding whose state is only prose is a finding that cannot be "
                    + "counted.", missing.size(), missing)
                .isEmpty();
    }

    @Test
    @DisplayName("a finding another row says it closed may not still read OPEN")
    void closureClaimedByAPeerRowIsHonoured() {
        assumeRegister();
        String reg = read(REGISTER);
        Map<String, String> states = states(reg);

        // Attribute each claim to the row that makes it, so the failure names both ends.
        Map<String, String> claims = new TreeMap<>();
        Matcher h = HEADING.matcher(reg);
        List<int[]> spans = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        while (h.find()) {
            spans.add(new int[] {h.start(), h.end()});
            ids.add(h.group(1));
        }
        for (int i = 0; i < ids.size(); i++) {
            int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : reg.length();
            Matcher c = CLOSED_BY_PEER.matcher(reg.substring(spans.get(i)[1], to));
            while (c.find()) {
                if (!c.group(1).equals(ids.get(i))) {
                    claims.putIfAbsent(c.group(1), ids.get(i));
                }
            }
        }

        assertThat(claims.size())
                .as("only %d peer-closure claims found; the pattern broke and this check is now "
                    + "vacuous", claims.size())
                .isGreaterThanOrEqualTo(MIN_PEER_CLAIMS);

        List<String> stale = claims.entrySet().stream()
                .filter(e -> readsOpen(states.get(e.getKey())))
                .map(e -> e.getKey() + " (claimed closed by " + e.getValue() + ", reads: "
                        + states.get(e.getKey()) + ")")
                .toList();

        assertThat(stale)
                .as("%d findings are recorded as closed by another row while their own state still "
                    + "reads open: %s. This is F2-120's defect: it was closed as collateral by "
                    + "F2-145's sharia slice, F2-145's row said so, and nothing visited F2-120 — so "
                    + "the register contradicted itself for a day. Update the closed finding's own "
                    + "cell in the same commit that claims the closure.", stale.size(), stale)
                .isEmpty();
    }

    @Test
    @DisplayName("a finding a test says it closed may not still read OPEN")
    void closureClaimedByATestIsHonoured() {
        assumeRegister();
        Assumptions.assumeTrue(Files.isDirectory(TEST_SOURCES),
                "test sources not reachable from " + TEST_SOURCES.toAbsolutePath());
        Map<String, String> states = states(read(REGISTER));

        Map<String, String> claims = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(TEST_SOURCES)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = CLOSED_BY_TEST.matcher(read(p));
                while (m.find()) {
                    if (!NEGATED.matcher(m.group(2)).find()) {
                        claims.putIfAbsent(m.group(1), p.getFileName().toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(claims.size())
                .as("only %d findings are claimed closed by a test javadoc; the scan broke",
                        claims.size())
                .isGreaterThanOrEqualTo(MIN_TEST_CLAIMS);

        // A claim naming an id with no register row is not a failure here — the plan carries
        // findings the register does not (that population is FindingsRegisterCoverageTest's job,
        // and F2-22b is a live example: the plan tracks it, the register carries only its
        // sub-findings). Silence beats a second instrument disagreeing with the first.
        List<String> stale = new TreeSet<>(claims.keySet()).stream()
                .filter(id -> states.containsKey(id) && readsOpen(states.get(id)))
                .map(id -> id + " (claimed closed in " + claims.get(id) + ", reads: "
                        + states.get(id) + ")")
                .toList();

        assertThat(stale)
                .as("%d findings are asserted CLOSED by a test while the register still reads them "
                    + "open: %s. The test is the executable claim and the register is what an "
                    + "auditor is handed — when they disagree the register is wrong, because a "
                    + "green test that describes a closure is stronger evidence than a cell nobody "
                    + "revisited. ⚠ If the test's wording is what is wrong, fix the wording: this "
                    + "check reads the javadoc, so 'F2-nnn … CLOSED' is a claim.",
                        stale.size(), stale)
                .isEmpty();
    }
}
