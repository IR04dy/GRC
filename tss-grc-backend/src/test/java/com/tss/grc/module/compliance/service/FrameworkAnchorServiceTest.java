package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.entity.PolicyFrameworkMapping;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.compliance.repository.PolicyFrameworkMappingRepository;
import com.tss.grc.module.compliance.repository.PolicyRepository;
import com.tss.grc.module.privacy.repository.DpiaRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CH-15 — {@link FrameworkAnchorService#coverage()} must scope policy→framework
 * mappings to the current tenant. The {@code policy_framework_mappings} table has
 * no organisation_id, so the count would otherwise tally every other tenant's
 * mappings for the same framework. Scope is derived from the parent policy's org.
 */
@ExtendWith(MockitoExtension.class)
class FrameworkAnchorServiceTest {

    private static final Long ORG = 1L;
    private static final Long FW_ID = 10L;

    @Mock private FrameworkRepository frameworkRepo;
    @Mock private PolicyRepository policyRepo;
    @Mock private PolicyFrameworkMappingRepository mappingRepo;
    @Mock private AuditFindingRepository findingRepo;
    @Mock private ControlRepository controlRepo;
    @Mock private DpiaRepository dpiaRepo;
    @Mock private KriDefinitionRepository kriRepo;
    @Mock private RiskRepository riskRepo;

    @InjectMocks private FrameworkAnchorService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Framework framework(long id) {
        Framework f = new Framework();
        f.setId(id);
        f.setCode("ECC");
        f.setNameEn("NCA ECC");
        f.setFamily("SECURITY");
        return f;
    }

    private static PolicyFrameworkMapping mapping(String satisfaction) {
        PolicyFrameworkMapping m = new PolicyFrameworkMapping();
        m.setFrameworkId(FW_ID);
        m.setSatisfaction(satisfaction);
        return m;
    }

    @Test
    void coverage_scopesMappingsToCurrentTenantViaParentPolicy() {
        when(frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc())
                .thenReturn(List.of(framework(FW_ID)));
        // Only THIS tenant's three mappings come back from the org-scoped query.
        when(mappingRepo.findByFrameworkIdAndPolicyOrg(FW_ID, ORG))
                .thenReturn(List.of(mapping("FULL"), mapping("PARTIAL"), mapping("FULL")));

        List<Map<String, Object>> out = service.coverage();

        assertThat(out).hasSize(1);
        Map<String, Object> r = out.get(0);
        assertThat(r.get("policyCount")).isEqualTo(3);
        assertThat(r.get("full")).isEqualTo(2L);
        assertThat(r.get("partial")).isEqualTo(1L);
        assertThat(r.get("outOfScope")).isEqualTo(0L);
        assertThat(r.get("notApplicable")).isEqualTo(0L);

        // The leaky, org-blind lookup must never be used for the tally.
        verify(mappingRepo).findByFrameworkIdAndPolicyOrg(FW_ID, ORG);
        verify(mappingRepo, never()).findByFrameworkId(anyLong());
    }

    @Test
    void coverage_threadsTheActiveTenantIntoTheLookup() {
        TenantContext.setCurrentTenant(7L);
        when(frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc())
                .thenReturn(List.of(framework(FW_ID)));
        when(mappingRepo.findByFrameworkIdAndPolicyOrg(FW_ID, 7L))
                .thenReturn(List.of(mapping("FULL")));

        service.coverage();

        // org=7 (not a hardcoded value) is what reaches the repository.
        verify(mappingRepo).findByFrameworkIdAndPolicyOrg(FW_ID, 7L);
    }
}
