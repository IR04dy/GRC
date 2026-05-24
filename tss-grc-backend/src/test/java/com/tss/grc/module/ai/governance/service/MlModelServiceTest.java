package com.tss.grc.module.ai.governance.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DecommissionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.LifecycleTransitionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelResponse;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MlModelService — Sprint 11 AI/ML Model Registry test backlog.
 *
 * Locks the tri-track-compliance state machine (PROPOSED → DEVELOPMENT →
 * VALIDATION → APPROVED_FOR_PROD → IN_PRODUCTION → MONITORING ⇄ REVISION
 * → DECOMMISSIONED), the HIGH/CRITICAL approval gate (validator +
 * fairness + DPIA + explainability), tenant isolation, and the F8
 * event contract.
 */
@ExtendWith(MockitoExtension.class)
class MlModelServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final UUID MODEL_EX  = UUID.randomUUID();
    private static final Long MODEL_ID  = 42L;

    @Mock private MlModelRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MlModelService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static MlModel model(String stage, String tier) {
        MlModel m = new MlModel();
        m.setId(MODEL_ID);
        m.setExternalId(MODEL_EX);
        m.setOrganisationId(ORG_ID);
        m.setModelCode("MODEL-2026-001");
        m.setTitleEn("Credit Decision Engine v2.3");
        m.setTitleAr("محرّك قرارات التمويل الشخصي - الإصدار 2.3");
        m.setModelType("CLASSIFICATION");
        m.setBusinessDomain("CREDIT_DECISION");
        m.setRiskTier(tier);
        m.setLifecycleStage(stage);
        m.setModelOwnerUserId(1L);
        m.setBusinessSponsorUserId(1L);
        m.setExternalDatasetUsed(false);
        m.setHasPdplDpia(false);
        m.setHasFairnessAudit(false);
        m.setHasExplainabilityDoc(false);
        m.setIsHighRiskEuAiAct(false);
        m.setSdaiaAiEthicsCompliance("NOT_ASSESSED");
        m.setSamaAiPrinciplesCompliance("NOT_ASSESSED");
        return m;
    }

    private static MlModelRequest req() {
        return new MlModelRequest(
                null, "Credit Decision Engine v2.3",
                "محرّك قرارات التمويل الشخصي - الإصدار 2.3",
                "XGBoost probability-of-default", "نموذج XGBoost",
                "CLASSIFICATION", "CREDIT_DECISION", "CRITICAL",
                "2.3.0", null,
                1L, 1L,
                null, null, null,
                null, null, null, null, null, null, false, null,
                "XGBOOST", "AZURE_KSA", 45, null,
                false, null, false, null, false, false,
                "NOT_ASSESSED", "NOT_ASSESSED"
        );
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

    // ─── Create ───────────────────────────────────────────────────

    @Test
    void create_stampsTenantAndDefaultsToProposed() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(MlModel.class))).thenAnswer(assignId(seq));

        MlModelResponse out = service.create(req());

        assertThat(out.lifecycleStage()).isEqualTo("PROPOSED");
        assertThat(out.modelCode()).startsWith("MODEL-");

        ArgumentCaptor<MlModel> cap = ArgumentCaptor.forClass(MlModel.class);
        verify(repo).save(cap.capture());
        MlModel saved = cap.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getRiskTier()).isEqualTo("CRITICAL");
        assertThat(saved.getBusinessDomain()).isEqualTo("CREDIT_DECISION");
        verify(audit).log(eq("CREATE"), eq("MlModel"), anyLong(), anyString());
    }

    @Test
    void create_publishesModelRegisteredEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(MlModel.class))).thenAnswer(assignId(seq));

        service.create(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("MODEL_REGISTERED");
        assertThat(ev.entityType()).isEqualTo("MlModel");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("riskTier", "CRITICAL");
        assertThat(ev.payload()).containsEntry("businessDomain", "CREDIT_DECISION");
        assertThat(ev.payload()).containsEntry("lifecycleStage", "PROPOSED");
    }

    @Test
    void create_honoursExplicitModelCode() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(MlModel.class))).thenAnswer(assignId(seq));

        MlModelRequest r = new MlModelRequest(
                "MODEL-2026-099", "X", null, null, null,
                "CLASSIFICATION", "CREDIT_DECISION", "LOW",
                null, null, 1L, 1L,
                null, null, null,
                null, null, null, null, null, null, false, null,
                null, null, null, null,
                null, null, null, null, null, null, null, null
        );
        MlModelResponse out = service.create(r);
        assertThat(out.modelCode()).isEqualTo("MODEL-2026-099");
    }

    // ─── Update ───────────────────────────────────────────────────

    @Test
    void update_rejectsOnDecommissioned() {
        MlModel m = model("DECOMMISSIONED", "HIGH");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.update(MODEL_EX, req()))
                .isInstanceOf(IllegalTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    // ─── Lifecycle transitions ────────────────────────────────────

    @Test
    void transition_proposedToDevelopmentSucceeds() {
        MlModel m = model("PROPOSED", "LOW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));
        when(repo.save(any(MlModel.class))).thenAnswer(inv -> inv.getArgument(0));

        MlModelResponse out = service.transition(MODEL_EX, new LifecycleTransitionRequest("DEVELOPMENT"));

        assertThat(out.lifecycleStage()).isEqualTo("DEVELOPMENT");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("MODEL_LIFECYCLE_TRANSITION");
        assertThat(cap.getValue().payload()).containsEntry("previousStage", "PROPOSED");
    }

    @Test
    void transition_rejectsIllegalJump() {
        MlModel m = model("PROPOSED", "LOW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));

        // PROPOSED → IN_PRODUCTION is not allowed (must traverse intermediate stages).
        assertThatThrownBy(() ->
                service.transition(MODEL_EX, new LifecycleTransitionRequest("IN_PRODUCTION")))
                .isInstanceOf(IllegalTransitionException.class);
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void transition_approvedForProd_blockedWhenHighRiskMissingFairnessDpiaExplainability() {
        MlModel m = model("VALIDATION", "HIGH");
        // Validator + date are set but compliance flags are not.
        m.setValidatorUserId(7L);
        m.setValidationDate(LocalDate.now());
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));

        assertThatThrownBy(() ->
                service.transition(MODEL_EX, new LifecycleTransitionRequest("APPROVED_FOR_PROD")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has_fairness_audit")
                .hasMessageContaining("has_pdpl_dpia")
                .hasMessageContaining("has_explainability_doc");
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void transition_approvedForProd_blockedWhenCriticalRiskMissingValidator() {
        MlModel m = model("VALIDATION", "CRITICAL");
        // Compliance flags are set but no validator yet.
        m.setHasFairnessAudit(true);
        m.setHasPdplDpia(true);
        m.setHasExplainabilityDoc(true);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));

        assertThatThrownBy(() ->
                service.transition(MODEL_EX, new LifecycleTransitionRequest("APPROVED_FOR_PROD")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validator_user_id");
    }

    @Test
    void transition_approvedForProd_succeedsForLowRiskWithoutFlags() {
        // LOW tier doesn't trigger the gate.
        MlModel m = model("VALIDATION", "LOW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));
        when(repo.save(any(MlModel.class))).thenAnswer(inv -> inv.getArgument(0));

        MlModelResponse out = service.transition(MODEL_EX, new LifecycleTransitionRequest("APPROVED_FOR_PROD"));

        assertThat(out.lifecycleStage()).isEqualTo("APPROVED_FOR_PROD");
    }

    @Test
    void transition_approvedForProd_succeedsForHighRiskWhenAllGatesPassed() {
        MlModel m = model("VALIDATION", "HIGH");
        m.setValidatorUserId(7L);
        m.setValidationDate(LocalDate.now());
        m.setHasFairnessAudit(true);
        m.setHasPdplDpia(true);
        m.setHasExplainabilityDoc(true);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));
        when(repo.save(any(MlModel.class))).thenAnswer(inv -> inv.getArgument(0));

        MlModelResponse out = service.transition(MODEL_EX, new LifecycleTransitionRequest("APPROVED_FOR_PROD"));

        assertThat(out.lifecycleStage()).isEqualTo("APPROVED_FOR_PROD");
        verify(eventPublisher).publishEvent(any(DomainEvent.class));
    }

    @Test
    void transition_approvedForProdToInProduction_stampsProductionDate() {
        MlModel m = model("APPROVED_FOR_PROD", "HIGH");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));
        when(repo.save(any(MlModel.class))).thenAnswer(inv -> inv.getArgument(0));

        MlModelResponse out = service.transition(MODEL_EX, new LifecycleTransitionRequest("IN_PRODUCTION"));

        assertThat(out.lifecycleStage()).isEqualTo("IN_PRODUCTION");
        assertThat(out.productionDate()).isEqualTo(LocalDate.now());
    }

    // ─── Decommission ─────────────────────────────────────────────

    @Test
    void decommission_stampsReasonAndDate() {
        MlModel m = model("IN_PRODUCTION", "CRITICAL");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));
        when(repo.save(any(MlModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DecommissionRequest req = new DecommissionRequest(
                "Superseded by v3.0",
                "أحلّ محلّه الإصدار 3.0",
                LocalDate.of(2026, 5, 30));
        MlModelResponse out = service.decommission(MODEL_EX, req);

        assertThat(out.lifecycleStage()).isEqualTo("DECOMMISSIONED");
        assertThat(out.decommissionDate()).isEqualTo(LocalDate.of(2026, 5, 30));
        assertThat(out.decommissionReasonEn()).isEqualTo("Superseded by v3.0");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("MODEL_DECOMMISSIONED");
        assertThat(cap.getValue().payload()).containsEntry("previousStage", "IN_PRODUCTION");
    }

    @Test
    void decommission_alreadyDecommissionedRejected() {
        MlModel m = model("DECOMMISSIONED", "LOW");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.decommission(MODEL_EX,
                new DecommissionRequest("again", null, null)))
                .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Tenant isolation ─────────────────────────────────────────

    @Test
    void mustGet_returnsNotFoundWhenWrongTenant() {
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, MODEL_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(MODEL_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void list_isOrgScoped() {
        TenantContext.setCurrentTenant(OTHER_ORG);
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(OTHER_ORG))
                .thenReturn(java.util.List.of());

        assertThat(service.list()).isEmpty();
        verify(repo).findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(OTHER_ORG);
    }

    // ─── Critical-drift hook ─────────────────────────────────────

    @Test
    void flipToRevisionDueToCriticalDrift_flipsAndEmits() {
        MlModel m = model("IN_PRODUCTION", "CRITICAL");
        when(repo.save(any(MlModel.class))).thenAnswer(inv -> inv.getArgument(0));

        MlModel out = service.flipToRevisionDueToCriticalDrift(m, "AUC < threshold");

        assertThat(out.getLifecycleStage()).isEqualTo("REVISION");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("MODEL_LIFECYCLE_TRANSITION");
        assertThat(cap.getValue().payload()).containsEntry("previousStage", "IN_PRODUCTION");
    }

    @Test
    void flipToRevisionDueToCriticalDrift_noOpWhenAlreadyRevision() {
        MlModel m = model("REVISION", "CRITICAL");

        service.flipToRevisionDueToCriticalDrift(m, "second drift");

        verify(repo, never()).save(any(MlModel.class));
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
    }
}
