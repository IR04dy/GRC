package com.tss.grc.module.bcm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.tss.grc.module.bcm.entity.BiaQuantitative;
import com.tss.grc.module.bcm.repository.BiaQuantitativeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;

/**
 * F2-261 — the quantitative BIA's board sign-off stamped {@code board_approved_at} from the
 * server's clock and recorded <b>no actor at all</b>, while both lower tiers of the same ladder
 * record theirs ({@code bcmLeadReviewedBy} from the controller's actor, {@code cfoApproverName}
 * from {@code CurrentActor} since F2-223).
 *
 * <p>🔑 <b>The attribution quality decreased as the authority increased</b>, and the tier with none
 * was the board approval — the artefact an ISO 22301 or SAMA business-continuity reviewer reads.
 * F2-256's shape (a server-owned clock with nobody owning the actor beside it) at the top of a
 * chain rather than at its edge.
 *
 * <p>The column V384 adds exists so a four-eyes bar can be written at all. Before it there were
 * only two comparable parties out of three, which is why {@code BiaQuantitativeController}'s own
 * javadoc recorded the bar as <i>"not buildable today"</i> and rejected {@code BCM:APPROVE} as a
 * conversion target on the strength of <i>"nothing enforces that they are different people, so the
 * guard is the only separation there is."</i>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F2-261 · Quantitative BIA — the board signatory, and the four-eyes bar it makes possible")
class QuantitativeBiaFourEyesTest {

    private static final Path SERVICE = Path.of("src", "main", "java", "com", "tss", "grc",
            "module", "bcm", "service", "QuantitativeBiaService.java");
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "tss", "grc",
            "module", "bcm", "controller", "BiaQuantitativeController.java");
    private static final Path V384 = Path.of("src", "main", "resources", "db", "migration",
            "sqlserver", "V384__f261_board_approver_for_the_quantitative_bia.sql");
    private static final Path ENTITY = Path.of("src", "main", "java", "com", "tss", "grc",
            "module", "bcm", "entity", "BiaQuantitative.java");

    @Mock private BiaQuantitativeRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private QuantitativeBiaService service;

    private static String source(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath(), e);
        }
    }

    /**
     * Java with its comments removed — required, and it would have fired here: this service's own
     * javadoc quotes the sentence it retired and names every field the assertions ban, so a raw
     * token scan reads the epitaph as the corpse (F2-193, F2-198, F2-260). One left-to-right
     * alternation, never two sequential passes: at a {@code //} an inner {@code /*} can never open a
     * block, and at a block start a {@code //} cannot truncate it.
     */
    private static final Pattern COMMENT =
            Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String code(Path p) {
        return COMMENT.matcher(source(p)).replaceAll(" ");
    }

    /** The brace-balanced body of one method, so an assertion is scoped to the shape of its claim. */
    private static String body(Path p, String signatureFragment) {
        String src = code(p);
        int at = src.indexOf(signatureFragment);
        assertThat(at).as("method %s must exist in %s — a pattern that matches nothing passes "
                + "forever", signatureFragment, p.getFileName()).isGreaterThanOrEqualTo(0);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("unbalanced braces after " + signatureFragment);
    }

    /**
     * ⚠ The principal has to be a {@code GrcUserDetails}: {@code CurrentActor.username()} returns
     * null for any other principal type, so a plain-String fixture makes {@code requireUsername}
     * throw its no-actor refusal — which reads exactly like the four-eyes bar not firing. The
     * house idiom is copied from {@code RecertificationDecisionAttributionTest#signIn}.
     */
    private static void authenticateAs(String username) {
        com.tss.grc.security.GrcUserDetails u = new com.tss.grc.security.GrcUserDetails(
                7L, 1L, username, "x", true, true, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        com.tss.grc.common.tenant.TenantContext.setCurrentTenant(1L);
    }

    private BiaQuantitative approvedBia(String reviewer, String cfo) {
        BiaQuantitative h = new BiaQuantitative();
        h.setId(1L);
        h.setOrganisationId(1L);
        h.setExternalId(java.util.UUID.randomUUID());
        h.setAssessmentCode("BIA-Q-REVIEW-TEST-001");
        h.setStatus("CFO_APPROVED");
        h.setBcmLeadReviewedBy(reviewer);
        h.setCfoApproverName(cfo);
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the board approval stamps the authenticated principal, not nothing")
    void theBoardApprovalRecordsWhoAsWellAsWhen() {
        String b = body(SERVICE, "public BiaQuantitativeResponse boardApprove(");
        assertThat(b)
                .as("F2-261: this method stamped boardApprovedAt from the clock and recorded no "
                        + "actor at all, while both lower tiers of the same ladder record theirs. A "
                        + "board sign-off that records the moment and not the person is not an "
                        + "attribution (F2-256).")
                .contains("setBoardApprovedBy(actor)")
                .contains("CurrentActor.requireUsername(");
        assertThat(b)
                .as("the actor must not be reachable from the request — this method takes only a "
                        + "minutesRef, and it must stay that way (F2-223: an attribution the caller "
                        + "supplies is a claim, not a record)")
                .doesNotContain("req.");
        assertThat(b)
                .as("requireUsername, not username(): refusing is deliberately louder than storing "
                        + "a blank, because a board approval whose signatory is the empty string "
                        + "reads as an attribution (F2-236)")
                .doesNotContain("CurrentActor.username()");
    }

    @Test
    @DisplayName("the four-eyes bar is deliberately NOT enforced — it could only ever refuse")
    void theFourEyesBarIsWritableAndDeliberatelyNotWired() {
        String board = body(SERVICE, "public BiaQuantitativeResponse boardApprove(");
        String cfo = body(SERVICE, "public BiaQuantitativeResponse cfoApprove(");

        // ⚠⚠ The bar was written, probed and WITHDRAWN. Measured from the guards and
        // iam.user_roles in both databases: cfo-approve admits CFO + PLATFORM_ADMIN and
        // board-approve admits EXECUTIVE_REVIEWER + PLATFORM_ADMIN, and CFO and
        // EXECUTIVE_REVIEWER are held by NOBODY — they are the last two rows of §B2b. So the
        // only way to complete this chain today is `admin` taking both approvals, which is
        // exactly what the bar must refuse. Probed live with the bar wired: admin
        // board-approving what it had CFO-approved answered 409 — correct, and the only path
        // that works. F2-112's shape inverted: not a control that cannot fire, a control that
        // can only ever fire.
        assertThat(board)
                .as("F2-261: no self-approval refusal may be wired here until the guard lockout "
                        + "is closed, or the feature becomes uncompletable. This leg exists so the "
                        + "next reader does not add the bar without also closing §B2b — the "
                        + "missing column, the missing bar and the two zero-holder roles are ONE "
                        + "problem, and enforcing the middle one alone breaks the feature.")
                .doesNotContain("Segregation of duties");
        assertThat(cfo)
                .as("and not one stage lower either, for the same reason")
                .doesNotContain("Segregation of duties");

        // What DOES ship: the three parties are now comparable, which is the prerequisite
        // F2-254 said was missing. Held by the entity's field plus the stamp above.
        assertThat(code(ENTITY))
                .as("the column that makes the bar writable at all must exist — before it there "
                        + "were two comparable parties out of three, which is why the controller's "
                        + "javadoc recorded the bar as \"not buildable today\"")
                .contains("boardApprovedBy");
        assertThat(board)
                .as("and the third party must be recorded from the principal, or the comparison "
                        + "still has nothing to compare")
                .contains("setBoardApprovedBy(actor)");
    }

    @Test
    @DisplayName("all three tiers of the ladder now record an actor")
    void everyTierOfTheLadderRecordsItsActor() {
        assertThat(body(SERVICE, "public BiaQuantitativeResponse bcmLeadReview("))
                .as("stage 1 has always recorded its actor, from the controller's actor()")
                .contains("setBcmLeadReviewedBy(actor)");
        assertThat(body(SERVICE, "public BiaQuantitativeResponse cfoApprove("))
                .as("stage 2 has recorded its actor since F2-223 — and the claim that it took a "
                        + "caller-supplied name had been repeated into three artefacts without "
                        + "anybody opening this method (F2-192)")
                .contains("setCfoApproverName(CurrentActor.username())");
        assertThat(body(SERVICE, "public BiaQuantitativeResponse boardApprove("))
                .as("F2-261: stage 3 recorded neither, so the attribution quality DECREASED as the "
                        + "authority increased and the tier with none was the board sign-off")
                .contains("setBoardApprovedBy(actor)");
    }

    @Test
    @DisplayName("V384 mirrors the sibling columns, and asserts no tenant data")
    void theMigrationMirrorsItsSiblingsAndAssertsNoTenantData() {
        assertThat(Files.exists(V384))
                .as("F2-261: the column is what makes the bar writable at all — before it there "
                        + "were two comparable parties out of three")
                .isTrue();
        String sql = source(V384);
        assertThat(sql)
                .as("the mirror check is load-bearing: the bar compares all three parties as "
                        + "strings, so a narrower column could truncate one and make two different "
                        + "people compare equal")
                .contains("board_approved_by")
                .contains("cfo_approver_name")
                .contains("bcm_lead_reviewed_by")
                .contains("V384 check 2");
        assertThat(sql.split("THROW 51384", -1).length - 1)
                .as("all five self-checks present: 0 table exists, 0b the three siblings, 1 the "
                        + "column, 2 the mirror, 3 anti-vacuity. A count, not a contains (F2-258).")
                .isEqualTo(5);
        // F2-260's rule, enforced structurally rather than remembered.
        String executable = COMMENT.matcher(sql).replaceAll(" ");
        assertThat(executable)
                .as("a migration may assert its own writes and must not assert tenant data: a "
                        + "fresh install has no users and no rows, so a check over either would "
                        + "block the whole chain (F2-260)")
                .doesNotContain("iam.user_roles")
                .doesNotContain("iam.users");
        assertThat(executable)
                .as("and it must not refuse rows that already have a board approval with no "
                        + "approver — on a customer database that is a pre-fix approval to disclose "
                        + "(F2-209's third state), not a migration failure (F2-183)")
                .doesNotContain("board_approved_at IS NOT NULL");
    }

    @Test
    @DisplayName("the controller's recorded blocker is corrected where it was written")
    void theRecordedBlockerIsCorrectedInPlace() {
        String doc = source(CONTROLLER);
        assertThat(doc)
                .as("F2-261: the javadoc said the four-eyes bar was \"not buildable today\" and "
                        + "that \"the guard is the only separation there is\". Both were true when "
                        + "written and the first is now false, so the correction belongs where the "
                        + "claim is — a stale blocker is the input to somebody's next estimate "
                        + "(F2-178).")
                .contains("F2-261 — the blocker had two halves and one of them is gone");
        assertThat(doc)
                .as("and the half that is NOT closed must stay stated as an owner decision, or the "
                        + "next reader reads a discharged prerequisite as a discharged question")
                .contains("still an owner decision");
        assertThat(doc)
                .as("the stale CFO claim is corrected too: F2-223 had already fixed that half, and "
                        + "the false sentence had been repeated into three artefacts (F2-192)")
                .contains("half of that paragraph was already false");
    }
}
