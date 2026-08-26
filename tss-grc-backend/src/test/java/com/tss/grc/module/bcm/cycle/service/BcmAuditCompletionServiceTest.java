package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.entity.SiemInboundEvent;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmManagementReviewRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.SiemInboundEventRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-380 — the SIEM/SOAR auto-escalation composes a CrisisEvent title from the ingested alert's own
 * title. Both SiemInboundEvent.title and CrisisEvent.title_en are @Column(length = 500), so a
 * 479–500 char SIEM title (stored fine) plus the 22-char "SIEM auto-escalation: " prefix reached up
 * to 522 chars and overflowed crisis_events.title_en → SQL 8152. As ingestSiemEvent is
 * @Transactional, that rolled back the WHOLE ingestion: a severity≥3 alert neither recorded nor
 * escalated. The composed title is now capped to the column length (F2-338's shape).
 *
 * <p>Hermetic (Mockito, no DB — a mocked repo has no column, so a unit test cannot reproduce the
 * 8152; assert the cap the service applies before save, per F2-320 / F2-325).
 */
@ExtendWith(MockitoExtension.class)
class BcmAuditCompletionServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private BcmManagementReviewRepository mgmtReviews;
    @Mock private SiemInboundEventRepository    siemEvents;
    @Mock private CrisisEventRepository         crisisEvents;
    @Mock private BcmBiaAssessmentRepository    bias;
    @Mock private AuditLogService               audit;
    @Mock private ApplicationEventPublisher     eventPublisher;
    @InjectMocks private BcmAuditCompletionService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private BcmAuditCompletionService.SiemInboundPayload payload(int severity, String title) {
        return new BcmAuditCompletionService.SiemInboundPayload(
            "Splunk", "ALERT-1", severity, title, "summary", null);
    }

    @Test
    void siemEscalation_longTitle_capsComposedCrisisTitleToColumnLength() {
        // A 500-char SIEM title stores fine in SiemInboundEvent.title (500), but composed with the
        // 22-char prefix it would be 522 — the pre-fix overflow. The cap must bring it to <= 500.
        when(crisisEvents.save(any(CrisisEvent.class))).thenAnswer(inv -> {
            CrisisEvent c = inv.getArgument(0); c.setId(99L); return c;
        });
        when(siemEvents.save(any(SiemInboundEvent.class))).thenAnswer(inv -> {
            SiemInboundEvent s = inv.getArgument(0); s.setId(7L); return s;
        });

        service.ingestSiemEvent(payload(4, "X".repeat(500)));

        ArgumentCaptor<CrisisEvent> captor = ArgumentCaptor.forClass(CrisisEvent.class);
        org.mockito.Mockito.verify(crisisEvents).save(captor.capture());
        assertThat(captor.getValue().getTitleEn().length())
            .as("composed crisis title must not exceed crisis_events.title_en (500)")
            .isLessThanOrEqualTo(BcmAuditCompletionService.CRISIS_TITLE_MAX);
    }

    @Test
    void siemEscalation_shortTitle_composesFullTitleUntruncated() {
        when(crisisEvents.save(any(CrisisEvent.class))).thenAnswer(inv -> {
            CrisisEvent c = inv.getArgument(0); c.setId(99L); return c;
        });
        when(siemEvents.save(any(SiemInboundEvent.class))).thenAnswer(inv -> {
            SiemInboundEvent s = inv.getArgument(0); s.setId(7L); return s;
        });

        service.ingestSiemEvent(payload(4, "Ransomware on core banking"));

        ArgumentCaptor<CrisisEvent> captor = ArgumentCaptor.forClass(CrisisEvent.class);
        org.mockito.Mockito.verify(crisisEvents).save(captor.capture());
        assertThat(captor.getValue().getTitleEn())
            .isEqualTo("SIEM auto-escalation: Ransomware on core banking");
    }

    @Test
    void capConstantMirrorsTheColumnLength() throws Exception {
        int columnLength = CrisisEvent.class.getDeclaredField("titleEn")
            .getAnnotation(Column.class).length();
        assertThat(BcmAuditCompletionService.CRISIS_TITLE_MAX)
            .as("CRISIS_TITLE_MAX must mirror CrisisEvent.title_en's @Column(length)")
            .isEqualTo(columnLength);
    }
}
