package com.tss.grc.module.data_officer.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.RecordQualityRunRequest;
import com.tss.grc.module.data_officer.entity.Entities.QualityRule;
import com.tss.grc.module.data_officer.entity.Entities.QualityRun;
import com.tss.grc.module.data_officer.repository.CatalogEntryRepository;
import com.tss.grc.module.data_officer.repository.CatalogLineageRepository;
import com.tss.grc.module.data_officer.repository.DisposalJobRepository;
import com.tss.grc.module.data_officer.repository.NdrRepository;
import com.tss.grc.module.data_officer.repository.QualityRuleRepository;
import com.tss.grc.module.data_officer.repository.QualityRunRepository;
import com.tss.grc.module.data_officer.repository.RetentionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-335 — a data-quality run's RAG {@code band} is caller-supplied free text (no {@code @Pattern},
 * no CHECK, {@code length = 8}) and TWO readers compare it case-sensitively: the
 * {@code QUALITY_RUN_RED} escalation emit in {@link DataGovernanceService#recordQualityRun}, and
 * {@code DataOfficerQueryService#qRed30} — the "RED runs (30d)" dashboard count. A caller-supplied
 * {@code "red"} would be stored verbatim, skip the escalation the dashboard + automation engine
 * react to, AND vanish from the RED count (F2-179's silent-wrong-branch shape). The fix normalises
 * on write (F2-302, not {@code equalsIgnoreCase}, so both readers agree over the stored value), and
 * coerces rather than requires (F2-182 — nothing upstream refuses a non-vocabulary band, so a 400
 * would break a working request).
 *
 * <p>The defect is pure Java logic (a case-sensitive comparison + an emit), fully visible to the
 * mocked {@code support} — no DB / tenant / collation dimension in the fix — so it is verified
 * hermetically rather than by a live probe (the F2-320/F2-325 calibration standard).
 */
@ExtendWith(MockitoExtension.class)
class DataGovernanceServiceTest {

    @Mock RetentionRepository retentionRepo;
    @Mock DisposalJobRepository disposalRepo;
    @Mock NdrRepository ndrRepo;
    @Mock CatalogEntryRepository catalogRepo;
    @Mock CatalogLineageRepository lineageRepo;
    @Mock QualityRuleRepository qualityRuleRepo;
    @Mock QualityRunRepository qualityRunRepo;
    @Mock DataOfficerSupport support;

    @InjectMocks DataGovernanceService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(1L);
        when(qualityRuleRepo.findByOrganisationIdAndId(eq(1L), anyLong()))
            .thenReturn(Optional.of(new QualityRule()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RecordQualityRunRequest run(String band) {
        return new RecordQualityRunRequest(5L, 100L, 40L, new BigDecimal("60"), band, "note", null);
    }

    private QualityRun captureSaved() {
        ArgumentCaptor<QualityRun> captor = ArgumentCaptor.forClass(QualityRun.class);
        verify(qualityRunRepo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void lowercaseRed_isCanonicalisedToUpper_andEscalates() {
        service.recordQualityRun(run("red"));
        // stored value is canonical, so the qRed30 dashboard filter (exact-match "RED") sees it
        assertThat(captureSaved().getBand()).isEqualTo("RED");
        // and the RED escalation the dashboard + automation engine react to fires
        verify(support).emit(eq("QUALITY_RUN_RED"), eq("QUALITY_RULE"), eq(5L), any());
    }

    @Test
    void mixedCaseRed_withWhitespace_stillEscalates() {
        service.recordQualityRun(run("  Red "));
        assertThat(captureSaved().getBand()).isEqualTo("RED");
        verify(support).emit(eq("QUALITY_RUN_RED"), eq("QUALITY_RULE"), eq(5L), any());
    }

    @Test
    void green_isCanonicalised_andDoesNotEscalate() {
        service.recordQualityRun(run("green"));
        assertThat(captureSaved().getBand()).isEqualTo("GREEN");
        verify(support, never()).emit(any(), any(), any(), any());
    }

    @Test
    void nullBand_isTolerated_andDoesNotEscalate() {
        service.recordQualityRun(run(null));
        assertThat(captureSaved().getBand()).isNull();
        verify(support, never()).emit(any(), any(), any(), any());
    }
}
