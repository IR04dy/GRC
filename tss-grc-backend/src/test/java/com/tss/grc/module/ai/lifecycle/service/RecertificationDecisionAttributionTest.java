package com.tss.grc.module.ai.lifecycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.lifecycle.dto.AiLifecycleDtos.TransitionRecertificationRequest;
import com.tss.grc.module.ai.lifecycle.entity.AiLifecycleEvent;
import com.tss.grc.module.ai.lifecycle.entity.Recertification;
import com.tss.grc.module.ai.lifecycle.repository.AiLifecycleEventRepository;
import com.tss.grc.module.ai.lifecycle.repository.ModelValidationRepository;
import com.tss.grc.module.ai.lifecycle.repository.RecertificationRepository;
import com.tss.grc.module.ai.lifecycle.repository.RetrainingEventRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-256 — an ISO/IEC 42001 model-recertification decision must record WHO decided it.
 *
 * <p>{@code AiLifecycleService} had no test of any kind, which is not the same as a path somebody
 * decided was safe (F2-248). What it was missing: {@code transitionRecertification} stamped
 * {@code decidedAt} from the server's own clock and wrote {@code approverUserId} nowhere — not from
 * the request either, since neither request record declares the field. So the register recorded
 * <i>when</i> a model's certification was granted, renewed, revoked or allowed to lapse and never
 * <i>who</i> made that call.
 *
 * <p>Measured in dev and prod when this was written: 4 of 13 rows carried {@code decided_at},
 * <b>2 of them with no approver at all</b>, and the 5 rows that do carry one were written by
 * {@code seed_real_data.py}. Every decision taken through the API would have joined that bucket.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecertificationDecisionAttributionTest {

    private static final Long ORG = 1L;
    private static final Long ACTOR_ID = 42L;

    @Mock private ModelValidationRepository validationRepo;
    @Mock private RetrainingEventRepository retrainingRepo;
    @Mock private RecertificationRepository recertRepo;
    @Mock private AiLifecycleEventRepository eventRepo;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private AiLifecycleService svc;

    @BeforeEach
    void bindTenant() {
        TenantContext.setCurrentTenant(ORG);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void signIn() {
        GrcUserDetails u = new GrcUserDetails(
            ACTOR_ID, ORG, "cro_user", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_CRO")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private Recertification parked(String status, Long priorApprover) {
        Recertification c = new Recertification();
        c.setId(9L);
        c.setOrganisationId(ORG);
        c.setModelId(3L);
        c.setReference("REC-2026-[REVIEW-TEST]");
        c.setStatus(status);
        c.setApproverUserId(priorApprover);
        when(recertRepo.findByOrganisationIdAndId(ORG, 9L)).thenReturn(Optional.of(c));
        when(recertRepo.save(any(Recertification.class))).thenAnswer(i -> i.getArgument(0));
        when(eventRepo.save(any(AiLifecycleEvent.class))).thenAnswer(i -> i.getArgument(0));
        return c;
    }

    private static TransitionRecertificationRequest to(String status) {
        return new TransitionRecertificationRequest(status, "rationale", "مبرر", null, null, null, null);
    }

    @Test
    @DisplayName("a RECERTIFIED decision records the deciding principal beside the server clock")
    void aDecisionRecordsTheDecidingPrincipal() {
        signIn();
        Recertification c = parked("IN_REVIEW", null);

        svc.transitionRecertification(9L, to("RECERTIFIED"));

        ArgumentCaptor<Recertification> saved = ArgumentCaptor.forClass(Recertification.class);
        verify(recertRepo).save(saved.capture());
        assertThat(saved.getValue().getDecidedAt())
            .as("the server stamps WHEN — that is what makes it the recorder of the act")
            .isNotNull();
        assertThat(saved.getValue().getApproverUserId())
            .as("""
                …so it must also record WHO. Before F2-256 this was null on every decision taken \
                through the API, and a blank decider on a governance row reads as missing data \
                rather than as a defect.""")
            .isEqualTo(ACTOR_ID);
        assertThat(c.getStatus()).isEqualTo("RECERTIFIED");
    }

    @Test
    @DisplayName("REVOKED and EXPIRED are attributed too — the states an assurance reader asks about")
    void aRevocationAndALapseAreAttributedAsWell() {
        for (String[] leg : new String[][]{{"IN_REVIEW", "REVOKED"}, {"DUE", "EXPIRED"}}) {
            SecurityContextHolder.clearContext();
            signIn();
            parked(leg[0], null);

            svc.transitionRecertification(9L, to(leg[1]));

            ArgumentCaptor<Recertification> saved = ArgumentCaptor.forClass(Recertification.class);
            verify(recertRepo, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
            assertThat(saved.getValue().getApproverUserId())
                .as("""
                    %s stamps decidedAt, so it is a recorded decision and needs a decider. Leaving \
                    these two blank would reproduce the defect on a certification withdrawn and one \
                    allowed to lapse — the two an ISO/IEC 42001 reviewer asks about hardest.""",
                    leg[1])
                .isEqualTo(ACTOR_ID);
        }
    }

    @Test
    @DisplayName("IN_REVIEW records neither half — starting a review is not deciding")
    void startingAReviewIsNotADecision() {
        signIn();
        parked("DUE", null);

        svc.transitionRecertification(9L, to("IN_REVIEW"));

        ArgumentCaptor<Recertification> saved = ArgumentCaptor.forClass(Recertification.class);
        verify(recertRepo).save(saved.capture());
        assertThat(saved.getValue().getStartedAt()).isNotNull();
        assertThat(saved.getValue().getDecidedAt())
            .as("no decision has been made yet, so neither half of the pair may be written")
            .isNull();
        assertThat(saved.getValue().getApproverUserId()).isNull();
    }

    @Test
    @DisplayName("a decision overwrites a prior ASSIGNMENT — once decidedAt is stamped it is an attribution")
    void aDecisionOverwritesAPriorAssignment() {
        signIn();
        // Two live DUE rows in dev carry an approver with no decided_at: the seed used the column as
        // "who is due to approve". F2-236's window rule — the same column is an assignment before the
        // act and the attribution after it, so the decision must claim it.
        parked("IN_REVIEW", 7L);

        svc.transitionRecertification(9L, to("RECERTIFIED_WITH_CONDITIONS"));

        ArgumentCaptor<Recertification> saved = ArgumentCaptor.forClass(Recertification.class);
        verify(recertRepo).save(saved.capture());
        assertThat(saved.getValue().getApproverUserId())
            .as("""
                a stale assignment left standing beside a real decidedAt would attribute the \
                decision to whoever was queued for it, which is the forgery this family is about""")
            .isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("with no authenticated principal the decision is refused, not stored unattributed")
    void anUnattributableDecisionIsRefused() {
        parked("IN_REVIEW", null);   // no signIn()

        assertThatThrownBy(() -> svc.transitionRecertification(9L, to("RECERTIFIED")))
            .as("""
                CurrentActor.requireUserId refuses rather than returning null (F2-236). Storing the \
                null would be exactly the pre-F2-256 behaviour, so a fallback here would leave the \
                hazard latent instead of absent (F2-213).""")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("recertification decision");

        verify(recertRepo, org.mockito.Mockito.never()).save(any(Recertification.class));
    }

    @Test
    @DisplayName("F2-257: the timeline row records BOTH halves of the actor pair")
    void theTimelineRowRecordsBothHalvesOfTheActorPair() {
        signIn();
        parked("IN_REVIEW", null);

        svc.transitionRecertification(9L, to("RECERTIFIED"));

        ArgumentCaptor<AiLifecycleEvent> ev = ArgumentCaptor.forClass(AiLifecycleEvent.class);
        verify(eventRepo).save(ev.capture());
        assertThat(ev.getValue().getActorUserId()).isEqualTo(ACTOR_ID);
        assertThat(ev.getValue().getActorName())
            .as("""
                ai_governance.ai_lifecycle_event declares actor_name beside actor_user_id and the \
                response record carried ONLY the name, so before F2-257 every event this service \
                wrote identified its actor nowhere the client could see. Ten registers had that \
                shape and nothing in the platform wrote the name.""")
            .isEqualTo("cro_user");
    }

    @Test
    @DisplayName("F2-257: with no principal BOTH halves are null — never one without the other")
    void anActorlessTimelineRowLeavesBothHalvesNull() {
        // IN_REVIEW does not call recordDecision, so this path does not refuse — which is the point.
        // A timeline writer records a side effect; making it throw would abort the operation it
        // exists to describe (F2-191), and openbanking.ob_event holds six live rows whose actor is a
        // machine with no user id at all. Both null together is the invariant, not "never null".
        parked("DUE", null);   // no signIn()

        svc.transitionRecertification(9L, to("IN_REVIEW"));

        ArgumentCaptor<AiLifecycleEvent> ev = ArgumentCaptor.forClass(AiLifecycleEvent.class);
        verify(eventRepo).save(ev.capture());
        assertThat(ev.getValue().getActorUserId()).isNull();
        assertThat(ev.getValue().getActorName())
            .as("requireUsername() here would have thrown and rolled back a legal transition")
            .isNull();
    }

    @Test
    @DisplayName("an illegal transition is still refused by the machine before any attribution runs")
    void theMachineStillGuardsTheTransition() {
        signIn();
        parked("RECERTIFIED", null);

        assertThatThrownBy(() -> svc.transitionRecertification(9L, to("IN_REVIEW")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot transition");

        verify(recertRepo, org.mockito.Mockito.never()).save(any(Recertification.class));
    }
}
