package com.tss.grc.module.capa.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CAPA state machine:
 *   OPEN                  → IN_PROGRESS, CANCELLED
 *   IN_PROGRESS           → AWAITING_VERIFICATION, CANCELLED
 *   AWAITING_VERIFICATION → CLOSED, IN_PROGRESS (failed verify), CANCELLED
 *   CLOSED, CANCELLED → terminal
 */
@ExtendWith(MockitoExtension.class)
class CapaTaskServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String EXT_ID = "CAPA-2026-001";

    @Mock private CapaTaskRepository repo;
    @InjectMocks private CapaTaskService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static CapaTask task(String status) {
        CapaTask t = new CapaTask();
        t.setExternalId(EXT_ID);
        t.setOrganisationId(ORG_ID);
        t.setOriginType("audit.finding");
        t.setOriginId("FIN-2026-001");
        t.setTitleEn("Fix"); t.setTitleAr("معالجة");
        t.setOwner("riskmanager");
        t.setTargetDate(LocalDate.now().plusDays(30));
        t.setCapaType("CORRECTIVE");
        t.setStatus(status);
        return t;
    }

    private static CapaTaskDto.TransitionRequest tx(String status) {
        CapaTaskDto.TransitionRequest r = new CapaTaskDto.TransitionRequest();
        r.setStatus(status);
        return r;
    }

    @Test
    void transition_OPEN_to_IN_PROGRESS_records_started() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(task("OPEN")));
        when(repo.save(any(CapaTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CapaTaskDto out = service.transition(EXT_ID, tx("IN_PROGRESS"));
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(out.getStartedAt()).isNotNull();
    }

    @Test
    void transition_IN_PROGRESS_to_AWAITING_VERIFICATION_records_submission() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(task("IN_PROGRESS")));
        when(repo.save(any(CapaTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CapaTaskDto out = service.transition(EXT_ID, tx("AWAITING_VERIFICATION"));
        assertThat(out.getStatus()).isEqualTo("AWAITING_VERIFICATION");
        assertThat(out.getSubmittedForVerificationAt()).isNotNull();
    }

    @Test
    void transition_AWAITING_VERIFICATION_back_to_IN_PROGRESS_for_failed_verify() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(task("AWAITING_VERIFICATION")));
        when(repo.save(any(CapaTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CapaTaskDto out = service.transition(EXT_ID, tx("IN_PROGRESS"));
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void transition_AWAITING_VERIFICATION_to_CLOSED_records_verifier_and_closure() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(task("AWAITING_VERIFICATION")));
        when(repo.save(any(CapaTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CapaTaskDto.TransitionRequest t = tx("CLOSED");
        t.setVerifiedBy("auditor");
        CapaTaskDto out = service.transition(EXT_ID, t);

        assertThat(out.getStatus()).isEqualTo("CLOSED");
        assertThat(out.getVerifiedBy()).isEqualTo("auditor");
        assertThat(out.getClosedAt()).isNotNull();
    }

    @Test
    void transition_OPEN_to_CLOSED_skips_remediation_rejected() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(task("OPEN")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("CLOSED")))
            .isInstanceOf(IllegalTransitionException.class)
            .extracting("from", "to")
            .containsExactly("OPEN", "CLOSED");
    }

    @Test
    void transition_CLOSED_is_terminal() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, EXT_ID)).thenReturn(Optional.of(task("CLOSED")));

        assertThatThrownBy(() -> service.transition(EXT_ID, tx("OPEN")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_unknownExternalId_throwsEntityNotFoundException() {
        when(repo.findByOrganisationIdAndExternalId(ORG_ID, "CAPA-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition("CAPA-X", tx("IN_PROGRESS")))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
