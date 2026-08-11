package com.tss.grc.module.intake.spi;

import com.tss.grc.module.audit_eng.intake.EngagementPromotionAdapter;
import com.tss.grc.module.bcm.intake.BiaPromotionAdapter;
import com.tss.grc.module.change_adapters.ChangePromotionAdapter;
import com.tss.grc.module.compliance.intake.FindingPromotionAdapter;
import com.tss.grc.module.ethics.intake.EthicsCasePromotionAdapter;
import com.tss.grc.module.incident.intake.IncidentPromotionAdapter;
import com.tss.grc.module.policy.intake.PolicyDocumentPromotionAdapter;
import com.tss.grc.module.policy.intake.PolicyExceptionPromotionAdapter;
import com.tss.grc.module.privacy.intake.BreachPromotionAdapter;
import com.tss.grc.module.privacy.intake.DpiaPromotionAdapter;
import com.tss.grc.module.privacy.intake.DsrPromotionAdapter;
import com.tss.grc.module.risk.intake.RiskPromotionAdapter;
import com.tss.grc.module.tprm.intake.VendorPromotionAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-180 — every value a promotion adapter writes into a {@code CHECK}-constrained column must be a
 * value <b>that column</b> accepts.
 *
 * <h3>What went wrong, and why source review could not find it</h3>
 *
 * <p>F2-172 built promotion as thirteen adapters and verified <b>one</b> of them against a real
 * database, reasoning about the other twelve from their source. Each adapter hands a <em>string</em> to
 * a service, which maps it to a column carrying a {@code CHECK} the Java side never mentions: no
 * compiler error, no failing unit test (the services are mocked), and {@code IntakePromotionContractTest}
 * checks the <em>wiring</em> — that a target has a port, that the port returns an id it wrote — not that
 * the values the port supplies are ones the schema accepts. Three adapters were writing values their
 * columns had never allowed:
 *
 * <ul>
 *   <li>{@link RiskPromotionAdapter} wrote {@code IDENTIFIED} (F2-175) — {@code CK_risks_status} allows
 *       {@code OPEN|UNDER_REVIEW|MITIGATING|ACCEPTED|CLOSED|ARCHIVED|TRANSFERRED};</li>
 *   <li>{@link VendorPromotionAdapter} wrote {@code ONBOARDING} — {@code CK_vendors_status} allows
 *       {@code ACTIVE|INACTIVE|PENDING_REVIEW|TERMINATED};</li>
 *   <li>{@link IncidentPromotionAdapter} passed the submitter's own category through untouched —
 *       {@code CK_incidents_category} allows five values and the forms feeding it offer none of the four
 *       that {@code SECURITY_INCIDENT} renders.</li>
 * </ul>
 *
 * <p>🔑 All three chose a word for what it <b>means</b>. Every one of those meanings was right; none of
 * them was a value the column accepts. That is not carelessness, it is the absence of a place where the
 * two artefacts meet — which is what this test is.
 *
 * <h3>⚠ Why this is keyed to the constraint AND checked against the table</h3>
 *
 * <p>The first attempt at a static sweep here compared each adapter's uppercase literals against
 * <em>every</em> CHECK in the database and reported the known-broken {@code RiskPromotionAdapter} as
 * clean, because {@code IDENTIFIED} is a legal value of some other table's column. <b>An unscoped
 * cross-check is worse than none: it answers "clean" for the one case you already know is broken.</b> So
 * every binding below names its constraint, and {@link #everyDeclaredConstraintIsOnItsDeclaredTable()}
 * proves from the migration text that the constraint really is declared on that adapter's target table.
 *
 * <h3>Why it reads the migrations rather than the database</h3>
 *
 * <p>{@code mvn -o clean package} is hermetic — no database, no secrets — and must stay that way. The
 * migration SQL is the only copy of these vocabularies inside the repository. Verified once against the
 * live dev database while writing this: all 13 constraints parse, all 13 resolve to the expected table,
 * and every parsed vocabulary matches {@code sys.check_constraints} exactly.
 */
class PromotionVocabularyTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");
    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    // ─────────────────────────────────────────────────────────────────────────────
    // The registry. One entry per adapter; the build fails if an adapter is missing.
    // ─────────────────────────────────────────────────────────────────────────────

    /** A value (or set of values) this adapter writes, and the constraint that governs it. */
    private record Binding(String constraint, Class<?> owner, String field) {}

    /**
     * @param table     the adapter's target table, {@code schema.table}
     * @param bindings  the constrained columns it writes, each pointing at the constant holding the value
     * @param unwritten constrained columns on that table this adapter does <b>not</b> write, with the
     *                  reason — an explicit exemption, so that adding a write to one of them means
     *                  deleting a line here and being seen to do it
     */
    private record Target(String table, List<Binding> bindings, Map<String, String> unwritten) {}

    private static Binding severity(String constraint) {
        // One clamp, five columns. IntakePromotionContext#severityOrMedium is the reason F2-180 found two
        // broken adapters and not four: severity was schema-aware from the start.
        return new Binding(constraint, IntakePromotionContext.class, "SEVERITY_BANDS");
    }

    private static final Map<Class<?>, Target> REGISTRY = new LinkedHashMap<>() {{
        put(RiskPromotionAdapter.class, new Target("risk.risks",
            List.of(new Binding("CK_risks_status", RiskPromotionAdapter.class, "PROMOTED_STATUS")),
            Map.of("source_module", "left null: a promoted intake is not sourced from another module's "
                + "register, and CK_risks_source has no member meaning 'raised by an employee'")));

        put(VendorPromotionAdapter.class, new Target("tprm.vendors",
            List.of(new Binding("CK_vendors_status", VendorPromotionAdapter.class, "PROMOTED_STATUS"),
                    severity("CK_vendors_risk_rating")),
            Map.of()));

        put(IncidentPromotionAdapter.class, new Target("cyber.incidents",
            List.of(new Binding("CK_incidents_category", IncidentPromotionAdapter.class, "PROMOTED_CATEGORIES"),
                    new Binding("CK_incidents_status", IncidentPromotionAdapter.class, "PROMOTED_STATUS"),
                    severity("CK_incidents_severity")),
            Map.of()));

        put(FindingPromotionAdapter.class, new Target("compliance.findings",
            List.of(new Binding("CK_findings_status", FindingPromotionAdapter.class, "PROMOTED_STATUS"),
                    severity("CK_findings_severity")),
            Map.of()));

        put(PolicyExceptionPromotionAdapter.class, new Target("policy.exceptions",
            List.of(new Binding("ck_policy_exc_status", PolicyExceptionPromotionAdapter.class, "PROMOTED_STATUS"),
                    severity("ck_policy_exc_risk")),
            Map.of()));

        put(ChangePromotionAdapter.class, new Target("change_mgmt.change_requests",
            List.of(new Binding("ck_change_status", ChangePromotionAdapter.class, "PROMOTED_STATUS"),
                    new Binding("ck_change_type", ChangePromotionAdapter.class, "CHANGE_TYPES"),
                    // The fallback is bound separately: a clamp whose default is outside the vocabulary
                    // fails only for the callers who omit the field, which is the quietest way to be wrong.
                    new Binding("ck_change_type", ChangePromotionAdapter.class, "CHANGE_TYPE_DEFAULT"),
                    severity("ck_change_risk")),
            Map.of("outcome", "an outcome is recorded after implementation; a promoted change has none",
                   "ratification_state", "ChangeRequest defaults it to NOT_REQUIRED, which is what a "
                       + "normally-raised change is — and is why ck_change_ratification_clock is satisfied "
                       + "without this adapter setting ratification_due_at")));

        put(PolicyDocumentPromotionAdapter.class, new Target("policy.documents",
            List.of(),
            Map.of("status", "PolicyManagementService.create pins DRAFT itself and says why: a client that "
                       + "could create one already approved would skip the state machine and the 4-eyes gate",
                   "review_cadence", "left null; a cadence is set when the document is adopted")));

        // ── The six targets whose tables carry no single-column CHECK vocabulary at all. Declared
        // deliberately: `noUndeclaredConstrainedColumn` turns "there is no CHECK today" into "you will
        // hear about it on the day somebody adds one", which is the only way an empty list can be a pin
        // rather than an omission.
        put(DpiaPromotionAdapter.class,       new Target("privacy.dpia",                  List.of(), Map.of()));
        put(BreachPromotionAdapter.class,     new Target("privacy.data_breaches",         List.of(), Map.of()));
        put(DsrPromotionAdapter.class,        new Target("privacy.data_subject_requests", List.of(), Map.of()));
        put(BiaPromotionAdapter.class,        new Target("bcm.bia_records",               List.of(), Map.of()));
        put(EthicsCasePromotionAdapter.class, new Target("ethics.cases",                  List.of(), Map.of()));
        put(EngagementPromotionAdapter.class, new Target("audit_eng.engagements",         List.of(), Map.of()));
    }};

    // ─────────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every value an adapter writes is in its own column's CHECK vocabulary")
    void everyBoundValueIsInItsColumnsVocabulary() throws Exception {
        Map<String, Constraint> constraints = parseConstraints();
        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<Class<?>, Target> e : REGISTRY.entrySet()) {
            for (Binding b : e.getValue().bindings()) {
                Constraint c = constraints.get(b.constraint().toLowerCase());
                assertThat(c).as(
                    "%s declares a binding to %s, which no migration declares. Either the constraint was "
                    + "renamed (update this binding) or it never existed (the binding is a typo, and a typo "
                    + "here silently checks nothing)", e.getKey().getSimpleName(), b.constraint())
                    .isNotNull();

                for (String value : valuesOf(b)) {
                    checked++;
                    if (!c.values().contains(value)) {
                        violations.add(String.format(
                            "%s.%s = '%s' but %s (%s) allows only %s",
                            b.owner().getSimpleName(), b.field(), value,
                            b.constraint(), c.table(), c.values()));
                    }
                }
            }
        }

        // Anti-vacuity: a parse that stops matching would make the loop above examine nothing and pass.
        assertThat(checked).as(
            "this test must actually compare values — %d is too few, so the migration parse or the "
            + "registry has come apart", checked)
            .isGreaterThanOrEqualTo(30);

        assertThat(violations).as(
            "⚠ Each of these is a promotion that fails on a CHECK constraint at the moment a triager "
            + "clicks Promote, and surfaces as a 409 whose message describes a foreign key. F2-180 found "
            + "three. Fix the value, not the constraint, unless widening the vocabulary is a decision "
            + "somebody has actually taken.")
            .isEmpty();
    }

    @Test
    @DisplayName("every declared constraint really is on that adapter's target table")
    void everyDeclaredConstraintIsOnItsDeclaredTable() throws Exception {
        Map<String, Constraint> constraints = parseConstraints();
        List<String> misScoped = new ArrayList<>();
        for (Map.Entry<Class<?>, Target> e : REGISTRY.entrySet()) {
            for (Binding b : e.getValue().bindings()) {
                Constraint c = constraints.get(b.constraint().toLowerCase());
                if (c != null && !c.table().equalsIgnoreCase(e.getValue().table())) {
                    misScoped.add(String.format("%s targets %s but binds %s, declared on %s",
                        e.getKey().getSimpleName(), e.getValue().table(), b.constraint(), c.table()));
                }
            }
        }
        assertThat(misScoped).as(
            "⚠⚠ This is the leg that makes the test worth having. A cross-check not scoped to the target "
            + "table clears the very defect it was written for — measured: the first sweep for F2-180 "
            + "compared literals against every CHECK in the database and called RiskPromotionAdapter clean, "
            + "because IDENTIFIED is legal somewhere else. Found: %s", misScoped)
            .isEmpty();
    }

    @Test
    @DisplayName("no constrained column on a target table is written without being declared")
    void noUndeclaredConstrainedColumn() throws Exception {
        Map<String, Constraint> constraints = parseConstraints();
        List<String> undeclared = new ArrayList<>();

        for (Map.Entry<Class<?>, Target> e : REGISTRY.entrySet()) {
            Target t = e.getValue();
            Set<String> declared = new LinkedHashSet<>();
            for (Binding b : t.bindings()) {
                Constraint c = constraints.get(b.constraint().toLowerCase());
                if (c != null) declared.add(c.column().toLowerCase());
            }
            for (Constraint c : constraints.values()) {
                if (!c.table().equalsIgnoreCase(t.table())) continue;
                String col = c.column().toLowerCase();
                if (declared.contains(col) || t.unwritten().containsKey(col)) continue;
                undeclared.add(String.format("%s → %s.%s (%s, allows %s)",
                    e.getKey().getSimpleName(), t.table(), c.column(), c.constraint(), c.values()));
            }
        }

        assertThat(undeclared).as(
            "A CHECK-constrained column on a promotion target is either written by the adapter — declare "
            + "the constant that holds the value — or deliberately not written, in which case say so in "
            + "`unwritten` with the reason. This is what makes the six targets with no CHECKs today into a "
            + "pin rather than an omission: add a CHECK to privacy.dpia and this fails until somebody has "
            + "looked at DpiaPromotionAdapter. Found: %s", undeclared)
            .isEmpty();
    }

    @Test
    @DisplayName("every adapter in the tree is in the registry, and every registry entry exists")
    void theRegistryCoversEveryAdapter() throws IOException {
        Set<String> inTree = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f).replace("\r\n", "\n");
                if (stripComments(src).contains("implements RegisterPromotionPort")) {
                    inTree.add(f.getFileName().toString().replace(".java", ""));
                }
            }
        }
        Set<String> registered = new TreeSet<>();
        REGISTRY.keySet().forEach(c -> registered.add(c.getSimpleName()));

        // Anti-vacuity: if the scan finds nothing, the equality below would hold only by both being empty.
        assertThat(inTree).as("the source scan must find the adapters — 13 at F2-180")
            .hasSizeGreaterThanOrEqualTo(13);

        assertThat(inTree).as(
            "A new promotion adapter must declare its target table and its schema-bound values here. "
            + "Without this leg the test degrades exactly the way coverage degrades: it keeps passing while "
            + "covering a shrinking share of the thing it names. In tree but unregistered: %s; registered "
            + "but not in tree: %s",
            new TreeSet<>(diff(inTree, registered)), new TreeSet<>(diff(registered, inTree)))
            .isEqualTo(registered);
    }

    @Test
    @DisplayName("the shared severity clamp satisfies all five columns that consume it")
    void sharedSeverityClampMatchesEveryColumnItFeeds() throws Exception {
        Map<String, Constraint> constraints = parseConstraints();
        List<String> feeds = List.of("CK_vendors_risk_rating", "CK_incidents_severity",
            "CK_findings_severity", "ck_policy_exc_risk", "ck_change_risk");

        for (String name : feeds) {
            Constraint c = constraints.get(name.toLowerCase());
            assertThat(c).as("%s must be readable from the migrations", name).isNotNull();
            assertThat(c.values()).as(
                "%s (%s) must admit every band severityOrMedium() can return. If a column narrows its "
                + "vocabulary, the clamp — not the column — is where the adapters find out.", name, c.table())
                .containsAll(IntakePromotionContext.SEVERITY_BANDS);
        }
        assertThat(IntakePromotionContext.SEVERITY_BANDS)
            .as("the default must itself be a legal band")
            .contains(IntakePromotionContext.SEVERITY_DEFAULT);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Migration parsing — verified against sys.check_constraints before being trusted
    // ─────────────────────────────────────────────────────────────────────────────

    private record Constraint(String constraint, String table, String column, Set<String> values) {}

    private static final Pattern TABLE_REF =
        Pattern.compile("(?:ALTER|CREATE)\\s+TABLE\\s+(?:\\[?(\\w+)\\]?\\.)?\\[?(\\w+)\\]?",
            Pattern.CASE_INSENSITIVE);
    /**
     * A column reference inside a CHECK body: an identifier followed by a comparison.
     *
     * <p>⚠ Run against the CHECK body <b>with the quoted literals removed</b>, and both halves of that
     * matter. This pattern's first draft was applied to the raw body and pulled the column name
     * {@code PEND} out of {@code ratification_state<>N'PENDING'} — because {@code PENDING} contains
     * {@code IN}, and {@code IN} was one of the operators. The {@link #noUndeclaredConstrainedColumn()}
     * leg failed on the first run and named the phantom column, which is the anti-vacuity habit paying
     * for itself twice: F2-175's {@code RiskPromotionStatusTest} caught {@code .replace("N","")} turning
     * {@code OPEN} into {@code OPE} the same way. 🔑 <b>A regex over SQL that has not had its string
     * literals removed is parsing two languages at once.</b>
     */
    private static final Pattern COLUMN_REF = Pattern.compile(
        "\\[?([a-z_][a-z0-9_]*)\\]?\\s*(?:<>|!=|=|\\bIN\\b|\\bIS\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LITERAL = Pattern.compile("N?'([A-Z][A-Z0-9_]*)'");
    private static final Pattern ANY_LITERAL = Pattern.compile("N?'[^']*'");

    /**
     * Every single-column CHECK vocabulary in the migration set, keyed by lower-cased constraint name.
     * Later migration versions overwrite earlier ones, so a widened or narrowed constraint is read as it
     * will exist after the whole chain has run — the state a deployed server is actually in.
     */
    private static Map<String, Constraint> parseConstraints() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.list(MIGRATIONS)) {
            files = walk.filter(p -> p.getFileName().toString().endsWith(".sql"))
                .sorted(Comparator.comparingInt(PromotionVocabularyTest::version))
                .toList();
        }
        Map<String, Constraint> out = new LinkedHashMap<>();
        for (Path f : files) {
            String src = Files.readString(f).replace("\r\n", "\n");
            Matcher m = Pattern.compile("(?:CONSTRAINT\\s+)?\\[?(\\w+)\\]?\\s+CHECK\\s*\\(",
                Pattern.CASE_INSENSITIVE).matcher(src);
            while (m.find()) {
                String name = m.group(1);
                String body = balanced(src, m.end() - 1);
                if (body == null) continue;
                Set<String> values = new LinkedHashSet<>();
                Matcher lit = LITERAL.matcher(body);
                while (lit.find()) values.add(lit.group(1));
                if (values.isEmpty()) continue;              // a range or null-check, not a vocabulary

                // Which column(s) does the predicate constrain? Answered with the string literals
                // removed, so a value never masquerades as a column name.
                Set<String> columns = new LinkedHashSet<>();
                Matcher col = COLUMN_REF.matcher(ANY_LITERAL.matcher(body).replaceAll("''"));
                while (col.find()) columns.add(col.group(1).toLowerCase());
                // More than one column means a cross-column rule, not a vocabulary — which is exactly
                // what sys.check_constraints says about them too (parent_column_id is NULL for
                // ck_change_pso and ck_change_ratification_clock). Reading those as vocabularies is how
                // the first run of this test invented a column called PEND.
                if (columns.size() != 1) continue;
                out.put(name.toLowerCase(), new Constraint(
                    name, tableOf(src, m.start()), columns.iterator().next(), values));
            }
        }
        assertThat(out).as("the migration parse must find the platform's CHECK vocabularies")
            .hasSizeGreaterThanOrEqualTo(40);
        return out;
    }

    /** The nearest {@code CREATE TABLE} / {@code ALTER TABLE} above this point owns the constraint. */
    private static String tableOf(String src, int at) {
        Matcher m = TABLE_REF.matcher(src.substring(0, at));
        String last = "?";
        while (m.find()) {
            last = m.group(1) == null ? m.group(2) : m.group(1) + "." + m.group(2);
        }
        return last;
    }

    private static String balanced(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            if (src.charAt(i) == '(') depth++;
            else if (src.charAt(i) == ')' && --depth == 0) return src.substring(open, i + 1);
        }
        return null;
    }

    private static int version(Path p) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(p.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> valuesOf(Binding b) throws Exception {
        Field f = b.owner().getDeclaredField(b.field());
        f.setAccessible(true);
        Object v = f.get(null);
        if (v instanceof String s) return Set.of(s);
        if (v instanceof Set<?> set) return (Set<String>) set;
        throw new IllegalStateException(b.owner().getSimpleName() + "." + b.field()
            + " must be a String or a Set<String>, was " + v);
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
    }
}
