package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementTransitionRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementUpdateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MaterialityAssessment;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingExitPlanRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingMonitoringEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link OutsourcingArrangementService} unit tests.
 *
 * <p>Covers the surface the TPRM Lead + Compliance Officer exercise:
 * <ul>
 *   <li>Create / update / transition / soft-delete</li>
 *   <li>Materiality auto-detection (3 paths: critical function, contract value, personal data)</li>
 *   <li>{@code recordReview} math (bumps next_review_due_date +12 months)</li>
 *   <li>Lifecycle state-machine guard</li>
 *   <li>SAMA non-objection gate (cannot APPROVE material without non-objection)</li>
 *   <li>Tenant isolation — every read filters by org</li>
 *   <li>F8 emission with payload shape</li>
 *   <li>Notification-required event on material-with-no-objection create</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OutsourcingArrangementServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private OutsourcingArrangementRepository repo;
    @Mock private OutsourcingMonitoringEventRepository monitoringRepo;
    @Mock private OutsourcingExitPlanRepository exitPlanRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private OutsourcingArrangementService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new OutsourcingArrangementService(repo, monitoringRepo, exitPlanRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ───────────────────────────────────────────────────

    private static ArrangementCreateRequest validReq() {
        return new ArrangementCreateRequest(
            "OUT-2026-001",
            "Microsoft Azure KSA Region",
            "مايكروسوفت أزور — منطقة المملكة",
            null, null,
            "IT_INFRASTRUCTURE",
            42L, null,
            true,
            "Hosts customer PII and SAMA returns",
            "يستضيف بيانات العملاء وتقارير مؤسسة النقد",
            true, true, "SAMA-NO-2024-1234", LocalDate.of(2024, 3, 15),
            LocalDate.of(2024, 4, 1), LocalDate.of(2027, 3, 31),
            new BigDecimal("18500000.00"), "EA-MSFT-001", null,
            "CUSTOMER_DATA",
            "SA", "Riyadh", true,
            true, true, LocalDate.of(2024, 3, 20),
            null,
            1L, 2L,
            LocalDate.of(2026, 12, 31)
        );
    }

    private static OutsourcingArrangement existing(String stage, boolean material) {
        OutsourcingArrangement o = new OutsourcingArrangement();
        o.setId(101L);
        o.setOrganisationId(ORG_ID);
        o.setExternalId(UUID.randomUUID());
        o.setArrangementCode("OUT-2026-001");
        o.setTitleEn("Microsoft Azure KSA");
        o.setTitleAr("مايكروسوفت أزور — المملكة");
        o.setServiceType("IT_INFRASTRUCTURE");
        o.setLifecycleStage(stage);
        o.setIsMaterial(material);
        o.setSamaNonObjectionRequired(material);
        o.setSamaNonObjectionObtained(false);
        o.setCriticalFunction(material ? "CUSTOMER_DATA" : "NA");
        o.setCountryOfProcessing("SA");
        o.setDataResidencyKdc(true);
        o.setOwnerUserId(1L);
        o.setSponsorUserId(2L);
        o.setPersonalDataProcessed(material);
        return o;
    }

    // ─── Create ─────────────────────────────────────────────────────

    @Test void create_persistsPlannedRow() {
        when(repo.existsByOrganisationIdAndArrangementCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> {
            OutsourcingArrangement o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        ArrangementResponse out = service.create(validReq());

        assertThat(out.arrangementCode()).isEqualTo("OUT-2026-001");
        assertThat(out.lifecycleStage()).isEqualTo("PLANNED");
        assertThat(out.isMaterial()).isTrue();
        assertThat(out.samaNonObjectionRequired()).isTrue();
        // Material + non-objection obtained = no extra notification trigger
        verify(publisher, times(1)).publishEvent(any(DomainEvent.class));
        verify(audit).log(eq("CREATE"), eq("OutsourcingArrangement"), eq(101L), anyString());
    }

    @Test void create_materialWithoutNonObjection_firesNotificationEvent() {
        ArrangementCreateRequest req = new ArrangementCreateRequest(
            "OUT-2026-099",
            "New material arrangement",
            "ترتيب جوهري جديد",
            null, null,
            "CALL_CENTRE",
            null, null,
            true,
            "Customer-facing", "تواجه العميل",
            true, false, null, null,
            null, null, new BigDecimal("8000000"), null, null,
            "CUSTOMER_DATA",
            "SA", "Riyadh", true,
            true, false, null, null,
            1L, 2L, null
        );
        when(repo.existsByOrganisationIdAndArrangementCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> {
            OutsourcingArrangement o = inv.getArgument(0);
            o.setId(202L);
            return o;
        });

        service.create(req);

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(DomainEvent::triggerEvent)
            .containsExactly("OUTSOURCING_REGISTERED", "OUTSOURCING_SAMA_NOTIFICATION_REQUIRED");
    }

    @Test void create_duplicateCode_throws() {
        when(repo.existsByOrganisationIdAndArrangementCodeAndIsDeletedFalse(eq(ORG_ID), eq("OUT-2026-001")))
            .thenReturn(true);
        assertThatThrownBy(() -> service.create(validReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Arrangement code already exists");
        verify(repo, never()).save(any());
    }

    // ─── Materiality auto-detection ──────────────────────────────────

    @Test void materialityAssessment_criticalFunctionPath() {
        OutsourcingArrangement o = existing("ACTIVE", false);
        o.setCriticalFunction("PAYMENTS");
        o.setPersonalDataProcessed(false);
        o.setContractValueSarAnnual(new BigDecimal("1000000"));
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));

        MaterialityAssessment out = service.materialityAssessment(101L);

        assertThat(out.suggestedMaterial()).isTrue();
        assertThat(out.reasons()).contains("critical_function=PAYMENTS");
    }

    @Test void materialityAssessment_contractValuePath() {
        OutsourcingArrangement o = existing("ACTIVE", false);
        o.setCriticalFunction("NA");
        o.setPersonalDataProcessed(false);
        o.setContractValueSarAnnual(new BigDecimal("6000000.00"));
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));

        MaterialityAssessment out = service.materialityAssessment(101L);

        assertThat(out.suggestedMaterial()).isTrue();
        assertThat(out.reasons()).anyMatch(r -> r.startsWith("contract_value_sar_annual>"));
    }

    @Test void materialityAssessment_personalDataPath() {
        OutsourcingArrangement o = existing("ACTIVE", false);
        o.setCriticalFunction("NA");
        o.setContractValueSarAnnual(new BigDecimal("100000"));
        o.setPersonalDataProcessed(true);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));

        MaterialityAssessment out = service.materialityAssessment(101L);

        assertThat(out.suggestedMaterial()).isTrue();
        assertThat(out.reasons()).contains("personal_data_processed=true");
    }

    @Test void materialityAssessment_nonMaterial_noPathsHit() {
        OutsourcingArrangement o = existing("ACTIVE", false);
        o.setCriticalFunction("NA");
        o.setContractValueSarAnnual(new BigDecimal("100000"));
        o.setPersonalDataProcessed(false);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));

        MaterialityAssessment out = service.materialityAssessment(101L);

        assertThat(out.suggestedMaterial()).isFalse();
        assertThat(out.reasons()).isEmpty();
    }

    // ─── Update — materiality change emits event ─────────────────────

    @Test void update_materialityFlip_emitsMaterialChangeEvent() {
        OutsourcingArrangement o = existing("ACTIVE", false);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> inv.getArgument(0));

        // 30 fields total; isMaterial at index 8 (titleEn, titleAr, descEn, descAr,
        // serviceType, primaryVendorId, coVendorIds, isMaterial, ...).
        ArrangementUpdateRequest req = new ArrangementUpdateRequest(
            null, null, null, null,         // 1-4: titleEn..descAr
            null, null, null,               // 5-7: serviceType, primaryVendorId, coVendorIds
            true,                            // 8: isMaterial (flip)
            null, null,                      // 9-10: materialityBasisEn/Ar
            null, null, null, null,         // 11-14: SAMA fields
            null, null,                      // 15-16: contractStart/End
            null, null, null,                // 17-19: contractValue, ref, url
            null,                            // 20: criticalFunction
            null, null,                      // 21-22: country, region
            null,                            // 23: dataResidencyKdc
            null, null, null,                // 24-26: personalData, dpaSigned, dpaDate
            null,                            // 27: crossBorderSafeguard
            null, null,                      // 28-29: ownerUserId, sponsorUserId
            null                             // 30: nextReviewDueDate
        );
        service.update(101L, req);

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue().triggerEvent()).isEqualTo("OUTSOURCING_MATERIAL_CHANGE");
    }

    // ─── recordReview — math + F8 ────────────────────────────────────

    @Test void recordReview_bumpsNextReviewBy12Months() {
        OutsourcingArrangement o = existing("ACTIVE", true);
        o.setNextReviewDueDate(LocalDate.of(2026, 1, 1));
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate before = LocalDate.now();
        ArrangementResponse out = service.recordReview(101L, 99L);
        LocalDate after = LocalDate.now();

        // Allow up to 1-day clock skew either side
        assertThat(out.nextReviewDueDate()).isBetween(before.plusMonths(12).minusDays(1), after.plusMonths(12).plusDays(1));
        verify(audit).log(eq("REVIEW"), eq("OutsourcingArrangement"), eq(101L), anyString());
        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue().triggerEvent()).isEqualTo("OUTSOURCING_REVIEWED");
    }

    // ─── Transition guards ──────────────────────────────────────────

    @Test void transition_legalPath_persists() {
        OutsourcingArrangement o = existing("PLANNED", false);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> inv.getArgument(0));

        ArrangementResponse out = service.transition(101L,
            new ArrangementTransitionRequest("DUE_DILIGENCE", "starting onboarding"));

        assertThat(out.lifecycleStage()).isEqualTo("DUE_DILIGENCE");
        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue().triggerEvent()).isEqualTo("OUTSOURCING_LIFECYCLE_TRANSITION");
    }

    @Test void transition_illegalPath_throws() {
        OutsourcingArrangement o = existing("PLANNED", false);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));

        // PLANNED → APPROVED is not allowed (must go through DUE_DILIGENCE first)
        assertThatThrownBy(() -> service.transition(101L,
                new ArrangementTransitionRequest("APPROVED", null)))
            .isInstanceOf(IllegalTransitionException.class);
        verify(repo, never()).save(any());
    }

    @Test void transition_approveMaterialWithoutSamaNonObjection_throws() {
        OutsourcingArrangement o = existing("AWAITING_SAMA", true);
        o.setSamaNonObjectionRequired(true);
        o.setSamaNonObjectionObtained(false);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.transition(101L,
                new ArrangementTransitionRequest("APPROVED", "skip the gate")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SAMA non-objection");
        verify(repo, never()).save(any());
    }

    @Test void transition_approveMaterialWithSamaNonObjection_succeeds() {
        OutsourcingArrangement o = existing("AWAITING_SAMA", true);
        o.setSamaNonObjectionRequired(true);
        o.setSamaNonObjectionObtained(true);
        o.setSamaNonObjectionRef("SAMA-NO-2026-001");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(101L)))
            .thenReturn(Optional.of(o));
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> inv.getArgument(0));

        ArrangementResponse out = service.transition(101L,
            new ArrangementTransitionRequest("APPROVED", null));

        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_passesCurrentTenantToRepo() {
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(eq(ORG_ID)))
            .thenReturn(List.of(existing("ACTIVE", true)));

        List<ArrangementResponse> out = service.list();

        assertThat(out).hasSize(1);
        verify(repo).findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(eq(ORG_ID));
    }

    // ─── F8 payload shape ───────────────────────────────────────────

    @Test void create_emitsRegistrationEventWithFullPayload() {
        when(repo.existsByOrganisationIdAndArrangementCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(repo.save(any(OutsourcingArrangement.class))).thenAnswer(inv -> {
            OutsourcingArrangement o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        service.create(validReq());

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(1)).publishEvent(events.capture());
        DomainEvent ev = events.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("OUTSOURCING_REGISTERED");
        assertThat(ev.entityType()).isEqualTo("OutsourcingArrangement");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload())
            .containsEntry("arrangementCode", "OUT-2026-001")
            .containsEntry("serviceType", "IT_INFRASTRUCTURE")
            .containsEntry("isMaterial", "true")
            .containsEntry("lifecycleStage", "PLANNED");
    }

    // ─── isAutoMaterial helper ──────────────────────────────────────

    @Test void isAutoMaterial_picksUpAnySinglePath() {
        assertThat(OutsourcingArrangementService.isAutoMaterial("PAYMENTS", null, false)).isTrue();
        assertThat(OutsourcingArrangementService.isAutoMaterial("NA", new BigDecimal("10000000"), false)).isTrue();
        assertThat(OutsourcingArrangementService.isAutoMaterial("NA", null, true)).isTrue();
        assertThat(OutsourcingArrangementService.isAutoMaterial("NA", new BigDecimal("1000"), false)).isFalse();
        assertThat(OutsourcingArrangementService.isAutoMaterial(null, null, null)).isFalse();
    }
}
