package com.tss.grc.module.admin.committee.dto;

import java.util.List;

/**
 * Tenant-wide roster-linkage coverage — every committee's seat linkage in one call (F2-178).
 *
 * <p><b>Why this exists when {@link SeatLinkageReport} already did.</b> F2-26 built the per-committee
 * report and wired it to the member-registry screen, so the gap has been visible <em>one committee at a
 * time</em> since then. That is enough to fix a roster you are already looking at and useless for finding
 * out that you have a problem: production has <b>0 of 31 seats linked</b> and discovering it requires
 * opening seventeen registry pages in turn. F2-177 made the stakes concrete by putting an authorisation
 * decision on the same column — a committee whose roster nobody has linked now silently refuses access to
 * the assurance views its members are the intended audience for.
 *
 * <p><b>Three states, not two, and the third is the one a per-committee report cannot show you.</b>
 * A committee with unlinked seats has a roster and a data-entry task. A committee with <b>no roster at
 * all</b> has neither: it cannot be quorate, no resolution it records would be valid, and
 * {@code ChangeGovernanceHealthService} already refuses CAB approvals for exactly that state. Measured
 * 2026-08-09: <b>9 of 17</b> dev committees and <b>11 of 17</b> production committees have no term rows
 * whatsoever. Rolling those in as "0% covered" would file them next to a committee that is 80% linked,
 * which understates them; {@link Row#hasRoster()} names the state instead.
 *
 * <p><b>DISSOLVED committees are excluded, and that is the same decision F2-177 made.</b> A dissolved body
 * confers no seat — {@code CommitteeSeatAuthority} only looks at ACTIVE committees — so listing its
 * roster gaps would add a permanently red row nobody may act on. The count of what was excluded is
 * reported ({@link #dissolvedExcluded()}) rather than left to be inferred from a short list.
 *
 * @param committees            one row per live committee, ordered so the worst offenders read first
 * @param totalCommittees       live (non-DISSOLVED) committees in this tenant
 * @param committeesWithRoster  of those, how many have at least one ACTIVE term
 * @param committeesFullyLinked how many have every linkable seat linked (external seats excluded, as in
 *                              {@link SeatLinkageReport})
 * @param committeesAdmittingNobody live committees where the F2-177 seat guard admits <b>no</b> platform
 *                              user — the number that says how much of the tenant's assurance surface is
 *                              closed. Includes the empty-roster committees, because an empty body admits
 *                              nobody for a different reason and the consequence is identical
 * @param totalSeats            ACTIVE seats across live committees
 * @param totalLinked           of those, how many name a platform user
 * @param totalDeclaredExternal how many are declared to have no platform login
 * @param totalUnlinked         how many are neither — the tenant's data-entry backlog
 * @param totalCurrentLinked    how many linked seats are also inside their term window, i.e. actually
 *                              confer access today. Lower than {@code totalLinked} means seats have
 *                              lapsed while still reading ACTIVE
 * @param dissolvedExcluded     DISSOLVED committees left out of every figure above
 */
public record RosterCoverageReport(
        List<Row> committees,
        long totalCommittees,
        long committeesWithRoster,
        long committeesFullyLinked,
        long committeesAdmittingNobody,
        long totalSeats,
        long totalLinked,
        long totalDeclaredExternal,
        long totalUnlinked,
        long totalCurrentLinked,
        long dissolvedExcluded
) {

    /**
     * One committee's coverage.
     *
     * @param committeeId    the committee, so the UI can link straight to its registry screen
     * @param code           registry code (COM-AUDIT, …) as it appears on the committees list
     * @param nameEn         English name
     * @param nameAr         Arabic name — carried because every user-visible record in this platform is
     *                       bilingual, and a report the Arabic UI has to fall back to English for is a
     *                       report that reads as half-built
     * @param committeeType  the {@code committee_type} value, which is also the argument a
     *                       {@code hasPermission(type,'COMMITTEE','SEAT')} guard names — so a reader can
     *                       tell which guarded screens this row is the explanation for
     * @param status         ACTIVE | INACTIVE (DISSOLVED never appears)
     * @param hasRoster      whether any ACTIVE term exists. {@code false} is a worse state than 0% linked
     * @param seats          ACTIVE seats
     * @param linked         seats naming a platform user
     * @param declaredExternal seats declared to have no platform login
     * @param unlinked       seats that are neither
     * @param votingSeats    seats carrying a counted vote
     * @param votingLinked   of those, how many can cast their own vote (F2-26's operational number)
     * @param currentSeats   seats whose term window contains today and which have not been ended
     * @param currentLinked  of those, how many name a platform user — <b>whether the seat guard admits
     *                       anybody here</b>
     * @param coveragePct    linked / (seats − declaredExternal); 0 when there is nothing linkable
     */
    public record Row(
            Long    committeeId,
            String  code,
            String  nameEn,
            String  nameAr,
            String  committeeType,
            String  status,
            boolean hasRoster,
            long    seats,
            long    linked,
            long    declaredExternal,
            long    unlinked,
            long    votingSeats,
            long    votingLinked,
            long    currentSeats,
            long    currentLinked,
            int     coveragePct
    ) {
        /** Does F2-177's seat guard admit anybody on this committee today? */
        public boolean admitsNobody() { return currentLinked == 0; }
    }
}
