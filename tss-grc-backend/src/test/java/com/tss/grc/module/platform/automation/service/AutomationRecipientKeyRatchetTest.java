package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-94 — every seeded NOTIFY/ESCALATE rule must address its audience under a key the
 * executor actually reads.
 *
 * <p><b>The defect this closes.</b> {@link AutomationActionExecutor} read {@code role} and
 * {@code to_role}; the seeds wrote {@code roles} on 147 of 164 NOTIFY/ESCALATE rows. So 127
 * of 140 rule codes fired, wrote {@code RULE_FIRED}, incremented {@code fire_count}, logged
 * a run — and reached nobody. Among them: HIGH-risk emergency change approved, expired
 * policy, HIGH-risk policy exception, declined attestation, overdue change task.
 *
 * <p><b>Why a ratchet and not just the executor fix.</b> The seeds had invented seven
 * recipient-ish keys ({@code roles}, {@code role}, {@code to_role}, {@code escalateTo},
 * {@code cc}, {@code also}, {@code ownerRole}). Teaching the executor to read all of them
 * would be whack-a-mole with an unbounded vocabulary, and actively wrong for {@code also},
 * which carries role names on two rules and <em>entity type</em> names on three
 * ({@code DRP}, {@code NcaCriticalAssessment}) — a generous union would broadcast to a
 * "role" named after an assessment type. The vocabulary being open <em>is</em> the defect,
 * so this test closes it: the executor publishes one list, and every migration is checked
 * against that same list. A rule authored with an eighth key fails the build.
 *
 * <p><b>Why it models Flyway's effective state rather than each file.</b> {@code V158} —
 * which seeded the one non-conforming rule — is already applied in dev and in production.
 * Editing it would change its Flyway checksum and fail validation on the deployed server,
 * so the repair has to be a later migration. This test therefore replays the migrations in
 * version order and keeps the <b>last</b> write per rule code, which is exactly the row
 * Flyway leaves behind. A repair migration satisfies it; an allowlist entry would only have
 * silenced it.
 */
class AutomationRecipientKeyRatchetTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");
    private static final ObjectMapper M = new ObjectMapper();

    /**
     * Any single-line JSON string literal. The {@code N} prefix is <b>optional</b>: V96 —
     * the original rule seed, and the file this finding traces back to — writes plain
     * {@code '{"type":"ESCALATE",…}'} with no {@code N}. Requiring the prefix skipped V96
     * and V100 entirely, which is to say it skipped every one of the 13 rules that actually
     * deliver today. A scanner that misses the origin file reports the healthiest part of
     * the codebase as the whole of it.
     */
    private static final Pattern JSON_LITERAL = Pattern.compile("N?'(\\{[^']*\\})'");
    /**
     * Two anchors, because the seeds name a rule in two ways. Later migrations guard with
     * {@code IF NOT EXISTS (… WHERE rule_code = N'AR-146')}; V96 and V100 instead emit one
     * bulk {@code VALUES} list whose tuples open {@code (1, 'AR-06', …)} and contain no
     * {@code rule_code =} anywhere in the file. With only the first anchor those two files
     * contributed nothing — every literal was dropped for want of a code, and the ratchet
     * silently covered 174 rules while appearing to cover all 214.
     *
     * <p>The second alternative matches a literal that is <em>entirely</em> an uppercase
     * hyphenated token ({@code AR-06}, {@code BCM-T4}, {@code CHG-RATIFY-BREACH}). It
     * deliberately does not match the underscored neighbours in the same tuples —
     * {@code PROMPT_14}, {@code RISK_APPETITE_BREACHED}, {@code RISK_COMMITTEE} — nor a
     * date, since a leading letter is required.
     */
    private static final Pattern RULE_CODE = Pattern.compile(
        "rule_code\\s*=\\s*N?'([^']+)'|N?'([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+)'");

    private static String codeOf(Matcher m) {
        return m.group(1) != null ? m.group(1) : m.group(2);
    }
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

    /**
     * Rules that deliberately address no role, with the finding that tracks each. An entry
     * here is a filed defect, not a pass: the alternative was inventing an audience for a
     * rule whose real recipient is a named assignee the engine cannot yet address, and
     * fabricating intent is worse than recording the gap.
     */
    private static final Map<String, String> AUDIENCE_ABSENT_BY_DESIGN = Map.of(
        "AR-149", "F2-97 — COMMITTEE_ACTION_CREATED/CRITICAL_ACTION_ASSIGNED is addressed to "
                + "the action's assignee, a per-user address NOTIFY cannot express. Left "
                + "audience-less rather than given a fabricated role."
    );

    private record Rule(String code, String migration, JsonNode action) {}

    /** Replay the migrations in version order; the last action_json per rule code wins. */
    private static Map<String, Rule> effectiveNotifyRules() {
        Map<String, Rule> effective = new LinkedHashMap<>();
        for (Path file : migrationsInVersionOrder()) {
            String sql = executableSql(read(file));
            Matcher m = JSON_LITERAL.matcher(sql);
            while (m.find()) {
                JsonNode action;
                try {
                    action = M.readTree(m.group(1));
                } catch (Exception notJson) {
                    continue;   // condition_json, a fields payload, or plain prose
                }
                String type = action.path("type").asText("");
                if (!type.equals("NOTIFY") && !type.equals("ESCALATE")) continue;
                String code = owningRuleCode(sql, m.start(), m.end());
                if (code == null) continue;
                effective.put(code, new Rule(code, file.getFileName().toString(), action));
            }
        }
        return effective;
    }

    /**
     * The rule code owning this literal. An INSERT names it in the preceding
     * {@code IF NOT EXISTS} guard; an UPDATE names it in the trailing {@code WHERE}. So a
     * following occurrence wins only when it is inside the <em>same statement</em> — no
     * {@code ;} between the literal and it.
     *
     * <p>⚠ The obvious version of this — nearest occurrence in either direction — is wrong,
     * and wrong in the worst way. A seeded rule's {@code action_json} sits at the end of a
     * long INSERT (two bilingual description columns), so the <b>next</b> rule's guard is
     * often nearer than its own: every code shifted by one. That mis-attribution both
     * invented a defect against an innocent rule and handed a real one the allowlist entry
     * belonging to its neighbour — a scanner's attribution error can hide a defect as
     * easily as fabricate one.
     *
     * <p>⚠ And "no {@code ;} in between" is not enough to recognise the UPDATE shape: a bulk
     * {@code VALUES} list is a single statement, so the <em>next tuple's</em> code is also
     * semicolon-free and every code shifts by one again. A following code therefore wins
     * only when a {@code WHERE} separates it from the literal.
     */
    private static String owningRuleCode(String sql, int literalStart, int literalEnd) {
        String previous = null;
        Matcher m = RULE_CODE.matcher(sql);
        while (m.find()) {
            if (m.start() < literalStart) {
                previous = codeOf(m);
                continue;
            }
            String between = sql.substring(literalEnd, m.start());
            if (!between.contains(";") && between.toUpperCase(java.util.Locale.ROOT).contains("WHERE")) {
                return codeOf(m);   // UPDATE … SET action_json = … WHERE rule_code = …
            }
            break;
        }
        return previous;
    }

    @Test
    void everySeededNotifyRuleAddressesAnAudienceTheExecutorCanRead() {
        Map<String, Rule> rules = effectiveNotifyRules();

        // Anti-vacuity: a regex that matches too little invents a clean bill of health.
        assertThat(rules)
            .as("the scan must find the platform's rule population — 140 NOTIFY/ESCALATE "
              + "codes were measured in the database on 2026-08-03")
            .hasSizeGreaterThan(100);

        List<String> unreachable = new ArrayList<>();
        for (Rule r : rules.values()) {
            if (AUDIENCE_ABSENT_BY_DESIGN.containsKey(r.code())) continue;
            if (AutomationActionExecutor.recipientRoles(r.action()).isEmpty()) {
                unreachable.add(r.code() + " (" + r.migration() + ") keys="
                    + fieldNames(r.action()));
            }
        }

        assertThat(unreachable)
            .as("these rules fire, audit, and reach nobody: their audience is named under a "
              + "key %s does not read. Add the audience under one of %s in a NEW migration "
              + "(never by editing an applied one — Flyway validates checksums), or, if the "
              + "rule genuinely has no role audience, file a finding and list it in "
              + "AUDIENCE_ABSENT_BY_DESIGN", "AutomationActionExecutor", AutomationActionExecutor.RECIPIENT_KEYS)
            .isEmpty();
    }

    @Test
    void theExecutorsKeyVocabularyStaysClosedAndExcludesTheOverloadedOne() {
        assertThat(AutomationActionExecutor.RECIPIENT_KEYS)
            .as("`roles` carried 147 of 164 rows and must be first; the two singular keys "
              + "stay for the 15 rows that use them")
            .containsExactly("roles", "role", "to_role");

        // The measured reason this set is closed rather than generous. `also` holds role
        // names on AR-09/AR-12 (CONTROL_OWNER, OWNER_MANAGER) and entity type names on
        // AR-02/AR-05/AR-20 (DRP, NcaCriticalAssessment, PrivacyReassessment). Reading it
        // would broadcast to a "role" named after an assessment type.
        assertThat(AutomationActionExecutor.RECIPIENT_KEYS)
            .as("`also` is semantically overloaded in the seeds — never resolve recipients "
              + "from it; widen the vocabulary only by normalising the data")
            .doesNotContain("also", "alsoNotify", "cc", "to");
    }

    @Test
    void aPluralAudienceResolvesAndDeduplicatesCaseInsensitively() throws Exception {
        // The shape 147 rows use.
        assertThat(AutomationActionExecutor.recipientRoles(
                M.readTree("{\"type\":\"NOTIFY\",\"roles\":[\"CISO\",\"DPO\"]}")))
            .containsExactly("CISO", "DPO");

        // Scalar on a plural key, and a duplicate across two keys — both are shapes the
        // seeds contain, and neither may produce a doubled notification.
        assertThat(AutomationActionExecutor.recipientRoles(
                M.readTree("{\"roles\":\"CISO\",\"to_role\":\"ciso\",\"role\":\" CISO \"}")))
            .containsExactly("CISO");

        // Blanks and nulls are not an audience — they must fall through to the
        // "addresses no audience" branch rather than broadcasting to "".
        assertThat(AutomationActionExecutor.recipientRoles(
                M.readTree("{\"roles\":[\"\",\"  \"],\"role\":null}")))
            .isEmpty();
    }

    @Test
    void everyAllowlistedRuleIsStillAudienceLessAndStillCited() {
        Map<String, Rule> rules = effectiveNotifyRules();
        for (Map.Entry<String, String> e : AUDIENCE_ABSENT_BY_DESIGN.entrySet()) {
            Rule r = rules.get(e.getKey());
            assertThat(r)
                .as("allowlisted rule %s no longer exists — remove the entry so the "
                  + "allowlist cannot outlive what it excuses", e.getKey())
                .isNotNull();
            assertThat(AutomationActionExecutor.recipientRoles(r.action()))
                .as("%s now addresses an audience — delete its allowlist entry, or the next "
                  + "reader will believe a working rule is a known gap", e.getKey())
                .isEmpty();
            assertThat(e.getValue())
                .as("an allowlist entry without a finding id is an untracked defect")
                .matches("(?s).*F2-\\d+.*");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private static List<Path> migrationsInVersionOrder() {
        try (Stream<Path> s = Files.list(MIGRATIONS)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingInt(AutomationRecipientKeyRatchetTest::versionOf))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int versionOf(Path p) {
        Matcher m = VERSION.matcher(p.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    /**
     * Drop whole-line {@code --} comments. A migration's own header often quotes the JSON
     * it is about to write, and matching a comment instead of a statement is how a scanner
     * reports a defect that is not there (or misses one that is).
     */
    private static String executableSql(String sql) {
        return sql.lines()
            .filter(l -> !l.stripLeading().startsWith("--"))
            .reduce(new StringBuilder(), (b, l) -> b.append(l).append('\n'), StringBuilder::append)
            .toString();
    }

    private static Set<String> fieldNames(JsonNode n) {
        Set<String> out = new java.util.LinkedHashSet<>();
        n.fieldNames().forEachRemaining(out::add);
        return out;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
