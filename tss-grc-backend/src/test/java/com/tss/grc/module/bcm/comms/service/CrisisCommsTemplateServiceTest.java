package com.tss.grc.module.bcm.comms.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.ApprovalTransitionRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.TemplateRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.TemplateResponse;
import com.tss.grc.module.bcm.comms.entity.CrisisCommsTemplateV2;
import com.tss.grc.module.bcm.comms.repository.CrisisCommsTemplateV2Repository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CrisisCommsTemplateService} (Slice 9-E).
 *
 * <p>Covers:
 * <ul>
 *   <li>Create — happy path, defaults to DRAFT, F8 event fires</li>
 *   <li>Approval state machine — full DRAFT → IN_REVIEW → APPROVED_BY_LEGAL
 *       → APPROVED_BY_CISO chain, plus reject-invalid-transition</li>
 *   <li>Search — by audience + scenario filter</li>
 *   <li>Tenant isolation — every read filters by org</li>
 *   <li>Validation — audience + scenario must be in the enum</li>
 *   <li>Edit guard — APPROVED_BY_CISO templates block updates</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CrisisCommsTemplateServiceTest {

    private static final Long ORG_ID = 9L;

    @Mock private CrisisCommsTemplateV2Repository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private CrisisCommsTemplateService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static TemplateRequest validRequest(String code, String audience, String scenario) {
        return new TemplateRequest(
            code, "Title EN", "العنوان", audience, scenario, "HIGH",
            "Subject EN", "الموضوع",
            "Body EN content", "محتوى النص العربي",
            "SMS EN", "رسالة SMS",
            "[]", "ISO 22301 §8.4");
    }

    private static CrisisCommsTemplateV2 existing(String status) {
        CrisisCommsTemplateV2 t = new CrisisCommsTemplateV2();
        t.setId(101L);
        t.setOrganisationId(ORG_ID);
        t.setExternalId(UUID.randomUUID());
        t.setTemplateCode("CCT-BRD-CRITICAL-001");
        t.setTitleEn("Board critical first notice");
        t.setTitleAr("الإشعار الأولي للمجلس");
        t.setAudience("BOARD");
        t.setScenarioCategory("GENERIC_MAJOR");
        t.setSeverityBand("CRITICAL");
        t.setSubjectEn("URGENT");
        t.setSubjectAr("عاجل");
        t.setBodyEn("body");
        t.setBodyAr("نص");
        t.setApprovalStatus(status);
        return t;
    }

    // ─── Create ──────────────────────────────────────────────────────

    @Test void create_happyPath_defaultsToDraft() {
        when(repo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(any(), any())).thenReturn(false);
        when(repo.save(any(CrisisCommsTemplateV2.class))).thenAnswer(inv -> {
            CrisisCommsTemplateV2 t = inv.getArgument(0);
            t.setId(101L);
            return t;
        });

        TemplateResponse out = service.create(validRequest("CCT-001", "BOARD", "GENERIC_MAJOR"));

        assertThat(out.approvalStatus()).isEqualTo("DRAFT");
        assertThat(out.audience()).isEqualTo("BOARD");
        assertThat(out.scenarioCategory()).isEqualTo("GENERIC_MAJOR");
        assertThat(out.organisationId()).isEqualTo(ORG_ID);

        // F8 event fired
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("CRISIS_COMMS_TEMPLATE_CREATED");
    }

    @Test void create_invalidAudience_rejected() {
        TemplateRequest req = validRequest("CCT-002", "BOGUS", "GENERIC_MAJOR");
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid audience");
    }

    @Test void create_invalidScenario_rejected() {
        TemplateRequest req = validRequest("CCT-003", "BOARD", "BOGUS_SCENARIO");
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid scenarioCategory");
    }

    @Test void create_duplicateCode_rejected() {
        when(repo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(ORG_ID, "CCT-001"))
            .thenReturn(true);
        assertThatThrownBy(() -> service.create(validRequest("CCT-001", "BOARD", "GENERIC_MAJOR")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    // ─── Approval state machine ──────────────────────────────────────

    @Test void transition_fullApprovalChain() {
        // DRAFT → IN_REVIEW → APPROVED_BY_LEGAL → APPROVED_BY_CISO
        CrisisCommsTemplateV2 t = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, t.getExternalId()))
            .thenReturn(Optional.of(t));
        when(repo.save(any(CrisisCommsTemplateV2.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateResponse step1 = service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("IN_REVIEW", null));
        assertThat(step1.approvalStatus()).isEqualTo("IN_REVIEW");

        f223AuthAs("Mohammed.Al-Otaibi", 5L);
        TemplateResponse step2 = service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("APPROVED_BY_LEGAL", null));
        assertThat(step2.approvalStatus()).isEqualTo("APPROVED_BY_LEGAL");
        assertThat(step2.approvedByLegalAt()).isNotNull();
        // F2-223 — the legal sign-off names the authenticated principal.
        assertThat(step2.approvedByLegalName()).contains("Mohammed");

        TemplateResponse step3 = service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("APPROVED_BY_CISO", null));
        assertThat(step3.approvalStatus()).isEqualTo("APPROVED_BY_CISO");
        assertThat(step3.approvedByCisoAt()).isNotNull();
    }

    @Test void transition_rejectInvalid_draftToCisoSkippingReview() {
        // DRAFT → APPROVED_BY_CISO is not allowed.
        CrisisCommsTemplateV2 t = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, t.getExternalId()))
            .thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("APPROVED_BY_CISO", null)))
            .isInstanceOf(IllegalTransitionException.class);
        verify(repo, never()).save(any());
    }

    @Test void transition_cisoCanReopenToInReview() {
        // APPROVED_BY_CISO → IN_REVIEW clears the CISO stamp (legal stays).
        CrisisCommsTemplateV2 t = existing("APPROVED_BY_CISO");
        t.setApprovedByCisoAt(java.time.LocalDateTime.now());
        t.setApprovedByCisoName("CISO");
        t.setApprovedByLegalAt(java.time.LocalDateTime.now());
        t.setApprovedByLegalName("Legal");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, t.getExternalId()))
            .thenReturn(Optional.of(t));
        when(repo.save(any(CrisisCommsTemplateV2.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateResponse out = service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("IN_REVIEW", null));

        assertThat(out.approvalStatus()).isEqualTo("IN_REVIEW");
        assertThat(out.approvedByCisoAt()).isNull();
        // Legal stamp preserved (re-open from CISO only clears CISO stamp).
        assertThat(out.approvedByLegalName()).isEqualTo("Legal");
    }

    @Test void transition_retiredCanBeReinstated() {
        CrisisCommsTemplateV2 t = existing("RETIRED");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, t.getExternalId()))
            .thenReturn(Optional.of(t));
        when(repo.save(any(CrisisCommsTemplateV2.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateResponse out = service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("IN_REVIEW", null));
        assertThat(out.approvalStatus()).isEqualTo("IN_REVIEW");
    }

    @Test void transition_emitsF8Event() {
        CrisisCommsTemplateV2 t = existing("DRAFT");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, t.getExternalId()))
            .thenReturn(Optional.of(t));
        when(repo.save(any(CrisisCommsTemplateV2.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(t.getExternalId(),
            new ApprovalTransitionRequest("IN_REVIEW", null));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("CRISIS_COMMS_TEMPLATE_TRANSITIONED");
        assertThat(ev.payload()).containsEntry("approvalStatus", "IN_REVIEW");
        assertThat(ev.payload()).containsEntry("previousApprovalStatus", "DRAFT");
    }

    // ─── Update guard on APPROVED_BY_CISO ────────────────────────────

    @Test void update_blockedOnApprovedByCiso() {
        CrisisCommsTemplateV2 t = existing("APPROVED_BY_CISO");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, t.getExternalId()))
            .thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.update(t.getExternalId(),
            validRequest("CCT-001", "BOARD", "GENERIC_MAJOR")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVED_BY_CISO");
    }

    // ─── Search filters ──────────────────────────────────────────────

    @Test void findByAudienceAndScenario_filtersByBoth() {
        when(repo.findByOrganisationIdAndAudienceAndScenarioCategoryAndIsDeletedFalseOrderByTemplateCodeAsc(
                ORG_ID, "CUSTOMERS", "DATA_BREACH"))
            .thenReturn(List.of(existing("APPROVED_BY_CISO")));

        List<TemplateResponse> out = service.findByAudienceAndScenario("CUSTOMERS", "DATA_BREACH");
        assertThat(out).hasSize(1);
        verify(repo, times(1))
            .findByOrganisationIdAndAudienceAndScenarioCategoryAndIsDeletedFalseOrderByTemplateCodeAsc(
                ORG_ID, "CUSTOMERS", "DATA_BREACH");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByAudienceAscTemplateCodeAsc(ORG_ID))
            .thenReturn(List.of());
        service.list();
        verify(repo, times(1))
            .findByOrganisationIdAndIsDeletedFalseOrderByAudienceAscTemplateCodeAsc(ORG_ID);
    }

    @Test void create_setsOrgFromTenantContext() {
        when(repo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(any(), any())).thenReturn(false);
        ArgumentCaptor<CrisisCommsTemplateV2> captor = ArgumentCaptor.forClass(CrisisCommsTemplateV2.class);
        when(repo.save(captor.capture())).thenAnswer(inv -> {
            CrisisCommsTemplateV2 t = inv.getArgument(0);
            t.setId(101L);
            return t;
        });

        service.create(validRequest("CCT-001", "BOARD", "GENERIC_MAJOR"));

        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    /**
     * F2-223 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void f223AuthAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }
}
