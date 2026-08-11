package com.tss.grc.security;

import com.tss.grc.module.intake.service.IntakeKindRouting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-174 — the <b>fourth</b> place in this platform where a role name is written as a bare string,
 * and the first one that is not an authorisation decision.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@code IntakeKindRouting.defaultAssignee} is the value {@code IntakeService#submit} writes to
 * {@code intake.intake_records.assigned_to} — the platform's answer to "who triages this
 * submission?" for all 26 intake kinds, including the statutory ones. Measured 2026-08-11 against
 * both real databases: of the <b>14 distinct codes</b> the catalogue used, <b>10 matched no row in
 * {@code iam.roles}</b> — {@code CISO_OFFICE}, {@code ETHICS_CTE}, {@code CSIRT},
 * {@code RISK_OFFICER}, {@code COMPLIANCE_LEAD}, {@code POLICY_OFFICER}, {@code TPRM_LEAD},
 * {@code BCM_LEAD}, {@code AUDIT_LEAD}, {@code CAB} — and they carried <b>22 of the 26 kinds</b>.
 * Every one of the ten had an obvious real counterpart already seated in the catalogue, which is
 * what made this a naming defect rather than a design gap.
 *
 * <p>The consequence is not a wrong label. {@code assigned_to} is a routing column: a value that
 * names nothing in {@code iam.roles} cannot be resolved to a person by <em>any</em> amount of tenant
 * configuration, because a tenant administrator can only grant roles the catalogue holds. So a
 * submission routed to {@code CSIRT} was, structurally, routed nowhere — and the reader of the
 * record is told otherwise, which is the F2-05 ghost-role failure shape moved out of
 * {@code @PreAuthorize} and into data.
 *
 * <h3>⚠ Two things this test deliberately does not claim</h3>
 *
 * <ol>
 *   <li><b>Naming a real role is not reaching a person.</b> Measured the same day: of the 11 names
 *       the catalogue now uses, only 4 have an active holder in dev ({@code CISO},
 *       {@code COMPLIANCE_OFFICER}, {@code INTERNAL_AUDITOR}, {@code RISK_MANAGER}) and the same 4
 *       in prod. That is the F2-178 caveat exactly — <i>the mechanism works and the data does not
 *       exist yet</i> — and it is now a roster question a tenant administrator can answer from the
 *       Roles screen, which it was not before.</li>
 *   <li><b>Nothing resolves this column yet.</b> {@code assigned_to} is rendered as a label
 *       ({@code IntakeFormPage} interpolates it into the acknowledgement text) and consulted by no
 *       queue, notification or guard. Making the routing actually deliver is separate work, and it
 *       is a precondition for that work rather than a substitute for it.</li>
 * </ol>
 *
 * <h3>Why the catalogue is read from {@link RoleGuardConversionRatchetTest} and not copied again</h3>
 *
 * <p>{@link RoleGuardConversionRatchetTest#ROLE_CATALOGUE} is the one pinned copy of the active role
 * names, read from {@code iam.roles} and maintained under a stated rule: it must be updated in the
 * same commit as the migration that seats a role. {@link RoleNameSurfaceRatchetTest} (surface #2,
 * the Java role lists in {@code AttachmentAccessPolicy}) already reads that same set rather than
 * holding its own, and its comment gives the reason — <i>"a duplicated catalogue is how the census
 * document drifted nineteen ghosts behind the build"</i>. This is the third reader, same rule.
 *
 * <p>Pinned rather than queried because {@code CLAUDE.md} requires {@code mvn -o clean package} to
 * need no database. {@link #everyAssigneeNameIsAlsoWrittenSomewhereInTheMigrations()} adds a second,
 * independent source for exactly these 11 names, and is explicit about what it cannot prove.
 *
 * <h3>Deployment note, measured rather than assumed</h3>
 *
 * <p>{@code ETHICS_OFFICER} — used by {@code SPEAKUP} and {@code ETHICS_DISCLOSURE} — is seated by
 * <b>V362</b>, and the deployed server is at Flyway head V344 with V345–V375 pending (O-1). So on
 * prod today that one name resolves to nothing until the next jar carries its migrations, while the
 * other ten resolve immediately. This is deployment lag on a known open item, not a catalogue
 * defect, and it is stated here because the alternative — quietly routing ethics disclosures to
 * {@code COMPLIANCE_OFFICER} so the number looked clean on an un-migrated database — would have
 * changed who triages a whistleblowing report in order to work around a pending migration.
 */
class IntakeAssigneeRoleResolutionTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    /**
     * The ten retired codes, kept as a fixture rather than only in prose so the write-up cannot
     * drift from the change. They are asserted absent, in both directions with the hard zero below:
     * if one is ever re-introduced it must first be a real role, and if it becomes a real role the
     * hard zero is satisfied and this list is what tells a reader the history.
     */
    private static final Set<String> RETIRED = Set.of(
        "CISO_OFFICE", "ETHICS_CTE", "CSIRT", "RISK_OFFICER", "COMPLIANCE_LEAD",
        "POLICY_OFFICER", "TPRM_LEAD", "BCM_LEAD", "AUDIT_LEAD", "CAB");

    private static Map<String, String> assigneeByKind() {
        Map<String, String> out = new TreeMap<>();
        IntakeKindRouting.all().forEach((kind, r) -> out.put(kind, r.defaultAssignee()));
        return out;
    }

    // ── the invariant ───────────────────────────────────────────────────────────────────────────

    /**
     * A hard zero, not a ratchet, and the population is why: it is 14 codes over 26 kinds, every
     * member was measured, and the remedy for a failure is unambiguous — either the name is wrong or
     * the role needs seating by a migration. {@code CLAUDE.md}'s rule from
     * {@code BASELINE_GHOST_ROLE_NAMES} reaching zero applies: <b>a ceiling tolerates the population
     * it measures</b>, so once a sweep lands, leaving a raisable count in place makes a tolerated
     * value indistinguishable from a reviewed one.
     */
    @Test
    void everyIntakeKindRoutesToARoleThePlatformActuallySeats() {
        Map<String, String> unresolvable = new TreeMap<>();
        assigneeByKind().forEach((kind, assignee) -> {
            if (!RoleGuardConversionRatchetTest.ROLE_CATALOGUE.contains(assignee)) {
                unresolvable.put(kind, assignee);
            }
        });

        System.out.println("[F2-174] intake kinds = " + assigneeByKind().size()
            + " ; distinct assignees = " + new TreeSet<>(assigneeByKind().values())
            + " ; unresolvable = " + unresolvable);

        assertThat(unresolvable)
            .as("Intake kind(s) routed to a name absent from iam.roles. This is not a label problem: "
                + "assigned_to is what the platform offers as the answer to \"who triages this?\", "
                + "and a name the role catalogue does not hold cannot be resolved to a person by any "
                + "tenant configuration — a tenant administrator can only grant roles that exist. "
                + "Either use the real role name, or seat the new role by migration AND pin it in "
                + "RoleGuardConversionRatchetTest.ROLE_CATALOGUE in the same commit. Do not convert "
                + "this to a ratchet: the population is 14 codes and all of them were measured.")
            .isEmpty();
    }

    /**
     * The other direction of the F2-174 change, so a revert cannot pass silently. A code from the
     * retired set can only come back by first becoming a real role — at which point
     * {@link #everyIntakeKindRoutesToARoleThePlatformActuallySeats()} would accept it and this test
     * would still object, on purpose: all ten were measured as having a real counterpart already in
     * the catalogue, so seating a second role that means the same thing is a decision that deserves
     * a failing build and a conversation, not a silent merge.
     */
    @Test
    void noRetiredStakeholderCodeComesBack() {
        Map<String, String> revived = new TreeMap<>();
        assigneeByKind().forEach((kind, assignee) -> {
            if (RETIRED.contains(assignee)) {
                revived.put(kind, assignee);
            }
        });
        assertThat(revived)
            .as("Intake kind(s) routed to a code F2-174 retired. Each of these ten matched no row in "
                + "iam.roles in either dev or prod, and each had a real counterpart already seated: "
                + "CISO_OFFICE->CISO, ETHICS_CTE->ETHICS_OFFICER, CSIRT->IR_LEAD, "
                + "RISK_OFFICER->RISK_MANAGER, COMPLIANCE_LEAD/POLICY_OFFICER->COMPLIANCE_OFFICER, "
                + "TPRM_LEAD->SUPPLIER_MANAGER, BCM_LEAD->BCM_MANAGER, AUDIT_LEAD->INTERNAL_AUDITOR, "
                + "CAB->CHANGE_MANAGER.")
            .isEmpty();
    }

    // ── the instrument ──────────────────────────────────────────────────────────────────────────

    /**
     * Anti-vacuity, as its own test rather than a leading assertion inside the invariant — an
     * earlier failing assertion in the same method masks everything after it, and every collapsed
     * measurement on this programme reported the reassuring answer (F2-170's 1,763 phantom endpoints,
     * F2-184's phantom 75 types, F2-183's 0-of-389). If {@code IntakeKindRouting} were somehow empty,
     * the hard zero above would pass over nothing at all.
     */
    @Test
    void theInstrumentIsNotVacuous() {
        Map<String, String> byKind = assigneeByKind();
        Set<String> distinct = new TreeSet<>(byKind.values());

        assertThat(byKind)
            .as("the intake catalogue resolved almost nothing — the hard zero above is vacuous until "
                + "this passes. 26 kinds were measured for F2-172")
            .hasSizeGreaterThanOrEqualTo(26);
        assertThat(distinct)
            .as("14 distinct assignee codes were measured for F2-174 and the rename collapsed four "
                + "pairs onto shared roles, leaving 11. A collapse to one or two means the field is "
                + "being read wrong, not that routing was simplified")
            .hasSizeGreaterThanOrEqualTo(8);
        assertThat(RoleGuardConversionRatchetTest.ROLE_CATALOGUE)
            .as("the pinned role catalogue is far smaller than the 69 active roles measured — a "
                + "truncated catalogue makes the hard zero above fail loudly rather than silently, "
                + "but it would also make this test the only place the truncation is visible")
            .hasSizeGreaterThanOrEqualTo(50);
        assertThat(RETIRED)
            .as("noRetiredStakeholderCodeComesBack() is only as strong as this list, and emptying it "
                + "is the one edit that makes that test pass over anything. Ten codes were retired")
            .hasSize(10);

        // Every kind must carry a non-blank assignee. A null would make the contains() check above
        // fail rather than pass, but it would fail for the wrong reason and read as a naming defect.
        byKind.forEach((kind, assignee) -> assertThat(assignee)
            .as("%s has no default assignee — IntakeKindRouting.Routing makes a partially described "
                + "kind unrepresentable, so this can only mean an empty string was written", kind)
            .isNotBlank());
    }

    /**
     * A <b>second, independent</b> source for the eleven names: each must appear as a quoted literal
     * somewhere in {@code db/migration/sqlserver/}.
     *
     * <p><b>What this proves:</b> the name was not invented in Java. The failure it catches is
     * specific and realistic — the pinned {@code ROLE_CATALOGUE} is a copy, and the commit that adds
     * an intake assignee is exactly the commit that would add the same plausible-but-fictional name
     * to that copy. Two edits by one author in one commit is not two sources. F2-177's lesson stated
     * the general form: <i>what made a drift harmless was two independent lists agreeing, and nobody
     * was maintaining that.</i>
     *
     * <p><b>What this does not prove:</b> that the migration <em>seats</em> the role. A name also
     * appears in grant statements, permission joins and comments, and the role-seeding
     * {@code INSERT INTO iam.roles} statements are spread across a dozen migrations with four
     * different column orders — deriving the catalogue from them by regex is the phantom-count
     * mistake this repository has now made four times. So this is deliberately the weaker claim,
     * stated as such, and {@link #everyIntakeKindRoutesToARoleThePlatformActuallySeats()} remains
     * the assertion that matters.
     */
    @Test
    void everyAssigneeNameIsAlsoWrittenSomewhereInTheMigrations() throws IOException {
        assertThat(Files.isDirectory(MIGRATIONS))
            .as("migrations not found at %s — this test is vacuous without them",
                MIGRATIONS.toAbsolutePath())
            .isTrue();

        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                all.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).append('\n');
            }
        }
        String sql = all.toString();
        assertThat(sql.length())
            .as("the migration corpus read as almost nothing — the check below would clear every "
                + "name for the wrong reason")
            .isGreaterThan(500_000);

        List<String> unmentioned = new TreeSet<>(assigneeByKind().values()).stream()
            .filter(name -> !sql.contains("'" + name + "'"))
            .toList();

        assertThat(unmentioned)
            .as("Assignee role name(s) that appear nowhere in the migration corpus. Every role the "
                + "platform ships is created by a migration, so a name absent from all of them was "
                + "typed into Java — and if it was also typed into ROLE_CATALOGUE in the same commit, "
                + "the hard zero above cannot see it. Seat the role by migration, or use a name that "
                + "is already seated.")
            .isEmpty();
    }
}
