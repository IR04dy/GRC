package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.RegulatoryChange;
import com.tss.grc.module.compliance.repository.RegulatoryChangeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-233 — {@code module.compliance.RegulatoryChangeService}, which had NO test of its own: the only
 * {@code RegulatoryChangeServiceTest} in the tree covers the identically-named class in
 * {@code module.regchange} (F2-222 measured 15 entity simple names declared twice, and this is the
 * service-level version of the same hazard).
 *
 * <p>The create named the state, so an obligation could be filed already CLOSED with a chosen
 * {@code closedAt}, having passed through none of NEW → APPLICABILITY_ASSESSED → IMPACT_ASSESSED →
 * CONTROLS_UPDATED → CLOSED — each of which demands something on the way through (F2-220).
 */
@ExtendWith(MockitoExtension.class)
class RegulatoryChangeCreateInitialStateTest {

    @Mock private RegulatoryChangeRepository repo;
    @InjectMocks private RegulatoryChangeService service;

    @BeforeEach void tenant() { TenantContext.setCurrentTenant(1L); }
    @AfterEach  void clear()  { TenantContext.clear(); }

    private RegulatoryChange change() {
        RegulatoryChange c = new RegulatoryChange();
        c.setTitleEn("SAMA circular 2026/14");
        c.setTitleAr("تعميم ساما ٢٠٢٦/١٤");
        c.setSourceAuthority("SAMA");
        return c;
    }

    @Test
    void create_refusesAStatusOtherThanTheMachinesRoot() {
        RegulatoryChange c = change();
        c.setStatus("CLOSED");

        assertThatThrownBy(() -> service.create(c))
            .as("a change filed CLOSED was never assessed for applicability or impact, and no control "
                + "was recorded as updated — the whole point of the register")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("assess-applicability");

        verify(repo, never()).save(any());
    }

    @Test
    void create_clearsTheClosureStamp() {
        RegulatoryChange c = change();
        c.setClosedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        when(repo.save(any(RegulatoryChange.class))).thenAnswer(i -> i.getArgument(0));

        service.create(c);

        ArgumentCaptor<RegulatoryChange> saved = ArgumentCaptor.forClass(RegulatoryChange.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getClosedAt())
            .as("closedAt is close()'s and assessApplicability()'s NOT_APPLICABLE branch's to stamp")
            .isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo("NEW");
    }

    @Test
    void create_keepsTheAssessorsImpactFieldsAndTheDefaultStatus() {
        RegulatoryChange c = change();
        c.setImpactLevel("HIGH");
        c.setImpactSummary("Affects three outsourcing contracts");
        when(repo.save(any(RegulatoryChange.class))).thenAnswer(i -> i.getArgument(0));

        service.create(c);

        ArgumentCaptor<RegulatoryChange> saved = ArgumentCaptor.forClass(RegulatoryChange.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getImpactLevel())
            .as("assessImpact() takes impactLevel from its own request body, so it is the assessor's "
                + "value in both places and only the STATUS is the machine's")
            .isEqualTo("HIGH");
        assertThat(saved.getValue().getImpactSummary()).isEqualTo("Affects three outsourcing contracts");
        assertThat(saved.getValue().getStatus())
            .as("the entity initialiser already says NEW, which is the only payload a real client sends")
            .isEqualTo("NEW");
    }
}
