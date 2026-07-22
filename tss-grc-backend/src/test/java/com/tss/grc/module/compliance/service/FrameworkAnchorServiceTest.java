package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.policy.entity.PolicyDocumentFrameworkMapping;
import com.tss.grc.module.policy.repository.PolicyDocumentFrameworkMappingRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CH-6 — {@link FrameworkAnchorService#coverage()} reads the canonical
 * {@code policy.document_framework_mappings}, org-scoped by the mapping row's own
 * organisation_id (no parent-policy subquery). Tenant scope must come from the
 * active TenantContext, and the satisfaction tallies must reflect only that
 * tenant's mappings for the framework.
 */
@ExtendWith(MockitoExtension.class)
class FrameworkAnchorServiceTest {

    private static final Long ORG = 1L;
    private static final Long FW_ID = 10L;

    @Mock private FrameworkRepository frameworkRepo;
    @Mock private PolicyDocumentRepository policyDocRepo;
    @Mock private PolicyDocumentFrameworkMappingRepository docMappingRepo;
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

    private static PolicyDocumentFrameworkMapping mapping(String satisfaction) {
        PolicyDocumentFrameworkMapping m = new PolicyDocumentFrameworkMapping();
        m.setOrganisationId(ORG);
        m.setDocumentId(100L);
        m.setFrameworkId(FW_ID);
        m.setSatisfaction(satisfaction);
        return m;
    }

    @Test
    void coverage_talliesCanonicalMappingsForTheActiveTenant() {
        when(frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc())
                .thenReturn(List.of(framework(FW_ID)));
        when(docMappingRepo.findByOrganisationIdAndFrameworkId(ORG, FW_ID))
                .thenReturn(List.of(mapping("FULL"), mapping("PARTIAL"), mapping("FULL")));

        List<Map<String, Object>> out = service.coverage();

        assertThat(out).hasSize(1);
        Map<String, Object> r = out.get(0);
        assertThat(r.get("policyCount")).isEqualTo(3);
        assertThat(r.get("full")).isEqualTo(2L);
        assertThat(r.get("partial")).isEqualTo(1L);
        assertThat(r.get("outOfScope")).isEqualTo(0L);
        assertThat(r.get("notApplicable")).isEqualTo(0L);

        // Coverage reads the canonical, org-scoped mapping table.
        verify(docMappingRepo).findByOrganisationIdAndFrameworkId(ORG, FW_ID);
    }

    @Test
    void coverage_scopesToTheActiveTenant() {
        TenantContext.setCurrentTenant(7L);
        when(frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc())
                .thenReturn(List.of(framework(FW_ID)));
        when(docMappingRepo.findByOrganisationIdAndFrameworkId(7L, FW_ID))
                .thenReturn(List.of(mapping("FULL")));

        service.coverage();

        // org=7 (from TenantContext, not hardcoded) is what reaches the repository.
        verify(docMappingRepo).findByOrganisationIdAndFrameworkId(7L, FW_ID);
    }
}
