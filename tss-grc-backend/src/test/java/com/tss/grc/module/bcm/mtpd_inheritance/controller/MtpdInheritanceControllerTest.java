package com.tss.grc.module.bcm.mtpd_inheritance.controller;

import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentDetailResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.ComputeRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.FindingResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.PublishRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.service.MtpdInheritanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lightweight controller smoke tests — service is mocked, we assert
 * wiring + dispatch shape. {@code @PreAuthorize} guards are enforced
 * by Spring Security at the proxy layer and covered separately by the
 * integration test harness.
 */
@ExtendWith(MockitoExtension.class)
class MtpdInheritanceControllerTest {

    @Mock private MtpdInheritanceService service;
    @InjectMocks private MtpdInheritanceController controller;

    private static AssessmentResponse stub(Long id, boolean published) {
        return new AssessmentResponse(
            id, UUID.randomUUID(), 7L,
            "MTPD-INH-2026-Q2-01", 2026, "Q2",
            LocalDateTime.now(), 42L,
            4, 1, 1, 1, 1,
            published, published ? LocalDateTime.now() : null, published ? 42L : null,
            null, null,
            LocalDateTime.now(), null
        );
    }

    @Test void compute_dispatchesToService() {
        when(service.computeAssessment(any(ComputeRequest.class))).thenReturn(stub(5001L, false));
        AssessmentResponse out = controller.compute(new ComputeRequest(2026, "Q2", null, null));
        assertThat(out.id()).isEqualTo(5001L);
        verify(service).computeAssessment(any(ComputeRequest.class));
    }

    @Test void list_dispatchesToService() {
        when(service.listAssessments()).thenReturn(List.of(stub(5001L, false), stub(5002L, true)));
        List<AssessmentResponse> out = controller.list();
        assertThat(out).hasSize(2);
    }

    @Test void detail_dispatchesToService() {
        AssessmentDetailResponse detail = new AssessmentDetailResponse(stub(5001L, false), List.of());
        when(service.getAssessmentDetail(5001L)).thenReturn(detail);
        AssessmentDetailResponse out = controller.detail(5001L);
        assertThat(out.assessment().id()).isEqualTo(5001L);
    }

    @Test void publish_dispatchesToService() {
        when(service.publishAssessment(eq(5001L), any())).thenReturn(stub(5001L, true));
        AssessmentResponse out = controller.publish(5001L, new PublishRequest(null, null));
        assertThat(out.isPublished()).isTrue();
    }

    @Test void createCapa_dispatchesToService() {
        CapaCreateResponse r = new CapaCreateResponse(9001L, 424242L, "CAPA-2026-0042", LocalDateTime.now());
        when(service.createCapaForFinding(eq(9001L), any())).thenReturn(r);
        CapaCreateResponse out = controller.createCapa(9001L,
            new CapaCreateRequest("BCM Manager", "HIGH", null, null));
        assertThat(out.capaExternalId()).isEqualTo("CAPA-2026-0042");
    }

    @Test void processHistory_dispatchesToService() {
        when(service.getProcessBreachHistory(11L)).thenReturn(List.of());
        List<FindingResponse> out = controller.processHistory(11L);
        assertThat(out).isEmpty();
        verify(service).getProcessBreachHistory(11L);
    }
}
