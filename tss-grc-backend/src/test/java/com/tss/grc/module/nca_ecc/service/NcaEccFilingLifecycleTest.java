package com.tss.grc.module.nca_ecc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.nca_ecc.dto.NcaEccFilingDto;
import com.tss.grc.module.nca_ecc.entity.NcaEccFiling;
import com.tss.grc.module.nca_ecc.repository.NcaEccFilingRepository;
import com.tss.grc.testsupport.StateMachineContract;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-03(b) — the Tier-1 floor for {@code nca_ecc}, the annual NCA ECC filing. Second in the row's
 * order, and the smallest of the seven at 364 LoC across one controller, entity, service and DTO.
 *
 * <h3>⚠ This module is why {@link StateMachineContract} exists</h3>
 * The loop written for {@code outsourcing} derived its state universe from {@code ALLOWED.keySet()}.
 * Here that is <b>wrong</b>: the map has four keys — {@code DRAFT}, {@code PEER_REVIEW},
 * {@code CISO_APPROVAL}, {@code SUBMITTED} — while six states exist, because {@code ACKNOWLEDGED}
 * and {@code CANCELLED} appear only as <em>targets</em>. Under the original loop the two states that
 * actually end an NCA filing would have been tested neither as a source nor as a target, and the
 * test would have reported green over unverified terminal behaviour. It held for {@code outsourcing}
 * only because all four of that module's maps happen to declare terminal states as explicit
 * {@code Set.of()} entries — accidentally, not by design.
 *
 * <h3>The assurance chain: F2-117's two halves, one of them now server-owned</h3>
 * This filing has a three-step assurance chain — drafted, peer-reviewed, CISO-approved. Until
 * F2-225 <b>all three names were strings taken from the request body</b>, with no comparison between
 * them and no reference to the security context, so one caller could drive
 * {@code DRAFT → PEER_REVIEW → CISO_APPROVAL → SUBMITTED} while typing every name, and the filing
 * that reaches the regulator recorded a four-eyes review that never happened.
 *
 * <p>Two separable defects were filed as one, and that is why half of it stayed open:
 * <ul>
 *   <li><b>ATTRIBUTION</b> — who the drafter and CISO approver ARE. <b>CLOSED</b>: both are now
 *       {@code CurrentActor.username()}, and the service refuses when there is no principal. It
 *       needed no migration and no new column. Found independently by F2-225's split-attribution
 *       re-derivation, because the CISO_APPROVAL branch stamps {@code cisoApprovalAt} from the
 *       server's own clock two lines above — one attribution, two owners (F2-223).</li>
 *   <li><b>SEGREGATION</b> — that the approver is not the peer reviewer. <b>STILL OPEN</b>, and its
 *       blocker is genuine: no {@code *_user_id} column exists for either assurance role, so a
 *       refusal over these strings would answer "cannot tell" every time and refuse nothing — the
 *       shipped-but-inert control this programme keeps finding. V357's shape is the remedy.</li>
 * </ul>
 *
 * <p>🔑 <b>The recorded blocker was an argument about segregation, and it was applied to
 * attribution.</b> This javadoc used to reason that a comparison would be inert because 0 of the 4
 * people-labels resolved to a platform user — correct, and irrelevant to stamping the principal.
 * Worse, the dependency ran the other way: comparing a reviewer against an approver is meaningless
 * while the approver is whatever the caller typed, so attribution was the PREREQUISITE for the
 * comparison the blocker was protecting. F2-178's "re-measure a recorded blocker before believing
 * its estimate", one level up — the estimate was right about a fix that was not the one blocked.
 *
 * <p>⚙ One input to the old measurement has moved: {@code ciso_approver} now holds a username,
 * which does resolve to a platform user, so "0 of 4 labels resolve" no longer describes it.
 *
 * <p>The peer reviewer stays caller-supplied deliberately: at PEER_REVIEW entry the reviewer is
 * being NOMINATED, not recording a review they have performed, and {@code peerReviewCompletedAt} is
 * stamped later without touching the name. Assignment-then-completion is a legitimate two-step
 * (F2-222); stamping the principal there would record a review nobody performed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NcaEccFilingLifecycleTest {

    private static final Long ORG = 1L;
    private static final String EXT = "NCA-2026-001";

    @Mock private NcaEccFilingRepository repo;

    @InjectMocks private NcaEccFilingService svc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // F2-225: the drafter and the CISO approver are the authenticated principal, and the
        // service REFUSES without one — so every test that creates or CISO-approves a filing
        // needs a principal. That refusal is the fix, not an inconvenience: a regulator
        // self-assessment with no named approver is not a filing.
        authAs("ciso.office", 77L);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private static void authAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, ORG, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

    // ── the machine ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Every declared transition is permitted and every undeclared pair refused, over all six states")
    void filingMachineHonoursItsOwnMap() {
        StateMachineContract.Result r = StateMachineContract.assertHonoursItsMap(
            allowedMap(),
            state -> when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT)))
                .thenReturn(Optional.of(filing(state))),
            to -> svc.transition(EXT, transitionTo(to)),
            "Illegal transition",
            "NcaEccFiling");

        // The point of this module for the shared harness: the universe is larger than the key set.
        assertThat(r.states()).containsExactlyInAnyOrder(
            "DRAFT", "PEER_REVIEW", "CISO_APPROVAL", "SUBMITTED", "ACKNOWLEDGED", "CANCELLED");
        assertThat(allowedMap().keySet()).as(
            "if ACKNOWLEDGED/CANCELLED ever become keys this test's whole reason for existing "
            + "changes, so it should be read again rather than quietly still passing")
            .containsExactlyInAnyOrder("DRAFT", "PEER_REVIEW", "CISO_APPROVAL", "SUBMITTED");
        assertThat(r.permitted()).as("the map declares nine legal transitions").isEqualTo(9);
    }

    @Test
    @DisplayName("A terminal filing refuses everything, including a repeat of its own terminal state")
    void acknowledgedAndCancelledAreTerminal() {
        for (String terminal : new String[] {"ACKNOWLEDGED", "CANCELLED"}) {
            for (String to : allStates()) {
                if (to.equals(terminal)) {
                    continue;
                }
                when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT)))
                    .thenReturn(Optional.of(filing(terminal)));
                assertThatThrownBy(() -> svc.transition(EXT, transitionTo(to)))
                    .as("%s -> %s must be refused", terminal, to)
                    .isInstanceOf(IllegalTransitionException.class);
            }
        }
    }

    @Test
    @DisplayName("The refusal names the states and the permitted set, so the client can act on it")
    void theRefusalIsActionable() {
        when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT)))
            .thenReturn(Optional.of(filing("DRAFT")));

        assertThatThrownBy(() -> svc.transition(EXT, transitionTo("SUBMITTED")))
            .isInstanceOf(IllegalTransitionException.class)
            .satisfies(ex -> {
                IllegalTransitionException it = (IllegalTransitionException) ex;
                assertThat(it.getFrom()).isEqualTo("DRAFT");
                assertThat(it.getTo()).isEqualTo("SUBMITTED");
                assertThat(it.getAllowed()).containsExactlyInAnyOrder("PEER_REVIEW", "CANCELLED");
            });
    }

    // ── the side effects each transition is responsible for ──────────────────

    @Test
    @DisplayName("Each transition stamps its own moment, and PEER_REVIEW's start is not overwritten on rework")
    void transitionsStampTheirOwnTimestamps() {
        NcaEccFiling f = filing("DRAFT");
        when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT))).thenReturn(Optional.of(f));

        svc.transition(EXT, transitionTo("PEER_REVIEW"));
        assertThat(f.getPeerReviewStartedAt()).isNotNull();

        svc.transition(EXT, transitionTo("CISO_APPROVAL"));
        assertThat(f.getPeerReviewCompletedAt()).isNotNull();
        assertThat(f.getCisoApprovalAt()).isNotNull();

        // CISO_APPROVAL -> PEER_REVIEW is the declared rework edge. The original start moment must
        // survive it: an assurance record that resets its own clock on rework cannot evidence how
        // long review actually took.
        //
        // ⚠ Backdated to a FIXED instant rather than compared against the value the first transition
        // wrote. Mutation testing caught the difference: replacing the guarded assignment with an
        // unconditional `f.setPeerReviewStartedAt(now)` did NOT fail the earlier version of this
        // assertion, because two LocalDateTime.now() calls inside one Windows clock tick return the
        // same value. A timestamp assertion that can only fail when the clock happens to advance is
        // not an assertion.
        java.time.LocalDateTime backdated = java.time.LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        f.setPeerReviewStartedAt(backdated);
        svc.transition(EXT, transitionTo("PEER_REVIEW"));
        assertThat(f.getPeerReviewStartedAt())
            .as("rework must not reset the original peer-review start")
            .isEqualTo(backdated);

        svc.transition(EXT, transitionTo("CISO_APPROVAL"));
        svc.transition(EXT, transitionTo("SUBMITTED"));
        assertThat(f.getSubmittedAt()).isNotNull();
        svc.transition(EXT, transitionTo("ACKNOWLEDGED"));
        assertThat(f.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("CANCELLED records why, from any non-terminal state")
    void cancellationRecordsItsReason() {
        for (String from : new String[] {"DRAFT", "PEER_REVIEW", "CISO_APPROVAL"}) {
            NcaEccFiling f = filing(from);
            when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT))).thenReturn(Optional.of(f));
            NcaEccFilingDto.TransitionRequest req = transitionTo("CANCELLED");
            req.setCancellationReasonEn("superseded by the 2027 filing");
            svc.transition(EXT, req);
            assertThat(f.getStatus()).isEqualTo("CANCELLED");
            assertThat(f.getCancelledAt()).as("%s -> CANCELLED must stamp the moment", from).isNotNull();
            assertThat(f.getCancellationReasonEn()).isEqualTo("superseded by the 2027 filing");
        }
    }

    @Test
    @DisplayName("capaCount is derived from the linked ids, never trusted from the caller")
    void capaCountIsDerivedNotAccepted() {
        NcaEccFiling f = filing("PEER_REVIEW");
        when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT))).thenReturn(Optional.of(f));

        NcaEccFilingDto.TransitionRequest req = transitionTo("CISO_APPROVAL");
        req.setLinkedCapaIds("11,22,33");
        svc.transition(EXT, req);

        assertThat(f.getLinkedCapaIds()).isEqualTo("11,22,33");
        assertThat(f.getCapaCount()).as("three ids means three CAPAs, counted not declared").isEqualTo(3);
    }

    // ── the domain scoring, which is the filing's actual output ──────────────

    @Test
    @DisplayName("The overall score is the mean of the domain scores that were answered, to 2dp")
    void overallScoreAveragesOnlyTheAnsweredDomains() {
        // All five, exact.
        assertThat(created(5, 4, 3, 2, 1).getOverallScore()).isEqualByComparingTo(new BigDecimal("3.00"));
        // Three answered, two unanswered: the mean is over 3, not over 5. Dividing by 5 would
        // silently understate maturity on a partially-completed filing.
        assertThat(created(5, 4, 3, null, null).getOverallScore())
            .isEqualByComparingTo(new BigDecimal("4.00"));
        // HALF_UP at 2dp: 5+4 over 2 is exact; 4+3+3 over 3 is 3.33...
        assertThat(created(4, 3, 3, null, null).getOverallScore())
            .isEqualByComparingTo(new BigDecimal("3.33"));
        // Nothing answered: no score, rather than a zero that reads as "assessed and failing".
        assertThat(created(null, null, null, null, null).getOverallScore()).isNull();
    }

    // ── creation defaults ────────────────────────────────────────────────────

    @Test
    @DisplayName("A new filing starts DRAFT on the current framework, with a derived reference")
    void creationDefaults() {
        NcaEccFilingDto req = new NcaEccFilingDto();
        req.setFiscalYear(2027);
        NcaEccFilingDto out = svc.create(req);

        assertThat(out.getStatus()).isEqualTo("DRAFT");
        assertThat(out.getFrameworkVersion()).isEqualTo("ECC-2:2024");
        assertThat(out.getId()).isEqualTo("NCA-2027-001");
    }

    // ── tenancy ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Every read and write is scoped to the caller's organisation")
    void everyLookupIsOrganisationScoped() {
        when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.transition(EXT, transitionTo("PEER_REVIEW")))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> svc.get(EXT)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> svc.softDelete(EXT)).isInstanceOf(EntityNotFoundException.class);

        // A filing that belongs to another tenant is Not Found, not Forbidden and not returned —
        // the org id is part of the lookup rather than a check after it.
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.atLeastOnce())
            .findByOrganisationIdAndExternalId(eq(ORG), eq(EXT));
    }

    @Test
    @DisplayName("softDelete marks the row rather than removing it, and the entity filters it out")
    void softDeleteMarksAndTheEntityFilters() {
        NcaEccFiling f = filing("CANCELLED");
        when(repo.findByOrganisationIdAndExternalId(eq(ORG), eq(EXT))).thenReturn(Optional.of(f));

        svc.softDelete(EXT);
        assertThat(f.getIsDeleted()).isEqualTo((short) 1);

        // The filter is what makes the soft delete mean anything; without it every register would
        // still return the row. CLAUDE.md's landmine list has this as a standing trap.
        org.hibernate.annotations.Where where =
            NcaEccFiling.class.getAnnotation(org.hibernate.annotations.Where.class);
        assertThat(where).as("NcaEccFiling must carry @Where or softDelete hides nothing").isNotNull();
        assertThat(where.clause().replace(" ", "")).isEqualTo("is_deleted=0");
    }

    // ── F2-117: the assurance chain nothing verifies ─────────────────────────

    /**
     * F2-117 — the ATTRIBUTION half is now server-owned; the four-eyes comparison stays OPEN.
     * The half that moved is the one the recorded blocker said could not move yet.
     *
     * <p>⚠ This javadoc deliberately avoids the {@code 'F2-nnn … CLOSED'} adjacency.
     * {@code FindingsRegisterConsistencyTest} reads it as a claim that the WHOLE finding is closed,
     * and it fired here — correctly, because half of this one is not. 🔑 <b>A binary closure gate
     * cannot express a half-closed finding, and the safe direction is for the TEST to under-claim
     * rather than for the register to over-claim:</b> the register is what an auditor is handed, so
     * marking a P1 closed to satisfy a regex would have retired a control that is still half
     * unbuilt. ⚙ Worth knowing rather than fixing here: that gate cannot tell a quotation from a
     * claim (F2-193's shape), and the {@code unquoted()} convention that solves it elsewhere
     * carries its own warning against being applied to a whole Java file (F2-216) — so naming the
     * shape with the placeholder id, exactly as the gate's own failure message does, is the honest
     * way to reference it without instantiating it.
     *
     * <p>This test used to assert the defect's <b>mechanism</b>: that the service never consults
     * the security context and takes both assurance names from the body. It said so in its name,
     * and it carried an exit condition — <i>"when F2-117 is fixed this test must fail, and that
     * is intended."</i> It did fail, exactly as designed, which is the honest version of a pinned
     * known defect and is why this rewrite is a handover rather than a correction.
     *
     * <p>🔑 <b>But the pin outlived the reason for it, because the class javadoc's blocker argument
     * covered TWO fixes and only one was blocked.</b> It reasoned that a comparison over these
     * strings would answer "cannot tell" every time, since 0 of 4 people-labels resolved to a
     * platform user — true, and it is an argument about SEGREGATION (approver ≠ peer reviewer),
     * which does need identity columns. It was applied to ATTRIBUTION (who the approver IS), which
     * needed nothing but {@code CurrentActor}. And attribution is the PREREQUISITE for segregation:
     * comparing a reviewer against an approver is meaningless while the approver is a string the
     * caller typed. <b>The recorded blocker inverted its own dependency</b> — F2-178's "re-measure
     * a recorded blocker before believing its estimate", in a new shape.
     *
     * <p>What F2-225 closed, proved live as {@code compliance} against a planted filing:
     * {@code cisoApprover} and {@code draftedBy} are the authenticated principal. What remains
     * open is the four-eyes comparison, and its blocker is now genuine and unchanged: no
     * {@code *_user_id} column exists for either assurance role. ⚙ One thing did move for it —
     * {@code ciso_approver} now holds a username, which DOES resolve to a platform user, so the
     * "0 of 4 labels resolve" measurement no longer describes that column.
     */
    @Test
    @DisplayName("F2-117: the CISO approver is server-stamped; the four-eyes comparison is still unbuilt")
    void theAssuranceChainIsCallerAssertedAndNothingComparesIt() throws IOException {
        String src = Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
            "nca_ecc", "service", "NcaEccFilingService.java").toAbsolutePath().normalize(),
            StandardCharsets.UTF_8);

        assertThat(src).as(
            "F2-225: the CISO approval branch stamps cisoApprovalAt from the server's clock, so its "
            + "approver is whoever is signed in — not a name the caller types into a form")
            .doesNotContain("req.getCisoApprover()")
            .doesNotContain("req.getDraftedBy()")
            .contains("requireActor(");

        assertThat(src).as(
            "the PEER REVIEWER stays caller-supplied on purpose: at PEER_REVIEW entry the reviewer "
            + "is being NOMINATED, and peerReviewCompletedAt is stamped later without touching the "
            + "name. Assignment-then-completion is a legitimate two-step (F2-222) and stamping the "
            + "principal there would record a review nobody performed")
            .contains("f.setPeerReviewer(req.getPeerReviewer())");

        // F2-117's RESIDUAL, and its blocker is real: with no identity column for either assurance
        // role a self-review gate would answer "cannot tell" every time and refuse nothing — the
        // shipped-but-inert control this programme keeps finding. V357's shape is the prerequisite.
        assertThat(NcaEccFiling.class.getDeclaredFields())
            .as("no *_user_id column exists for either assurance role, so the four-eyes comparison "
                + "still has nothing to compare. If these columns ever appear, write the refusal "
                + "test and close F2-117 — the attribution half is already done")
            .noneMatch(f -> f.getName().equals("peerReviewerUserId")
                         || f.getName().equals("cisoApproverUserId"));
    }

    // ── the authorisation branches ───────────────────────────────────────────

    @Test
    @DisplayName("Every endpoint is guarded, reads and writes by permission and destroy by role")
    void everyEndpointIsGuarded() throws IOException {
        String src = Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
            "nca_ecc", "controller", "NcaEccFilingController.java").toAbsolutePath().normalize(),
            StandardCharsets.UTF_8);

        long mappings = src.lines()
            .filter(l -> l.contains("@GetMapping") || l.contains("@PostMapping")
                      || l.contains("@PutMapping") || l.contains("@DeleteMapping")).count();
        long guards = src.lines().filter(l -> l.contains("@PreAuthorize")).count();
        assertThat(guards).as("every one of the %d mappings must carry a guard", mappings)
            .isEqualTo(mappings);

        // ⚠ Unlike outsourcing's, these guards are hasPermission — the ratified idiom (F2-05), which
        // gets the PLATFORM_ADMIN bypass and is validated against the catalogue. So this module is
        // NOT part of F2-52's 537 role-name sites, and that is worth pinning so a future edit does
        // not quietly convert it back.
        assertThat(src).contains("hasPermission('CYBER','READ')")
                       .contains("hasPermission('CYBER','WRITE')");
        assertThat(src).as("the entitlement gate must name the SKU that sells this module")
            .contains("@RequireModule(\"COMPLIANCE\")");
        assertThat(src).as("destroy stays PLATFORM_ADMIN by choice, and should be argued for if changed")
            .contains("hasRole('PLATFORM_ADMIN')");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private NcaEccFiling filing(String status) {
        NcaEccFiling f = new NcaEccFiling();
        f.setId(1L);
        f.setExternalId(EXT);
        f.setOrganisationId(ORG);
        f.setFiscalYear(2026);
        f.setStatus(status);
        return f;
    }

    private static NcaEccFilingDto.TransitionRequest transitionTo(String status) {
        NcaEccFilingDto.TransitionRequest r = new NcaEccFilingDto.TransitionRequest();
        r.setStatus(status);
        return r;
    }

    private NcaEccFiling created(Integer d1, Integer d2, Integer d3, Integer d4, Integer d5) {
        NcaEccFilingDto req = new NcaEccFilingDto();
        req.setFiscalYear(2026);
        req.setDomain1Score(d1);
        req.setDomain2Score(d2);
        req.setDomain3Score(d3);
        req.setDomain4Score(d4);
        req.setDomain5Score(d5);
        NcaEccFilingDto out = svc.create(req);
        NcaEccFiling f = new NcaEccFiling();
        f.setOverallScore(out.getOverallScore());
        return f;
    }

    private Set<String> allStates() {
        Set<String> all = new java.util.TreeSet<>(allowedMap().keySet());
        allowedMap().values().forEach(all::addAll);
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> allowedMap() {
        Object v = ReflectionTestUtils.getField(NcaEccFilingService.class, "ALLOWED");
        assertThat(v).as("NcaEccFilingService.ALLOWED must exist — a rename would otherwise make "
            + "this whole class stop checking the machine").isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }
}
