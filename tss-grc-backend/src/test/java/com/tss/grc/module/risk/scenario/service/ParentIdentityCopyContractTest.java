package com.tss.grc.module.risk.scenario.service;

import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-245 — <b>a soft FK that also carries the parent's identity is making TWO claims, and only
 * the parent can answer the second.</b>
 *
 * <p>{@code risk.scenarios} stores {@code risk_id} (which row) beside {@code risk_code} and
 * {@code risk_title_en} (what it is called). {@code RiskScenarioService.create} took all three
 * from the request, so nothing ever compared them. Measured in dev and prod:
 *
 * <ul>
 *   <li><b>11 of 11</b> live scenarios stored a {@code risk_title_en} that is not the
 *       {@code title_en} of the risk their {@code risk_id} names. Scenario 1, <i>"Aramco contract
 *       delay — Monte Carlo (base case)"</i>, stored <i>"Aramco contract renewal delay"</i>
 *       against {@code risk_id = 4}, which is <i>"Non-Compliance with NCA ECC Controls"</i>.</li>
 *   <li>Every stored {@code risk_code} is {@code RSK-2026-NNN}, and {@code risk.risks} has
 *       <b>no code column at all</b> — {@code external_id} is its only reference and it is a
 *       UUID. So the register's risk column named a code space that exists nowhere in the
 *       platform (F2-203's disjoint-code-space shape).</li>
 * </ul>
 *
 * <p>🔑 <b>The two halves were read by different surfaces, and nothing ever put them side by
 * side.</b> The stored copy is what the register renders; the id is what
 * {@code GET /by-risk/{riskId}} joins on. So the FAIR/Monte-Carlo analysis a risk committee
 * reads under one risk's name was, in the database, hanging off another — and the label was
 * always coherent, which is what stopped anybody asking.
 *
 * <p>🔑 <b>The frontend collected the LABEL and never the LINK.</b> The create form had two
 * free-text inputs and no {@code riskId} at all, the code one placeholdered
 * {@code 'RSK-2026-042'}. So a scenario created through the platform's own form could never be
 * found from its risk, because the column the join uses was never set. F2-236's rule — an
 * invented value in a placeholder is how a free-text field teaches an operator to invent one.
 *
 * <p>⚠ <b>REFUTED while deriving this, and the refutations are the reason the census carries no
 * verdicts.</b> {@code NdiGapService} validates its {@code risk_id} on <i>both</i> write paths
 * through {@code NdiCrossLinkService}, whose javadoc calls itself <i>"the single place where
 * those soft links are validated against their real targets"</i> — true of NDI, and the phrase
 * is exactly what made seven peers writing the same column look covered (F2-215's two-of-N).
 * {@code RiskControlFindingPivotService} dereferences {@code link.getRiskId()} at three sites
 * with a bare {@code findById}, and <b>all three are correct</b>: every one is followed by
 * {@code .filter(r -> orgId.equals(r.getOrganisationId()))}. A grep located them; reading
 * adjudicated them (F2-244).
 *
 * <p>This gate is deliberately two things at once, because they fail for different reasons:
 * an <b>invariant</b> over the register this slice fixed (a hard zero, no exemptions — there is
 * no correct code for it to fail on, since a derived copy is always available), and an
 * <b>exactly-asserted census</b> of the shape platform-wide, with <b>no verdicts</b> for the
 * unread members (F2-188: five fluent reasons, five false).
 */
class ParentIdentityCopyContractTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "tss", "grc");
    private static final Path SERVICE =
            MAIN.resolve("module/risk/scenario/service/RiskScenarioService.java");
    private static final Path FE_CLIENT =
            Path.of("..", "tss-grc-ui-v2", "src", "lib", "riskScenarios.ts");
    private static final Path FE_PAGE =
            Path.of("..", "tss-grc-ui-v2", "src", "pages", "risk-scenarios", "RiskScenariosPage.tsx");

    /**
     * Strip line and block comments in ONE left-to-right alternation.
     *
     * <p>⚠⚠ Two sequential passes is the defect (F2-198, corrected by F2-211): at a {@code //}
     * an inner {@code /*} can never open a block, and a URL cannot truncate a block. Written the
     * obvious way it deleted 45% of a file and reported a SMALLER population, which reads exactly
     * like a clean tree. <b>Within one alternation the branch order is a no-op</b> — the two
     * patterns can never match at the same position — so the thing that matters is that it is
     * one alternation, not which branch comes first.
     *
     * <p>Stripping is load-bearing here for the reason F2-193/F2-202/F2-205 all record: this
     * file's own javadoc <i>quotes the retired shapes</i>, so a comment-blind scan reads the
     * epitaph as the corpse.
     */
    private static final Pattern COMMENTS =
            Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String code(Path p) {
        try {
            return COMMENTS.matcher(Files.readString(p, StandardCharsets.UTF_8)).replaceAll("");
        } catch (IOException e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath() + " — a gate that cannot "
                    + "read its subject reads exactly like a clean tree", e);
        }
    }

    // ── the shape, as a detector ───────────────────────────────────────────────────────────────

    /**
     * A parent-identity column: an id naming a row, plus a word saying what that row is called.
     *
     * <p>Deliberately NOT a list of column names — F2-217 measured what a literal field-name
     * list costs (it finds the spellings its author had already seen). The suffix set is the
     * shape.
     */
    private static final Pattern IDENTITY_COPY = Pattern.compile(
            "^(?<parent>[a-z0-9_]+?)_(?:code|title_en|title_ar|name_en|name_ar|name)$");

    /** A setter fed straight from the bound request — the defect's signature. */
    private static Pattern writtenFromRequest(String setter) {
        return Pattern.compile(
                "\\." + Pattern.quote(setter) + "\\s*\\(\\s*(?:req|body|in|patch)\\s*\\.");
    }

    // ── leg 1: the register this slice fixed ───────────────────────────────────────────────────

    @Test
    @DisplayName("no writer stores a caller-supplied copy of the parent risk's identity")
    void noWriterStoresACallerSuppliedParentIdentity() {
        String src = code(SERVICE);
        for (String setter : List.of("setRiskId", "setRiskCode", "setRiskTitleEn")) {
            assertThat(writtenFromRequest(setter).matcher(src).find())
                    .as("%s is fed from the request in RiskScenarioService. The parent's identity "
                      + "must be DERIVED from the risk the server resolves — a caller who can "
                      + "name the id and also say what it is called can make the register "
                      + "contradict itself, which is what 11 of 11 live rows do.", setter)
                    .isFalse();
        }
    }

    /**
     * ⚠ <b>This leg's first draft failed on the correct fix, and the gate was wrong rather than
     * the code.</b> It asserted "exactly one call site" per setter; {@code applyParentRisk} sets
     * {@code riskId} in <i>both</i> branches — null parent and resolved parent — which is right,
     * because a scenario with no parent must have all three columns cleared, not left alone.
     * F2-206's rule applied to an assertion: a red leg is a finding only if the code is actually
     * wrong. The claim that was wanted is <b>"the only writer is the resolver"</b>, which is a
     * statement about WHERE the writes live, not how many there are — and it is the claim that
     * actually prevents drift.
     */
    @Test
    @DisplayName("all three parent columns are written only inside the tenant-resolving helper")
    void allThreeParentColumnsAreWrittenOnlyByTheResolver() {
        String src = code(SERVICE);
        Matcher m = Pattern.compile(
                "private void applyParentRisk\\([^)]*\\)\\s*\\{(?<body>[\\s\\S]*?)\\n    \\}")
                .matcher(src);
        assertThat(m.find()).as("applyParentRisk must be declared in this file").isTrue();
        String body = m.group("body");
        String outside = src.substring(0, m.start()) + src.substring(m.end());

        for (String setter : List.of("setRiskId", "setRiskCode", "setRiskTitleEn")) {
            Pattern site = Pattern.compile("\\." + Pattern.quote(setter) + "\\s*\\(");
            assertThat(site.matcher(outside).find())
                    .as("%s is written outside applyParentRisk. A second writer is how the id "
                      + "and its identity copy drift apart — the whole finding.", setter)
                    .isFalse();
            assertThat(site.matcher(body).find())
                    .as("%s must be written by the resolver — dropping the write would leave the "
                      + "column stale rather than derived", setter)
                    .isTrue();
        }
        assertThat(body)
                .as("the resolver must filter on the caller's organisation — an existence check "
                  + "is not a tenant check (F2-242: a foreign key knows nothing about tenancy, "
                  + "so a cross-tenant id is a perfectly valid reference)")
                .contains("getOrganisationId()");
        assertThat(body)
                .as("the resolver must refuse rather than store an unresolvable reference")
                .contains("orElseThrow");
    }

    // ── leg 2: the strongest closure — the bound type cannot carry them ────────────────────────

    @Test
    @DisplayName("the create request does not declare the parent's identity, and the response does")
    void theBoundTypeCannotCarryTheCopiesAndTheResponseStillDoes() {
        Set<String> create = componentNames(RiskScenarioDtos.CreateRequest.class);
        assertThat(create)
                .as("a field the bound type does not declare can never arrive — the strongest "
                  + "closure there is (F2-233), and stronger than an annotation")
                .doesNotContain("riskCode", "riskTitleEn");
        assertThat(create)
                .as("the create must still take the LINK; removing that would retire the "
                  + "capability instead of fixing it")
                .contains("riskId");

        // The reverse leg — the one nobody writes first (F2-199). The server DERIVES both and
        // sends them back; a slice that "cleaned up" the response would blank the register's
        // risk column, which is the opposite of this fix.
        Set<String> response = componentNames(RiskScenarioDtos.Response.class);
        assertThat(response)
                .as("the response must still carry the derived identity — the register renders it")
                .contains("riskId", "riskCode", "riskTitleEn");
    }

    private static Set<String> componentNames(Class<?> record) {
        assertThat(record.isRecord()).as("%s must be a record", record).isTrue();
        Set<String> out = new LinkedHashSet<>();
        for (RecordComponent rc : record.getRecordComponents()) out.add(rc.getName());
        return out;
    }

    // ── leg 3: the frontend half, where the defect was visible ────────────────────────────────

    @Test
    @DisplayName("the frontend collects the link, not the label")
    void theFrontendCollectsTheLinkNotTheLabel() {
        String client = code(FE_CLIENT);
        Matcher m = Pattern.compile(
                "export interface ScenarioCreateRequest \\{(?<body>[^}]*)\\}").matcher(client);
        assertThat(m.find()).as("ScenarioCreateRequest must be declared in lib/riskScenarios.ts").isTrue();
        String body = m.group("body");
        assertThat(body)
                .as("the FE create type must not declare the parent's identity — the wire and the "
                  + "client are two contracts and only one of them used to be checked (F2-211)")
                .doesNotContain("riskCode").doesNotContain("riskTitleEn");
        assertThat(body).as("the FE create type must declare the link").contains("riskId");

        String page = code(FE_PAGE);
        // Assert the CALL SHAPE, never the bare token: F2-194 recorded a substring check
        // satisfied by an import, and F2-205 one satisfied by a name still in an import list.
        assertThat(Pattern.compile("riskId\\s*:\\s*values\\.riskId").matcher(page).find())
                .as("the create handler must send the resolved riskId from the picker")
                .isTrue();
        assertThat(page)
                .as("the retired free-text inputs must be gone: a field asking an operator to "
                  + "type a parent's code is how the invented code space was populated")
                .doesNotContain("key: 'riskCode'")
                .doesNotContain("key: 'riskTitleEn'");
        assertThat(page)
                .as("the picker's options must come from the tenant's own register, so a risk "
                  + "the tenant does not own cannot be named at all")
                .contains("riskApi.list");
    }

    // ── leg 4: the census — the shape platform-wide, no verdicts ──────────────────────────────

    /**
     * The two members that are <b>BY DESIGN</b>, and they are the only ones with a verdict.
     *
     * <p>An audit row must record what an entity was <i>called at the time</i>, because the
     * entity may since have been renamed or deleted — that is the point of an audit trail, and
     * deriving the label at read time would rewrite history. Both were read; neither is a
     * caller-supplied copy of a live parent.
     *
     * <p>⚠ A hard zero over the whole census would fail on these, and <b>a gate that fails on
     * correct code is worse than no gate: it trains the next reader to edit the assertion rather
     * than the code</b> (F2-214).
     */
    private static final Map<String, String> BY_DESIGN = new TreeMap<>(Map.of(
            "platform.audit_log :: entity_id + entity_code",
            "BY DESIGN — an audit row is a POINT-IN-TIME record. It must keep the code the entity "
          + "carried when the event happened, because the entity may since have been renamed or "
          + "soft-deleted, and deriving the label at read time would silently rewrite the trail. "
          + "The copy is written by the audit writer from the subject it was handed, never from a "
          + "caller's request body.",

            "ndi.audit_event :: entity_id + entity_code",
            "BY DESIGN — the NDI audit trail, same reasoning as platform.audit_log: NdiAuditPublisher "
          + "is handed the subject's own code by the service that just changed it, so the stored "
          + "value is a snapshot of a resolved row rather than a caller's claim about one."
    ));

    /** The register this slice closed — asserted present so a rename cannot silently drop it. */
    private static final String CLOSED_THIS_SLICE = "risk.scenarios :: risk_id + risk_code, risk_title_en";

    @Test
    @DisplayName("census: every entity carrying a parent id AND a copy of that parent's identity")
    void censusOfIdPlusIdentityCopyPairs() {
        Map<String, List<String>> pairs = measurePairs();

        System.out.println("F2-245 — (parent id + identity copy) pairs: " + pairs.size());
        pairs.forEach((k, v) -> System.out.println("    " + k + "  copies=" + v));

        // Anti-vacuity. The floor must sit above the largest WRONG answer, not above zero
        // (F2-215): a detector that stops matching reports an empty census, which is
        // indistinguishable from a swept tree. Measured 52 raw pairs over 425 entities.
        assertThat(pairs.size())
                .as("a detector that stops resolving reports a small census, and small reads "
                  + "exactly like clean")
                .isGreaterThanOrEqualTo(30);

        assertThat(pairs.keySet())
                .as("the register F2-245 closed must stay in the census — if this entity is "
                  + "renamed the census must move with it rather than quietly shrinking")
                .contains(CLOSED_THIS_SLICE);

        // Every BY_DESIGN verdict must still name a member the scan finds. This is the direction
        // nobody writes first (F2-199), and it is what stops a reason outliving its subject —
        // F2-199's own first draft wrote two reasons for pages that were not in the population.
        for (String key : BY_DESIGN.keySet()) {
            assertThat(pairs.keySet())
                    .as("BY_DESIGN names '%s', which the scan does not find. A reason written for "
                      + "something not in the population reads exactly like a reason for "
                      + "something that is.", key)
                    .contains(key);
        }
        for (String reason : BY_DESIGN.values()) {
            assertThat(reason)
                    .as("a verdict must name its evidence, not wear a label (F2-228)")
                    .contains("BY DESIGN").hasSizeGreaterThan(140);
        }
    }

    /**
     * ⚠ Deliberately <b>no verdicts</b> for the rest.
     *
     * <p>F2-188 measured what happens when a slice writes reasons for code it has not read: five
     * claims, five false, and the fluency was what made them look verified. Two of the seven
     * candidates this slice DID read were refuted. So the remainder is a work list, and its size
     * is asserted only as a floor — it may shrink as slices adjudicate members, and it must not
     * silently shrink because the detector rotted.
     *
     * <p>Note the classification a reader needs before touching it: roughly half the raw
     * population is {@code *_by_id + *_by_name}, which is <b>actor attribution</b> — F2-236's
     * family, a different question (who performed this) with a different answer (the
     * authenticated principal). The parent-register members are the ones this shape is about,
     * and F2-243's own filing already noticed one of them:
     * {@code scm.exit_plans}, where <i>"vendor_name is separately caller-supplied and NOT NULL,
     * so the register can contradict itself"</i>.
     */
    @Test
    @DisplayName("the unadjudicated remainder is a work list, and it is not empty by accident")
    void theRemainderIsAWorkListWithNoVerdicts() {
        Map<String, List<String>> pairs = measurePairs();
        List<String> unadjudicated = pairs.keySet().stream()
                .filter(k -> !BY_DESIGN.containsKey(k))
                .filter(k -> !k.equals(CLOSED_THIS_SLICE))
                .collect(Collectors.toList());

        System.out.println("F2-245 — unadjudicated: " + unadjudicated.size());
        unadjudicated.forEach(k -> System.out.println("    NEEDS-READING  " + k));

        assertThat(unadjudicated)
                .as("an empty work list on a tree nobody has swept means the detector stopped "
                  + "matching — F2-219's lesson that a floor of `>= BASELINE - n` cannot fail at 0")
                .isNotEmpty();
    }

    private static Map<String, List<String>> measurePairs() {
        Map<String, List<String>> out = new TreeMap<>();
        for (Class<?> type : shippedEntities()) {
            Map<String, String> byColumn = new LinkedHashMap<>();
            for (Field f : allFields(type)) {
                Column c = f.getAnnotation(Column.class);
                if (c == null || c.name().isBlank()) continue;
                byColumn.put(c.name(), f.getName());
            }
            Set<String> columns = byColumn.keySet();
            Map<String, List<String>> perParent = new TreeMap<>();
            for (String col : columns) {
                Matcher m = IDENTITY_COPY.matcher(col);
                if (!m.matches()) continue;
                String idCol = m.group("parent") + "_id";
                if (columns.contains(idCol)) {
                    perParent.computeIfAbsent(idCol, k -> new ArrayList<>()).add(col);
                }
            }
            if (perParent.isEmpty()) continue;
            Table t = type.getAnnotation(Table.class);
            String table = t == null ? type.getSimpleName()
                    : (t.schema().isBlank() ? "dbo" : t.schema()) + "." + t.name();
            perParent.forEach((idCol, copies) -> {
                copies.sort(String::compareTo);
                out.put(table + " :: " + idCol + " + " + String.join(", ", copies), copies);
            });
        }
        return out;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            out.addAll(List.of(c.getDeclaredFields()));
        }
        return out;
    }

    private static List<Class<?>> shippedEntities() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                if (ships(c)) out.add(c);
            } catch (Throwable ignored) {
                // a class that cannot load cannot be asserted on
            }
        }
        return out;
    }

    /**
     * Shipped, not a test fixture — decided by <b>code source</b> and never by a name ending in
     * {@code Test}, because F2-241 found its own census counting
     * {@code ServerOwnedFieldsInvariantTest$SyntheticUnprobeableEntity}, and F2-192 recorded that
     * a name test lets any member escape by being renamed.
     */
    private static boolean ships(Class<?> type) {
        try {
            var src = type.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null) return true;
            return !src.getLocation().getPath().replace('\\', '/').contains("/test-classes");
        } catch (Throwable t) {
            return true;
        }
    }

    // ── leg 5: the synthetic self-test ────────────────────────────────────────────────────────

    /**
     * The detector must fire on the literal pre-fix shape and stay silent on the fixed one.
     *
     * <p>F2-195's rule: the corpus assertions above pass both when the tree is clean and when
     * the regexes have rotted, so something has to drive the detector against a known defect.
     * F2-208 is the sharper warning — its load-bearing detector was SILENT on the very defect it
     * was written for, because it was written for the shape its author imagined.
     */
    @Test
    @DisplayName("self-test: the detector fires on the pre-fix shape and not on the fixed one")
    void selfTest() {
        String preFix = """
                s.setRiskId(req.riskId());
                s.setRiskCode(req.riskCode());
                s.setRiskTitleEn(req.riskTitleEn());
                """;
        String fixed = """
                applyParentRisk(s, req.riskId(), orgId);
                s.setRiskId(parent.getId());
                s.setRiskCode(parent.getExternalId() == null ? null : parent.getExternalId().toString());
                s.setRiskTitleEn(parent.getTitleEn());
                """;
        for (String setter : List.of("setRiskId", "setRiskCode", "setRiskTitleEn")) {
            assertThat(writtenFromRequest(setter).matcher(preFix).find())
                    .as("the detector must fire on the pre-fix %s", setter).isTrue();
            assertThat(writtenFromRequest(setter).matcher(fixed).find())
                    .as("the detector must NOT fire on the fixed %s — a gate that fails on "
                      + "correct code is worse than no gate", setter).isFalse();
        }

        // The comment stripper is load-bearing, in both directions (F2-198/F2-211).
        assertThat(COMMENTS.matcher("// a URL http://x and a /* inside a line comment\nkeep();")
                .replaceAll("").trim())
                .as("a `/*` inside a `//` comment must not open a block — the two-pass bug "
                  + "deleted 45% of a file and reported a SMALLER population")
                .isEqualTo("keep();");
        assertThat(COMMENTS.matcher("a();/* b\nc */d();").replaceAll(""))
                .as("a real block comment must still be removed").isEqualTo("a();d();");

        // And the shape detector must not mistake an actor attribution for a parent identity's
        // TITLE: both are id + name, and only reading tells them apart. Asserted so the
        // distinction is recorded rather than assumed.
        assertThat(IDENTITY_COPY.matcher("approver_name").matches())
                .as("`approver_name` DOES match the shape — it is F2-236's actor-attribution "
                  + "family, which is why the census carries no verdicts and says so")
                .isTrue();
        assertThat(IDENTITY_COPY.matcher("risk_id").matches())
                .as("an id is not an identity copy").isFalse();
    }
}
