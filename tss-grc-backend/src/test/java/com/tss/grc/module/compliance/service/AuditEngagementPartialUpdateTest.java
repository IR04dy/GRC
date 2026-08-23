package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditEngagement;
import com.tss.grc.module.compliance.repository.AuditEngagementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-294 — the sharpest of the 27 never-firing null guards, and the only one whose reset escaped a
 * state machine.
 *
 * <p>{@code AuditEngagementController} binds the {@code AuditEngagement} ENTITY on POST and PUT with
 * no {@code @Valid}, and the entity declared {@code status = "PLANNED"}. So
 * {@code if (src.getStatus() != null)} inside {@code apply} was true on <b>every</b> request, and a
 * partial body reset an ISO 27001 external audit engagement to PLANNED — measured at 10 of 12 live
 * rows, identical in dev and prod, two of them CLOSED.
 *
 * <p>🔑 <b>{@code advance} is explicitly FORWARD-ONLY</b> — {@code if (to <= from) throw "must
 * advance forward only"} — so the PUT was the only reverse edge in the register, and it carried the
 * same {@code COMPLIANCE:WRITE} permission as {@code advance} itself. There is no privilege
 * escalation here: the object-level machine was the only control and this route sat beside it
 * without one (F2-219's shape), on a table with <b>zero</b> CHECK constraints.
 *
 * <p>⚠ The create's own default was dead too, which is F2-292's finding on a second register:
 * {@code if (e.getStatus() == null) e.setStatus("PLANNED")} could never fire, because {@code apply}
 * copies the request's status and the request's initialiser made it non-null. So the create also
 * ACCEPTED a caller-chosen status and could land an engagement anywhere in the ladder, skipping the
 * route that owns the move. Both halves are closed here, and both are asserted — a structural gate
 * can see the initialiser is gone and only these legs can see what the code now does.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditEngagementPartialUpdateTest {

    private static final Long ORG_ID = 1L;

    @Mock private AuditEngagementRepository repo;
    @InjectMocks private AuditEngagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(AuditEngagement.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** A stored engagement whose every swept field differs from the value the initialiser held. */
    private AuditEngagement stored() {
        AuditEngagement e = new AuditEngagement();
        e.setId(7L);
        e.setOrganisationId(ORG_ID);
        e.setEngagementCode("AUD-2026-004");
        e.setTitleEn("ISO 27001 recertification");
        e.setTitleAr("إعادة اعتماد أيزو 27001");
        e.setAuditType("EXTERNAL");
        e.setStatus("CLOSED");
        e.setFindingsCount(9);
        return e;
    }

    @Test
    void aPartialBodyThatChangesOnlyTheTitleLeavesTheClosedStatusAlone() {
        when(repo.findById(7L)).thenReturn(Optional.of(stored()));

        AuditEngagement body = new AuditEngagement();
        body.setTitleEn("ISO 27001 recertification (rev 2)");

        AuditEngagement saved = service.update(7L, body);

        assertThat(saved.getStatus())
            .as("the reset walked a CLOSED engagement back to PLANNED through a route with no "
                + "machine, and `advance` refuses every backwards move")
            .isEqualTo("CLOSED");
        assertThat(saved.getFindingsCount())
            .as("`AuditEngagementRequest` does not declare findingsCount at all, so the form could "
                + "never send it and the guard fired with the initialiser's 0 on every request")
            .isEqualTo(9);
        assertThat(saved.getTitleEn()).isEqualTo("ISO 27001 recertification (rev 2)");
    }

    @Test
    void aPartialBodyCannotUndoTheForwardOnlyMachineFromEitherEnd() {
        when(repo.findById(7L)).thenReturn(Optional.of(stored()));

        // the machine's own refusal, stated here so the two halves are visible together: the PUT
        // must not be able to do what `advance` is written to forbid.
        assertThatThrownBy(() -> service.advance(7L, "PLANNED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must advance forward only");

        // ⚠⚠ F2-296 — the comment above states the general rule and this body sends NO STATUS AT
        // ALL, so the case it claims to cover was never exercised. F2-294 removed the initialiser,
        // which fixed the OMITTED half; `apply` kept copying a STATED one, so the PUT could still
        // perform the exact move `advance` refuses. F2-293's shape — the one test that could have
        // caught it picked the case where the principle already held — inside a single method whose
        // comment asserts the whole rule. The stated-status leg is below.
        AuditEngagement body = new AuditEngagement();
        body.setScopeSummary("scope narrowed to the KSA estate");
        assertThat(service.update(7L, body).getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void aPutThatStatesAnEarlierStatusIsRefusedAndNamesAdvance() {
        when(repo.findById(7L)).thenReturn(Optional.of(stored()));

        AuditEngagement body = new AuditEngagement();
        body.setTitleEn("ISO 27001 recertification");
        body.setStatus("PLANNED");

        assertThatThrownBy(() -> service.update(7L, body))
            .as("before F2-296 this answered 200 and walked a CLOSED IIA engagement back to "
                + "PLANNED — the register's only reverse edge, on a route carrying the same "
                + "COMPLIANCE:WRITE as the forward-only /advance it bypassed")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status:")
            .hasMessageContaining("is at CLOSED")
            .hasMessageContaining("cannot be moved to PLANNED by an update")
            .hasMessageContaining("/advance");
    }

    @Test
    void theRefusalNamesOnlyTheTargetsTheMachineWouldActuallyAccept() {
        when(repo.findById(7L)).thenReturn(Optional.of(stored()));

        AuditEngagement body = new AuditEngagement();
        body.setStatus("FIELDWORK");

        // CLOSED is index 6 of 8, so `advance` accepts FOLLOW_UP and nothing else. A diagnostic
        // that listed the whole vocabulary would promise moves the machine refuses (F2-196: never
        // restate a vocabulary a machine already declares) — the allowed set is derived from the
        // ladder, so it cannot drift from what `advance` will do.
        assertThatThrownBy(() -> service.update(7L, body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Allowed from CLOSED: [FOLLOW_UP]");
    }

    @Test
    void aCaseVariantEchoIsToleratedBecauseTheColumnCollatesCaseInsensitively() {
        when(repo.findById(7L)).thenReturn(Optional.of(stored()));

        AuditEngagement body = new AuditEngagement();
        body.setStatus("closed");

        assertThat(service.update(7L, body).getStatus())
            .as("`compliance.audit_engagements.status` is Latin1_General_100_CI_AS_SC_UTF8 and had "
                + "no CHECK before V394, so 'closed' was storable and a bare equals would refuse an "
                + "echo differing only in case (F2-179). The stored value is left CANONICAL.")
            .isEqualTo("CLOSED");
    }

    @Test
    void anEchoOfTheStoredStatusIsStillAccepted() {
        when(repo.findById(7L)).thenReturn(Optional.of(stored()));

        // The register's edit form seeds from `engagementToReq`, which names every field including
        // status, so it echoes the stored value back on every save. Refusing the field outright
        // would break the screen that works today — the one failure a fix must never have (F2-214).
        AuditEngagement body = new AuditEngagement();
        body.setStatus("CLOSED");
        body.setTitleEn("still closed");

        assertThat(service.update(7L, body).getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void aBareCreateStillGetsBothDefaultsTheEntityUsedToCarry() {
        AuditEngagement body = new AuditEngagement();
        body.setTitleEn("New external audit");
        body.setAuditType("EXTERNAL");

        AuditEngagement saved = service.create(body);

        assertThat(saved.getStatus())
            .as("removing the initialiser must not leave a NOT NULL column null on create — that is "
                + "the one way this fix goes wrong and nothing else in the tree would notice")
            .isEqualTo("PLANNED");
        assertThat(saved.getFindingsCount()).isZero();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    void aCreateMayNotName_AStatusFurtherAlongTheLadder() {
        AuditEngagement body = new AuditEngagement();
        body.setTitleEn("Filed already closed");
        body.setAuditType("EXTERNAL");
        body.setStatus("CLOSED");

        assertThatThrownBy(() -> service.create(body))
            .as("`apply` copied a caller-chosen status onto the new row, so a create could land "
                + "anywhere in the ladder and skip the route that owns the move")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("advance");
    }

    @Test
    void aCreateThatEchoesTheInitialStatusIsAccepted() {
        // ENGAGEMENT_EMPTY sends `status: 'PLANNED'`, so refusing a present-but-initial value would
        // break the new-engagement form.
        AuditEngagement body = new AuditEngagement();
        body.setTitleEn("New external audit");
        body.setAuditType("EXTERNAL");
        body.setStatus(AuditEngagementService.INITIAL_STATUS);

        assertThat(service.create(body).getStatus()).isEqualTo("PLANNED");
    }

    @Test
    void theInitialStatusIsDerivedFromTheLadderRatherThanTyped() {
        assertThat(AuditEngagementService.INITIAL_STATUS)
            .as("two literals for one fact is how the create's default and the machine's root drift "
                + "apart (F2-217); this one is STATUS_ORDER.get(0)")
            .isEqualTo("PLANNED");
    }
}
