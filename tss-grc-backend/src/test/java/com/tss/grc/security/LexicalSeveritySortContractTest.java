package com.tss.grc.security;

import com.tss.grc.module.scm.fourthparty.repository.FourthPartyExposureRepository;
import com.tss.grc.module.scm.fourthparty.service.ConcentrationTier;
import com.tss.grc.support.MigrationVocabularies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-229 — <b>a lexical sort is a ranking only where the vocabulary happens to be in alphabetical
 * order, and severity ladders never are.</b>
 *
 * <p>{@code FourthPartyExposureRepository} declared
 * {@code findByOrganisationIdOrderByConcentrationRiskDescDownstreamCriticalityDesc} — a method name
 * that asserts a ranking. Both columns are {@code NVARCHAR} holding LOW|MEDIUM|HIGH|CRITICAL, so
 * descending <em>alphabetically</em> they read MEDIUM, LOW, HIGH, CRITICAL. Measured against the live
 * register (13 rows, identical in dev and prod) the row that came back <b>last of 13 was the
 * CRITICAL/CRITICAL one</b>, with the three HIGH/CRITICAL rows 10th, 11th and 12th. The frontend asks
 * for {@code size=200} and renders the server's order, so the most concentrated fourth party in the
 * register was the last row on the page.
 *
 * <p><b>THE MEASUREMENT.</b> Everything here is reflection over {@code target/classes} — repository
 * interfaces and their {@code @Query} annotations are runtime-retained, so no regex reads Java (the
 * recorded rule, after three phantom counts over Java source in this programme). Measured 2026-08-17:
 * <b>429 repository interfaces, 644 derived {@code OrderBy} methods, 91 {@code @Query} carrying an
 * {@code ORDER BY}, 857 lexical order terms</b>, of which <b>15 columns</b> carry a {@code CHECK}
 * vocabulary of three or more tokens.
 *
 * <p>⚠⚠ <b>A HARD ZERO OVER THOSE 15 WOULD FAIL ON CORRECT CODE, WHICH IS THE ONE THING A GATE MUST
 * NEVER DO (F2-214).</b> A lexical sort of a closed vocabulary is a false ranking only where the
 * vocabulary is <em>ordered</em>. Most of the 15 are <em>categorical</em>:
 * {@code sust.esg_metric.category} is [ENVIRONMENTAL, SOCIAL, GOVERNANCE],
 * {@code admin.committee_form_templates.form_type} is [AGENDA, ATTENDANCE, MOTION, …], and sorting
 * those alphabetically is a deterministic grouping that claims nothing. Only a ladder —
 * {@code admin.committee_member_skills.skill_level} [BASIC, INTERMEDIATE, EXPERT],
 * {@code ndi.gap.severity} [CRITICAL, HIGH, MEDIUM, LOW] — turns an alphabetical sort into an
 * assertion about which row matters most. So {@code OrderedVocabularySortContractTest#everyLexicalSortOfAClosedVocabularyIsClassified}
 * is an exactly-asserted census rather than a zero, and it carries <b>no verdicts</b>: F2-188 measured
 * what happens when a slice writes reasons for code it has not read (five claims, five false, and the
 * fluency was what made them look verified).
 *
 * <p>⚠⚠ <b>MY OWN DETECTOR UNDER-REPORTED, AND THE ANTI-VACUITY LEG IS THE ONLY REASON I KNOW.</b>
 * {@link MigrationVocabularies} keys its vocabularies {@code schema.table.column}; my first draft
 * built the entity-side key as {@code table.column} from {@code @Table(name)} while ignoring
 * {@code @Table(schema)}. Every lookup therefore missed, every column read as having <b>no CHECK</b>,
 * and the hard zero I had written <em>passed</em> — over a population of 15. The throwaway census that
 * used the same key printed "total columns affected: 0" and I read that as a measurement. What caught
 * it was {@code OrderedVocabularySortContractTest#theCensusIsNotVacuous} asserting that V377's own constraints are findable.
 * 🔑 <b>A detector that cannot match reads exactly like a clean tree, and it under-reports — the quiet
 * direction.</b> Two of the fifteen sit in {@code admin}, four in {@code sust}: nothing about the
 * shape of the answer looked wrong.
 *
 * <p><b>The narrowing that matters, pinned by measurement rather than by argument.</b> A name-based
 * locator over graded-sounding column names returns <b>10</b> lexically-sorted columns after the fix
 * (12 before it), and <b>4 of them are numeric</b> — where a lexical sort is simply a correct numeric
 * sort. Counting those would inflate the population by 40%. {@link #theNumericNarrowingIsLoadBearing}
 * holds them, so deleting the String narrowing changes an answer instead of an opinion.
 *
 * <p><b>Two of the six graded String columns were read while measuring, and both show why a blanket
 * verdict would have been wrong in opposite directions.</b> {@code MlModelRepository}'s two queries
 * are <b>accidentally correct</b>: {@code WHERE m.riskTier IN ('CRITICAL','HIGH')} narrows the
 * vocabulary to two tokens whose alphabetical order <em>is</em> their severity order. Meanwhile
 * {@code CommitteeMemberSkillRepository#findByDimension} sorts {@code skillLevel DESC} over
 * [BASIC, INTERMEDIATE, EXPERT] to answer "does this committee have a financial expert?" (CMA
 * Article 54) — which alphabetically ranks INTERMEDIATE above EXPERT. The other four are unread and
 * the work list says so rather than guessing.
 */
class LexicalSeveritySortContractTest {

    private static final Path CLASSES = Path.of("target/classes");

    /** OrderBy&lt;Prop&gt;[Asc|Desc] pairs at the tail of a derived query method name. */
    private static final Pattern ORDER_PROP =
        Pattern.compile("([A-Z][a-z0-9]*(?:[A-Z][a-z0-9]*)*?)(Asc|Desc)(?=[A-Z]|$)");

    /** A bare property reference in an ORDER BY term: {@code f.someProp}, optionally ASC/DESC. */
    private static final Pattern BARE_ORDER_TERM =
        Pattern.compile("^(?:\\w+\\.)?([a-z]\\w*)(?:\\s+(?:asc|desc))?$", Pattern.CASE_INSENSITIVE);

    /** Column names that sound like a graded judgement. A locator, never a verdict (F2-217). */
    private static final Pattern GRADED = Pattern.compile(
        "(?i)(severity|critical|priority|risk|impact|likelihood|tier|rating|band|level|grade)");
    /**
     * Graded-sounding columns that are NUMERIC, where a lexical sort IS a correct numeric sort.
     * Held so the narrowing is pinned by measurement: delete the String narrowing above and the work
     * list has to grow by exactly these four.
     */
    private static final Set<String> GRADED_NUMERIC_SORTS = Set.of(
        "bcm.communication_contacts.escalation_level",
        "platform.automation_rules.priority",
        "socops.ot_asset.purdue_level",
        "sust.climate_risk_opportunity.risk_score");

    /** Anti-vacuity: the reflection sweep must keep finding the corpus it is written against. */
    // ⚠ F2-229's PLATFORM-WIDE census and its six-entry graded-String work list have MOVED to
    // OrderedVocabularySortContractTest (F2-230). Two instruments answering one question is this
    // programme's own recurring defect, and after F2-230 ranked three of the four filed columns those
    // numbers were stale — a stale ceiling is indistinguishable from a reviewed one (house rule 3).
    // What stays here is what is specific to the fourth-party register: its CASE ladder mirroring
    // ConcentrationTier, its hard zero, and the numeric/String narrowing that stops the census being a
    // pile of correct code.

    private static final int MIN_REPOSITORIES = 400;
    private static final int MIN_LEXICAL_TERMS = 700;
    private static final int MIN_CHECK_VOCABULARIES = 300;

    private record Site(String repo, String method, String property, String source) {}

    // ── the register this slice read end to end ───────────────────────────────────────────────

    @Test
    @DisplayName("the fourth-party register is ranked, and the ladder in the SQL is the Java ladder")
    void theRegisterOrderingRanksByTheDeclaredLadder() throws Exception {
        Method m = FourthPartyExposureRepository.class
            .getMethod("findRegisterByConcentration", Long.class,
                       org.springframework.data.domain.Pageable.class);
        Query q = m.getAnnotation(Query.class);
        assertThat(q).as("the register ordering must be an explicit query, not a derived OrderBy")
            .isNotNull();

        // Parse each CASE ladder out of the annotation and compare it token for token, in order, with
        // ConcentrationTier's. A @Query string is necessarily a SECOND copy of the order; this is what
        // stops it drifting from the first.
        List<Map<String, Integer>> ladders = caseLadders(q.value());
        assertThat(ladders)
            .as("one CASE ladder for concentrationRisk and one for downstreamCriticality")
            .hasSize(2);
        List<String> declared = ConcentrationTier.ladder();
        for (Map<String, Integer> ladder : ladders) {
            assertThat(ladder.keySet())
                .as("the SQL ladder must name every declared tier and nothing else")
                .containsExactlyInAnyOrderElementsOf(declared);
            for (int i = 0; i < declared.size(); i++) {
                assertThat(ladder.get(declared.get(i)))
                    .as("%s must rank %d, matching ConcentrationTier.ladder()", declared.get(i), i)
                    .isEqualTo(i);
            }
        }
        // ⚠ EVERY ladder must descend, asserted by COUNT. A bare `contains("END DESC")` passed a
        // mutation that flipped the FIRST ladder to ASC, because the second one still matched — the
        // same shape as F2-194's substring satisfied by something other than the claim, and the
        // reason a surviving mutation has to be diagnosed rather than dismissed.
        long descending = q.value().split("END DESC", -1).length - 1;
        assertThat(descending)
            .as("all %d rank ladders must descend, so the most concentrated row is first",
                ladders.size())
            .isEqualTo(ladders.size());
        assertThat(q.value()).as("no rank ladder may ascend").doesNotContain("END ASC");
    }

    @Test
    @DisplayName("no fourth-party severity column is sorted lexically by any repository method")
    void theRegisterHasNoLexicalSortLeft() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Site s : lexicalOrderTerms()) {
            if (!s.repo().equals("FourthPartyExposureRepository")) continue;
            if (s.property().equals("concentrationRisk")
                    || s.property().equals("downstreamCriticality")) {
                offenders.add(s.repo() + "#" + s.method() + "::" + s.property());
            }
        }
        assertThat(offenders)
            .as("the pre-fix shape was findByOrganisationIdOrderByConcentrationRiskDesc"
                + "DownstreamCriticalityDesc — a derived OrderBy on either column is a lexical sort "
                + "of a ladder, and the CRITICAL row sorted last of 13")
            .isEmpty();
    }
    @Test
    @DisplayName("the numeric narrowing is load-bearing, not an argument")
    void theNumericNarrowingIsLoadBearing() throws Exception {
        Set<String> numeric = new TreeSet<>();
        for (Site s : lexicalOrderTerms()) {
            if (!GRADED.matcher(s.property()).find()) continue;
            String column = columnOf(s);
            if (column == null || "String".equals(typeOf(s))) continue;
            numeric.add(column);
        }
        assertThat(numeric)
            .as("graded-SOUNDING columns where a lexical sort is a correct numeric sort. Held here so "
                + "that removing the String narrowing from the work-list leg changes its answer by "
                + "exactly these four, rather than being defensible in prose")
            .containsExactlyInAnyOrderElementsOf(GRADED_NUMERIC_SORTS);
    }

    // ── positive controls ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("positive control: the pre-fix and post-fix shapes are told apart")
    void theDetectorTellsTheTwoShapesApart() {
        // The literal pre-fix method name. Its OrderBy tail must resolve to BOTH properties, or the
        // fourth-party leg would have passed over the defect it was written for.
        String preFix = "findByOrganisationIdOrderByConcentrationRiskDescDownstreamCriticalityDesc";
        List<String> props = new ArrayList<>();
        Matcher pm = ORDER_PROP.matcher(preFix.substring(preFix.indexOf("OrderBy") + 7));
        while (pm.find()) props.add(decapitalise(pm.group(1)));
        assertThat(props).containsExactly("concentrationRisk", "downstreamCriticality");

        // A derived method with no OrderBy contributes nothing.
        assertThat("findByIdAndOrganisationId".indexOf("OrderBy")).isEqualTo(-1);

        // A CASE ladder is not a bare term, and a bare term is not a ladder.
        assertThat(BARE_ORDER_TERM.matcher("f.concentrationRisk DESC").matches()).isTrue();
        assertThat(BARE_ORDER_TERM.matcher(
            "CASE f.concentrationRisk WHEN 'LOW' THEN 0 ELSE -1 END DESC").matches()).isFalse();
        // ...and the ladder parser reads the tokens rather than matching the word CASE.
        List<Map<String, Integer>> parsed = caseLadders(
            "SELECT f FROM X f ORDER BY CASE f.x WHEN 'LOW' THEN 0 WHEN 'HIGH' THEN 9 ELSE -1 END DESC");
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0)).containsExactlyInAnyOrderEntriesOf(Map.of("LOW", 0, "HIGH", 9));
    }
    // ── machinery ─────────────────────────────────────────────────────────────────────────────

    /** {@code WHEN 'TOKEN' THEN n} pairs, grouped per CASE expression, within ORDER BY only. */
    private static List<Map<String, Integer>> caseLadders(String jpql) {
        int at = jpql.toUpperCase(Locale.ROOT).lastIndexOf("ORDER BY");
        String clause = at < 0 ? jpql : jpql.substring(at);
        List<Map<String, Integer>> out = new ArrayList<>();
        Matcher caseStart = Pattern.compile("(?i)\\bCASE\\b").matcher(clause);
        Matcher when = Pattern.compile("(?i)WHEN\\s+'([A-Z_]+)'\\s+THEN\\s+(-?\\d+)").matcher(clause);
        List<Integer> starts = new ArrayList<>();
        while (caseStart.find()) starts.add(caseStart.start());
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : clause.length();
            Map<String, Integer> ladder = new TreeMap<>();
            when.reset();
            while (when.find()) {
                if (when.start() >= from && when.start() < to) {
                    ladder.put(when.group(1), Integer.parseInt(when.group(2)));
                }
            }
            if (!ladder.isEmpty()) out.add(ladder);
        }
        return out;
    }

    private static Map<String, Set<String>> cachedVocab;

    private static synchronized Map<String, Set<String>> checkVocabulariesByColumn()
            throws IOException {
        if (cachedVocab != null) return cachedVocab;
        Map<String, Set<String>> out = new TreeMap<>();
        for (MigrationVocabularies.Vocabulary v : MigrationVocabularies.byConstraint().values()) {
            if (v.table() == null || v.column() == null) continue;
            out.merge(key(v.table(), v.column()), new TreeSet<>(v.values()), (a, b) -> {
                Set<String> u = new TreeSet<>(a);
                u.addAll(b);
                return u;
            });
        }
        cachedVocab = Map.copyOf(out);
        return cachedVocab;
    }

    private static List<Site> cachedTerms;

    private static synchronized List<Site> lexicalOrderTerms() throws IOException {
        if (cachedTerms != null) return cachedTerms;
        List<Site> lexical = new ArrayList<>();
        for (Class<?> repo : repositories()) {
            for (Method m : repo.getDeclaredMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q != null && q.value().toUpperCase(Locale.ROOT).contains("ORDER BY")) {
                    for (String term : orderTerms(q.value())) {
                        Matcher bare = BARE_ORDER_TERM.matcher(term);
                        if (bare.matches()) {
                            lexical.add(new Site(repo.getSimpleName(), m.getName(),
                                                 bare.group(1), "@Query"));
                        }
                    }
                }
                int at = m.getName().indexOf("OrderBy");
                if (at < 0) continue;
                String tail = m.getName().substring(at + "OrderBy".length());
                Matcher pm = ORDER_PROP.matcher(tail);
                boolean any = false;
                while (pm.find()) {
                    any = true;
                    lexical.add(new Site(repo.getSimpleName(), m.getName(),
                                         decapitalise(pm.group(1)), "derived"));
                }
                if (!any && !tail.isEmpty()) {
                    lexical.add(new Site(repo.getSimpleName(), m.getName(),
                                         decapitalise(tail), "derived"));
                }
            }
        }
        cachedTerms = List.copyOf(lexical);
        return cachedTerms;
    }

    private static List<String> orderTerms(String jpql) {
        int at = jpql.toUpperCase(Locale.ROOT).lastIndexOf("ORDER BY");
        if (at < 0) return List.of();
        String clause = jpql.substring(at + "ORDER BY".length());
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < clause.length(); i++) {
            char c = clause.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                out.add(clause.substring(start, i));
                start = i + 1;
            }
        }
        out.add(clause.substring(start));
        return out.stream().map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    /**
     * {@code schema.table.column}, in {@link MigrationVocabularies}' own key space.
     *
     * <p>⚠ <b>The schema segment is load-bearing and omitting it is silent.</b> See the class javadoc
     * and the schema-qualified key in {@code OrderedVocabularySortContractTest}.
     */
    private static String columnOf(Site s) throws IOException {
        Class<?> entity = entityOfRepo(s.repo());
        if (entity == null) return null;
        Table t = entity.getAnnotation(Table.class);
        Field f = fieldOf(entity, s.property());
        if (t == null || f == null) return null;
        Column col = f.getAnnotation(Column.class);
        String name = col != null && !col.name().isBlank() ? col.name() : f.getName();
        String qualified = t.schema() == null || t.schema().isBlank()
                ? t.name() : t.schema() + "." + t.name();
        return key(qualified, name);
    }

    private static String typeOf(Site s) throws IOException {
        Class<?> entity = entityOfRepo(s.repo());
        Field f = entity == null ? null : fieldOf(entity, s.property());
        return f == null ? "?" : f.getType().getSimpleName();
    }

    private static Field fieldOf(Class<?> entity, String property) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(property)) return f;
            }
        }
        return null;
    }

    private static Class<?> entityOfRepo(String simpleName) throws IOException {
        for (Class<?> repo : repositories()) {
            if (!repo.getSimpleName().equals(simpleName)) continue;
            for (Type t : repo.getGenericInterfaces()) {
                if (t instanceof ParameterizedType pt
                        && pt.getActualTypeArguments().length >= 1
                        && pt.getActualTypeArguments()[0] instanceof Class<?> c
                        && c.isAnnotationPresent(Entity.class)) {
                    return c;
                }
            }
        }
        return null;
    }

    private static List<Class<?>> cachedRepos;

    private static synchronized List<Class<?>> repositories() throws IOException {
        if (cachedRepos != null) return cachedRepos;
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(CLASSES)) {
            for (Path p : walk.filter(x -> x.toString().endsWith("Repository.class")).toList()) {
                String cn = CLASSES.relativize(p).toString()
                        .replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
                try {
                    Class<?> c = Class.forName(cn, false,
                            LexicalSeveritySortContractTest.class.getClassLoader());
                    if (c.isInterface() && Repository.class.isAssignableFrom(c)) out.add(c);
                } catch (Throwable ignored) {
                    // Not loadable from the test classpath. The anti-vacuity floor is what notices if
                    // this ever starts swallowing the corpus.
                }
            }
        }
        cachedRepos = List.copyOf(new LinkedHashSet<>(out));
        return cachedRepos;
    }

    private static String key(String table, String column) {
        return table.toLowerCase(Locale.ROOT) + "." + column.toLowerCase(Locale.ROOT);
    }

    private static String decapitalise(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
