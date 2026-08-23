package com.tss.grc.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * F2-221 — a state machine's vocabulary and its column's CHECK are two artefacts, and nothing
 * was comparing them.
 *
 * <p><b>Why this exists.</b> F2-220 closed nine registers whose create named a state its transition
 * route adjudicates, and filed two of them — {@code platform.issues} and {@code bcm.crisis_events} —
 * as having no CHECK on the column at all. That filing's population was <i>the nine registers F2-220
 * happened to visit</i>. Measured properly, the same structural discriminator
 * ({@link ServerStampedFieldContractTest#machineOwned}) finds <b>28</b> declared machines, so the
 * nine were the subset whose create <i>also</i> accepted the field. 🔑 <b>The invariant is real; the
 * population it was drawn from was a previous slice's itinerary</b> — F2-198's shape, for the ninth
 * time in this lineage.
 *
 * <p><b>What it asserts.</b> Where a service declares its vocabulary as a named {@code Set<String>}
 * <i>and</i> the column carries a CHECK, the two must name exactly the same tokens — read by
 * reflection from the constant and parsed from the migration that declares the constraint. Nothing
 * here is a regex over Java: F2-221's own scratch census produced a false DISAGREE on
 * {@code ChangeRequest#status} by matching {@code OUTCOMES.contains(...)} — the vocabulary of a
 * <i>different field</i> in the same method — and reflection cannot make that mistake.
 *
 * <p><b>Hermetic.</b> The CHECK text comes from {@code db/migration/sqlserver}, the way
 * {@code PromotionVocabularyTest} and {@code ClosedVocabularyPinTest} already read it, so
 * {@code mvn -o clean package} still needs no database.
 *
 * <p>⚠ <b>Two things this gate deliberately does NOT do.</b>
 * <ul>
 *   <li>It does not compare a CHECK against a machine's <i>case labels</i>. For a
 *       {@code switch (from) { … default -> false }} machine an omitted case <b>is</b> the terminal
 *       declaration, so a terminal state legitimately never appears as a label — measured on
 *       {@code ControlAssessment#workflowStatus}, whose 17 dev / 16 prod {@code APPROVED} rows a
 *       label-based scan reported as "untransitionable" when they are simply finished. 🔑 <b>"The row
 *       is stuck" and "the row is finished" are the same observation unless the machine declares its
 *       terminal states explicitly.</b>
 *   <li>It does not add CHECKs. F2-183's rule is that a constraint and its writer land in the same
 *       commit, and most of {@link #UNCONSTRAINED} still has an open writer.
 * </ul>
 */
class StateMachineColumnMirrorTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MIGRATIONS =
        Path.of("src", "main", "resources", "db", "migration", "sqlserver");

    /**
     * The machines whose vocabulary is a named constant AND whose column carries a CHECK.
     *
     * <p>{@code Type#field → [service class, constant, constraint]}. Both halves are resolved, never
     * asserted: the constant by reflection, the constraint from the migration that declares it.
     */
    private static final Map<String, String[]> MIRRORED = Map.ofEntries(
        // F2-289 — the first MAP-shaped machine in this bucket, and it could not have been here
        // before. {@link #constantValue} cast the constant to Set<String>, so a machine that
        // declares its vocabulary as Map<String, Set<String>> — i.e. as keys WITH their edges, the
        // more precise declaration — was structurally excluded from being mirrored at all. That is
        // why F2-221's UNCONSTRAINED entry for this register gave a reason about its writer: the
        // shape was never the stated obstacle, because nothing had tried. F2-198's shape inside the
        // gate, where the narrowing is the Java TYPE the resolver happens to cast to.
        Map.entry("AmlAlert#status", new String[]{
            "com.tss.grc.module.ethics.service.AmlAlertService",
            "TRANSITIONS", "CK_aml_alerts_status"}),
        Map.entry("AuthorityDocument#status", new String[]{
            "com.tss.grc.module.platform.regulatory.service.AuthorityDocumentService",
            "STATUSES", "ck_authdoc_status"}),
        Map.entry("BcmGap#status", new String[]{
            "com.tss.grc.module.bcm.cycle.service.BcmRemediationService",
            "GAP_STATUSES", "ck_gap_status"}),
        Map.entry("BcmRisk#status", new String[]{
            "com.tss.grc.module.bcm.cycle.service.BcmRiskService",
            "STATUSES", "ck_bcm_risk_status"}),
        Map.entry("BcpPlan#status", new String[]{
            "com.tss.grc.module.bcm.cycle.service.BcpPlanService",
            "STATUSES", "ck_bcp_status"}),
        Map.entry("ClimateRiskOpportunity#status", new String[]{
            "com.tss.grc.module.sust.climate.service.ClimateRiskOpportunityService",
            "STATUSES", "ck_cro_status"}),
        Map.entry("ContinuityStrategy#approvalStatus", new String[]{
            "com.tss.grc.module.bcm.cycle.service.ContinuityStrategyService",
            "APPROVAL_STATUSES", "ck_strategy_approval"}),
        Map.entry("ControlAssessment#workflowStatus", new String[]{
            "com.tss.grc.module.platform.controls.service.ControlLibraryService",
            "ASSESSMENT_STATUSES", "ck_ca_workflow"}),
        Map.entry("CrisisEvent#status", new String[]{
            "com.tss.grc.module.bcm.cycle.service.CrisisEventService",
            "STATUSES", "CK_crisis_events_status"}),
        Map.entry("GhgEmissionRecord#status", new String[]{
            "com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService",
            "STATUSES", "ck_ghg_status"}),
        Map.entry("Issue#status", new String[]{
            "com.tss.grc.module.platform.service.IssueService",
            "STATUSES", "CK_issues_status"}),
        Map.entry("SustainabilityReport#reportType", new String[]{
            "com.tss.grc.module.sust.annex.service.SustainabilityReportService",
            "REPORT_TYPES", "ck_sr_type"}),
        Map.entry("SustainabilityReport#status", new String[]{
            "com.tss.grc.module.sust.annex.service.SustainabilityReportService",
            "REPORT_STATUSES", "ck_sr_status"}));

    /**
     * Machines whose column carries no CHECK at all — the vocabulary lives only in the heads of its
     * readers (F2-196). Every entry's claim is verified against the migrations below, so a reason
     * here cannot be a recollection.
     *
     * <p>Decrease-only: {@link #BASELINE_UNCONSTRAINED}. Two came off this list in F2-221 (V376).
     */
    private static final Map<String, String> UNCONSTRAINED = Map.of(
        "BcmBiaAssessment#approvalStatus",
        "bcm.bia_assessments.approval_status. Has a named STATUSES constant and no CHECK, so it is "
        + "the readiest candidate after F2-221's two — blocked only on reading its writers.",

        "Disclosure#status",
        "ethics.disclosures.status. Map-shaped machine, 18 rows, 0 outside the keys in both "
        + "databases; the transition writes no vocabulary literal so there is no constant to mirror.",

        "EthicsCase#status",
        "ethics.cases.status. NOT SAFE TO CONSTRAIN: 11 of 30 rows in BOTH databases hold "
        + "INVESTIGATING or TRIAGED, which are not keys of TRANSITIONS, so those cases are already "
        + "untransitionable by any route. Adding a CHECK would fail Flyway; collapsing the synonyms "
        + "rewrites live rows and is an owner decision (F2-183/F2-196/F2-203). Filed by F2-221.",

        "RiskTreatment#status",
        "risk.risk_treatments.status. Has a named TREATMENT_STATUSES constant and no CHECK; 54 rows, "
        + "0 outside the machine in both databases. Same candidate class as BcmBiaAssessment.");

    /**
     * Machines the discriminator finds whose written field is not a vocabulary at all — a person's
     * name, an id, a physical location. Structurally these ARE transitions (they load a row, read
     * the field, throw conditionally and write it from a scalar); semantically there is nothing for
     * a CHECK to mirror.
     *
     * <p>⚠ This bucket is why F2-221's first reachability pass reported {@code ApprovalRequest#decidedBy}
     * as "9 of 9 rows untransitionable": it was comparing four analysts' names against a status
     * vocabulary. A scan not scoped to the shape of its claim invents findings.
     */
    private static final Map<String, String> NOT_A_VOCABULARY = Map.of(
        "ApprovalRequest#decidedBy", "approvals.requests.decided_by holds the deciding principal.",
        "AttackSurfaceFinding#capaTaskId", "cyber.attack_surface_finding.capa_task_id is a link.",
        "DisposalCertificate#executedBy", "privacy.disposal_certificates.executed_by is a person.",
        "ForensicsArtefact#currentCustodian",
        "socops.forensics_artefact.current_custodian is the chain-of-custody holder.",
        "ForensicsArtefact#currentLocation",
        "socops.forensics_artefact.current_location is free text — an evidence-locker reference.");

    /**
     * Machines whose column IS constrained but whose service declares no named vocabulary constant,
     * so there is nothing to mirror it against by reflection.
     *
     * <p>⚠ Both entries were caught by reading, after the scratch census paired them with the wrong
     * constant: {@code ChangeManagementService} holds {@code OUTCOMES} (the outcome field's four
     * tokens) and {@code NdiAssessmentCycleService} holds {@code JUMP_STATUSES = Set.of("ARCHIVED")}
     * (one jump target). 🔑 <b>A constant that is in scope is not the constant that governs.</b>
     */
    private static final Map<String, String> NO_DECLARED_CONSTANT = Map.of(
        "ChangeRequest#status",
        "ck_change_status exists; the machine is a switch and the only Set<String> in the guard's "
        + "reach is OUTCOMES, which governs change_requests.outcome.",
        "CorrectiveAction#status", "ck_ca_status exists; the machine writes no vocabulary literal.",
        "NdiAssessmentCycle#status",
        "ck_ndi_cycle_status exists; JUMP_STATUSES is a one-element jump-target set, not the "
        + "vocabulary.",
        "NdiImprovementPlan#status", "ck_ndi_plan_status exists; switch machine, no named constant.",
        "PolicyDocument#status", "ck_policy_status exists; the machine writes no vocabulary literal.",
        "QuestionnaireTemplate#status",
        "ck_qt_status exists; F2-220 deleted TEMPLATE_STATUSES because its only reader was the "
        + "create's membership test — the check that admitted ACTIVE — and transitionTemplate's "
        + "switch is exhaustive (F2-184: an inert construct is worse than none).");

    /**
     * Decrease-only. F2-221 measured 7 and closed 2 with V376; F2-289 closed {@code AmlAlert#status}
     * with V393 and moved it to {@link #MIRRORED}.
     *
     * <p>⚠ Re-derived from the map's own size, never by subtracting one from the number written
     * here (F2-207) — and lowered in the SAME commit as the sweep, because a ceiling left in place
     * after a fix makes a tolerated member indistinguishable from a reviewed one (house rule 3,
     * which F2-207 had to learn from the inside).
     */
    private static final int BASELINE_UNCONSTRAINED = 4;

    // ── plumbing ──────────────────────────────────────────────────────────────

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every declared machine in the tree, from the discriminator F2-220 already ships. */
    static Set<String> declaredMachines() {
        Set<String> out = new TreeSet<>();
        try (var s = Files.walk(MAIN_JAVA)) {
            for (Path p : s.filter(x -> x.getFileName().toString().endsWith("Service.java"))
                           .collect(Collectors.toList())) {
                out.addAll(ServerStampedFieldContractTest.machineOwned(
                    ServerStampedFieldContractTest.publicMethods(
                        ServerStampedFieldContractTest.stripComments(read(p)))).keySet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** The tokens a named CHECK admits, parsed from whichever migration declares it. */
    static Set<String> checkVocabulary(String constraint) {
        Pattern decl = Pattern.compile(
            "CONSTRAINT\\s+" + Pattern.quote(constraint) + "\\s+CHECK\\s*\\(",
            Pattern.CASE_INSENSITIVE);
        List<Path> files;
        try (var s = Files.walk(MIGRATIONS)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted()
                     .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> found = null;
        for (Path p : files) {
            Matcher m = decl.matcher(read(p));
            while (m.find()) {
                // brace-match the CHECK's own parentheses so a trailing clause cannot bleed in
                String src = read(p);
                int i = m.end() - 1, depth = 0, j = i;
                for (; j < src.length(); j++) {
                    if (src.charAt(j) == '(') depth++;
                    else if (src.charAt(j) == ')' && --depth == 0) break;
                }
                Set<String> v = new TreeSet<>();
                Matcher t = Pattern.compile("N?'([A-Z][A-Z0-9_]*)'").matcher(src.substring(i, j + 1));
                while (t.find()) v.add(t.group(1));
                found = v;   // last declaration wins — a later migration may redefine it
            }
        }
        return found;
    }

    /**
     * The tokens a named vocabulary constant declares, whichever of the two shapes it uses.
     *
     * <p>⚠ F2-289 — this used to be a bare cast to {@code Set<String>}, which silently excluded
     * every <b>Map-shaped</b> machine from {@link #MIRRORED}: a
     * {@code Map<String, Set<String>>} declares the vocabulary as keys <i>with their edges</i>, and
     * {@code new TreeSet<>((Set<String>) aMap)} is a {@code ClassCastException}, not a smaller
     * answer. So the more precisely a machine declared itself, the less this gate could check —
     * F2-198's shape, where the narrowing is the Java type the resolver happens to cast to.
     *
     * <p>A machine's vocabulary is its <b>state set</b>, so for a Map that is the key set. Reading
     * the values instead would give the reachable targets, which for a terminal-bearing machine is a
     * strict subset and would report every terminal state as "the column admits but the machine
     * never writes".
     */
    private static Set<String> constantValue(String fqn, String name) throws Exception {
        Field f = Class.forName(fqn).getDeclaredField(name);
        f.setAccessible(true);
        Object raw = f.get(null);
        if (raw instanceof Map<?, ?> m) {
            Set<String> out = new TreeSet<>();
            for (Object k : m.keySet()) out.add((String) k);
            return out;
        }
        Set<String> out = new TreeSet<>();
        for (Object v : (Set<?>) raw) out.add((String) v);
        return out;
    }

    // ── the assertions ────────────────────────────────────────────────────────

    /**
     * Nothing may be silently omitted: every machine the discriminator finds is classified.
     *
     * <p>F2-204's rule — membership is a measurement, not a recollection. A new
     * {@code transition(Long, String)} anywhere in the tree fails the build until somebody says
     * which of the four buckets it belongs in.
     */
    @Test
    void everyDeclaredMachineIsClassifiedExactlyOnce() {
        Set<String> classified = new TreeSet<>(MIRRORED.keySet());
        List<String> dupes = new ArrayList<>();
        for (Map<String, String> m : List.of(UNCONSTRAINED, NOT_A_VOCABULARY, NO_DECLARED_CONSTANT)) {
            for (String k : m.keySet()) {
                if (!classified.add(k)) dupes.add(k);
            }
        }
        assertThat(dupes).as("a machine may sit in exactly one bucket").isEmpty();
        assertThat(declaredMachines())
            .as("the population is DERIVED from the discriminator, so this fails in both "
                + "directions: a new machine is unclassified, a deleted one leaves a stale entry")
            .containsExactlyInAnyOrderElementsOf(classified);
    }

    /**
     * The comparison, extracted so the unresolvable-constraint branch can be exercised directly.
     *
     * <p>⚠ It is here because a mutation replacing {@code if (sql == null)} with {@code if (false)}
     * SURVIVED the first harness run — not because the gate was loose, but because on a green tree
     * every constraint resolves, so the branch is unreachable from the corpus. Only the pre-fix
     * control (V376 removed) reached it. A branch exercised solely by a control that is not part of
     * the suite is an unasserted behaviour.
     *
     * @return a description of the drift, or {@code null} when the two agree.
     */
    static String mirrorDrift(String constant, String constraint, Set<String> java, Set<String> sql) {
        if (sql == null) return "no migration declares CONSTRAINT " + constraint;
        Set<String> onlyJava = new LinkedHashSet<>(java);
        onlyJava.removeAll(sql);
        Set<String> onlySql = new LinkedHashSet<>(sql);
        onlySql.removeAll(java);
        if (onlyJava.isEmpty() && onlySql.isEmpty()) return null;
        return constant + " vs " + constraint
            + (onlyJava.isEmpty() ? "" : "  machine can write but column REFUSES: " + onlyJava)
            + (onlySql.isEmpty() ? "" : "  column admits but machine never writes: " + onlySql);
    }

    /** The load-bearing leg: the constant and the column must name the same tokens. */
    @Test
    void everyMirroredMachineNamesExactlyWhatItsColumnAdmits() throws Exception {
        Map<String, String> drift = new LinkedHashMap<>();
        for (var e : new TreeMap<>(MIRRORED).entrySet()) {
            String[] v = e.getValue();
            String d = mirrorDrift(v[1], v[2], constantValue(v[0], v[1]), checkVocabulary(v[2]));
            if (d != null) drift.put(e.getKey(), d);
        }
        assertThat(drift)
            .as("a token the machine writes and the column refuses is a live 547 on a legitimate "
                + "transition; a token the column admits and the machine never writes is a state no "
                + "route can leave. Both directions, always.")
            .isEmpty();
    }

    /**
     * The Map-shape branch of {@link #constantValue} is load-bearing, not defensive.
     *
     * <p>F2-289 widened that resolver, and a widening whose new branch nothing exercises is
     * indistinguishable from one that was never needed — F2-248's rule, where deleting a token from
     * a vocabulary SURVIVED because the anti-vacuity list had not grown with it. So this asserts the
     * population it admitted: at least one mirrored machine declares its states as a {@code Map}
     * with edges, and it resolves to a non-trivial vocabulary rather than to an empty set.
     *
     * <p>⚠ Deleting the branch does not merely shrink an answer here, it throws
     * {@code ClassCastException} — so the mirror leg above fails too. That is a second kill and not
     * a reason to skip this one: a future refactor that made the resolver return {@code Set.of()}
     * for an unrecognised shape would be silent, and silent is the direction this register keeps
     * getting caught by.
     */
    @Test
    void theResolverHandlesAMapShapedMachineAndSomethingActuallyUsesIt() throws Exception {
        List<String> mapShaped = new ArrayList<>();
        for (var e : new TreeMap<>(MIRRORED).entrySet()) {
            String[] v = e.getValue();
            Field f = Class.forName(v[0]).getDeclaredField(v[1]);
            f.setAccessible(true);
            if (f.get(null) instanceof Map<?, ?>) mapShaped.add(e.getKey());
        }
        assertThat(mapShaped)
            .as("F2-289 widened constantValue to resolve a Map-shaped machine; if no mirrored "
                + "machine is Map-shaped, that branch is untested and the next reader will read its "
                + "presence as coverage")
            .isNotEmpty();
        for (String key : mapShaped) {
            String[] v = MIRRORED.get(key);
            assertThat(constantValue(v[0], v[1]))
                .as("%s resolves through the Map branch, so it must yield the machine's STATES "
                    + "(its keys). An empty or single-element answer means the branch is returning "
                    + "something other than the vocabulary.", key)
                .hasSizeGreaterThan(1);
        }
    }

    /**
     * Each mirrored constraint is declared exactly once across the whole migration folder.
     *
     * <p>This is the assumption {@link #checkVocabulary}'s last-declaration-wins rests on, and it was
     * implicit until a mutation making it first-declaration-wins survived as an equivalent mutant.
     * It is also worth having on its own: a constraint dropped and recreated in a later migration is
     * two definitions that can disagree, and the one the gate reads would be a coin toss.
     */
    @Test
    void everyMirroredConstraintIsDeclaredExactlyOnce() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String[] v : MIRRORED.values()) {
            Pattern decl = Pattern.compile("CONSTRAINT\\s+" + Pattern.quote(v[2]) + "\\s+CHECK\\s*\\(",
                                           Pattern.CASE_INSENSITIVE);
            int n = 0;
            try (var s = Files.walk(MIGRATIONS)) {
                for (Path f : s.filter(x -> x.getFileName().toString().endsWith(".sql"))
                               .collect(Collectors.toList())) {
                    Matcher m = decl.matcher(read(f));
                    while (m.find()) n++;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (n != 1) counts.put(v[2], n);
        }
        assertThat(counts).as("declared other than exactly once").isEmpty();
    }

    /**
     * The gate refutes its own claims (F2-204): an "no CHECK on this column" reason is verified
     * against the migrations, not believed.
     */
    @Test
    void everyUnconstrainedMachineReallyHasNoCheckOnThatColumn() {
        Map<String, String> wrong = new LinkedHashMap<>();
        for (var e : UNCONSTRAINED.entrySet()) {
            // the reason must OPEN with the artefact it is about, so the claim is machine-checkable
            Matcher m = Pattern.compile("^(\\w+)\\.(\\w+)\\.(\\w+)\\b").matcher(e.getValue());
            if (!m.find()) {
                wrong.put(e.getKey(), "the reason must OPEN with schema.table.column; got: "
                    + e.getValue().substring(0, Math.min(48, e.getValue().length())));
                continue;
            }
            String table = m.group(2);
            String col = m.group(3);
            if (migrationDeclaresCheckOn(table, col)) {
                wrong.put(e.getKey(), m.group() + " — a migration DOES declare a CHECK naming "
                    + col + " on " + table + "; this entry claims the opposite");
            }
        }
        assertThat(wrong).as("an unaudited reason is an assumption wearing a citation").isEmpty();
        assertThat(UNCONSTRAINED)
            .as("decrease-only: a state column may lose its CHECK-lessness, never gain it")
            .hasSizeLessThanOrEqualTo(BASELINE_UNCONSTRAINED);
    }

    /**
     * True when any migration declares a CHECK, inside a statement about {@code table}, whose body
     * <b>opens with</b> {@code column}.
     *
     * <p>⚠ The first draft of this was {@code CONSTRAINT \w+ CHECK\([^;]{0,4000}?\bcolumn\b} over any
     * file whose text mentioned the table anywhere, and it reported a CHECK on
     * {@code ethics.cases.status}, {@code ethics.disclosures.status} and
     * {@code risk.risk_treatments.status} — none of which has one. A 4000-character window around the
     * commonest word in the schema matches a constraint on a different column of a different table in
     * the same file. 🔑 <b>Scope a scan to the shape of its claim</b> — third time in this one slice,
     * after a {@code LIKE '%[status]%'} character class and a {@code contains()} paired with the wrong
     * field's vocabulary. It over-reported, which is the direction that fails loudly.
     *
     * <p>Two narrowings do the work: statements are cut at each {@code CREATE|ALTER TABLE} so a body
     * belongs to exactly one table, and the CHECK's body must OPEN with the column, which is what
     * makes it a constraint <i>on</i> that column rather than one that merely mentions it.
     */
    static boolean migrationDeclaresCheckOn(String table, String column) {
        Pattern stmt = Pattern.compile(
            "\\b(?:CREATE|ALTER)\\s+TABLE\\s+\\[?(\\w+)\\]?\\.\\[?(\\w+)\\]?",
            Pattern.CASE_INSENSITIVE);
        Pattern opensWith = Pattern.compile(
            "^\\s*\\[?" + Pattern.quote(column) + "\\]?\\s*(?:IN|=|<>|NOT\\s+IN)\\b",
            Pattern.CASE_INSENSITIVE);
        try (var s = Files.walk(MIGRATIONS)) {
            for (Path f : s.filter(x -> x.getFileName().toString().endsWith(".sql")).sorted()
                           .collect(Collectors.toList())) {
                String src = read(f);
                Matcher st = stmt.matcher(src);
                List<int[]> spans = new ArrayList<>();
                List<String> tables = new ArrayList<>();
                while (st.find()) {
                    if (!spans.isEmpty()) spans.get(spans.size() - 1)[1] = st.start();
                    spans.add(new int[]{st.end(), src.length()});
                    tables.add(st.group(2));
                }
                for (int k = 0; k < spans.size(); k++) {
                    if (!tables.get(k).equalsIgnoreCase(table)) continue;
                    String scope = src.substring(spans.get(k)[0], spans.get(k)[1]);
                    Matcher c = Pattern.compile("\\bCHECK\\s*\\(", Pattern.CASE_INSENSITIVE)
                                       .matcher(scope);
                    while (c.find()) {
                        int i = c.end() - 1, depth = 0, j = i;
                        for (; j < scope.length(); j++) {
                            if (scope.charAt(j) == '(') depth++;
                            else if (scope.charAt(j) == ')' && --depth == 0) break;
                        }
                        String body = scope.substring(i + 1, Math.min(j, scope.length()));
                        if (opensWith.matcher(body).find()) return true;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }

    /**
     * Positive control. A gate whose every assertion passes on the first run is indistinguishable
     * from one whose detectors have rotted, so both halves are exercised against literal fixtures.
     */
    @Test
    void theDetectorSeesDriftInBothDirections() {
        Set<String> ck = checkVocabulary("CK_issues_status");
        assertThat(ck).as("the migration parser resolves V376's own constraint")
            .containsExactlyInAnyOrder("OPEN", "IN_PROGRESS", "BLOCKED",
                                       "RESOLVED", "CLOSED", "CANCELLED");

        assertThat(checkVocabulary("ck_no_such_constraint_anywhere"))
            .as("an unresolvable constraint is null, never an empty set — an empty set would "
                + "silently compare equal to an empty vocabulary")
            .isNull();

        assertThat(migrationDeclaresCheckOn("issues", "status")).isTrue();
        assertThat(migrationDeclaresCheckOn("cases", "status"))
            .as("ethics.cases is the register F2-221 measured as unconstrained; if this ever "
                + "becomes true the UNCONSTRAINED entry is stale")
            .isFalse();

        // ⚠ The line above does NOT exercise the column-scoping, and believing it did cost a
        // surviving mutant: ethics.cases has no CHECK on ANY column, so an un-scoped detector has
        // nothing to falsely match there and answers false for the right reason by accident.
        // 🔑 A negative control has to reach the code it controls (F2-220). bcm.gaps does: it carries
        // three vocabulary CHECKs, so a detector that has stopped checking WHICH column a CHECK
        // constrains will claim this nonexistent one is constrained. It is deliberately NOT one of
        // the tables V376 touches, so the control keeps discriminating on a pre-V376 tree too.
        assertThat(migrationDeclaresCheckOn("gaps", "no_such_column_anywhere"))
            .as("a table WITH vocabulary CHECKs on other columns is the only fixture that can tell "
                + "a column-scoped detector from an un-scoped one")
            .isFalse();
        assertThat(migrationDeclaresCheckOn("gaps", "status"))
            .as("...and the same fixture must still find the CHECK that is really there").isTrue();

        // A RANGE is not a vocabulary, and this gate is about vocabularies: CK_crisis_events_severity
        // is `severity BETWEEN 1 AND 5` and is deliberately invisible here. Asserted so the limit is
        // a decision on the record rather than an accident of which operators the pattern lists.
        assertThat(migrationDeclaresCheckOn("crisis_events", "severity"))
            .as("range CHECKs are out of scope by design").isFalse();

        // the unresolvable-constraint branch, which no green corpus can reach
        assertThat(mirrorDrift("STATUSES", "ck_gone", Set.of("OPEN"), null))
            .isEqualTo("no migration declares CONSTRAINT ck_gone");
        assertThat(mirrorDrift("STATUSES", "ck_ok", Set.of("OPEN"), Set.of("OPEN"))).isNull();

        // and the comparison itself: a one-token difference must be visible in EACH direction,
        // because the two directions are different defects — a token the machine writes and the
        // column refuses is a 547 on a legitimate transition, a token the column admits and the
        // machine never writes is a state no route can leave.
        Set<String> machine = new TreeSet<>(Set.of("OPEN", "CLOSED"));
        Set<String> column = new TreeSet<>(Set.of("OPEN", "CLOSED", "ARCHIVED"));
        assertThat(diff(machine, column)).as("machine ⊂ column: nothing the column refuses").isEmpty();
        assertThat(diff(column, machine)).as("column admits one state the machine never writes")
            .containsExactly("ARCHIVED");
        assertThat(diff(machine, Set.of("OPEN")))
            .as("and the refusing direction, which is the live-547 one").containsExactly("CLOSED");
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    /**
     * Every reason is substantial and names the artefact it is about — F2-188's rule, after six of
     * its own first-draft entries turned out to be bare labels.
     */
    @Test
    void everyReasonNamesAColumnAndSaysSomething() {
        List<String> thin = new ArrayList<>();
        for (Map<String, String> m : List.of(UNCONSTRAINED, NOT_A_VOCABULARY, NO_DECLARED_CONSTANT)) {
            m.forEach((k, reason) -> {
                if (reason.length() < 40) {
                    thin.add(k + ": too short to be a reason");
                } else if (!reason.matches("(?s).*(\\b\\w+\\.\\w+\\b|\\b[cC][kK]_\\w+\\b).*")) {
                    // a dotted artefact (schema.table.column) or the constraint it is about —
                    // NO_DECLARED_CONSTANT reasons legitimately name only the constraint
                    thin.add(k + ": names no artefact");
                }
            });
        }
        if (!thin.isEmpty()) fail("thin reasons: " + thin);
    }
}
