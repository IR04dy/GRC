package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisAar;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.repository.CrisisAarRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisDecisionRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisRegulatoryNotificationRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisSituationReportRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for Tier-4 #23 — NCEMA AAR builder + the report-code sequence
 * fix from commit 17a2fb1.
 *
 * <ul>
 *   <li>generate() rejects crisisEventId from another tenant</li>
 *   <li>generate() builds a draft with year-scoped report code</li>
 *   <li>nextReportCode race-tolerance loop picks an unused suffix</li>
 *   <li>update() rejects edits on APPROVED / PUBLISHED states</li>
 *   <li>transition() enforces DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED</li>
 *   <li>get() rejects cross-tenant id (404 not 200)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrisisAarServiceTest {

    private static final Long ORG_ID = 5L;
    private static final Long OTHER_ORG = 999L;

    @Mock private CrisisAarRepository                       aars;
    @Mock private CrisisEventRepository                     crises;
    @Mock private CrisisSituationReportRepository           sitreps;
    @Mock private CrisisDecisionRepository                  decisions;
    @Mock private CrisisRegulatoryNotificationRepository    regNotifications;
    @Mock private AuditLogService                           audit;
    @Mock private ApplicationEventPublisher                 eventPublisher;

    @InjectMocks private CrisisAarService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(aars.save(any(CrisisAar.class))).thenAnswer(inv -> {
            CrisisAar a = inv.getArgument(0);
            if (a.getId() == null) a.setId(101L);
            return a;
        });
        when(sitreps.findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(decisions.findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(regNotifications.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(any(), any()))
            .thenReturn(List.of());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ══════════════════════════════════════════════════════════════════
    // Tenant isolation
    // ══════════════════════════════════════════════════════════════════

    @Test void generate_rejectsCrisisFromOtherTenant() {
        // findByIdAndOrganisationId is the proper tenant-scoped finder;
        // an empty result means "not found for this tenant".
        when(crises.findByIdAndOrganisationId(50L, ORG_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(50L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("CrisisEvent 50");
    }

    @Test void get_rejectsCrossTenant() {
        CrisisAar other = new CrisisAar();
        other.setId(200L);
        other.setOrganisationId(OTHER_ORG);
        when(aars.findById(200L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.get(200L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════
    // generate() — draft assembly + year-scoped report code
    // ══════════════════════════════════════════════════════════════════

    @Test void generate_buildsDraftWithReportCodeAndBilingualTitles() {
        CrisisEvent ce = newCrisisEvent(60L, "Ransomware on file server",
            "برنامج فدية على خادم الملفات");
        when(crises.findByIdAndOrganisationId(60L, ORG_ID)).thenReturn(Optional.of(ce));
        when(aars.countByOrganisationIdAndReportCodeStartingWith(eq(ORG_ID), anyString()))
            .thenReturn(0L);
        when(aars.findByOrganisationIdAndReportCode(eq(ORG_ID), anyString()))
            .thenReturn(Optional.empty());

        CrisisAar out = service.generate(60L);

        assertThat(out.getStatus()).isEqualTo("DRAFT");
        assertThat(out.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(out.getCrisisEventId()).isEqualTo(60L);
        assertThat(out.getReportCode())
            .startsWith("AAR-" + LocalDateTime.now().getYear())
            .endsWith("000001");
        assertThat(out.getTitleEn()).contains("Ransomware on file server");
        assertThat(out.getTitleAr()).contains("برنامج فدية");
        // Auto-narratives populated for the spine of the report.
        assertThat(out.getExecutiveSummaryEn()).contains("Crisis event").contains("severity");
        assertThat(out.getTimelineMdEn()).contains("DETECTED");

        verify(audit).log(eq("GENERATE"), eq("CrisisAar"), anyLong(), any());
    }

    @Test void nextReportCode_skipsCollidingSuffixViaRaceLoop() {
        CrisisEvent ce = newCrisisEvent(70L, "Phishing", "تصيد");
        when(crises.findByIdAndOrganisationId(70L, ORG_ID)).thenReturn(Optional.of(ce));
        when(aars.countByOrganisationIdAndReportCodeStartingWith(eq(ORG_ID), anyString()))
            .thenReturn(3L);
        // Simulate two AARs that just got created with codes 4 and 5; the
        // sequencer should walk past them to 6.
        String year = "AAR-" + LocalDateTime.now().getYear() + "-";
        when(aars.findByOrganisationIdAndReportCode(eq(ORG_ID), eq(year + "000004")))
            .thenReturn(Optional.of(new CrisisAar()));
        when(aars.findByOrganisationIdAndReportCode(eq(ORG_ID), eq(year + "000005")))
            .thenReturn(Optional.of(new CrisisAar()));
        when(aars.findByOrganisationIdAndReportCode(eq(ORG_ID), eq(year + "000006")))
            .thenReturn(Optional.empty());

        CrisisAar out = service.generate(70L);

        assertThat(out.getReportCode()).isEqualTo(year + "000006");
    }

    // ══════════════════════════════════════════════════════════════════
    // update() — refuses edits in approved/published
    // ══════════════════════════════════════════════════════════════════

    @Test void update_rejectsEditOnApproved() {
        CrisisAar approved = newAar(101L, "APPROVED");
        when(aars.findById(101L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.update(101L,
            new CrisisAarService.UpdateBody("New title", null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("editable only in DRAFT or UNDER_REVIEW");
    }

    @Test void update_acceptsEditOnDraft() {
        CrisisAar draft = newAar(102L, "DRAFT");
        when(aars.findById(102L)).thenReturn(Optional.of(draft));

        CrisisAar out = service.update(102L,
            new CrisisAarService.UpdateBody("Revised title", "عنوان مُحدَّث",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null));

        assertThat(out.getTitleEn()).isEqualTo("Revised title");
        assertThat(out.getTitleAr()).isEqualTo("عنوان مُحدَّث");
    }

    // ══════════════════════════════════════════════════════════════════
    // transition() — state machine
    // ══════════════════════════════════════════════════════════════════

    @Test void transition_draftToUnderReview() {
        CrisisAar draft = newAar(103L, "DRAFT");
        when(aars.findById(103L)).thenReturn(Optional.of(draft));

        CrisisAar out = service.transition(103L, "UNDER_REVIEW", null);

        assertThat(out.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test void transition_rejectsIllegalJump() {
        CrisisAar draft = newAar(104L, "DRAFT");
        when(aars.findById(104L)).thenReturn(Optional.of(draft));

        // Can't jump DRAFT → PUBLISHED
        assertThatThrownBy(() -> service.transition(104L, "PUBLISHED", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Illegal transition");
    }

    @Test void transition_approvedStampsApprovedAt_AndSignatureRef() {
        CrisisAar underReview = newAar(105L, "UNDER_REVIEW");
        when(aars.findById(105L)).thenReturn(Optional.of(underReview));

        ArgumentCaptor<CrisisAar> cap = ArgumentCaptor.forClass(CrisisAar.class);

        service.transition(105L, "APPROVED", 700L);

        verify(aars, atLeast(1)).save(cap.capture());
        CrisisAar saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("APPROVED");
        assertThat(saved.getApprovedAt()).isNotNull();
        assertThat(saved.getDigitalSignatureTxnId()).isEqualTo(700L);
    }

    @Test void transition_unknownTargetThrows() {
        CrisisAar draft = newAar(106L, "DRAFT");
        when(aars.findById(106L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.transition(106L, "ZOMBIE", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────
    private CrisisEvent newCrisisEvent(Long id, String en, String ar) {
        CrisisEvent ce = new CrisisEvent();
        ce.setId(id);
        ce.setOrganisationId(ORG_ID);
        ce.setEventCode("CRIS-" + id);
        ce.setSeverity(4);
        ce.setStatus("CLOSED");
        ce.setTitleEn(en);
        ce.setTitleAr(ar);
        ce.setDetectionTime(LocalDateTime.now().minusDays(2));
        ce.setClosureTime(LocalDateTime.now().minusHours(2));
        ce.setPersonalDataAffected(false);
        ce.setCyberRelated(true);
        return ce;
    }

    private CrisisAar newAar(Long id, String status) {
        CrisisAar a = new CrisisAar();
        a.setId(id);
        a.setOrganisationId(ORG_ID);
        a.setCrisisEventId(99L);
        a.setReportCode("AAR-2026-000099");
        a.setTitleEn("Test AAR");
        a.setStatus(status);
        return a;
    }
}
