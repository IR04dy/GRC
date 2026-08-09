package com.tss.grc.module.intake.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-172 — every employee can now use the platform's front door, and this pins the guard that makes
 * that true together with the three that must NOT move with it.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@code POST /api/v1/intake} was {@code hasPermission('INTAKE','WRITE')}. Measured 2026-08-09
 * against {@code iam.role_permissions}, that permission is held by <b>3 of 69 active roles</b> —
 * {@code COMPLIANCE_OFFICER}, {@code GRC_ADMINISTRATOR}, {@code ORG_ADMIN} — every one an
 * administrator. Of the seven documented dev personas only {@code compliance} held it.
 *
 * <p>The intake substrate is where <em>new work originates</em>: 26 kinds, including reporting a
 * security event (NCA ECC 6.1), a privacy breach (PDPL Art. 27), a workplace injury (GOSI, 24-hour
 * notification), raising a risk, submitting a BIA, and blowing the whistle (Nazaha, ISO 37001 §8.3).
 * Under the old guard an ordinary employee could do none of those. Nobody had noticed because no
 * product code had ever called the endpoint — F2-171 found the client writing submissions to
 * {@code localStorage} — so the first act of wiring the client to the server would have converted a
 * universally-available front door into an administrators-only one, and the failure would have
 * arrived as a 403 on "Report a security event".
 *
 * <h3>Why this is not simply "open the guard"</h3>
 *
 * <p>Same trap as F2-144, avoided the same way and in the same order. {@code submit()} read
 * {@code submitterName}, {@code submitterEmail}, {@code submitterPhone} and
 * {@code submitterIsAnonymous} from the request body, and {@code intake.intake_records} had no column
 * naming a platform user at all — so a wider guard over a body-chosen submitter is an impersonation
 * path. V374 added {@code submitter_user_id} and {@code IntakeService} fills it from
 * {@code CurrentActor}; {@code IntakeSubmitterBindingTest} holds that half and fails unconditionally
 * if the service trusts the body again. <b>The two files together, not either alone, are what makes
 * {@code isAuthenticated()} defensible here.</b>
 *
 * <h3>Why a permission is the wrong instrument</h3>
 *
 * <p>ADR-010 §Sanctioned exceptions: a grant list can only ever wrongly <em>exclude</em> someone, and
 * for an endpoint that is how a person discharges a duty, exclusion is the expensive failure — the
 * obligation still exists, the platform records no discharge of it, and the gap reads as the
 * employee's fault. Note this endpoint <b>subsumes</b> {@code CoiController#submitDeclaration}, which
 * was admitted for exactly this reason at F2-144(b): {@code ETHICS_DISCLOSURE} is one of the 26
 * kinds. Gating the same duty two different ways depending on which screen the employee reached would
 * have been indefensible on its face.
 *
 * @see com.tss.grc.module.intake.service.IntakeSubmitterBindingTest the server-side subject binding
 * @see com.tss.grc.security.ControllerAuthorizationRatchetTest {@code SELF_SCOPED} — the sanctioned floor
 */
class IntakeSubmitGuardTest {

    private static final String OPEN_SUBMIT = "isAuthenticated()";

    private static Map<String, String> guards() {
        Map<String, String> found = new TreeMap<>();
        for (Method m : IntakeController.class.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                || m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
            if (!mapped) {
                continue;
            }
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<NO GUARD>" : pa.value());
        }
        return found;
    }

    /** Anti-vacuity: reflection that resolved nothing would make every assertion below pass. */
    @Test
    void theControllerResolvesItsMappedHandlers() {
        assertThat(guards().keySet())
            .as("IntakeController's mapped handlers did not resolve — the reflection is broken, so "
                + "every guard assertion in this class is vacuous")
            .contains("submit", "list", "findOne", "triage", "track");
    }

    @Test
    void anyAuthenticatedEmployeeMayFileAnIntake() {
        assertThat(guards())
            .as("submit must stay isAuthenticated(). A permission here can only wrongly exclude a "
                + "reporter, and an employee unable to report a security event (NCA ECC 6.1), a "
                + "privacy breach (PDPL Art. 27) or a workplace injury (GOSI 24h) is a compliance "
                + "failure that looks like theirs — ADR-010 §Sanctioned exceptions. Measured: the "
                + "permission this replaced was held by 3 of 69 roles, all administrators.")
            .containsEntry("submit", OPEN_SUBMIT);
    }

    /**
     * The asymmetry is the design, and this is the assertion that keeps the exception from leaking.
     * Filing is a duty; <em>reading other people's</em> submissions is a privilege — the inbox holds
     * whistleblowing reports, DSRs naming data subjects, and injury notifications. An over-wide READ
     * here would expose precisely the material the open write exists to let people report safely.
     */
    @Test
    void readingAndTriagingOtherPeoplesSubmissionsStaysPermissioned() {
        assertThat(guards())
            .containsEntry("list", "hasPermission('INTAKE', 'READ')")
            .containsEntry("findOne", "hasPermission('INTAKE', 'READ')")
            .containsEntry("triage", "hasPermission('INTAKE', 'TRIAGE')");
    }

    /**
     * The public case-tracker is deliberately unauthenticated — an anonymous submitter pastes their
     * reference code — and that is safe only because it answers with the PII-free
     * {@code IntakePublicStatusDto}. Pinned so a later "consistency" pass does not point it at the
     * full record: a reference code is a weak, enumerable bearer, and the endpoint's own javadoc
     * (SEC-FIN-001) says so.
     */
    @Test
    void thePublicTrackerHasNoGuardAndThatIsIntentional() {
        assertThat(guards()).containsEntry("track", "<NO GUARD>");
        assertThat(IntakeController.class.getDeclaredMethods())
            .filteredOn(m -> m.getName().equals("track"))
            .allSatisfy(m -> assertThat(m.getGenericReturnType().getTypeName())
                .as("the public tracker must keep returning the PII-free projection — never the full "
                    + "IntakeRecordDto, which carries submitter identity, the raw payload and the "
                    + "event history")
                .contains("IntakePublicStatusDto"));
    }
}
