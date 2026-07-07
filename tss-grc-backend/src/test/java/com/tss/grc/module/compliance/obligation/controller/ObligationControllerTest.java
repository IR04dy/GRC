package com.tss.grc.module.compliance.obligation.controller;

import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationKpis;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.AttestationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationTransitionRequest;
import com.tss.grc.module.attachment.dto.AttachmentResponse;
import com.tss.grc.module.attachment.service.AttachmentService;
import com.tss.grc.module.compliance.obligation.service.ObligationAttestationService;
import com.tss.grc.module.compliance.obligation.service.ObligationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 11 — Lightweight smoke tests for the obligation controllers.
 *
 * <p>The controllers are thin pass-throughs; we mock the service and
 * assert dispatch shape. {@code @PreAuthorize} is enforced at the
 * Spring Security proxy layer and is covered separately by integration
 * tests.
 */
@ExtendWith(MockitoExtension.class)
class ObligationControllerTest {

    @Mock private ObligationService service;
    @Mock private AttachmentService attachments;
    @Mock private ObligationAttestationService attestationService;
    @InjectMocks private ObligationController controller;

    private static ObligationResponse stub(String stage) {
        return new ObligationResponse(
            101L, UUID.randomUUID(), 7L,
            "OBL-SAMA-2026-001",
            "SAMA monthly CAR return", "تقرير نسبة كفاية رأس المال الشهري",
            null, null,
            "SAMA", "SAMA Prudential Returns §4.2",
            "REPORTING", "MONTHLY", "CRITICAL",
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 15), 7,
            42L, null,
            "Treasury", "الخزينة",
            null, null,
            stage,
            null, null,
            LocalDateTime.now(), null
        );
    }

    @Test void create_dispatchesToService() {
        when(service.create(any(ObligationCreateRequest.class))).thenReturn(stub("DRAFT"));
        ObligationCreateRequest req = new ObligationCreateRequest(
            "OBL-SAMA-2026-001", "t", "ع", null, null,
            "SAMA", null, "REPORTING", "MONTHLY", "CRITICAL",
            null, LocalDate.now().plusDays(30), 7, 42L, null,
            null, null, null, null);

        ObligationResponse out = controller.create(req);

        assertThat(out.lifecycleStage()).isEqualTo("DRAFT");
        verify(service).create(req);
    }

    @Test void list_dispatchesToService() {
        when(service.list()).thenReturn(List.of(stub("ACTIVE")));
        List<ObligationResponse> out = controller.list();
        assertThat(out).hasSize(1);
        verify(service).list();
    }

    @Test void kpis_dispatchesToService() {
        ObligationKpis stub = new ObligationKpis(20L, 2L, 5L, 4L);
        when(service.kpis()).thenReturn(stub);
        ObligationKpis out = controller.kpis();
        assertThat(out.activeCount()).isEqualTo(20L);
        assertThat(out.overdueCount()).isEqualTo(2L);
        verify(service).kpis();
    }

    @Test void due_dispatchesWithDaysParam() {
        when(service.listDueWithinDays(30)).thenReturn(List.of(stub("ACTIVE")));
        List<ObligationResponse> out = controller.due(30);
        assertThat(out).hasSize(1);
        verify(service).listDueWithinDays(30);
    }

    @Test void transition_dispatchesToService() {
        when(service.transition(eq(101L), any(ObligationTransitionRequest.class))).thenReturn(stub("ACTIVE"));
        ObligationTransitionRequest req = new ObligationTransitionRequest("ACTIVE", "Approved");
        ObligationResponse out = controller.transition(101L, req);
        assertThat(out.lifecycleStage()).isEqualTo("ACTIVE");
        verify(service).transition(101L, req);
    }

    @Test void byRegulator_dispatchesToService() {
        when(service.listByRegulator("SAMA")).thenReturn(List.of(stub("ACTIVE")));
        List<ObligationResponse> out = controller.byRegulator("SAMA");
        assertThat(out).hasSize(1);
        verify(service).listByRegulator("SAMA");
    }

    // ─── C-11 (TC-20): audit evidence-package assembly ───────────────
    // The obligation, its own attachments, and the per-attestation-cycle
    // evidence fan-out — the structure an auditor pulls in one call.

    @Test void evidencePackage_assemblesObligationAndPerAttestationEvidence() {
        long oid = 101L;
        when(service.get(oid)).thenReturn(stub("ACTIVE"));
        when(attachments.list("Obligation", oid)).thenReturn(List.of(
            attach(11L, "obligation-charter.pdf", "Obligation", oid)));

        AttestationResponse a1 = att(501L, "Q1-2026", "ATTESTED");
        AttestationResponse a2 = att(502L, "Q2-2026", "DUE");
        when(attestationService.listForObligation(oid)).thenReturn(List.of(a1, a2));
        // a1 carries two evidence files; a2 has none yet.
        when(attachments.list("ObligationAttestation", 501L)).thenReturn(List.of(
            attach(21L, "q1-filing-receipt.pdf", "ObligationAttestation", 501L),
            attach(22L, "q1-signoff.pdf", "ObligationAttestation", 501L)));
        when(attachments.list("ObligationAttestation", 502L)).thenReturn(List.of());

        ObligationController.ObligationEvidencePackage pkg = controller.evidencePackage(oid);

        assertThat(pkg.obligation().obligationCode()).isEqualTo("OBL-SAMA-2026-001");
        assertThat(pkg.obligationAttachments()).hasSize(1);
        assertThat(pkg.obligationAttachments().get(0).fileName()).isEqualTo("obligation-charter.pdf");
        assertThat(pkg.obligationAttachments().get(0).sha256()).isNotBlank();
        assertThat(pkg.obligationAttachments().get(0).downloadUrl()).contains("/download");

        assertThat(pkg.attestations()).hasSize(2);
        ObligationController.AttestationEvidence e1 = pkg.attestations().get(0);
        assertThat(e1.attestationId()).isEqualTo(501L);
        assertThat(e1.periodLabel()).isEqualTo("Q1-2026");
        assertThat(e1.status()).isEqualTo("ATTESTED");
        assertThat(e1.attachments()).hasSize(2);
        ObligationController.AttestationEvidence e2 = pkg.attestations().get(1);
        assertThat(e2.attestationId()).isEqualTo(502L);
        assertThat(e2.attachments()).isEmpty();

        assertThat(pkg.generatedAt()).isNotBlank();
    }

    @Test void evidencePackage_emptyWhenNoAttachmentsOrAttestations() {
        long oid = 101L;
        when(service.get(oid)).thenReturn(stub("ACTIVE"));
        when(attachments.list("Obligation", oid)).thenReturn(List.of());
        when(attestationService.listForObligation(oid)).thenReturn(List.of());

        ObligationController.ObligationEvidencePackage pkg = controller.evidencePackage(oid);

        assertThat(pkg.obligationAttachments()).isEmpty();
        assertThat(pkg.attestations()).isEmpty();
        assertThat(pkg.generatedAt()).isNotBlank();
    }

    private static AttestationResponse att(long id, String period, String status) {
        return new AttestationResponse(
            id, UUID.randomUUID(), 7L, 101L, null,
            period, null, null, LocalDate.of(2026, 7, 15), status,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    }

    private static AttachmentResponse attach(long id, String fileName, String entityType, long entityId) {
        return new AttachmentResponse(
            id, entityType, entityId, "EVIDENCE", fileName, fileName,
            1024L, "application/pdf", "abc123sha256", null, "INTERNAL",
            99L, null, 1, null, "/api/v1/attachments/" + id + "/download");
    }
}
