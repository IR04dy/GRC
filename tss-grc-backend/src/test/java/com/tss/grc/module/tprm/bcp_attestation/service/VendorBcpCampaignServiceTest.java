package com.tss.grc.module.tprm.bcp_attestation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.notify.NotificationChannel;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCloseRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCreateRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VendorBcpCampaignService} (Slice 10-D).
 *
 * <p>Covers the surface the TPRM Lead actually exercises:
 * <ul>
 *   <li>Create → open → remind → escalate → close happy path</li>
 *   <li>Open materialises one attestation row per matching vendor</li>
 *   <li>Threshold filter (CRITICAL_ONLY, HIGH_AND_ABOVE, ALL) resolves
 *       the right vendor subset</li>
 *   <li>Reminder dispatcher skips rows not yet past cadence cutoff</li>
 *   <li>Escalation creates the F8 trigger that feeds the Audit
 *       Committee action item</li>
 *   <li>Tenant isolation — every read filters by org</li>
 *   <li>F8 event emitted at every transition with payload shape</li>
 *   <li>State-machine guard rejects illegal transitions</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VendorBcpCampaignServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private VendorBcpCampaignRepository campaignRepo;
    @Mock private VendorBcpAttestationRepository attestationRepo;
    @Mock private VendorRepository vendorRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private VendorBcpCampaignService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Inject the service explicitly so we can pass an empty channels list
        // (production: Spring resolves a List<NotificationChannel> bean).
        service = new VendorBcpCampaignService(
            campaignRepo, attestationRepo, vendorRepo, audit, publisher, List.of());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static CampaignCreateRequest validReq() {
        return new CampaignCreateRequest(
            "VBCP-2026-ANNUAL",
            "Annual critical-third-party BCP attestation — 2026",
            "الشهادة السنوية لاستمرارية الأعمال للموردين الجوهريين — 2026",
            "Covers CRITICAL-rated third parties (Azure KSA, AWS me-south-1, stc, Mobily, Lean, SITE, Bahri).",
            "تشمل الأطراف الجوهرية (أزور KSA، AWS me-south-1، stc، موبايلي، Lean، SITE، البحري).",
            2026, null,
            LocalDateTime.now().plusDays(45),
            14, 30,
            "CRITICAL_ONLY",
            "SAMA BCF §3.4; NCA ECC-2 4-2-3"
        );
    }

    private static VendorBcpCampaign existing(String status, int invited) {
        VendorBcpCampaign c = new VendorBcpCampaign();
        c.setId(101L);
        c.setOrganisationId(ORG_ID);
        c.setExternalId(UUID.randomUUID());
        c.setCampaignCode("VBCP-2026-ANNUAL");
        c.setTitleEn("Annual critical-third-party BCP attestation — 2026");
        c.setTitleAr("الشهادة السنوية لاستمرارية الأعمال للموردين الجوهريين — 2026");
        c.setCycleYear(2026);
        c.setStatus(status);
        c.setMandatoryThreshold("CRITICAL_ONLY");
        c.setReminderCadenceDays(14);
        c.setEscalationThresholdDays(30);
        c.setTotalInvited(invited);
        c.setTotalSubmitted(0);
        c.setTotalAccepted(0);
        c.setTotalRejected(0);
        c.setTotalOverdue(0);
        c.setTotalNa(0);
        c.setClosesAt(LocalDateTime.now().plusDays(15));
        return c;
    }

    private static Vendor vendor(Long id, String name, String rating) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setOrganisationId(ORG_ID);
        v.setVendorName(name);
        v.setRiskRating(rating);
        v.setStatus("ACTIVE");
        v.setContactEmail(name.toLowerCase().replace(" ", "") + "@example.com");
        return v;
    }

    // ─── Create ──────────────────────────────────────────────────────

    @Test void createCampaign_persistsDraftWithGivenCode() {
        when(campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> {
            VendorBcpCampaign c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        CampaignResponse out = service.createCampaign(validReq());

        assertThat(out.campaignCode()).isEqualTo("VBCP-2026-ANNUAL");
        assertThat(out.status()).isEqualTo("DRAFT");
        assertThat(out.mandatoryThreshold()).isEqualTo("CRITICAL_ONLY");
        assertThat(out.titleAr()).contains("الشهادة السنوية");
    }

    @Test void createCampaign_duplicateCodeRejected() {
        when(campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(true);
        assertThatThrownBy(() -> service.createCampaign(validReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void createCampaign_invalidThresholdRejected() {
        CampaignCreateRequest req = new CampaignCreateRequest(
            "VBCP-X", "t", "ع", null, null, 2026, null,
            null, 14, 30,
            "BOGUS",                        // not in enum
            null);
        assertThatThrownBy(() -> service.createCampaign(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid mandatoryThreshold");
    }

    // ─── Open ────────────────────────────────────────────────────────

    @Test void openCampaign_materialisesInvitationsForCriticalVendors() {
        VendorBcpCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(vendorRepo.findByOrganisationIdAndRiskRatingAndStatus(ORG_ID, "CRITICAL", "ACTIVE"))
            .thenReturn(List.of(
                vendor(1L, "Microsoft Azure KSA", "CRITICAL"),
                vendor(2L, "AWS me-south-1", "CRITICAL"),
                vendor(3L, "Solutions by stc", "CRITICAL"),
                vendor(4L, "Mobily", "CRITICAL"),
                vendor(5L, "Lean Technologies", "CRITICAL"),
                vendor(6L, "SITE", "CRITICAL"),
                vendor(7L, "Bahri", "CRITICAL")));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.openCampaign(101L);

        assertThat(out.status()).isEqualTo("OPEN");
        assertThat(out.totalInvited()).isEqualTo(7);
        verify(attestationRepo, times(7)).save(any(VendorBcpAttestation.class));
    }

    @Test void openCampaign_highAndAbove_resolvesBothCriticalAndHighVendors() {
        VendorBcpCampaign c = existing("DRAFT", 0);
        c.setMandatoryThreshold("HIGH_AND_ABOVE");
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(vendorRepo.findByOrganisationIdAndRiskRatingInAndStatus(
                eq(ORG_ID), eq(List.of("CRITICAL", "HIGH")), eq("ACTIVE")))
            .thenReturn(List.of(
                vendor(1L, "Azure KSA", "CRITICAL"),
                vendor(2L, "Bahri", "HIGH")));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.openCampaign(101L);

        assertThat(out.totalInvited()).isEqualTo(2);
        verify(vendorRepo).findByOrganisationIdAndRiskRatingInAndStatus(
            ORG_ID, List.of("CRITICAL", "HIGH"), "ACTIVE");
    }

    @Test void openCampaign_acceptsEmptyTargetSet() {
        // Tenant with no critical vendors yet — opening still works so
        // the audit cycle is recorded as "performed, zero in scope".
        VendorBcpCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(vendorRepo.findByOrganisationIdAndRiskRatingAndStatus(ORG_ID, "CRITICAL", "ACTIVE"))
            .thenReturn(List.of());
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.openCampaign(101L);

        assertThat(out.status()).isEqualTo("OPEN");
        assertThat(out.totalInvited()).isEqualTo(0);
        verify(attestationRepo, never()).save(any(VendorBcpAttestation.class));
    }

    @Test void openCampaign_blockedFromTerminalStates() {
        VendorBcpCampaign c = existing("CLOSED", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.openCampaign(101L))
            .isInstanceOf(IllegalTransitionException.class);
        verify(attestationRepo, never()).save(any());
    }

    // ─── Reminders ───────────────────────────────────────────────────

    @Test void sendReminders_round1_advancesToRemindedR1() {
        VendorBcpCampaign c = existing("OPEN", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        List<VendorBcpAttestation> due = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            VendorBcpAttestation a = new VendorBcpAttestation();
            a.setId((long) i + 1);
            a.setOrganisationId(ORG_ID);
            a.setCampaignId(101L);
            a.setVendorId((long) i + 1);
            a.setStatus("INVITED");
            a.setInvitedAt(LocalDateTime.now().minusDays(20));
            due.add(a);
        }
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), eq(List.of("INVITED")), any())).thenReturn(due);
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.sendReminders(101L, 1);

        assertThat(out.status()).isEqualTo("REMINDED_R1");
        verify(attestationRepo, times(3)).save(any(VendorBcpAttestation.class));
    }

    @Test void sendReminders_skipsNotDueRows() {
        // Empty result set from the cutoff query means there is nothing
        // to remind right now — the call still flips campaign state,
        // documenting that "round 1 ran but no one was due".
        VendorBcpCampaign c = existing("OPEN", 3);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                anyLong(), anyLong(), anyList(), any())).thenReturn(List.of());
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.sendReminders(101L, 1);

        assertThat(out.status()).isEqualTo("REMINDED_R1");
        verify(attestationRepo, never()).save(any(VendorBcpAttestation.class));
    }

    @Test void sendReminders_invalidRoundRejected() {
        assertThatThrownBy(() -> service.sendReminders(101L, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("round must be 1 or 2");
    }

    @Test void sendReminders_round2BlockedBeforeRound1() {
        VendorBcpCampaign c = existing("OPEN", 3);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.sendReminders(101L, 2))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Escalation ──────────────────────────────────────────────────

    @Test void escalateOverdue_flipsOverdueRowsAndAdvancesCampaign() {
        VendorBcpCampaign c = existing("REMINDED_R2", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        List<VendorBcpAttestation> stale = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            VendorBcpAttestation a = new VendorBcpAttestation();
            a.setId((long) i + 10);
            a.setOrganisationId(ORG_ID);
            a.setCampaignId(101L);
            a.setStatus("REMINDED");
            a.setInvitedAt(LocalDateTime.now().minusDays(45));
            stale.add(a);
        }
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), eq(List.of("INVITED", "REMINDED", "OVERDUE")), any())).thenReturn(stale);
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse out = service.escalateOverdue(101L);

        assertThat(out.status()).isEqualTo("ESCALATED");
        assertThat(out.totalOverdue()).isEqualTo(2);
    }

    @Test void escalateOverdue_emitsAuditCommitteeTrigger() {
        VendorBcpCampaign c = existing("REMINDED_R2", 3);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                anyLong(), anyLong(), anyList(), any())).thenReturn(List.of());
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        service.escalateOverdue(101L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("VENDOR_BCP_ESCALATED");
    }

    // ─── Close ───────────────────────────────────────────────────────

    @Test void closeCampaign_recomputesRollupsFromAttestationCounts() {
        VendorBcpCampaign c = existing("OPEN", 5);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndStatusInAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), anyList())).thenReturn(4L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), eq("ACCEPTED"))).thenReturn(3L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), eq("REJECTED"))).thenReturn(1L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), eq("OVERDUE"))).thenReturn(1L);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(
                eq(ORG_ID), eq(101L), eq("NA_OUT_OF_SCOPE"))).thenReturn(0L);
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignCloseRequest req = new CampaignCloseRequest(
            "All in-scope vendors responded; one rejected pending re-test.",
            "جميع الموردين الجوهريين استجابوا؛ واحد مرفوض بانتظار إعادة الاختبار.");
        CampaignResponse out = service.closeCampaign(101L, req);

        assertThat(out.status()).isEqualTo("CLOSED");
        assertThat(out.totalSubmitted()).isEqualTo(4);
        assertThat(out.totalAccepted()).isEqualTo(3);
        assertThat(out.totalRejected()).isEqualTo(1);
        assertThat(out.totalOverdue()).isEqualTo(1);
        assertThat(out.closingSummaryAr()).contains("جميع الموردين");
    }

    @Test void closeCampaign_blockedFromDraft() {
        VendorBcpCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.closeCampaign(101L, null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── F8 emission ─────────────────────────────────────────────────

    @Test void openCampaign_emitsCampaignOpenedEvent() {
        VendorBcpCampaign c = existing("DRAFT", 0);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(vendorRepo.findByOrganisationIdAndRiskRatingAndStatus(ORG_ID, "CRITICAL", "ACTIVE"))
            .thenReturn(List.of(vendor(1L, "Azure KSA", "CRITICAL")));
        when(attestationRepo.save(any(VendorBcpAttestation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        service.openCampaign(101L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("VENDOR_BCP_CAMPAIGN_OPENED");
        assertThat(ev.entityType()).isEqualTo("VendorBcpCampaign");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("status", "OPEN");
        assertThat(ev.payload()).containsEntry("previousStatus", "DRAFT");
    }

    @Test void sendReminders_emitsReminderEvent() {
        VendorBcpCampaign c = existing("OPEN", 3);
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(c));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                anyLong(), anyLong(), anyList(), any())).thenReturn(List.of());
        when(campaignRepo.save(any(VendorBcpCampaign.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendReminders(101L, 1);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("VENDOR_BCP_REMINDER_SENT");
        assertThat(captor.getValue().payload()).containsEntry("round", "1");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of());
        service.list();
        verify(campaignRepo, times(1))
            .findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID);
    }

    @Test void create_setsOrgFromTenantContext() {
        when(campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        ArgumentCaptor<VendorBcpCampaign> captor = ArgumentCaptor.forClass(VendorBcpCampaign.class);
        when(campaignRepo.save(captor.capture())).thenAnswer(inv -> {
            VendorBcpCampaign c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        service.createCampaign(validReq());

        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    // ─── State machine guard ─────────────────────────────────────────

    @Test void guardTransition_allowedTransitionsByState() {
        // DRAFT → OPEN / CANCELLED (both allowed); other transitions throw.
        VendorBcpCampaignService.guardTransition("DRAFT", "OPEN");
        VendorBcpCampaignService.guardTransition("DRAFT", "CANCELLED");
        assertThatThrownBy(() -> VendorBcpCampaignService.guardTransition("DRAFT", "CLOSED"))
            .isInstanceOf(IllegalTransitionException.class);
        assertThatThrownBy(() -> VendorBcpCampaignService.guardTransition("CLOSED", "OPEN"))
            .isInstanceOf(IllegalTransitionException.class);
    }
}
