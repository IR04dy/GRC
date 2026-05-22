package com.tss.grc.module.regchange.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.regchange.dto.RegulatoryChangeDto;
import com.tss.grc.module.regchange.entity.RegulatoryChange;
import com.tss.grc.module.regchange.repository.RegulatoryChangeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regulatory change state machine:
 *   NEW                → ASSESSED, DEPRIORITISED
 *   ASSESSED           → IMPACT_ANALYSED, DEPRIORITISED
 *   IMPACT_ANALYSED    → IN_IMPLEMENTATION, DEPRIORITISED
 *   IN_IMPLEMENTATION  → IMPLEMENTED, DEPRIORITISED
 *   IMPLEMENTED        → CLOSED
 *   CLOSED, DEPRIORITISED → terminal
 */
@ExtendWith(MockitoExtension.class)
class RegulatoryChangeServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "RC-2026-001";

    @Mock private RegulatoryChangeRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private RegulatoryChangeService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static RegulatoryChange change(String status) {
        RegulatoryChange c = new RegulatoryChange();
        c.setExternalId(EXT_ID);
        c.setOrganisationId(ORG_ID);
        c.setRegulator("NCA");
        c.setTitleEn("ECC update"); c.setTitleAr("تحديث ECC");
        c.setStatus(status);
        return c;
    }

    private static RegulatoryChangeDto.TransitionRequest tx(String status) {
        RegulatoryChangeDto.TransitionRequest t = new RegulatoryChangeDto.TransitionRequest();
        t.setStatus(status);
        return t;
    }

    @Test
    void transition_NEW_to_ASSESSED_records_assessor_and_applicability() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(change("NEW")));
        when(repo.save(any(RegulatoryChange.class))).thenAnswer(inv -> inv.getArgument(0));

        RegulatoryChangeDto.TransitionRequest t = tx("ASSESSED");
        t.setAssessedBy("complianceofficer");
        t.setApplicability("APPLICABLE");
        RegulatoryChangeDto out = service.transition(EXT_ID, t);

        assertThat(out.getStatus()).isEqualTo("ASSESSED");
        assertThat(out.getAssessedBy()).isEqualTo("complianceofficer");
        assertThat(out.getAssessedAt()).isNotNull();
        assertThat(out.getApplicability()).isEqualTo("APPLICABLE");
    }

    @Test
    void transition_full_chain_to_CLOSED() {
        // NEW → ASSESSED → IMPACT_ANALYSED → IN_IMPLEMENTATION → IMPLEMENTED → CLOSED
        for (String[] hop : new String[][] {
            {"NEW", "ASSESSED"}, {"ASSESSED", "IMPACT_ANALYSED"},
            {"IMPACT_ANALYSED", "IN_IMPLEMENTATION"}, {"IN_IMPLEMENTATION", "IMPLEMENTED"},
            {"IMPLEMENTED", "CLOSED"}
        }) {
            when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(change(hop[0])));
            when(repo.save(any(RegulatoryChange.class))).thenAnswer(inv -> inv.getArgument(0));
            RegulatoryChangeDto out = service.transition(EXT_ID, tx(hop[1]));
            assertThat(out.getStatus()).isEqualTo(hop[1]);
        }
    }

    @Test
    void transition_NEW_to_DEPRIORITISED_records_reason() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(change("NEW")));
        when(repo.save(any(RegulatoryChange.class))).thenAnswer(inv -> inv.getArgument(0));

        RegulatoryChangeDto.TransitionRequest t = tx("DEPRIORITISED");
        t.setDeprioritisationReasonEn("Not applicable to our scope");
        RegulatoryChangeDto out = service.transition(EXT_ID, t);

        assertThat(out.getStatus()).isEqualTo("DEPRIORITISED");
        assertThat(out.getDeprioritisationReasonEn()).contains("Not applicable");
    }

    @Test
    void transition_NEW_to_IMPLEMENTED_skips_assessment_rejected() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(change("NEW")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("IMPLEMENTED")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to")
            .containsExactly("NEW", "IMPLEMENTED");
    }

    @Test
    void transition_CLOSED_is_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(change("CLOSED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("NEW")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "RC-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("RC-X", tx("ASSESSED")))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
