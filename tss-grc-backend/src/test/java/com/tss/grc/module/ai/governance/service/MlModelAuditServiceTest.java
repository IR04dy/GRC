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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    // ─── F2-182: the casing the CHECK constraint could not see ───────────────────────

    /**
     * The defect, stated as behaviour: a lower-case FAIRNESS audit with a lower-case PASSED status
     * satisfied {@code @NotBlank @Size(max = 40)}, satisfied {@code CK_ml_audit_type} and
     * {@code CK_ml_audit_status} (both on a case-insensitive collation), was stored verbatim — and
     * then matched neither side-effect condition. {@code has_fairness_audit} stayed false for an
     * audit that had happened, which is an EU AI Act Art. 9/15 and SDAIA attribute recorded as absent.
     */
    @Test
    void lowerCaseFairnessAudit_stillFlipsTheComplianceAttribute() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(new AtomicLong(100)));

        MlModelAuditResponse out = service.recordAudit(MODEL_EX, req("fairness", "passed"));

        assertThat(m.getHasFairnessAudit()).as(
            "this is the whole finding: the side effect must not depend on the caller's casing, "
            + "because the column cannot distinguish it and a machine caller decides it")
            .isTrue();
        assertThat(m.getLastFairnessAuditDate()).isNotNull();
        assertThat(out.auditType()).as(
            "and the value is NORMALISED, not merely matched — leaving 'fairness' in the column would "
            + "hide the row from getLatestByType, which queries audit_type directly, and from the "
            + "frontend's label map")
            .isEqualTo("FAIRNESS");
        assertThat(out.status()).isEqualTo("PASSED");
    }

    @Test
    void lowerCaseExplainabilityAudit_stillFlipsTheDocumentationAttribute() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(new AtomicLong(100)));

        service.recordAudit(MODEL_EX, req("  explainability  ", "remediated"));

        assertThat(m.getHasExplainabilityDoc()).as("surrounding whitespace is trimmed too").isTrue();
    }

    /**
     * ⚠ The negative control. Normalising must not make every audit satisfy the attribute — a FAILED
     * fairness audit is the case the flag exists to distinguish, and a mutation that replaced the
     * status check with {@code true} would pass every test above.
     */
    @Test
    void lowerCaseFailedAudit_doesNotFlipAnything() {
        MlModel m = model();
        when(modelService.mustGet(MODEL_EX)).thenReturn(m);
        when(auditRepo.save(any(MlModelAudit.class))).thenAnswer(assignId(new AtomicLong(100)));

        service.recordAudit(MODEL_EX, req("fairness", "failed"));

        assertThat(m.getHasFairnessAudit()).isFalse();
        assertThat(m.getLastFairnessAuditDate()).isNull();
    }

    @Test
    void aTokenOutsideTheVocabularyIs400NamingTheField_notA409FromTheConstraint() {
        when(modelService.mustGet(MODEL_EX)).thenReturn(model());

        assertThatThrownBy(() -> service.recordAudit(MODEL_EX, req("PENETRATION_TEST", "PASSED")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("auditType")
            .hasMessageContaining("PENETRATION_TEST");

        assertThatThrownBy(() -> service.recordAudit(MODEL_EX, req("FAIRNESS", "INCONCLUSIVE")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status");

        verify(auditRepo, never()).save(any());
    }

    /**
     * The literals the side effects compare against must be members of the vocabulary. F2-177's
     * {@code 'AUDIT_COMMITTE'} is one letter from a token that cannot ever match, and a comparison
     * against a token the column cannot hold is silently always false — the same failure mode as a
     * ghost role name.
     */
    @Test
    void theSideEffectLiteralsAreTokensTheColumnCanActuallyHold() {
        assertThat(MlModelAuditService.AUDIT_TYPE.tokens())
            .contains(MlModelAuditService.AUDIT_TYPE_FAIRNESS,
                      MlModelAuditService.AUDIT_TYPE_EXPLAINABILITY);
        assertThat(MlModelAuditService.AUDIT_STATUS.tokens())
            .containsAll(MlModelAuditService.SATISFYING_STATUSES);
        assertThat(MlModelAuditService.SATISFYING_STATUSES).as(
            "anti-vacuity: an empty set would make containsAll pass and the gate meaningless")
            .isNotEmpty();
        assertThat(MlModelAuditService.AUDIT_STATUS.tokens()).as(
            "and FAILED must NOT be satisfying, or the attribute means nothing")
            .contains("FAILED");
        assertThat(MlModelAuditService.SATISFYING_STATUSES).doesNotContain("FAILED");
    }
}
