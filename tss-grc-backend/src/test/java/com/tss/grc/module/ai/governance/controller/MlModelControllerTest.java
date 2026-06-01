package com.tss.grc.module.ai.governance.controller;

import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.DecommissionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.LifecycleTransitionRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelRequest;
import com.tss.grc.module.ai.governance.dto.AiGovernanceDtos.MlModelResponse;
import com.tss.grc.module.ai.governance.service.MlModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Light controller smoke tests — verify dispatch to the service. The
 * {@code @PreAuthorize} role guards are enforced by Spring Security at
 * the proxy boundary; here we lock the request/response wiring shape
 * so refactors don't silently change the URL contract.
 */
@ExtendWith(MockitoExtension.class)
class MlModelControllerTest {

    private static final UUID MODEL_EX = UUID.randomUUID();

    @Mock private MlModelService service;
    @InjectMocks private MlModelController controller;

    private static MlModelResponse stub() {
        // 43 fields — order must match the record exactly:
        // id, externalId, modelCode,
        // titleEn, titleAr, descriptionEn, descriptionAr,
        // modelType, businessDomain, riskTier, lifecycleStage,
        // versionLabel, versionHistoryJson,
        // modelOwnerUserId, businessSponsorUserId, validatorUserId,
        // validationDate, productionDate, decommissionDate,
        // decommissionReasonEn, decommissionReasonAr,
        // trainingDataProvenanceEn, trainingDataProvenanceAr,
        // trainingDataSizeRecords, trainingDataPeriodStart, trainingDataPeriodEnd,
        // featureCount, externalDatasetUsed, externalDatasetProvenance,
        // framework, hosting, inferenceLatencyMs, trainingComputeHours,
        // hasPdplDpia, pdplDpiaId, hasFairnessAudit, lastFairnessAuditDate,
        // hasExplainabilityDoc, isHighRiskEuAiAct,
        // sdaiaAiEthicsCompliance, samaAiPrinciplesCompliance,
        // createdAt, updatedAt
        return new MlModelResponse(
                1L, MODEL_EX, "MODEL-2026-001",
                "Credit", "تمويل", null, null,
                "CLASSIFICATION", "CREDIT_DECISION", "CRITICAL", "IN_PRODUCTION",
                null, null,
                1L, 1L, null,
                null, null, null,
                null, null,
                null, null,
                null, null, null,
                null, false, null,
                "XGBOOST", "AZURE_KSA", 45, null,
                null, null, null, null, null,
                true, null, true, null,
                true, false,
                "COMPLIANT", "COMPLIANT",
                null, null
        );
    }

    @Test
    void list_dispatchesToService() {
        when(service.list()).thenReturn(List.of(stub()));

        List<MlModelResponse> out = controller.list();

        assertThat(out).hasSize(1);
        verify(service).list();
    }

    @Test
    void get_dispatchesToService() {
        when(service.get(MODEL_EX)).thenReturn(stub());

        MlModelResponse out = controller.get(MODEL_EX.toString());

        assertThat(out.modelCode()).isEqualTo("MODEL-2026-001");
        verify(service).get(MODEL_EX);
    }

    @Test
    void create_returns201AndBody() {
        when(service.create(any(MlModelRequest.class))).thenReturn(stub());

        MlModelRequest req = new MlModelRequest(
                null, "Credit", null, null, null,
                "CLASSIFICATION", "CREDIT_DECISION", "CRITICAL",
                null, null, 1L, 1L,
                null, null, null,
                null, null, null, null, null, null, false, null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );
        ResponseEntity<MlModelResponse> resp = controller.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().modelCode()).isEqualTo("MODEL-2026-001");
    }

    @Test
    void transition_dispatches() {
        when(service.transition(any(UUID.class), any(LifecycleTransitionRequest.class)))
                .thenReturn(stub());

        MlModelResponse out = controller.transition(MODEL_EX.toString(),
                new LifecycleTransitionRequest("IN_PRODUCTION"));

        assertThat(out).isNotNull();
        verify(service).transition(MODEL_EX, new LifecycleTransitionRequest("IN_PRODUCTION"));
    }

    @Test
    void decommission_dispatches() {
        when(service.decommission(any(UUID.class), any(DecommissionRequest.class)))
                .thenReturn(stub());

        MlModelResponse out = controller.decommission(MODEL_EX.toString(),
                new DecommissionRequest("Superseded", null, null));

        assertThat(out).isNotNull();
        verify(service).decommission(any(UUID.class), any(DecommissionRequest.class));
    }

    @Test
    void highRiskWithoutFairness_dispatches() {
        when(service.findHighRiskModelsWithoutFairnessAudit())
                .thenReturn(List.of(stub()));

        List<MlModelResponse> out = controller.highRiskWithoutFairness();

        assertThat(out).hasSize(1);
        verify(service).findHighRiskModelsWithoutFairnessAudit();
    }
}
