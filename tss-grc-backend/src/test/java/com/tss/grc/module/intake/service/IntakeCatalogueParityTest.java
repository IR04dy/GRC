package com.tss.grc.module.intake.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-172 — the intake catalogue exists twice, and until this test nothing asked whether the two
 * copies agreed.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@code IntakeService}'s javadoc said its routing metadata "is mirrored from frontend
 * INTAKE_CATALOG so the two stay in sync". Measured 2026-08-09: the frontend held <b>26</b> kinds
 * and the server held <b>16</b>. It was mirrored once, at DD-82; the frontend then grew the five
 * DD-88 HRSD workflows and the five DD-94 MOC workflows, and the server learned about none of them.
 * {@code submit()} gates on the catalogue, so all ten would have been rejected with
 * {@code "Unknown intake kind"} — among them the <b>24-hour GOSI workplace-injury notification</b>
 * and the <b>WPS payroll confirmation</b>, both statutory Saudi obligations with a clock.
 *
 * <p>The divergence was harmless only because nothing had ever called the server: F2-171 found the
 * client filing every submission into {@code localStorage}. The first act of pointing the client at
 * the server would have made 10 of 26 forms fail. That is why this check landed <em>before</em> the
 * rewire, not after it — and it is the reusable shape: <b>when a value is duplicated across the two
 * codebases, the sentence claiming they are mirrored is not a control; a test that reads both is.</b>
 *
 * <h3>Why five fields and not just the kind list</h3>
 *
 * <p>A kind-only check would have passed over the more insidious failure. The server previously kept
 * three parallel maps and gated on one of them, so a kind present in {@code SLA_HOURS} and missing
 * from the others got {@code CODE_PREFIX.getOrDefault(kind, "II")} — a reference code under a prefix
 * that means nothing — and a {@code null} assignee. {@link IntakeKindRouting} makes that
 * unrepresentable, and this test compares every field of every entry so a future edit to one side's
 * SLA, assignee, target register or anonymity flag cannot land alone.
 *
 * <p>{@code allowsAnonymous} is the one that must never drift silently: the server honours an
 * anonymity claim only where the catalogue permits it, so a flag that disagrees between the two
 * codebases means a form offering anonymity the server will refuse, or the reverse.
 */
class IntakeCatalogueParityTest {

    /** Same idiom as {@code FrontendEndpointContractTest} — relative, never a machine-local path
     *  (F2-134: three of four measurement scripts hardcoded a Windows path and so never ran in CI). */
    private static final Path FE_INTAKE = Path.of("..", "tss-grc-ui-v2", "src", "lib", "intake.ts");

    /** The number of kinds measured on 2026-08-09. A floor, not an equality — kinds get added. */
    private static final int KIND_FLOOR = 26;

    private record FeEntry(String codePrefix, int slaHours, String defaultTarget,
                           String defaultAssignee, boolean allowsAnonymous) {
    }

    // ── parsing ─────────────────────────────────────────────────────────────────────────────────

    private static String source() {
        try {
            return new String(Files.readAllBytes(FE_INTAKE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + FE_INTAKE.toAbsolutePath(), e);
        }
    }

    private static String block(String src, String startMarker) {
        int from = src.indexOf(startMarker);
        assertThat(from).as("%s not found in %s", startMarker, FE_INTAKE).isGreaterThanOrEqualTo(0);
        int to = src.indexOf("\n};", from);
        assertThat(to).as("unterminated %s block", startMarker).isGreaterThan(from);
        return src.substring(from, to);
    }

    /** The {@code IntakeKind} union — the type the rest of the frontend programs against. */
    private static Set<String> feUnionKinds(String src) {
        int from = src.indexOf("export type IntakeKind =");
        assertThat(from).as("IntakeKind union not found").isGreaterThanOrEqualTo(0);
        int to = src.indexOf(";", from);
        Set<String> kinds = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\|\\s*'([A-Z_]+)'").matcher(src.substring(from, to));
        while (m.find()) {
            kinds.add(m.group(1));
        }
        return kinds;
    }

    private static Map<String, FeEntry> feCatalogue(String src) {
        String cat = block(src, "export const INTAKE_CATALOG");
        Map<String, FeEntry> out = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("^  ([A-Z_]+):\\s*\\{(.*?)^  \\},",
            Pattern.DOTALL | Pattern.MULTILINE).matcher(cat);
        while (entry.find()) {
            String kind = entry.group(1);
            String body = entry.group(2);
            out.put(kind, new FeEntry(
                str(kind, body, "codePrefix"),
                slaHours(kind, body),
                str(kind, body, "defaultTarget"),
                str(kind, body, "defaultAssignee"),
                bool(kind, body, "allowsAnonymous")));
        }
        return out;
    }

    private static String str(String kind, String body, String field) {
        Matcher m = Pattern.compile(field + ":\\s*'([^']*)'").matcher(body);
        assertThat(m.find()).as("%s.%s not parseable", kind, field).isTrue();
        return m.group(1);
    }

    private static boolean bool(String kind, String body, String field) {
        Matcher m = Pattern.compile(field + ":\\s*(true|false)").matcher(body);
        assertThat(m.find()).as("%s.%s not parseable", kind, field).isTrue();
        return Boolean.parseBoolean(m.group(1));
    }

    /** {@code slaHours} is written as an integer or as {@code A * B} (e.g. {@code 24 * 14}). */
    private static int slaHours(String kind, String body) {
        Matcher m = Pattern.compile("slaHours:\\s*(\\d+)(?:\\s*\\*\\s*(\\d+))?\\s*,").matcher(body);
        assertThat(m.find()).as("%s.slaHours not parseable", kind).isTrue();
        int a = Integer.parseInt(m.group(1));
        return m.group(2) == null ? a : a * Integer.parseInt(m.group(2));
    }

    // ── tests ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Anti-vacuity, and its own test rather than a first assertion inside the parity check, because
     * an earlier failing assertion in the same method masks everything after it.
     *
     * <p>This is the assertion that would have caught my scan being wrong rather than the codebase.
     * A regex that matched nothing would report perfect parity over two empty sets — the most
     * reassuring possible wrong answer, and the exact failure mode of three earlier scans on this
     * programme (F2-137's newline, F2-165's regex literal, F2-170's javadoc-as-code).
     */
    @Test
    void theParserResolvesARealCatalogue() {
        Assumptions.assumeTrue(Files.isRegularFile(FE_INTAKE),
            "frontend not checked out at " + FE_INTAKE.toAbsolutePath() + " — nothing to compare");
        String src = source();
        Map<String, FeEntry> fe = feCatalogue(src);
        System.out.println("[F2-172 parity] frontend kinds parsed = " + fe.size()
            + ", server kinds = " + IntakeKindRouting.kinds().size());
        assertThat(fe)
            .as("the frontend INTAKE_CATALOG parse resolved almost nothing — the PARSER is broken, "
                + "not the catalogue. Every parity assertion is vacuous until this passes.")
            .hasSizeGreaterThanOrEqualTo(KIND_FLOOR);
        assertThat(IntakeKindRouting.kinds())
            .as("the server catalogue is smaller than the population measured for F2-172 — a kind "
                + "was deleted, which is a product decision and not a refactor")
            .hasSizeGreaterThanOrEqualTo(KIND_FLOOR);
        // Every parsed field is populated — a null slipping through would make a comparison below
        // succeed against nothing.
        fe.forEach((kind, e) -> {
            assertThat(e.codePrefix()).as("%s.codePrefix", kind).isNotBlank();
            assertThat(e.defaultTarget()).as("%s.defaultTarget", kind).isNotBlank();
            assertThat(e.defaultAssignee()).as("%s.defaultAssignee", kind).isNotBlank();
            assertThat(e.slaHours()).as("%s.slaHours", kind).isGreaterThan(0);
        });
    }

    @Test
    void everyFrontendIntakeKindIsAcceptedByTheServer() {
        Assumptions.assumeTrue(Files.isRegularFile(FE_INTAKE), "frontend not checked out");
        Set<String> fe = new TreeSet<>(feCatalogue(source()).keySet());
        Set<String> missing = new TreeSet<>(fe);
        missing.removeAll(IntakeKindRouting.kinds());
        assertThat(missing)
            .as("Intake kind(s) the frontend offers and IntakeService#submit would reject with "
                + "\"Unknown intake kind\". Add them to IntakeKindRouting with the same five fields "
                + "as the frontend entry. This set was {SAUDIZATION_REPORT, WORKPLACE_INJURY, "
                + "GOSI_EMPLOYEE_REGISTER, EMPLOYMENT_CONTRACT, WPS_PAYROLL_RUN, "
                + "TASATTUR_SELF_ASSESSMENT, BENEFICIAL_OWNERSHIP_DISCLOSURE, "
                + "CR_ANNUAL_CONFIRMATION, CAPITAL_CHANGE_NOTIFICATION, "
                + "CONSUMER_COMPLAINT_RESPONSE} for as long as nothing called the server.")
            .isEmpty();
    }

    /**
     * The other direction, and it is not symmetric hand-waving: a server kind with no frontend entry
     * is a kind no form can produce, and it would keep an SLA and a default assignee that no
     * catalogue tile documents. Either direction of drift means one codebase changed alone.
     */
    @Test
    void theServerAcceptsNoKindTheFrontendCannotProduce() {
        Assumptions.assumeTrue(Files.isRegularFile(FE_INTAKE), "frontend not checked out");
        Set<String> extra = new TreeSet<>(IntakeKindRouting.kinds());
        extra.removeAll(feCatalogue(source()).keySet());
        assertThat(extra)
            .as("Server intake kind(s) with no frontend catalogue entry — no form can produce them, "
                + "so their SLA and routing are unreachable configuration")
            .isEmpty();
    }

    @Test
    void everyRoutingFieldAgreesWithTheFrontendEntry() {
        Assumptions.assumeTrue(Files.isRegularFile(FE_INTAKE), "frontend not checked out");
        Map<String, FeEntry> fe = feCatalogue(source());
        Map<String, String> mismatches = new TreeMap<>();
        fe.forEach((kind, f) -> {
            IntakeKindRouting.Routing s = IntakeKindRouting.get(kind);
            if (s == null) {
                return;      // reported by everyFrontendIntakeKindIsAcceptedByTheServer
            }
            if (!f.codePrefix().equals(s.codePrefix())) {
                mismatches.put(kind + ".codePrefix", f.codePrefix() + " (fe) != " + s.codePrefix());
            }
            if (f.slaHours() != s.slaHours()) {
                mismatches.put(kind + ".slaHours", f.slaHours() + " (fe) != " + s.slaHours());
            }
            if (!f.defaultTarget().equals(s.defaultTarget())) {
                mismatches.put(kind + ".defaultTarget", f.defaultTarget() + " (fe) != " + s.defaultTarget());
            }
            if (!f.defaultAssignee().equals(s.defaultAssignee())) {
                mismatches.put(kind + ".defaultAssignee", f.defaultAssignee() + " (fe) != " + s.defaultAssignee());
            }
            if (f.allowsAnonymous() != s.allowsAnonymous()) {
                mismatches.put(kind + ".allowsAnonymous", f.allowsAnonymous() + " (fe) != " + s.allowsAnonymous());
            }
        });
        assertThat(mismatches)
            .as("The two intake catalogues disagree. The frontend is the authority for the SLA, the "
                + "target register and the anonymity flag because it is what the submitter is shown; "
                + "bring IntakeKindRouting to it. An allowsAnonymous mismatch is the dangerous one — "
                + "a form offering anonymity the server refuses, or the reverse.")
            .isEmpty();
    }

    /**
     * The frontend's own internal consistency, checked here because the server depends on it: a kind
     * in the {@code IntakeKind} union but absent from {@code INTAKE_CATALOG} is a value the frontend
     * type-checks and then cannot route, and this test's whole comparison is against the catalogue.
     */
    @Test
    void theFrontendUnionAndCatalogueDescribeTheSameKinds() {
        Assumptions.assumeTrue(Files.isRegularFile(FE_INTAKE), "frontend not checked out");
        String src = source();
        Set<String> union = new TreeSet<>(feUnionKinds(src));
        Set<String> cat = new TreeSet<>(feCatalogue(src).keySet());
        assertThat(union).as("IntakeKind union parse resolved nothing").hasSizeGreaterThanOrEqualTo(KIND_FLOOR);
        assertThat(union)
            .as("The IntakeKind union and INTAKE_CATALOG disagree — a kind that type-checks but has "
                + "no routing entry, or an entry no code can name")
            .isEqualTo(cat);
    }

    /**
     * A known, bounded defect rather than a parity failure: the two-letter prefix is documented as
     * "per intake kind", and {@code CHANGE_REQUEST} and {@code CR_ANNUAL_CONFIRMATION} both use
     * {@code CR}. Both codebases agree, so parity is intact — the collision is in the design, not
     * the mirroring. It is recorded with a ceiling of one so a second one fails the build, and left
     * unfixed here because changing a documented reference-code prefix is a user-visible decision
     * and not part of a parity slice.
     *
     * <p>Consequence, stated so the ceiling is not mistaken for "harmless": a submitter tracking
     * {@code CR-XXXX-YYYY} cannot tell a change request from a commercial-registry confirmation.
     * Uniqueness of {@code reference_code} is unaffected — that is enforced by the column.
     */
    @Test
    void referenceCodePrefixesCollideExactlyOnce() {
        Map<String, Set<String>> byPrefix = new TreeMap<>();
        for (String kind : IntakeKindRouting.kinds()) {
            byPrefix.computeIfAbsent(IntakeKindRouting.get(kind).codePrefix(), k -> new TreeSet<>()).add(kind);
        }
        Map<String, Set<String>> collisions = new TreeMap<>();
        byPrefix.forEach((p, kinds) -> {
            if (kinds.size() > 1) {
                collisions.put(p, kinds);
            }
        });
        assertThat(collisions)
            .as("Reference-code prefix collisions. One is known and accepted (CR — CHANGE_REQUEST "
                + "and CR_ANNUAL_CONFIRMATION); a second means a new kind reused a prefix, which "
                + "makes its tracking code ambiguous to the submitter. Pick a free two-letter code.")
            .hasSize(1);
        assertThat(collisions.get("CR"))
            .containsExactly("CHANGE_REQUEST", "CR_ANNUAL_CONFIRMATION");
    }
}
