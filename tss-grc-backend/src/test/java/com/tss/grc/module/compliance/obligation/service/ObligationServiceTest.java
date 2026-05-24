package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationTransitionRequest;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 11 — {@link ObligationService} unit tests.
 *
 * <p>Covers the surface the Compliance Officer exercises:
 * <ul>
 *   <li>Create / update / transition / soft-delete</li>
 *   <li>{@code computeNextDueDate} math for all 9 frequencies</li>
 *   <li>Lifecycle state-machine guard (DRAFT → ACTIVE → SUSPENDED → RETIRED)</li>
 *   <li>Tenant isolation — every read filters by org</li>
 *   <li>F8 event emission with payload shape</li>
 *   <li>Roll-forward after a successful attestation</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ObligationServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private ObligationRepository repo;
    @Mock private ObligationAttestationRepository attestationRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private ObligationService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new ObligationService(repo, attestationRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static ObligationCreateRequest validReq() {
        return new ObligationCreateRequest(
            "OBL-SAMA-2026-001",
            "SAMA monthly Capital Adequacy Ratio (CAR) return",
            "تقرير نسبة كفاية رأس المال الشهري لمؤسسة النقد",
            null, null,
            "SAMA", "SAMA Prudential Returns §4.2",
            "REPORTING", "MONTHLY", "CRITICAL",
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 15), 7,
            42L, null,
            "Treasury", "الخزينة",
            null, null
        );
    }

    private static Obligation existing(String stage, String frequency) {
        Obligation o = new Obligation();
        o.setId(101L);
        o.setOrganisationId(ORG_ID);
        o.setExternalId(UUID.randomUUID());
        o.setObligationCode("OBL-SAMA-2026-001");
        o.setTitleEn("SAMA monthly CAR return");
        o.setTitleAr("تقرير نسبة كفاية رأس المال الشهري");
        o.setRegulator("SAMA");
        o.setObligationType("REPORTING");
        o.setFrequency(frequency);
        o.setSeverityIfMissed("CRITICAL");
        o.setNextDueDate(LocalDate.of(2026, 6, 15));
        o.setLeadTimeDays(7);
        o.setOwnerUserId(42L);
        o.setLifecycleStage(stage);
        return o;
    }

    // ─── Create ──────────────────────────────────────────────────────

    @Test void create_persistsDraftRow() {
        when(repo.existsByOrganisationIdAndObligationCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(false);
        when(repo.save(any(Obligation.class))).thenAnswer(inv -> {
            Obligation o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        ObligationResponse out = service.create(validReq());

        assertThat(out.obligationCode()).isEqualTo("OBL-SAMA-2026-001");
        assertThat(out.lifecycleStage()).isEqualTo("DRAFT");
        assertThat(out.regulator()).isEqualTo("SAMA");
        assertThat(out.frequency()).isEqualTo("MONTHLY");
        assertThat(out.severityIfMissed()).isEqualTo("CRITICAL");
        assertThat(out.titleAr()).contains("كفاية رأس المال");
    }

    @Test void create_duplicateCodeRejected() {
        when(repo.existsByOrganisationIdAndObligationCodeAndIsDeletedFalse(eq(ORG_ID), anyString()))
            .thenReturn(true);
        assertThatThrownBy(() -> service.create(validReq()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void create_emitsObligationCreatedEvent() {
        when(repo.existsByOrganisationIdAndObligationCodeAndIsDeletedFalse(eq(ORG_ID), anyString())).thenReturn(false);
        when(repo.save(any(Obligation.class))).thenAnswer(inv -> {
            Obligation o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        service.create(validReq());

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("OBLIGATION_CREATED");
        assertThat(ev.entityType()).isEqualTo("Obligation");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("regulator", "SAMA");
        assertThat(ev.payload()).containsEntry("frequency", "MONTHLY");
    }

    // ─── Lifecycle transitions ───────────────────────────────────────

    @Test void transition_draftToActive_succeeds() {
        Obligation o = existing("DRAFT", "MONTHLY");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(o));
        when(repo.save(any(Obligation.class))).thenAnswer(inv -> inv.getArgument(0));

        ObligationResponse out = service.transition(101L, new ObligationTransitionRequest("ACTIVE", "Approved by CCO"));

        assertThat(out.lifecycleStage()).isEqualTo("ACTIVE");
    }

    @Test void transition_retiredIsTerminal() {
        Obligation o = existing("RETIRED", "MONTHLY");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.transition(101L, new ObligationTransitionRequest("ACTIVE", null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void transition_emitsObligationTransitionedEvent() {
        Obligation o = existing("DRAFT", "QUARTERLY");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(o));
        when(repo.save(any(Obligation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(101L, new ObligationTransitionRequest("ACTIVE", "Approved"));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("OBLIGATION_TRANSITIONED");
        assertThat(captor.getValue().payload()).containsEntry("previousLifecycleStage", "DRAFT");
        assertThat(captor.getValue().payload()).containsEntry("lifecycleStage", "ACTIVE");
    }

    // ─── computeNextDueDate — all 9 frequencies ──────────────────────

    @Test void computeNextDueDate_daily() {
        Obligation o = existing("ACTIVE", "DAILY");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 5, 10)))
            .isEqualTo(LocalDate.of(2026, 5, 11));
    }

    @Test void computeNextDueDate_weekly() {
        Obligation o = existing("ACTIVE", "WEEKLY");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 5, 10)))
            .isEqualTo(LocalDate.of(2026, 5, 17));
    }

    @Test void computeNextDueDate_monthly() {
        Obligation o = existing("ACTIVE", "MONTHLY");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 5, 15)))
            .isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test void computeNextDueDate_quarterly() {
        Obligation o = existing("ACTIVE", "QUARTERLY");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 3, 31)))
            .isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test void computeNextDueDate_semiAnnual() {
        Obligation o = existing("ACTIVE", "SEMI_ANNUAL");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 1, 31)))
            .isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test void computeNextDueDate_annual() {
        Obligation o = existing("ACTIVE", "ANNUAL");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 6, 30)))
            .isEqualTo(LocalDate.of(2027, 6, 30));
    }

    @Test void computeNextDueDate_biennial() {
        Obligation o = existing("ACTIVE", "BIENNIAL");
        assertThat(service.computeNextDueDate(o, LocalDate.of(2026, 6, 30)))
            .isEqualTo(LocalDate.of(2028, 6, 30));
    }

    @Test void computeNextDueDate_onEventAndAdHocPreserveDate() {
        // Event-driven obligations don't auto-roll forward; the service
        // updates next_due_date when an event registers, not from a
        // calendar tick.
        Obligation onEvent = existing("ACTIVE", "ON_EVENT");
        Obligation adHoc = existing("ACTIVE", "AD_HOC");
        LocalDate d = LocalDate.of(2026, 5, 10);
        assertThat(service.computeNextDueDate(onEvent, d)).isEqualTo(d);
        assertThat(service.computeNextDueDate(adHoc, d)).isEqualTo(d);
    }

    @Test void computeNextDueDate_unknownFrequencyRejected() {
        Obligation o = existing("ACTIVE", "FORTNIGHTLY");
        assertThatThrownBy(() -> service.computeNextDueDate(o, LocalDate.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported frequency");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_alwaysFiltersByOrganisation() {
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(ORG_ID)).thenReturn(List.of());
        service.list();
        verify(repo, times(1)).findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(ORG_ID);
    }

    @Test void listDueWithinDays_routesThroughRepository() {
        when(repo.findDueWithinDays(eq(ORG_ID), any(), any())).thenReturn(List.of());
        service.listDueWithinDays(30);
        verify(repo).findDueWithinDays(eq(ORG_ID), any(), any());
    }

    // ─── Roll-forward ────────────────────────────────────────────────

    @Test void rollForwardAfterAttestation_advancesNextDueDateForMonthly() {
        Obligation o = existing("ACTIVE", "MONTHLY");
        o.setNextDueDate(LocalDate.of(2026, 5, 15));
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(o));
        when(repo.save(any(Obligation.class))).thenAnswer(inv -> inv.getArgument(0));

        Obligation result = service.rollForwardAfterAttestation(101L, 99L, java.time.LocalDateTime.now());

        assertThat(result.getNextDueDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(result.getLastAttestedByUserId()).isEqualTo(99L);
        assertThat(result.getLastAttestedAt()).isNotNull();
    }

    // ─── State machine guard ─────────────────────────────────────────

    @Test void guardLifecycleTransition_drives() {
        // DRAFT → ACTIVE / RETIRED allowed
        ObligationService.guardLifecycleTransition("DRAFT", "ACTIVE");
        ObligationService.guardLifecycleTransition("DRAFT", "RETIRED");
        // ACTIVE → SUSPENDED / RETIRED allowed; ACTIVE → DRAFT not.
        ObligationService.guardLifecycleTransition("ACTIVE", "SUSPENDED");
        ObligationService.guardLifecycleTransition("ACTIVE", "RETIRED");
        assertThatThrownBy(() -> ObligationService.guardLifecycleTransition("ACTIVE", "DRAFT"))
            .isInstanceOf(IllegalTransitionException.class);
        // SUSPENDED → ACTIVE / RETIRED allowed; SUSPENDED → DRAFT not.
        ObligationService.guardLifecycleTransition("SUSPENDED", "ACTIVE");
        assertThatThrownBy(() -> ObligationService.guardLifecycleTransition("SUSPENDED", "DRAFT"))
            .isInstanceOf(IllegalTransitionException.class);
        // RETIRED is terminal.
        assertThatThrownBy(() -> ObligationService.guardLifecycleTransition("RETIRED", "ACTIVE"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ─── Soft delete ─────────────────────────────────────────────────

    @Test void softDelete_marksIsDeleted() {
        Obligation o = existing("ACTIVE", "MONTHLY");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 101L)).thenReturn(Optional.of(o));

        service.softDelete(101L);

        ArgumentCaptor<Obligation> captor = ArgumentCaptor.forClass(Obligation.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }
}
