package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F2-177 — the seat predicate that closed F2-52's last three ghost role names.
 *
 * <h3>⚠ What this test can and cannot prove, stated up front</h3>
 * <p>The term-window rule ("a seat that has not started, or whose term ran out while
 * {@code renewal_status} still says ACTIVE, authorises nothing") lives in <b>JPQL</b>, and the unit suite
 * is database-free by design. So the assertions below prove the two halves this layer owns — that the
 * <em>clock</em> decides which date is asked about, and that every other refusal path is closed — and the
 * window itself is proven in the live probe against real rows, which is recorded in the register rather
 * than implied by a green test here. F2-159's lesson applies directly: a test that describes the code
 * accurately can still be describing the wrong thing, and the honest defence is naming the gap.
 */
class CommitteeSeatAuthorityTest {

    private static final Long ORG = 1L;
    private static final Long USER = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);

    private CommitteeRepository committees;
    private CommitteeMemberTermRepository terms;
    private CommitteeSeatAuthority authority;

    @BeforeEach
    void setUp() {
        committees = mock(CommitteeRepository.class);
        terms = mock(CommitteeMemberTermRepository.class);
        authority = new CommitteeSeatAuthority(committees, terms);
        authority.setClock(Clock.fixed(TODAY.atStartOfDay(ZoneId.of("UTC")).toInstant(),
                                       ZoneId.of("UTC")));
    }

    private Committee body(long id) {
        Committee c = new Committee();
        c.setId(id);
        c.setOrganisationId(ORG);
        c.setCommitteeType("AUDIT_COMMITTEE");
        c.setStatus("ACTIVE");
        return c;
    }

    // ── the affirmative case ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a user named on a current seat of an ACTIVE committee of that type holds it")
    void aSeatedUserHoldsTheSeat() {
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(ORG, "AUDIT_COMMITTEE", "ACTIVE"))
            .thenReturn(List.of(body(3L)));
        when(terms.countCurrentSeatsForUser(eq(ORG), any(), eq(USER), eq(TODAY))).thenReturn(1L);

        assertThat(authority.holdsActiveSeat("AUDIT_COMMITTEE", USER, ORG)).isTrue();
    }

    @Test
    @DisplayName("a seat that names nobody — 29 of 43 in dev — confers nothing")
    void anUnlinkedSeatConfersNothing() {
        // The measured state of the dataset: COM-AUDIT has five seats and member_user_id is NULL on all
        // five, so the count is 0 and the predicate is false. This is the case a reader most needs to see
        // asserted, because it is what the guard does TODAY — the change is that a tenant administrator
        // can now fix it from the roster screen instead of needing a jar.
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(ORG, "AUDIT_COMMITTEE", "ACTIVE"))
            .thenReturn(List.of(body(3L)));
        when(terms.countCurrentSeatsForUser(eq(ORG), any(), eq(USER), eq(TODAY))).thenReturn(0L);

        assertThat(authority.holdsActiveSeat("AUDIT_COMMITTEE", USER, ORG)).isFalse();
    }

    // ── the refusals ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("only ACTIVE committees are asked about — a DISSOLVED body confers no seat")
    void onlyActiveCommitteesAreConsidered() {
        // dev has COM-OLD-TECH, DISSOLVED, with a seat still attached. Its former chair is not on a
        // committee. Asserted on the ARGUMENT because that is where the rule lives: if the status filter
        // were dropped, this call would still return a body and the seat would authorise.
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(anyLong(), anyString(), anyString()))
            .thenReturn(List.of());

        assertThat(authority.holdsActiveSeat("TECH_COMMITTEE", USER, ORG)).isFalse();
        verify(committees).findByOrganisationIdAndCommitteeTypeAndStatus(ORG, "TECH_COMMITTEE", "ACTIVE");
        verifyNoInteractions(terms);
    }

    @Test
    @DisplayName("an unconstituted committee type is a quiet false, not an error")
    void anUnconstitutedTypeIsFalse() {
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(anyLong(), anyString(), anyString()))
            .thenReturn(List.of());
        assertThat(authority.holdsActiveSeat("SHARIA_BOARD", USER, ORG)).isFalse();
        verify(terms, never()).countCurrentSeatsForUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("the tenant is the caller's own, on both queries")
    void bothQueriesAreTenantScoped() {
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(eq(7L), anyString(), anyString()))
            .thenReturn(List.of(body(3L)));
        when(terms.countCurrentSeatsForUser(anyLong(), any(), anyLong(), any())).thenReturn(1L);

        assertThat(authority.holdsActiveSeat("AUDIT_COMMITTEE", USER, 7L)).isTrue();
        verify(committees).findByOrganisationIdAndCommitteeTypeAndStatus(7L, "AUDIT_COMMITTEE", "ACTIVE");
        verify(terms).countCurrentSeatsForUser(eq(7L), any(), eq(USER), eq(TODAY));
    }

    @Test
    @DisplayName("every committee of that type is considered, because a tenant may constitute two")
    void allBodiesOfTheTypeAreConsidered() {
        // dev has THREE IT_STEERING committees (COM-IT plus two golden-path CABs), which is why the
        // finder returns a List. An Optional-shaped query would have thrown inside @PreAuthorize and
        // turned a false guard leg into a 500.
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(ORG, "IT_STEERING", "ACTIVE"))
            .thenReturn(List.of(body(13L), body(103L), body(104L)));
        when(terms.countCurrentSeatsForUser(anyLong(), any(), anyLong(), any())).thenReturn(1L);

        assertThat(authority.holdsActiveSeat("IT_STEERING", USER, ORG)).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(terms).countCurrentSeatsForUser(eq(ORG), ids.capture(), eq(USER), eq(TODAY));
        assertThat(ids.getValue()).containsExactly(13L, 103L, 104L);
    }

    @Test
    @DisplayName("the date asked about comes from the clock, so a term boundary is testable")
    void theQueryDateComesFromTheClock() {
        // F2-160 — the suite that failed on Saturdays because nothing said the clock was an input. Here a
        // term boundary decides an authorisation outcome; if this method read LocalDate.now() directly,
        // no test could ever stand on the far side of one.
        authority.setClock(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("UTC")));
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(ORG, "AUDIT_COMMITTEE", "ACTIVE"))
            .thenReturn(List.of(body(3L)));
        when(terms.countCurrentSeatsForUser(anyLong(), any(), anyLong(), any())).thenReturn(0L);

        authority.holdsActiveSeat("AUDIT_COMMITTEE", USER, ORG);
        verify(terms).countCurrentSeatsForUser(eq(ORG), any(), eq(USER), eq(LocalDate.of(2030, 1, 1)));
    }

    @Test
    @DisplayName("the committee type is normalised, so a lower-case guard literal still resolves")
    void theTypeIsNormalised() {
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(ORG, "AUDIT_COMMITTEE", "ACTIVE"))
            .thenReturn(List.of(body(3L)));
        when(terms.countCurrentSeatsForUser(anyLong(), any(), anyLong(), any())).thenReturn(1L);

        assertThat(authority.holdsActiveSeat("  audit_committee ", USER, ORG)).isTrue();
    }

    @Test
    @DisplayName("null and blank inputs are refused without touching the database")
    void nullsAreRefusedWithoutQuerying() {
        assertThat(authority.holdsActiveSeat(null, USER, ORG)).isFalse();
        assertThat(authority.holdsActiveSeat("", USER, ORG)).isFalse();
        assertThat(authority.holdsActiveSeat("   ", USER, ORG)).isFalse();
        assertThat(authority.holdsActiveSeat("AUDIT_COMMITTEE", null, ORG)).isFalse();
        assertThat(authority.holdsActiveSeat("AUDIT_COMMITTEE", USER, null)).isFalse();
        verifyNoInteractions(committees, terms);
    }

    @Test
    @DisplayName("a repository failure denies rather than 500-ing the endpoint")
    void aRepositoryFailureDeniesInsteadOfBreakingTheExpression() {
        // This predicate is one leg of an `or`. A propagated exception would turn a refusal into an
        // outage on an endpoint the caller may legitimately reach by role — the promise F2-159's
        // ownership resolvers make, for the same reason.
        when(committees.findByOrganisationIdAndCommitteeTypeAndStatus(anyLong(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("roster query blew up"));

        assertThat(authority.holdsActiveSeat("AUDIT_COMMITTEE", USER, ORG)).isFalse();
    }
}
