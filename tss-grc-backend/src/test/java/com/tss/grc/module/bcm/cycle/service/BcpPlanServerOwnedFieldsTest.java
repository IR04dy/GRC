package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpSectionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpVersionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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
import static org.mockito.Mockito.when;

/**
 * F2-234 — the two {@link BcpPlanService} fields F2-220 left behind when it closed
 * {@code status}/{@code approvedBy}/{@code approvedAt}/{@code publishedAt} on the same create.
 *
 * <p>🔑 F2-215's headline: <b>a reader who finds four fields scrubbed does not ask what the fifth
 * is.</b> {@code currentVersion} sat one line below them as a DEFAULT, so a caller could file a
 * brand-new continuity plan claiming to be at v7 with no {@code BcpVersion} row behind it — and
 * {@code snapshot()} derives the next number from the versions table, so the first approval would
 * silently reduce 7 → 1. The version on a BCP is what a recovery team cites.
 *
 * <p>These two behaviours were verified by live probe (22/22) and are asserted here so the mutation
 * harness has something to kill: a fix nothing asserts is a fix the next reader can delete
 * (F2-219 — a surviving mutant a gate legitimately cannot see is an unasserted behaviour).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcpPlanServerOwnedFieldsTest {

    private static final Long ORG_ID = 1L;

    @Mock private BcpPlanRepository                plans;
    @Mock private BcpSectionRepository             sections;
    @Mock private BcpVersionRepository             versions;
    @Mock private BcmBiaAssessmentRepository       bias;
    @Mock private BcpPlanAcknowledgementRepository acks;
    @Mock private AuditLogService                  audit;
    @Mock private ApplicationEventPublisher        eventPublisher;

    @InjectMocks private BcpPlanService service;

    @BeforeEach void tenant() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(plans.save(any(BcpPlan.class))).thenAnswer(i -> {
            BcpPlan p = i.getArgument(0);
            if (p.getId() == null) p.setId(55L);
            return p;
        });
        when(sections.save(any(BcpSection.class))).thenAnswer(i -> {
            BcpSection s = i.getArgument(0);
            if (s.getId() == null) s.setId(77L);
            return s;
        });
        when(sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(ORG_ID, 55L))
            .thenReturn(List.of());
        when(sections.findByOrganisationIdAndPlanIdAndSectionKey(ORG_ID, 55L, "rt-1"))
            .thenReturn(Optional.empty());
        // nextCode() derives the plan code from a page-1 total, so the create needs this stub.
        when(plans.findByOrganisationIdOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(ORG_ID),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.PageRequest.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
    }
    @AfterEach void clear() { TenantContext.clear(); }

    private static BcpPlan draft() {
        BcpPlan p = new BcpPlan();
        p.setTitleEn("Group BCP");
        p.setTitleAr("خطة استمرارية المجموعة");
        p.setPlanType("BCP");
        return p;
    }

    @Test
    void create_forcesTheVersionCounterToOne() {
        BcpPlan body = draft();
        body.setCurrentVersion(7);

        BcpPlan saved = service.create(body);

        assertThat(saved.getCurrentVersion())
            .as("snapshot() owns this counter — it derives the next number from the versions "
                + "table, so a caller-chosen 7 would be silently reduced to 1 by the first "
                + "approval, having meanwhile claimed six approved versions that never existed")
            .isEqualTo(1);
    }

    @Test
    void addSection_clearsTheEditStampsUpdateSectionOwns() {
        BcpPlan plan = draft();
        plan.setId(55L);
        plan.setStatus("DRAFT");
        plan.setPlanCode("BCP-001");
        when(plans.findByIdAndOrganisationId(55L, ORG_ID)).thenReturn(Optional.of(plan));

        BcpSection body = new BcpSection();
        body.setSectionKey("rt-1");
        body.setSectionTitleEn("Recovery team");
        body.setLastEditedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        body.setLastEditedBy("Nobody Who Edited This");

        BcpSection saved = service.addSection(55L, body);

        assertThat(saved.getLastEditedAt())
            .as("a section that has just been added has never been edited, so it cannot carry "
                + "updateSection()'s clock")
            .isNull();
        assertThat(saved.getLastEditedBy())
            .as("nor its editor — the CREATE is recorded by the audit row and by createdAt")
            .isNull();
    }
}
