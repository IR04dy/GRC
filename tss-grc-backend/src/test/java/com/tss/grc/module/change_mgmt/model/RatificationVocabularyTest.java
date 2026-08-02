package com.tss.grc.module.change_mgmt.model;

import com.tss.grc.module.change_mgmt.dto.RatificationDtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A17-D3 ratchet — the Java vocabulary, the {@code @Pattern} regex, the DTO bounds
 * and the V345 CHECK constraints must all say the same thing.
 *
 * <p><b>Why this test exists rather than an enum.</b> The ratification vocabulary is
 * declared in three places that cannot reference each other: a Java constants class
 * (because {@code @Pattern} needs a compile-time constant), a regex, and a SQL Server
 * CHECK constraint in a migration file. F2-91 is what happens when those drift — the
 * Java side accepted a value the database refused, so the API returned 500 where it
 * should have returned 400. This asserts the agreement instead of hoping for it.
 *
 * <p>It reads the <b>migration file</b>, not a live database, on purpose: this suite is
 * hermetic ({@code mvn -o clean package} needs no database and no secrets), and the
 * migration file is what a customer's Flyway actually applies. A live-DB assertion would
 * only prove the dev box was hand-patched correctly.
 */
class RatificationVocabularyTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/sqlserver/V345__change_ratification_sla.sql");

    private static String migration() throws IOException {
        assertThat(MIGRATION).as("V345 must exist — it is the source of truth a customer applies").exists();
        return Files.readString(MIGRATION, StandardCharsets.UTF_8);
    }

    @Test
    void theRegexAndTheValueListAgree() {
        List<String> fromRegex = List.of(RatificationStates.REGEX.split("\\|"));
        assertThat(fromRegex)
            .as("RatificationStates.REGEX is hand-derived from VALUES because @Pattern needs a "
              + "compile-time constant; if they drift, the DTO and the service disagree")
            .containsExactlyElementsOf(RatificationStates.VALUES);
    }

    @Test
    void everyStateIsWellFormedAndDistinct() {
        assertThat(RatificationStates.VALUES).doesNotHaveDuplicates();
        for (String v : RatificationStates.VALUES) {
            assertThat(v).matches("[A-Z][A-Z_]*[A-Z]")
                .as("state '%s' must be SCREAMING_SNAKE — it is stored in an NVARCHAR(24) column "
                  + "and compared literally", v);
            assertThat(v.length()).as("state '%s' must fit ratification_state NVARCHAR(24)", v)
                .isLessThanOrEqualTo(24);
        }
    }

    @Test
    void theJavaVocabularyEqualsTheMigrationCheckConstraint() throws IOException {
        String sql = migration();
        int at = sql.indexOf("ck_change_ratification_state");
        assertThat(at).as("V345 must declare ck_change_ratification_state").isGreaterThan(0);
        // The CHECK ... IN (...) list immediately following the constraint name.
        String tail = sql.substring(at, Math.min(sql.length(), at + 600));
        Matcher m = Pattern.compile("N'([A-Z_]+)'").matcher(tail);
        List<String> fromSql = new ArrayList<>();
        while (m.find()) fromSql.add(m.group(1));

        assertThat(fromSql)
            .as("V345's ck_change_ratification_state lists %s; RatificationStates.VALUES lists %s. "
              + "A value the database refuses but Java accepts surfaces as a 500 instead of a 400 (F2-91)",
                fromSql, RatificationStates.VALUES)
            .containsExactlyInAnyOrderElementsOf(RatificationStates.VALUES);
    }

    @Test
    void theSlaCeilingIsTheSameNumberInJavaTheDtoAndTheDatabase() throws IOException {
        String sql = migration();
        Matcher m = Pattern.compile(
            "ck_ratification_sla_bounds\\s+CHECK\\s*\\(\\s*sla_business_days\\s+BETWEEN\\s+(\\d+)\\s+AND\\s+(\\d+)\\s*\\)")
            .matcher(sql);
        assertThat(m.find())
            .as("V345 must declare ck_ratification_sla_bounds as a BETWEEN — it is the answer to "
              + "ADR-017 D-3 amendment 2's \"what stops a tenant choosing 365 days?\"")
            .isTrue();
        int sqlMin = Integer.parseInt(m.group(1));
        int sqlMax = Integer.parseInt(m.group(2));

        assertThat(sqlMin).isEqualTo(RatificationStates.MIN_SLA_BUSINESS_DAYS);
        assertThat(sqlMax)
            .as("the database ceiling (%d) and RatificationStates.MAX_SLA_BUSINESS_DAYS (%d) must match, "
              + "or the API and the storage disagree about what a legal SLA is",
                sqlMax, RatificationStates.MAX_SLA_BUSINESS_DAYS)
            .isEqualTo(RatificationStates.MAX_SLA_BUSINESS_DAYS);

        // And the DTO annotation — the layer that produces the explanatory 400.
        Min min = accessorAnnotation("slaBusinessDays", Min.class);
        Max max = accessorAnnotation("slaBusinessDays", Max.class);
        assertThat(min).as("RatificationPolicyRequest.slaBusinessDays must carry @Min").isNotNull();
        assertThat(max).as("RatificationPolicyRequest.slaBusinessDays must carry @Max").isNotNull();
        assertThat(min.value()).isEqualTo(RatificationStates.MIN_SLA_BUSINESS_DAYS);
        assertThat(max.value()).isEqualTo(RatificationStates.MAX_SLA_BUSINESS_DAYS);
    }

    @Test
    void theDefaultSlaIsFiveBusinessDaysAndWithinTheCeiling() {
        assertThat(RatificationStates.DEFAULT_SLA_BUSINESS_DAYS)
            .as("ADR-017 D-3 decided 5 business days — the weekly-CAB market norm")
            .isEqualTo(5);
        assertThat(RatificationStates.DEFAULT_SLA_BUSINESS_DAYS)
            .isBetween(RatificationStates.MIN_SLA_BUSINESS_DAYS, RatificationStates.MAX_SLA_BUSINESS_DAYS);
    }

    @Test
    void grandfatheredIsAStateAndNotAFlag() {
        // Amendment 3. If this ever becomes a boolean beside NOT_REQUIRED, the breach query
        // has to special-case it, and the one row with no implementation timestamp goes back
        // to being filtered out by NULL comparison and silently reported compliant.
        assertThat(RatificationStates.VALUES).contains(RatificationStates.GRANDFATHERED);
        assertThat(RatificationStates.isOpen(RatificationStates.GRANDFATHERED))
            .as("a grandfathered change owes nothing — it must not appear as an open duty")
            .isFalse();
        assertThat(RatificationStates.isOpen(RatificationStates.PENDING)).isTrue();
        assertThat(RatificationStates.isOpen(RatificationStates.AWAITING_IMPLEMENTATION))
            .as("ECAB-approved but not yet implemented is still a duty owed — the clock has "
              + "not started, which is not the same as nothing being owed")
            .isTrue();
    }

    @Test
    void breachIsNotAState() {
        assertThat(RatificationStates.VALUES)
            .as("breach is derived (PENDING and past due) so a late ratification cannot overwrite "
              + "the fact that it breached; ratification_escalated_at is the durable record")
            .doesNotContain("BREACHED");
    }

    @Test
    void theMigrationSeedsAnEscalationRuleForARoleThatIsNotAGhost() throws IOException {
        String sql = migration();
        assertThat(sql).contains("CHG-RATIFY-BREACH");
        assertThat(sql).contains(RatificationStates.EVENT_BREACHED);
        assertThat(sql)
            .as("the escalation must name CISO — verified present in iam.roles in both databases "
              + "before it was written. F2-52: 40 of 74 role names recited in guards do not exist, "
              + "and an escalation aimed at a ghost role is an escalation into a void")
            .contains("CISO");
    }

    /**
     * The rule must carry the key {@code AutomationActionExecutor} actually reads.
     *
     * <p>F2-94, and the reason this assertion is oddly specific. The first cut of V345 wrote
     * {@code "roles":["CISO"]}, copying V305 and 125 other seeded rules. The live probe caught
     * the engine logging {@code ESCALATE (no role configured — audit-only)}:
     * {@code handleNotify} reads {@code to_role} (ESCALATE) or {@code role} (NOTIFY) — singular
     * strings — and never looks at {@code roles}. Its javadoc claims the singular naming matches
     * the seed-rule convention; measured, it matches 12 of 164 rules.
     *
     * <p>So this asserts against the <em>executor's</em> contract rather than the convention.
     * A seeded rule row is not evidence that anything gets delivered.
     */
    @Test
    void theEscalationRuleUsesTheKeyTheEngineReadsAndNotTheSeedConvention() throws IOException {
        String sql = executableSql(migration());
        int at = sql.indexOf("CHG-RATIFY-BREACH");
        assertThat(at).as("V345 must seed CHG-RATIFY-BREACH").isGreaterThan(0);

        assertThat(sql)
            .as("ESCALATE actions are resolved from \"to_role\"; \"roles\":[…] fires, audits, and "
              + "notifies nobody (F2-94 — 126 of 164 rule codes are in that state today)")
            .contains("\"to_role\":\"CISO\"");
        assertThat(sql)
            .as("V345 must not reintroduce the plural key for its own rule")
            .doesNotContain("\"roles\":[\"CISO\"]");
        // And the migration must refuse to pass if the row ever drifts back.
        assertThat(sql)
            .as("the self-check has to assert the delivery key, or a future edit silently "
              + "returns the escalation to audit-only")
            .contains("action_json NOT LIKE N'%\"to_role\"%'");
    }

    @Test
    void theMigrationDoesNotTouchTheChangeStatusVocabulary() throws IOException {
        // X-2 (S5) owns ck_change_status and warns that shipping state code before the
        // constraint is runtime-breaking. D-3 models ratification orthogonally precisely so
        // it does not need that migration — this pins the decision so a later edit cannot
        // quietly couple them.
        //
        // Scanned with comments stripped, and that is not a detail. The first version of
        // this assertion read the raw file and failed: V345's header *explains* why it
        // leaves ck_change_status and PENDING_RATIFICATION alone, so the scanner matched
        // the explanation and reported the opposite of the truth. "Does not touch" is a
        // claim about executable statements. A scanner that matches too much invents
        // defects — the same way one that matches too little misses them (W1-5).
        String sql = executableSql(migration());
        assertThat(sql)
            .as("V345 must not alter ck_change_status — that constraint is X-2's, in S5")
            .doesNotContain("ck_change_status");
        assertThat(sql)
            .as("PENDING_RATIFICATION is X-2's status value, not D-3's; ratification_state is "
              + "orthogonal to the lifecycle status because a change is IMPLEMENTED *and* awaiting "
              + "ratification at the same time")
            .doesNotContain("PENDING_RATIFICATION");
        // Anti-vacuity: prove the stripping did not simply empty the file.
        assertThat(sql).contains("ck_change_ratification_state").contains("change_ratification_policy");
    }

    /** Strip {@code /* ... *}{@code /} block comments and {@code --} line comments. */
    private static String executableSql(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                  .replaceAll("(?m)--[^\\n]*", " ");
    }

    private static <A extends java.lang.annotation.Annotation> A accessorAnnotation(
            String component, Class<A> type) {
        try {
            // jakarta validation annotations have no RECORD_COMPONENT target, so
            // RecordComponent.getAnnotation returns null — read the accessor instead.
            return RatificationDtos.RatificationPolicyRequest.class
                .getDeclaredMethod(component).getAnnotation(type);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("RatificationPolicyRequest has no component '" + component + "'", e);
        }
    }
}
