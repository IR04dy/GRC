package com.tss.grc.module.workflow.approval_chains.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.StepCreateRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateCreateRequest;
import com.tss.grc.module.workflow.approval_chains.dto.ApprovalChainDtos.TemplateResponse;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainStep;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainTemplate;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainStepRepository;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link ApprovalChainTemplateService} unit tests.
 *
 * <p>Covers the surface the Compliance Officer / Platform Admin
 * exercises: template CRUD + set-default exclusivity + step-validation.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalChainTemplateServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private ApprovalChainTemplateRepository templateRepo;
    @Mock private ApprovalChainStepRepository stepRepo;
    @Mock private AuditLogService audit;

    private ApprovalChainTemplateService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new ApprovalChainTemplateService(templateRepo, stepRepo, audit);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static StepCreateRequest step(int order, String type, String role) {
        return new StepCreateRequest(order, type, "Step " + order, N("الخطوة ") + order,
            role, null, null, true, true, 48, "PLATFORM_ADMIN");
    }

    private static TemplateCreateRequest validReq() {
        return new TemplateCreateRequest(
            "CHAIN-CAPA-CLOSURE",
            "CAPA Closure — Risk Owner → Dept Head → DPO → CISO",
            N("إغلاق الإجراءات التصحيحية — مالك المخاطرة ← رئيس الإدارة ← مسؤول حماية البيانات ← رئيس أمن المعلومات"),
            null, null, "CAPA_CLOSURE", "COMPLIANCE", true,
            List.of(
                step(1, "SERIAL", "RISK_OWNER"),
                step(2, "SERIAL", "DEPARTMENT_HEAD"),
                step(3, "SERIAL", "DPO"),
                step(4, "SERIAL", "CISO")
            ));
    }

    private static ApprovalChainTemplate existing(Long id, boolean isDefault) {
        ApprovalChainTemplate t = new ApprovalChainTemplate();
        t.setId(id);
        t.setOrganisationId(ORG_ID);
        t.setTemplateCode("CHAIN-EXIST-" + id);
        t.setTitleEn("Existing");
        t.setTitleAr(N("موجود"));
        t.setAppliesToModule("COMPLIANCE");
        t.setIsDefault(isDefault);
        return t;
    }

    private static String N(String s) { return s; }

    // ─── Create ──────────────────────────────────────────────────────

    @Test void create_persistsTemplateAndSteps() {
        when(templateRepo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(templateRepo.save(any(ApprovalChainTemplate.class))).thenAnswer(inv -> {
            ApprovalChainTemplate t = inv.getArgument(0);
            t.setId(101L);
            return t;
        });
        when(stepRepo.findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(ORG_ID, 101L))
            .thenReturn(List.of());

        TemplateResponse out = service.create(validReq());

        assertThat(out.templateCode()).isEqualTo("CHAIN-CAPA-CLOSURE");
        assertThat(out.titleAr()).contains("الإجراءات");
        verify(stepRepo, atLeast(4)).save(any(ApprovalChainStep.class));
    }

    @Test void create_duplicateCodeRejected() {
        when(templateRepo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(true);
        assertThatThrownBy(() -> service.create(validReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void create_emptyStepsRejected() {
        TemplateCreateRequest req = new TemplateCreateRequest(
            "CHAIN-X", "X", N("س"), null, null, null, "COMPLIANCE", false, List.of());
        when(templateRepo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one step");
    }

    @Test void create_stepWithoutMatcherRejected() {
        StepCreateRequest bad = new StepCreateRequest(
            1, "SERIAL", "Step 1", N("الخطوة 1"),
            null, null, null, true, true, 48, "PLATFORM_ADMIN");
        TemplateCreateRequest req = new TemplateCreateRequest(
            "CHAIN-X", "X", N("س"), null, null, null, "COMPLIANCE", false, List.of(bad));
        when(templateRepo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly one of");
    }

    @Test void create_stepWithTwoMatchersRejected() {
        StepCreateRequest bad = new StepCreateRequest(
            1, "SERIAL", "Step 1", N("الخطوة 1"),
            "RISK_OWNER", 42L, null, true, true, 48, "PLATFORM_ADMIN");
        TemplateCreateRequest req = new TemplateCreateRequest(
            "CHAIN-X", "X", N("س"), null, null, null, "COMPLIANCE", false, List.of(bad));
        when(templateRepo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("more than one matcher");
    }

    // ─── Set-default exclusivity ─────────────────────────────────────

    @Test void setDefault_demotesSiblingsInSameModule() {
        ApprovalChainTemplate target = existing(101L, false);
        ApprovalChainTemplate sibling1 = existing(102L, true);
        ApprovalChainTemplate sibling2 = existing(103L, true);
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L))
            .thenReturn(Optional.of(target));
        when(templateRepo.save(any(ApprovalChainTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepo.findByOrganisationIdAndAppliesToModuleAndIsDefaultTrueAndIsDeletedFalseAndIdNot(
            ORG_ID, "COMPLIANCE", 101L)).thenReturn(List.of(sibling1, sibling2));
        when(stepRepo.findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(ORG_ID, 101L))
            .thenReturn(List.of());

        TemplateResponse out = service.setDefault(101L);

        assertThat(out.isDefault()).isTrue();
        ArgumentCaptor<ApprovalChainTemplate> captor = ArgumentCaptor.forClass(ApprovalChainTemplate.class);
        verify(templateRepo, atLeast(3)).save(captor.capture());
        // siblings demoted
        long demoted = captor.getAllValues().stream()
            .filter(t -> (t.getId() == 102L || t.getId() == 103L) && Boolean.FALSE.equals(t.getIsDefault()))
            .count();
        assertThat(demoted).isEqualTo(2);
    }

    @Test void setDefault_requiresModuleScope() {
        ApprovalChainTemplate t = existing(101L, false);
        t.setAppliesToModule(null);
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L))
            .thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.setDefault(101L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("appliesToModule");
    }

    // ─── Soft delete ────────────────────────────────────────────────

    @Test void softDelete_marksIsDeleted() {
        ApprovalChainTemplate t = existing(101L, false);
        when(templateRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L))
            .thenReturn(Optional.of(t));

        service.softDelete(101L);

        ArgumentCaptor<ApprovalChainTemplate> captor = ArgumentCaptor.forClass(ApprovalChainTemplate.class);
        verify(templateRepo).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    // ─── Tenant isolation ───────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(templateRepo.findByOrganisationIdAndIsDeletedFalseOrderByTemplateCodeAsc(ORG_ID)).thenReturn(List.of());
        service.list();
        verify(templateRepo).findByOrganisationIdAndIsDeletedFalseOrderByTemplateCodeAsc(ORG_ID);
    }
}
