package com.tss.grc.module.change_adapters;

import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.service.CommitteeGateMapService;
import com.tss.grc.module.admin.committee.service.CommitteeReferralService;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S5 / CE-1 — the adapter is pure translation, so what needs testing is that the translation is
 * <b>total</b>.
 *
 * <p>A projection that silently drops a field is worse than the direct import it replaced: two change
 * endpoints return {@code ReferralView} over HTTP, so a missed field is a field that vanishes from
 * the frontend with nothing failing anywhere. {@link #theProjectionCoversEveryFieldOfThePeerEntity}
 * is therefore a ratchet, not a unit test — it fails when a column is added to
 * {@code CommitteeReferral} and not to the port, which is the only moment anyone would think to look.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeAdapterTest {

    @Mock private CommitteeReferralService referrals;
    @Mock private CommitteeGateMapService gateMap;

    private CommitteeAdapter adapter() { return new CommitteeAdapter(referrals, gateMap); }

    private static CommitteeReferral entity() {
        CommitteeReferral r = new CommitteeReferral();
        r.setId(41L);
        r.setOrganisationId(1L);
        r.setSourceModule("CHANGE");
        r.setSourceEntityId(500L);
        r.setCommitteeId(13L);
        r.setMeetingId(69L);
        r.setResolutionId(7L);
        r.setGate("CAB");
        r.setMode("APPROVAL");
        r.setStatus("DECIDED");
        r.setReferredBy("compliance");
        r.setRecusalNames("raiser,owner");
        return r;
    }

    @Test
    void everyFieldSurvivesTheCrossing() {
        when(referrals.activeReferral("CHANGE", 500L)).thenReturn(Optional.of(entity()));

        CommitteePort.ReferralView v = adapter().activeReferral("CHANGE", 500L).orElseThrow();

        assertThat(v.id()).isEqualTo(41L);
        assertThat(v.organisationId()).isEqualTo(1L);
        assertThat(v.sourceModule()).isEqualTo("CHANGE");
        assertThat(v.sourceEntityId()).isEqualTo(500L);
        assertThat(v.committeeId()).isEqualTo(13L);
        assertThat(v.meetingId()).isEqualTo(69L);
        assertThat(v.resolutionId()).isEqualTo(7L);
        assertThat(v.gate()).isEqualTo("CAB");
        assertThat(v.mode()).isEqualTo("APPROVAL");
        assertThat(v.status()).isEqualTo("DECIDED");
        assertThat(v.referredBy()).isEqualTo("compliance");
        assertThat(v.recusalNames()).isEqualTo("raiser,owner");
    }

    /**
     * The ratchet. Two change endpoints serialise {@code ReferralView} to the frontend, so a field
     * present on the entity and absent from the record disappears from the API with nothing failing.
     *
     * <p>{@code isDeleted} is excluded deliberately: soft-delete state is the engine's bookkeeping,
     * the repository already filters on it, and exposing it would invite a caller to make a decision
     * from it. Stated as an exclusion rather than simply omitted, because an unexplained gap in a
     * completeness check is indistinguishable from an oversight.
     */
    @Test
    void theProjectionCoversEveryFieldOfThePeerEntity() {
        java.util.Set<String> excluded = java.util.Set.of("isDeleted");

        java.util.Set<String> entityFields = new TreeSet<>();
        for (Field f : CommitteeReferral.class.getDeclaredFields()) {
            if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (excluded.contains(f.getName())) continue;
            entityFields.add(f.getName());
        }

        java.util.Set<String> viewComponents = new TreeSet<>();
        List<String> order = new ArrayList<>();
        for (var rc : CommitteePort.ReferralView.class.getRecordComponents()) {
            viewComponents.add(rc.getName());
            order.add(rc.getName());
        }

        assertThat(viewComponents)
            .as("""
                CommitteeReferral has fields the port does not carry, so they vanish between the \
                committee engine and the frontend with nothing failing:

                  entity  %s
                  port    %s

                Add the component to ReferralView and map it in CommitteeAdapter.view(), or add it to \
                `excluded` here with a reason. A silent projection is the failure mode a port is \
                supposed to prevent, not introduce.""", entityFields, viewComponents)
            .containsAll(entityFields);

        // Order matters for readability, not correctness — but a record whose order drifts from the
        // entity is a record somebody edited without looking at the entity.
        assertThat(order.subList(0, 4))
            .containsExactly("id", "organisationId", "sourceModule", "sourceEntityId");
    }

    @Test
    void aWriteWithNoCommitteeEngineFailsLoudlyRatherThanPretending() {
        CommitteeAdapter noEngine = new CommitteeAdapter(null, null);

        assertThatThrownBy(() -> noEngine.refer("CHANGE", 1L, "CAB", "APPROVAL", 13L, "x", null,
            new CommitteePort.SubjectRef("CHG-1", "t", null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("committee engine is not available")
            .as("recording a change as committee-decided without the engine would assert an "
              + "authority that never ran (ADR-017 D-2)");

        assertThatThrownBy(() -> noEngine.mapGate("CAB", 13L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aReadWithNoEngineIsEmptyRatherThanAnError() {
        CommitteeAdapter noEngine = new CommitteeAdapter(null, null);

        // Reads degrade; writes throw. A change register that 500s because no committee is wired
        // would make the whole module unusable on a tenant that has not constituted one yet.
        assertThat(noEngine.activeReferral("CHANGE", 1L)).isEmpty();
        assertThat(noEngine.gateMap()).isEmpty();
        assertThat(noEngine.eligibleCommittees()).isEmpty();
    }

    @Test
    void theAdapterAddsNoDecisionLogicOfItsOwn() {
        // assertDecisionApproves must be delegated verbatim: it reads the resolution tally, the
        // meeting's quorum source and the minutes' approval state, and re-deriving any of that here
        // is how the platform ends up with a second, weaker answer to "was this quorate?".
        adapter().assertDecisionApproves("CHANGE", 500L, 2);
        verify(referrals).assertDecisionApproves("CHANGE", 500L, 2);
    }

    @Test
    void describeReReadsTheReferralAndNeverInventsIt() {
        when(referrals.activeReferral("CHANGE", 500L)).thenReturn(Optional.empty());
        java.util.Map<String, Object> out = new java.util.HashMap<>();

        adapter().describe(new CommitteePort.ReferralView(
            41L, 1L, "CHANGE", 500L, 13L, null, null, "CAB", "APPROVAL", "PENDING", "x", null,
            LocalDateTime.now(), "CHG-1", "Subject", null), out);

        assertThat(out).as("a referral the engine no longer has must enrich nothing").isEmpty();
        verify(referrals, never()).enrich(org.mockito.ArgumentMatchers.any(),
                                          org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aNullReferralCrossesAsNullRatherThanThrowing() {
        when(referrals.linkResolution(anyString(), anyLong(), anyLong())).thenReturn(null);
        assertThat(adapter().linkResolution("CHANGE", 500L, 7L)).isNull();
    }
}
