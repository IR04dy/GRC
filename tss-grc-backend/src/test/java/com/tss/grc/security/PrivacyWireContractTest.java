package com.tss.grc.security;

import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.entity.Dpia;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-210 / F2-211 — a client type may not name a field its endpoint does not bind.
 *
 * <p><b>Why the ENTITY is the authority and the wire is not.</b> Every privacy
 * endpoint here returns the JPA entity, and the app sets
 * {@code default-property-inclusion: non_null}. So a field that never appears in a
 * live response is ambiguous between two states that demand OPPOSITE fixes:
 *
 * <ul>
 *   <li><b>No column.</b> {@code PrivacyBreachDto} declared {@code severity},
 *       {@code occurredAt}, {@code reportedAt} and {@code affectedRecords};
 *       {@code privacy.data_breaches} has 25 columns and none of them. The value
 *       could never arrive — delete the declaration and whatever reads it.</li>
 *   <li><b>Column exists, every row null.</b> {@code sdaiaSubmissionRef} and
 *       {@code sdaiaSubmittedAt} are real columns, null on all 17 live rows because
 *       no SDAIA submission has been recorded yet; all six {@code DpiaDto} fields
 *       that look absent ({@code processingActivityId}, {@code dpoId},
 *       {@code assessorId}, {@code approvedAt}, {@code rejectedAt},
 *       {@code rejectionReason}) are the same. These are correct declarations and a
 *       DATA fact — leave them alone.</li>
 * </ul>
 *
 * <p>Measuring from the wire called ten fields dead across these four types. The
 * entity says four. Reflection over the entity sees a declared field whether or not
 * any row has populated it, which is exactly the distinction the wire erases.
 *
 * <p><b>What the defect cost.</b> The breach register's Severity badge, Records
 * count and Occurred date rendered an em-dash on all 17 live rows; its severity
 * filter compared against {@code undefined} so choosing any band emptied the whole
 * register — a false all-clear on a PDPL register; and the column titled with the
 * PDPL Article 20 72-hour clock returned early on {@code !r.occurredAt}, so the
 * statutory countdown had never once rendered against real data, for any tenant.
 *
 * <p><b>Why a named population and not a sweep.</b> A general FE-type to BE-type
 * diff was built first and rejected: it resolved 73 of 163 {@code *Dto} interfaces
 * by name and its "orphans" were dominated by {@code BaseEntity} fields a per-file
 * scan cannot see. F2-206's rule — "unresolved" reads exactly like "missing" — so
 * that instrument would manufacture findings at scale. Each entry below names the
 * endpoint whose binding was verified against the live wire, because a guessed
 * pairing manufactures phantom findings: F2-210 measured five candidate renames
 * this way and four were false, entirely because the pairing was assumed.
 *
 * <p>Hermetic: reads one source file and four compiled entities. No database.
 */
class PrivacyWireContractTest {

    private static final Path API_TS = Path.of("..", "tss-grc-ui-v2", "src", "lib", "api.ts");

    /**
     * One entry per privacy register: the frontend interface, the entity the
     * endpoint returns, and the endpoint the API client actually calls — verified,
     * not inferred from the schema name. F2-202 guessed three privacy paths from
     * their schema and got all three wrong, each differently.
     */
    private record Register(String iface, Class<?> entity, String endpoint) { }

    private static final List<Register> REGISTERS = List.of(
        new Register("PrivacyDsrDto",    DataSubjectRequest.class, "GET /api/v1/privacy/dsrs"),
        new Register("PrivacyBreachDto", DataBreach.class,         "GET /api/v1/privacy/breaches"),
        new Register("PrivacyRopaDto",   ProcessingActivity.class, "GET /api/v1/privacy/ropa"),
        new Register("DpiaDto",          Dpia.class,               "GET /api/v1/privacy/dpias")
    );

    /**
     * Client-side names legitimately absent from their entity, keyed
     * {@code Iface#field}, with the reason each is allowed.
     *
     * <p>Empty, and that is the assertion. A field belongs here only if something
     * other than the entity supplies it — never because a live probe happened to
     * show no value, which is the ambiguity this whole gate exists to resolve.
     */
    private static final Map<String, String> ALLOWED_CLIENT_ONLY = new LinkedHashMap<>();

    private static String api() throws IOException {
        assertThat(Files.exists(API_TS))
            .as("lib/api.ts must exist at %s", API_TS.toAbsolutePath().normalize())
            .isTrue();
        // ⚠ ONE alternation, line comments FIRST — the ordering this repo has got
        // wrong twice (F2-68(c), F2-198). Load-bearing here: these interfaces carry
        // headers that NAME the retired keys, and reversed, the block pattern would
        // run from a `//` comment containing `/*` to the next `*/` and delete real
        // declarations. A corrupted source yields a SMALLER population, which reads
        // exactly like "those types are fine".
        return Files.readString(API_TS, StandardCharsets.UTF_8)
                    .replaceAll("(?<![:/])//[^\\n]*|/\\*(?s).*?\\*/", "");
    }

    private static List<String> fieldsOf(String src, String iface) {
        Matcher m = Pattern.compile("export interface " + Pattern.quote(iface) + "\\s*\\{([^}]*)}",
                                    Pattern.DOTALL).matcher(src);
        assertThat(m.find()).as("interface %s must be declared in lib/api.ts", iface).isTrue();
        List<String> out = new ArrayList<>();
        Matcher f = Pattern.compile("(?:^|[;\\n])\\s*(\\w+)\\s*\\??\\s*:").matcher(m.group(1));
        while (f.find()) out.add(f.group(1));
        return out;
    }

    /** Declared fields of the entity and every mapped superclass. */
    private static Set<String> propertiesOf(Class<?> entity) {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.isSynthetic()) names.add(f.getName());
            }
        }
        return names;
    }

    @Test
    @DisplayName("no privacy client type names a field its endpoint cannot supply")
    void everyDeclaredFieldResolvesToItsEntity() throws IOException {
        String src = api();
        List<String> offences = new ArrayList<>();
        int checked = 0;

        for (Register r : REGISTERS) {
            List<String> declared = fieldsOf(src, r.iface());
            assertThat(declared)
                .as("no fields parsed for %s — an empty parse makes this gate vacuous", r.iface())
                .isNotEmpty();
            Set<String> bindable = propertiesOf(r.entity());
            for (String f : declared) {
                checked++;
                if (bindable.contains(f)) continue;
                if (ALLOWED_CLIENT_ONLY.containsKey(r.iface() + "#" + f)) continue;
                offences.add("%s#%s (%s returns %s, which has no such property)"
                             .formatted(r.iface(), f, r.endpoint(), r.entity().getSimpleName()));
            }
        }

        // Anti-vacuity: a gate keyed to a literal shape reports a SMALLER population
        // the moment an entry changes, and silence then reads as success (F2-209).
        assertThat(checked)
            .as("far fewer fields checked than these four registers declare — the parser has rotted")
            .isGreaterThanOrEqualTo(40);

        assertThat(offences)
            .as("""
                A privacy client type names a field its endpoint cannot supply. These \
                endpoints return the JPA entity, so such a field is not a type error, a \
                4xx or a log line — it is simply never sent, and every surface reading it \
                renders an em-dash forever. That is how the breach register's PDPL \
                Article 20 72-hour countdown came to have never once displayed against \
                real data. Before deleting a field, check sys.columns: a column that \
                exists and is null on every row looks identical on the wire and is NOT \
                this defect. If something other than the entity supplies it, add it to \
                ALLOWED_CLIENT_ONLY with a reason.""")
            .isEmpty();
    }

    /**
     * The reverse leg. F2-199 shipped two exemptions whose subjects were not in the
     * population at all, and the forward assertion passed because the population was
     * already empty — a reason written for something that is not in the population
     * reads exactly like a reason for something that is.
     */
    @Test
    @DisplayName("REVERSE: every exemption names a field that is really declared and really absent")
    void everyExemptionIsStillEarned() throws IOException {
        String src = api();
        for (Map.Entry<String, String> e : ALLOWED_CLIENT_ONLY.entrySet()) {
            String[] parts = e.getKey().split("#", 2);
            assertThat(parts).as("exemption key must be Iface#field: %s", e.getKey()).hasSize(2);
            Register r = REGISTERS.stream().filter(x -> x.iface().equals(parts[0])).findFirst()
                .orElseThrow(() -> new AssertionError("exemption names an unknown register: " + parts[0]));
            assertThat(fieldsOf(src, r.iface()))
                .as("exemption %s names a field the interface no longer declares — delete the entry", e.getKey())
                .contains(parts[1]);
            assertThat(propertiesOf(r.entity()))
                .as("exemption %s is no longer earned: %s DOES have that property now", e.getKey(),
                    r.entity().getSimpleName())
                .doesNotContain(parts[1]);
            assertThat(e.getValue().length())
                .as("exemption %s needs a real reason, not a label", e.getKey())
                .isGreaterThanOrEqualTo(40);
        }
    }

    @Test
    @DisplayName("the breach register reads the columns the schema actually keeps")
    void breachFieldsAreTheOnesTheSchemaKeeps() throws IOException {
        List<String> declared = fieldsOf(api(), "PrivacyBreachDto");

        // Retired: no column in privacy.data_breaches (25 columns, dev == prod).
        assertThat(declared)
            .as("`severity` has no column — the band is DERIVED from the affected-subject "
                + "count and disclosed as provisional; re-declaring it would let a surface "
                + "read a stored severity this platform does not have")
            .doesNotContain("severity");
        assertThat(declared)
            .as("`occurredAt` / `reportedAt` have no column. `discovered_at` is when the "
                + "controller became AWARE, which is a different fact and the one every PDPL "
                + "clock runs from — the register's 72-hour countdown returned early on the "
                + "absent `occurredAt` and so never rendered at all")
            .doesNotContain("occurredAt", "reportedAt");
        assertThat(declared)
            .as("`affectedRecords` has no column; the count the server keeps is "
                + "`data_subjects_affected_count`, non-null on all 17 live rows")
            .doesNotContain("affectedRecords");

        // Kept: real columns, unpopulated today. Deleting these would be the mirror
        // error — removing a correct declaration because no row has filled it in yet.
        assertThat(declared)
            .as("these have real columns (null on all 17 live rows because no SDAIA "
                + "submission has been recorded); they are a data fact, not a contract fact")
            .contains("sdaiaSubmissionRef", "sdaiaSubmittedAt");
        assertThat(declared)
            .as("the clock needs the awareness stamp and whether notification is required at all")
            .contains("discoveredAt", "dataSubjectsAffectedCount", "pdpcNotificationRequired");
    }

    @Test
    @DisplayName("the ROPA retention period is bound as the integer column, not as prose")
    void retentionIsTheIntegerColumn() throws IOException {
        String src = api();
        for (String iface : List.of("PrivacyRopaDto", "PrivacyRopaRequest")) {
            List<String> fields = fieldsOf(src, iface);
            assertThat(fields)
                .as("%s must read the retention period from the column the entity declares "
                    + "(retention_period_months / retentionPeriodMonths)", iface)
                .contains("retentionPeriodMonths");
            assertThat(fields)
                .as("%s still declares the retired `retentionPeriod` — the server has never sent "
                    + "a key by that name, and reading it is what made the SDAIA completeness "
                    + "check report a complete register as incomplete", iface)
                .doesNotContain("retentionPeriod");
        }
        assertThat(propertiesOf(ProcessingActivity.class))
            .as("the entity must still be the authority for this field name")
            .contains("retentionPeriodMonths");
    }

    @Test
    @DisplayName("every PrivacyRopaRequest field is a property POST /ropa can bind")
    void everyRequestFieldIsBindable() throws IOException {
        List<String> declared = fieldsOf(api(), "PrivacyRopaRequest");
        assertThat(declared)
            .as("no fields parsed — an empty parse makes this gate vacuous")
            .hasSizeGreaterThanOrEqualTo(5);

        Set<String> bindable = propertiesOf(ProcessingActivity.class);
        assertThat(declared.stream().filter(f -> !bindable.contains(f)).toList())
            .as("""
                PrivacyRopaRequest names a field POST /api/v1/privacy/ropa cannot bind. That \
                endpoint takes the ProcessingActivity ENTITY and Spring Boot ignores unknown \
                properties silently, so such a field is not a type error, a 4xx, or a log line \
                — it is simply discarded, and the record is written without it. That is exactly \
                how the PDPL Article 31 creation wizard came to fail on every submission with a \
                400 that named no field.""")
            .isEmpty();
    }

    /**
     * ⚠ A corpus assertion passes both when the code is correct and when the regex
     * has rotted. Feed the parser the exact shapes that existed before each fix and
     * require the entities to reject them.
     */
    @Test
    @DisplayName("SELF-TEST: the parser sees the pre-fix field names and the entities reject them")
    void selfTest() {
        String preFix = """
            export interface PrivacyRopaRequest {
              processNameEn: string; processNameAr?: string;
              legalBasis?: string; dataCategories?: string;
              retentionPeriod?: string; status?: string;
            }
            export interface PrivacyBreachDto {
              id: number; titleEn: string; breachType?: string; severity?: string;
              occurredAt?: string; reportedAt?: string; affectedRecords?: number;
              dataSubjectsAffectedCount?: number; discoveredAt?: string;
            }
            """;

        List<String> ropa = fieldsOf(preFix, "PrivacyRopaRequest");
        assertThat(ropa)
            .as("the parser must actually find the fields, or every assertion above is vacuous")
            .contains("processNameEn", "processNameAr", "dataCategories", "retentionPeriod", "status");
        Set<String> activity = propertiesOf(ProcessingActivity.class);
        assertThat(ropa.stream().filter(f -> !activity.contains(f)).toList())
            .as("the pre-fix request named four keys ProcessingActivity cannot bind — if this "
                + "list is empty the entity has changed and the gate is no longer testing anything")
            .containsExactlyInAnyOrder("processNameEn", "processNameAr", "dataCategories", "retentionPeriod");

        List<String> breach = fieldsOf(preFix, "PrivacyBreachDto");
        Set<String> dataBreach = propertiesOf(DataBreach.class);
        assertThat(breach.stream().filter(f -> !dataBreach.contains(f)).toList())
            .as("the pre-fix breach DTO named exactly these four keys DataBreach has no property "
                + "for — the four the register's Severity, Records, Occurred and 72-hour clock "
                + "columns each read")
            .containsExactlyInAnyOrder("severity", "occurredAt", "reportedAt", "affectedRecords");

        // …and the fields that DID resolve are why the failure looked partial rather
        // than total: the rows rendered, only these columns were empty.
        assertThat(dataBreach).contains("status", "titleEn", "breachType",
                                        "dataSubjectsAffectedCount", "discoveredAt");
    }
}
