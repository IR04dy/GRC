package com.tss.grc.module.ethics;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The ONE declaration of the {@code ethics.cases} lifecycle and of the taxonomy the platform
 * reports that register in — the misconduct / whistleblowing case register (NAZAHA, the Saudi
 * Whistleblower Protection Law, SAMA AML/CFT where a case is financial-crime related).
 *
 * <p><b>Why this class exists (F2-290).</b> Three artefacts answered "is this case still open?"
 * and no two of them agreed:
 *
 * <ul>
 *   <li><b>{@code EthicsPage} (the client)</b> — {@code !['CLOSED','DISMISSED'].includes(status)},
 *       the <em>complement</em> of the terminal set. <b>Correct</b>, and the only correct one.</li>
 *   <li><b>{@code EthicsCaseService.analytics()}</b> — an <em>inclusion</em> list of five open
 *       states ({@code INTAKE + TRIAGE + INVESTIGATION + REMEDIATION + ESCALATED}).</li>
 *   <li><b>{@code BoardPackAggregateService.ethics()}</b> — a <em>narrower</em> inclusion list of
 *       three ({@code INTAKE + TRIAGE + INVESTIGATION}), and this one <b>emits a document</b>:
 *       {@code BoardPackService} prints {@code "Cases: N · Open N · …"} into the board-pack PDF and
 *       {@code BoardPackPptxService} into the PPTX, in both languages.</li>
 * </ul>
 *
 * <p>Measured 2026-08-23, <b>identically in dev and the deployed database</b>: 30 live cases, of
 * which 4 are terminal — so <b>26 are open, and both server readers answered 10</b>. The register
 * screen showed 26 beside a board pack that reported 10. The two server figures coincide only
 * because {@code REMEDIATION} and {@code ESCALATED} hold no rows today, so their difference is
 * latent while the understatement is live.
 *
 * <p><b>The rule (F2-196).</b> "Open" must be the COMPLEMENT of a declared terminal set, never a
 * second list. An inclusion list over an open vocabulary silently drops every token nobody
 * remembered — and this column has <b>no CHECK constraint</b>, so it holds
 * {@code INVESTIGATING} (8 rows) and {@code TRIAGED} (3 rows), near-synonyms of two declared
 * states, which no bucket could see. With a complement an unrecognised status is <em>chased</em>
 * rather than excused, which for a misconduct register is the safe direction: under-reporting the
 * open caseload is the finding an examiner writes up.
 *
 * <p>{@link #TERMINAL} is <b>derived</b> from {@link #ALLOWED} — a state is terminal exactly when
 * it has no outgoing edges — so the machine and the terminal set cannot drift. Note this derivation
 * reads the <em>edge set</em>, not the absence of a key: this machine gives its terminal states an
 * explicit empty entry, and {@code DsrCaseLifecycle} (F2-196) omits them entirely. Reading the edges
 * is correct under both conventions; reading key-absence would have returned an EMPTY terminal set
 * here, which reads exactly like "every case is open".
 *
 * <p><b>Collapsing the synonyms is NOT this class's business.</b> Rewriting 11 live rows in two
 * real databases is an owner decision (F2-183 / F2-196 / F2-203), and a CHECK constraint admitting
 * both spellings would <em>bless</em> them. What is fixed here is the readers; what is disclosed is
 * the count of rows outside the declared vocabulary, so the owner can see the population the
 * decision is about.
 */
public final class EthicsCaseLifecycle {

    /**
     * The declared machine, moved verbatim from {@code EthicsCaseService.TRANSITIONS} so this is
     * the only copy. Terminal states carry an explicit empty edge set; {@link #TERMINAL} reads
     * that emptiness, so adding a state with edges declares it open and adding one without
     * declares it terminal.
     *
     * <pre>
     *   INTAKE          → TRIAGE | DISMISSED | ESCALATED
     *   TRIAGE          → INVESTIGATION | DISMISSED | ESCALATED
     *   INVESTIGATION   → SUBSTANTIATED | UNSUBSTANTIATED | NO_VIOLATION | ESCALATED | DISMISSED
     *   SUBSTANTIATED   → REMEDIATION | ESCALATED | CLOSED
     *   UNSUBSTANTIATED → CLOSED
     *   NO_VIOLATION    → CLOSED
     *   REMEDIATION     → CLOSED
     *   ESCALATED       → CLOSED
     *   DISMISSED       → (terminal)
     *   CLOSED          → (terminal)
     * </pre>
     *
     * <p>{@code Map.of} tops out at ten pairs and this is exactly ten: an eleventh state is a
     * compile error rather than a silent truncation, which is the right way round.
     */
    public static final Map<String, Set<String>> ALLOWED = Map.of(
        "INTAKE",          Set.of("TRIAGE", "DISMISSED", "ESCALATED"),
        "TRIAGE",          Set.of("INVESTIGATION", "DISMISSED", "ESCALATED"),
        "INVESTIGATION",   Set.of("SUBSTANTIATED", "UNSUBSTANTIATED", "NO_VIOLATION", "ESCALATED", "DISMISSED"),
        "SUBSTANTIATED",   Set.of("REMEDIATION", "ESCALATED", "CLOSED"),
        "UNSUBSTANTIATED", Set.of("CLOSED"),
        "NO_VIOLATION",    Set.of("CLOSED"),
        "REMEDIATION",     Set.of("CLOSED"),
        "ESCALATED",       Set.of("CLOSED"),
        "DISMISSED",       Set.of(),
        "CLOSED",          Set.of()
    );

    /** Every state the machine mentions, as a key or as a target. Sorted, so a bucket loop is stable. */
    public static final List<String> STATES;

    /**
     * States the case can never leave — computed, never typed. Today: CLOSED, DISMISSED.
     */
    public static final Set<String> TERMINAL;

    /** Sorted, so a repository query's parameter order is stable and diffable. */
    public static final List<String> TERMINAL_LIST;

    /**
     * The status a newly filed case starts in — the mirror of {@link #TERMINAL}, computed rather
     * than typed: the one state that is a key and never a target.
     *
     * <p>F2-291 made this worth deriving. It has two readers now — {@code create()}'s default and
     * {@code requireLifecycleIsNotBodySet}'s create-side comparison — and while it was a literal
     * they were two copies of one fact, which is the shape F2-217 measured (a default and a
     * vocabulary that can disagree). The static block THROWS if the machine ever admits two roots
     * or none, because a silently-chosen root would make "a new case starts here" depend on
     * iteration order (F2-197's shape at a lifecycle).
     */
    public static final String INITIAL;

    static {
        Set<String> mentioned = new TreeSet<>(ALLOWED.keySet());
        ALLOWED.values().forEach(mentioned::addAll);
        STATES = List.copyOf(mentioned);

        Set<String> dead = new TreeSet<>();
        for (String s : mentioned) {
            if (ALLOWED.getOrDefault(s, Set.of()).isEmpty()) dead.add(s);
        }
        TERMINAL = Set.copyOf(dead);
        TERMINAL_LIST = List.copyOf(dead);

        Set<String> targets = new TreeSet<>();
        ALLOWED.values().forEach(targets::addAll);
        Set<String> roots = new TreeSet<>(ALLOWED.keySet());
        roots.removeAll(targets);
        if (roots.size() != 1) throw new IllegalStateException(
            "EthicsCaseLifecycle must have exactly one state that is never a transition target, "
            + "because that is the state a newly filed case starts in. Found: " + roots);
        INITIAL = roots.iterator().next();
    }

    /**
     * The taxonomy the platform <em>reports</em> {@code ethics.cases.category} in.
     *
     * <p>Deliberately NOT a {@link com.tss.grc.common.validation.ClosedVocabulary}: that primitive
     * names the CHECK constraint it mirrors, and this column has none — 6 of 30 live rows hold
     * {@code AML}, {@code POLICY_BREACH}, {@code GIFTS_HOSPITALITY}, {@code ABUSE_OF_OFFICE} and
     * {@code DATA_PRIVACY}, in both databases. Adding a CHECK would refuse them (F2-183), and
     * widening this list to admit them would bless four synonym pairs. So this is the reporting
     * taxonomy, the count of rows outside it is disclosed, and reconciling the register is the
     * owner's call.
     *
     * <p>Mirrored by {@code EthicsPage.CATEGORY_OPTIONS}; the two are pinned against each other in
     * both directions, because two copies agreeing is not evidence when nobody is maintaining the
     * agreement (F2-177 / F2-205).
     */
    public static final List<String> CATEGORY_TAXONOMY = List.of(
        "BRIBERY", "KICKBACK", "CONFLICT_OF_INTEREST", "FRAUD", "THEFT", "HARASSMENT",
        "DISCRIMINATION", "RETALIATION", "DATA_MISUSE", "AML_SUSPICION", "POLICY_VIOLATION",
        "SAFETY", "OTHER");

    private EthicsCaseLifecycle() {}

    /**
     * Case-insensitively normalise a stored status for comparison. The column is
     * {@code Latin1_General_100_CI_AS_SC_UTF8} and has no CHECK constraint, so {@code 'closed'} is
     * storable and {@code "CLOSED".equals(...)} would be false against it — the F2-179 family.
     * Compare through here, never with a bare equals.
     */
    public static String normalise(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    /** True when the case has reached a state it can never leave. */
    public static boolean isTerminal(String status) {
        return TERMINAL.contains(normalise(status));
    }

    /**
     * True when the case is still live — the complement of {@link #isTerminal}, so a status nobody
     * has heard of counts as OPEN. That asymmetry is the whole point of this class.
     */
    public static boolean isOpen(String status) {
        return !isTerminal(status);
    }

    /** Legal targets from {@code from}; empty when {@code from} is terminal or unrecognised. */
    public static Set<String> allowedTargets(String from) {
        return ALLOWED.getOrDefault(normalise(from), Set.of());
    }
}
