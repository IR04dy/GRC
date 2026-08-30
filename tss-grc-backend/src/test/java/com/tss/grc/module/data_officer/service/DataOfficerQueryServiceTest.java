package com.tss.grc.module.data_officer.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.DashboardResponse;
import com.tss.grc.module.data_officer.entity.Entities;
import com.tss.grc.module.data_officer.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-402 — the Data Officer dashboard's "open" counts must exclude DECIDED states. An
 * APPROVED_WITH_CONDITIONS DPIA stamps {@code decidedAt} (PrivacyAssuranceService's DPIA machine),
 * so it is decided, not open — yet it was counted BOTH as open ({@code dpiaOpen}) and as approved
 * this year ({@code dpiaApprovedYtd}), a double-count across two mutually-exclusive buckets. The
 * ethics "open" count ({@code ethicsOpen}) dropped the same token.
 *
 * <p>{@code dashboard()} had no test (F2-248); the query service is a cross-aggregate reader, so the
 * repositories left unstubbed here return Mockito's empty-collection defaults.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataOfficerQueryServiceTest {

    private static final Long ORG = 3L;

    @Mock DataOfficerSupport support;
    @Mock DsrRepository dsrRepo;
    @Mock ConsentRepository consentRepo;
    @Mock DpiaRepository dpiaRepo;
    @Mock BreachRepository breachRepo;
    @Mock PrivacyNoticeRepository noticeRepo;
    @Mock RetentionRepository retentionRepo;
    @Mock DisposalJobRepository disposalRepo;
    @Mock NdrRepository ndrRepo;
    @Mock CatalogEntryRepository catalogRepo;
    @Mock QualityRuleRepository qualityRuleRepo;
    @Mock QualityRunRepository qualityRunRepo;
    @Mock EthicsRepository ethicsRepo;

    @InjectMocks DataOfficerQueryService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Entities.Dpia dpia(String status, LocalDateTime decidedAt) {
        Entities.Dpia d = new Entities.Dpia();
        d.setStatus(status);
        d.setDecidedAt(decidedAt);
        return d;
    }

    private static Entities.EthicsReview ethics(String status) {
        Entities.EthicsReview e = new Entities.EthicsReview();
        e.setStatus(status);
        return e;
    }

    /**
     * F2-402 — an APPROVED_WITH_CONDITIONS DPIA is decided, not open. {@code dpiaOpen} counts only
     * the genuinely in-review DRAFT; {@code dpiaApprovedYtd} counts AWC + APPROVED (unchanged by the
     * fix), which is exactly why pre-fix the one AWC record was double-counted (in both buckets).
     */
    @Test void dashboard_approvedWithConditionsDpia_isDecidedNotOpen() {
        when(dpiaRepo.findByOrganisationIdOrderByCreatedAtDesc(any())).thenReturn(List.of(
            dpia("APPROVED_WITH_CONDITIONS", LocalDateTime.now()),   // decided this year
            dpia("APPROVED", LocalDateTime.now()),                    // decided this year
            dpia("DRAFT", null)));                                    // genuinely open

        DashboardResponse r = service.dashboard();

        assertThat(r.dpiaOpen()).isEqualTo(1);          // pre-fix: 2 (DRAFT + AWC) — discriminating
        assertThat(r.dpiaApprovedYtd()).isEqualTo(2);   // AWC + APPROVED — guard (pre & post)
    }

    /** F2-402 — the ethics twin: APPROVED_WITH_CONDITIONS is decided (stamps decidedAt), so not open;
     *  ESCALATED stays open (an in-flight re-review edge ESCALATED -> COMMITTEE_REVIEW, not a decision). */
    @Test void dashboard_approvedWithConditionsEthics_isDecidedNotOpen() {
        when(ethicsRepo.findByOrganisationIdOrderByCreatedAtDesc(any())).thenReturn(List.of(
            ethics("APPROVED_WITH_CONDITIONS"),   // decided
            ethics("ESCALATED"),                  // in-flight -> open
            ethics("APPROVED")));                 // decided

        DashboardResponse r = service.dashboard();

        assertThat(r.ethicsOpen()).isEqualTo(1);   // pre-fix: 2 (ESCALATED + AWC) — discriminating
    }
}
