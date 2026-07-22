package com.tss.grc.module.policy.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocumentControlLink;
import com.tss.grc.module.policy.repository.PolicyDocumentControlLinkRepository;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.repository.PolicyAttestationRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.repository.PolicyExceptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PolicyManagementService (module.policy) — V79 lifecycle rule lock.
 *
 * <p>Covers:
 * <ul>
 *   <li>Policy DRAFT → REVIEW → APPROVED → PUBLISHED happy path,
 *       including the "effective_date required before PUBLISHED" guard
 *       and {@code publishedAt} stamp.</li>
 *   <li>Illegal transitions (DRAFT → PUBLISHED, EXPIRED → PUBLISHED)
 *       throw {@link IllegalTransitionException}.</li>
 *   <li>Delete of PUBLISHED policy refused (must ARCHIVE first).</li>
 *   <li>Exception REQUESTED → UNDER_REVIEW → APPROVED happy path
 *       stamps reviewer/approver, and REJECTED requires a reason.</li>
 *   <li>Attestation PENDING → ATTESTED stamps attestedAt; double-attest
 *       refused; decline requires a reason.</li>
 *   <li>Expiry sweep flips PUBLISHED → EXPIRED for policies whose
 *       expiryDate is in the past.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PolicyManagementServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private PolicyDocumentRepository docs;
    @Mock private PolicyExceptionRepository exceptions;
    @Mock private PolicyAttestationRepository attestations;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ControlRepository controls;
    @Mock private PolicyDocumentControlLinkRepository controlLinks;

    @InjectMocks private PolicyManagementService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static PolicyDocument draft() {
        PolicyDocument p = new PolicyDocument();
        p.setId(1L);
        p.setOrganisationId(ORG_ID);
        p.setPolicyCode("ISMS-001");
        p.setVersion("1.0");
        p.setTitleEn("Information Security Policy");
        p.setCategory("CYBER");
        p.setStatus("DRAFT");
        return p;
    }

    @Test void policy_publishedRequiresEffectiveDate() {
        PolicyDocument p = draft();
        p.setStatus("APPROVED");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.transition(1L, "PUBLISHED", "alice"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("effective_date");
    }

    @Test void policy_publishedStampsPublishedAt() {
        PolicyDocument p = draft();
        p.setStatus("APPROVED");
        p.setEffectiveDate(LocalDate.now());
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyDocument out = service.transition(1L, "PUBLISHED", "alice");
        assertThat(out.getStatus()).isEqualTo("PUBLISHED");
        assertThat(out.getPublishedAt()).isNotNull();
    }

    @Test void policy_illegalTransitionRejected() {
        PolicyDocument p = draft();
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        // DRAFT → PUBLISHED is not allowed (must go via REVIEW + APPROVED)
        assertThatThrownBy(() -> service.transition(1L, "PUBLISHED", "alice"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ── C-6 segregation of duty on approval ──────────────────────────────────

    @Test void policy_approvalBlockedForAuthor_segregationOfDuty() {
        PolicyDocument p = draft();
        p.setStatus("REVIEW");
        p.setCreatedBy("alice");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        // alice authored it → she may not approve it (no admin override).
        assertThatThrownBy(() -> service.transition(1L, "APPROVED", "alice", false))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Segregation of duty");
    }

    @Test void policy_approvalAllowedForAdminOverride() {
        PolicyDocument p = draft();
        p.setStatus("REVIEW");
        p.setCreatedBy("alice");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // A compliance/platform admin may approve on the author's behalf.
        PolicyDocument out = service.transition(1L, "APPROVED", "alice", true);
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApproverName()).isEqualTo("alice");
    }

    @Test void policy_approvalAllowedForDifferentReviewer() {
        PolicyDocument p = draft();
        p.setStatus("REVIEW");
        p.setCreatedBy("alice");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // bob is not the author/owner → approval allowed without override.
        PolicyDocument out = service.transition(1L, "APPROVED", "bob", false);
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApproverName()).isEqualTo("bob");
    }

    @Test void policy_deletePublishedRefused() {
        PolicyDocument p = draft();
        p.setStatus("PUBLISHED");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PUBLISHED");
    }

    @Test void exception_approvalStampsApprover() {
        PolicyException e = new PolicyException();
        e.setId(10L);
        e.setOrganisationId(ORG_ID);
        e.setPolicyId(1L);
        e.setStatus("UNDER_REVIEW");
        when(exceptions.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(e));
        when(exceptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyException out = service.transitionException(10L, "APPROVED", "bob", null);
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApproverName()).isEqualTo("bob");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test void exception_rejectionRequiresReason() {
        PolicyException e = new PolicyException();
        e.setId(10L);
        e.setOrganisationId(ORG_ID);
        e.setStatus("UNDER_REVIEW");
        when(exceptions.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.transitionException(10L, "REJECTED", "bob", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    @Test void attestation_attestStampsTimestamp() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setPolicyId(1L);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));
        when(attestations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // SEC-CMP-004: completed by the assignee herself ("carol"), no admin override.
        PolicyAttestation out = service.attest(50L, "EV-001", "carol", false);
        assertThat(out.getStatus()).isEqualTo("ATTESTED");
        assertThat(out.getAttestedAt()).isNotNull();
        assertThat(out.getEvidenceRef()).isEqualTo("EV-001");
        assertThat(out.getUpdatedBy()).isEqualTo("carol");
    }

    @Test void attestation_doubleAttestRefused() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setUserUsername("carol");
        a.setStatus("ATTESTED");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.attest(50L, null, "carol", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING");
    }

    @Test void attestation_declineRequiresReason() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.decline(50L, "   ", "carol", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
    }

    // SEC-CMP-004: an attestation-of-record may only be completed by its assignee.
    @Test void attestation_attestByNonAssigneeRejected() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));

        // 'bob' is not the assignee and holds no admin override → rejected, status unchanged.
        assertThatThrownBy(() -> service.attest(50L, "EV-001", "bob", false))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("assignee");
        assertThat(a.getStatus()).isEqualTo("PENDING");
    }

    // SEC-CMP-004: a compliance/platform admin may complete on the assignee's behalf.
    @Test void attestation_attestByAdminOverrideAllowed() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));
        when(attestations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PolicyAttestation out = service.attest(50L, "EV-001", "admin", true);
        assertThat(out.getStatus()).isEqualTo("ATTESTED");
        // Assignee-of-record is preserved; the acting admin is captured separately.
        assertThat(out.getUserUsername()).isEqualTo("carol");
        assertThat(out.getUpdatedBy()).isEqualTo("admin");
    }

    // SEC-CMP-004: decline by a non-assignee without override is rejected.
    @Test void attestation_declineByNonAssigneeRejected() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.decline(50L, "no longer applicable", "bob", false))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("assignee");
        assertThat(a.getStatus()).isEqualTo("PENDING");
    }

    @Test void expirySweep_flipsExpiredPublished() {
        PolicyDocument p1 = draft(); p1.setId(100L); p1.setStatus("PUBLISHED");
        PolicyDocument p2 = draft(); p2.setId(101L); p2.setStatus("PUBLISHED");
        when(docs.findExpiredPublished(anyLong(), any())).thenReturn(List.of(p1, p2));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Long> flipped = service.runExpirySweep();
        assertThat(flipped).containsExactly(100L, 101L);
        assertThat(p1.getStatus()).isEqualTo("EXPIRED");
        assertThat(p2.getStatus()).isEqualTo("EXPIRED");
    }

    @Test void create_duplicateVersionRejected() {
        PolicyDocument body = new PolicyDocument();
        body.setPolicyCode("ISMS-001");
        body.setVersion("1.0");
        body.setTitleEn("Information Security Policy");
        body.setCategory("CYBER");
        when(docs.findByOrganisationIdAndPolicyCodeAndVersion(ORG_ID, "ISMS-001", "1.0"))
            .thenReturn(Optional.of(draft()));

        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void create_forcesDRAFT_evenIfBodyRequestsPublished() {
        // PM-C2: a client cannot create a policy already PUBLISHED and skip the
        // review/approval state machine + SoD + effective-date publish guard.
        PolicyDocument body = new PolicyDocument();
        body.setPolicyCode("ISMS-002");
        body.setVersion("1.0");
        body.setTitleEn("Access Control Policy");
        body.setStatus("PUBLISHED");                 // attempted bypass
        when(docs.findByOrganisationIdAndPolicyCodeAndVersion(ORG_ID, "ISMS-002", "1.0"))
            .thenReturn(Optional.empty());
        when(docs.save(any(PolicyDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(body).getStatus()).isEqualTo("DRAFT");
    }

    // ── CH-3 / PM-M2: audit coverage on previously-silent policy ops ────────────

    @Test void update_writes_audit_log() {
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(draft()));
        when(docs.save(any(PolicyDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyDocument body = new PolicyDocument();
        body.setSummaryEn("edited summary");
        service.update(1L, body);

        verify(audit).log(eq("UPDATE"), eq("PolicyDocument"), eq(1L), any(String.class));
    }

    @Test void delete_writes_audit_log() {
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(draft())); // DRAFT → deletable
        when(docs.save(any(PolicyDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);

        verify(audit).log(eq("DELETE"), eq("PolicyDocument"), eq(1L), any(String.class));
    }

    // ── F7 / F8 emission contract ─────────────────────────────────────────────

    @Test void publish_emits_POLICY_PUBLISHED_with_previousStatus_and_writes_audit() {
        PolicyDocument p = draft();
        p.setStatus("APPROVED");
        p.setEffectiveDate(LocalDate.now());
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transition(1L, "PUBLISHED", "alice");

        verify(audit).log(eq("TRANSITION"), eq("PolicyDocument"), eq(1L), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("POLICY_PUBLISHED");
        assertThat(emitted.entityType()).isEqualTo("PolicyDocument");
        assertThat(emitted.payload()).containsEntry("policyCode", "ISMS-001");
        assertThat(emitted.payload()).containsEntry("status", "PUBLISHED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "APPROVED");
        assertThat(emitted.payload()).containsEntry("category", "CYBER");
    }

    @Test void exception_approval_emits_POLICY_EXCEPTION_APPROVED() {
        PolicyException e = new PolicyException();
        e.setId(10L);
        e.setOrganisationId(ORG_ID);
        e.setPolicyId(1L);
        e.setStatus("UNDER_REVIEW");
        e.setRiskLevel("HIGH");
        when(exceptions.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(e));
        when(exceptions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionException(10L, "APPROVED", "bob", null);

        verify(audit).log(eq("TRANSITION"), eq("PolicyException"), eq(10L), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("POLICY_EXCEPTION_APPROVED");
        assertThat(emitted.payload()).containsEntry("riskLevel", "HIGH");
        assertThat(emitted.payload()).containsEntry("status", "APPROVED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "UNDER_REVIEW");
    }

    @Test void attestation_attest_emits_POLICY_ATTESTED() {
        PolicyAttestation a = new PolicyAttestation();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setPolicyId(1L);
        a.setUserUsername("carol");
        a.setStatus("PENDING");
        when(attestations.findByIdAndOrganisationId(50L, ORG_ID)).thenReturn(Optional.of(a));
        when(attestations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.attest(50L, "EV-001", "carol", false);

        verify(audit).log(eq("ATTEST"), eq("PolicyAttestation"), eq(50L), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("POLICY_ATTESTED");
        assertThat(emitted.payload()).containsEntry("status", "ATTESTED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "PENDING");
        assertThat(emitted.payload()).containsEntry("userUsername", "carol");
        assertThat(emitted.payload()).containsEntry("evidenceRef", "EV-001");
    }

    @Test void expirySweep_emits_POLICY_EXPIRED_per_row() {
        PolicyDocument p1 = draft(); p1.setId(100L); p1.setStatus("PUBLISHED");
        PolicyDocument p2 = draft(); p2.setId(101L); p2.setStatus("PUBLISHED");
        when(docs.findExpiredPublished(anyLong(), any())).thenReturn(List.of(p1, p2));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runExpirySweep();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).hasSize(2);
        assertThat(ev.getAllValues()).allSatisfy(e -> {
            assertThat(e.triggerEvent()).isEqualTo("POLICY_EXPIRED");
            assertThat(e.payload()).containsEntry("status", "EXPIRED");
            assertThat(e.payload()).containsEntry("previousStatus", "PUBLISHED");
        });
        verify(audit, atLeastOnce()).log(eq("EXPIRY_SWEEP"), eq("PolicyDocument"), any(), any(String.class));
    }

    // ── C-12: free-text control cross-link resolution ──────────────────────────

    @Test void resolveControls_matchesKnownCodesAndFlagsUnknown() {
        Control c1 = ctl(11L, "1-1-1", "Governance", "الحوكمة", "NCA ECC v2.0");
        Control c2 = ctl(12L, "2-3-1", "Asset management", "إدارة الأصول", "NCA ECC v2.0");
        when(controls.findByOrganisationIdAndCodeIn(eq(ORG_ID), any())).thenReturn(List.of(c1, c2));

        List<PolicyManagementService.ResolvedControl> out =
            service.resolveControls("1-1-1, 2-3-1 , 9-9-9");

        assertThat(out).hasSize(3);
        assertThat(out.get(0).code()).isEqualTo("1-1-1");
        assertThat(out.get(0).matched()).isTrue();
        assertThat(out.get(0).controlId()).isEqualTo(11L);
        assertThat(out.get(0).nameEn()).isEqualTo("Governance");
        assertThat(out.get(0).framework()).isEqualTo("NCA ECC v2.0");
        assertThat(out.get(1).code()).isEqualTo("2-3-1");
        assertThat(out.get(1).matched()).isTrue();
        // unknown code surfaces as unmatched (stale/typo reference)
        assertThat(out.get(2).code()).isEqualTo("9-9-9");
        assertThat(out.get(2).matched()).isFalse();
        assertThat(out.get(2).controlId()).isNull();
    }

    @Test void resolveControls_blankReturnsEmpty() {
        assertThat(service.resolveControls(null)).isEmpty();
        assertThat(service.resolveControls("   ")).isEmpty();
    }

    // ── C-12 (expand phase): FK control links ─────────────────────────────────

    @Test void resolveControlsForPolicy_prefersFkLinksOverFreeText() {
        PolicyDocument p = draft();
        p.setOrganisationId(ORG_ID);
        p.setRelatedControls("LEGACY-1, LEGACY-2");   // legacy free-text (would be unmatched)
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(controlLinks.findByOrganisationIdAndPolicyDocumentId(ORG_ID, 1L))
            .thenReturn(List.of(new PolicyDocumentControlLink(ORG_ID, 1L, 11L)));
        when(controls.findAllById(List.of(11L)))
            .thenReturn(List.of(ctl(11L, "NCA-2-12-3", "Network security", "أمن الشبكة", "NCA ECC")));

        List<PolicyManagementService.ResolvedControl> out = service.resolveControlsForPolicy(1L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo("NCA-2-12-3");
        assertThat(out.get(0).matched()).isTrue();
        assertThat(out.get(0).controlId()).isEqualTo(11L);
        // FK links win — the legacy free-text path is not consulted
        verify(controls, never()).findByOrganisationIdAndCodeIn(anyLong(), any());
    }

    @Test void resolveControlsForPolicy_fallsBackToFreeTextWhenNoLinks() {
        PolicyDocument p = draft();
        p.setOrganisationId(ORG_ID);
        p.setRelatedControls("NCA-2-12-3, BOGUS-1");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        when(controlLinks.findByOrganisationIdAndPolicyDocumentId(ORG_ID, 1L)).thenReturn(List.of());
        when(controls.findByOrganisationIdAndCodeIn(eq(ORG_ID), any()))
            .thenReturn(List.of(ctl(50L, "NCA-2-12-3", "Network security", "أمن", "NCA ECC")));

        List<PolicyManagementService.ResolvedControl> out = service.resolveControlsForPolicy(1L);

        assertThat(out).hasSize(2);
        assertThat(out).anySatisfy(r -> { assertThat(r.code()).isEqualTo("NCA-2-12-3"); assertThat(r.matched()).isTrue(); });
        assertThat(out).anySatisfy(r -> { assertThat(r.code()).isEqualTo("BOGUS-1");   assertThat(r.matched()).isFalse(); });
    }

    @SuppressWarnings("unchecked")
    @Test void setControlLinks_addsValidRemovesStaleIgnoresForeignOrg() {
        PolicyDocument p = draft();
        p.setOrganisationId(ORG_ID);
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(p));
        Control c11 = ctl(11L, "C-11", "x", "س", "F");                 // valid — same org
        Control cForeign = new Control();
        cForeign.setId(99L); cForeign.setOrganisationId(999L); cForeign.setCode("C-99");  // foreign org — ignored
        when(controls.findAllById(List.of(11L, 99L))).thenReturn(List.of(c11, cForeign));
        when(controlLinks.findByOrganisationIdAndPolicyDocumentId(ORG_ID, 1L))
            .thenReturn(List.of(new PolicyDocumentControlLink(ORG_ID, 1L, 22L)))            // pre-sync (22 stale)
            .thenReturn(List.of(new PolicyDocumentControlLink(ORG_ID, 1L, 11L)));           // post-sync (for resolve)
        when(controls.findAllById(List.of(11L))).thenReturn(List.of(c11));

        service.setControlLinks(1L, List.of(11L, 99L));

        ArgumentCaptor<List<PolicyDocumentControlLink>> del = ArgumentCaptor.forClass(List.class);
        verify(controlLinks).deleteAll(del.capture());
        assertThat(del.getValue()).extracting(PolicyDocumentControlLink::getControlId).containsExactly(22L);

        ArgumentCaptor<List<PolicyDocumentControlLink>> add = ArgumentCaptor.forClass(List.class);
        verify(controlLinks).saveAll(add.capture());
        assertThat(add.getValue()).extracting(PolicyDocumentControlLink::getControlId).containsExactly(11L);
    }

    private static Control ctl(Long id, String code, String nameEn, String nameAr, String framework) {
        Control c = new Control();
        c.setId(id);
        c.setOrganisationId(ORG_ID);
        c.setCode(code);
        c.setNameEn(nameEn);
        c.setNameAr(nameAr);
        c.setFramework(framework);
        return c;
    }
}
