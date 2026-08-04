package com.tss.grc.module.change_mgmt;

import com.tss.grc.module.change_mgmt.service.ChangeKpiService;
import com.tss.grc.module.change_mgmt.service.ChangeManagementService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S8c / CE-16 — the documentation is measured too.
 *
 * <h3>Why this test exists</h3>
 * <p>CE-16 is "docs", and a docs slice has exactly one failure mode: it describes what the plan said
 * instead of what the code does, and then rots. Both had already happened here.
 *
 * <ul>
 *   <li>{@code module/change_mgmt/package-info.java} — the module's own contract — described a
 *       <b>ten</b>-state lifecycle "with an EMERGENCY fast-path that <b>bypasses CAB</b>". The
 *       machine has <b>sixteen</b> states, and emergency changes have carried a mandatory,
 *       constraint-backed ratification clock since {@code V345}. The contract was five states short
 *       and wrong about the first thing an auditor asks. That is the {@code advisory}-javadoc
 *       failure of F2-95 in documentation form: the word in the comment <em>was</em> the finding.</li>
 *   <li>{@code PROJECT_CHRONICLE.md} — the handbook {@code CLAUDE.md} instructs everyone to read
 *       before deep work, and whose §3 is titled "every module, screen, API" — mentioned
 *       {@code change_mgmt} <b>zero</b> times in 1,145 lines, after twelve slices of governance
 *       machinery had shipped.</li>
 * </ul>
 *
 * <h3>What is gated, and what deliberately is not</h3>
 * <p>Only claims with a single mechanical source of truth: the transition table, the decision-target
 * set, the {@code CHANGE} actions the controllers actually demand, the KPI count, and the existence
 * of every API path the documents cite. Prose, rationale and measured row counts are <b>not</b>
 * gated — row counts move with every seed and gating them would make this the test everybody
 * disables (F2-70's lesson).
 *
 * <p>The vocabularies are read by <b>reflection over the running classes</b>, not by regex over
 * source: RC-20 is this repository's own record of a census that counted {@code @EntityListeners} as
 * an entity. Only the endpoint scan parses source, because a mapping annotation's path is not
 * reachable without a Spring context — and that scan carries an anti-vacuity floor so a broken
 * regex cannot read as a pass.
 *
 * <p>Path matching treats a controller's {@code {placeholder}} segment as a wildcard, so a document
 * may cite {@code /authority/CAB} against {@code /authority/{gate}}. The honest consequence is that
 * a cited two-segment path can match a placeholder route it was not describing; this test catches
 * <em>renamed and deleted</em> endpoints, not mistyped ids.
 *
 * <h3>How to fix a failure</h3>
 * <p>Change the document, not the expectation — the code is the source of truth here. If a state,
 * decision target or endpoint genuinely went away, say so in the document in the same commit.
 */
class ChangeDocumentationFreshnessTest {

    private static final Path CHRONICLE = Path.of("..", "PROJECT_CHRONICLE.md");
    private static final Path RUNBOOK =
        Path.of("..", "docs", "change-manager", "CAB_OPERATING_RUNBOOK.md");
    private static final Path CONTRACT = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "change_mgmt", "package-info.java");
    private static final List<Path> CONTROLLERS = List.of(
        Path.of("src", "main", "java", "com", "tss", "grc", "module", "change_mgmt", "controller",
                "ChangeManagementController.java"),
        Path.of("src", "main", "java", "com", "tss", "grc", "module", "change_mgmt", "controller",
                "ChangeAuthorityController.java"),
        Path.of("src", "main", "java", "com", "tss", "grc", "module", "reporting", "controller",
                "ExportController.java"));

    /** The chronicle section CE-16 added. Everything gated against the chronicle is read from here. */
    private static final String CHRONICLE_SECTION_HEADING = "### 3.10 Change Enablement & CAB";

    // ── sources of truth ────────────────────────────────────────────────────

    /** Every status the machine can be in: the transition table's keys and all its targets. */
    private static Set<String> statesFromTheMachine() {
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> transitions =
            (Map<String, Set<String>>) staticField(ChangeManagementService.class, "NORMAL_TRANSITIONS");
        Set<String> all = new TreeSet<>(transitions.keySet());
        transitions.values().forEach(all::addAll);
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> decisionTargets() {
        return new TreeSet<>((Set<String>) staticField(ChangeManagementService.class, "DECISION_TARGETS"));
    }

    private static Object staticField(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                "Cannot read " + type.getSimpleName() + "." + name + " — this test is pinned to it. "
                + "If it was renamed, update this test rather than deleting the assertion.", e);
        }
    }

    // ── the contract ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the module contract names every state the machine can reach")
    void contractNamesEveryState() {
        String contract = read(CONTRACT);
        Set<String> states = statesFromTheMachine();
        assertThat(states).as("anti-vacuity: the transition table must be readable").hasSizeGreaterThanOrEqualTo(16);

        List<String> missing = states.stream().filter(s -> !contract.contains(s)).toList();
        assertThat(missing)
            .as("package-info.java is the module contract and these states exist in code but not in "
                + "it. This is exactly how it came to describe a ten-state lifecycle for a "
                + "sixteen-state machine. Add them to the diagram.")
            .isEmpty();
    }

    @Test
    @DisplayName("the contract does not claim the emergency path bypasses the CAB")
    void contractDoesNotClaimEmergencyBypass() {
        String contract = read(CONTRACT).toLowerCase();
        // The literal sentence that was there, and any close variant of it.
        assertThat(contract)
            .as("An emergency change skips the pre-approval sitting, not the authorisation: "
                + "ratification is mandatory, clocked by ck_change_ratification_clock and swept "
                + "daily. Describing it as a bypass tells a reader the control does not exist.")
            .doesNotContain("bypasses cab")
            .doesNotContain("bypasses the cab")
            .doesNotContain("bypass the cab");
        assertThat(contract)
            .as("the contract must say what replaces the sitting")
            .contains("ratification");
    }

    // ── the chronicle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PROJECT_CHRONICLE documents the change module at all")
    void chronicleHasAChangeSection() {
        String chronicle = read(CHRONICLE);
        assertThat(chronicle)
            .as("§3 of the chronicle is titled 'every module, screen, API' and mentioned this "
                + "module zero times for twelve slices. CE-16 added §3.10.")
            .contains(CHRONICLE_SECTION_HEADING);
        assertThat(chronicleSection())
            .as("anti-vacuity: a heading with nothing under it is not documentation")
            .hasSizeGreaterThan(1_500);
    }

    @Test
    @DisplayName("the chronicle's lifecycle names every state the machine can reach")
    void chronicleNamesEveryState() {
        String section = chronicleSection();
        List<String> missing = statesFromTheMachine().stream().filter(s -> !section.contains(s)).toList();
        assertThat(missing)
            .as("§3.10 publishes the lifecycle. A state that exists in code and not there is a "
                + "state nobody reading the handbook knows a change can be in.")
            .isEmpty();
    }

    @Test
    @DisplayName("every CHANGE action a controller demands is documented somewhere")
    void everyDemandedActionIsDocumented() {
        Set<String> demanded = new TreeSet<>();
        Matcher m = Pattern.compile("hasPermission\\('CHANGE'\\s*,\\s*'([A-Z_]+)'\\)")
                           .matcher(String.join("\n", CONTROLLERS.stream().map(this::read).toList()));
        while (m.find()) demanded.add(m.group(1));

        assertThat(demanded).as("anti-vacuity: the guard scan must find something")
            .contains("READ", "WRITE", "APPROVE");

        String docs = chronicleSection() + "\n" + read(RUNBOOK);
        List<String> undocumented = demanded.stream()
            .filter(a -> !docs.contains("CHANGE:" + a) && !docs.contains("`" + a + "`"))
            .toList();
        assertThat(undocumented)
            .as("A permission an endpoint demands but no document names is a 403 nobody can "
                + "diagnose. Name it in §3.10 or the runbook.")
            .isEmpty();
    }

    @Test
    @DisplayName("the KPI count the documents publish is the count the service serves")
    void kpiCountMatches() {
        int served = ChangeKpiService.ALL_KEYS.size();
        assertThat(served).as("anti-vacuity").isGreaterThan(0);

        for (Path doc : List.of(CHRONICLE, RUNBOOK)) {
            String text = doc.equals(CHRONICLE) ? chronicleSection() : read(doc);
            Matcher m = Pattern.compile("\\*{0,2}(\\d+)\\*{0,2}\\s+ITIL/ISO metrics").matcher(text);
            assertThat(m.find())
                .as("%s must state how many KPIs the pack publishes — a metric pack whose size is "
                    + "unstated cannot be checked against the service", doc)
                .isTrue();
            assertThat(Integer.parseInt(m.group(1)))
                .as("%s publishes a KPI count that no longer matches ChangeKpiService.ALL_KEYS "
                    + "(%d). Update the document.", doc, served)
                .isEqualTo(served);
        }
    }

    @Test
    @DisplayName("the runbook's decision table covers every recorded CAB decision")
    void runbookCoversEveryDecisionTarget() {
        String runbook = read(RUNBOOK);
        Set<String> targets = decisionTargets();
        assertThat(targets).as("anti-vacuity").hasSizeGreaterThanOrEqualTo(5);

        List<String> missing = targets.stream().filter(t -> !runbook.contains(t)).toList();
        assertThat(missing)
            .as("A decision the CAB can record, that the operating runbook does not explain, is a "
                + "decision the secretary will not know to use. S7c shipped CAB_DEFERRED and "
                + "MORE_INFO_REQUIRED precisely so the committee could say those things.")
            .isEmpty();
    }

    @Test
    @DisplayName("the runbook leads with the two prerequisites that fail closed")
    void runbookNamesTheFailClosedPrerequisites() {
        String runbook = read(RUNBOOK);
        assertThat(runbook)
            .as("This runbook exists because the authority model fails closed: an unmapped gate or "
                + "an unconstituted committee refuses every CAB approval (ADR-017 D-2, F2-96). If "
                + "it does not say so, it is not the document an operator needed.")
            .contains("committee_gate_map")
            .contains("governance-health")
            .contains("voting");
    }

    @Test
    @DisplayName("every API path the documents cite still exists")
    void everyCitedPathExists() {
        Set<String> mapped = mappedPaths();
        assertThat(mapped).as("anti-vacuity: the controller scan must find paths")
            .hasSizeGreaterThan(30);

        Set<String> cited = new LinkedHashSet<>();
        cited.addAll(citedPaths(chronicleSection()));
        cited.addAll(citedPaths(read(RUNBOOK)));
        cited.addAll(citedPaths(read(CONTRACT)));
        assertThat(cited).as("anti-vacuity: the documents must cite endpoints at all")
            .hasSizeGreaterThan(8);

        List<String> dead = cited.stream().filter(c -> mapped.stream().noneMatch(p -> shapeMatches(p, c))).toList();
        assertThat(dead)
            .as("These paths appear in the documentation and in no controller. Either the endpoint "
                + "moved (fix the document) or the document invented it (worse — an operator will "
                + "try it). Scanned: %s", CONTROLLERS)
            .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** §3.10 up to the next {@code ###}/{@code ##} heading. */
    private String chronicleSection() {
        String chronicle = read(CHRONICLE);
        int start = chronicle.indexOf(CHRONICLE_SECTION_HEADING);
        if (start < 0) return "";
        int next = chronicle.indexOf("\n## ", start);
        int nextSub = chronicle.indexOf("\n### ", start + CHRONICLE_SECTION_HEADING.length());
        int end = next < 0 ? chronicle.length() : next;
        if (nextSub >= 0 && nextSub < end) end = nextSub;
        return chronicle.substring(start, end);
    }

    /** Full request paths declared by the scanned controllers, class mapping + method mapping. */
    private Set<String> mappedPaths() {
        Set<String> out = new LinkedHashSet<>();
        Pattern classMapping = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
        Pattern methodMapping = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch)Mapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"");
        Pattern bareMapping = Pattern.compile("@(?:Get|Post|Put|Delete|Patch)Mapping\\s*(?:\\(\\s*\\))?\\s*\\n");
        for (Path c : CONTROLLERS) {
            String src = read(c);
            Matcher cm = classMapping.matcher(src);
            String base = cm.find() ? cm.group(1) : "";
            Matcher mm = methodMapping.matcher(src);
            while (mm.find()) out.add(join(base, mm.group(1)));
            if (bareMapping.matcher(src).find()) out.add(base);
        }
        return out;
    }

    /** Absolute {@code /api/...} paths cited in a document, with brace-lists expanded. */
    private Set<String> citedPaths(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(/api/v\\d+/[A-Za-z0-9._,{}/\\-]*[A-Za-z0-9._}\\-])").matcher(text);
        while (m.find()) {
            for (String expanded : expandBraceLists(m.group(1))) {
                // Trim a trailing punctuation mark markdown leaves behind.
                String p = expanded.replaceAll("[.,;)]+$", "");
                if (p.startsWith("/api/")) out.add(p);
            }
        }
        return out;
    }

    /** {@code a/{x,y}.pdf} → {@code a/x.pdf}, {@code a/y.pdf}. A single-token brace stays a placeholder. */
    private static List<String> expandBraceLists(String path) {
        int open = path.indexOf('{');
        if (open < 0) return List.of(path);
        int close = path.indexOf('}', open);
        if (close < 0) return List.of(path);
        String body = path.substring(open + 1, close);
        if (!body.contains(",")) {
            List<String> tails = expandBraceLists(path.substring(close + 1));
            List<String> out = new ArrayList<>();
            for (String tail : tails) out.add(path.substring(0, close + 1) + tail);
            return out;
        }
        List<String> out = new ArrayList<>();
        for (String option : body.split(",")) {
            String candidate = path.substring(0, open) + option.trim() + path.substring(close + 1);
            out.addAll(expandBraceLists(candidate));
        }
        return out;
    }

    /**
     * Concrete values a document may legitimately write where the route declares a placeholder —
     * {@code PUT /authority/CAB} against {@code PUT /authority/{gate}}. Anything not listed here is
     * treated as a path name and must match literally.
     *
     * <p>⚠ This list is the reason the endpoint check has teeth. The first version of
     * {@link #shapeMatches} let <em>any</em> mapped placeholder absorb <em>any</em> cited literal, and
     * a deliberate mutation proved it: renaming the runbook's {@code governance-health} to
     * {@code governance-healthz} was silently accepted, because {@code /changes/{id}} has the same
     * segment count. A check that cannot fail is not a check.
     */
    private static final Set<String> PATH_PARAMETER_VALUES = Set.of("CAB", "ECAB");

    /**
     * Segment-wise match. A placeholder on either side matches a placeholder on the other, and a
     * cited literal matches a mapped placeholder <b>only</b> when the literal is a declared path
     * parameter value ({@link #PATH_PARAMETER_VALUES}).
     */
    private static boolean shapeMatches(String mapped, String cited) {
        String[] a = mapped.split("/", -1);
        String[] b = cited.split("/", -1);
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            boolean mappedVar = a[i].startsWith("{") && a[i].endsWith("}");
            boolean citedVar = b[i].startsWith("{") && b[i].endsWith("}");
            if (mappedVar && (citedVar || PATH_PARAMETER_VALUES.contains(b[i]))) continue;
            if (a[i].equals(b[i])) continue;
            return false;
        }
        return true;
    }

    private static String join(String base, String suffix) {
        if (suffix == null || suffix.isEmpty()) return base;
        String s = suffix.startsWith("/") ? suffix : "/" + suffix;
        return base + s;
    }

    private String read(Path p) {
        Assumptions.assumeTrue(Files.exists(p), "not present in this checkout: " + p);
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
