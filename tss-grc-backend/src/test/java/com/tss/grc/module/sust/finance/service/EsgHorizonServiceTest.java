package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.finance.entity.EnvPermit;
import com.tss.grc.module.sust.finance.entity.EnvPermitViolation;
import com.tss.grc.module.sust.finance.repository.CbamLineRepository;
import com.tss.grc.module.sust.finance.repository.EnvPermitRepository;
import com.tss.grc.module.sust.finance.repository.EnvPermitViolationRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetActualRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetMilestoneRepository;
import com.tss.grc.module.sust.finance.repository.EsgTargetRepository;
import com.tss.grc.module.sust.finance.repository.IfrsRequirementRepository;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import jakarta.persistence.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-346 — {@code EsgHorizonService.recordViolation} composes the auto-raised CAPA's title as
 * {@code "NCEC violation on permit <no>: " + descriptionEn}, and {@code CorrectiveAction.title_en}
 * is {@code @Column(length = 255)}. {@code descriptionEn} is {@code @Size(max = 500)} /
 * {@code NVARCHAR(500)}, so a long-but-legal violation description overflowed the 255-char title
 * column → SQL 8152 → 500 on recording an NCEC environmental-permit violation (the violation and
 * its auto-CAPA are one transaction, so both roll back). F2-338's server-composed-overflow family;
 * the fix caps the composed title to the column length.
 *
 * <p>Hermetic: a unit test cannot hit a real 8152 (the mocked repository has no column), so it
 * asserts the cap the service now applies before saving — the fix's guarantee (F2-338's pattern).
 */
@ExtendWith(MockitoExtension.class)
class EsgHorizonServiceTest {

    private static final Long ORG = 1L;
    private static final Long PERMIT_ID = 5L;
    private static final String PREFIX = "NCEC violation on permit NCEC-2026-000123: ";

    @Mock private EsgTargetRepository targets;
    @Mock private EsgTargetActualRepository actuals;
    @Mock private EsgTargetMilestoneRepository milestones;
    @Mock private IfrsRequirementRepository ifrs;
    @Mock private EnvPermitRepository permits;
    @Mock private EnvPermitViolationRepository violations;
    @Mock private CbamLineRepository cbam;
    @Mock private GhgEmissionRecordRepository ghgRecords;
    @Mock private CorrectiveActionRepository capas;
    @Mock private AuditLogService audit;
    @InjectMocks private EsgHorizonService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private EnvPermit permit() {
        EnvPermit p = new EnvPermit();
        p.setId(PERMIT_ID);
        p.setOrganisationId(ORG);
        p.setPermitNumber("NCEC-2026-000123");
        p.setFacilityEn("Riyadh Refinery");
        return p;
    }

    private CorrectiveAction recordAndCaptureCapa(String descriptionEn) {
        when(permits.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, PERMIT_ID))
            .thenReturn(Optional.of(permit()));
        when(violations.save(any())).thenAnswer(inv -> {
            EnvPermitViolation e = inv.getArgument(0);
            if (e.getId() == null) e.setId(77L);
            return e;
        });
        when(capas.save(any())).thenAnswer(inv -> {
            CorrectiveAction c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });
        service.recordViolation(PERMIT_ID, LocalDate.of(2026, 8, 24), descriptionEn, "وصف المخالفة", "HIGH");
        ArgumentCaptor<CorrectiveAction> cap = ArgumentCaptor.forClass(CorrectiveAction.class);
        verify(capas).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void recordViolation_capsAutoCapaTitleToTheColumnLength() {
        // 500 chars is the maximum a violation description_en / @Size(500) allows.
        CorrectiveAction ca = recordAndCaptureCapa("x".repeat(500));
        assertThat(ca.getTitleEn().length()).isLessThanOrEqualTo(255);
        assertThat(ca.getTitleEn()).startsWith(PREFIX);       // the permit reference is never truncated away
        assertThat(ca.getTitleEn()).endsWith("…");       // truncation ellipsis
    }

    @Test
    void recordViolation_shortDescription_leavesTitleWhole() {
        CorrectiveAction ca = recordAndCaptureCapa("SO2 limit exceeded during Q3 flare event");
        assertThat(ca.getTitleEn()).isEqualTo(PREFIX + "SO2 limit exceeded during Q3 flare event");
        assertThat(ca.getTitleEn()).doesNotEndWith("…");
    }

    @Test
    void recordViolation_doesNotThrowOnAMaximalDescription() {
        assertThatCode(() -> recordAndCaptureCapa("y".repeat(500))).doesNotThrowAnyException();
    }

    @Test
    void caTitleMax_mirrorsTheCorrectiveActionColumnLength() throws Exception {
        Field f = EsgHorizonService.class.getDeclaredField("CA_TITLE_MAX");
        f.setAccessible(true);
        int cap = (int) f.get(null);
        int col = CorrectiveAction.class.getDeclaredField("titleEn").getAnnotation(Column.class).length();
        assertThat(cap)
            .as("F2-346: CA_TITLE_MAX must mirror CorrectiveAction.title_en @Column(length) so the cap and the column cannot drift")
            .isEqualTo(col)
            .isEqualTo(255);
    }
}
