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
import static org.mockito.Mockito.never;
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

    // ══════════════════════════════════════════════════════════════════
    // F8 automation events
    //
    // CrisisAarService publishes:
    //   CRISIS_AAR_GENERATED   — on generate() with crisisEventId, severity
    //   CRISIS_AAR_TRANSITIONED — on every transition with previousStatus,
    //                             severity pulled lazily from parent event
    //
    // Mirrors the CapaTaskService / FindingService pattern from PRs #46/#50.
    // ══════════════════════════════════════════════════════════════════

    @Test void generate_publishesGeneratedEvent_withSeverityFromParent() {
        CrisisEvent ce = newCrisisEvent(60L, "Ransomware on file server",
            "برنامج فدية على خادم الملفات");
        when(crises.findByIdAndOrganisationId(60L, ORG_ID)).thenReturn(Optional.of(ce));
        when(aars.countByOrganisationIdAndReportCodeStartingWith(eq(ORG_ID), anyString()))
            .thenReturn(0L);
        when(aars.findByOrganisationIdAndReportCode(eq(ORG_ID), anyString()))
            .thenReturn(Optional.empty());

        CrisisAar out = service.generate(60L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("CRISIS_AAR_GENERATED");
        assertThat(ev.entityType()).isEqualTo("CrisisAar");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        // correlationId threads downstream rule_runs to the human-readable
        // reportCode rather than the surrogate id.
        assertThat(ev.correlationId()).isEqualTo(out.getReportCode());
        assertThat(ev.payload()).containsEntry("status", "DRAFT");
        assertThat(ev.payload()).containsEntry("crisisEventId", "60");
        // Severity is carried over from the parent CrisisEvent so a rule
        // like "AAR generated for SEV-4 → notify executive sponsor" can
        // match without re-loading the crisis event in the action handler.
        assertThat(ev.payload()).containsEntry("severity", "4");
        assertThat(ev.payload()).containsEntry("reportCode", out.getReportCode());
    }

    @Test void transition_publishesTransitionedEvent_withPreviousStatusInPayload() {
        CrisisAar underReview = newAar(110L, "UNDER_REVIEW");
        when(aars.findById(110L)).thenReturn(Optional.of(underReview));
        // The transition path needs severity from the parent — service
        // loads it lazily from crisisEventId. Stub it so the payload
        // assertion below is real, not just "".
        CrisisEvent parent = newCrisisEvent(99L, "Crisis", "أزمة");
        parent.setSeverity(1);
        when(crises.findByIdAndOrganisationId(99L, ORG_ID)).thenReturn(Optional.of(parent));

        service.transition(110L, "APPROVED", 700L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("CRISIS_AAR_TRANSITIONED");
        assertThat(ev.entityId()).isEqualTo(110L);
        assertThat(ev.correlationId()).isEqualTo("AAR-2026-000099");
        // Single TRANSITIONED event (vs four distinct CRISIS_AAR_<status>
        // events the service emitted before this PR). Rules target the
        // step via condition_json {"status":"APPROVED"} — same shape as
        // FINDING_TRANSITIONED.
        assertThat(ev.payload()).containsEntry("status", "APPROVED");
        assertThat(ev.payload()).containsEntry("previousStatus", "UNDER_REVIEW");
        assertThat(ev.payload()).containsEntry("severity", "1");
    }

    @Test void transition_payloadValues_nullCoercedToEmptyString() {
        // The F8 engine's equality check NPEs on null payload values.
        // Confirm absent fields (titleEn, parent crisis event) are coerced
        // to "" so a rule that doesn't care about them just doesn't match.
        CrisisAar a = new CrisisAar();
        a.setId(120L);
        a.setOrganisationId(ORG_ID);
        a.setReportCode("AAR-2026-000120");
        a.setStatus("DRAFT");
        a.setCrisisEventId(null);
        a.setTitleEn(null);
        when(aars.findById(120L)).thenReturn(Optional.of(a));

        service.transition(120L, "UNDER_REVIEW", null);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().get("titleEn")).isEqualTo("");
        assertThat(captor.getValue().payload().get("crisisEventId")).isEqualTo("");
        assertThat(captor.getValue().payload().get("severity")).isEqualTo("");
    }

    @Test void transition_illegalJump_doesNotEmitEvent() {
        // Cross-tenant / illegal-state guards must NOT fire an F8 event
        // — otherwise a rule would react to a state change that never
        // actually happened (info leak + spurious side-effects).
        CrisisAar draft = newAar(130L, "DRAFT");
        when(aars.findById(130L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.transition(130L, "PUBLISHED", null))
            .isInstanceOf(IllegalStateException.class);

        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
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
