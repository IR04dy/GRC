package com.tss.grc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.bcm.cycle.entity.CrisisDecision;
import com.tss.grc.module.privacy.dsa.entity.DataSharingAgreement;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-22b(iii) — decision, verification and signature provenance is server-owned.
 *
 * <p>Third tranche of the same rule, after {@code createdBy}/{@code updatedBy} (F2-22a) and
 * {@code approvedBy}/{@code approvedAt} (F2-22b(ii)): <b>a field that records who did something, or
 * when, is never the caller's to set</b>. DF-3 established it for a committee vote and DF-4 for a CAB
 * approval, both of which were real bypasses found by hand.
 *
 * <p><b>This tranche is defence in depth, not a newly closed hole, and the distinction is worth
 * stating.</b> Measurement first, per the standing rule: every {@code setDecidedBy} /
 * {@code setDecidedAt} / {@code setSignedAt} call in the platform already takes its value from an
 * actor or from {@code now()}, and {@code CorrectiveActionService} is explicit about it — create
 * scrubs {@code verifiedBy}/{@code verifiedAt} (SEC-ESG-003), update whitelists around them, and only
 * {@code transition()} sets them, from the authenticated verifier. So no live bypass was found here.
 * What the annotations add is that the guarantee stops depending on each service remembering: a new
 * endpoint on any of these entities cannot reintroduce it, which is exactly the argument F2-22a made
 * for {@code BaseEntity}'s nine fields.
 *
 * <p><b>What was deliberately NOT annotated.</b> {@code verifiedBy} is legitimately caller-supplied on
 * two <em>DTO</em> paths — {@code CapaTaskService.transition} sets it from
 * {@code CapaTaskDto.TransitionRequest} on the CLOSED branch, and {@code VulnerabilityService} does
 * the same. Entity annotations do not reach them, and that is the useful correction to F2-22b's
 * premise: <b>a DTO does not fix mass assignment if the DTO carries the same field.</b> Those, plus
 * {@code BoardPackService}, {@code CptyService} and {@code TreatmentPlanService}, are the recorded
 * follow-up list.
 */
class DecisionProvenanceBindingTest {

    /**
     * Configured to match what Spring Boot auto-configures, not what {@code new ObjectMapper()}
     * gives you. Three differences bite here, and all three produced false failures on the first run:
     * a bare mapper cannot read {@code LocalDateTime} at all (JavaTimeModule — the same trap F2-22a
     * documented), it <b>throws</b> on an unknown property where Boot ignores it, and it writes dates
     * as numeric arrays rather than ISO strings. A test asserting Jackson behaviour has to use the
     * application's Jackson behaviour or it is testing a different mapper than the one that runs.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Every provenance field named in one hostile body, so a partial fix cannot pass. */
    private static final String HOSTILE = """
        {
          "decidedBy": 9999,
          "decidedByName": "the_ceo",
          "decidedAt": "2001-01-01T00:00:00",
          "verifiedBy": "the_auditor",
          "verifiedAt": "2001-01-01T00:00:00",
          "signedAt": "2001-01-01T00:00:00"
        }
        """;

    @Test
    void aCrisisDecisionCannotNameItsOwnDecider() throws Exception {
        CrisisDecision bound = MAPPER.readValue(HOSTILE, CrisisDecision.class);

        assertThat(bound.getDecidedBy())
            .as("a crisis decision log whose decider is caller-supplied records nothing — DF-3's rule")
            .isNull();
        assertThat(bound.getDecidedByName())
            .as("and the NAME is the field DF-3 was actually about — an id is harder to forge "
                + "convincingly than a display name that appears in the decision log")
            .isNull();
        // decidedAt initialises to now() on the entity, so READ_ONLY means the POSTED value never
        // lands — not that the field is null. Asserting null here would have been wrong.
        assertThat(bound.getDecidedAt())
            .as("the backdated 2001 timestamp must not bind; the field keeps its server default")
            .isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
    }

    @Test
    void aCorrectiveActionCannotCarryAForgedVerifier() throws Exception {
        CorrectiveAction bound = MAPPER.readValue(HOSTILE, CorrectiveAction.class);

        assertThat(bound.getVerifiedBy())
            .as("CAPA verification is the evidence that a remediation actually happened")
            .isNull();
        assertThat(bound.getVerifiedAt()).isNull();
    }

    @Test
    void anAmlAlertAndADisclosureCannotBackdateTheirDecision() throws Exception {
        AmlAlert alert = MAPPER.readValue(HOSTILE, AmlAlert.class);
        assertThat(alert.getDecidedAt()).as("an AML alert decision timestamp is a regulatory clock")
            .isNull();
        assertThat(alert.getDecidedByName())
            .as("who cleared an AML alert is the whole audit question")
            .isNull();
    }

    @Test
    void aDataSharingAgreementCannotClaimToHaveBeenSigned() throws Exception {
        DataSharingAgreement bound = MAPPER.readValue(HOSTILE, DataSharingAgreement.class);

        assertThat(bound.getSignedAt())
            .as("signedAt is set by the service at signing time; a posted value asserts an "
                + "agreement that may never have been executed")
            .isNull();
    }

    /**
     * ...and all of it must still SERIALISE. Every one of these fields is displayed or exported —
     * the crisis decision log, the CAPA verification column, the DSA register. Hiding them would be
     * the {@code @JsonIgnore} trap F2-22a documented, where the mitigation is worse than the defect.
     */
    @Test
    void provenanceIsStillReadable() throws Exception {
        CrisisDecision d = new CrisisDecision();
        d.setDecidedBy(42L);
        d.setDecidedByName("real_commander");
        d.setDecidedAt(LocalDateTime.of(2026, 7, 28, 9, 0));
        assertThat(MAPPER.writeValueAsString(d))
            .contains("\"decidedBy\":42")
            .contains("\"decidedByName\":\"real_commander\"")
            .contains("2026-07-28T09:00");

        CorrectiveAction ca = new CorrectiveAction();
        ca.setVerifiedBy("real_verifier");
        assertThat(MAPPER.writeValueAsString(ca)).contains("\"verifiedBy\":\"real_verifier\"");
    }
}
