package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.dto.CabVoteRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeCommentRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DF-4 / finding F2-24 — the forged-actor test for the <b>change-approval</b> path, the
 * twin of {@code CommitteeVoteProvenanceTest}.
 *
 * <p>{@code recordApproval} used to bind the {@code ChangeApproval} JPA entity straight
 * from the request body and trust {@code approverName}. Because the CM-C2
 * segregation-of-duties gate compared that <em>claimed</em> name against the change's
 * requester and owner, <b>a requester could approve their own change by submitting anybody
 * else's name</b>. {@code approver_id} was client-settable too, nothing enforced one vote
 * per approver, and {@code audit.log} recorded the claim.
 *
 * <p>The in-code comment had already conceded the gap — "(Name-based check; principal-id
 * capture is a follow-up.)" — and the existing test
 * {@code recordApproval_self_vote_blocked_by_SoD} <b>passed the whole time</b>, because it
 * politely supplied the requester's real name. A test that only exercises the honest path
 * cannot detect a dishonest one; that is precisely the blind spot recorded in F2-21, found
 * here a second time in a different module.
 *
 * <p>Two independent reviews each found this defect class in a different code path and
 * neither found the other's, which is why the sweep — not just these two fixes — is on the
 * plan (F2-22).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CabVoteProvenanceTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 500L;
    private static final long REQUESTER_UID = 10L;   // Alice raised the change
    private static final long OWNER_UID     = 11L;   // Basim owns it
    private static final long CAB_UID       = 20L;   // Dana sits on the CAB

    @Mock ChangeRequestRepository  changeRepo;
    @Mock ChangeApprovalRepository approvalRepo;
    @Mock ChangeTaskRepository     taskRepo;
    @Mock ChangeLinkRepository     linkRepo;
    @Mock ChangeCommentRepository  commentRepo;
    @Mock AuditLogService          audit;
    @Mock ApplicationEventPublisher publisher;

    @InjectMocks ChangeManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(approvalRepo.save(any(ChangeApproval.class))).thenAnswer(i -> i.getArgument(0));
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** A change raised by Alice (id 10) and owned by Basim (id 11), awaiting CAB. */
    private ChangeRequest change() {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-0500");
        c.setStatus("CAB_PENDING");
        c.setChangeType("NORMAL");
        c.setRequestedById(REQUESTER_UID);
        c.setRequestedByName("Alice Requester");
        c.setChangeOwnerId(OWNER_UID);
        c.setChangeOwnerName("Basim Owner");
        return c;
    }

    private CabVoteRequest vote(String decision) {
        return new CabVoteRequest(decision, "CAB Member", null);
    }

    // ── 1. the forgery that used to work ────────────────────────────────────

    /**
     * <b>The defect, stated as a test.</b> Alice raised the change. Under the old code she
     * submitted {@code approverName: "Dana CAB"} and the SoD gate — comparing that string
     * to her own name — waved it through. Her identity now comes from the principal, so the
     * name she puts in the body is irrelevant: there is nowhere to put it.
     */
    @Test
    void the_requester_cannot_approve_their_own_change_by_naming_someone_else() {
        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("APPROVE"),
                REQUESTER_UID, "Dana CAB"))   // Alice's id, somebody else's name
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duty");
        verify(approvalRepo, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void the_change_owner_cannot_approve_their_own_change_either() {
        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("APPROVE"),
                OWNER_UID, "Basim pretending to be someone"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duty");
        verify(approvalRepo, never()).save(any());
    }

    @Test
    void an_unidentified_caller_cannot_cast_a_cab_vote() {
        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("APPROVE"), null, "  "))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("identified user");
        verify(approvalRepo, never()).save(any());
    }

    // ── 2. the vote is attributed to the caller, not to the body ────────────

    @Test
    void the_vote_is_recorded_against_the_authenticated_user() {
        ChangeApproval saved = service.recordApproval(CHANGE_ID, vote("APPROVE"), CAB_UID, "Dana CAB");
        assertThat(saved.getApproverId()).isEqualTo(CAB_UID);
        assertThat(saved.getApproverName()).isEqualTo("Dana CAB");
        assertThat(saved.getDecision()).isEqualTo("APPROVE");
        assertThat(saved.getChangeRequestId()).isEqualTo(CHANGE_ID);
    }

    @Test
    void the_audit_line_carries_the_authenticated_user_id() {
        service.recordApproval(CHANGE_ID, vote("REJECT"), CAB_UID, "Dana CAB");
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("APPROVAL"), eq("ChangeRequest"), eq(CHANGE_ID), detail.capture());
        assertThat(detail.getValue())
            .contains("REJECT")
            .contains("Dana CAB")
            .contains("user#" + CAB_UID);   // the claim can no longer stand alone
    }

    @Test
    void a_decision_outside_the_permitted_set_is_rejected() {
        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("MAYBE"), CAB_UID, "Dana CAB"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("decision must be one of");
        verify(approvalRepo, never()).save(any());
    }

    @Test
    void a_lowercase_padded_decision_is_normalised() {
        ChangeApproval saved = service.recordApproval(CHANGE_ID,
            new CabVoteRequest("  approve ", "CAB", null), CAB_UID, "Dana CAB");
        assertThat(saved.getDecision()).isEqualTo("APPROVE");
    }

    // ── 3. one vote per approver ────────────────────────────────────────────

    /** Without this a single actor manufactured a CAB majority by voting repeatedly. */
    @Test
    void an_approver_cannot_cast_a_second_vote_on_the_same_change() {
        ChangeApproval existing = new ChangeApproval();
        existing.setId(1L);
        existing.setChangeRequestId(CHANGE_ID);
        existing.setApproverId(CAB_UID);
        existing.setDecision("APPROVE");
        when(approvalRepo.findByChangeRequestIdAndApproverId(CHANGE_ID, CAB_UID))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("REJECT"), CAB_UID, "Dana CAB"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already recorded a CAB vote");
        verify(approvalRepo, never()).save(any());
    }

    @Test
    void a_different_approver_may_still_vote() {
        ChangeApproval danas = new ChangeApproval();
        danas.setId(1L);
        danas.setChangeRequestId(CHANGE_ID);
        danas.setApproverId(CAB_UID);
        when(approvalRepo.findByChangeRequestIdAndApproverId(CHANGE_ID, CAB_UID))
            .thenReturn(Optional.of(danas));
        when(approvalRepo.findByChangeRequestIdAndApproverId(CHANGE_ID, 21L))
            .thenReturn(Optional.empty());

        ChangeApproval saved = service.recordApproval(CHANGE_ID, vote("APPROVE"), 21L, "Faisal CAB");
        assertThat(saved.getApproverId()).isEqualTo(21L);
    }

    // ── 4. the pre-existing gates still hold, in the right order ────────────

    @Test
    void a_vote_is_refused_while_the_change_is_in_the_wrong_state() {
        ChangeRequest c = change();
        c.setStatus("IMPLEMENTING");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("APPROVE"), CAB_UID, "Dana CAB"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("IMPACT_ASSESSED or CAB_PENDING");
        verify(approvalRepo, never()).save(any());
    }

    /** IMPACT_ASSESSED is the other legal state for a CAB vote. */
    @Test
    void a_vote_is_accepted_while_the_change_is_impact_assessed() {
        ChangeRequest c = change();
        c.setStatus("IMPACT_ASSESSED");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));

        ChangeApproval saved = service.recordApproval(CHANGE_ID, vote("APPROVE"), CAB_UID, "Dana CAB");
        assertThat(saved.getDecision()).isEqualTo("APPROVE");
    }

    /**
     * The state check must run before the identity work, so a caller cannot use a vote on a
     * wrong-state change to discover who raised it.
     */
    @Test
    void the_state_check_precedes_the_identity_check() {
        ChangeRequest c = change();
        c.setStatus("CLOSED");
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.recordApproval(CHANGE_ID, vote("APPROVE"), null, null))
            .isInstanceOf(IllegalArgumentException.class)   // state, not AccessDenied
            .hasMessageContaining("IMPACT_ASSESSED or CAB_PENDING");
    }
}
