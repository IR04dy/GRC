package com.tss.grc.module.sust.climate.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression test for the partial-update "default clobber" on the Climate Risk
 * &amp; Opportunity register, plus the kind-immutability hardening.
 *
 * <p>{@code PUT /api/sustainability/climate-risks/{id}} binds the JPA entity
 * directly. The original defect: {@code kind} carried a field initialiser
 * ({@code = "RISK"}), so a PUT body that OMITTED kind deserialised it to
 * "RISK" (not null), {@code update()}'s {@code if (req.getKind() != null)}
 * guard fired, and editing an OPPORTUNITY row flipped it to RISK — then
 * {@code validateAxes} rejected the (valid-for-OPPORTUNITY) category with a
 * 400 "Category X not valid for kind RISK".
 *
 * <p>Two layers now prevent this: the "RISK" insert default lives in
 * {@code @PrePersist} (so an omitted field stays null), and {@code update()}
 * treats {@code kind} as immutable post-create (so it can never be flipped by
 * a PUT, even an explicit/mismatched one).
 */
@ExtendWith(MockitoExtension.class)
class ClimateRiskOpportunityServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ID = 5L;

    @Mock private ClimateRiskOpportunityRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private ClimateRiskOpportunityService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** A persisted OPPORTUNITY row — id 5 in the original repro. */
    private ClimateRiskOpportunity persistedOpportunity() {
        ClimateRiskOpportunity cur = new ClimateRiskOpportunity();
        cur.setId(ID);
        cur.setOrganisationId(ORG_ID);
        cur.setKind("OPPORTUNITY");
        cur.setCategory("RESOURCE_EFFICIENCY");
        cur.setTitleEn("Solar-powered branch network");
        cur.setTimeHorizon("MEDIUM");
        cur.setLikelihood((short) 3);
        cur.setImpact((short) 3);
        cur.setStatus("IDENTIFIED");
        return cur;
    }

    /** The exact repro body: edits an OPPORTUNITY and OMITS kind. */
    @Test
    void update_omittingKind_doesNotClobberToRisk() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedOpportunity()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateRiskOpportunity req = new ClimateRiskOpportunity();
        req.setCategory("RESOURCE_EFFICIENCY");
        req.setTimeHorizon("MEDIUM");
        req.setTitleEn("Solar-powered branch network (phase 2)");
        req.setLikelihood((short) 4);
        req.setImpact((short) 5);
        // kind intentionally omitted — must stay OPPORTUNITY

        ClimateRiskOpportunity saved = service.update(ID, req);

        assertThat(saved.getKind()).isEqualTo("OPPORTUNITY");           // NOT flipped to RISK
        assertThat(saved.getCategory()).isEqualTo("RESOURCE_EFFICIENCY");
        assertThat(saved.getRiskScore()).isEqualTo((short) 20);         // 4 × 5 recomputed
    }

    /** kind is immutable post-create: an explicit, mismatched kind is ignored. */
    @Test
    void update_withMismatchedKind_isIgnored_andDoesNotThrow() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedOpportunity()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateRiskOpportunity req = new ClimateRiskOpportunity();
        req.setKind("RISK");                      // attempt to flip — must be ignored
        req.setCategory("RESOURCE_EFFICIENCY");   // only valid while kind stays OPPORTUNITY
        req.setLikelihood((short) 2);
        req.setImpact((short) 2);

        assertThatCode(() -> {
            ClimateRiskOpportunity saved = service.update(ID, req);
            assertThat(saved.getKind()).isEqualTo("OPPORTUNITY");
        }).doesNotThrowAnyException();
    }

    /** Ordinary editable fields still apply on a partial update. */
    @Test
    void update_appliesEditableFields() {
        when(repo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(persistedOpportunity()));
        when(repo.save(any(ClimateRiskOpportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateRiskOpportunity req = new ClimateRiskOpportunity();
        req.setTitleEn("Revised opportunity title");
        req.setLikelihood((short) 5);
        req.setImpact((short) 4);

        ClimateRiskOpportunity saved = service.update(ID, req);

        assertThat(saved.getTitleEn()).isEqualTo("Revised opportunity title");
        assertThat(saved.getRiskScore()).isEqualTo((short) 20);
        assertThat(saved.getKind()).isEqualTo("OPPORTUNITY");
    }
}
