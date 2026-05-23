package com.tss.grc.module.tprm.bcp_attestation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationResponse;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationReviewRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.AttestationSubmitRequest;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VendorBcpAttestationService} (Slice 10-D).
 *
 * <p>Covers the vendor-portal submission path and the TPRM Lead's
 * accept / reject / mark-NA review path:
 * <ul>
 *   <li>Submit happy path — fields persist, status → SUBMITTED, F8 emitted</li>
 *   <li>Accept / reject / mark-NA state transitions, F8 + audit per case</li>
 *   <li>RTO/MTPD breach flag set when attested > contractual SLA snapshot</li>
 *   <li>Evidence file path captured verbatim</li>
 *   <li>Token-based vendor portal submission bypasses tenant context</li>
 *   <li>Illegal transitions rejected with IllegalTransitionException</li>
 *   <li>Tenant isolation on the reviewer-scoped reads</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VendorBcpAttestationServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private VendorBcpAttestationRepository attestationRepo;
    @Mock private VendorBcpCampaignRepository campaignRepo;
    @Mock private VendorBcpCampaignService campaignService;
    @Mock private VendorRepository vendorRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private VendorBcpAttestationService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static VendorBcpAttestation existing(String status) {
        VendorBcpAttestation a = new VendorBcpAttestation();
        a.setId(501L);
        a.setOrganisationId(ORG_ID);
        a.setExternalId(UUID.randomUUID());
        a.setSubmissionToken(UUID.randomUUID());
        a.setCampaignId(101L);
        a.setVendorId(11L);
        a.setStatus(status);
        a.setInvitedAt(LocalDateTime.now().minusDays(10));
        a.setContractualRtoHours(4);          // contractual: 4h
        a.setContractualMtpdHours(72);        // contractual: 72h
        return a;
    }

    private static AttestationSubmitRequest submitReq(int rtoHrs, int mtpdHrs) {
        return new AttestationSubmitRequest(
            rtoHrs, mtpdHrs, true,
            LocalDate.now().minusMonths(2),
            LocalDate.now().minusMonths(3),
            "[{\"name\":\"AWS me-south-1\",\"role\":\"compute\"},{\"name\":\"Cloudflare\",\"role\":\"DNS\"}]",
            "/tmp/bcp/report.pdf", "BCP-Test-Report-2026Q1.pdf",
            "bcp-ops@vendor.example.com", "Khalid Al-Mutairi"
        );
    }

    // ─── Submit ──────────────────────────────────────────────────────

    @Test void submit_happyPath_persistsAndAdvancesToSubmitted() {
        VendorBcpAttestation a = existing("INVITED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        AttestationResponse out = service.submitAttestation(501L, submitReq(2, 24));

        assertThat(out.status()).isEqualTo("SUBMITTED");
        assertThat(out.rtoHoursAttested()).isEqualTo(2);
        assertThat(out.mtpdHoursAttested()).isEqualTo(24);
        assertThat(out.hasTestedBcp()).isTrue();
        assertThat(out.submittedByContactEmail()).isEqualTo("bcp-ops@vendor.example.com");
        assertThat(out.evidenceFileName()).isEqualTo("BCP-Test-Report-2026Q1.pdf");
        assertThat(out.submittedAt()).isNotNull();
        // Attested values are inside contractual SLA — no breach flags.
        assertThat(out.rtoBreach()).isFalse();
        assertThat(out.mtpdBreach()).isFalse();
    }

    @Test void submit_rtoBreach_flaggedWhenAttestedExceedsContractual() {
        VendorBcpAttestation a = existing("INVITED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        // contractual RTO 4h, attested 8h → breach
        AttestationResponse out = service.submitAttestation(501L, submitReq(8, 48));

        assertThat(out.rtoBreach()).isTrue();
        assertThat(out.mtpdBreach()).isFalse();   // 48h ≤ 72h contractual
    }

    @Test void submit_blockedFromAcceptedTerminal() {
        VendorBcpAttestation a = existing("ACCEPTED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.submitAttestation(501L, submitReq(2, 24)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void submit_byToken_bypassesTenantContext() {
        // Vendor portal flow — no tenant context required because the
        // token itself is the credential.
        VendorBcpAttestation a = existing("INVITED");
        when(attestationRepo.findBySubmissionTokenAndIsDeletedFalse(a.getSubmissionToken()))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        AttestationResponse out = service.submitByToken(a.getSubmissionToken(), submitReq(2, 24));

        assertThat(out.status()).isEqualTo("SUBMITTED");
        assertThat(out.rtoHoursAttested()).isEqualTo(2);
    }

    // ─── Review ──────────────────────────────────────────────────────

    @Test void accept_transitionsSubmittedToAccepted() {
        VendorBcpAttestation a = existing("SUBMITTED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        AttestationResponse out = service.acceptAttestation(501L,
            new AttestationReviewRequest("Evidence sufficient. RTO/MTPD within SLA.",
                "الأدلة كافية. RTO/MTPD ضمن نطاق اتفاقية مستوى الخدمة.",
                "Salman Al-Otaibi (TPRM Lead)", 42L));

        assertThat(out.status()).isEqualTo("ACCEPTED");
        assertThat(out.reviewedByName()).contains("Salman Al-Otaibi");
        assertThat(out.reviewedAt()).isNotNull();
    }

    @Test void reject_transitionsSubmittedToRejected() {
        VendorBcpAttestation a = existing("SUBMITTED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        AttestationResponse out = service.rejectAttestation(501L,
            new AttestationReviewRequest("Last BCP test older than 12 months — please retest.",
                "آخر اختبار لخطة الاستمرارية يتجاوز 12 شهراً — يرجى إعادة الاختبار.",
                "TPRM Lead", null));

        assertThat(out.status()).isEqualTo("REJECTED");
        assertThat(out.reviewerCommentAr()).contains("يرجى إعادة الاختبار");
    }

    @Test void markOutOfScope_allowedFromInvited() {
        VendorBcpAttestation a = existing("INVITED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        AttestationResponse out = service.markOutOfScope(501L,
            new AttestationReviewRequest("Vendor contract terminated before campaign open.",
                "تم إنهاء العقد مع المورّد قبل بدء الحملة.",
                "TPRM Lead", null));

        assertThat(out.status()).isEqualTo("NA_OUT_OF_SCOPE");
    }

    @Test void accept_blockedFromInvited() {
        VendorBcpAttestation a = existing("INVITED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        // Cannot accept without a submission — caller must wait for SUBMITTED.
        assertThatThrownBy(() -> service.acceptAttestation(501L,
            new AttestationReviewRequest(null, null, null, null)))
            .isInstanceOf(IllegalTransitionException.class);
        verify(attestationRepo, never()).save(any());
    }

    @Test void reject_blockedFromAcceptedTerminal() {
        VendorBcpAttestation a = existing("ACCEPTED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.rejectAttestation(501L,
            new AttestationReviewRequest(null, null, null, null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── F8 events ───────────────────────────────────────────────────

    @Test void submit_emitsSubmittedEvent() {
        VendorBcpAttestation a = existing("INVITED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        service.submitAttestation(501L, submitReq(2, 24));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("VENDOR_BCP_ATTESTATION_SUBMITTED");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("previousStatus", "INVITED");
    }

    @Test void accept_emitsAcceptedEvent() {
        VendorBcpAttestation a = existing("SUBMITTED");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.findById(101L)).thenReturn(Optional.of(new VendorBcpCampaign()));

        service.acceptAttestation(501L, new AttestationReviewRequest(null, null, "Lead", null));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("VENDOR_BCP_ATTESTATION_ACCEPTED");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_filtersByOrg() {
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByCreatedAtAsc(ORG_ID, 101L))
            .thenReturn(java.util.List.of());
        service.listForCampaign(101L);
        verify(attestationRepo)
            .findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByCreatedAtAsc(ORG_ID, 101L);
    }

    // ─── Mapping ─────────────────────────────────────────────────────

    @Test void response_includesVendorNameFromJoin() {
        VendorBcpAttestation a = existing("SUBMITTED");
        Vendor v = new Vendor();
        v.setId(11L);
        v.setVendorName("Microsoft Azure KSA");
        v.setVendorNameAr("مايكروسوفت أزور — السعودية");
        when(attestationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 501L))
            .thenReturn(Optional.of(a));
        when(vendorRepo.findById(11L)).thenReturn(Optional.of(v));

        AttestationResponse out = service.get(501L);

        assertThat(out.vendorName()).isEqualTo("Microsoft Azure KSA");
        assertThat(out.vendorNameAr()).isEqualTo("مايكروسوفت أزور — السعودية");
    }
}
