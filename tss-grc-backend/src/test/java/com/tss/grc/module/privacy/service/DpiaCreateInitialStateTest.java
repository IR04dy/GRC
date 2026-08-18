package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.entity.Dpia;
import com.tss.grc.module.privacy.repository.DpiaMitigationRepository;
import com.tss.grc.module.privacy.repository.DpiaQuestionRepository;
import com.tss.grc.module.privacy.repository.DpiaRepository;
import com.tss.grc.module.privacy.repository.DpiaReviewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-233 — {@code POST /api/v1/privacy/dpias} binds the {@code Dpia} ENTITY, so before this the create
 * accepted the transition columns and a caller could file a PDPL Article 25 impact assessment already
 * APPROVED or REJECTED. Both routes carry {@code PRIVACY:WRITE}, so the state machine was the only
 * control and the create sat beside it without one (F2-219/F2-220's shape).
 *
 * <p>The refusal is deliberately {@code require}, not {@code coerce}: {@code dpiaApi.create} is dark
 * (recorded as a HOLE in {@code apiClientMethodReachability}), so no working request sends a status and
 * F2-182's test says nothing that succeeds today can start failing.
 */
@ExtendWith(MockitoExtension.class)
class DpiaCreateInitialStateTest {

    @Mock  private DpiaRepository           dpiaRepo;
    @Mock  private DpiaQuestionRepository   questionRepo;
    @Mock  private DpiaMitigationRepository mitigationRepo;
    @Mock  private DpiaReviewRepository     reviewRepo;
    @Mock  private AuditLogService          audit;
    @Mock  private ApplicationEventPublisher publisher;

    @InjectMocks private DpiaService service;

    @BeforeEach void tenant() { TenantContext.setCurrentTenant(1L); }
    @AfterEach  void clear()  { TenantContext.clear(); }

    private Dpia submitted() {
        Dpia d = new Dpia();
        d.setTitleEn("Payroll analytics");
        d.setTitleAr("تحليلات الرواتب");
        return d;
    }

    @Test
    void create_refusesAStatusOtherThanTheMachinesRoot() {
        Dpia req = submitted();
        req.setStatus("APPROVED");

        assertThatThrownBy(() -> service.create(req))
            .as("a DPIA created APPROVED skips DRAFT -> IN_REVIEW -> APPROVED entirely, and /approve "
                + "carries the same permission as the create, so the machine is the only control")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submit-for-review");

        verify(dpiaRepo, never()).save(any());
    }

    @Test
    void create_clearsEveryColumnTheRejectedStateProduces() {
        Dpia req = submitted();
        req.setRejectedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        req.setRejectionReason("Residual risk unacceptable");
        when(dpiaRepo.save(any(Dpia.class))).thenAnswer(i -> i.getArgument(0));

        service.create(req);

        ArgumentCaptor<Dpia> saved = ArgumentCaptor.forClass(Dpia.class);
        verify(dpiaRepo).save(saved.capture());
        assertThat(saved.getValue().getRejectedAt())
            .as("rejectedAt is reject()'s to stamp — a brand-new DPIA cannot already carry one")
            .isNull();
        assertThat(saved.getValue().getRejectionReason())
            .as("the reason travels with the stamp; a DRAFT carrying a rejection reason is a record "
                + "of a decision that was never taken")
            .isNull();
    }

    @Test
    void create_acceptsTheAssessorsOwnFieldsAndLandsAtTheMachinesRoot() {
        Dpia req = submitted();
        req.setStatus("DRAFT");
        req.setNextReviewDate(java.time.LocalDate.of(2027, 6, 1));
        req.setConclusion("Proceed with mitigations");
        when(dpiaRepo.save(any(Dpia.class))).thenAnswer(i -> i.getArgument(0));

        service.create(req);

        ArgumentCaptor<Dpia> saved = ArgumentCaptor.forClass(Dpia.class);
        verify(dpiaRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getValue().getNextReviewDate())
            .as("approve() only DEFAULTS nextReviewDate when absent, so the review cadence is the "
                + "assessor's field — refusing it would be a gate failing on correct code (F2-233)")
            .isEqualTo(java.time.LocalDate.of(2027, 6, 1));
        assertThat(saved.getValue().getConclusion())
            .as("approve() takes the conclusion from its own request body, so it is caller data there "
                + "too and the create must keep accepting it")
            .isEqualTo("Proceed with mitigations");
    }

    @Test
    void create_omittingTheStatusStillWorks() {
        Dpia req = submitted();
        when(dpiaRepo.save(any(Dpia.class))).thenAnswer(i -> i.getArgument(0));

        service.create(req);

        ArgumentCaptor<Dpia> saved = ArgumentCaptor.forClass(Dpia.class);
        verify(dpiaRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus())
            .as("the entity initialiser already says DRAFT; requireInitial must accept that rather "
                + "than refusing the only payload any real caller sends")
            .isEqualTo("DRAFT");
    }
}
