package com.tss.grc.security;

import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-303 — a refused state transition must say what it WOULD have accepted, and it must be a
 * conflict rather than bad input.
 *
 * <h3>The measurement this exists to hold</h3>
 *
 * {@code IllegalTransitionException} carries {@code from}, {@code to} and the {@code allowed} set,
 * and {@code GlobalExceptionHandler} maps it to <b>409</b> with a structured ProblemDetail.
 * <b>139 sites</b> used it. Exactly <b>five</b> threw a bare
 * {@code IllegalArgumentException("Illegal transition: " + from + " → " + to)} — a <b>400</b> naming
 * neither the legal targets nor a route — in {@code BcmBiaAssessmentService},
 * {@code BcpPlanService}, {@code CrisisEventService}, {@code AuthorityDocumentService} and
 * {@code QuestionnaireService}.
 *
 * <p>🔑 <b>The two halves had one cause.</b> Each of the five declared its edges as an inline
 * {@code switch} of {@code Set.of(...).contains(...)}, so <b>the legal-target list was not
 * derivable at runtime</b> — the refusal could not name it because nothing could be asked. And
 * {@code CrisisCommsTemplateService}, the one member whose refusal DID name its targets, paid for
 * that with a <b>second copy of the whole machine</b> in an {@code allowedFrom} helper: three
 * copies in one file counting its javadoc, agreeing, with nobody maintaining that ([[F2-177]]).
 *
 * <h3>Why the vocabulary check in front of each guard stays a 400</h3>
 *
 * An unknown token <em>is</em> bad input; a known token from the wrong state is a conflict with the
 * record's state. Three of the five kept a separate {@code STATUSES.contains(...)} refusal and those
 * are deliberately untouched — conflating the two is exactly what the bare refusal did, and it is
 * the same family as [[F2-230]]'s 547-naming-no-field and [[F2-210]]'s diagnostic that sends the
 * reader nowhere.
 *
 * <h3>Why a hard zero with no exemption map is safe here</h3>
 *
 * {@code IllegalTransitionException} is always available and a declared
 * {@code Map<String, Set<String>>} is always available, so there is no correct code for this to fail
 * on — the one failure a gate must never have ([[F2-214]]).
 */
class TransitionRefusalContractTest {

    /**
     * A refusal that names the rejected move and nothing else. Scoped to messages that mention a
     * <b>transition</b>: {@code IllegalArgumentException} is the right type for malformed input, and
     * banning it outright would fail on the vocabulary checks this slice deliberately kept.
     */
    private static final Pattern BARE_TRANSITION_REFUSAL = Pattern.compile(
        "new\\s+IllegalArgumentException\\s*\\(\\s*\"[^\"]*[Tt]ransition");

    private static final Pattern TYPED_REFUSAL =
        Pattern.compile("new\\s+IllegalTransitionException\\s*\\(");

    /**
     * Anti-vacuity: 145 sites throw the typed refusal after this slice. If this scan ever sees a
     * handful, the pattern broke and the hard zero below would be asserted over nothing — the
     * direction a broken scan always fails in.
     */
    private static final int MIN_TYPED_REFUSALS = 100;

    /**
     * The six machines this slice declared, and one state of each whose outgoing edges the refusal
     * must be able to name. Held as data so the leg below reads the real constant by name rather
     * than trusting that a {@code Map} exists somewhere in the file.
     */
    private static final Map<String, String> DECLARED_MACHINES = new LinkedHashMap<>(Map.of(
        "module/bcm/cycle/service/BcmBiaAssessmentService.java",           "TRANSITIONS",
        "module/bcm/cycle/service/BcpPlanService.java",                    "TRANSITIONS",
        "module/bcm/cycle/service/CrisisEventService.java",                "TRANSITIONS",
        "module/platform/regulatory/service/AuthorityDocumentService.java", "TRANSITIONS",
        "module/tprm/service/QuestionnaireService.java",                   "TEMPLATE_TRANSITIONS",
        "module/platform/service/IssueService.java",                       "TRANSITIONS",
        "module/bcm/comms/service/CrisisCommsTemplateService.java",        "TRANSITIONS"));

    private static final Path MAIN = Paths.get("src", "main", "java").toAbsolutePath().normalize();

    /**
     * The services that still refuse a transition with a bare {@code IllegalArgumentException},
     * asserted <b>exactly</b>: a new one fails, and a converted one must be deleted from here in the
     * same commit.
     *
     * <h4>Why this is a census and not yet a hard zero</h4>
     *
     * ⚠⚠ F2-303 measured this population with a grep for {@code IllegalArgumentException("Illegal}
     * and found <b>five</b>. This gate, scoped to the SHAPE (any transition-mentioning message)
     * rather than to a spelling, finds <b>eleven</b> — the others say "Cannot transition from",
     * "Illegal BIA transition", "Illegal template transition". 🔑 <b>A detector keyed on the wording
     * you have already seen returns a smaller population, and a smaller population reads exactly
     * like a cleaner tree.</b> One of them is a SECOND refusal inside
     * {@code AuthorityDocumentService}, a file that slice had already converted.
     *
     * <p>⚠⚠ [[F2-304]] read all six, and they split <b>four</b> ways — which is why the census
     * shipped with no verdicts rather than six manufactured ones ([[F2-188]]):
     *
     * <ul>
     *   <li><b>Three</b> had {@code allowed} already in a local and <b>threw it away</b>
     *       ({@code AmlAlertService}, {@code DisclosureService}, {@code EthicsCaseService}) — one-line
     *       fixes, and the same shape as {@code ChangeManagementService.guard} before it.</li>
     *   <li><b>One</b> was an inline switch with no declared map ({@code IssueService}), and its
     *       refusal is the one that explains the under-count: the message sat on a <b>continuation
     *       line</b>, so a pattern anchored on {@code IllegalArgumentException("Illegal} could never
     *       match it. 🔑 Wording was only half the reason a single-line grep found five of eleven.</li>
     *   <li><b>One</b> was a different SHAPE: {@code WorkflowEngineService} refuses an action code
     *       that has no row for the instance's current state. A conflict, and the available action
     *       codes were already queryable — so the platform's engine, adopted by 14 modules, now
     *       names them.</li>
     *   <li><b>One is not a transition refusal at all</b> and stays a 400 — see the map below. The
     *       pattern over-matches it, which is a fact about the pattern rather than about the code.</li>
     * </ul>
     *
     * <p>So the enforceable destination was never "zero refusals matching this pattern"; it is
     * "zero refusals matching it that are actually lifecycle refusals". One entry remains, named.
     */
    private static final Map<String, String> UNREAD_BARE_REFUSALS = Map.of(
        "module/platform/regulatory/service/AuthorityDocumentService.java",
        "NOT a transition refusal — F2-304 read it. \"supersededById is required when transitioning "
        + "to SUPERSEDED\" is a REQUIRED-FIELD refusal that merely mentions transitioning, and 400 "
        + "is the right answer for a missing field. This entry exists because the pattern above is "
        + "scoped to any transition-mentioning message, which over-matches here; narrowing it on the "
        + "word \"required\" would silently exempt every future message containing it, so the "
        + "exemption is named instead and the leg below keeps it honest.");

    @Test
    void noServiceRefusesATransitionWithoutNamingWhatItWouldAccept() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : services().entrySet()) {
            String code = GuardExpressions.stripComments(e.getValue());
            Matcher m = BARE_TRANSITION_REFUSAL.matcher(code);
            while (m.find()) {
                offenders.add(e.getKey());
            }
        }

        List<String> found = offenders.stream().distinct().sorted().toList();
        List<String> newOnes = found.stream()
            .filter(f -> !UNREAD_BARE_REFUSALS.containsKey(f))
            .toList();
        List<String> fixed = UNREAD_BARE_REFUSALS.keySet().stream()
            .filter(k -> !found.contains(k))
            .sorted()
            .toList();

        assertThat(fixed).as(
            "These no longer refuse with a bare IllegalArgumentException — delete them from "
            + "UNREAD_BARE_REFUSALS in the same commit, which is the census working: %s", fixed)
            .isEmpty();

        assertThat(newOnes).as("""
            A service refuses a state transition with a bare IllegalArgumentException.

            That is an HTTP 400 — malformed input — for a request that was well formed and merely \
            arrived from the wrong state, and its message can name only the move that was \
            REJECTED, never the moves that would have been accepted.

            Use IllegalTransitionException(from, to, allowed): GlobalExceptionHandler maps it to \
            409 with from/to/allowed in a structured ProblemDetail, and 145 sites already do. If \
            the machine cannot tell you its allowed set, that is the defect — declare its edges as \
            a Map<String, Set<String>> rather than an inline switch, which is what F2-303 did to \
            the last five.

            ⚠ A vocabulary check ("status must be one of ...") is NOT this: an unknown token really \
            is bad input. This pattern is scoped to messages mentioning a transition for exactly \
            that reason.

            %s""".formatted(String.join("\n            ", newOnes)))
            .isEmpty();
    }

    /**
     * The one exemption stays honest in both directions: the message must still match the pattern —
     * so a reworded or deleted message forces the entry out — and it must still be the
     * required-field refusal the reason claims, not a lifecycle refusal that drifted into it.
     */
    @Test
    void theOneExemptionIsStillTheRequiredFieldRefusalItClaimsToBe() throws IOException {
        String rel = "module/platform/regulatory/service/AuthorityDocumentService.java";
        String code = GuardExpressions.stripComments(services().get(rel));

        assertThat(BARE_TRANSITION_REFUSAL.matcher(code).find()).as(
            "The exempted message no longer matches the pattern, so the exemption describes nothing. "
            + "Delete the UNREAD_BARE_REFUSALS entry — an exemption for something not in the "
            + "population reads exactly like one for something that is (F2-199).")
            .isTrue();

        assertThat(code).as(
            "The exemption's reason says this is a REQUIRED-FIELD refusal, which is why 400 is "
            + "correct. If that message is gone, the reason is stale and the site needs re-reading.")
            .contains("supersededById is required when transitioning to SUPERSEDED");

        // and the file's real lifecycle refusal must still be the typed one F2-303 converted
        assertThat(code).as(
            "This file's actual transition refusal must remain the typed 409 — the point of the "
            + "exemption is that the file has BOTH kinds, and only one of them is a 400.")
            .contains("new IllegalTransitionException(from, to, allowed)");
    }

    /** The floor, so the ban above cannot pass by matching nothing. */
    @Test
    void theTypedRefusalIsTheOverwhelmingConvention() throws IOException {
        long typed = services().values().stream()
            .map(GuardExpressions::stripComments)
            .mapToLong(s -> TYPED_REFUSAL.matcher(s).results().count())
            .sum();

        assertThat(typed).as(
            "Sites throwing IllegalTransitionException: %d. This is the population the ban above "
            + "protects; if it collapsed, the scan broke and the zero means nothing.", typed)
            .isGreaterThanOrEqualTo(MIN_TYPED_REFUSALS);
    }

    /**
     * Each converted machine declares its edges <b>once</b>, and the inline {@code case "STATE" ->}
     * arms are gone. This is the half that catches the {@code allowedFrom} shape: a second switch
     * over the same states, written so the refusal could name its targets, is how
     * {@code CrisisCommsTemplateService} came to hold three copies of one machine.
     */
    @Test
    void everyDeclaredMachineHoldsItsEdgesInExactlyOnePlace() throws IOException {
        Map<String, String> problems = new TreeMap<>();
        for (Map.Entry<String, String> e : DECLARED_MACHINES.entrySet()) {
            Path p = MAIN.resolve(Paths.get("com", "tss", "grc").resolve(e.getKey()));
            assertThat(Files.exists(p)).as("declared machine file is missing: %s", p).isTrue();
            String code = GuardExpressions.stripComments(Files.readString(p));

            String decl = "Map<String, Set<String>> " + e.getValue() + " = Map.of(";
            long declarations = code.split(Pattern.quote(decl), -1).length - 1;
            if (declarations != 1) {
                problems.put(e.getKey(), "declares " + e.getValue() + " " + declarations
                             + " times, expected exactly 1");
                continue;
            }

            // A `case "X" ->` arm is a second copy of an edge only when it YIELDS a machine: a
            // set of states, or a comparison against the requested target.
            //
            // ⚠ The first draft banned the arm outright and fired on
            // CrisisEventService's `case "DETECTED" -> "بحاجة للتقييم"` — an Arabic label map, which
            // legitimately switches on the same states. A gate that fails on correct code trains
            // the next reader to edit the assertion rather than the code ([[F2-214]]), and a label
            // switch is house style on every register with a bilingual UI. Scope the ban to the
            // shape of its claim, never to the token.
            for (String token : tokensOf(code, decl)) {
                Matcher arm = Pattern.compile(
                    "case\\s+\"" + Pattern.quote(token) + "\"[^\\n]*->([^\\n;]*)").matcher(code);
                while (arm.find()) {
                    String yields = arm.group(1);
                    if (yields.contains("Set.of(") || yields.contains(".contains(")
                        || yields.contains(".equals(to")) {
                        problems.put(e.getKey(), "`case \"" + token + "\"` still yields a machine ("
                                     + yields.trim() + ") — the edges are written twice");
                        break;
                    }
                }
            }
        }

        assertThat(problems).as("""
            A declared machine's edges must live in one place. A second switch over the same states \
            — even one written so a refusal could name its targets, which is exactly why \
            CrisisCommsTemplateService had an `allowedFrom` helper — is a copy nobody maintains \
            (F2-177), and the two were written in different FORMS ("X".equals(to) against \
            Set.of("X")), which is what makes a drift between them invisible in a diff.

            %s""".formatted(problems))
            .isEmpty();
    }

    /**
     * The point of the whole slice, asserted rather than assumed: a machine can be ASKED what it
     * allows. Terminal states are declared with an empty set rather than omitted, so the terminal
     * set is derivable from the machine instead of typed out a second time ([[F2-196]]) — and a
     * refusal from a terminal state says {@code allowed: []} rather than staying silent.
     */
    @Test
    void everyDeclaredMachineCanBeAskedWhatItAllows() throws IOException {
        List<String> thin = new ArrayList<>();
        for (Map.Entry<String, String> e : DECLARED_MACHINES.entrySet()) {
            Path p = MAIN.resolve(Paths.get("com", "tss", "grc").resolve(e.getKey()));
            String code = GuardExpressions.stripComments(Files.readString(p));
            String decl = "Map<String, Set<String>> " + e.getValue() + " = Map.of(";
            Set<String> tokens = tokensOf(code, decl);
            if (tokens.size() < 3) {
                thin.add(e.getKey() + " declares only " + tokens.size() + " states: " + tokens);
            }
            // and the refusal must be fed FROM the map, never a literal set
            if (!code.contains("getOrDefault(")) {
                thin.add(e.getKey() + " never reads its own map with getOrDefault");
            }
        }
        assertThat(thin).as(
            "A declared machine that names fewer than three states, or whose refusal does not read "
            + "the map, is not the thing this slice built:\n  %s", String.join("\n  ", thin))
            .isEmpty();
    }

    /**
     * F2-305 — where a machine's edges are ALSO written in prose, the prose must agree with the map
     * edge for edge.
     *
     * <h4>Why only four files, and why that is the honest answer</h4>
     *
     * ⚠⚠ The obvious version of this leg — parse every {@code A → B} in every comment — was built
     * and <b>rejected</b>. It reported 6 disagreements across 8 machines and almost all were the
     * detector's fault: this codebase documents lifecycles as <b>ASCII-art happy paths</b>
     * ({@code DRAFT → UNDER_REVIEW ─┬─> APPROVED}), as narrative chains inside
     * {@code @Operation(summary = "Move a case through INTAKE → TRIAGE → …")}, as single-edge
     * remarks in code comments, and in one case as an <b>HTTP transcript</b>. 🔑 <b>An arrow in
     * prose is not a claim about a complete outgoing set, and a gate that treats it as one fails on
     * correct code</b> ([[F2-214]]).
     *
     * <p>So the population is files whose {@code <pre>} block is a real table — one
     * {@code FROM -> A | B} line per state, the form {@code CrisisCommsTemplateService} already
     * used. F2-305 rewrote three ASCII-art diagrams into that form <em>because they were
     * incomplete</em>: they documented 6 of 11, 7 of 11 and 8 of 9 edges respectively, so a reader
     * consulting one to learn what was legal got a narrower answer than the machine — F2-303's
     * defect one layer out, in the artefact a developer reads before writing a client.
     *
     * <p>⚙ The four other machines have no such table and are deliberately not gated. Inventing one
     * so the leg could cover them would be documentation written to satisfy a test.
     */
    @Test
    void everyDocumentedEdgeTableAgreesWithItsMap() throws IOException {
        Map<String, String> documented = Map.of(
            "module/bcm/cycle/service/BcmBiaAssessmentService.java",    "TRANSITIONS",
            "module/bcm/cycle/service/BcpPlanService.java",             "TRANSITIONS",
            "module/bcm/cycle/service/CrisisEventService.java",         "TRANSITIONS",
            "module/bcm/comms/service/CrisisCommsTemplateService.java", "TRANSITIONS");

        Map<String, String> problems = new TreeMap<>();
        int edgesChecked = 0;

        for (Map.Entry<String, String> e : documented.entrySet()) {
            Path p = MAIN.resolve(Paths.get("com", "tss", "grc").resolve(e.getKey()));
            String raw = Files.readString(p);
            Map<String, Set<String>> map = declaredEdges(
                GuardExpressions.stripComments(raw), e.getValue());
            Map<String, Set<String>> table = documentedEdges(raw);

            if (map.isEmpty()) {
                problems.put(e.getKey(), "no " + e.getValue() + " map could be parsed");
                continue;
            }
            if (table.isEmpty()) {
                problems.put(e.getKey(), "no <pre> edge table could be parsed — if the diagram was "
                             + "rewritten as art, either restore the table form or remove this "
                             + "file from the leg deliberately");
                continue;
            }
            if (!table.keySet().equals(map.keySet())) {
                problems.put(e.getKey(), "table documents states " + new TreeSet<>(table.keySet())
                             + " and the map declares " + new TreeSet<>(map.keySet()));
                continue;
            }
            for (String from : map.keySet()) {
                if (!table.get(from).equals(map.get(from))) {
                    problems.put(e.getKey() + " :: " + from,
                                 "table says " + new TreeSet<>(table.get(from))
                                 + ", map allows " + new TreeSet<>(map.get(from)));
                }
                edgesChecked += map.get(from).size();
            }
        }

        assertThat(problems).as("""
            A documented edge table disagrees with the machine it documents.

            These <pre> blocks are the specification, not a summary: F2-305 rewrote three of them             because as ASCII-art happy paths they documented 6 of 11, 7 of 11 and 8 of 9 edges, and             a reader consulting one to learn what is legal got a narrower answer than the machine.

            Fix whichever is wrong. If the MAP gained an edge, document it; if the map lost one, the             table must lose it too.

            %s""".formatted(problems))
            .isEmpty();

        assertThat(edgesChecked).as(
            "Edges compared: %d. If this collapsed, one of the two parsers stopped matching and the "
            + "agreement above is being asserted over almost nothing.", edgesChecked)
            .isGreaterThanOrEqualTo(30);
    }

    /** {@code FROM -> A | B} lines inside a {@code <pre>} block; {@code (terminal)} means no edges. */
    private static Map<String, Set<String>> documentedEdges(String raw) {
        Map<String, Set<String>> out = new TreeMap<>();
        Matcher block = Pattern.compile("<pre>([\\s\\S]*?)</pre>").matcher(raw);
        while (block.find()) {
            Matcher line = Pattern.compile(
                "^\\s*\\*?\\s*([A-Z][A-Z_]*)\\s*(?:->|\u2192)\\s*(.+)$", Pattern.MULTILINE)
                .matcher(block.group(1));
            Map<String, Set<String>> found = new TreeMap<>();
            while (line.find()) {
                String rhs = line.group(2).trim();
                Set<String> targets = new java.util.LinkedHashSet<>();
                if (!rhs.startsWith("(terminal)")) {
                    Matcher t = Pattern.compile("([A-Z][A-Z_]*)").matcher(rhs);
                    while (t.find()) {
                        targets.add(t.group(1));
                    }
                }
                found.put(line.group(1), targets);
            }
            // a block qualifies as a table only if it is ALL edge lines -- an ASCII diagram or an
            // HTTP transcript yields one or two accidental matches and must not be treated as one
            if (found.size() >= 3) {
                out.putAll(found);
            }
        }
        return out;
    }

    /** {@code Map.of(...)} / {@code Map.ofEntries(...)} machine edges, parsed from source. */
    private static Map<String, Set<String>> declaredEdges(String code, String constant) {
        Matcher m = Pattern.compile(
            "Map<String,\\s*Set<String>>\\s+" + Pattern.quote(constant)
            + "\\s*=\\s*Map\\.(?:of|ofEntries)\\(").matcher(code);
        if (!m.find()) {
            return Map.of();
        }
        int depth = 1;
        int i = m.end();
        while (i < code.length() && depth > 0) {
            if (code.charAt(i) == '(') {
                depth++;
            } else if (code.charAt(i) == ')') {
                depth--;
            }
            i++;
        }
        String body = code.substring(m.end(), Math.max(m.end(), i - 1));
        Map<String, Set<String>> out = new TreeMap<>();
        Matcher pair = Pattern.compile(
            "\"([A-Z][A-Z_]*)\"\\s*,\\s*Set\\.of\\(([^)]*)\\)").matcher(body);
        while (pair.find()) {
            Set<String> targets = new java.util.LinkedHashSet<>();
            Matcher t = Pattern.compile("\"([A-Z][A-Z_]*)\"").matcher(pair.group(2));
            while (t.find()) {
                targets.add(t.group(1));
            }
            out.put(pair.group(1), targets);
        }
        return out;
    }

    /** The keys of a {@code Map.of(...)} machine declaration. */
    private static Set<String> tokensOf(String code, String decl) {
        int at = code.indexOf(decl);
        if (at < 0) {
            return Set.of();
        }
        int end = code.indexOf(");", at);
        String body = end < 0 ? code.substring(at) : code.substring(at, end);
        Set<String> out = new java.util.LinkedHashSet<>();
        Matcher m = Pattern.compile("\"([A-Z][A-Z_]+)\"\\s*,\\s*Set\\.of\\(").matcher(body);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Every shipped {@code *Service.java}, keyed by path so two files of one name cannot merge. */
    private static Map<String, String> services() throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(MAIN)) {
            for (Path p : walk.filter(f -> f.getFileName().toString().endsWith("Service.java"))
                              .toList()) {
                // Keyed as `module/...`, the same space DECLARED_MACHINES and
                // UNREAD_BARE_REFUSALS use, so a path mismatch cannot make a leg vacuous.
                String rel = MAIN.relativize(p).toString().replace('\\', '/')
                    .replaceFirst("^com/tss/grc/", "");
                out.put(rel, Files.readString(p));
            }
        }
        assertThat(out.size()).as(
            "Service files found under %s: %d. A collapsed walk would make every leg here vacuous.",
            MAIN, out.size())
            .isGreaterThan(200);
        return out;
    }
}
