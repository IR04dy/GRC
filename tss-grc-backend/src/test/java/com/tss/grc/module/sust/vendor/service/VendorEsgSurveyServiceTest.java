package com.tss.grc.module.sust.vendor.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.vendor.dto.VendorEsgResponseRequest;
import com.tss.grc.module.sust.vendor.entity.VendorEsgSurvey;
import com.tss.grc.module.sust.vendor.repository.VendorEsgSurveyRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VendorEsgSurveyService (S-O) — locks the surveillance rules:
 *
 * <ul>
 *   <li><b>Real link</b> — create validates the tprm vendor exists in-tenant
 *       (dangling id → 400) and snapshots its bilingual name.</li>
 *   <li><b>Computed score</b> — esgScore is derived from the reported data,
 *       never bound; a full-disclosure vendor scores 100, a silent one 0.</li>
 *   <li><b>Non-response escalation</b> — sweepOverdue moves past-due SENT
 *       surveys to ESCALATED and opens a VENDOR_ESG auto-CAPA (back-linked).</li>
 *   <li><b>Reject</b> — a rejected response opens a VENDOR_ESG auto-CAPA.</li>
 *   <li><b>Lifecycle</b> — illegal transitions (e.g. send from a non-DRAFT
 *       state) are refused.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VendorEsgSurveyServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private VendorEsgSurveyRepository repo;
    @Mock private VendorRepository          tprmVendorRepo;
    @Mock private CorrectiveActionService   capaService;
    @Mock private AuditLogService           audit;
    @InjectMocks private VendorEsgSurveyService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── create — real, validated vendor link ────────────────────────

    @Test
    void create_validVendor_snapshotsNameForcesDraftAndScrubsComputed() {
        Vendor v = vendor(7L, "Cisco Systems", "سيسكو");
        when(tprmVendorRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(v));
        when(repo.findByOrganisationIdAndTprmVendorIdAndReportingYear(ORG_ID, 7L, (short) 2025))
            .thenReturn(Optional.empty());
        when(repo.findMaxExternalIdSeq(eq(ORG_ID), any())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorEsgSurvey req = new VendorEsgSurvey();
        req.setTprmVendorId(7L);
        req.setReportingYear((short) 2025);
        req.setSpendSarK(new BigDecimal("1200.00"));
        // Caller tries to smuggle a state + score — must be scrubbed.
        req.setStatus("ACCEPTED");
        req.setEsgScore(new BigDecimal("99.00"));
        req.setLinkedCapaId(555L);

        VendorEsgSurvey out = service.create(req);

        assertThat(out.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(out.getVendorNameEn()).isEqualTo("Cisco Systems");
        assertThat(out.getVendorNameAr()).isEqualTo("سيسكو");
        assertThat(out.getExternalId()).isEqualTo("VES-2025-0001");
        assertThat(out.getStatus()).isEqualTo("DRAFT");        // forced
        assertThat(out.getEsgScore()).isNull();                // scrubbed
        assertThat(out.getLinkedCapaId()).isNull();            // scrubbed
        verify(audit).log(eq("CREATE"), eq("VendorEsgSurvey"), any(), any());
    }

    @Test
    void create_danglingVendorId_rejects() {
        when(tprmVendorRepo.findByIdAndOrganisationId(999L, ORG_ID)).thenReturn(Optional.empty());
        VendorEsgSurvey req = new VendorEsgSurvey();
        req.setTprmVendorId(999L);
        req.setReportingYear((short) 2025);

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist");
        verify(repo, never()).save(any());
    }

    @Test
    void create_duplicateForVendorYear_rejects() {
        Vendor v = vendor(7L, "Cisco Systems", "سيسكو");
        when(tprmVendorRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(v));
        when(repo.findByOrganisationIdAndTprmVendorIdAndReportingYear(ORG_ID, 7L, (short) 2025))
            .thenReturn(Optional.of(new VendorEsgSurvey()));

        VendorEsgSurvey req = new VendorEsgSurvey();
        req.setTprmVendorId(7L);
        req.setReportingYear((short) 2025);

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(repo, never()).save(any());
    }

    // ── computed ESG score (never free-typed) ────────────────────────

    @Test
    void computeEsgScore_fullDisclosureIs100_silentIs0() {
        VendorEsgSurvey full = new VendorEsgSurvey();
        full.setRenewablePct(100);
        full.setSaudizationPct(100);
        full.setHasNetZeroTarget(true);
        full.setIso14001(true);
        full.setReportedScope12(new BigDecimal("1234"));
        assertThat(VendorEsgSurveyService.computeEsgScore(full)).isEqualByComparingTo("100.00");

        VendorEsgSurvey silent = new VendorEsgSurvey();
        assertThat(VendorEsgSurveyService.computeEsgScore(silent)).isEqualByComparingTo("0.00");

        // Partial: 50% renewable (15) + net-zero (25) + scope12 disclosed (10) = 50.
        VendorEsgSurvey partial = new VendorEsgSurvey();
        partial.setRenewablePct(50);
        partial.setHasNetZeroTarget(true);
        partial.setReportedScope12(new BigDecimal("10"));
        assertThat(VendorEsgSurveyService.computeEsgScore(partial)).isEqualByComparingTo("50.00");
    }

    @Test
    void recordResponse_transitionsToRespondedAndComputesScore() {
        VendorEsgSurvey s = survey(3L, "SENT");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorEsgSurvey out = service.recordResponse(3L,
            new VendorEsgResponseRequest(new BigDecimal("500"), 40, 60, true, false));

        assertThat(out.getStatus()).isEqualTo("RESPONDED");
        assertThat(out.getRespondedAt()).isNotNull();
        // 40*0.30 + 60*0.20 + netzero 25 + scope12 10 = 12 + 12 + 25 + 10 = 59.
        assertThat(out.getEsgScore()).isEqualByComparingTo("59.00");
    }

    // ── non-response escalation → auto-CAPA ──────────────────────────

    @Test
    void sweepOverdue_escalatesPastDueSentAndOpensCapa() {
        VendorEsgSurvey overdue = survey(5L, "SENT");
        overdue.setDueDate(LocalDate.now().minusDays(1));
        when(repo.findByOrganisationIdAndStatusAndDueDateBefore(eq(ORG_ID), eq("SENT"), any()))
            .thenReturn(List.of(overdue));
        CorrectiveAction capa = new CorrectiveAction(); capa.setId(88L);
        when(capaService.autoOpen(eq("VENDOR_ESG"), eq("VES-5"), any(), any(), any(), eq("HIGH")))
            .thenReturn(capa);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VendorEsgSurvey> escalated = service.sweepOverdue();

        assertThat(escalated).hasSize(1);
        assertThat(escalated.get(0).getStatus()).isEqualTo("ESCALATED");
        assertThat(escalated.get(0).getLinkedCapaId()).isEqualTo(88L);
        verify(capaService).autoOpen(eq("VENDOR_ESG"), eq("VES-5"), any(), any(), any(), eq("HIGH"));
    }

    @Test
    void reject_opensCapaAndTransitions() {
        VendorEsgSurvey s = survey(9L, "RESPONDED");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(s));
        CorrectiveAction capa = new CorrectiveAction(); capa.setId(42L);
        when(capaService.autoOpen(eq("VENDOR_ESG"), eq("VES-9"), any(), any(), any(), eq("HIGH")))
            .thenReturn(capa);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorEsgSurvey out = service.reject(9L, null, "incomplete Scope-3 data");

        assertThat(out.getStatus()).isEqualTo("REJECTED");
        assertThat(out.getRejectReason()).isEqualTo("incomplete Scope-3 data");
        assertThat(out.getLinkedCapaId()).isEqualTo(42L);
    }

    // ── lifecycle guard ──────────────────────────────────────────────

    @Test
    void send_fromNonDraft_refused() {
        VendorEsgSurvey s = survey(4L, "SENT");
        when(repo.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.send(4L))
            .isInstanceOf(IllegalTransitionException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void send_fromDraft_setsSentAndDueDate() {
        VendorEsgSurvey s = survey(2L, "DRAFT");
        when(repo.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorEsgSurvey out = service.send(2L);

        assertThat(out.getStatus()).isEqualTo("SENT");
        assertThat(out.getSentAt()).isNotNull();
        assertThat(out.getDueDate()).isEqualTo(
            LocalDate.now().plusDays(VendorEsgSurveyService.DEFAULT_RESPONSE_WINDOW_DAYS));
    }

    // ── helpers ──────────────────────────────────────────────────────
    private static Vendor vendor(Long id, String en, String ar) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setOrganisationId(ORG_ID);
        v.setVendorName(en);
        v.setVendorNameAr(ar);
        return v;
    }

    private static VendorEsgSurvey survey(Long id, String status) {
        VendorEsgSurvey s = new VendorEsgSurvey();
        s.setId(id);
        s.setOrganisationId(ORG_ID);
        s.setTprmVendorId(7L);
        s.setReportingYear((short) 2025);
        s.setVendorNameEn("Cisco Systems");
        s.setVendorNameAr("سيسكو");
        s.setStatus(status);
        return s;
    }
}
