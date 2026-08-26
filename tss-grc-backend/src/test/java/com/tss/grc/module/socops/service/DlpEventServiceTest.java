package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.DlpEvent;
import com.tss.grc.module.socops.repository.DlpEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-386 — a DLP event ingested through {@code POST /api/v1/socops/dlp/ingest} must be born
 * <b>unreviewed</b>. {@code ingest} binds the whole {@link DlpEvent} entity, so without a scrub a
 * caller (a DLP connector or any {@code DLP_INGEST}/{@code CISO} holder) could POST
 * {@code {severity:CRITICAL, reviewed:true}} and have the event stored "reviewed", silencing the
 * AR-136 {@code DLP_CRITICAL_UNREVIEWED} SOC escalation. Review is the analyst's act, written only
 * by {@code review()}; the create path now scrubs the three review fields, the same server-owned
 * discipline as the SEC-CYB-002 id scrub on the same branch.
 *
 * <p>Hermetic (Mockito, no DB): the escalation is a domain event and the scrub is a field write, so
 * an {@code ArgumentCaptor} on the publisher and one on {@code save} prove both without a database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DlpEventServiceTest {

    private static final Long ORG = 7L;

    @Mock DlpEventRepository repo;
    @Mock AuditLogService audit;
    @Mock ApplicationEventPublisher publisher;

    private DlpEventService svc;

    @BeforeEach
    void setUp() {
        svc = new DlpEventService(repo, audit, publisher);
        TenantContext.setCurrentTenant(ORG);
        // Default: no existing row → the create branch.
        when(repo.findByOrganisationIdAndSourceSystemAndExternalId(any(), any(), any()))
            .thenReturn(Optional.empty());
        // save returns its argument (the scrubbed entity), with a DB-assigned id.
        when(repo.save(any(DlpEvent.class))).thenAnswer(inv -> {
            DlpEvent d = inv.getArgument(0);
            if (d.getId() == null) d.setId(99L);
            return d;
        });
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private DlpEvent critical(boolean reviewed) {
        DlpEvent e = new DlpEvent();
        e.setSourceSystem("Forcepoint");
        e.setExternalId("DLP-1");
        e.setSeverity("CRITICAL");
        e.setDataClassification("CONFIDENTIAL");
        e.setActionTaken("ALERT");
        e.setChannel("EMAIL");
        e.setReviewed(reviewed);
        e.setReviewOutcome(reviewed ? "Cleared by connector" : null);
        e.setReviewNotes(reviewed ? "auto" : null);
        return e;
    }

    /** Load-bearing: a forged {@code reviewed:true} on a CRITICAL ingest is scrubbed to false AND
     *  the AR-136 escalation still fires. Pre-fix both legs are RED (stored reviewed=true; no event). */
    @Test
    void ingest_criticalWithForgedReviewedTrue_isBornUnreviewedAndEscalates() {
        DlpEvent saved = svc.ingest(critical(true));

        assertThat(saved.getReviewed()).isFalse();
        assertThat(saved.getReviewOutcome()).isNull();
        assertThat(saved.getReviewNotes()).isNull();

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues())
            .anyMatch(ev -> "DLP_CRITICAL_UNREVIEWED".equals(ev.triggerEvent()));
    }

    /** Regression: an honestly-unreviewed CRITICAL still escalates (the scrub must not suppress it). */
    @Test
    void ingest_criticalUnreviewed_escalates() {
        svc.ingest(critical(false));

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues())
            .anyMatch(ev -> "DLP_CRITICAL_UNREVIEWED".equals(ev.triggerEvent()));
    }

    /** Re-ingest (dedup) returns the stored row untouched — it never copies the request body, so a
     *  forged reviewed:true on a re-ingest cannot flip an existing event and fires no escalation. */
    @Test
    void reIngest_returnsExistingUntouchedWithoutEscalation() {
        DlpEvent existing = critical(false);
        existing.setId(42L);
        when(repo.findByOrganisationIdAndSourceSystemAndExternalId(any(), any(), any()))
            .thenReturn(Optional.of(existing));

        DlpEvent out = svc.ingest(critical(true));

        assertThat(out).isSameAs(existing);
        verify(repo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }
}
