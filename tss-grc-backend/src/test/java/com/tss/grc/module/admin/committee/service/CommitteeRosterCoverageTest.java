package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.RosterCoverageReport;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberSkillRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.iam.repository.UserRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>F2-178 — tenant-wide roster coverage.</b>
 *
 * <p>The finding this covers is not a broken mechanism either. F2-26 built the per-committee linkage
 * report and wired it to the member-registry screen, so a gap has been visible <em>one committee at a
 * time</em> ever since — which is enough to fix a roster you are already looking at, and no use at all for
 * learning that you have a problem. Production has <b>0 of 31 seats linked</b> and <b>11 of 17 committees
 * with no roster whatsoever</b>, and nobody found out, because finding out meant opening seventeen
 * registry screens in turn. F2-177 then made a seat an authorisation subject, so what used to be an
 * audit-trail blemish (a vote entered on someone's behalf) is now a closed assurance screen.
 *
 * <p>These tests pin the four things that make the roll-up worth having rather than a prettier total:
 *
 * <ol>
 *   <li>a committee with <b>no roster</b> is a named state, not 0% coverage — it can never be quorate,
 *       which is a different defect with a different remedy, and a report keyed by committee id
 *       structurally cannot show it;</li>
 *   <li>the <b>authorisation</b> count is reported beside the <b>roster</b> count, because
 *       {@code renewal_status = 'ACTIVE'} and "may authorise" are two different true statements about
 *       one column and nothing was reconciling them;</li>
 *   <li>DISSOLVED committees are excluded <em>and counted</em>, matching what the seat guard does, so an
 *       administrator is never shown a permanently red row they may not act on;</li>
 *   <li>rows arrive <b>worst-first</b>, so the report is a work queue rather than a list.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeRosterCoverageTest {

    private static final Long ORG = 1L;
    /**
     * Fixed so the term-window arithmetic is not a function of the day the suite runs — F2-160.
     *
     * <p>⚠ <b>And deliberately NOT the real current date.</b> This constant read {@code 2026-08-09} —
     * the day it was written — and a mutation replacing {@code LocalDate.now(clock)} with
     * {@code LocalDate.now()} in the service <b>survived</b>, because the two produced the same value.
     * A fixed-clock test whose fixed date happens to be today proves the clock is injected on every day
     * except the one you are looking at. Any far-future date works; this one is chosen so the assertion
     * fails loudly if the injection is ever removed.
     */
    private static final LocalDate TODAY = LocalDate.of(2029, 3, 14);

    @Mock CommitteeMemberTermRepository  terms;
    @Mock CommitteeRepository            committees;
    @Mock CommitteeMemberSkillRepository skills;
    @Mock UserRepository                 users;
    @Mock AuditLogService                audit;
    @Mock ApplicationEventPublisher      publisher;

    @InjectMocks CommitteeMemberRegistryService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service.setClock(Clock.fixed(TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── 1. the third state: no roster at all ────────────────────────────────

    @Test
    void aCommitteeWithNoRosterIsNamedAsSuchRatherThanReportedAsZeroPercentCovered() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE"),
            committee(11L, "COM-CYBER", "CYBER_SECURITY", "ACTIVE")));
        // Only the audit committee has term rows. The cyber committee produces no aggregate row at all —
        // which is precisely why the service joins against the committee list instead of reading the
        // shape off the aggregate.
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 2, 0, 5, 2, 5, 2)));

        RosterCoverageReport rep = service.rosterCoverage();

        RosterCoverageReport.Row cyber = row(rep, "COM-CYBER");
        assertThat(cyber.hasRoster()).isFalse();
        assertThat(cyber.seats()).isZero();
        assertThat(row(rep, "COM-AUDIT").hasRoster()).isTrue();
        assertThat(rep.totalCommittees()).isEqualTo(2);
        assertThat(rep.committeesWithRoster()).isEqualTo(1);
    }

    @Test
    void anEmptyRosterCountsAsAdmittingNobodyForTheSameReasonAnUnlinkedOneDoes() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE"),   // 0 linked
            committee(11L, "COM-CYBER", "CYBER_SECURITY", "ACTIVE"),    // no roster
            committee(12L, "COM-RISK",  "RISK_COMMITTEE", "ACTIVE")));  // 2 linked
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 0, 0, 5, 0, 5, 0),
            agg(12L, 7, 2, 0, 7, 2, 7, 2)));

        RosterCoverageReport rep = service.rosterCoverage();

        // The audit committee has a roster and no links; the cyber committee has no roster. Both admit
        // nobody, for different reasons, with identical consequences for every guard that asks.
        assertThat(rep.committeesAdmittingNobody()).isEqualTo(2);
        assertThat(row(rep, "COM-AUDIT").admitsNobody()).isTrue();
        assertThat(row(rep, "COM-CYBER").admitsNobody()).isTrue();
        assertThat(row(rep, "COM-RISK").admitsNobody()).isFalse();
    }

    // ── 2. two counts of "linked", because there are two questions ──────────

    @Test
    void aLinkedSeatOutsideItsTermWindowLowersTheGuardsCountAndNotTheRosterCount() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE")));
        // 5 ACTIVE seats, all 5 linked — but only 1 is inside its term window. Nothing in the platform
        // ever moves an ACTIVE term to EXPIRED, so this is the state a roster drifts into by itself.
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 5, 0, 5, 5, 1, 1)));

        RosterCoverageReport rep = service.rosterCoverage();
        RosterCoverageReport.Row r = row(rep, "COM-AUDIT");

        assertThat(r.linked()).isEqualTo(5);
        assertThat(r.coveragePct()).isEqualTo(100);      // the roster reads complete…
        assertThat(r.currentLinked()).isEqualTo(1);      // …and admits one person
        assertThat(r.admitsNobody()).isFalse();
        assertThat(rep.totalLinked()).isEqualTo(5);
        assertThat(rep.totalCurrentLinked()).isEqualTo(1);
    }

    @Test
    void aFullyLapsedRosterReadsOneHundredPercentAndStillAdmitsNobody() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE")));
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 5, 0, 5, 5, 0, 0)));

        RosterCoverageReport rep = service.rosterCoverage();
        RosterCoverageReport.Row r = row(rep, "COM-AUDIT");

        // This is the pair of numbers the whole slice exists to put next to each other. Reporting only
        // coveragePct here would state, truthfully and uselessly, that the roster is perfect.
        assertThat(r.coveragePct()).isEqualTo(100);
        assertThat(r.admitsNobody()).isTrue();
        assertThat(rep.committeesFullyLinked()).isEqualTo(1);
        assertThat(rep.committeesAdmittingNobody()).isEqualTo(1);
    }

    // ── 3. declared-external seats are not a gap ────────────────────────────

    @Test
    void declaredExternalSeatsLeaveTheDenominatorRatherThanKeepingTheReportPermanentlyRed() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE")));
        // 5 seats: 3 linked, 2 declared external (the external auditor and an independent director who
        // will never hold a login). Nothing is unlinked, so this roster is finished.
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 3, 2, 4, 3, 5, 3)));

        RosterCoverageReport.Row r = row(service.rosterCoverage(), "COM-AUDIT");

        assertThat(r.unlinked()).isZero();
        assertThat(r.coveragePct()).isEqualTo(100);
        assertThat(r.declaredExternal()).isEqualTo(2);
    }

    @Test
    void aRosterOfNothingButExternalSeatsIsNotCountedAsFullyLinked() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-BOARD", "BOARD", "ACTIVE")));
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 4, 0, 4, 4, 0, 4, 0)));

        RosterCoverageReport rep = service.rosterCoverage();

        // Nothing is linkable, so there is no gap to report and nothing to congratulate either: a board
        // of six external directors admits nobody and no amount of data entry changes that.
        assertThat(row(rep, "COM-BOARD").coveragePct()).isZero();
        assertThat(rep.committeesFullyLinked()).isZero();
        assertThat(rep.committeesAdmittingNobody()).isEqualTo(1);
    }

    // ── 4. DISSOLVED is excluded, and said so ───────────────────────────────

    @Test
    void dissolvedCommitteesAreExcludedFromEveryFigureAndTheExclusionIsReported() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE"),
            committee(13L, "COM-TECH",  "TECH_COMMITTEE",  "DISSOLVED")));
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 2, 0, 5, 2, 5, 2),
            agg(13L, 1, 0, 0, 1, 0, 1, 0)));   // the dissolved body still has a term row

        RosterCoverageReport rep = service.rosterCoverage();

        assertThat(rep.committees()).extracting(RosterCoverageReport.Row::code)
            .containsExactly("COM-AUDIT");
        assertThat(rep.totalCommittees()).isEqualTo(1);
        assertThat(rep.totalSeats()).isEqualTo(5);          // the dissolved committee's seat is not here
        assertThat(rep.dissolvedExcluded()).isEqualTo(1);
        // The exclusion must be a number, not an absence: a short list with no explanation reads as
        // "these are all your committees", which is how CommitteeSeatAuthority's DISSOLVED rule would
        // otherwise become invisible to the person reading the report.
        assertThat(rep.committeesAdmittingNobody()).isZero();
    }

    // ── 5. ordering makes it a work queue ───────────────────────────────────

    /**
     * ⚠ <b>The codes are deliberately adversarial to the expected order, and the first version of this
     * test was vacuous because they were not.</b> It named them ALPHA / MID / PART / ZETA "for
     * readability", which made alphabetical order <em>coincide</em> with worst-first — so a mutation
     * replacing the whole comparator with {@code String.valueOf(code)} produced the identical list and
     * <b>survived</b>. Here the alphabetical order is very nearly the reverse of the correct one, so the
     * assertion can only pass if the severity comparator is the one doing the work.
     */
    @Test
    void rowsArriveWorstFirstSoTheReportIsAWorkQueueRatherThanAList() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AAA", "AUDIT_COMMITTEE",      "ACTIVE"),  // complete       -> last
            committee(11L, "COM-ZZZ", "CYBER_SECURITY",       "ACTIVE"),  // no roster      -> first
            committee(12L, "COM-MMM", "RISK_COMMITTEE",       "ACTIVE"),  // admits nobody  -> second
            committee(14L, "COM-BBB", "COMPLIANCE_COMMITTEE", "ACTIVE"))); // partly linked -> third
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 3, 3, 0, 3, 3, 3, 3),
            agg(12L, 5, 0, 0, 5, 0, 5, 0),
            agg(14L, 6, 2, 0, 6, 2, 6, 2)));

        List<String> order = service.rosterCoverage().committees().stream()
            .map(RosterCoverageReport.Row::code).toList();

        // Admits-nobody first, and among those the empty roster before the 0%-linked one, because
        // appointing members is the more basic omission; then the partly-linked; then the complete.
        assertThat(order).containsExactly("COM-ZZZ", "COM-MMM", "COM-BBB", "COM-AAA");
    }

    @Test
    void theCodeIsOnlyATieBreakAmongEquallyBadCommittees() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-ZZZ", "AUDIT_COMMITTEE", "ACTIVE"),
            committee(11L, "COM-AAA", "CYBER_SECURITY",  "ACTIVE")));
        // Identical shape: same seats, same links, same currency. Nothing but the code separates them,
        // and a stable, predictable order matters on a screen an administrator re-reads.
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 4, 1, 0, 4, 1, 4, 1),
            agg(11L, 4, 1, 0, 4, 1, 4, 1)));

        assertThat(service.rosterCoverage().committees())
            .extracting(RosterCoverageReport.Row::code)
            .containsExactly("COM-AAA", "COM-ZZZ");
    }

    // ── 6. arithmetic and plumbing ──────────────────────────────────────────

    @Test
    void tenantTotalsAreTheSumOfTheRows() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE",      "ACTIVE"),
            committee(12L, "COM-RISK",  "RISK_COMMITTEE",       "ACTIVE"),
            committee(14L, "COM-COMP",  "COMPLIANCE_COMMITTEE", "ACTIVE")));
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 0, 1, 5, 0, 5, 0),
            agg(12L, 7, 2, 0, 7, 2, 6, 1),
            agg(14L, 6, 2, 0, 6, 2, 6, 2)));

        RosterCoverageReport rep = service.rosterCoverage();

        assertThat(rep.totalSeats()).isEqualTo(18);
        assertThat(rep.totalLinked()).isEqualTo(4);
        assertThat(rep.totalDeclaredExternal()).isEqualTo(1);
        assertThat(rep.totalUnlinked()).isEqualTo(13);      // 18 − 4 − 1
        assertThat(rep.totalCurrentLinked()).isEqualTo(3);   // one of the risk committee's links has lapsed
        assertThat(rep.totalSeats())
            .isEqualTo(rep.totalLinked() + rep.totalDeclaredExternal() + rep.totalUnlinked());
    }

    @Test
    void aNullSumFromAnEmptyGroupIsCoalescedRatherThanThrowing() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(
            committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE")));
        // SQL Server returns NULL, not 0, from SUM over an empty group. This query groups only non-empty
        // groups so it cannot happen today — but a LEFT JOIN added later would reintroduce it, and the
        // failure would be an NPE inside a report rather than anything a reader could interpret.
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            new Object[] { 10L, 4L, null, null, null, null, null, null }));

        RosterCoverageReport.Row r = row(service.rosterCoverage(), "COM-AUDIT");

        assertThat(r.seats()).isEqualTo(4);
        assertThat(r.linked()).isZero();
        assertThat(r.unlinked()).isEqualTo(4);
        assertThat(r.currentLinked()).isZero();
    }

    @Test
    void theAggregateIsScopedToTheCallersTenantAndJudgedAgainstTheInjectedClock() {
        when(committees.findByOrganisationId(anyLong())).thenReturn(List.of());
        when(terms.rosterCoverageByCommittee(anyLong(), any())).thenReturn(List.of());

        service.rosterCoverage();

        ArgumentCaptor<Long> org = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDate> on = ArgumentCaptor.forClass(LocalDate.class);
        verify(terms).rosterCoverageByCommittee(org.capture(), on.capture());
        assertThat(org.getValue()).isEqualTo(ORG);
        // Not LocalDate.now(): a report whose term arithmetic depends on the wall clock is a report that
        // fails on one day in seven and passes on review (F2-160).
        assertThat(on.getValue()).isEqualTo(TODAY);
        verify(committees).findByOrganisationId(ORG);
    }

    @Test
    void aTenantWithNoCommitteesReportsZeroesRatherThanFailing() {
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of());
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of());

        RosterCoverageReport rep = service.rosterCoverage();

        assertThat(rep.committees()).isEmpty();
        assertThat(rep.totalCommittees()).isZero();
        assertThat(rep.committeesAdmittingNobody()).isZero();
        assertThat(rep.dissolvedExcluded()).isZero();
    }

    @Test
    void bothNamesAreCarriedSoTheArabicUiDoesNotFallBackToEnglish() {
        Committee c = committee(10L, "COM-AUDIT", "AUDIT_COMMITTEE", "ACTIVE");
        c.setNameAr("لجنة المراجعة");
        when(committees.findByOrganisationId(ORG)).thenReturn(List.of(c));
        when(terms.rosterCoverageByCommittee(ORG, TODAY)).thenReturn(List.<Object[]>of(
            agg(10L, 5, 2, 0, 5, 2, 5, 2)));

        RosterCoverageReport.Row r = row(service.rosterCoverage(), "COM-AUDIT");

        assertThat(r.nameEn()).isEqualTo("Audit Committee");
        assertThat(r.nameAr()).isEqualTo("لجنة المراجعة");
        // The committee TYPE is carried too, because it is the first argument of the
        // hasPermission(type,'COMMITTEE','SEAT') guard — so a reader can tell which guarded screens this
        // row is the explanation for.
        assertThat(r.committeeType()).isEqualTo("AUDIT_COMMITTEE");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static RosterCoverageReport.Row row(RosterCoverageReport rep, String code) {
        return rep.committees().stream().filter(r -> code.equals(r.code())).findFirst()
            .orElseThrow(() -> new AssertionError("no row for " + code + " in " + rep.committees()));
    }

    private static Committee committee(Long id, String code, String type, String status) {
        Committee c = new Committee();
        c.setId(id);
        c.setOrganisationId(ORG);
        c.setCode(code);
        c.setNameEn(switch (type) {
            case "AUDIT_COMMITTEE" -> "Audit Committee";
            case "BOARD"           -> "Board of Directors";
            default                -> type;
        });
        c.setCommitteeType(type);
        c.setStatus(status);
        return c;
    }

    /** One row of {@code rosterCoverageByCommittee}, in its declared column order. */
    private static Object[] agg(Long committeeId, long seats, long linked, long external,
                                long votingSeats, long votingLinked,
                                long currentSeats, long currentLinked) {
        return new Object[] { committeeId, seats, linked, external,
                              votingSeats, votingLinked, currentSeats, currentLinked };
    }
}
