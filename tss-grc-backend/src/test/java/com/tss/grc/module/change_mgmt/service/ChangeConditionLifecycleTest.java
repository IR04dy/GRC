package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.dto.ChangeConditionRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeCondition;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeConditionRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.spi.EvidencePort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S7e / CE-5 — the register's behaviour: what it records, what it refuses, and who may do which.
 *
 * <p>Companion to {@code ChangeConditionRegisterTest}, which pins the design decisions by reading the
 * source and the migration. This one drives the service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeConditionLifecycleTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 9L;

    @Mock private ChangeRequestRepository     changeRepo;
    @Mock private ChangeConditionRepository   conditionRepo;
    @Mock private AuditLogService             audit;
    @Mock private ApplicationEventPublisher   publisher;
    @Mock private EvidencePort                evidencePort;

    private ChangeManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new ChangeManagementService(changeRepo, null, null, null, null, audit, publisher);
        ReflectionTestUtils.setField(service, "conditionRepo", conditionRepo);
        ReflectionTestUtils.setField(service, "evidencePort", evidencePort);
        // S8a/CE-9: satisfying a condition now requires APPROVED evidence. The default here is "one
        // approved artefact is attached", so the tests written before that gate keep testing what
        // they were written to test; the gate itself is exercised by ChangeConditionEvidenceTest.
        when(evidencePort.attached(EvidencePort.Subject.CONDITION, 501L))
            .thenReturn(List.of(approvedEvidence()));
        when(changeRepo.save(any(ChangeRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(conditionRepo.save(any(ChangeCondition.class))).thenAnswer(i -> {
            ChangeCondition c = i.getArgument(0);
            if (c.getId() == null) c.setId(500L);
            return c;
        });
        authenticateAs("cab-chair", 77L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String username, Long userId) {
        var authorities = List.of(new SimpleGrantedAuthority("CHANGE_WRITE"),
                                  new SimpleGrantedAuthority("CHANGE_APPROVE"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new GrcUserDetails(userId, ORG, username, "n/a", true, true, authorities),
                "n/a", authorities));
    }

    private static ChangeRequest approvedChange() {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-2026-0009");
        c.setTitleEn("Payment gateway TLS upgrade");
        c.setTitleAr("ترقية بروتوكول التشفير");
        c.setChangeType("NORMAL");
        c.setRiskLevel("HIGH");
        c.setStatus("SCHEDULED");
        c.setRequestedByName("requester");
        c.setRequestedById(11L);
        c.setChangeOwnerName("owner");
        c.setChangeOwnerId(12L);
        return c;
    }

    private static EvidencePort.EvidenceRef approvedEvidence() {
        return new EvidencePort.EvidenceRef(9001L, "ev-9001", "DR rehearsal report", "تقرير التمرين",
                                           "REPORT", "APPROVED", true, "dr.pdf", "abc123");
    }

    private static ChangeCondition open(long id) {
        ChangeCondition k = new ChangeCondition();
        k.setId(id);
        k.setOrganisationId(ORG);
        k.setChangeRequestId(CHANGE_ID);
        k.setTextEn("Re-run the DR rehearsal and attach the report");
        k.setTextAr("إعادة تنفيذ تمرين التعافي وإرفاق التقرير");
        k.setStatus(ChangeConditionStatus.OPEN);
        k.setImposedAt(LocalDateTime.now());
        return k;
    }

    // ── Imposing ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("imposing records who imposed it, and cannot be born closed")
    void anImposedConditionNamesItsAuthorAndOpensOpen() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));
        // ⚠ The request is a narrow record, and that IS the mass-assignment control:
        // MassAssignmentRatchetTest rejected the first draft, which bound ChangeCondition from the
        // body and scrubbed it in the service. There is no `status`, no `organisationId`, no `id` and
        // no closing field to send — the fields a caller may not set do not exist on the wire.
        ChangeConditionRequest body = new ChangeConditionRequest(
            "Re-run the DR rehearsal and attach the report",
            "إعادة تنفيذ تمرين التعافي وإرفاق التقرير",
            "DBA Lead", null, null);

        ChangeCondition saved = service.addCondition(CHANGE_ID, body);

        assertThat(saved.getStatus())
            .as("the only route to a closed state is satisfy()/waive(), which stamp the principal — "
              + "the shape CorrectiveActionService.create() uses")
            .isEqualTo("OPEN");
        assertThat(saved.getClosedByName()).isNull();
        assertThat(saved.getClosedAt()).isNull();
        assertThat(saved.getClosureNotes()).isNull();
        assertThat(saved.getOrganisationId()).as("tenant comes from the change, never the body").isEqualTo(ORG);
        assertThat(saved.getChangeRequestId()).isEqualTo(CHANGE_ID);
        assertThat(saved.getOwnerName()).as("the fields a caller MAY set arrive intact").isEqualTo("DBA Lead");
        assertThat(saved.getSortOrder()).as("a null sortOrder defaults rather than failing NOT NULL").isEqualTo(0);
        assertThat(saved.getImposedByName())
            .as("F2-93/F2-103 applied when the record is introduced, not after someone notices the "
              + "column is empty — an obligation with no named imposer cannot be appealed or varied")
            .isEqualTo("cab-chair");
        assertThat(saved.getImposedById()).isEqualTo(77L);
        assertThat(saved.getImposedAt()).isNotNull();
        verify(audit).log(org.mockito.ArgumentMatchers.eq("CONDITION_IMPOSED"),
                          org.mockito.ArgumentMatchers.eq("ChangeRequest"),
                          org.mockito.ArgumentMatchers.eq(CHANGE_ID), anyString());
    }

    // ── The gate ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⚠ SCHEDULED → IMPLEMENTING is refused while a condition is open, and names it")
    void implementationIsBlockedByAnOpenObligation() {
        ChangeRequest c = approvedChange();
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));
        when(conditionRepo.countByOrganisationIdAndChangeRequestIdAndStatus(ORG, CHANGE_ID, "OPEN"))
            .thenReturn(1L);
        when(conditionRepo.findByOrganisationIdAndChangeRequestIdOrderBySortOrderAscIdAsc(ORG, CHANGE_ID))
            .thenReturn(List.of(open(501L)));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "IMPLEMENTING", Map.of("target", "IMPLEMENTING")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot start implementation")
            .hasMessageContaining("Re-run the DR rehearsal")
            .hasMessageContaining("waive");

        verify(changeRepo, never()).save(any(ChangeRequest.class));
        assertThat(c.getImplementationStarted())
            .as("a refusal must leave the change untouched — and arming the A17-D3 ratification clock "
              + "for an implementation that is then refused would start an SLA against work that "
              + "never began")
            .isNull();
        assertThat(c.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("no open conditions → implementation proceeds (the gate is narrow)")
    void aChangeWithNoOpenObligationsImplements() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));
        when(conditionRepo.countByOrganisationIdAndChangeRequestIdAndStatus(ORG, CHANGE_ID, "OPEN"))
            .thenReturn(0L);

        ChangeRequest out = service.transition(CHANGE_ID, "IMPLEMENTING", Map.of("target", "IMPLEMENTING"));

        assertThat(out.getStatus()).isEqualTo("IMPLEMENTING");
        assertThat(out.getImplementationStarted()).isNotNull();
        verify(conditionRepo, never())
            .findByOrganisationIdAndChangeRequestIdOrderBySortOrderAscIdAsc(anyLong(), anyLong());
    }

    @Test
    @DisplayName("the singular/plural refusal reads correctly for one and for many")
    void theMessageIsWrittenForAHuman() {
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));
        when(conditionRepo.countByOrganisationIdAndChangeRequestIdAndStatus(ORG, CHANGE_ID, "OPEN"))
            .thenReturn(2L);
        ChangeCondition second = open(502L);
        second.setTextEn("Obtain the CISO sign-off");
        when(conditionRepo.findByOrganisationIdAndChangeRequestIdOrderBySortOrderAscIdAsc(ORG, CHANGE_ID))
            .thenReturn(List.of(open(501L), second));

        assertThatThrownBy(() -> service.transition(CHANGE_ID, "IMPLEMENTING", Map.of("target", "IMPLEMENTING")))
            .hasMessageContaining("2 conditions")
            .hasMessageContaining("are still open")
            .hasMessageContaining("Obtain the CISO sign-off");
    }

    // ── Satisfying ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("satisfying stamps who and when, and keeps the evidence reference")
    void satisfactionIsAuthored() {
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        ChangeCondition out = service.satisfyCondition(501L, "DR report attached as EVD-2026-118");

        assertThat(out.getStatus()).isEqualTo("SATISFIED");
        assertThat(out.getClosedByName()).isEqualTo("cab-chair");
        assertThat(out.getClosedById()).isEqualTo(77L);
        assertThat(out.getClosedAt()).isNotNull();
        assertThat(out.getClosureNotes())
            .as("S8's ConditionsSatisfiedGuard grows into this field — it is the seam for a verified "
              + "evidence link, and the author is stamped now so the eventual four-eyes check has "
              + "something to compare")
            .isEqualTo("DR report attached as EVD-2026-118");
    }

    @Test
    @DisplayName("a closed condition cannot be reopened or re-closed")
    void closingIsOneWay() {
        for (String closed : List.of("SATISFIED", "WAIVED")) {
            ChangeCondition k = open(501L);
            k.setStatus(closed);
            k.setClosedByName("someone");
            k.setClosedAt(LocalDateTime.now());
            when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(k));

            assertThatThrownBy(() -> service.satisfyCondition(501L, "again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already " + closed)
                .as("the remedy must be stated, not left to guesswork")
                .hasMessageContaining("Impose a new condition");
        }
    }

    @Test
    @DisplayName("a condition cannot be closed with no authenticated principal")
    void anUnauthoredClosureIsRefusedInCodeNotOnlyBySql() {
        SecurityContextHolder.clearContext();
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        assertThatThrownBy(() -> service.satisfyCondition(501L, "done"))
            .isInstanceOf(IllegalStateException.class)
            .as("ck_cc_closure would refuse it too, but as an opaque constraint violation — a "
              + "condition closed by nobody is the defect this register replaces, so say so")
            .hasMessageContaining("closed by nobody");
        verify(conditionRepo, never()).save(any(ChangeCondition.class));
    }

    // ── Waiving ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a waiver without a reason is refused")
    void removingAnObligationCannotStandOnNothing() {
        for (String noReason : List.of("", "   ")) {
            assertThatThrownBy(() -> service.waiveCondition(501L, noReason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("waiver reason is required");
        }
        assertThatThrownBy(() -> service.waiveCondition(501L, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(conditionRepo, never()).save(any(ChangeCondition.class));
    }

    @Test
    @DisplayName("a waiver with a reason is recorded as WAIVED, distinctly from SATISFIED")
    void aWaiverIsRecordedAsAWaiver() {
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        ChangeCondition out = service.waiveCondition(501L, "DR site is decommissioned; obligation moot");

        assertThat(out.getStatus())
            .as("collapsing WAIVED into SATISFIED would make a governance breach indistinguishable "
              + "from compliance in every report")
            .isEqualTo("WAIVED");
        assertThat(out.getClosureNotes()).contains("decommissioned");
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(audit).log(action.capture(), anyString(), anyLong(), anyString());
        assertThat(action.getValue()).isEqualTo("CONDITION_WAIVED");
    }

    @Test
    @DisplayName("⚠ the change's requester may not waive a condition on it")
    void theRequesterCannotForgiveTheirOwnCondition() {
        authenticateAs("requester", 11L);   // == requestedById on the change
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        assertThatThrownBy(() -> service.waiveCondition(501L, "not needed"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("may not waive")
            .hasMessageContaining("not a condition");
        verify(conditionRepo, never()).save(any(ChangeCondition.class));
    }

    @Test
    @DisplayName("...nor may its owner")
    void theOwnerCannotEither() {
        authenticateAs("owner", 12L);       // == changeOwnerId
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        assertThatThrownBy(() -> service.waiveCondition(501L, "not needed"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("...but the SAME person may record it as satisfied — doing the work is not forgiving it")
    void theRequesterMaySatisfyWhatTheyMayNotWaive() {
        authenticateAs("requester", 11L);
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        ChangeCondition out = service.satisfyCondition(501L, "rehearsal re-run, report attached");

        assertThat(out.getStatus()).isEqualTo("SATISFIED");
        assertThat(out.getClosedByName()).isEqualTo("requester");
    }

    @Test
    @DisplayName("the SoD check falls back to names only when the change captured no ids")
    void theLegacyFallbackIsAFallbackNotThePrimaryTest() {
        // DF-4/F2-24: name-only comparison is what let a requester approve their own change by
        // claiming somebody else's name, so it applies only where the ids are absent.
        ChangeRequest legacy = approvedChange();
        legacy.setRequestedById(null);
        legacy.setChangeOwnerId(null);
        authenticateAs("requester", 999L);   // a different id, the same name
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.waiveCondition(501L, "not needed"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("may not waive");
    }

    @Test
    @DisplayName("a stranger with the permission may waive it")
    void anIndependentApproverMayWaive() {
        authenticateAs("second-approver", 88L);
        when(conditionRepo.findByIdAndOrganisationId(501L, ORG)).thenReturn(Optional.of(open(501L)));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(approvedChange()));

        ChangeCondition out = service.waiveCondition(501L, "superseded by CHG-2026-0012");

        assertThat(out.getStatus()).isEqualTo("WAIVED");
        assertThat(out.getClosedByName()).isEqualTo("second-approver");
    }
}
