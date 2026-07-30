package com.tss.grc.module.board_pack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tss.grc.module.board_pack.controller.BoardPackController;
import com.tss.grc.module.board_pack.dto.BoardPackDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-05 slice 5 — the guard that read as a narrow board audience and behaved as a wide one.
 *
 * <h3>What was wrong</h3>
 * <p>{@code BoardPackController}'s read guard was:
 * <pre>
 *   hasAnyRole('PLATFORM_ADMIN','CRO','BOARD_MEMBER','AUDIT_COMMITTEE_CHAIR',
 *              'AUDIT_COMMITTEE_MEMBER','COMPANY_SECRETARY')
 *   or hasAuthority('REPORTING_READ')
 * </pre>
 * and the comment above it said reads "must be gated to a board/governance audience, not any
 * authenticated principal". <b>Five of the six role names do not exist in {@code iam.roles}</b> —
 * CRO, BOARD_MEMBER, AUDIT_COMMITTEE_CHAIR, AUDIT_COMMITTEE_MEMBER, COMPANY_SECRETARY — and a
 * role guard never reaches {@code ModulePermissionEvaluator}, so it gets no PLATFORM_ADMIN bypass
 * either. The entire board-audience half was inert. Everyone who actually got in came through the
 * trailing {@code hasAuthority('REPORTING_READ')} leg: <b>REPORTING:READ is held by 20 roles
 * including VIEWER</b>, the generic read-only persona the comment excludes by name.
 *
 * <p><b>This is the inverse of the usual F2-52 finding.</b> Elsewhere the ghost roles left an
 * endpoint admin-only by attrition; here they left a wide guard <em>looking</em> narrow. Reading
 * the source, a reviewer sees six board roles and stops. Live before the fix: {@code viewer} GET
 * {@code /api/board-pack/packs} returned <b>200 with all 16 packs</b> — every audience
 * (BOARD, AUDIT_COMMITTEE, RISK_COMMITTEE, CYBER_COMMITTEE, EXEC) and every status, including
 * the 2 PUBLISHED and 7 APPROVED ones.
 *
 * <h3>Why ADMIN, and why no migration</h3>
 * <p>ADMIN is the module that already owns governance administration — the board secretariat and
 * committees. {@code ADMIN:READ} is 21 roles and {@code ADMIN:WRITE} is exactly
 * {BOARD_SECRETARY, GRC_ADMINISTRATOR, ORG_ADMIN}; both are already granted, so like slice 4 this
 * needs no migration. The write tier is the evidence-led half: <b>4 of the 16 existing packs
 * record {@code drafted_by='board.secretary@tss.com.sa'}</b>, a persona the old guard could not
 * have admitted — REPORTING:WRITE is only GRC_ADMINISTRATOR and ORG_ADMIN, and the intended
 * executive author was the ghost {@code CRO}.
 *
 * <p>⚠ <b>Not a strict narrowing, and the test says so.</b> VIEWER, EMERGENCY_RESPONDER and
 * MA_LEAD lose read; CRISIS_MANAGER, LEGAL_COUNSEL, RISK_MANAGER and SUPPLIER_MANAGER gain it.
 * RISK_MANAGER gaining it is a fix — a risk manager was 403 on a RISK_COMMITTEE pack.
 * SUPPLIER_MANAGER gaining it is the cost of using a catalogued tier instead of inventing one:
 * <b>no catalogued permission's population is "the board"</b>, and choosing the grant list for a
 * new BOARD module is a product decision, not a fix. Recorded in the plan, not guessed here.
 *
 * @see com.tss.grc.security.RoleGuardConversionRatchetTest the census and the platform-wide ratchet
 */
class BoardPackGuardTest {

    private static final String ADMIN_READ = "hasPermission('ADMIN','READ')";
    private static final String ADMIN_WRITE = "hasPermission('ADMIN','WRITE')";

    /** The five names that cannot be granted, so naming them guarded nothing. */
    private static final List<String> GHOSTS = List.of(
        "CRO", "BOARD_MEMBER", "AUDIT_COMMITTEE_CHAIR", "AUDIT_COMMITTEE_MEMBER",
        "COMPANY_SECRETARY");

    private static Map<String, String> guards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : BoardPackController.class.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
            if (!mapped) continue;
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    @Test
    void readsAuthoriseOnAdminRead() {
        Map<String, String> g = guards();
        assertThat(g).containsEntry("list", ADMIN_READ).containsEntry("get", ADMIN_READ);
    }

    @Test
    void writesAuthoriseOnAdminWrite() {
        Map<String, String> g = guards();
        assertThat(g)
            .as("create and transition must be on ADMIN:WRITE, which is what admits BOARD_SECRETARY "
              + "— the drafter 4 of the 16 existing packs already name")
            .containsEntry("create", ADMIN_WRITE)
            .containsEntry("transition", ADMIN_WRITE);
    }

    /**
     * The heart of the slice. A guard that recites a role nobody can hold does not merely fail to
     * admit that role — it misrepresents the guard's breadth to everyone who reads the source.
     */
    @Test
    void noGuardRecitesARoleThatCannotBeGranted() {
        guards().forEach((endpoint, guard) ->
            GHOSTS.forEach(ghost ->
                assertThat(guard)
                    .as("%s recites %s, absent from iam.roles — it can never be granted, so it "
                      + "narrows the guard's appearance without narrowing its population",
                      endpoint, ghost)
                    .doesNotContain(ghost)));
    }

    /**
     * The specific trap: {@code REPORTING_READ} as a fallback authority is what made the guard
     * wide, because REPORTING:READ includes VIEWER. If it comes back, so does the exposure.
     */
    @Test
    void readsDoNotFallBackToReportingRead() {
        Map<String, String> g = guards();
        for (String read : List.of("list", "get")) {
            assertThat(g.get(read))
                .as("%s must not admit REPORTING:READ — 20 roles hold it, including VIEWER, the "
                  + "generic read-only persona this endpoint's own comment rules out", read)
                .doesNotContain("REPORTING_READ")
                .doesNotContain("REPORTING','READ");
        }
    }

    /**
     * Delete is PLATFORM_ADMIN-only on purpose and must stay a role guard: the ADMIN module
     * catalogues only APPROVE/EXPORT/READ/WRITE, so there is no ADMIN:DELETE to convert to
     * (measured: zero holders). Pinned so a future sweep does not "fix" it into existence.
     */
    @Test
    void deleteStaysPlatformAdminOnlyByChoice() {
        assertThat(guards().get("delete")).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    /**
     * F2-22b(vi) stamped reviewedBy/approvedBy from the principal and left {@code draftedBy}
     * bindable on create. READ_ONLY (not {@code @JsonIgnore}) so it still serialises out.
     */
    @Test
    void draftedByIsNotBindableFromTheRequestBody() throws Exception {
        Field f = BoardPackDto.class.getDeclaredField("draftedBy");
        JsonProperty jp = f.getAnnotation(JsonProperty.class);
        assertThat(jp)
            .as("draftedBy must carry @JsonProperty so Jackson refuses to bind it inbound")
            .isNotNull();
        assertThat(jp.access())
            .as("READ_ONLY keeps the drafter in responses while making it unforgeable on create")
            .isEqualTo(JsonProperty.Access.READ_ONLY);
    }

    /**
     * V331 — the audience vocabulary must be able to express what the table already holds.
     *
     * <p>Before V331 the pattern was {@code BOARD|AUDIT_COMMITTEE|RISK_COMMITTEE|EXEC}: four values
     * against five stored ones and seventeen real {@code committee_type}s. One pack held
     * {@code CYBER_COMMITTEE} — <b>a record this API could not recreate</b> — and neither
     * {@code CYBER_COMMITTEE} nor {@code EXEC} matched any committee, so joining audience to committee
     * resolved 3 of 5 and silently mis-scoped the other 2.
     *
     * <p>This pins both directions: the aligned spellings are accepted, and the two drifted ones are
     * refused so a well-meaning revert cannot reintroduce a value that resolves to no committee.
     */
    @Test
    void theAudienceVocabularyMatchesTheCommitteeVocabulary() throws Exception {
        java.lang.reflect.Field f = BoardPackDto.class.getDeclaredField("audience");
        jakarta.validation.constraints.Pattern pat =
            f.getAnnotation(jakarta.validation.constraints.Pattern.class);
        assertThat(pat).as("audience must stay validated").isNotNull();
        java.util.regex.Pattern re = java.util.regex.Pattern.compile(pat.regexp());

        // Every audience actually present in the table must be expressible.
        for (String aud : List.of("BOARD", "AUDIT_COMMITTEE", "RISK_COMMITTEE",
                                  "EXEC_COMMITTEE", "CYBER_SECURITY")) {
            assertThat(re.matcher(aud).matches())
                .as("audience '%s' exists in board_pack.packs and is a real committee_type, so the "
                  + "API must accept it — otherwise a stored pack cannot be recreated", aud)
                .isTrue();
        }
        // The two pre-V331 spellings resolve to no committee and must not come back.
        for (String dead : List.of("CYBER_COMMITTEE", "EXEC")) {
            assertThat(re.matcher(dead).matches())
                .as("'%s' matches no admin.committees.committee_type; accepting it would let a pack be "
                  + "created that an audience-scoped read filter cannot place (V331)", dead)
                .isFalse();
        }
    }

    @Test
    void everyEndpointStillCarriesAGuard() {
        Map<String, String> g = guards();
        assertThat(g.values())
            .as("board packs are the most sensitive aggregate in the platform; an unguarded "
              + "endpoint here is readable by any authenticated caller")
            .doesNotContain("<NO GUARD>");
        // Anti-vacuity: a reflection scan that resolves nothing passes every assertion above.
        assertThat(g).hasSize(5)
            .containsKeys("list", "get", "create", "transition", "delete");
    }
}
