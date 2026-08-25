package com.tss.grc.module.opres.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.opres.dto.OpResDtos.AssessmentResponse;
import com.tss.grc.module.opres.dto.OpResDtos.CreateAssessmentRequest;
import com.tss.grc.module.opres.entity.OpResAssessment;
import com.tss.grc.module.opres.entity.OpResPillar;
import com.tss.grc.module.opres.repository.OpResAssessmentCellRepository;
import com.tss.grc.module.opres.repository.OpResAssessmentRepository;
import com.tss.grc.module.opres.repository.OpResPillarRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F2-363 — the SAMA Op-Res self-assessment {@code completeness} label.
 *
 * <p>{@code createAssessment} used to hard-code {@code "0/5"} while
 * {@code upsertCell} recomputes the denominator from
 * {@link OpResPillarRepository#findActiveForOrg} — shared catalog rows
 * plus tenant extensions, less deactivated ones — a count that is
 * genuinely variable per tenant. These legs pin the create-path
 * denominator to that same active-pillar count, so a hard-coded number
 * (5 or any other) fails.
 */
@ExtendWith(MockitoExtension.class)
class OpResServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private OpResPillarRepository pillarRepo;
    @Mock private OpResAssessmentRepository assessmentRepo;
    @Mock private OpResAssessmentCellRepository cellRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private OpResService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static List<OpResPillar> pillars(int n) {
        return IntStream.range(0, n).mapToObj(i -> new OpResPillar()).toList();
    }

    private static CreateAssessmentRequest req() {
        return CreateAssessmentRequest.builder()
                .fiscalYear(2026)
                .titleEn("Op-Res self-assessment")
                .titleAr("التقييم الذاتي للمرونة التشغيلية")
                .build();
    }

    private AssessmentResponse create() {
        when(assessmentRepo.save(any())).thenAnswer(inv -> {
            OpResAssessment a = inv.getArgument(0);
            a.setId(77L);
            return a;
        });
        return service.createAssessment(req());
    }

    @Test
    void createAssessment_completenessDenominatorIsTheActivePillarCount_notHardcodedFive() {
        // A tenant with SIX active pillars (a tenant-specific extension on top
        // of the five-pillar SAMA catalog). The pre-fix "0/5" understated it.
        when(pillarRepo.findActiveForOrg(eq(ORG_ID))).thenReturn(pillars(6));

        assertThat(create().getCompleteness()).isEqualTo("0/6");
    }

    @Test
    void createAssessment_completenessTracksAThreePillarTenant() {
        // And a tenant that has DEACTIVATED two pillars — the denominator
        // follows down as well, so a hard-coded constant of any value fails.
        when(pillarRepo.findActiveForOrg(eq(ORG_ID))).thenReturn(pillars(3));

        assertThat(create().getCompleteness()).isEqualTo("0/3");
    }

    @Test
    void createAssessment_numeratorIsZeroBeforeAnyCellIsScored() {
        when(pillarRepo.findActiveForOrg(eq(ORG_ID))).thenReturn(pillars(5));

        assertThat(create().getCompleteness()).startsWith("0/");
    }
}
