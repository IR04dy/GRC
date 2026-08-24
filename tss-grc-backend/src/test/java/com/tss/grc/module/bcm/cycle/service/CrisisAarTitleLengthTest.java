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
import jakarta.persistence.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F2-338 — <b>the After-Action Report title is capped to its column, so a long-but-legal crisis
 * title cannot 500 the AAR generation.</b>
 *
 * <p>{@code generate()} composed {@code crisis_aar.title_en/title_ar} (NVARCHAR(300)) as
 * {@code "After-Action Report — " + crisisEvent.title} — and {@code crisis_events.title_en} is
 * NVARCHAR(500), with no cap on the composition. A crisis whose title runs 279–500 chars (legal
 * in its own column) therefore overflowed the 300-char AAR column and turned generation of the
 * NCEMA / ISO 22301 After-Action Report into a 500. This is F2-185b: a length budget must account
 * for everything the code adds — here a 22-char fixed prefix — not just the source field.
 *
 * <p>Pure composition logic, fully visible to a mocked repository (no DB, tenant, row_version or
 * collation dimension), so verified hermetically — the F2-320/F2-325 calibration standard. The cap
 * is asserted against the entity's own {@code @Column(length)} by reflection, so the test cannot
 * drift from the column it protects (the F2-185b mirror).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrisisAarTitleLengthTest {

    private static final Long ORG = 5L;

    @Mock private CrisisAarRepository                    aars;
    @Mock private CrisisEventRepository                  crises;
    @Mock private CrisisSituationReportRepository        sitreps;
    @Mock private CrisisDecisionRepository               decisions;
    @Mock private CrisisRegulatoryNotificationRepository regNotifications;
    @Mock private AuditLogService                        audit;
    @Mock private ApplicationEventPublisher              eventPublisher;

    @InjectMocks private CrisisAarService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(aars.save(any(CrisisAar.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sitreps.findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(decisions.findByOrganisationIdAndCrisisEventIdOrderByDecidedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(regNotifications.findByOrganisationIdAndCrisisEventIdOrderByNotifiedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(aars.countByOrganisationIdAndReportCodeStartingWith(eq(ORG), anyString()))
            .thenReturn(0L);
        when(aars.findByOrganisationIdAndReportCode(eq(ORG), anyString()))
            .thenReturn(Optional.empty());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static int columnLen(String field) throws Exception {
        return CrisisAar.class.getDeclaredField(field).getAnnotation(Column.class).length();
    }

    // ── the defect: a maximal legal source title overflows the AAR column ─────────────────────────

    @Test void aLongCrisisTitleDoesNotOverflowTheAarTitleColumn() throws Exception {
        // Both titles at the crisis_events.title_* ceiling (NVARCHAR(500)) — entirely legal there.
        CrisisEvent ce = event(60L, "X".repeat(500), "ء".repeat(500));
        when(crises.findByIdAndOrganisationId(60L, ORG)).thenReturn(Optional.of(ce));

        CrisisAar out = service.generate(60L);

        int enMax = columnLen("titleEn");
        int arMax = columnLen("titleAr");
        // The composed title must fit the column it is stored in — the whole finding.
        assertThat(out.getTitleEn().length()).isLessThanOrEqualTo(enMax);
        assertThat(out.getTitleAr().length()).isLessThanOrEqualTo(arMax);
        // The service cap must equal the column length, so the two cannot drift (F2-185b mirror).
        assertThat(CrisisAarService.TITLE_MAX).isEqualTo(enMax).isEqualTo(arMax);
        // The prefix is far shorter than the column, so it always survives the cap.
        assertThat(out.getTitleEn()).startsWith("After-Action Report — ");
        assertThat(out.getTitleAr()).startsWith("تقرير ما بعد الحدث — ");
    }

    // ── grounding: an ordinary short title is left intact, not truncated ──────────────────────────

    @Test void aShortCrisisTitleIsLeftIntact() {
        CrisisEvent ce = event(61L, "Ransomware on file server", "برنامج فدية");
        when(crises.findByIdAndOrganisationId(61L, ORG)).thenReturn(Optional.of(ce));

        CrisisAar out = service.generate(61L);

        assertThat(out.getTitleEn()).isEqualTo("After-Action Report — Ransomware on file server");
        assertThat(out.getTitleEn()).doesNotContain("…");   // no ellipsis — nothing was cut
        assertThat(out.getTitleAr()).contains("برنامج فدية");
    }

    private CrisisEvent event(Long id, String en, String ar) {
        CrisisEvent ce = new CrisisEvent();
        ce.setId(id);
        ce.setOrganisationId(ORG);
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
}
