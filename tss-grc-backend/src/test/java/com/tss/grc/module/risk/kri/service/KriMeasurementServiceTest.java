package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementResponse;
import com.tss.grc.module.risk.kri.entity.KriBreach;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.kri.repository.KriBreachRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KRI measurement engine — Sprint 13.
 *
 * <p>Locks the four-quadrant {@link KriMeasurementService#evaluateStatus}
 * function, breach open/close/escalate behaviour, the F8 event contract,
 * tenant isolation, and the sustained-red → CAPA delegate.
 */
@ExtendWith(MockitoExtension.class)
class KriMeasurementServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long KRI_ID = 42L;
    private static final UUID KRI_EX = UUID.randomUUID();

    @Mock private KriDefinitionRepository defRepo;
    @Mock private KriMeasurementRepository measRepo;
    @Mock private KriBreachRepository breachRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private KriBreachService breachService;

    private KriMeasurementService service;

    @BeforeEach
    void setUp() {
        service = new KriMeasurementService(defRepo, measRepo, breachRepo, audit, eventPublisher, breachService);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static KriDefinition lowerBetter() {
        KriDefinition d = new KriDefinition();
        d.setId(KRI_ID);
        d.setExternalId(KRI_EX);
        d.setOrganisationId(ORG_ID);
        d.setKriCode("KRI-CYBER-FLA-001");
        d.setTitleEn("Failed login attempts per day");
        d.setTitleAr("محاولات تسجيل الدخول الفاشلة في اليوم");
        d.setCategory("CYBER");
        d.setMeasurementUnit("COUNT");
        d.setValueDirection("LOWER_IS_BETTER");
        d.setGreenThreshold(new BigDecimal("50"));
        d.setAmberThreshold(new BigDecimal("200"));
        d.setRedThreshold(new BigDecimal("200"));
        d.setIsActive(Boolean.TRUE);
        return d;
    }

    private static KriDefinition higherBetter() {
        KriDefinition d = new KriDefinition();
        d.setId(KRI_ID);
        d.setExternalId(KRI_EX);
        d.setOrganisationId(ORG_ID);
        d.setKriCode("KRI-LIQUIDITY-LCR-001");
        d.setTitleEn("LCR");
        d.setTitleAr("نسبة تغطية السيولة");
        d.setCategory("LIQUIDITY");
        d.setMeasurementUnit("PERCENTAGE");
        d.setValueDirection("HIGHER_IS_BETTER");
        d.setGreenThreshold(new BigDecimal("130"));
        d.setAmberThreshold(new BigDecimal("120"));
        d.setRedThreshold(new BigDecimal("110"));
        d.setIsActive(Boolean.TRUE);
        return d;
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── evaluateStatus — four-quadrant truth table ──────────────

    @Test
    void evaluateStatus_lowerBetter_green() {
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("30"), lowerBetter()))
                .isEqualTo("GREEN");
        // boundary on green threshold is inclusive (GREEN).
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("50"), lowerBetter()))
                .isEqualTo("GREEN");
    }

    @Test
    void evaluateStatus_lowerBetter_amber() {
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("150"), lowerBetter()))
                .isEqualTo("AMBER");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("200"), lowerBetter()))
                .isEqualTo("AMBER");
    }

    @Test
    void evaluateStatus_lowerBetter_red() {
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("201"), lowerBetter()))
                .isEqualTo("RED");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("9999"), lowerBetter()))
                .isEqualTo("RED");
    }

    @Test
    void evaluateStatus_higherBetter_quadrants() {
        KriDefinition lcr = higherBetter();
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("145"), lcr)).isEqualTo("GREEN");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("130"), lcr)).isEqualTo("GREEN");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("125"), lcr)).isEqualTo("AMBER");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("120"), lcr)).isEqualTo("AMBER");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("110"), lcr)).isEqualTo("RED");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("50"), lcr)).isEqualTo("RED");
    }

    @Test
    void evaluateStatus_naWhenThresholdsMissing() {
        KriDefinition naked = new KriDefinition();
        naked.setValueDirection("LOWER_IS_BETTER");
        assertThat(KriMeasurementService.evaluateStatus(new BigDecimal("10"), naked)).isEqualTo("NA");
    }

    @Test
    void evaluateStatus_naWhenValueNull() {
        assertThat(KriMeasurementService.evaluateStatus(null, lowerBetter())).isEqualTo("NA");
    }

    // ─── record — happy path ─────────────────────────────────────

    @Test
    void record_persistsMeasurementAndEvaluatesStatus() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.empty());

        KriMeasurementResponse out = service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("30"), null, "MANUAL", null, null));

        assertThat(out.status()).isEqualTo("GREEN");
        assertThat(out.value()).isEqualByComparingTo("30");
        verify(audit).log(eq("KRI_MEASURED"), eq("KriDefinition"), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues().stream().anyMatch(e -> "KRI_MEASURED".equals(e.triggerEvent())))
                .isTrue();
    }

    @Test
    void record_amberOpensBreach() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.empty());
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(assignId(new AtomicLong(7000)));

        service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("150"), null, "MANUAL", null, null));

        ArgumentCaptor<KriBreach> bcap = ArgumentCaptor.forClass(KriBreach.class);
        verify(breachRepo).save(bcap.capture());
        assertThat(bcap.getValue().getBreachLevel()).isEqualTo("AMBER");
        assertThat(bcap.getValue().getClosedAt()).isNull();
        verify(audit).log(eq("KRI_BREACH_OPENED"), eq("KriBreach"), anyLong(), anyString());
    }

    @Test
    void record_redEscalatesOpenAmberBreach() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));

        KriBreach openAmber = new KriBreach();
        openAmber.setId(7001L);
        openAmber.setKriId(KRI_ID);
        openAmber.setOrganisationId(ORG_ID);
        openAmber.setBreachLevel("AMBER");
        openAmber.setOpenedAt(LocalDateTime.now().minusDays(2));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(openAmber));
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(assignId(new AtomicLong(7100)));

        service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("500"), null, "MANUAL", null, null));

        ArgumentCaptor<KriBreach> bcap = ArgumentCaptor.forClass(KriBreach.class);
        verify(breachRepo, atLeastOnce()).save(bcap.capture());
        // Two saves: the AMBER row closed + a new RED row opened.
        boolean sawClosedAmber = bcap.getAllValues().stream()
                .anyMatch(b -> "AMBER".equals(b.getBreachLevel()) && b.getClosedAt() != null);
        boolean sawOpenRed = bcap.getAllValues().stream()
                .anyMatch(b -> "RED".equals(b.getBreachLevel()) && b.getClosedAt() == null);
        assertThat(sawClosedAmber).isTrue();
        assertThat(sawOpenRed).isTrue();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues().stream().anyMatch(e -> "KRI_BREACH_ESCALATED".equals(e.triggerEvent())))
                .isTrue();
    }

    @Test
    void record_greenClosesOpenBreach() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));

        KriBreach openRed = new KriBreach();
        openRed.setId(7002L);
        openRed.setKriId(KRI_ID);
        openRed.setOrganisationId(ORG_ID);
        openRed.setBreachLevel("RED");
        openRed.setOpenedAt(LocalDateTime.now().minusDays(5));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(openRed));
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("10"), null, "MANUAL", null, null));

        ArgumentCaptor<KriBreach> bcap = ArgumentCaptor.forClass(KriBreach.class);
        verify(breachRepo).save(bcap.capture());
        assertThat(bcap.getValue().getClosedAt()).isNotNull();
        verify(audit).log(eq("KRI_BREACH_CLOSED"), eq("KriBreach"), anyLong(), anyString());
    }

    @Test
    void record_amberThatStaysAmberDoesNotOpenSecondBreach() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));

        KriBreach openAmber = new KriBreach();
        openAmber.setId(7003L);
        openAmber.setKriId(KRI_ID);
        openAmber.setOrganisationId(ORG_ID);
        openAmber.setBreachLevel("AMBER");
        openAmber.setOpenedAt(LocalDateTime.now().minusDays(1));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(openAmber));

        service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("180"), null, "MANUAL", null, null));

        // No save on the breach repo — the existing row stays open.
        verify(breachRepo, never()).save(any(KriBreach.class));
    }

    @Test
    void record_redInvokesSustainedRedCheck() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.empty());
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(assignId(new AtomicLong(7100)));

        service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("999"), null, "MANUAL", null, null));

        // The measurement service must consult the breach service so
        // the sustained-red ladder gets a chance to open a CAPA.
        verify(breachService).checkSustainedRed(d);
    }

    @Test
    void record_greenDoesNotInvokeSustainedRedCheck() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.empty());

        service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("10"), null, "MANUAL", null, null));

        verify(breachService, never()).checkSustainedRed(any(KriDefinition.class));
    }

    // ─── Tenant isolation + validation ───────────────────────────

    @Test
    void record_otherTenantThrows() {
        TenantContext.setCurrentTenant(999L);
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(999L, KRI_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("1"), null, "MANUAL", null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void record_rejectsNullValue() {
        KriDefinition d = lowerBetter();
        assertThatThrownBy(() ->
                service.recordInternal(d, null, "MANUAL", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void record_rejectsInvalidSource() {
        KriDefinition d = lowerBetter();
        assertThatThrownBy(() ->
                service.recordInternal(d, new BigDecimal("1"), "WAT", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void record_naValueRecordedNoBreachAction() {
        KriDefinition d = new KriDefinition();
        d.setId(KRI_ID);
        d.setExternalId(KRI_EX);
        d.setOrganisationId(ORG_ID);
        d.setKriCode("KRI-NO-THRESH-001");
        d.setTitleEn("No thresholds");
        d.setTitleAr("بدون عتبات");
        d.setCategory("OPERATIONAL");
        d.setMeasurementUnit("COUNT");
        d.setValueDirection("LOWER_IS_BETTER");
        // No green / amber thresholds → status falls back to NA.
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        AtomicLong seq = new AtomicLong(900);
        when(measRepo.save(any(KriMeasurement.class))).thenAnswer(assignId(seq));
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.empty());

        KriMeasurementResponse out = service.record(KRI_EX,
                new KriMeasurementRequest(new BigDecimal("42"), null, "MANUAL", null, null));

        assertThat(out.status()).isEqualTo("NA");
        verify(breachRepo, never()).save(any(KriBreach.class));
    }

    // ─── recent / scheduled evaluation ────────────────────────────

    @Test
    void recent_returnsTenantScopedMeasurements() {
        KriDefinition d = lowerBetter();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        KriMeasurement m = new KriMeasurement();
        m.setId(900L);
        m.setKriId(KRI_ID);
        m.setOrganisationId(ORG_ID);
        m.setValue(new BigDecimal("30"));
        m.setStatus("GREEN");
        m.setSource("MANUAL");
        m.setMeasuredAt(LocalDateTime.now());
        when(measRepo.findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                eq(ORG_ID), eq(KRI_ID), any())).thenReturn(List.of(m));

        List<KriMeasurementResponse> out = service.recent(KRI_EX, 10);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo("GREEN");
        assertThat(out.get(0).kriCode()).isEqualTo("KRI-CYBER-FLA-001");
    }

    @Test
    void evaluateAll_walksActiveNonManualKris() {
        KriDefinition manual = lowerBetter();
        manual.setSourceQueryType("MANUAL");
        KriDefinition sql = lowerBetter();
        sql.setId(43L);
        sql.setKriCode("KRI-CYBER-FLA-002");
        sql.setSourceQueryType("SQL");
        when(defRepo.findByOrganisationIdAndIsActiveTrueAndIsDeletedFalse(ORG_ID))
                .thenReturn(List.of(manual, sql));

        // Non-MANUAL KRIs have no fetcher bound yet, so each is skipped;
        // the returned count is the number SKIPPED (not dispatched).
        int skipped = service.evaluateAll(ORG_ID);

        assertThat(skipped).isEqualTo(1);
        verify(audit).log(eq("KRI_EVALUATION_SKIPPED"), eq("KriDefinition"), anyLong(), anyString());
    }
}
