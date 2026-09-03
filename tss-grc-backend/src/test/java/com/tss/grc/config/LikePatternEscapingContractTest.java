package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-449 — a caller-supplied search term IS the {@code LIKE} pattern unless something escapes it.
 *
 * <p>SQL Server treats {@code %}, {@code _} and a <b>closed</b> {@code [..]} as pattern
 * metacharacters, and {@code LIKE} has <b>no default escape character</b> — an {@code ESCAPE}
 * clause has to be declared. Every search query here builds its pattern as
 * {@code CONCAT('%', :q, '%')}, so the caller's own string is the pattern. Probed live pre-fix:
 * {@code ?search=%}, {@code ?search=_} and {@code ?search=[ac]} each returned the <b>whole
 * register</b> (7 of 7 users, 35 of 35 compliance controls) where a nonsense term returned 0.
 *
 * <p>🔑 This is house rule 5's own recorded trap — <i>{@code [REVIEW-TEST]} in a {@code LIKE} is a
 * character class</i> — asked of <b>user input</b> rather than of test data, which is a question
 * nobody had put to it.
 *
 * <p>⚠⚠ <b>The population must come from REFLECTION, and a source scan under-reports it in a way
 * that reads exactly like a clean tree.</b> A census keyed on the source text {@code "@Query("}
 * found 9 methods and silently missed {@code PrivacyNoticeRepository}'s two, which write the
 * annotation <b>fully qualified</b> as {@code @org.springframework.data.jpa.repository.Query(} —
 * 8 further {@code LIKE} occurrences, on the PDPL Article 31 ROPA-to-notice linkage. {@code @Query}
 * is {@code @Retention(RUNTIME)}, so the annotation value is readable here and no source spelling
 * can hide a member: this class reads it by asking the method, and it matches an annotation by
 * <i>type name ending in {@code .Query}</i> rather than by import.
 *
 * <p>⚠ And a reflective census is only as wide as its classpath: driven from a throwaway program
 * with only {@code target/classes} on the classpath, every repository interface failed to load
 * (they extend Spring Data types that live in the dependency jars) and the census printed
 * <b>0 methods, 0 LIKE occurrences</b> — a clean-looking answer produced by loading nothing. This
 * class therefore <b>counts what it could not load and fails on it</b>, rather than reporting a
 * zero it did not earn.
 */
class LikePatternEscapingContractTest {

    private static final Path CLASSES = Path.of("target", "classes");
    private static final String PKG = "com.tss.grc.";

    /** {@code LIKE} followed, within one clause, by a bound parameter. */
    private static final Pattern LIKE_PARAM =
        Pattern.compile("(?is)\\bLIKE\\b[^\\n]{0,60}?:(\\w+)");
    private static final Pattern LIKE = Pattern.compile("(?i)\\bLIKE\\b");
    private static final Pattern ESCAPE = Pattern.compile("(?i)\\bESCAPE\\b");

    /**
     * The methods whose bound {@code LIKE} parameter is <b>not</b> a caller-supplied search term,
     * with the reason each. Enforced in BOTH directions: an entry naming a method the scan does
     * not find is an exemption for something that was never in the population, which reads exactly
     * like a reason for something that is (F2-199).
     *
     * <p>⚠⚠ The two {@code SodViolationRepository} entries must be pinned APART from the search
     * shape rather than "made consistent" with it: {@code SodRule.rolePatternCsv}'s own javadoc
     * says the column holds <i>"names or LIKE patterns"</i>, so a wildcard there is the configured
     * intent of a governance rule an administrator wrote — escaping it would break the SoD
     * detection rules outright. Same judgement as F2-430, which had to pin two charsets apart
     * because one of them was a control rather than an inconsistency.
     */
    private static final Map<String, String> NOT_A_SEARCH_TERM = Map.of(
        "SodViolationRepository#readonlyPersonaWriters",
        "BY DESIGN. :pattern comes from SodRule.rolePatternCsv, whose own javadoc says the column "
            + "holds 'names or LIKE patterns' — an administrator writing an SoD detection rule "
            + "chooses the wildcard deliberately, so escaping it would disable the rule.",

        "SodViolationRepository#serviceMultiModuleUsers",
        "BY DESIGN. Same rolePatternCsv column and the same administrator-authored LIKE pattern as "
            + "readonlyPersonaWriters — the SERVICE_MULTI_MODULE structural rule matches a family "
            + "of service-account role names by pattern.",

        "RegInspectionRepository#findMaxOsiOrdinal",
        "SERVER-BUILT. The pattern is composed by RegInspectService from the regulator, the fiscal "
            + "year and the quarter (F2-354's MAX-seed minter), never from a request field, so "
            + "there is no caller string to escape.",

        "TreatmentPlanRepository#findMaxExternalIdSeq",
        "SERVER-BUILT. The prefix is the module's own reference stem for the MAX-seed minter, not "
            + "caller input.",

        "VendorEsgSurveyRepository#findMaxExternalIdSeq",
        "SERVER-BUILT. Same MAX-seed minter shape as TreatmentPlanRepository — the prefix is a "
            + "server-composed reference stem.");

    private record Site(String key, int likes, String params, int escapes,
                        boolean fullyEscaped) { }

    /**
     * The other half: every site that BINDS a caller term must escape it.
     *
     * <p>⚠⚠ This is asserted per named site rather than derived, because "is this value
     * escaped before it is bound" is a dataflow question a scan cannot answer in general (F2-182)
     * — and without it the escaping half has no gate at all: the control that deleted
     * {@code LikePatterns.escape(...)} from {@code UserService} SURVIVED the whole build, caught
     * only by the live probe. A named list is what makes a silent removal fail.
     *
     * <p>Keyed on the source path, value = the expression that must appear.
     */
    private static final Map<String, String> BINDING_SITES = Map.of(
        "module/bcm/cycle/service/ExerciseScenarioLibraryService.java",
        "LikePatterns.escape(blankToNull(query))",

        "module/platform/help/service/HelpTopicService.java",
        "LikePatterns.escape(query.trim())",

        "module/compliance/service/ComplianceService.java",
        "LikePatterns.escape(q)",

        "module/iam/service/UserService.java",
        "LikePatterns.escape(search)",

        "module/admin/committee/service/CommitteeCatalogService.java",
        "LikePatterns.contains(committeeType)",

        "module/privacy/extensions/controller/RopaWorkflowsController.java",
        "LikePatterns.escape(ropaCode)");

    // ── the population ───────────────────────────────────────────────────────────────────────

    private static List<String> repositoryClassNames() {
        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.walk(CLASSES)) {
            s.filter(p -> p.toString().endsWith("Repository.class")).forEach(p -> {
                String rel = CLASSES.relativize(p).toString();
                names.add(rel.substring(0, rel.length() - ".class".length())
                    .replace('\\', '.').replace('/', '.'));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        names.removeIf(n -> !n.startsWith(PKG));
        return names;
    }

    private static String queryValueOf(Method m) {
        for (Annotation a : m.getAnnotations()) {
            if (!a.annotationType().getName().endsWith(".Query")) {
                continue;
            }
            try {
                return (String) a.annotationType().getMethod("value").invoke(a);
            } catch (ReflectiveOperationException ignored) {
                // an annotation named Query with no value() is not the one we mean
            }
        }
        return null;
    }

    private static List<Site> sites(List<String> unloadable) {
        List<Site> out = new ArrayList<>();
        for (String n : repositoryClassNames()) {
            Class<?> c;
            try {
                c = Class.forName(n, false, LikePatternEscapingContractTest.class.getClassLoader());
            } catch (Throwable t) {
                unloadable.add(n);
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                String q = queryValueOf(m);
                if (q == null) {
                    continue;
                }
                TreeSet<String> params = new TreeSet<>();
                Matcher pm = LIKE_PARAM.matcher(q);
                while (pm.find()) {
                    params.add(pm.group(1));
                }
                if (params.isEmpty()) {
                    continue;
                }
                int likes = 0;
                Matcher lm = LIKE.matcher(q);
                while (lm.find()) {
                    likes++;
                }
                int escapes = 0;
                Matcher em = ESCAPE.matcher(q);
                while (em.find()) {
                    escapes++;
                }
                // ⚠⚠ COUNT, never contains(): UserRepository carries three LIKEs, so a
                //    contains("ESCAPE") is satisfied by the other two and the control that removes
                //    one clause SURVIVED the whole build -- F2-194's substring defect, in a gate
                //    written after quoting it. One ESCAPE per LIKE is the claim.
                out.add(new Site(c.getSimpleName() + "#" + m.getName(), likes,
                    String.join(",", params), escapes, escapes >= likes));
            }
        }
        out.sort((a, b) -> a.key().compareTo(b.key()));
        return out;
    }

    // ── the invariant ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F2-449 — every caller-supplied LIKE term is escaped, or named with a reason")
    void everyCallerSuppliedLikeTermIsEscapedOrExplained() {
        List<String> unloadable = new ArrayList<>();
        List<Site> all = sites(unloadable);

        assertThat(unloadable)
            .as("""
                A reflective census is only as wide as its classpath. Driven with only
                target/classes on it, every repository interface failed to load and the census
                answered "0 methods, 0 LIKE occurrences" — a clean-looking zero produced by
                loading nothing. Count the failures rather than reporting a zero you did not
                earn.""")
            .isEmpty();

        assertThat(all)
            .as("the detector must still be resolving @Query values at all")
            .hasSizeGreaterThan(8);

        List<String> open = new ArrayList<>();
        for (Site s : all) {
            if (!s.fullyEscaped() && !NOT_A_SEARCH_TERM.containsKey(s.key())) {
                open.add(s.key() + "  (" + s.escapes() + " ESCAPE for " + s.likes()
                    + " LIKE, :" + s.params() + ")");
            }
        }
        assertThat(open)
            .as("""
                A caller-supplied search term IS the LIKE pattern unless something escapes it:
                SQL Server has no default escape character, so the query must declare
                ESCAPE and the term must be escaped before binding. Probed live pre-fix,
                ?search=%%, ?search=_ and ?search=[ac] each returned the whole register.
                Escape the term with LikePatterns.escape(...) and add ESCAPE to the query, or
                name the method in NOT_A_SEARCH_TERM with a reason if its pattern is
                administrator-authored or server-composed.""")
            .isEmpty();
    }

    @Test
    @DisplayName("F2-449 — every named exemption is still a site the scan finds")
    void everyExemptionStillNamesASiteInThePopulation() {
        List<String> unloadable = new ArrayList<>();
        List<String> keys = sites(unloadable).stream().map(Site::key).toList();

        assertThat(keys)
            .as("""
                The reverse direction, which is the one nobody writes first (F2-199): an entry
                naming a method the scan no longer finds is an exemption for something that was
                never in the population, and that reads exactly like a reason for something that
                is. If a query is deleted or renamed, delete its entry in the same commit.""")
            .containsAll(NOT_A_SEARCH_TERM.keySet());

        assertThat(NOT_A_SEARCH_TERM.values())
            .as("a reason must say WHY, not merely label the site")
            .allSatisfy(r -> assertThat(r.length()).isGreaterThan(60));
    }

    @Test
    @DisplayName("F2-449 — the census is published so a reader can see what it covers")
    void theCensusIsPublished() {
        List<String> unloadable = new ArrayList<>();
        List<Site> all = sites(unloadable);
        int likes = all.stream().mapToInt(Site::likes).sum();
        int escaping = (int) all.stream().filter(Site::fullyEscaped).count();

        StringBuilder sb = new StringBuilder();
        sb.append("F2-449 :: LIKE patterns built from a bound parameter\n");
        sb.append(String.format("%-48s %-6s %-18s %s%n", "method", "likes", "params", "escapes?"));
        for (Site s : all) {
            sb.append(String.format("%-48s %-6d %-18s %s%s%n", s.key(), s.likes(), s.params(),
                s.fullyEscaped() ? "yes" : ("NO (" + s.escapes() + "/" + s.likes() + ")"),
                NOT_A_SEARCH_TERM.containsKey(s.key()) ? "   [not a search term]" : ""));
        }
        sb.append(String.format("%n%d methods, %d LIKE occurrences, %d escape, %d exempt%n",
            all.size(), likes, escaping, NOT_A_SEARCH_TERM.size()));

        try {
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target", "like-pattern-escaping.txt"), sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(Path.of("target", "like-pattern-escaping.txt")).exists();
        assertThat(all).isNotEmpty();
    }


    @Test
    @DisplayName("F2-449 — every binding site escapes the caller's term")
    void everyBindingSiteEscapesTheTerm() {
        Path main = Path.of("src", "main", "java", "com", "tss", "grc");
        assertThat(BINDING_SITES).as("the list must not be emptied").hasSizeGreaterThan(5);
        BINDING_SITES.forEach((rel, expr) -> {
            Path p = main.resolve(rel.replace('/', java.io.File.separatorChar));
            assertThat(p).as("a named binding site must still exist").exists();
            String src;
            try {
                src = Files.readString(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assertThat(src)
                .as("""
                    %s must escape the caller's term before binding it into a LIKE. The ESCAPE
                    clause and the escaping are two halves and neither works alone: escaping with
                    no clause stores a literal backslash and matches nothing, a clause with no
                    escaping changes nothing. Without this leg the control that deleted
                    LikePatterns.escape(...) from UserService survived the whole build.""".formatted(rel))
                .contains(expr);
        });
    }

    @Test
    @DisplayName("F2-449 — the census sees a fully-qualified @Query, which a source scan did not")
    void theCensusSeesAFullyQualifiedQueryAnnotation() {
        List<String> unloadable = new ArrayList<>();
        List<String> keys = sites(unloadable).stream().map(Site::key).toList();

        assertThat(keys)
            .as("""
                PrivacyNoticeRepository writes the annotation as
                @org.springframework.data.jpa.repository.Query(, so a census keyed on the source
                text "@Query(" silently missed both its methods — 8 LIKE occurrences on the PDPL
                Article 31 ROPA-to-notice linkage, and an under-report reads exactly like a clean
                tree. Reflection sees the TYPE, so the source spelling is irrelevant BY
                CONSTRUCTION; that immunity is what this leg pins, because it cannot be mutated
                in the gate itself.""")
            .contains("PrivacyNoticeRepository#findByOrgAndLinkedRopa",
                      "PrivacyNoticeRepository#countByOrgAndLinkedRopa");
    }
}
