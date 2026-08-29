package com.tss.grc.security;

import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.dto.IncidentResponse;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.support.GuardExpressions;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-281 — the cyber → PDPL classification must be on the incident's own wire,
 * and it must stay read-only there.
 *
 * <h2>What this gate is about</h2>
 *
 * <p>{@code cyber.incidents} has 39 columns and {@link Incident} mapped 30. The
 * nine it omitted were the cyber → PDPL bridge's half of the row, written by ONE
 * writer — {@code privacy.bridge.CyberPdplBridgeService}, in native SQL — and
 * read by nothing at all. Measured 2026-08-22 in dev and prod: 2 incidents are
 * classified as affecting personal data, holding 1,450 and 380 estimated data
 * subjects and the categories {@code EMAIL,NATIONAL_ID,PHONE_NUMBER} and
 * {@code EMAIL,SALARY}.
 *
 * <p>The consequence was not merely that the classification was invisible. This
 * service RENDERS the PDPL Article 20 breach notification the platform hands its
 * operator to file with the Saudi Data Office, and that body omitted the number
 * of affected data subjects and the categories of personal data affected — the
 * two facts Article 20 turns on — while the same row stored both. The HRSD body
 * went further and printed {@code EMPLOYEES AFFECTED: <count>} and
 * {@code DATA CATEGORIES AFFECTED: <e.g. salary, ID, contract>}: literal
 * placeholders asking a human to supply values the row already held. Probed live
 * 16/16 before the fix.
 *
 * <h2>Why the columns are read-only rather than simply mapped</h2>
 *
 * <p>The classification is a privacy-module decision. Mapping the columns
 * writable would put a way to forge a PDPL classification on an incident-module
 * route, which is F2-213's family; {@code insertable = false, updatable = false}
 * plus {@code @Setter(AccessLevel.NONE)} makes the hazard absent rather than
 * latent, and the second half of that makes a write a COMPILE error rather than
 * a silent no-op (F2-184's decoy shape).
 *
 * <p>It also matters for the INSERT: {@code affects_personal_data} is
 * {@code smallint NOT NULL DEFAULT 0}, so leaving it out of Hibernate's INSERT
 * is what lets the database default apply — F2-280's kind-1 category, and here
 * the default is the correct value rather than a wrong one, because a new
 * incident affects no personal data until somebody classifies it.
 */
class PdplClassificationWireContractTest {

    /** The nine bridge columns, and the entity field each is mapped by. */
    private static final Map<String, String> BRIDGE_COLUMNS = new LinkedHashMap<>();
    static {
        BRIDGE_COLUMNS.put("affects_personal_data",         "affectsPersonalData");
        BRIDGE_COLUMNS.put("data_subjects_affected_est",    "dataSubjectsAffectedEst");
        BRIDGE_COLUMNS.put("data_categories_affected",      "dataCategoriesAffected");
        BRIDGE_COLUMNS.put("personal_data_pii_assessment",  "personalDataPiiAssessment");
        BRIDGE_COLUMNS.put("linked_data_breach_id",         "linkedDataBreachId");
        BRIDGE_COLUMNS.put("linked_data_breach_code",       "linkedDataBreachCode");
        BRIDGE_COLUMNS.put("pii_classified_at",             "piiClassifiedAt");
        BRIDGE_COLUMNS.put("pii_classified_by",             "piiClassifiedBy");
    }

    private static final Path SERVICE = Path.of(
            "src", "main", "java", "com", "tss", "grc", "module", "incident",
            "service", "IncidentService.java");
    private static final Path API_TS = Path.of(
            "..", "tss-grc-ui-v2", "src", "lib", "api.ts");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /**
     * Comments stripped before any scan.
     *
     * <p>Measured, and said out loud rather than implied: on this tree the
     * stripping is <b>not</b> load-bearing — the raw and stripped counts are
     * identical (1 comparison in the entity, 0 in the service, 0 placeholder
     * hits), because this slice's javadoc happens not to quote the shapes it
     * retired. It stays because the convention in this codebase is to document a
     * retirement by quoting what was removed (F2-274), so the first author who
     * explains this fix by citing {@code EMPLOYEES AFFECTED: <count>} in a comment
     * makes it load-bearing — and comment-blind is the direction that reads the
     * epitaph as the corpse (F2-193).
     */
    private static String code(Path p) throws IOException {
        return GuardExpressions.stripComments(read(p));
    }

    // ─── the mapping ────────────────────────────────────────────────────────

    /**
     * By reflection, never by a source regex: an annotation's attributes cannot
     * be misread from the class file, and this codebase has three phantom counts
     * on record from reading Java with regexes.
     */
    @Test
    void everyBridgeColumnIsMappedReadOnly() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : BRIDGE_COLUMNS.entrySet()) {
            Field f;
            try {
                f = Incident.class.getDeclaredField(e.getValue());
            } catch (NoSuchFieldException nsf) {
                problems.add(e.getValue() + " — field is not declared at all, so column "
                        + e.getKey() + " is off the wire again");
                continue;
            }
            Column c = f.getAnnotation(Column.class);
            if (c == null) {
                problems.add(e.getValue() + " — no @Column");
                continue;
            }
            if (!e.getKey().equals(c.name())) {
                problems.add(e.getValue() + " — maps " + c.name() + ", expected " + e.getKey());
            }
            if (c.insertable()) {
                problems.add(e.getValue() + " — insertable; the bridge owns the writes");
            }
            if (c.updatable()) {
                problems.add(e.getValue() + " — updatable; an incident route could forge a "
                        + "PDPL classification");
            }
        }
        assertThat(problems)
                .as("F2-281: the eight cyber -> PDPL bridge columns are mapped read-only")
                .isEmpty();
    }

    /**
     * A Lombok setter over a non-updatable column compiles, runs and silently
     * does nothing. Suppressing it turns the write into a compile error, which is
     * stronger than any gate — so the gate's job is only to keep it suppressed.
     */
    @Test
    void noBridgeColumnHasASetter() {
        List<String> found = new ArrayList<>();
        for (String field : BRIDGE_COLUMNS.values()) {
            String setter = "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            for (var m : Incident.class.getMethods()) {
                if (m.getName().equals(setter)) found.add(setter);
            }
        }
        assertThat(found)
                .as("F2-281: a setter that silently does nothing is the decoy F2-184 named")
                .isEmpty();
    }

    /** Anti-vacuity: the getters must exist, or every leg above passes over nothing. */
    @Test
    void everyBridgeColumnIsReadable() {
        List<String> missing = new ArrayList<>();
        for (String field : BRIDGE_COLUMNS.values()) {
            String suffix = Character.toUpperCase(field.charAt(0)) + field.substring(1);
            boolean ok = false;
            for (var m : Incident.class.getMethods()) {
                if (m.getName().equals("get" + suffix) || m.getName().equals("is" + suffix)) ok = true;
            }
            if (!ok) missing.add(field);
        }
        assertThat(missing).as("F2-281: reading is the whole point of the mapping").isEmpty();
        assertThat(BRIDGE_COLUMNS).hasSize(8);
    }

    // ─── the wire, in both directions ───────────────────────────────────────

    /**
     * Enforced both ways. The response must declare the classification — that is
     * the finding — and the request must declare none of it, because a field the
     * bound type does not declare can never arrive (F2-233), which is a stronger
     * closure than any guard in the service.
     */
    @Test
    void theResponseDeclaresTheClassificationAndTheRequestDeclaresNone() {
        Set<String> response = componentNames(IncidentResponse.class);
        Set<String> request  = componentNames(IncidentRequest.class);

        List<String> missing = new ArrayList<>();
        List<String> leaked  = new ArrayList<>();
        for (String field : BRIDGE_COLUMNS.values()) {
            if (!response.contains(field)) missing.add(field);
            if (request.contains(field))   leaked.add(field);
        }
        assertThat(missing)
                .as("F2-281: IncidentResponse carries the PDPL classification")
                .isEmpty();
        assertThat(leaked)
                .as("F2-281: IncidentRequest must not declare a bridge-owned field — "
                    + "the bridge is the only writer")
                .isEmpty();
    }

    private static Set<String> componentNames(Class<?> record) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (RecordComponent rc : record.getRecordComponents()) out.add(rc.getName());
        return out;
    }

    // ─── one predicate, not two ─────────────────────────────────────────────

    /**
     * F2-280 measured what a second copy of a governance predicate costs: two
     * readers of one fact that disagreed on 12 roles in dev and 7 in prod. Four
     * surfaces ask "is this incident classified as affecting personal data?" —
     * the Article 20 body, the HRSD body, the template applicability and the
     * response DTO — so the comparison lives in exactly one place.
     *
     * <p>Scoped to the comparison SHAPE rather than to the field name, because
     * {@code getAffectsPersonalData()} appearing in a getter is a mapping and not
     * a second opinion (F2-280's own first-draft error, one gate over).
     */
    @Test
    void theClassificationIsComparedInExactlyOnePlace() throws IOException {
        String entity = code(Path.of("src", "main", "java", "com", "tss", "grc",
                "module", "incident", "entity", "Incident.java"));
        // Scoped to a comparison against a NUMERIC LITERAL. `!= null` is a null
        // check, not a second opinion about the classification -- a pattern loose
        // enough to match it fails on correct code, which is the one failure a gate
        // must never have (F2-214), and the first draft of this leg did exactly that.
        Pattern comparison = Pattern.compile(
                "(?:get)?[Aa]ffectsPersonalData(?:\\(\\))?\\s*(?:!=|==|>|<)\\s*\\d");

        int inEntity = count(comparison, entity);
        int inService = count(comparison, code(SERVICE));

        assertThat(inEntity)
                .as("F2-281: Incident#isPiiConfirmed is the one comparison")
                .isEqualTo(1);
        assertThat(inService)
                .as("F2-281: IncidentService must ask isPiiConfirmed(), never compare the "
                    + "column itself — that is how two readers of one fact drift apart")
                .isZero();
        assertThat(code(SERVICE))
                .as("F2-281: and it must actually ask")
                .containsPattern("isPiiConfirmed\\s*\\(");
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // ─── the emitted statutory bodies ───────────────────────────────────────

    /**
     * Structural, beside the behavioural legs in {@code IncidentServiceTest}.
     * The point is the ABSENCE of the two placeholders: a body that asks a human
     * for a count the row holds is the defect, and it can come back as a literal
     * without any behaviour changing anywhere else.
     */
    @Test
    void noEmittedBodyAsksAHumanForAValueTheRowHolds() throws IOException {
        String src = code(SERVICE);
        assertThat(src)
                .as("F2-281: the HRSD body held this placeholder while "
                    + "data_categories_affected was populated on the same row")
                .doesNotContain("<e.g. salary, ID, contract>");
        assertThat(src)
                .as("F2-281: and this one while data_subjects_affected_est was populated")
                .doesNotContain("EMPLOYEES AFFECTED: <count>");
        assertThat(src)
                .as("F2-281: an unassessed count must render as absent, never as 0")
                .containsPattern("notAssessed\\s*\\(");
    }

    /**
     * The withdrawal, asserted in both directions.
     *
     * <p>The classification is CARRIED by every template and deliberately does
     * not gate one. Suppressing the PDPL Article 20 notification for an
     * unclassified incident would turn "nobody has assessed this yet" into a
     * clean bill on a statutory clock — and a one-directional gate would let
     * somebody add that suppression as an apparent improvement.
     */
    @Test
    void theClassificationIsCarriedAndDoesNotSuppressATemplate() throws IOException {
        String src = code(SERVICE);
        assertThat(src)
                .as("F2-281: every template carries the applicability answer")
                .containsPattern("m\\.put\\(\"piiClassification\"");
        // F2-390 corrected the operator to inclusive `<= 0` (a statutory deadline is
        // breached AT the deadline, and the elapsed count is floor-truncated to whole
        // hours). This leg's point is unchanged: overdue references only the
        // deadline-derived quantity and the notified stamp — never piiClassification —
        // so the classification is carried without gating the statutory clock.
        assertThat(src)
                .as("F2-281: overdue stays a function of the deadline and the stamp only "
                    + "(operator corrected to <= by F2-390)")
                .contains("m.put(\"overdue\", (deadlineHours - hoursSinceDiscovery) <= 0 "
                        + "&& notifiedAt == null);");

        // The five templates are added unconditionally: no `if` may stand between
        // the template list and any out.add(template(...)) call.
        String templates = src.substring(src.indexOf("java.util.List<java.util.Map<String, Object>> out"));
        templates = templates.substring(0, templates.indexOf("return out;"));
        assertThat(templates)
                .as("F2-281: a template may not be conditional on the classification")
                .doesNotContain("if (");
        assertThat(count(Pattern.compile("out\\.add\\(template\\("), templates))
                .as("F2-281: all five Saudi regulator templates are still offered")
                .isEqualTo(5);
    }

    // ─── the frontend half (the F2-193 pair) ────────────────────────────────

    /**
     * The backend half cannot tell a real DTO property from a plausible string,
     * and the frontend half cannot resolve a Java field — so both are needed.
     *
     * <p>Comment-stripped, because F2-280's equivalent leg was
     * {@code contains("privileged")} and its own JSDoc satisfied it: deleting the
     * field left the gate green.
     */
    @Test
    void theFrontendWireDeclaresTheClassificationOnceAndOnTheResponseOnly() throws IOException {
        String ts = code(API_TS);

        String dto = slice(ts, "export interface IncidentDto {", "}");
        String req = slice(ts, "export interface IncidentRequest {", "}");
        String tmpl = slice(ts, "export interface RegulatoryTemplate {", "}");

        assertThat(dto)
                .as("F2-281: IncidentDto carries the classification")
                .containsPattern("affectsPersonalData\\s*\\??\\s*:\\s*boolean")
                .containsPattern("dataSubjectsAffectedEst\\s*\\??\\s*:\\s*number")
                .containsPattern("dataCategoriesAffected\\s*\\??\\s*:\\s*string")
                .containsPattern("linkedDataBreachCode\\s*\\??\\s*:\\s*string");
        assertThat(req)
                .as("F2-281: the request type declares none of it — the bridge is the "
                    + "only writer, and an undeclared field can never arrive")
                .doesNotContain("affectsPersonalData")
                .doesNotContain("dataCategoriesAffected")
                .doesNotContain("piiClassified");
        assertThat(tmpl)
                .as("F2-281: the template's applicability answer is on the wire")
                .containsPattern("piiClassification\\s*:");
    }

    private static String slice(String s, String open, String close) {
        int i = s.indexOf(open);
        assertThat(i).as("F2-281: anchor %s must exist in api.ts", open).isNotNegative();
        int j = s.indexOf("\n" + close, i);
        assertThat(j).as("F2-281: %s must be closed", open).isGreaterThan(i);
        return s.substring(i, j);
    }

    /**
     * The stripper is load-bearing for two legs above, and a stripper that
     * silently fails reports a SMALLER population, which reads exactly like a
     * clean tree. The negative control is the TWO-PASS form, never a reordered
     * alternation: within one alternation the branch order is a no-op (F2-211).
     */
    @Test
    void theStripperIsSound() {
        // The HOSTILE shape is the primary fixture, and that ordering is the
        // finding: my first draft led with a well-formed `/* ... */`, which the
        // TWO-PASS form also strips correctly, so the mutation replacing the
        // helper with two passes SURVIVED -- not a hole in the gate, because the
        // mutated code was not wrong on that fixture (F2-206), but a fixture that
        // could not tell the two apart. The corruption needs an UNTERMINATED `/*`
        // inside a `//` comment plus a closer downstream (F2-274).
        String src = "// retired: affectsPersonalData != 0 -- see /* the old note\n"
                   + "boolean b = getAffectsPersonalData() != null;\n"
                   + "/* affectsPersonalData == 1 */\n";

        String stripped = GuardExpressions.stripComments(src);
        assertThat(stripped)
                .as("the declaration between the two delimiters must survive")
                .contains("getAffectsPersonalData()");
        assertThat(stripped).doesNotContain("retired");
        assertThat(stripped).doesNotContain("!= 0");
        assertThat(stripped).doesNotContain("== 1");

        // The negative control, on the same fixture: two sequential passes run the
        // block pattern from the `/*` INSIDE the line comment to the next closing
        // delimiter and take the declaration with it -- a SMALLER population,
        // which reads exactly like a clean tree (F2-198).
        String twoPass = src.replaceAll("/\\*[\\s\\S]*?\\*/", "")
                            .replaceAll("(?<!:)//[^\\n]*", "");
        assertThat(twoPass)
                .as("the two-pass form is what corrupts the source")
                .doesNotContain("getAffectsPersonalData()");

        // And a reordered alternation is an EQUIVALENT mutant, never a control:
        // `//` and `/*` can never match at the same position, so leftmost-match
        // consumes the line comment first whichever branch is written first
        // (F2-211). Both orderings must give the same answer.
        String reordered = src.replaceAll("/\\*[\\s\\S]*?\\*/|(?<!:)//[^\\n]*", "");
        assertThat(reordered)
                .as("branch order within one alternation is a no-op")
                .isEqualTo(src.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", ""));
    }
}
