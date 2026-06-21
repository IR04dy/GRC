package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceControl;
import com.tss.grc.module.evidence.entity.EvidenceFramework;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.entity.EvidenceSubControl;
import com.tss.grc.module.evidence.repository.ConnectorDefinitionRepository;
import com.tss.grc.module.evidence.repository.EvidenceControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceFrameworkRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceSubControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PERF-NQ-003 — matrix() must batch-load the control/sub-control/item graph in a
 * fixed number of queries and group in memory, never issuing a query per
 * framework / control / sub-control.
 */
@ExtendWith(MockitoExtension.class)
class EvidenceMatrixServiceTest {

    private static final Long ORG = 1L;

    @Mock EvidenceFrameworkRepository frameworkRepo;
    @Mock EvidenceControlRepository controlRepo;
    @Mock EvidenceSubControlRepository subControlRepo;
    @Mock EvidenceItemRepository itemRepo;
    @Mock EvidenceVersionRepository versionRepo;
    @Mock ConnectorDefinitionRepository connectorRepo;

    private EvidenceMatrixService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new EvidenceMatrixService(frameworkRepo, controlRepo, subControlRepo, itemRepo, versionRepo, connectorRepo);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test
    void matrix_batchLoadsGraph_noPerNodeQueries() {
        when(frameworkRepo.findAll()).thenReturn(List.of(fw(1L, "NCA-ECC")));
        when(controlRepo.findByFrameworkIdInAndIsActiveTrue(List.of(1L))).thenReturn(List.of(ctrl(10L, 1L, "C-1")));
        when(subControlRepo.findByControlIdInAndIsActiveTrue(List.of(10L)))
            .thenReturn(List.of(sub(100L, 10L, "S-1"), sub(101L, 10L, "S-2")));
        when(itemRepo.findByOrganisationIdAndSubControlIdIn(eq(ORG), any()))
            .thenReturn(List.of(
                item(1000L, 100L, "MANUAL",    LocalDate.now().plusDays(90)),
                item(1001L, 100L, "AUTOMATED", LocalDate.now().plusDays(10))));

        List<Map<String, Object>> out = service.matrix();

        assertThat(out).hasSize(1);
        Map<String, Object> fwBlock = out.get(0);
        assertThat(fwBlock.get("evidenceCount")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> controls = (List<Map<String, Object>>) fwBlock.get("controls");
        assertThat(controls).hasSize(1);
        assertThat(controls.get(0).get("evidenceCount")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs = (List<Map<String, Object>>) controls.get(0).get("subControls");
        assertThat(subs).hasSize(2);
        assertThat(subs.get(0).get("evidenceCount")).isEqualTo(2); // S-1 has both items
        assertThat(subs.get(1).get("evidenceCount")).isEqualTo(0); // S-2 has none
        assertThat(subs.get(0).get("freshness")).isEqualTo("GREEN");

        @SuppressWarnings("unchecked")
        Map<String, Long> mix = (Map<String, Long>) subs.get(0).get("methodMix");
        assertThat(mix).containsEntry("MANUAL", 1L).containsEntry("AUTOMATED", 1L);

        // The fix: ONE batch query for items; the per-sub-control and per-control/
        // per-framework finders are never used.
        verify(itemRepo, times(1)).findByOrganisationIdAndSubControlIdIn(eq(ORG), any());
        verify(itemRepo, never()).findBySubControl(anyLong(), anyLong());
        verify(controlRepo, never()).findByFrameworkIdAndIsActiveTrue(anyLong());
        verify(subControlRepo, never()).findByControlIdAndIsActiveTrue(anyLong());
    }

    // ── fixtures ─────────────────────────────────────────────────────
    private static EvidenceFramework fw(long id, String code) {
        EvidenceFramework f = new EvidenceFramework();
        f.setId(id); f.setCode(code); f.setNameEn(code); f.setIsActive(true);
        return f;
    }
    private static EvidenceControl ctrl(long id, long fwId, String code) {
        EvidenceControl c = new EvidenceControl();
        c.setId(id); c.setFrameworkId(fwId); c.setControlCode(code); c.setNameEn(code); c.setIsActive(true);
        return c;
    }
    private static EvidenceSubControl sub(long id, long ctrlId, String code) {
        EvidenceSubControl s = new EvidenceSubControl();
        s.setId(id); s.setControlId(ctrlId); s.setSubControlCode(code); s.setNameEn(code); s.setIsActive(true);
        return s;
    }
    private static EvidenceItem item(long id, long subId, String method, LocalDate validTo) {
        EvidenceItem e = new EvidenceItem();
        e.setId(id); e.setOrganisationId(ORG); e.setSubControlId(subId);
        e.setCollectionMethod(method); e.setValidTo(validTo);
        return e;
    }
}
