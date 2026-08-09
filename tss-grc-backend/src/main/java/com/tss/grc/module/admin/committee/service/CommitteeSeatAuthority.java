package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.security.CommitteeSeatDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * F2-177 — answers {@code hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')} from the roster F2-26
 * built.
 *
 * <h3>What made this possible, and what it measured on the way</h3>
 * <p>F2-26 linked roster seats to platform users; F2-28 made the roster the quorum denominator; S6c
 * added {@link CommitteeSeatOffice} because {@code committee_member_terms.role} is free text. All three
 * were prerequisites, and none of them was authorisation. This class is the first thing to <em>guard</em>
 * on a seat.
 *
 * <p>⚠ <b>Measured 2026-08-09, and it is the number a reader has to see before trusting a guard that
 * uses this:</b> of 43 seats in dev, <b>13</b> name a platform user, <b>1</b> is declared external, and
 * <b>29 do neither</b> — they name a human being with no platform identity and no declaration that they
 * lack one. For {@code AUDIT_COMMITTEE} specifically the linkage is <b>0 of 5</b>. So on the dev dataset
 * as it stands this predicate answers {@code false} for every caller, which is exactly as unreachable as
 * the ghost role name it replaces — <b>the difference is that a tenant administrator can now fix it from
 * the UI instead of needing a jar.</b> That distinction is the whole value of the change and it is also
 * the thing a green test suite cannot show you, so it is recorded here rather than in a commit message.
 *
 * <h3>Three decisions, stated once</h3>
 * <ol>
 *   <li><b>An observer seat counts.</b> {@code Standing Invitee} classifies as
 *       {@link CommitteeSeatOffice#OBSERVER} and is admitted, because on the audit committee that seat
 *       is held by the head of internal audit and the external auditor — the two people most obviously
 *       entitled to read an assurance view. Voting membership gates <em>resolutions</em>, not reads;
 *       conflating the two is how F2-52's guards came to exclude the people they were written for.</li>
 *   <li><b>A dissolved committee confers nothing.</b> {@code COM-OLD-TECH} is {@code DISSOLVED} in dev
 *       with a seat still on it. Its former chair is not on the committee, so
 *       {@link CommitteeRepository#findByOrganisationIdAndCommitteeTypeAndStatus} filters on
 *       {@code ACTIVE} and the entity's {@code @Where(is_deleted = 0)} does the rest.</li>
 *   <li><b>The term window is checked, and that is why this does not reuse
 *       {@link CommitteeMemberTermRepository#countActiveSeatsForUser}.</b> That method exists to stop
 *       one person holding two seats on one committee (F2-26), and it deliberately ignores dates: a
 *       future-dated appointment still <em>occupies</em> the seat. Authorisation is the opposite
 *       question — occupying a seat that has not started, or whose term ran out while
 *       {@code renewal_status} still says ACTIVE, must not authorise anything.</li>
 * </ol>
 *
 * <p><b>Tenancy.</b> {@code organisationId} comes from the caller's token via {@code GrcUserDetails},
 * never from the guard literal or a request body, and both queries filter on it. The guard's literal
 * names a committee <em>type</em>, so it cannot be used to point the lookup at another tenant's body.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommitteeSeatAuthority implements CommitteeSeatDirectory {

    private static final String ACTIVE = "ACTIVE";

    private final CommitteeRepository committees;
    private final CommitteeMemberTermRepository terms;

    /**
     * ⚠ F2-160 — the clock is an input. That finding was a suite that failed on Saturdays because
     * nothing said so; here a term boundary decides an authorisation outcome, so the test has to be
     * able to stand on either side of one. Same shape as {@code SanctionsHitService}.
     */
    private Clock clock = Clock.system(ZoneId.of("UTC"));

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean holdsActiveSeat(String committeeType, Long userId, Long organisationId) {
        if (committeeType == null || committeeType.isBlank() || userId == null || organisationId == null) {
            return false;
        }
        try {
            String type = committeeType.trim().toUpperCase(Locale.ROOT);
            List<Committee> bodies = committees.findByOrganisationIdAndCommitteeTypeAndStatus(
                organisationId, type, ACTIVE);
            if (bodies.isEmpty()) {
                // Not an error: a tenant need not have constituted every committee type. Logged at
                // debug because a guard evaluating false on an unconstituted body is the design.
                log.debug("[F2-177] org {} has no ACTIVE committee of type {} — seat leg is false",
                    organisationId, type);
                return false;
            }
            List<Long> ids = bodies.stream().map(Committee::getId).toList();
            return terms.countCurrentSeatsForUser(organisationId, ids, userId, LocalDate.now(clock)) > 0;
        } catch (RuntimeException ex) {
            // Never propagate. This is one leg of an `or`; throwing here would turn a false leg into a
            // 500 on an endpoint the caller may be entitled to reach by role. F2-159's resolvers make
            // the same promise for the same reason.
            log.warn("[F2-177] seat lookup failed for type '{}' user {} org {}: {} — refusing",
                committeeType, userId, organisationId, ex.toString());
            return false;
        }
    }
}
