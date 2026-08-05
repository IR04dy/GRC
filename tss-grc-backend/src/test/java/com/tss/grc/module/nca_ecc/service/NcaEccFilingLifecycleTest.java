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
 * <h3>What is deliberately NOT asserted, and why that is the honest choice</h3>
 * This filing has a three-step assurance chain — drafted, peer-reviewed, CISO-approved — and
 * {@code peerReviewer} and {@code cisoApprover} are <b>strings taken from the request body</b>, with
 * no comparison between them and no reference to the security context. One caller can therefore
 * drive {@code DRAFT → PEER_REVIEW → CISO_APPROVAL → SUBMITTED} while typing both names, and the
 * filing that reaches the regulator records a four-eyes review that never happened.
 *
 * <p>That is <b>F2-117</b>, and it is filed rather than fixed here. Building the comparison now would
 * knowingly reproduce F2-112: measured in dev, <b>0 of the 4 people-labels</b> across
 * {@code drafted_by}/{@code peer_reviewer}/{@code ciso_approver} resolve to a platform user — they
 * are display names and an email address, while the identity a gate could compare is a user id that
 * no column holds. A refusal over those strings would answer "cannot tell" every time and refuse
 * nothing, which is precisely the shipped-but-inert control this programme keeps finding. The remedy
 * is V357's shape: identity columns plus principal capture, then the gate.
 *
 * <p>So {@link #theAssuranceChainIsCallerAssertedAndNothingComparesIt()} asserts the <em>mechanism</em>
 * — that the service never consults the security context — rather than asserting that self-review
 * succeeds. Asserting the permissive behaviour would pin the defect as a specification, which is the
 * trap F2-115 sprang on this row's first module.
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
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
     * See the class javadoc. This asserts the <b>mechanism</b> — that the service derives no actor
     * from the security context and takes both assurance names from the request — rather than
     * asserting that self-review succeeds, which would pin the defect as a specification.
     *
     * <p>When F2-117 is fixed this test must fail, and that is intended: the fix adds principal
     * capture, at which point this assertion is the reminder to replace it with a refusal test.
     */
    @Test
    @DisplayName("F2-117: the peer reviewer and CISO approver are caller-asserted, and nothing compares them")
    void theAssuranceChainIsCallerAssertedAndNothingComparesIt() throws IOException {
        String src = Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
            "nca_ecc", "service", "NcaEccFilingService.java").toAbsolutePath().normalize(),
            StandardCharsets.UTF_8);

        assertThat(src).as(
            "F2-117 — if the service now reads a principal, the assurance chain may finally be "
            + "verifiable. Replace this test with one that REFUSES self-review, and close F2-117.")
            .doesNotContain("SecurityContextHolder")
            .doesNotContain("Authentication");

        assertThat(src).as("both names still come straight from the request body")
            .contains("f.setPeerReviewer(req.getPeerReviewer())")
            .contains("f.setCisoApprover(req.getCisoApprover())");

        // And there is no identity to compare even if someone wrote the comparison: the entity holds
        // names, not user ids. This is the measured reason the fix needs a migration first.
        assertThat(NcaEccFiling.class.getDeclaredFields())
            .as("no *_user_id column exists for either assurance role, so a gate would have nothing "
                + "to compare — V357's shape is the prerequisite, not the comparison")
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
