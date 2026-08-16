package com.tss.grc.module.policy.service;

import com.tss.grc.common.exception.DomainAccessDeniedException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.attachment.repository.AttachmentRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyAttestationRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentControlLinkRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.repository.PolicyExceptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * F2-222 — the C-6 approval control, its INPUT and its ATTRIBUTION, against the update path.
 *
 * <p>{@code transition()} carries C-6 segregation of duty — <i>"the policy author/owner cannot
 * approve their own policy"</i> — and stamps {@code approverName} from the authenticated principal
 * once that check has passed. {@code update()} is guarded by the same permission
 * ({@code COMPLIANCE:WRITE}, 13 roles, 5 of them seated) and wrote both the control's input and its
 * attribution straight from the request body.
 *
 * <p>Probed live 2026-08-16 against rows this probe PLANTED (F2-206), never a seeded policy:
 *
 * <pre>
 *   -- the attribution --
 *   POST /documents/{id}/transition {to: APPROVED}   as the author   -> 403  (C-6 fires)
 *   POST /documents/{id}/transition {to: APPROVED}   as another      -> 200, approverName="auditor"
 *   PUT  /documents/{id} {approverName: "Nobody Who Approved This"}  -> 200, STORED, still APPROVED
 *   PUT  /documents/{id} {approverName: "compliance"} as the author  -> 200  (the act C-6 refused)
 *
 *   -- the control's own input --
 *   POST /documents/{id}/transition {to: APPROVED}   as the owner    -> 403
 *   PUT  /documents/{id} {ownerName: "somebody else"} as the owner   -> 200
 *   POST /documents/{id}/transition {to: APPROVED}   as the owner    -> 200
 * </pre>
 *
 * <p>🔑 The freeze at the top of {@code update()} — <i>"Once PUBLISHED, only metadata fields are
 * editable"</i> — already had the right idea and was written for the wrong state: the stamp happens
 * at APPROVED, so the attribution was frozen ONE STATE TOO LATE.
 *
 * <p>Half of these legs assert that the control still WORKS. A slice that closed the bypass by
 * breaking the approval workflow would otherwise score identically (F2-218).
 */
@ExtendWith(MockitoExtension.class)
class PolicyApprovalAttributionTest {

    private static final Long ORG_ID = 1L;

    @Mock private PolicyDocumentRepository docs;
    @Mock private PolicyExceptionRepository exceptions;
    @Mock private PolicyAttestationRepository attestations;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ControlRepository controls;
    @Mock private PolicyDocumentControlLinkRepository controlLinks;
    @Mock private AttachmentRepository attachments;

    private PolicyManagementService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new PolicyManagementService(docs, exceptions, attestations, audit, publisher,
                                              controls, controlLinks, attachments);
        lenient().when(docs.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    /** A stored policy in the given state, approved by a real principal where that applies. */
    private PolicyDocument stored(String status) {
        PolicyDocument p = new PolicyDocument();
        p.setId(7L);
        p.setOrganisationId(ORG_ID);
        p.setPolicyCode("POL-F222");
        p.setVersion("1.0");
        p.setStatus(status);
        p.setCreatedBy("compliance");
        p.setOwnerName("auditor");
        p.setOwnerEmail("auditor@example.invalid");
        p.setBodyUrl("https://example.invalid/f222");
        p.setEffectiveDate(java.time.LocalDate.of(2026, 1, 1));
        p.setApproverName("so_officer");
        p.setApproverEmail("so_officer@example.invalid");
        when(docs.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(p));
        return p;
    }

    private static PolicyDocument forgery() {
        PolicyDocument body = new PolicyDocument();
        body.setApproverName("Nobody Who Approved This");
        body.setApproverEmail("nobody@example.invalid");
        return body;
    }

    // ── the bypass is closed ──────────────────────────────────────────────────

    @Test
    void anApprovedPolicysApproverCannotBeRewrittenThroughTheUpdatePath() {
        stored("APPROVED");

        PolicyDocument saved = service.update(7L, forgery());

        assertThat(saved.getApproverName())
            .as("approverName is transition()'s stamp of who passed the C-6 check. The update "
                + "path is guarded by the same permission and performs none of the check, so a "
                + "caller writing it replaces the attribution of a governance act.")
            .isEqualTo("so_officer");
        assertThat(saved.getApproverEmail()).isEqualTo("so_officer@example.invalid");
    }

    @Test
    void norCanItOnAnyLaterStateEither() {
        for (String status : new String[]{"PUBLISHED", "EXPIRED", "ARCHIVED"}) {
            stored(status);
            assertThat(service.update(7L, forgery()).getApproverName())
                .as("%s is downstream of APPROVED — fixing only the state that was probed is "
                    + "what leaves the remainder looking covered (F2-215)", status)
                .isEqualTo("so_officer");
        }
    }

    @Test
    void anUnrecognisedStatusProtectsTheAttributionRatherThanExposingIt() {
        stored("SOME_STATUS_NOBODY_DECLARED");

        assertThat(service.update(7L, forgery()).getApproverName())
            .as("an inclusion list over the pre-approval states fails in the safe direction; an "
                + "exclusion list of the post-approval ones would expose every token nobody "
                + "remembered (F2-196)")
            .isEqualTo("so_officer");
    }

    @Test
    void theOwnerCannotBeChangedWhileTheApprovalIsPending() {
        stored("REVIEW");
        PolicyDocument body = new PolicyDocument();
        body.setOwnerName("somebody else");
        body.setOwnerEmail("somebody@example.invalid");

        PolicyDocument saved = service.update(7L, body);

        assertThat(saved.getOwnerName())
            .as("ownerName is an INPUT to C-6. Editable in REVIEW, the party the control "
                + "constrains edits the control's own input and then approves.")
            .isEqualTo("auditor");
        assertThat(saved.getOwnerEmail()).isEqualTo("auditor@example.invalid");
    }

    // ── and nothing that worked has stopped working ───────────────────────────

    @Test
    void theApprovingBodyIsStillRecordableWhileThePolicyIsBeingDrafted() {
        for (String status : new String[]{"DRAFT", "REVIEW"}) {
            stored(status);
            PolicyDocument body = new PolicyDocument();
            body.setApproverName("Board Risk Committee");

            assertThat(service.update(7L, body).getApproverName())
                .as("24 of 41 live dev rows name an approver and most name an OFFICE rather than "
                    + "a principal ('رئيس الهيئة' x12, 'Board Risk Committee'). Refusing that "
                    + "would break a working request, the one thing this fix must not do (F2-182).")
                .isEqualTo("Board Risk Committee");
        }
    }

    @Test
    void ownershipIsStillReassignableWhileThePolicyIsADraft() {
        stored("DRAFT");
        PolicyDocument body = new PolicyDocument();
        body.setOwnerName("somebody else");

        assertThat(service.update(7L, body).getOwnerName())
            .as("the machine's REVIEW -> DRAFT edge is the route for reassigning a policy that "
                + "has already been submitted, so the freeze narrows a window rather than "
                + "removing a capability")
            .isEqualTo("somebody else");
    }

    @Test
    void everyOtherEditableFieldIsUntouchedByTheFreeze() {
        stored("APPROVED");
        PolicyDocument body = new PolicyDocument();
        body.setTitleEn("Edited title");
        body.setNotes("Edited notes");

        PolicyDocument saved = service.update(7L, body);
        assertThat(saved.getTitleEn()).isEqualTo("Edited title");
        assertThat(saved.getNotes()).isEqualTo("Edited notes");
    }

    @Test
    void transitionStillStampsTheAttributionFromTheActingPrincipal() {
        PolicyDocument p = stored("REVIEW");
        p.setApproverName(null);

        PolicyDocument saved = service.transition(7L, "APPROVED", "so_officer");

        assertThat(saved.getStatus()).isEqualTo("APPROVED");
        assertThat(saved.getApproverName())
            .as("the stamp is the whole point of freezing the field — a fix that removed it "
                + "would pass every assertion above")
            .isEqualTo("so_officer");
    }

    @Test
    void theC6SegregationOfDutyCheckStillRefusesTheAuthorAndTheOwner() {
        stored("REVIEW");
        assertThatThrownBy(() -> service.transition(7L, "APPROVED", "compliance"))
            .isInstanceOf(DomainAccessDeniedException.class)
            .hasMessageContaining("Segregation of duty");

        stored("REVIEW");
        assertThatThrownBy(() -> service.transition(7L, "APPROVED", "auditor"))
            .as("the owner half is the one the live probe evaded")
            .isInstanceOf(DomainAccessDeniedException.class);
    }

    // ── the set is a derivation, not a second list ────────────────────────────

    @Test
    void beforeApprovalIsExactlyTheStatesFromWhichApprovedIsStillReachable() throws Exception {
        Field f = PolicyManagementService.class.getDeclaredField("BEFORE_APPROVAL");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> declared = (Set<String>) f.get(null);

        // Drive the machine itself for every status the column admits, and take the closure of
        // the states from which APPROVED can still be reached. F2-196: derive it, never type it.
        Set<String> vocabulary = Set.of("DRAFT", "REVIEW", "APPROVED", "PUBLISHED", "EXPIRED",
                                        "ARCHIVED");
        Set<String> reaches = new TreeSet<>();
        Set<String> frontier = new LinkedHashSet<>(Set.of("APPROVED"));
        while (!frontier.isEmpty()) {
            Set<String> next = new LinkedHashSet<>();
            for (String from : vocabulary) {
                if (reaches.contains(from) || "APPROVED".equals(from)) continue;
                for (String to : frontier) {
                    if (machineAllows(from, to)) { reaches.add(from); next.add(from); break; }
                }
            }
            frontier = next;
        }

        assertThat(declared)
            .as("BEFORE_APPROVAL must BE the reachability, so the freeze and the machine cannot "
                + "drift — a second hand-typed list is the artefact F2-217 found measuring itself")
            .containsExactlyInAnyOrderElementsOf(reaches);
    }

    /** Asks the real machine, by calling it — never a copy of its edges. */
    private boolean machineAllows(String from, String to) {
        stored(from);
        try {
            // "someone" is neither createdBy nor the owner, so C-6 cannot mask an illegal edge
            service.transition(7L, to, "someone-unrelated");
            return true;
        } catch (com.tss.grc.common.exception.IllegalTransitionException e) {
            return false;
        } catch (RuntimeException e) {
            // a legal edge that failed a downstream guard (effective date, document control) is
            // still a legal edge — only IllegalTransitionException means the machine refused it
            return true;
        }
    }
}
