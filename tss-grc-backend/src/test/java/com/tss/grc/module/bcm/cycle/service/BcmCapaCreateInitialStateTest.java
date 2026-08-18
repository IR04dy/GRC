package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmLessonLearnedRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-234 — {@code POST /api/v1/bcm/capas} binds the {@code BcmCorrectiveAction} ENTITY, and the
 * create had {@code if (status == null) setStatus("OPEN")} followed by a MEMBERSHIP test, so
 * {@code CLOSED} was accepted.
 *
 * <p>🔑 <b>The identical two lines were fixed by F2-220 in the SAME FILE, eighty lines above, under
 * a comment naming this exact defect.</b> Not a sibling file, not a sibling module — the same file
 * (F2-215's headline at its sharpest).
 *
 * <p>What the bypass skipped is an EVIDENCE REQUIREMENT, not merely a ladder:
 * {@code transitionCapa} refuses {@code CLOSED} when {@code evidenceId} is null. It also produced a
 * row nothing could move — {@code updateCapa} refuses to edit a CLOSED CAPA and
 * {@code transitionCapa} has {@code case "CLOSED" -> false} — so a {@code BCM:WRITE} holder could
 * manufacture a row only a {@code BCM:APPROVE} holder can touch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmCapaCreateInitialStateTest {

    private static final Long ORG_ID = 1L;

    @Mock private BcmLessonLearnedRepository      lessons;
    @Mock private BcmGapRepository                gaps;
    @Mock private BcmCorrectiveActionRepository   capas;
    @Mock private AuditLogService                 audit;
    @Mock private ApplicationEventPublisher       events;

    @InjectMocks private BcmRemediationService service;

    @BeforeEach void tenant() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(capas.save(any(BcmCorrectiveAction.class))).thenAnswer(i -> {
            BcmCorrectiveAction a = i.getArgument(0);
            if (a.getId() == null) a.setId(99L);
            return a;
        });
    }
    @AfterEach void clear() { TenantContext.clear(); }

    private static BcmCorrectiveAction capa() {
        BcmCorrectiveAction a = new BcmCorrectiveAction();
        a.setTitleEn("Restore the secondary link");
        a.setTitleAr("استعادة الوصلة الثانوية");
        return a;
    }

    @Test
    void createCapa_refusesAStatusOtherThanTheMachinesRoot() {
        BcmCorrectiveAction a = capa();
        a.setStatus("CLOSED");

        assertThatThrownBy(() -> service.createCapa(a))
            .as("a CAPA created CLOSED skips transitionCapa's 'Cannot CLOSE without evidence' "
                + "requirement as well as the whole OPEN..UNDER_REVIEW ladder")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/transition");

        verify(capas, never()).save(any());
    }

    @Test
    void createCapa_clearsTheClosureStamp() {
        BcmCorrectiveAction a = capa();
        a.setClosureDate(LocalDate.of(2001, 1, 1));

        BcmCorrectiveAction saved = service.createCapa(a);

        assertThat(saved.getClosureDate())
            .as("closureDate is transitionCapa()'s stamp on CLOSE and reopenCapa() clears it; "
                + "a CAPA that has never been closed cannot carry one")
            .isNull();
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    /**
     * The narrowing that keeps this from failing on correct code (F2-214): {@code progressPercent}
     * is stamped to 100 on closure, but it is the owner's own progress report the rest of the time
     * — validated 0..100, defaulted to 0. Refusing it would break a working request.
     */
    @Test
    void createCapa_keepsTheOwnersProgressReport() {
        BcmCorrectiveAction a = capa();
        a.setProgressPercent(40);

        BcmCorrectiveAction saved = service.createCapa(a);

        assertThat(saved.getProgressPercent()).isEqualTo(40);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void createCapa_omittingTheStatusStillWorks() {
        BcmCorrectiveAction saved = service.createCapa(capa());

        ArgumentCaptor<BcmCorrectiveAction> c = ArgumentCaptor.forClass(BcmCorrectiveAction.class);
        verify(capas).save(c.capture());
        assertThat(c.getValue().getStatus())
            .as("the only payload any real client sends carries no status at all")
            .isEqualTo("OPEN");
        assertThat(saved.getProgressPercent()).isZero();
    }
}
