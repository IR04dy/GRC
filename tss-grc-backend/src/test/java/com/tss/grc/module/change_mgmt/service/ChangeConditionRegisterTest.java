package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.entity.ChangeCondition;
import com.tss.grc.module.change_mgmt.repository.ChangeConditionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7e / CE-5 — <b>the conditions register</b>: approve-with-conditions becomes a recorded, enforced
 * obligation instead of a sentence in a notes field.
 *
 * <p><b>The gap, measured 2026-08-04 in both databases.</b> G-5 of the uplift plan reads "Decision
 * vocabulary too narrow — no DEFER / MORE_INFO / approve-with-conditions; conditions not recorded or
 * enforced before implementation". S7c closed the first half. The rest measured as total absence:
 *
 * <pre>
 *   tables named %condition% platform-wide ......... 0   (dev and prod)
 *   conditions concept in change_mgmt/** .......... 0   (every grep hit is incidental prose)
 *   conditions concept in admin/committee/** ...... 0
 * </pre>
 *
 * <p>So a CAB approving a change "subject to the DR rehearsal being re-run" had one place to put it —
 * {@code cab_decision_notes} — and the change proceeded to {@code IMPLEMENTING} with the obligation
 * unrecorded, unowned and unchecked.
 *
 * <p>These tests pin the three decisions the slice took, because each of them is the kind a later
 * tidy-up reverses without noticing.
 */
class ChangeConditionRegisterTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");
    private static final Path SERVICE =
        Path.of("src/main/java/com/tss/grc/module/change_mgmt/service/ChangeManagementService.java");
    private static final Path CONTROLLER =
        Path.of("src/main/java/com/tss/grc/module/change_mgmt/controller/ChangeManagementController.java");

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Strip block and line comments before asserting a call site exists.
     *
     * <p>⚠ Found by mutation-proving this file: commenting out {@code assertConditionsSatisfied(c);}
     * left the string in the source, so {@code theGuardIsWiredIntoTheTransition} stayed green while
     * the gate was disabled. Only the behavioural companion caught it. A structural test that a
     * comment satisfies is the "reads as coverage, proves nothing" failure — same rule
     * {@code ChangeModuleBoundaryTest.code()} applies for the opposite reason (a javadoc naming a
     * peer is documentation, not a dependency).
     */
    private static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }

    private static String migration() {
        try (Stream<Path> walk = Files.walk(MIGRATIONS)) {
            return walk.filter(p -> p.getFileName().toString().startsWith("V352__"))
                       .findFirst().map(ChangeConditionRegisterTest::read)
                       .orElseThrow(() -> new AssertionError("V352 is missing — the register has no table"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── Decision 1: the vocabulary, stated once ─────────────────────────────────────────────────

    @Test
    @DisplayName("the Java status vocabulary equals ck_cc_status")
    void javaMatchesTheCheckConstraint() {
        Matcher m = Pattern.compile(
            "CONSTRAINT\\s+ck_cc_status\\s+CHECK\\s*\\(\\s*status\\s+IN\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE).matcher(migration());
        assertThat(m.find()).as("V352 no longer declares ck_cc_status — this test is broken, not the code").isTrue();

        Set<String> declared = new LinkedHashSet<>();
        for (String tok : m.group(1).split(",")) {
            String v = tok.trim().replaceAll("^[Nn]?'", "").replaceAll("'$", "").trim();
            if (!v.isEmpty()) declared.add(v);
        }
        // F2-91's pattern: a vocabulary stated in two places and checked in neither was wrong in two
        // of the three places it appeared.
        assertThat(declared).containsExactlyInAnyOrderElementsOf(ChangeConditionStatus.ALL);
        assertThat(ChangeConditionStatus.ALL).containsExactly("OPEN", "SATISFIED", "WAIVED");
    }

    @Test
    @DisplayName("SATISFIED and WAIVED both close, and are not collapsed into one value")
    void aWaiverIsNotCompliance() {
        // Collapsing them into "CLOSED" would make a governance breach indistinguishable from
        // compliance in every report — and the waiver is the one an auditor asks about.
        assertThat(ChangeConditionStatus.CLOSED).containsExactlyInAnyOrder("SATISFIED", "WAIVED");
        assertThat(ChangeConditionStatus.isClosed("OPEN")).isFalse();
        assertThat(ChangeConditionStatus.isClosed("SATISFIED")).isTrue();
        assertThat(ChangeConditionStatus.isClosed("WAIVED")).isTrue();
        assertThat(ChangeConditionStatus.isClosed(null)).isFalse();
        assertThat(ChangeConditionStatus.isClosed(" satisfied ")).as("case/space tolerant").isTrue();
    }

    // ── Decision 2: conditionality is DERIVED, never stored ─────────────────────────────────────

    @Test
    @DisplayName("⚠ there is no CONDITIONAL status — conditionality is derived from the register")
    void conditionalityIsDerivedAndNotAWord() throws Exception {
        // A literal stored beside the rows can disagree with them: a CONDITIONAL change whose
        // conditions were never entered reads conditional with nothing to satisfy, and a plain
        // CAB_APPROVED one that later acquires a condition reads unconditional while being blocked.
        @SuppressWarnings("unchecked")
        Field f = ChangeManagementService.class.getDeclaredField("NORMAL_TRANSITIONS");
        f.setAccessible(true);
        var transitions = (java.util.Map<String, Set<String>>) f.get(null);
        Set<String> reachable = new LinkedHashSet<>(transitions.keySet());
        transitions.values().forEach(reachable::addAll);
        assertThat(reachable)
            .as("""
                An approve-with-conditions change IS approved (CAB_APPROVED); the conditions are the \
                record. V352's self-check 7 THROWs if ck_change_status ever names CONDITIONAL — to \
                change that decision, amend the migration, ChangeConditionStatus's javadoc and this \
                test in one commit.""")
            .doesNotContain("CONDITIONAL");
        assertThat(migration())
            .as("the migration must state the decision, not merely omit the value")
            .contains("CONDITIONAL");

        assertThat(ChangeConditionStatus.isConditional(0)).isFalse();
        assertThat(ChangeConditionStatus.isConditional(1)).isTrue();
        assertThat(ChangeConditionStatus.isConditional(3))
            .as("all conditions, not open ones — a change whose conditions were all satisfied WAS "
              + "approved conditionally and must keep saying so, or 'how many conditional approvals "
              + "did the CAB grant' becomes unanswerable")
            .isTrue();
    }

    // ── Decision 3: the gate ships WITH the register ────────────────────────────────────────────

    @Test
    @DisplayName("⚠ SCHEDULED → IMPLEMENTING consults the register — a register nothing reads is a dead control")
    void theGuardIsWiredIntoTheTransition() {
        String src = read(SERVICE);
        Matcher arm = Pattern.compile("case \"IMPLEMENTING\" ->\\s*\\{(.*?)\\n            \\}",
                                      Pattern.DOTALL).matcher(src);
        assertThat(arm.find()).as("could not locate the IMPLEMENTING branch").isTrue();
        String body = arm.group(1);
        assertThat(body)
            .as("""
                The plan lists ConditionsSatisfiedGuard under S8, and S8's version adds a real \
                requirement (the satisfaction must carry VERIFIED evidence). But a register nothing \
                consults is the F2-62/F2-95/F2-89 shape this programme has spent four slices closing \
                — 33 configured requirements enforced zero times. S7e ships the gate; S8 strengthens \
                what counts as satisfying it.""")
            .contains("assertConditionsSatisfied(c)");
        // Before any mutation: a refusal must leave the change untouched, and arming the A17-D3
        // ratification clock for an implementation that is then refused would start an SLA against
        // work that never began.
        assertThat(body.indexOf("assertConditionsSatisfied"))
            .as("the gate must run before setImplementationStarted and startRatificationClock")
            .isLessThan(body.indexOf("setImplementationStarted"))
            .isLessThan(body.indexOf("startRatificationClock"));
    }

    @Test
    @DisplayName("the refusal names the blocking conditions, not just their count")
    void theRefusalTellsTheOperatorWhatToDo() {
        String src = code(read(SERVICE));
        int at = src.indexOf("private void assertConditionsSatisfied");
        assertThat(at).as("assertConditionsSatisfied is gone").isGreaterThan(0);
        String body = src.substring(at, src.indexOf("\n    }", at));
        // The A17-D2/F2-96 remedy rule: an operator told "conditions are outstanding" has to go
        // looking. The thing they must do belongs in the message.
        assertThat(body).contains("getTextEn");
        assertThat(body).containsPattern("waive|satisfied");
    }

    @Test
    @DisplayName("⚠ the register repository is actually injected — not green-because-absent")
    void theRegisterRepositoryIsActuallyWired() throws Exception {
        // The field is @Autowired(required=false) so six positionally-constructed test classes keep
        // compiling, and a null repo makes the guard no-op — the fail-OPEN direction. That is only
        // acceptable because it cannot happen in the running app, which this asserts rather than
        // assumes. ChangeAuthorityFailClosedTest documented the same trap after three of its
        // assertions passed vacuously.
        Field f = ChangeManagementService.class.getDeclaredField("conditionRepo");
        assertThat(f.getType()).isEqualTo(ChangeConditionRepository.class);
        assertThat(f.getAnnotation(org.springframework.beans.factory.annotation.Autowired.class))
            .as("the field must be an injection point, or Spring never populates it and the guard "
              + "silently no-ops in production")
            .isNotNull();
    }

    // ── The authorisation split ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("imposing and waiving are APPROVE acts; satisfying is a WRITE act")
    void theGuardsSayWhoMayDoWhat() {
        String src = code(read(CONTROLLER));
        record Ep(String path, String action) { }
        for (Ep e : List.of(new Ep("/{id}/conditions\")", "APPROVE"),
                            new Ep("/conditions/{conditionId}/satisfy\")", "WRITE"),
                            new Ep("/conditions/{conditionId}/waive\")", "APPROVE"))) {
            int at = src.indexOf("@PostMapping(\"" + e.path());
            assertThat(at).as("endpoint %s is missing", e.path()).isGreaterThan(0);
            String block = src.substring(at, Math.min(src.length(), at + 320));
            assertThat(block)
                .as("""
                    %s must demand CHANGE:%s. Adding an obligation as the price of approval and \
                    forgiving one are approval acts (9 roles hold CHANGE:APPROVE); doing the work is \
                    operational (42 hold CHANGE:WRITE). F2-29's classification, applied to a new \
                    surface rather than discovered on it later — a waiver on the CHANGE:WRITE \
                    default would let anyone who can edit a change delete the condition attached to \
                    its approval.""", e.path(), e.action())
                .contains("hasPermission('CHANGE','" + e.action() + "')");
        }
    }

    @Test
    @DisplayName("a waiver cannot come from the change's own requester or owner")
    void aConditionTheRequesterCanForgiveIsNotACondition() {
        String src = code(read(SERVICE));
        int at = src.indexOf("public ChangeCondition waiveCondition");
        assertThat(at).isGreaterThan(0);
        String body = src.substring(at, src.indexOf("\n    }", at));
        assertThat(body).contains("closeCondition");
        int close = src.indexOf("private ChangeCondition closeCondition");
        String closeBody = src.substring(close, src.indexOf("\n    }", close));
        assertThat(closeBody)
            .as("the self-review refusal must be on the waive path")
            .contains("assertNotSelfWaiver(c)");
        int sod = src.indexOf("private void assertNotSelfWaiver");
        String sodBody = src.substring(sod, src.indexOf("\n    }", sod));
        assertThat(sodBody)
            .as("DF-4/F2-24: resolve on the user id first, falling back to the name only for legacy "
              + "rows — a name-only comparison is what let a requester approve their own change")
            .contains("getRequestedById")
            .contains("getChangeOwnerId");
    }

    // ── The schema's own guarantees ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the schema refuses a closed condition with no author, and a waiver with no reason")
    void theSchemaCarriesTheTwoGuaranteesThatMatter() {
        String sql = migration();
        // F2-103 wrote into the schema: "satisfied" with no author is the exact defect that finding
        // measured on change_requests, and this table exists to replace prose with accountability.
        assertThat(sql).contains("ck_cc_closure");
        assertThat(sql).contains("ck_cc_waiver_reason");
        assertThat(sql)
            .as("both language columns NOT NULL — house rule 7 in the schema, not only in @NotBlank, "
              + "because F2-30 measured what happens when it lives in prose alone")
            .containsPattern("text_en\\s+NVARCHAR\\(1000\\) NOT NULL")
            .containsPattern("text_ar\\s+NVARCHAR\\(1000\\) NOT NULL");
        assertThat(sql)
            .as("the register must ship empty — a register whose first rows are demo data teaches "
              + "the reader that conditions are decoration")
            .contains("must ship empty");
    }

    @Test
    @DisplayName("the entity is soft-deleted and tenant-scoped in its own right, unlike change_tasks")
    void theEntityCarriesItsOwnTenantColumn() {
        assertThat(ChangeCondition.class.getAnnotation(org.hibernate.annotations.Where.class))
            .as("soft deletes are platform-wide (@Where + is_deleted)")
            .isNotNull();
        assertThat(Stream.of(ChangeCondition.class.getDeclaredFields()).map(Field::getName).toList())
            .as("""
                organisation_id is carried explicitly. change_tasks scopes through its parent, which \
                turns the tenant-wide register read ("every open obligation in this organisation") \
                into a join on every call — and CLAUDE.md makes filtering organisation_id the \
                developer's responsibility on every query.""")
            .contains("organisationId", "changeRequestId", "textEn", "textAr",
                      "imposedByName", "closedByName", "closureNotes");
    }
}
