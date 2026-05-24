package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelAuditResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelAudit;
import com.tss.grc.module.ai.governance.repository.MlModelAuditRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MlModelAuditService — Sprint 11 test backlog.
 *
 * Locks the side-effect contract: a positive FAIRNESS audit flips
 * parent {@code has_fairness_audit} + stamps date. EXPLAINABILITY ditto.
 * Negative outcomes do not flip flags. F8 emission contract.
 */
@ExtendWith(MockitoExtension.class)
class MlModelAuditServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final UUID MODEL_EX  = UUID.randomUUID();
    private static final Long MODEL_ID  = 42L;

    @Mock private MlModelAuditRepository auditRepo;
    @Mock private MlModelService modelService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MlModelAuditService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static MlModel model() {
        MlModel m = new MlModel();
        m.setId(MODEL_ID);
        m.setExternalId(MODEL_EX);
        m.setOrganisationId(ORG_ID);
        m.setModelCode("MODEL-2026-001");
        m.setTitleEn("Credit");
        m.setRiskTier("CRITICAL");
        m.setLifecycleStage("IN_PRODUCTION");
        m.setHasFairnessAudit(false);
        m.setHasExplainabilityDoc(false);
        return m;
    }

    private static MlModelAuditRequest req(String type, String status) {
        return new MlModelAuditRequest(
                type, LocalDate.now(), 1L, null,
                "Demographic parity", "تكافؤ ديموغرافي",
                "Findings", "النتائج",
                status,
                "{\"demographic_parity_diff\":0.038}",
                null);
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Record ──────────────────────────────────────────────────

    @Test
    void recordFairnessAudit_flipsParentFlagAndStampsDate() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(seq));

        MlModelAuditResponse out = service.recordAudit(MODEL_EX, req("FAIRNESS", "PASSED"));

        assertThat(out.status()).isEqualTo("PASSED");
        assertThat(out.auditType()).isEqualTo("FAIRNESS");
        assertThat(m.getHasFairnessAudit()).isTrue();
        assertThat(m.getLastFairnessAuditDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void recordExplainabilityAudit_passedWithConditionsFlipsParent() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(seq));

        service.recordAudit(MODEL_EX, req("EXPLAINABILITY", "PASSED_WITH_CONDITIONS"));

        assertThat(m.getHasExplainabilityDoc()).isTrue();
        // Fairness flag stays untouched.
        assertThat(m.getHasFairnessAudit()).isFalse();
    }

    @Test
    void recordFailedFairnessAudit_doesNotFlipFlag() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(seq));

        service.recordAudit(MODEL_EX, req("FAIRNESS", "FAILED"));

        assertThat(m.getHasFairnessAudit()).isFalse();
        assertThat(m.getLastFairnessAuditDate()).isNull();
    }

    @Test
    void recordAudit_publishesEventWithMetadata() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(seq));

        service.recordAudit(MODEL_EX, req("FAIRNESS", "PASSED"));

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("MODEL_AUDIT_RECORDED");
        assertThat(ev.payload()).containsEntry("auditType", "FAIRNESS");
        assertThat(ev.payload()).containsEntry("status", "PASSED");
        assertThat(ev.payload()).containsEntry("modelCode", "MODEL-2026-001");
        assertThat(ev.payload()).containsEntry("riskTier", "CRITICAL");
    }

    @Test
    void recordAudit_capaLinkSurfacedInPayload() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        AtomicLong seq = new AtomicLong(100);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(seq));

        MlModelAuditRequest r = new MlModelAuditRequest(
                "FAIRNESS", LocalDate.now(), 1L, null,
                "method", "منهج",
                "findings", "النتائج",
                "FAILED",
                "{\"demographic_parity_diff\":0.18}",
                501L);

        MlModelAuditResponse out = service.recordAudit(MODEL_EX, r);
        assertThat(out.capaTaskId()).isEqualTo(501L);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().payload()).containsEntry("hasCapa", true);
    }

    // ─── Reads ──────────────────────────────────────────────────

    @Test
    void listAudits_isOrgAndModelScoped() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(auditRepo.findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByAuditDateDesc(
                ORG_ID, MODEL_ID))
                .thenReturn(List.of());

        assertThat(service.listAudits(MODEL_EX)).isEmpty();
        verify(auditRepo).findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByAuditDateDesc(
                ORG_ID, MODEL_ID);
    }

    @Test
    void getLatestByType_returnsNewest() {
        MlModel m = model();
        MlModelAudit a = new MlModelAudit();
        a.setId(7L);
        a.setExternalId(UUID.randomUUID());
        a.setModelId(MODEL_ID);
        a.setAuditType("FAIRNESS");
        a.setAuditDate(LocalDate.of(2026, 4, 1));
        a.setStatus("PASSED");
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(auditRepo.findTopByOrganisationIdAndModelIdAndAuditTypeAndIsDeletedFalseOrderByAuditDateDesc(
                ORG_ID, MODEL_ID, "FAIRNESS"))
                .thenReturn(Optional.of(a));

        Optional<MlModelAuditResponse> out = service.getLatestByType(MODEL_EX, "FAIRNESS");
        assertThat(out).isPresent();
        assertThat(out.get().auditDate()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    // ─── Tenant scope ───────────────────────────────────────────

    @Test
    void listAudits_runsUnderTenantContext() {
        TenantContext.setCurrentTenant(OTHER_ORG);
        MlModel otherTenantModel = model();
        otherTenantModel.setOrganisationId(OTHER_ORG);
        when(modelService.mustGet(MODEL_EX)).thenReturn(otherTenantModel);
        when(auditRepo.findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByAuditDateDesc(
                OTHER_ORG, MODEL_ID))
                .thenReturn(List.of());

        service.listAudits(MODEL_EX);
        verify(auditRepo).findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByAuditDateDesc(
                OTHER_ORG, MODEL_ID);
    }
}
