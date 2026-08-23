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
     * <p>Six are left <b>unread</b>. Each needs its own machine examined — whether it has a declared
     * map to name, whether its refusal is even a lifecycle refusal — and writing six reasons for
     * code nobody has opened is precisely the failure [[F2-188]] measured, where five claims were
     * written fluently and all five were false. The reason here is the honest one: not yet read.
     *
     * <p>The destination is zero. {@code IllegalTransitionException} is always available, so there
     * is no correct code standing in the way — only reading.
     */
    private static final Map<String, String> UNREAD_BARE_REFUSALS = Map.of(
        "module/ethics/service/AmlAlertService.java",                       "not yet read",
        "module/ethics/service/DisclosureService.java",                     "not yet read",
        "module/ethics/service/EthicsCaseService.java",                     "not yet read",
        "module/platform/regulatory/service/AuthorityDocumentService.java", "not yet read — a SECOND refusal in a file F2-303 converted",
        "module/platform/service/IssueService.java",                        "not yet read",
        "module/workflow/service/WorkflowEngineService.java",               "not yet read");

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
