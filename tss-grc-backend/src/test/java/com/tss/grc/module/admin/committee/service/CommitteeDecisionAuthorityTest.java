package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7a — the decision-mode statement and the outcome vocabulary, both pinned.
 *
 * <p>S7's row requires the platform to state which decision mode is authoritative per change type.
 * {@link CommitteeDecisionAuthority} is that statement; these tests stop it drifting from the
 * schema it describes, which is the F2-91 lesson: the committee-type vocabulary was stated three
 * times, wrong twice, and the constant that disagreed with its CHECK constraint turned a create
 * into a 500.
 */
class CommitteeDecisionAuthorityTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    private static List<Path> migrations() {
        try (Stream<Path> walk = Files.walk(MIGRATIONS)) {
            return walk.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("PROPOSED is undecided; the four V154 values are decided")
    void theVocabularySplit() {
        assertThat(CommitteeDecisionAuthority.isDecided("PROPOSED")).isFalse();
        for (String decided : List.of("APPROVED", "REJECTED", "DEFERRED", "WITHDRAWN"))
            assertThat(CommitteeDecisionAuthority.isDecided(decided)).as(decided).isTrue();

        assertThat(CommitteeDecisionAuthority.ALL)
            .containsExactlyInAnyOrder("PROPOSED", "APPROVED", "REJECTED", "DEFERRED", "WITHDRAWN");
        assertThat(CommitteeDecisionAuthority.DECIDED)
            .doesNotContain(CommitteeDecisionAuthority.PROPOSED);
    }

    @Test
    @DisplayName("an unreadable or absent outcome is UNDECIDED, never decided")
    void unknownOutcomesAreNotTreatedAsDecisions() {
        // The permissive direction is the dangerous one: a row whose outcome cannot be read must
        // not be reported as carrying a verdict, or the referral gate would let a change advance
        // on a decision nobody took. Same stance as CommitteeSeatOffice.UNCLASSIFIED.
        for (String bad : List.of("", "   ", "CARRIED", "approved_pending", "PROPOSED "))
            assertThat(CommitteeDecisionAuthority.isDecided(bad)).as("isDecided(\"%s\")", bad).isFalse();
        assertThat(CommitteeDecisionAuthority.isDecided((String) null)).isFalse();
        assertThat(CommitteeDecisionAuthority.isDecided((CommitteeResolution) null)).isFalse();
        // …but case and padding on a REAL value must still read as decided, because the service
        // upper-cases on the way in and a stored row may pre-date that.
        assertThat(CommitteeDecisionAuthority.isDecided(" approved ")).isTrue();
    }

    @Test
    @DisplayName("binding = decided AND minutes approved — and a missing meeting is not binding")
    void bindingRequiresApprovedMinutes() {
        CommitteeResolution decided = new CommitteeResolution();
        decided.setOutcome("APPROVED");
        CommitteeResolution raised = new CommitteeResolution();
        raised.setOutcome(CommitteeDecisionAuthority.PROPOSED);

        CommitteeMeeting approved = new CommitteeMeeting();
        approved.setStatus("MINUTES_APPROVED");
        CommitteeMeeting completed = new CommitteeMeeting();
        completed.setStatus("COMPLETED");

        assertThat(CommitteeDecisionAuthority.isBinding(decided, approved)).isTrue();
        assertThat(CommitteeDecisionAuthority.isBinding(decided, completed)).isFalse();
        assertThat(CommitteeDecisionAuthority.isBinding(raised, approved)).isFalse();
        // Fail closed: the worst case is a decision that is not announced, never one announced
        // without approved minutes.
        assertThat(CommitteeDecisionAuthority.isBinding(decided, null)).isFalse();
        assertThat(CommitteeDecisionAuthority.isBinding(null, approved)).isFalse();
    }

    @Test
    @DisplayName("the Java vocabulary matches what the migrations actually declare")
    void javaMatchesTheCheckConstraint() {
        // Find the LAST migration that defines CK_resolution_outcome and read the values out of
        // its CHECK. Comparing against the newest definition is the point: V154 declared four and
        // V349 widened it to five, so a test pinned to the first would pass forever while the
        // constant and the database drifted apart.
        String latest = null;
        String from = null;
        for (Path m : migrations()) {
            String sql = read(m);
            java.util.regex.Matcher mt = java.util.regex.Pattern
                .compile("CONSTRAINT\\s+CK_resolution_outcome\\s+CHECK\\s*\\(\\s*outcome\\s+IN\\s*\\(([^)]*)\\)",
                         java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(sql);
            while (mt.find()) { latest = mt.group(1); from = m.getFileName().toString(); }
        }
        assertThat(latest).as("no migration declares CK_resolution_outcome — the scan is broken, "
            + "not the code").isNotNull();

        Set<String> declared = new java.util.LinkedHashSet<>();
        for (String tok : latest.split(",")) {
            String v = tok.trim().replaceAll("^[Nn]?'", "").replaceAll("'$", "").trim();
            if (!v.isEmpty()) declared.add(v.toUpperCase(Locale.ROOT));
        }

        assertThat(declared)
            .as("""
                CommitteeDecisionAuthority.ALL and %s disagree about the resolution outcome \
                vocabulary. F2-91: a constant that disagrees with its CHECK constraint turns a \
                create into a 500. Update both, in the same commit.""", from)
            .isEqualTo(CommitteeDecisionAuthority.ALL);
    }

    @Test
    @DisplayName("no other committee file re-derives 'is this outcome decided' with its own list")
    void oneHomeForTheDecidedPredicate() {
        // F2-62's rule. A second list of decided outcomes disagrees the first time a value is
        // added — and V349 has just added one, so this is the moment the second list appears.
        //
        // ⚠ SCOPED TO THE COMMITTEE MODULE ON PURPOSE — do not widen it. The first draft scanned
        // all of src/main/java and reported five "offenders", every one a false positive:
        // APPROVED / REJECTED / DEFERRED are ordinary status words, and AiLifecycleService,
        // ApprovalRequestService, AuditLifecycleService, ContinuityStrategyService and
        // StrategyRoiService each own an unrelated state machine that happens to use them. That
        // detector tested a word coincidence, not a contract — the same failure as a regex that
        // matches too little, in the opposite direction. What actually needs guarding is narrow
        // and measurable: exactly three files read CommitteeResolution.getOutcome(), and all
        // three live here.
        List<String> adhoc = new ArrayList<>();
        Path main = Path.of("src/main/java/com/tss/grc/module/admin/committee");
        try (Stream<Path> walk = Files.walk(main)) {
            for (Path j : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = j.getFileName().toString();
                if (name.equals("CommitteeDecisionAuthority.java")) continue;
                // Strip comments — the migrations and javadoc in this module quote the vocabulary
                // at length by design, and a doc comment is not a second predicate.
                String src = read(j).replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
                // A literal list of outcomes: two or more of the four decided values inside one
                // Set.of(...) / List.of(...) call.
                java.util.regex.Matcher mt = java.util.regex.Pattern
                    .compile("(?:Set|List)\\.(?:<String>)?of\\(([^)]*)\\)").matcher(src);
                while (mt.find()) {
                    String body = mt.group(1);
                    long hits = List.of("\"APPROVED\"", "\"REJECTED\"", "\"DEFERRED\"", "\"WITHDRAWN\"")
                        .stream().filter(body::contains).count();
                    if (hits >= 2) adhoc.add(main.relativize(j) + " → " + body.trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(adhoc)
            .as("""
                These committee files carry their own list of resolution outcomes instead of \
                asking CommitteeDecisionAuthority. Two lists disagree the first time the \
                vocabulary changes — which V349 just did:

                %s""", adhoc)
            .isEmpty();

        // Anti-vacuity: a mistyped path would make the scan empty and the assertion above
        // trivially true, which is how a guard becomes decoration.
        assertThat(Path.of("src/main/java/com/tss/grc/module/admin/committee")).exists();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java/com/tss/grc/module/admin/committee"))) {
            assertThat(walk.filter(p -> p.toString().endsWith(".java")).count())
                .as("the committee module should hold dozens of files, not a handful")
                .isGreaterThan(30);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
