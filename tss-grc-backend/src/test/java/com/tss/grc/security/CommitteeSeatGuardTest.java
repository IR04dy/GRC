package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-177 — where a committee seat authorises, and the vocabulary it may name.
 *
 * <h3>The defect this must not let recur, in the mechanism built to end it</h3>
 * <p>F2-52's whole finding is that {@code hasAnyRole('AUDIT_COMMITTEE_CHAIR')} is a permanently false OR
 * leg, because nothing validates the string. {@code hasPermission('AUDIT_COMMITTE','COMMITTEE','SEAT')}
 * — note the typo — fails in exactly the same way and just as silently: the lookup finds no committee of
 * that type, the leg is false, and the guard is tighter than it reads. <b>Replacing an unvalidated string
 * with a different unvalidated string is not a fix</b>, so the committee-type vocabulary is pinned here
 * the same way {@code RoleGuardConversionRatchetTest.ROLE_CATALOGUE} pins role names, and for the same
 * hermetic reason: the suite has no database.
 *
 * <p>⚠ Unlike the role catalogue, this one demands <b>zero</b> unknown names rather than ratcheting down
 * from a population. There is no legacy here — the mechanism is new, so the only reason a guard would name
 * an absent type is a mistake, and a ratchet that tolerates the first one is how 40 ghost role names
 * accumulated.
 */
class CommitteeSeatGuardTest {

    /** {@code hasPermission('<TYPE>', 'COMMITTEE', 'SEAT')} — any spacing, single or double quotes. */
    private static final Pattern SEAT_LEG = Pattern.compile(
        "hasPermission\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]COMMITTEE['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    /**
     * {@code admin.committees.committee_type} as measured in dev on 2026-08-09 — 17 distinct values over
     * 19 rows. Pinned rather than queried so the unit suite stays database-free, exactly as
     * {@code CLAUDE.md} requires.
     *
     * <p><b>Not the authority on what a tenant may constitute</b> — a customer can create a committee of
     * any type the enum allows. It is the authority on what a <em>guard</em> may name, which is a
     * different and much smaller question: a guard is written once, ships in a jar, and must name a type
     * that exists on every tenant's platform.
     */
    private static final Set<String> COMMITTEE_TYPES = Set.of(
        "AI_ETHICS", "ANTI_FRAUD", "AUDIT_COMMITTEE", "BCM_COMMITTEE", "BOARD",
        "COMPLIANCE_COMMITTEE", "CRISIS_MANAGEMENT", "CYBER_SECURITY", "DATA_GOVERNANCE",
        "ESG_SUSTAINABILITY", "EXEC_COMMITTEE", "INTERNAL_CONTROL", "IT_STEERING",
        "NOMINATION_REMUNERATION", "PRIVACY_DPO", "RISK_COMMITTEE", "TECH_COMMITTEE");

    /**
     * The six sites. All reads, all cross-module assurance views the audit committee exists to review, and
     * every one of them named a seat as a role name until F2-177.
     */
    private static final Set<String> MUST_ADMIT_A_SEAT = Set.of(
        "RiskControlFindingPivotController#byRisk",
        "RiskControlFindingPivotController#byControl",
        "RiskControlFindingPivotController#byFinding",
        "ThreeLinesController#overview",
        "ThreeLinesController#drillDown",
        "ThreeLinesController#userBand");

    /**
     * ⚠ The boundary, and it is the same one {@code MlModelOwnershipGuardTest} draws: being seated on the
     * committee that <em>reviews</em> the three-lines model is not a mandate to <em>redefine</em> it.
     * {@code upsertMapping} re-bands a role platform-wide and stays {@code PLATFORM_ADMIN} /
     * {@code GRC_ADMINISTRATOR}. A ratchet that only ever counts role legs down invites binding
     * everything; this says where to stop.
     */
    private static final Set<String> MUST_NOT_ADMIT_A_SEAT = Set.of(
        "ThreeLinesController#upsertMapping");

    /** The three names F2-52 could not remove by seating, dropping or converting. */
    private static final Set<String> RETIRED_GHOSTS = Set.of(
        "AUDIT_COMMITTEE_CHAIR", "AUDIT_COMMITTEE_MEMBER", "AUDIT_DIRECTOR");

    private static TreeMap<String, String> guards(String... packages) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        TreeMap<String, String> out = new TreeMap<>();
        for (String pkg : packages) {
            for (var bd : scanner.findCandidateComponents(pkg)) {
                Class<?> c;
                try {
                    c = Class.forName(bd.getBeanClassName());
                } catch (Throwable t) {                                              // NOPMD
                    continue;
                }
                for (Method m : c.getDeclaredMethods()) {
                    PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                    if (pa != null) out.put(c.getSimpleName() + "#" + m.getName(), pa.value());
                }
            }
        }
        return out;
    }

    @Test
    void theSixAssuranceViewsAdmitASeatedAuditCommitteeAndTheMutationDoesNot() {
        TreeMap<String, String> found = guards("com.tss.grc.module.governance");
        assertThat(found.size())
            .as("scanned %d guarded methods under module.governance; the two controllers carry 7. "
                + "A check that cannot find its subject passes silently.", found.size())
            .isGreaterThanOrEqualTo(7);

        List<String> problems = new ArrayList<>();
        for (String site : MUST_ADMIT_A_SEAT) {
            String g = found.get(site);
            if (g == null) {
                problems.add(site + " — NOT FOUND (renamed or removed; this test must follow it)");
            } else if (!SEAT_LEG.matcher(g).find()) {
                problems.add(site + " — no seat leg; guard is: " + g);
            }
        }
        assertThat(problems).as("""
            These views exist for assurance oversight — the pivot's own javadoc says \
            "audit-committee preparation" — and until F2-177 the audit committee could not open them, \
            because the guard named AUDIT_COMMITTEE_CHAIR and AUDIT_COMMITTEE_MEMBER and neither is a row \
            in iam.roles. Offenders: %s""".formatted(problems)).isEmpty();

        List<String> overreach = new ArrayList<>();
        for (String site : MUST_NOT_ADMIT_A_SEAT) {
            String g = found.get(site);
            if (g == null) {
                overreach.add(site + " — NOT FOUND (a missing subject is a silent pass)");
            } else if (SEAT_LEG.matcher(g).find()) {
                overreach.add(site + " -> " + g);
            }
        }
        assertThat(overreach).as("""
            Reviewing the three-lines model is not authority to redefine it. Re-banding a role changes \
            what every artefact in the platform counts as, for every tenant user. Offenders: %s"""
            .formatted(overreach)).isEmpty();
    }

    @Test
    void noGuardAnywhereStillNamesOneOfTheThreeRetiredSeatNames() {
        // Platform-wide, not just these two controllers: the point of F2-52's ghost baseline reaching 0 is
        // that the names are gone from the authorisation surface, and a new controller reciting one would
        // reopen the defect somewhere this file is not looking.
        TreeMap<String, String> all = guards("com.tss.grc.module");
        assertThat(all.size()).as("collapsed scan: %d guarded methods", all.size())
            .isGreaterThanOrEqualTo(1000);

        List<String> offenders = new ArrayList<>();
        all.forEach((site, guard) -> RETIRED_GHOSTS.stream()
            .filter(guard::contains)
            .forEach(ghost -> offenders.add(site + " recites " + ghost + ": " + guard)));

        assertThat(offenders).as("""
            These three names match no row in iam.roles and never did, so each is a permanently false OR \
            leg that makes a guard stricter than it reads. They are not typos for a role that exists — \
            they are committee seats, and hasPermission('<TYPE>','COMMITTEE','SEAT') is how to ask for \
            one. Offenders: %s""".formatted(offenders)).isEmpty();
    }

    @Test
    void everySeatLegNamesACommitteeTypeThatExistsAndTheOnlyRelationIsSEAT() {
        TreeMap<String, String> all = guards("com.tss.grc.module");

        List<String> legs = new ArrayList<>();
        List<String> badType = new ArrayList<>();
        List<String> badRelation = new ArrayList<>();
        all.forEach((site, guard) -> {
            Matcher m = SEAT_LEG.matcher(guard);
            while (m.find()) {
                legs.add(site + " -> " + m.group(1) + "/" + m.group(2));
                if (!COMMITTEE_TYPES.contains(m.group(1).toUpperCase())) {
                    badType.add(site + " names committee type '" + m.group(1) + "'");
                }
                if (!CommitteeSeatDirectory.SEAT_RELATION.equals(m.group(2).toUpperCase())) {
                    badRelation.add(site + " asks for relation '" + m.group(2) + "'");
                }
            }
        });

        System.out.println("[F2-177] committee-seat guard legs = " + legs.size() + " " + legs);

        // Anti-vacuity: a regex that matches nothing reports a perfectly clean surface.
        assertThat(legs.size())
            .as("no seat legs found at all — the matcher has stopped seeing the expression it judges, "
                + "which is the failure mode that shipped a vacuous guard twice in F2-172")
            .isGreaterThanOrEqualTo(MUST_ADMIT_A_SEAT.size());

        assertThat(badType).as("""
            A committee type absent from admin.committees resolves to no body, so the leg is permanently \
            false — F2-52's defect, rebuilt in its own replacement. Correct the spelling, or add the type \
            to this test's pinned vocabulary in the same commit as the migration that introduces it. \
            Offenders: %s""".formatted(badType)).isEmpty();

        assertThat(badRelation).as("""
            SEAT is the only relation implemented. A guard asking for CHAIR would be refused by \
            ModulePermissionEvaluator and answer 403 for everyone including the chair — add the relation \
            to CommitteeSeatDirectory in the commit that first needs it. Offenders: %s"""
            .formatted(badRelation)).isEmpty();
    }
}
