package com.tss.grc.module.platform.health;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DbSizeMonitorJob}.
 *
 * <p>Covers threshold classification, F8 emission shape, audit logging, and
 * the configuration-validity check. The actual cron-schedule binding is
 * a Spring concern — exercised by booting the context, not here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DbSizeMonitorJobTest {

    private static final long WARN  = 7168L;
    private static final long ALERT = 9216L;
    private static final long HALT  = 9728L;

    @Mock JdbcTemplate jdbcTemplate;
    @Mock ApplicationEventPublisher publisher;
    @Mock AuditLogService audit;

    DbSizeMonitorJob job;

    @BeforeEach
    void setup() {
        job = new DbSizeMonitorJob(jdbcTemplate, publisher, audit, WARN, ALERT, HALT);
    }

    // ───────────────── Configuration validation ─────────────────

    @Test
    void constructor_rejects_non_ascending_thresholds() {
        assertThatThrownBy(() ->
            new DbSizeMonitorJob(jdbcTemplate, publisher, audit, 9000L, 8000L, 9500L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("strictly ascending");
    }

    @Test
    void constructor_rejects_equal_thresholds() {
        assertThatThrownBy(() ->
            new DbSizeMonitorJob(jdbcTemplate, publisher, audit, 7000L, 7000L, 9500L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────── Threshold classification ─────────────────

    @Test
    void classify_returns_OK_below_warn() {
        assertThat(job.classify(7167L)).isEqualTo(DbSizeMonitorJob.Severity.OK);
        assertThat(job.classify(0L)).isEqualTo(DbSizeMonitorJob.Severity.OK);
    }

    @Test
    void classify_returns_WARN_at_warn_threshold() {
        assertThat(job.classify(WARN)).isEqualTo(DbSizeMonitorJob.Severity.WARN);
        assertThat(job.classify(9215L)).isEqualTo(DbSizeMonitorJob.Severity.WARN);
    }

    @Test
    void classify_returns_ALERT_at_alert_threshold() {
        assertThat(job.classify(ALERT)).isEqualTo(DbSizeMonitorJob.Severity.ALERT);
        assertThat(job.classify(9727L)).isEqualTo(DbSizeMonitorJob.Severity.ALERT);
    }

    @Test
    void classify_returns_HALT_at_halt_threshold() {
        assertThat(job.classify(HALT)).isEqualTo(DbSizeMonitorJob.Severity.HALT);
        assertThat(job.classify(10240L)).isEqualTo(DbSizeMonitorJob.Severity.HALT);
    }

    // ───────────────── Size measurement ─────────────────

    @Test
    void measureSizeMb_rounds_decimal_to_nearest_mb() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(8512.7);
        assertThat(job.measureSizeMb()).isEqualTo(8513L);
    }

    @Test
    void measureSizeMb_handles_null_result() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(null);
        assertThat(job.measureSizeMb()).isZero();
    }

    // ───────────────── Tick & emission ─────────────────

    @Test
    void tick_emits_nothing_when_under_warn() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(4096.0);
        job.tick();
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
        verify(audit, never()).log(anyString(), anyString(), any(), anyString());
    }

    @Test
    void tick_emits_WARN_event_when_above_warn_threshold() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(7500.0);
        job.tick();

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(1)).publishEvent(evCap.capture());
        DomainEvent ev = evCap.getValue();

        assertThat(ev.triggerEvent()).isEqualTo("DB_SIZE_THRESHOLD_BREACHED");
        assertThat(ev.entityType()).isEqualTo("Database");
        assertThat(ev.organisationId()).isNull();   // platform-wide
        assertThat(ev.payload()).containsEntry("severity", "WARN");
        assertThat(ev.payload()).containsEntry("sizeMb", 7500L);
        assertThat(ev.payload()).containsEntry("thresholdMb", WARN);
        assertThat(ev.payload()).containsEntry("capMb", 10240L);

        verify(audit).log(eq("DB_SIZE_WARN"), eq("Database"), eq(null), anyString());
    }

    @Test
    void tick_emits_ALERT_event_when_above_alert_threshold() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(9300.0);
        job.tick();

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().payload()).containsEntry("severity", "ALERT");
        verify(audit).log(eq("DB_SIZE_ALERT"), anyString(), eq(null), anyString());
    }

    @Test
    void tick_emits_HALT_event_when_above_halt_threshold() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(9800.0);
        job.tick();

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        DomainEvent ev = evCap.getValue();
        assertThat(ev.payload()).containsEntry("severity", "HALT");
        // 9800 / 10240 = 95.7%
        assertThat((Double) ev.payload().get("pctOfCap")).isBetween(95.5, 96.0);
        verify(audit).log(eq("DB_SIZE_HALT"), anyString(), eq(null), anyString());
    }

    @Test
    void tick_swallows_jdbc_error_and_continues() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class)))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB down"));
        // No exception thrown
        job.tick();
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void tick_continues_when_audit_write_fails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(9500.0);
        when(audit.log(anyString(), anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("audit DB unreachable"));
        // Should still publish the F8 event — alert pathway must not depend on audit
        job.tick();
        verify(publisher, times(1)).publishEvent(any(DomainEvent.class));
    }

    @Test
    void emit_payload_includes_threshold_config_for_downstream_rule_authors() {
        // Automation rules want to know "what threshold did we pass" without
        // re-reading config from another source. Lock the payload shape.
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class))).thenReturn(8000.0);
        job.tick();

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        DomainEvent ev = evCap.getValue();
        assertThat(ev.payload()).containsKeys(
            "severity", "sizeMb", "thresholdMb", "capMb", "pctOfCap",
            "warnMb", "alertMb", "haltMb");
    }
}
