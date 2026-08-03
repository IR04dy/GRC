package com.tss.grc.security;

import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S6 — {@code cab_profile} and {@code cab_member_role} are <b>subsumed by the generic committee
 * engine and must not be built</b>. Do not "finish S6" by creating them.
 *
 * <p>The S6 row names five deliverables — {@code cab_profile}, {@code cab_member_role}, charter/ToR,
 * term lifecycle and quorum policy. S6a already found the middle three shipped in the generic engine.
 * This test pins the other two, because a plan row listing an unbuilt table is a standing invitation
 * to build it, and the result would be a second home for facts the platform already stores.
 *
 * <p><b>The measurement, taken in dev and production on 2026-08-03 against
 * {@code INFORMATION_SCHEMA}, not against the plan text:</b>
 *
 * <pre>
 *   cab_profile      15 spec fields → 12 already exist   absent: scope_json,
 *                                                                quorum_requires_chair,
 *                                                                notice_period_hours
 *   cab_member_role   7 spec fields →  6 already exist   absent: delegate_for_member_id
 * </pre>
 *
 * <p>Every claimed home was verified present in both databases (0 wrong claims), so the mapping is
 * not wishful. And {@code quorum_requires_chair} — the one absent field that belonged to S6 rather
 * than to a later slice — shipped as a column on {@code admin.committees} in <b>V348</b>, which is
 * the point: it needed a flag, not a table.
 *
 * <p><b>What moved rather than being dropped.</b> {@code scope_json} and {@code notice_period_hours}
 * are consumed only by <b>S7</b>'s agenda build (candidate scope filtering, and the notice-period
 * warning on publication), so they belong with their consumers. {@code delegate_for_member_id} is a
 * standing-delegation feature; the platform already supports delegation <em>by authority</em> —
 * {@code recordVote} accepts a vote on a named member's behalf only from a secretary or chair — so a
 * standing appointment is an addition, not a gap in S6.
 *
 * <p><b>To build them for real:</b> amend the S6 row with the field that has no home and delete this
 * test in the same commit. That is the intended way through, and it forces the duplication question
 * to be answered rather than drifted past — the same instrument that stopped S5b from reversing
 * ADR-017 D-4.
 */
class CabGovernedBodySubsumptionTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    private static List<Path> filesUnder(Path root, String suffix) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Does the entity declare a field of this name? Reflection, so a rename fails here loudly. */
    private static boolean hasField(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(f -> f.getName().equals(name));
    }

    @Test
    @DisplayName("neither table has been created — no migration and no entity")
    void theDuplicateTablesDoNotExist() {
        List<String> offenders = new ArrayList<>();

        for (Path m : filesUnder(MIGRATIONS, ".sql")) {
            String sql = read(m).toLowerCase();
            // A migration may *mention* the names in a comment (this project's migrations explain
            // themselves at length); only a CREATE TABLE counts.
            if (sql.matches("(?s).*create\\s+table\\s+[^;]*\\bcab_profile\\b.*"))
                offenders.add(m.getFileName() + " creates cab_profile");
            if (sql.matches("(?s).*create\\s+table\\s+[^;]*\\bcab_member_role\\b.*"))
                offenders.add(m.getFileName() + " creates cab_member_role");
        }
        for (Path j : filesUnder(MAIN, ".java")) {
            String src = read(j);
            if (src.contains("@Table(name = \"cab_profile\"") || src.contains("@Table(name=\"cab_profile\""))
                offenders.add(j.getFileName() + " maps cab_profile");
            if (src.contains("@Table(name = \"cab_member_role\"") || src.contains("@Table(name=\"cab_member_role\""))
                offenders.add(j.getFileName() + " maps cab_member_role");
        }

        assertThat(offenders)
            .as("""
                S6's cab_profile / cab_member_role are subsumed by the generic committee engine — \
                12 of 15 and 6 of 7 spec fields already exist, measured in both databases. Creating \
                them gives the platform two homes for the same governance facts. If a field \
                genuinely has no home, amend the S6 row and delete this test in the same commit:

                %s""", offenders)
            .isEmpty();

        // Anti-vacuity: an empty scan would make the above pass for the wrong reason.
        assertThat(filesUnder(MIGRATIONS, ".sql")).hasSizeGreaterThan(300);
        assertThat(filesUnder(MAIN, ".java")).hasSizeGreaterThan(1000);
    }

    @Test
    @DisplayName("cab_profile's fields live on the generic committee, its gate map and the change matrix")
    void cabProfileIsSubsumed() {
        // charter / ToR / cadence / review cadence / status — Committee
        assertThat(hasField(Committee.class, "charterEn")).isTrue();
        assertThat(hasField(Committee.class, "charterAr")).isTrue();
        assertThat(hasField(Committee.class, "charterTemplateCode")).as("terms_of_reference").isTrue();
        assertThat(hasField(Committee.class, "meetingCadence")).as("standing_cadence").isTrue();
        assertThat(hasField(Committee.class, "nextCharterReviewAt")).as("review_due_date").isTrue();
        assertThat(hasField(Committee.class, "status")).isTrue();

        // quorum policy — the percentage was already live (F2-28/V319); S6c added the chair clause
        // as a COLUMN, which is the whole argument of this test in one line.
        assertThat(hasField(Committee.class, "quorumPct")).as("quorum_min_members").isTrue();
        assertThat(hasField(Committee.class, "quorumRequiresChair"))
            .as("V348 — the one genuinely-absent S6 field, shipped as a flag rather than a table")
            .isTrue();

        // chair / secretary identity — the roster's seat title, classified once
        assertThat(hasField(CommitteeMemberTerm.class, "role"))
            .as("chair_member_id / secretary_member_id").isTrue();

        // min_approvals lives per change type × risk, which is STRICTER than per-committee: the
        // authorisation matrix can demand three approvals for a CRITICAL change and one for a LOW
        // one through the same CAB. A cab_profile column could not express that.
        assertThat(hasField(ChangeAuthorisationMatrix.class, "minApprovals")).isTrue();
    }

    @Test
    @DisplayName("cab_member_role's fields all live on committee_member_terms")
    void cabMemberRoleIsSubsumed() {
        Set<String> present = Arrays.stream(CommitteeMemberTerm.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName).collect(Collectors.toSet());
        assertThat(present).contains(
            "committeeId",       // cab_profile_id
            "memberUserId",      // member_id — and F2-26's linkage, which is what made F2-95
                                 //             enforceable WITHOUT this table
            "role",              // role
            "isVotingMember",    // voting
            "termStartsAt",      // from_date
            "termEndsAt");       // to_date
    }

    @Test
    @DisplayName("the seat-office classifier is the single home for reading a governance title")
    void oneHomeForTheChairPredicate() {
        // F2-62's rule: one predicate, every consumer. If a second place starts matching
        // '%CHAIR%' against role text, the two will disagree the first time a tenant types
        // "Chairman" — and one of them will be the one that decides quorum.
        List<String> adhoc = new ArrayList<>();
        for (Path j : filesUnder(MAIN, ".java")) {
            if (j.getFileName().toString().equals("CommitteeSeatOffice.java")) continue;
            String src = read(j).replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
            if (src.matches("(?s).*(equalsIgnoreCase|contains|matches)\\s*\\(\\s*\"(?i:chair)[^\"]*\"\\s*\\).*")
                    || src.contains("\"%CHAIR%\"") || src.contains("'%CHAIR%'"))
                adhoc.add(MAIN.relativize(j).toString());
        }
        assertThat(adhoc)
            .as("""
                These files test a seat title against a chair literal directly instead of going \
                through CommitteeSeatOffice.classify(). Two predicates disagree the first time a \
                tenant writes "Chairman" — and a substring match also accepts "Vice-Chair", which \
                is a different office:

                %s""", adhoc)
            .isEmpty();
    }
}
