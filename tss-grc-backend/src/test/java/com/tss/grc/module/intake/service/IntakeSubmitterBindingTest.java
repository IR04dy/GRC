package com.tss.grc.module.intake.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.dto.IntakeRecordDto;
import com.tss.grc.module.intake.dto.IntakeSubmissionRequest;
import com.tss.grc.module.intake.dto.TriageDecisionRequest;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.PromotionRegistry;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-172 — the half that makes {@code isAuthenticated()} on {@code POST /api/v1/intake} safe.
 *
 * <p>{@code IntakeSubmitGuardTest} pins the open guard. This pins the three properties without which
 * that guard would be a defect rather than a fix, and it is written so that each fails
 * <em>unconditionally</em> if the service goes back to trusting the request body:
 *
 * <ol>
 *   <li><b>The submitter is the caller.</b> The body's identity fields are ignored, not validated —
 *       {@code CoiService#declarantFor}'s reasoning: rejecting them would leak whether another
 *       identity exists, and a self-report has no business carrying one.</li>
 *   <li><b>An anonymity claim is honoured only where the catalogue allows it.</b> This is the
 *       property with no COI counterpart and the easiest to lose in a refactor. If the server simply
 *       obeyed {@code submitterIsAnonymous}, any caller could make <em>any</em> submission
 *       unattributable on request — the open guard would then be exactly the impersonation path the
 *       binding exists to prevent, entered through the field that exists to protect whistleblowers.</li>
 *   <li><b>Where anonymity IS honoured, nothing identifying is stored.</b> Not the user id, and not
 *       the name/email/phone the body may still carry. A record that quietly retains a
 *       whistleblower's identity is worse than one that never offered to hide it (ISO 37001 §8.3,
 *       Nazaha).</li>
 * </ol>
 *
 * <p>Three further tests cover the promotion target, which is the same class of defect one field
 * over: the triage decision recorded {@code promotedToRecordId} from the client while creating
 * nothing. Two of them were rewritten where this slice met the half that makes promotion actually
 * write the register record — see their javadoc for which premise stopped holding, and why a
 * rewrite was right where deleting them would have dropped the property.
 */
class IntakeSubmitterBindingTest {

    private IntakeRecordRepository repo;
    private IntakeService service;

    private static final long ORG = 1L;
    private static final long CALLER_ID = 77L;
    private static final String CALLER = "reporter@tss.sa";

    @BeforeEach
    void setUp() {
        repo = mock(IntakeRecordRepository.class);
        when(repo.save(any(IntakeRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new IntakeService(repo,
            new PromotionRegistry(List.of(new StubPort("DPIA", "12"), new StubPort("RISK", "907"))),
            ALWAYS_PERMITTED,                   // authorisation is IntakePromotionServiceTest's subject
            new ObjectMapper());
        TenantContext.setCurrentTenant(ORG);
        authenticateAs(CALLER_ID, CALLER);
    }

    /** Stands in for a real adapter: answers with the id of the row it would have written. */
    private record StubPort(String target, String id) implements RegisterPromotionPort {
        @Override public String requiredPermission() { return "PRIVACY:WRITE"; }
        @Override public String promote(IntakePromotionContext ctx) { return id; }
    }

    private static final PermissionEvaluator ALWAYS_PERMITTED = new PermissionEvaluator() {
        @Override public boolean hasPermission(Authentication a, Object target, Object permission) {
            return true;
        }
        @Override public boolean hasPermission(Authentication a, Serializable id, String t, Object p) {
            return true;
        }
    };

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static void authenticateAs(Long userId, String username) {
        GrcUserDetails principal = new GrcUserDetails(userId, ORG, username, "n/a", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
    }

    /** A submission that names somebody else in every identity field the DTO carries. */
    private static IntakeSubmissionRequest impersonating(String kind, boolean claimAnonymous) {
        IntakeSubmissionRequest req = new IntakeSubmissionRequest();
        req.setKind(kind);
        req.setChannel("WEB_FORM");
        req.setSubmitterIsAnonymous(claimAnonymous);
        req.setSubmitterName("Somebody Else");
        req.setSubmitterEmail("somebody.else@tss.sa");
        req.setSubmitterPhone("+966500000000");
        req.setSubmitterRole("EMPLOYEE");
        req.setPayload("{\"summary\":\"[REVIEW-TEST] binding probe\"}");
        return req;
    }

    private IntakeRecord saved() {
        ArgumentCaptor<IntakeRecord> cap = ArgumentCaptor.forClass(IntakeRecord.class);
        verify(repo).save(cap.capture());
        return cap.getValue();
    }

    // ── 1. the submitter is the caller ──────────────────────────────────────────────────────────

    @Test
    void theSubmitterIsTakenFromTheSecurityContextNotTheBody() {
        service.submit(impersonating("SECURITY_INCIDENT", false));
        IntakeRecord r = saved();
        assertThat(r.getSubmitterUserId())
            .as("submitter_user_id must be the authenticated caller. Taking it (or leaving it null) "
                + "while the guard is isAuthenticated() means anyone can file a report attributed to "
                + "nobody, or to whoever the body names — the impersonation path ADR-010 condition 2 "
                + "exists to close.")
            .isEqualTo(CALLER_ID);
        assertThat(r.getOrganisationId()).isEqualTo(ORG);
    }

    /**
     * ⚠ Added after the fact, and it is the assertion that would have caught the defect the two
     * checks above could not.
     *
     * <p>Every test in this class reads the entity captured at {@code repo.save}, and the live
     * probe read the column with pyodbc. Both were green over an {@code IntakeRecordDto.from()}
     * that <b>never copied {@code submitterUserId}</b> — so every HTTP response reported the
     * submitter as {@code null}, identified or not. A field that always answers "nobody" is
     * F2-159's decoy shape: present, consulted, and measuring something else.
     *
     * <p>🔑 The transferable point: <em>assert on the artefact the consumer receives.</em> The
     * entity and the column are intermediate representations, and a mapper sits between them and
     * every caller.
     */
    @Test
    void theResponseCarriesTheBoundSubmitter() {
        IntakeRecordDto dto = service.submit(impersonating("SECURITY_INCIDENT", false));
        assertThat(dto.getSubmitterUserId())
            .as("IntakeRecordDto.from() must copy submitterUserId. Without it every response says "
                + "the submission was filed by nobody, and no caller can tell an identified "
                + "submission from an anonymous one.")
            .isEqualTo(CALLER_ID);
        assertThat(dto.getSubmitterIsAnonymous()).isFalse();
    }

    @Test
    void theResponseForAnAnonymousSubmissionCarriesNoSubmitter() {
        IntakeRecordDto dto = service.submit(impersonating("SPEAKUP", true));
        assertThat(dto.getSubmitterUserId())
            .as("the wire format must not disclose the reporter either — the DTO is what a triager's "
                + "browser receives")
            .isNull();
        assertThat(dto.getSubmitterName()).isNull();
        assertThat(dto.getSubmitterEmail()).isNull();
        assertThat(dto.getSubmitterIsAnonymous()).isTrue();
    }

    /**
     * The identified path keeps the body's contact fields on purpose — an employee reporting on behalf
     * of a walk-in customer supplies that customer's details — and this test states the boundary that
     * makes it safe: the row is <em>attributed</em> to a server-chosen id, so a mislabelled name is a
     * data-quality problem against a known reporter, never a report attributed to someone who never
     * made it. Same bounded compromise CoiService documents.
     */
    @Test
    void theBodysContactFieldsSurviveButDoNotDecideAttribution() {
        service.submit(impersonating("SECURITY_INCIDENT", false));
        IntakeRecord r = saved();
        assertThat(r.getSubmitterName()).isEqualTo("Somebody Else");
        assertThat(r.getSubmitterUserId())
            .as("the name is descriptive; the id is the attribution, and they must be allowed to differ")
            .isEqualTo(CALLER_ID);
    }

    // ── 2. an anonymity claim is honoured only where the catalogue allows it ─────────────────────

    @Test
    void anAnonymityClaimOnAKindThatDoesNotAllowItIsRefused() {
        // SECURITY_INCIDENT: allowsAnonymous = false in both catalogues.
        assertThat(IntakeKindRouting.get("SECURITY_INCIDENT").allowsAnonymous()).isFalse();

        service.submit(impersonating("SECURITY_INCIDENT", true));
        IntakeRecord r = saved();
        assertThat(r.getSubmitterIsAnonymous())
            .as("the server must not obey an anonymity claim on a kind whose catalogue entry refuses "
                + "it — otherwise every submission can be made unattributable on request and the "
                + "binding above is decorative")
            .isFalse();
        assertThat(r.getSubmitterUserId())
            .as("a refused anonymity claim must still bind the caller")
            .isEqualTo(CALLER_ID);
    }

    @Test
    void anAnonymityClaimOnAWhistleblowingKindIsHonoured() {
        assertThat(IntakeKindRouting.get("SPEAKUP").allowsAnonymous()).isTrue();

        service.submit(impersonating("SPEAKUP", true));
        IntakeRecord r = saved();
        assertThat(r.getSubmitterIsAnonymous()).isTrue();
    }

    // ── 3. an honoured anonymous submission stores nothing identifying ──────────────────────────

    @Test
    void anHonouredAnonymousSubmissionStoresNoIdentityAtAll() {
        service.submit(impersonating("SPEAKUP", true));
        IntakeRecord r = saved();
        assertThat(r.getSubmitterUserId())
            .as("recording the user id of a whistleblower who asked for anonymity defeats the channel "
                + "while appearing to improve the audit trail (ISO 37001 §8.3, Nazaha)")
            .isNull();
        assertThat(r.getSubmitterName()).isNull();
        assertThat(r.getSubmitterEmail())
            .as("the body still carried an email; an honoured anonymous submission must drop it "
                + "rather than persist it unread")
            .isNull();
        assertThat(r.getSubmitterPhone()).isNull();
    }

    @Test
    void theEventTrailOfAnAnonymousSubmissionNamesNobody() {
        service.submit(impersonating("SPEAKUP", true));
        IntakeRecord r = saved();
        assertThat(r.getHistory()).isNotEmpty();
        assertThat(r.getHistory())
            .as("an event actor that leaks the authenticated username re-identifies the reporter in "
                + "the one place a triager always reads")
            .allSatisfy(e -> assertThat(e.getActor()).isNotEqualTo(CALLER));
        assertThat(r.getHistory().get(0).getActor()).isEqualTo("anonymous");
    }

    @Test
    void anIdentifiedSubmissionsEventTrailNamesTheCaller() {
        service.submit(impersonating("SECURITY_INCIDENT", false));
        IntakeRecord r = saved();
        assertThat(r.getHistory().get(0).getActor())
            .as("the SUBMITTED event must name the authenticated caller, not the body's email — "
                + "otherwise the audit trail records the claim rather than the act")
            .isEqualTo(CALLER);
    }

    // ── 4. the promotion target is server truth ─────────────────────────────────────────────────

    /**
     * The same defect one field over. {@code triage()} stored {@code promotedToRecordId} from the
     * body and created no downstream record, so the id could only ever be invented — F2-171 found the
     * client minting {@code Date.now() % 1_000_000}, a positive integer inside the range real
     * IDENTITY values occupy.
     *
     * <p>⚠ <b>This test was rewritten when the two halves of F2-172 met.</b> It used to set the two
     * fields on the request and assert the server ignored them, and that the stamped id stayed NULL
     * because nothing created the row. Both halves of that premise are now false: the fields are
     * gone from the DTO, and the id is the primary key of a record that exists. An ignored field is
     * still an advertised one — it appears in the API contract and invites a client to send it — so
     * removing it is the stronger form of the same property, and this asserts it by reflection
     * rather than by trying to call setters that no longer compile.
     */
    @Test
    void theClientCannotExpressAPromotionTarget() {
        assertThat(TriageDecisionRequest.class.getDeclaredFields())
            .extracting(java.lang.reflect.Field::getName)
            .as("TriageDecisionRequest must carry no promotedTo* field. The client naming the target "
                + "register, or the row it lands in, is the defect F2-172 closed — and Jackson drops "
                + "unknown properties silently, so a re-added field would be obeyed without any "
                + "caller having to change.")
            .doesNotContain("promotedToEntityType", "promotedToRecordId");
    }

    /**
     * The target register comes from the kind, and the id comes from the write. Neither is
     * expressible by the caller (above), so this pins the positive half: a DPIA_INITIATION intake
     * promotes to DPIA, and the stamped id is the one the port returned — not a constant, and not a
     * number the service invented.
     */
    @Test
    void triageDerivesTheTargetFromTheKindAndTheIdFromTheWrite() {
        IntakeRecord existing = new IntakeRecord();
        existing.setOrganisationId(ORG);
        existing.setReferenceCode("DI-K7L9-Z4M2");
        existing.setKind("DPIA_INITIATION");
        existing.setStatus("TRIAGING");
        when(repo.findById(5L)).thenReturn(Optional.of(existing));

        TriageDecisionRequest req = new TriageDecisionRequest();
        req.setDecision("PROMOTE");
        service.triage(5L, req, "triager@tss.sa");

        assertThat(existing.getPromotedToEntityType())
            .as("the target register is a property of the intake kind — DPIA_INITIATION promotes to "
                + "DPIA, and no request body can say otherwise")
            .isEqualTo("DPIA");
        assertThat(existing.getPromotedToRecordId())
            .as("the stamped id must be the one the port returned — the id of the row that was "
                + "actually written. A value from anywhere else is the F2-171 defect, durable.")
            .isEqualTo("12");
        assertThat(existing.getStatus()).isEqualTo("PROMOTED");
    }

    /**
     * The note a triager and an auditor actually read. It said {@code "Promoted to DPIA #482913"} —
     * a permanent audit note naming a record that existed nowhere, and at a measured 0.056% of
     * promotions in dev, naming a real unrelated one.
     *
     * <p>⚠ Also rewritten at the seam. Master's half made the note say the target did <em>not</em>
     * exist yet, which was the honest thing to write while nothing created it. Now that promotion
     * writes the row, the honest note is the opposite: it names the id, and the id resolves.
     */
    @Test
    void thePromotionNoteNamesTheRecordThatWasCreated() {
        IntakeRecord existing = new IntakeRecord();
        existing.setOrganisationId(ORG);
        existing.setReferenceCode("RI-A1B2-C3D4");
        existing.setKind("RISK_ISSUE");
        existing.setStatus("TRIAGING");
        when(repo.findById(9L)).thenReturn(Optional.of(existing));

        TriageDecisionRequest req = new TriageDecisionRequest();
        req.setDecision("PROMOTE");
        service.triage(9L, req, "triager@tss.sa");

        String note = existing.getHistory().get(existing.getHistory().size() - 1).getNote();
        assertThat(note)
            .as("the promotion note is the permanent record of the decision, read by people who "
                + "cannot check whether the target exists. It must name the register and the id that "
                + "was written — and that id must be the port's, so the note cannot drift from the "
                + "column beside it.")
            .isEqualTo("Promoted to RISK #907");
        assertThat(note).contains(existing.getPromotedToRecordId());
    }
}
