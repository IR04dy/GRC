package com.tss.grc.security;

import com.tss.grc.common.validation.OrderedVocabulary;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberSkillRepository;
import com.tss.grc.module.admin.committee.service.SkillLevel;
import com.tss.grc.module.change_mgmt.repository.ChangeAuthorisationMatrixRepository;
import com.tss.grc.module.change_mgmt.service.ChangeRiskLevel;
import com.tss.grc.module.ndi.gap.repository.NdiGapRepository;
import com.tss.grc.module.ndi.gap.service.NdiGapSeverity;
import com.tss.grc.module.scm.fourthparty.repository.FourthPartyExposureRepository;
import com.tss.grc.module.scm.fourthparty.service.ConcentrationTier;
import com.tss.grc.module.scm.fourthparty.service.FourthPartyExposureService;
import com.tss.grc.module.tprm.cyber.repository.ThirdPartyCyberAssessmentRepository;
import com.tss.grc.module.tprm.cyber.service.ResidualRiskTier;
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
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-230 — <b>an ORDERED vocabulary may not be ranked by an alphabetical sort.</b>
 *
 * <h2>What F2-229 got right, and the population it measured</h2>
 * F2-229 found {@code ORDER BY concentration_risk DESC} presented as a severity ranking and fixed it,
 * then measured the platform for the same shape. Its locator was a <b>graded-sounding column NAME</b>,
 * which is the instrument class F2-217 proved measures its own author's vocabulary. F2-230 re-derived
 * the population from the values the columns actually <em>store</em>:
 *
 * <pre>
 *   483  columns are sorted by some repository query (853 ORDER BY terms across 429 repositories)
 *   118  of those are String-typed
 *    57  hold a closed vocabulary (≤ 12 distinct UPPER_SNAKE values in the live database)
 *     6  of the 57 are ORDERED — a ladder. The rest are CATEGORICAL.
 * </pre>
 *
 * <p>🔑 <b>The property that decides whether a lexical sort is a defect is whether the vocabulary is
 * ORDERED, and grading is only one way to be ordered.</b> Measuring from values rather than names
 * surfaced {@code consumer.complaint_aggregate_report.report_period}, which holds {@code Q4-2025}: a
 * lexical sort of {@code Q<n>-<year>} orders by QUARTER before YEAR, so the most recent SAMA quarterly
 * complaints return is not first. Its name sounds like nothing of the kind. Time and version are
 * ordered too — see {@link #ORDERED_BUT_NOT_GRADED}.
 *
 * <h2>The two legs, and why neither is sufficient</h2>
 * <ol>
 *   <li><b>The invariant</b> ({@link #noDeclaredLadderIsSortedLexically}): for every declared
 *       {@link OrderedVocabulary}, no repository may sort its column lexically — unless the sort is
 *       <b>provably</b> safe, which is a mechanical check and not an exemption (see below).</li>
 *   <li><b>The census</b> ({@link #everyLexicalSortOfAClosedVocabularyIsClassified}): the full set of
 *       CHECK-constrained columns still sorted lexically, exactly asserted. This exists because leg 1's
 *       population is "columns somebody declared a ladder for" — a narrowing that would let the next
 *       ordered column escape simply by never being declared. Ninth instance of F2-198's shape in this
 *       programme; the answer is to make the wider population an assertion rather than a hope.</li>
 * </ol>
 *
 * <h2>Why the census carries no verdicts</h2>
 * A hard zero over all 57 would <b>fail on correct code</b> — the one failure a gate must never have
 * (F2-214). {@code sust.esg_metric.category} is {@code [ENVIRONMENTAL, SOCIAL, GOVERNANCE]} and
 * {@code admin.committee_form_templates.form_type} is {@code [AGENDA, ATTENDANCE, MOTION, …]}: sorting
 * either alphabetically is a deterministic grouping that claims nothing. Whether a vocabulary is
 * ordered is a question for a reader, so the census is an exactly-asserted list and the reasons live
 * with whoever read them (F2-188).
 *
 * <h2>"Provably safe" is measured, not asserted</h2>
 * {@code ai_governance.ml_model.risk_tier} is sorted {@code ORDER BY m.riskTier ASC} and the sort is
 * <b>correct</b>, because both queries narrow to {@code IN ('CRITICAL','HIGH')} and those two tokens'
 * alphabetical order <em>is</em> their severity order. F2-229 called that "accidentally correct" in
 * prose. Here it is checked: the narrowing is parsed out of the {@code @Query} and the subset's
 * alphabetical order compared with its position in the ladder. A gate that flagged it would be a gate
 * failing on working code; a gate that exempted it by name would tolerate the next one that is not.
 */
class OrderedVocabularySortContractTest {

    /** OrderBy&lt;Prop&gt;[Asc|Desc] pairs at the tail of a derived query method name. */
    private static final Pattern ORDER_PROP =
        Pattern.compile("([A-Z][a-z0-9]*(?:[A-Z][a-z0-9]*)*?)(?:Asc|Desc)");

    /** A bare {@code alias.property [asc|desc]} ORDER BY term — i.e. an unranked, lexical sort. */
    private static final Pattern BARE_ORDER_TERM =
        Pattern.compile("(?i)\\s*\\w+\\.([A-Za-z_]\\w*)\\s*(?:asc|desc)?\\s*");

    /** {@code CASE x.prop WHEN 'TOKEN' THEN n … END DESC|ASC} — a ranked ordering. */
    private static final Pattern CASE_LADDER = Pattern.compile(
        "(?is)CASE\\s+\\w+\\.(\\w+)(.*?)END\\s+(DESC|ASC)");
    private static final Pattern CASE_RUNG =
        Pattern.compile("(?i)WHEN\\s+'([^']+)'\\s+THEN\\s+(-?\\d+)");

    /** An {@code IN ('A','B')} narrowing on a ladder column, which can make a lexical sort correct. */
    private static final Pattern IN_NARROWING = Pattern.compile(
        "(?is)\\w+\\.(\\w+)\\s+IN\\s*\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    /**
     * Every ladder declared in main sources, keyed {@code schema.table.column}, paired with the
     * repository that owns it. The pairing is hand-declared because a ladder class does not know which
     * table it describes — and the pairing is what the legs below verify, so a wrong one fails loudly.
     */
    private record Ladder(String key, OrderedVocabulary vocabulary, Class<?> repository) {}

    private static final List<Ladder> LADDERS = List.of(
        new Ladder("scm.fourth_party_exposures.concentration_risk",
                   ConcentrationTier.LADDER, FourthPartyExposureRepository.class),
        // F2-229 ranked this column in SQL and declared no ladder for it, so the CASE had nothing to
        // be checked against. Found by this gate's own anti-vacuity floor coming up one short.
        new Ladder("scm.fourth_party_exposures.downstream_criticality",
                   FourthPartyExposureService.DOWNSTREAM_CRITICALITY_LADDER,
                   FourthPartyExposureRepository.class),
        new Ladder("tprm.third_party_cyber_assessments.residual_risk_tier",
                   ResidualRiskTier.LADDER, ThirdPartyCyberAssessmentRepository.class),
        new Ladder("admin.committee_member_skills.skill_level",
                   SkillLevel.LADDER, CommitteeMemberSkillRepository.class),
        new Ladder("ndi.gap.severity",
                   NdiGapSeverity.LADDER, NdiGapRepository.class),
        new Ladder("change_mgmt.change_authorisation_matrix.risk_level",
                   ChangeRiskLevel.LADDER, ChangeAuthorisationMatrixRepository.class));

    /**
     * CHECK-constrained columns still sorted lexically, exactly asserted, <b>no verdicts</b>.
     *
     * <p>Every one of these is CATEGORICAL as far as the reader who measured it could tell — a set of
     * labels, not a ladder — with the single deliberate exception of {@code ml_model.risk_tier}, whose
     * safety is <em>proved</em> by {@link #anAccidentallyCorrectSortIsProvedNotExempted} rather than
     * excused here.
     *
     * <p>F2-229 held 15 of these. Four have been ranked since: the fourth-party register (F2-229) and
     * this finding's committee-skills, NDI-gap and change-matrix columns. The number goes DOWN and says
     * why, which is what distinguishes a closed member from a tolerated one (house rule 3).
     */
    private static final Set<String> LEXICALLY_SORTED_CLOSED_VOCABULARIES = Set.of(
        "admin.committee_form_templates.form_type",
        "admin.committee_templates.committee_type",
        "admin.committee_templates.regulatory_domain",
        "ai_governance.ml_model.risk_tier",
        "change_mgmt.change_authorisation_matrix.change_type",
        "evidence.evidence_connectors.category",
        "ndi.framework_item.component",
        "scm.alternate_suppliers.alternate_vendor_role",
        "scm.contract_clauses.category",
        "sust.esg_metric.category",
        "sust.financed_emissions_record.asset_class",
        "sust.tcfd_item.pillar");

    /**
     * The four graded-String columns F2-229 filed, adjudicated. Two closed, two REFUTED by reading.
     *
     * <p>Same ~2-in-10 rate F2-188, F2-192, F2-204, F2-211 and F2-228 each measured in their
     * predecessor's claims — here 2 of 6, and in <b>opposite</b> directions, which is the argument for
     * reading every member rather than sweeping the shape.
     */
    private static final Map<String, String> ADJUDICATED = new LinkedHashMap<>();
    static {
        ADJUDICATED.put("tprm.third_party_cyber_assessments.residual_risk_tier",
            "CLOSED (F2-230). Five-rung ladder; ORDER BY … DESC ranked MODERATE > LOW > HIGH > "
            + "ELEVATED > CRITICAL, so the most exposed vendor sorted last on a PAGED register. "
            + "Ranked by ResidualRiskTier.");
        ADJUDICATED.put("admin.committee_member_skills.skill_level",
            "CLOSED (F2-230). ORDER BY skillLevel DESC over BASIC|INTERMEDIATE|EXPERT ranked "
            + "INTERMEDIATE above EXPERT on the CMA Article 54 financial-expert query. Probed live: "
            + "the single EXPERT came back LAST of seven. Ranked by SkillLevel.");
        ADJUDICATED.put("ndi.gap.severity",
            "CLOSED (F2-230). OrderBySeverityAsc over CRITICAL|HIGH|MEDIUM|LOW is accidentally right "
            + "on the top two rungs and transposes MEDIUM and LOW — which is why it survived. Ranked "
            + "by NdiGapSeverity.");
        ADJUDICATED.put("change_mgmt.change_authorisation_matrix.risk_level",
            "CLOSED (F2-230). Presentation rather than control — nothing decides anything from the "
            + "order — but the grid could not be read downward as an escalation. Ranked by "
            + "ChangeRiskLevel; the sibling change_type is categorical and left alphabetical.");
        ADJUDICATED.put("ai_governance.ml_model.risk_tier",
            "REFUTED by reading, and the refutation is CHECKED by "
            + "anAccidentallyCorrectSortIsProvedNotExempted: both queries narrow to "
            + "IN ('CRITICAL','HIGH'), whose alphabetical order IS their severity order.");
        ADJUDICATED.put("bcm.mtpd_inheritance_finding.severity",
            "REFUTED by reading. MtpdInheritanceService#getAssessmentDetail re-sorts the result in "
            + "memory with severityRank(), and computeSeverity — the only writer — emits exactly the "
            + "vocabulary that ranker knows, so the DB order never reaches a reader. The eight stored "
            + "rows carry INFO|MAJOR|MINOR|MODERATE, which that ranker maps to its default; that is a "
            + "seed-vocabulary mismatch (F2-196's shape) and is FILED, not fixed, because collapsing "
            + "synonyms rewrites live rows in two real databases.");
    }

    /**
     * Ordered vocabularies whose order is <b>not</b> a grading — the members F2-229's graded-name
     * locator could not see, and the reason this finding re-derived the population from stored values.
     *
     * <p>Filed rather than fixed: both need a decision this slice has no standing to make. A period is
     * ordered by calendar, and {@code report_period} is {@code @NotBlank} free text with no format
     * contract of any kind, so making it sortable means either choosing a canonical format or ordering
     * by a different column — a product call, not a side effect of a bug fix (F2-183's rule).
     */
    private static final Map<String, String> ORDERED_BUT_NOT_GRADED = new LinkedHashMap<>();
    static {
        ORDERED_BUT_NOT_GRADED.put("consumer.complaint_aggregate_report.report_period",
            "ORDERED BY CALENDAR. ComplaintService#listQuarterlyReports sorts report_period DESC; the "
            + "one stored value is 'Q4-2025', and lexically 'Q4-2025' > 'Q1-2026', so the QUARTER "
            + "dominates the YEAR and the most recent SAMA quarterly return is not first. Latent (1 "
            + "row in each database). The column is @NotBlank free text with no format validation, so "
            + "a fix has to settle a format first — FILED.");
        ORDERED_BUT_NOT_GRADED.put("tprm.questionnaire_templates.version",
            "ORDERED BY VERSION. Sorted lexically, so '10.0' would precede '9.0'. Latent: the only "
            + "stored value is '1.0'. FILED — a version ordering needs a declared scheme.");
    }

    private static List<Class<?>> cachedRepositories;

    // ── leg 1: the invariant ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no declared ladder is sorted lexically anywhere")
    void noDeclaredLadderIsSortedLexically() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Ladder l : LADDERS) {
            String column = l.key().substring(l.key().lastIndexOf('.') + 1);
            for (Method m : l.repository().getDeclaredMethods()) {
                for (String prop : lexicalOrderProperties(m)) {
                    if (!columnOf(l.repository(), prop).equalsIgnoreCase(column)) continue;
                    if (isProvablySafe(m, l, prop)) continue;
                    offenders.add(l.repository().getSimpleName() + "#" + m.getName()
                                  + " sorts " + l.key() + " lexically");
                }
            }
        }
        assertThat(offenders)
            .as("A ladder's order is data. Sorting it as text ranks it alphabetically, which for "
              + "LOW|MODERATE|ELEVATED|HIGH|CRITICAL reads MODERATE > LOW > HIGH > ELEVATED > "
              + "CRITICAL — the most exposed row last. Rank with a CASE ladder mirroring the "
              + "declaration, or narrow the query so the sort is provably safe.")
            .isEmpty();
    }

    @Test
    @DisplayName("every CASE ladder in a @Query matches its declaration, token for token, in order")
    void everyCaseLadderMirrorsItsDeclaration() throws Exception {
        int checked = 0;
        for (Ladder l : LADDERS) {
            String column = l.key().substring(l.key().lastIndexOf('.') + 1);
            for (Method m : l.repository().getDeclaredMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q == null) continue;
                Matcher cm = CASE_LADDER.matcher(q.value());
                while (cm.find()) {
                    String prop = cm.group(1);
                    if (!columnOf(l.repository(), prop).equalsIgnoreCase(column)) continue;
                    checked++;
                    Map<String, Integer> rungs = new LinkedHashMap<>();
                    Matcher rm = CASE_RUNG.matcher(cm.group(2));
                    while (rm.find()) rungs.put(rm.group(1), Integer.parseInt(rm.group(2)));
                    List<String> declared = l.vocabulary().ladder();

                    assertThat(rungs.keySet())
                        .as("%s#%s's CASE for %s must name every declared rung and nothing else",
                            l.repository().getSimpleName(), m.getName(), l.key())
                        .containsExactlyInAnyOrderElementsOf(declared);
                    for (int i = 0; i < declared.size(); i++) {
                        assertThat(rungs.get(declared.get(i)))
                            .as("%s must rank %d in %s#%s, matching the declared ladder %s",
                                declared.get(i), i, l.repository().getSimpleName(), m.getName(),
                                declared)
                            .isEqualTo(i);
                    }
                }
            }
        }
        // Anti-vacuity: a regex that stops matching would make the loop above assert nothing at all,
        // and an empty pass is indistinguishable from a clean tree (F2-229 shipped a hard zero over a
        // population of 15 for exactly this reason).
        assertThat(checked)
            .as("the CASE-ladder parse must find the rankings this finding and F2-229 shipped: two on "
              + "the fourth-party register, two on third-party cyber, two on the NDI gap register, one "
              + "on committee skills and one on the change matrix")
            .isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("an accidentally-correct lexical sort is PROVED safe, never exempted by name")
    void anAccidentallyCorrectSortIsProvedNotExempted() {
        // ml_model.risk_tier's ladder is NOT alphabetical…
        OrderedVocabulary mlLike = OrderedVocabulary.of(
            "riskTier", "CK_ml_model_risk_tier", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(mlLike.isAlphabetical())
            .as("LOW|MEDIUM|HIGH|CRITICAL is not in alphabetical order, so a bare lexical sort of the "
              + "whole vocabulary would be a false ranking")
            .isFalse();
        // …but the subset the queries narrow to IS, which is what makes those two sorts correct.
        assertThat(alphabeticalWithinLadder(mlLike, List.of("CRITICAL", "HIGH")))
            .as("IN ('CRITICAL','HIGH') narrows to two tokens whose alphabetical order is their "
              + "severity order — ASC gives CRITICAL then HIGH, which is severity-descending")
            .isTrue();
        // ⚠ Widening to three tokens does NOT break it, and getting that wrong was my error, caught by
        // running the gate: alphabetically CRITICAL, HIGH, MEDIUM has ranks 3, 2, 1 — monotone, so a
        // lexical sort of that subset IS a correct severity ranking. F2-206's rule (a surviving mutant
        // is a hole only if the mutated code is actually wrong) applied to an assertion rather than a
        // mutation: I asserted the helper was wrong before checking whether it was.
        assertThat(alphabeticalWithinLadder(mlLike, List.of("CRITICAL", "HIGH", "MEDIUM")))
            .as("CRITICAL, HIGH, MEDIUM is alphabetical AND severity-descending, so this subset is "
              + "genuinely safe — the narrowing does not have to be two tokens to be sound")
            .isTrue();
        // LOW is the token that breaks it: alphabetically it lands between HIGH and MEDIUM, which is
        // nowhere near its rank. This is the whole four-token vocabulary, i.e. exactly the defect.
        assertThat(alphabeticalWithinLadder(
                mlLike, List.of("CRITICAL", "HIGH", "LOW", "MEDIUM")))
            .as("the full vocabulary is NOT safe: alphabetical order gives ranks 3, 2, 0, 1, so "
              + "dropping the IN narrowing must make the gate fire")
            .isFalse();
        assertThat(alphabeticalWithinLadder(mlLike, List.of("LOW", "MEDIUM")))
            .as("LOW then MEDIUM is alphabetical and severity-ASCENDING, which is still a consistent "
              + "ranking — the check is about AGREEMENT with the ladder, not about direction, because "
              + "the direction is the query's ASC/DESC and either can be the intended one")
            .isTrue();
    }

    // ── leg 2: the census that stops leg 1's population being gamed ────────────────────────────

    @Test
    @DisplayName("every lexical sort of a CHECK-constrained vocabulary is in the census")
    void everyLexicalSortOfAClosedVocabularyIsClassified() throws Exception {
        Set<String> found = new TreeSet<>();
        Set<String> constrained = constrainedColumns();
        for (Class<?> repo : repositories()) {
            for (Method m : repo.getDeclaredMethods()) {
                for (String prop : lexicalOrderProperties(m)) {
                    String key = qualifiedColumn(repo, prop);
                    if (key != null && constrained.contains(key)) found.add(key);
                }
            }
        }
        assertThat(found)
            .as("A lexical sort of a CHECK-constrained vocabulary must be classified, not merely "
              + "present. Adding one means reading the vocabulary and deciding whether it is ORDERED "
              + "(then rank it with a CASE ladder) or CATEGORICAL (then add it here). Enforced in both "
              + "directions, so a column that gains a ranking must lose its entry in the same commit.")
            .containsExactlyInAnyOrderElementsOf(LEXICALLY_SORTED_CLOSED_VOCABULARIES);
    }

    @Test
    @DisplayName("the census is not vacuous: the constraint corpus and the sort corpus are both real")
    void theCensusIsNotVacuous() throws Exception {
        assertThat(constrainedColumns())
            .as("the migration CHECK corpus — a parse that stops matching would empty the census and "
              + "read exactly like a tree with no lexical sorts left (F2-229's own detector bug, which "
              + "passed a hard zero over a population of 15)")
            .hasSizeGreaterThan(250);
        assertThat(repositories()).hasSizeGreaterThan(400);

        int terms = 0;
        for (Class<?> repo : repositories()) {
            for (Method m : repo.getDeclaredMethods()) terms += lexicalOrderProperties(m).size();
        }
        assertThat(terms)
            .as("the lexical ORDER BY corpus this census filters")
            .isGreaterThan(700);
    }

    // ── the adjudications, held in both directions ─────────────────────────────────────────────

    @Test
    @DisplayName("F2-229's filed work list is adjudicated in full, and every reason names its evidence")
    void theFiledWorkListIsFullyAdjudicated() {
        assertThat(ADJUDICATED.keySet()).containsExactlyInAnyOrder(
            "tprm.third_party_cyber_assessments.residual_risk_tier",
            "admin.committee_member_skills.skill_level",
            "ndi.gap.severity",
            "change_mgmt.change_authorisation_matrix.risk_level",
            "ai_governance.ml_model.risk_tier",
            "bcm.mtpd_inheritance_finding.severity");

        for (Map.Entry<String, String> e : ADJUDICATED.entrySet()) {
            String reason = e.getValue();
            assertThat(reason.length())
                .as("%s: a verdict must state what was measured, not a label (F2-188)", e.getKey())
                .isGreaterThan(80);
            assertThat(reason)
                .as("%s: a verdict must NAME its evidence — CLOSED with the mechanism, or REFUTED "
                  + "with what refuted it. F2-224's lesson: the rule is that a verdict names its "
                  + "evidence, not that it comes from a fixed list of sources.", e.getKey())
                .matches("(?s)^(CLOSED|REFUTED|FILED|BY DESIGN|ORDERED)\\b.*");
        }

        // The four CLOSED ones must have a declared ladder; the two REFUTED ones must NOT, because a
        // ladder for them would put them in leg 1's population and leg 1 would then fire on code this
        // finding deliberately left alone.
        Set<String> declared = new LinkedHashSet<>();
        for (Ladder l : LADDERS) declared.add(l.key());
        for (Map.Entry<String, String> e : ADJUDICATED.entrySet()) {
            boolean closed = e.getValue().startsWith("CLOSED");
            assertThat(declared.contains(e.getKey()))
                .as("%s is %s, so a declared ladder must%s exist for it",
                    e.getKey(), closed ? "CLOSED" : "REFUTED", closed ? "" : " NOT")
                .isEqualTo(closed);
        }
    }

    @Test
    @DisplayName("the ordered-but-not-graded members are held, with what makes each one ordered")
    void theOrderedButNotGradedMembersAreHeld() {
        assertThat(ORDERED_BUT_NOT_GRADED).isNotEmpty();
        for (Map.Entry<String, String> e : ORDERED_BUT_NOT_GRADED.entrySet()) {
            assertThat(e.getValue())
                .as("%s: say what ORDERS it, since the whole point of this bucket is that the name "
                  + "does not say", e.getKey())
                .matches("(?s)^ORDERED BY (CALENDAR|VERSION|SEQUENCE)\\b.*");
            assertThat(e.getValue()).contains("FILED");
        }
        // Neither has a graded-sounding name, which is the finding: F2-229's locator could not see
        // them, and a locator keyed on names measures its author's vocabulary (F2-217).
        Pattern graded = Pattern.compile(
            "(?i)(severity|critical|priority|risk|impact|likelihood|tier|rating|band|level|grade)");
        for (String key : ORDERED_BUT_NOT_GRADED.keySet()) {
            String column = key.substring(key.lastIndexOf('.') + 1);
            assertThat(graded.matcher(column).find())
                .as("%s must NOT look graded — if it did, F2-229's name-keyed locator would have "
                  + "found it and this bucket would not be the finding it is", column)
                .isFalse();
        }
    }

    @Test
    @DisplayName("every ladder's declaration is internally sound and non-alphabetical")
    void everyLadderIsSound() {
        for (Ladder l : LADDERS) {
            OrderedVocabulary v = l.vocabulary();
            assertThat(v.ladder()).as("%s ladder", l.key()).hasSizeGreaterThan(1);
            assertThat(Set.copyOf(v.ladder()))
                .as("%s: the ladder and its vocabulary must hold the same tokens", l.key())
                .isEqualTo(v.vocabulary().tokens());
            assertThat(v.isAlphabetical())
                .as("%s: if this ladder WERE alphabetical, ranking it would be unnecessary and the "
                  + "CASE could be deleted — so the assertion is a statement about why the CASE has "
                  + "to exist, not decoration", l.key())
                .isFalse();
            assertThat(v.rank(v.top())).isGreaterThan(v.rank(v.bottom()));
            assertThat(v.atLeast(v.top(), v.bottom())).isTrue();
            assertThat(v.atLeast(v.bottom(), v.top())).isFalse();
            assertThat(v.rank(null)).isEqualTo(OrderedVocabulary.UNRECOGNISED);
            assertThat(v.rank("  " + v.top().toLowerCase(Locale.ROOT) + " "))
                .as("%s: casing must normalise — the collation is case-insensitive, so a lower-case "
                  + "token is storable and a case-sensitive comparison then misses it (F2-179)",
                    l.key())
                .isEqualTo(v.ladder().size() - 1);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    /**
     * The properties a method sorts <b>lexically</b> — a derived {@code OrderBy} tail, or a bare
     * {@code alias.prop} ORDER BY term in a {@code @Query}. A {@code CASE … END} term is a ranking and
     * deliberately contributes nothing.
     */
    private static List<String> lexicalOrderProperties(Method m) {
        List<String> props = new ArrayList<>();
        Query q = m.getAnnotation(Query.class);
        if (q != null && q.value().toUpperCase(Locale.ROOT).contains("ORDER BY")) {
            for (String term : orderTerms(q.value())) {
                Matcher bare = BARE_ORDER_TERM.matcher(term);
                if (bare.matches()) props.add(bare.group(1));
            }
        }
        int at = m.getName().indexOf("OrderBy");
        if (at >= 0) {
            String tail = m.getName().substring(at + "OrderBy".length());
            Matcher pm = ORDER_PROP.matcher(tail);
            boolean any = false;
            while (pm.find()) { any = true; props.add(decapitalise(pm.group(1))); }
            if (!any && !tail.isEmpty()) props.add(decapitalise(tail));
        }
        return props;
    }

    /**
     * Is this lexical sort provably correct despite the ladder not being alphabetical?
     *
     * <p>Two ways, both measured rather than declared: the ladder is alphabetical (then any sort of it
     * is a correct ranking), or the query narrows the column to a subset whose alphabetical order
     * agrees with the ladder.
     */
    private static boolean isProvablySafe(Method m, Ladder l, String prop) {
        if (l.vocabulary().isAlphabetical()) return true;
        Query q = m.getAnnotation(Query.class);
        if (q == null) return false;
        Matcher in = IN_NARROWING.matcher(q.value());
        while (in.find()) {
            if (!in.group(1).equalsIgnoreCase(prop)) continue;
            List<String> subset = new ArrayList<>();
            Matcher tok = QUOTED.matcher(in.group(2));
            while (tok.find()) subset.add(tok.group(1));
            if (!subset.isEmpty() && alphabeticalWithinLadder(l.vocabulary(), subset)) return true;
        }
        return false;
    }

    /** Does {@code subset}'s alphabetical order agree (forwards or backwards) with the ladder? */
    private static boolean alphabeticalWithinLadder(OrderedVocabulary v, List<String> subset) {
        List<String> alpha = subset.stream().sorted().toList();
        List<Integer> ranks = alpha.stream().map(v::rank).toList();
        boolean rising = true, falling = true;
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) < ranks.get(i - 1)) rising = false;
            if (ranks.get(i) > ranks.get(i - 1)) falling = false;
        }
        return rising || falling;
    }

    /**
     * Every column a single-column CHECK constrains, keyed {@code schema.table.column}.
     *
     * <p>⚠ The schema segment is load-bearing and F2-229 got it wrong: {@code MigrationVocabularies}
     * qualifies its table with the schema, and a key built as {@code table.column} matches nothing —
     * so every column reads as unconstrained, the census empties, and a hard zero PASSES over a
     * population of 15. {@link #theCensusIsNotVacuous} is what makes that failure loud.
     */
    private static Set<String> constrainedColumns() {
        Set<String> out = new TreeSet<>();
        for (MigrationVocabularies.Vocabulary v : MigrationVocabularies.byConstraint().values()) {
            out.add((v.table() + "." + v.column()).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<String> orderTerms(String jpql) {
        int at = jpql.toUpperCase(Locale.ROOT).lastIndexOf("ORDER BY");
        if (at < 0) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : jpql.substring(at + "ORDER BY".length()).split(",")) out.add(part.trim());
        return out;
    }

    private static String qualifiedColumn(Class<?> repo, String prop) {
        Class<?> entity = entityOfRepo(repo);
        if (entity == null) return null;
        Table t = entity.getAnnotation(Table.class);
        Field f = fieldOf(entity, prop);
        if (t == null || f == null) return null;
        Column col = f.getAnnotation(Column.class);
        String name = col != null && !col.name().isBlank() ? col.name() : f.getName();
        // ⚠ The schema segment is load-bearing: MigrationVocabularies keys schema.table.column, and
        // F2-229's first draft built table.column, so every lookup missed, every column read as having
        // no CHECK, and a hard zero PASSED over a population of 15.
        String qualified = t.schema() == null || t.schema().isBlank()
            ? t.name() : t.schema() + "." + t.name();
        return (qualified + "." + name).toLowerCase(Locale.ROOT);
    }

    private static String columnOf(Class<?> repo, String prop) {
        String q = qualifiedColumn(repo, prop);
        return q == null ? "?" : q.substring(q.lastIndexOf('.') + 1);
    }

    private static Field fieldOf(Class<?> type, String prop) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equalsIgnoreCase(prop)) return f;
            }
        }
        return null;
    }

    private static Class<?> entityOfRepo(Class<?> repo) {
        for (Type t : repo.getGenericInterfaces()) {
            if (t instanceof ParameterizedType pt
                    && pt.getActualTypeArguments().length >= 1
                    && pt.getActualTypeArguments()[0] instanceof Class<?> c
                    && c.isAnnotationPresent(Entity.class)) {
                return c;
            }
        }
        return null;
    }

    private static synchronized List<Class<?>> repositories() throws IOException {
        if (cachedRepositories != null) return cachedRepositories;
        List<Class<?>> out = new ArrayList<>();
        Path base = Path.of("target", "classes");
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".class")).toList()) {
                String cn = base.relativize(p).toString()
                    .replace(".class", "").replace('\\', '.').replace('/', '.');
                if (!cn.contains("Repository")) continue;
                try {
                    Class<?> c = Class.forName(
                        cn, false, OrderedVocabularySortContractTest.class.getClassLoader());
                    if (c.isInterface() && Repository.class.isAssignableFrom(c)) out.add(c);
                } catch (Throwable ignored) {
                    // Not loadable in isolation; it cannot declare a query that ships either.
                }
            }
        }
        cachedRepositories = List.copyOf(out);
        return cachedRepositories;
    }

    private static String decapitalise(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
