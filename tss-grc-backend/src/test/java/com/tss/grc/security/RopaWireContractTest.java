package com.tss.grc.security;

import com.tss.grc.module.privacy.entity.ProcessingActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-210 — a client type may not name a field the endpoint does not bind.
 *
 * <p><b>The defect.</b> {@code POST /api/v1/privacy/ropa} takes
 * {@code @RequestBody ProcessingActivity} — the entity — and Spring Boot disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, so any key the entity does not declare is
 * discarded in silence. {@code PrivacyRopaRequest} declared
 * {@code processNameEn} / {@code processNameAr} / {@code dataCategories} /
 * {@code retentionPeriod}, none of which exist on the entity. Probed live with
 * the exact payload the wizard sent: <b>HTTP 400, "A required field was not
 * supplied"</b> — because {@code activity_name_en} is NOT NULL and arrived null —
 * and the message names no field, so the operator cannot tell what to fix. The
 * same probe using the entity's names returns <b>201</b>.
 *
 * <p>So the PDPL Article 31 register's creation wizard had never once created a
 * record, which is why all 11 rows in dev and prod are seeded. The read side had
 * the mirror defect: the client read {@code retentionPeriod} where the wire
 * carries {@code retentionPeriodMonths} (non-null on 11 of 11 rows), so the
 * register's Retention column, the detail rail and — the sharp one — the
 * <i>"Completeness check (SDAIA examiner expectations)"</i> panel reported the
 * retention period MISSING on every activity in a register where every activity
 * has one.
 *
 * <p><b>Why a named population rather than a sweep.</b> A general FE-type to
 * BE-type diff was attempted first and rejected: it resolved 73 of 163
 * {@code *Dto} interfaces by name, and its "orphans" were dominated by
 * {@code BaseEntity} fields a per-file scan cannot see. F2-206's rule —
 * "unresolved" reads exactly like "missing" — so that instrument would
 * manufacture findings. This gate names the two types whose endpoint binding was
 * verified against the live wire, and each addition must be verified the same
 * way.
 *
 * <p>Hermetic: reads a source file and a compiled entity. No database.
 */
class RopaWireContractTest {

    private static final Path API_TS = Path.of("..", "tss-grc-ui-v2", "src", "lib", "api.ts");

    /**
     * Client-side names that are legitimately absent from the entity, with the
     * reason each is allowed. Empty today, and that is the assertion: every field
     * of both ROPA types resolves to a property the endpoint actually binds.
     */
    private static final Set<String> ALLOWED_CLIENT_ONLY = new LinkedHashSet<>();

    private static String api() throws IOException {
        assertThat(Files.exists(API_TS))
            .as("lib/api.ts must exist at %s", API_TS.toAbsolutePath().normalize())
            .isTrue();
        // ⚠ ONE alternation, line comments FIRST — the ordering this repo has got
        // wrong twice (F2-68(c), F2-198). Load-bearing here because both ROPA
        // types now carry headers that NAME the retired keys.
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

    private static Set<String> entityProperties() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> c = ProcessingActivity.class; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.isSynthetic()) names.add(f.getName());
            }
        }
        return names;
    }

    @Test
    @DisplayName("every PrivacyRopaRequest field is a property POST /ropa can bind")
    void everyRequestFieldIsBindable() throws IOException {
        List<String> declared = fieldsOf(api(), "PrivacyRopaRequest");
        assertThat(declared)
            .as("no fields parsed — an empty parse makes this gate vacuous")
            .hasSizeGreaterThanOrEqualTo(5);

        Set<String> bindable = entityProperties();
        List<String> unbindable = declared.stream()
            .filter(f -> !bindable.contains(f))
            .filter(f -> !ALLOWED_CLIENT_ONLY.contains(f))
            .toList();

        assertThat(unbindable)
            .as("""
                PrivacyRopaRequest names a field POST /api/v1/privacy/ropa cannot bind. That \
                endpoint takes the ProcessingActivity ENTITY and Spring Boot ignores unknown \
                properties silently, so such a field is not a type error, a 4xx, or a log line — \
                it is simply discarded, and the record is written without it. That is exactly how \
                the PDPL Article 31 creation wizard came to fail on every submission with a 400 \
                that named no field. Rename it to the entity's property, or add it to \
                ALLOWED_CLIENT_ONLY with a reason.""")
            .isEmpty();
    }

    @Test
    @DisplayName("every PrivacyRopaDto field is a property GET /ropa actually returns")
    void everyReadFieldIsOnTheWire() throws IOException {
        List<String> declared = fieldsOf(api(), "PrivacyRopaDto");
        Set<String> served = entityProperties();
        List<String> absent = declared.stream()
            .filter(f -> !served.contains(f))
            .filter(f -> !ALLOWED_CLIENT_ONLY.contains(f))
            .toList();

        assertThat(absent)
            .as("""
                PrivacyRopaDto declares a field GET /api/v1/privacy/ropa never sends — it returns \
                the ProcessingActivity entity. A column bound to such a field renders an em-dash \
                on every row forever, and a completeness check over it reports the field missing \
                on a register where it is present. Note the direction: this errs PESSIMISTICALLY, \
                which produces no false assurance but is far less likely to be reported, because \
                "no data yet" is a plausible reading of an empty column.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the retention period is bound as the integer column, not as prose")
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
        assertThat(entityProperties())
            .as("the entity must still be the authority for this field name")
            .contains("retentionPeriodMonths");
    }

    /**
     * ⚠ A corpus assertion passes both when the code is correct and when the
     * regex has rotted. Feed the parser the exact shape that existed before the
     * fix and require it to be rejected.
     */
    @Test
    @DisplayName("SELF-TEST: the parser sees the pre-fix field names and the entity rejects them")
    void selfTest() {
        String preFix = """
            export interface PrivacyRopaRequest {
              processNameEn: string; processNameAr?: string;
              legalBasis?: string; dataCategories?: string;
              retentionPeriod?: string; status?: string;
            }
            """;
        List<String> parsed = fieldsOf(preFix, "PrivacyRopaRequest");
        assertThat(parsed)
            .as("the parser must actually find the fields, or every assertion above is vacuous")
            .contains("processNameEn", "processNameAr", "dataCategories", "retentionPeriod", "status");

        Set<String> bindable = entityProperties();
        assertThat(parsed.stream().filter(f -> !bindable.contains(f)).toList())
            .as("the pre-fix request named four keys the entity cannot bind — if this list is "
                + "empty the entity has changed and this gate is no longer testing anything")
            .containsExactlyInAnyOrder("processNameEn", "processNameAr", "dataCategories", "retentionPeriod");

        // …and `status` was the one field that DID bind, which is why the failure
        // looked partial rather than total.
        assertThat(bindable).contains("status", "activityNameEn", "purposeEn");
    }
}
