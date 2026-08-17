package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeAuthorisationMatrixRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CE-13 — the CAB queue, and the three populations it must never merge.
 *
 * <h3>What these tests are for</h3>
 * The workspace's whole value is that it distinguishes <b>a change nobody referred</b> from <b>a
 * referral nobody can table</b> from <b>an item the committee has</b>. Merge any two and the number it
 * publishes is unactionable: "3 items in the CAB queue" says nothing about whether the remedy is to
 * refer a change, clear a dead row, or rebuild an agenda. That is F2-94's distinction on its third
 * outing, and the reason it keeps recurring is that every one of these failures reads, from a distance,
 * like the same failure.
 *
 * <p>Measured on dev before the slice: 2 changes at {@code CAB_PENDING} with no referral, 2 open
 * referrals naming change ids that exist in no organisation, and a CAB sitting whose agenda names no
 * change at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeCabQueueTest {

    private static final Long ORG = 1L;
    private static final Path SRC = Path.of("src/main/java/com/tss/grc/module");

    @Mock private ChangeRequestRepository changeRepo;
    @Mock private ChangeAuthorisationMatrixRepository matrixRepo;
    @Mock private CommitteePort committeePort;

    private ChangeCabQueueService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new ChangeCabQueueService(changeRepo, matrixRepo, committeePort);
        when(matrixRepo.findMatrixRanked(anyLong()))
            .thenReturn(List.of(cell("NORMAL", "HIGH", "CAB", 2), cell("EMERGENCY", "HIGH", "ECAB", 1)));
        when(committeePort.gateMap()).thenReturn(List.of(
            gateRow("CAB", 13L, "IT Steering Committee", true, null),
            gateRow("ECAB", 12L, "Executive Committee", true, null)));
        when(committeePort.queue(anyLong())).thenReturn(CommitteePort.CommitteeQueue.empty());
        when(committeePort.nextSitting(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── the three populations ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a change at CAB_PENDING with no referral is NOT_REFERRED, not 'in the queue'")
    void unreferredChangeIsItsOwnFinding() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-2025-0004", "NORMAL", "HIGH", "CAB_PENDING")));

        ChangeCabQueueService.CabQueue q = service.queue();

        assertThat(q.notReferred())
            .as("CAB_PENDING is a status the change wears; referral is what tells the committee. "
              + "Nothing in transition() refers a change, so the two can disagree indefinitely.")
            .isEqualTo(1);
        assertThat(cab(q).awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .isEqualTo(ChangeCabQueueService.Pending.NOT_REFERRED);
        assertThat(cab(q).queue()).isEmpty();
    }

    @Test
    @DisplayName("a referral whose change does not exist is SUBJECT_NOT_FOUND — an orphan, not a queue item")
    void orphanReferralIsReportedNotCounted() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of());
        when(changeRepo.findById(101L)).thenReturn(Optional.empty());
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(referral(7L, 101L, "CHG-GONE")), List.of()));

        ChangeCabQueueService.CabQueue q = service.queue();

        assertThat(q.orphans()).isEqualTo(1);
        assertThat(cab(q).queue()).singleElement()
            .satisfies(r -> {
                assertThat(r.subjectState()).isEqualTo(ChangeCabQueueService.Row.SUBJECT_NOT_FOUND);
                assertThat(r.changeStatus())
                    .as("there is no change to have a status; a fabricated one would make the row "
                      + "look tableable")
                    .isNull();
            });
    }

    @Test
    @DisplayName("SUBJECT_NOT_FOUND beats SUBJECT_NOT_STATED when a referral is both")
    void theMoreActionableStateWins() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of());
        when(changeRepo.findById(102L)).thenReturn(Optional.empty());
        // Unstated AND orphaned — dev's two rows are exactly this.
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(), List.of(referral(8L, 102L, null))));

        assertThat(cab(service.queue()).queue()).singleElement()
            .extracting(ChangeCabQueueService.Row::subjectState)
            .as("a subject snapshot cannot be backfilled for a row with no subject; reporting the "
              + "softer state first sends someone to write a label for a change that does not exist")
            .isEqualTo(ChangeCabQueueService.Row.SUBJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("an unstated referral whose change DOES exist stays SUBJECT_NOT_STATED")
    void unstatedButRealIsALabelDefect() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of());
        when(changeRepo.findById(5L)).thenReturn(Optional.of(change(5L, "CHG-5", "NORMAL", "HIGH", "CAB_PENDING")));
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(), List.of(referral(9L, 5L, null))));

        assertThat(cab(service.queue()).queue()).singleElement()
            .extracting(ChangeCabQueueService.Row::subjectState)
            .isEqualTo(ChangeCabQueueService.Row.SUBJECT_NOT_STATED);
    }

    // ── the ladder ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unmapped gate is reported ahead of anything else — nothing can be referred")
    void unmappedGateOutranksEverything() {
        when(committeePort.gateMap()).thenReturn(List.of(
            gateRow("CAB", null, null, false, "NOT_MAPPED"),
            gateRow("ECAB", 12L, "Executive Committee", true, null)));
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));

        ChangeCabQueueService.CabQueue q = service.queue();

        assertThat(cab(q).awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .isEqualTo(ChangeCabQueueService.Pending.GATE_UNMAPPED);
        assertThat(cab(q).fault())
            .as("A17-D2's own code, passed through — never re-worded here, or the workspace and the "
              + "409 the user hits would explain the same fault differently")
            .isEqualTo("NOT_MAPPED");
        assertThat(cab(q).mapped()).isFalse();
        assertThat(q.notReferred())
            .as("it is not 'unreferred' — referral is impossible until a committee is named")
            .isZero();
    }

    @Test
    @DisplayName("a mapped gate whose committee cannot decide still moves the queue (F2-96)")
    void aRosterEmptyGateStillRefersAndTables() {
        // ⚠ The live probe caught this: the gate map's `configured` flag folds NOT_MAPPED,
        // COMMITTEE_UNAVAILABLE and ROSTER_EMPTY together, and dev's CAB is the third — correctly
        // mapped, zero voting seats. Keying the ladder on that flag reported every row unfixable on a
        // gate whose referral returned 201 and whose agenda line appeared, telling the operator to
        // re-map a mapping that was already right. That is the remedy F2-96 exists to prevent.
        when(committeePort.gateMap()).thenReturn(List.of(
            gateRow("CAB", 13L, "IT Steering Committee", false, "ROSTER_EMPTY"),
            gateRow("ECAB", 12L, "Executive Committee", true, null)));
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));

        ChangeCabQueueService.Gate g = cab(service.queue());

        assertThat(g.mapped()).as("a committee IS named, so a referral can be made").isTrue();
        assertThat(g.canDecide()).as("but A17-D2 will refuse the approval until seats are filled")
            .isFalse();
        assertThat(g.fault()).isEqualTo("ROSTER_EMPTY");
        assertThat(g.awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .as("the actionable next step is to refer it, not to re-map the gate")
            .isEqualTo(ChangeCabQueueService.Pending.NOT_REFERRED);
    }

    @Test
    @DisplayName("a dissolved committee is not a place anything can be referred to")
    void aDissolvedCommitteeIsUnmapped() {
        Map<String, Object> row = gateRow("CAB", 5L, "Technology Steering (legacy)", false,
                                         "COMMITTEE_UNAVAILABLE");
        row.put("committeeStatus", "DISSOLVED");
        when(committeePort.gateMap()).thenReturn(List.of(
            row, gateRow("ECAB", 12L, "Executive Committee", true, null)));
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));

        ChangeCabQueueService.Gate g = cab(service.queue());
        assertThat(g.mapped()).isFalse();
        assertThat(g.awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .isEqualTo(ChangeCabQueueService.Pending.GATE_UNMAPPED);
    }

    @Test
    @DisplayName("a referred change with no change_code is SUBJECT_UNCITEABLE, not NOT_TABLED (F2-109)")
    void anUnciteableChangeCannotBeTabled() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(60L, null, "NORMAL", "HIGH", "CAB_PENDING")));
        when(changeRepo.findById(60L)).thenReturn(Optional.of(change(60L, null, "NORMAL", "HIGH", "CAB_PENDING")));
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(referral(10L, 60L, "SET")), List.of()));
        when(committeePort.nextSitting(13L)).thenReturn(Optional.of(sitting(List.of("CHG-9 — other"))));

        assertThat(cab(service.queue()).awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .as("rebuilding the agenda cannot fix this: with no code there is no reference for an "
              + "agenda line to name. F2-109 says every API-created change is in this state.")
            .isEqualTo(ChangeCabQueueService.Pending.SUBJECT_UNCITEABLE);
    }

    @Test
    @DisplayName("a referred change with no future sitting is NO_SITTING_SCHEDULED — an item nobody will hear")
    void noSittingIsAFinding() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));
        when(changeRepo.findById(4L)).thenReturn(Optional.of(change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(referral(11L, 4L, "CHG-4")), List.of()));
        when(committeePort.nextSitting(13L)).thenReturn(Optional.empty());

        assertThat(cab(service.queue()).awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .isEqualTo(ChangeCabQueueService.Pending.NO_SITTING_SCHEDULED);
    }

    @Test
    @DisplayName("referred, sitting scheduled, agenda names another change → NOT_TABLED for this one")
    void tablingIsPerChangeNotPerAgenda() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-2025-0004", "NORMAL", "HIGH", "CAB_PENDING")));
        when(changeRepo.findById(4L)).thenReturn(Optional.of(
            change(4L, "CHG-2025-0004", "NORMAL", "HIGH", "CAB_PENDING")));
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(referral(12L, 4L, "CHG-2025-0004")), List.of()));
        // The agenda has a decision item — for a different change. A count-based check would have
        // called this tabled, which is the whole reason Sitting carries the lines and not a number.
        when(committeePort.nextSitting(13L))
            .thenReturn(Optional.of(sitting(List.of("For decision: CHG-2025-0009 — something else"))));

        assertThat(cab(service.queue()).awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .isEqualTo(ChangeCabQueueService.Pending.NOT_TABLED);
    }

    @Test
    @DisplayName("the agenda naming this change reads TABLED")
    void tabledIsTheGoodCase() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-2025-0004", "NORMAL", "HIGH", "CAB_PENDING")));
        when(changeRepo.findById(4L)).thenReturn(Optional.of(
            change(4L, "CHG-2025-0004", "NORMAL", "HIGH", "CAB_PENDING")));
        when(committeePort.queue(13L)).thenReturn(new CommitteePort.CommitteeQueue(
            List.of(referral(12L, 4L, "CHG-2025-0004")), List.of()));
        when(committeePort.nextSitting(13L))
            .thenReturn(Optional.of(sitting(List.of("For decision: CHG-2025-0004 — migrate the database"))));

        ChangeCabQueueService.CabQueue q = service.queue();
        assertThat(cab(q).awaiting()).singleElement()
            .extracting(ChangeCabQueueService.Pending::reason)
            .isEqualTo(ChangeCabQueueService.Pending.TABLED);
        assertThat(q.tabled()).isEqualTo(1);
        assertThat(q.notReferred()).isZero();
    }

    @Test
    @DisplayName("Sitting.tables matches the reference, not the display prefix, and refuses a blank one")
    void tablingMatchesTheReference() {
        CommitteePort.Sitting s = sitting(List.of("Zur Entscheidung: CHG-7 — reworded prefix"));
        assertThat(s.tables("CHG-7"))
            .as("the prefix is display text in two languages; the code is the data the builder puts "
              + "there for exactly this join")
            .isTrue();
        assertThat(s.tables(null)).isFalse();
        assertThat(s.tables("  ")).isFalse();
        assertThat(sitting(List.of()).tables("CHG-7")).isFalse();
    }

    // ── the population, and the engine ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("AWAITING_CAB holds only statuses where the decision is still ahead")
    void theQueuePopulationIsTheDecisionsStillAhead() {
        assertThat(ChangeCabQueueService.AWAITING_CAB)
            .containsExactly("CAB_PENDING", "CAB_DEFERRED", "MORE_INFO_REQUIRED");
        assertThat(ChangeCabQueueService.AWAITING_CAB)
            .as("a change before its decision point is not the committee's work yet; listing it "
              + "would fill the queue with items no CAB member can act on")
            .doesNotContain("DRAFT", "SUBMITTED", "IMPACT_ASSESSED");
        assertThat(ChangeCabQueueService.AWAITING_CAB)
            .as("everything at or past CAB_APPROVED belongs to S8f's reconciliation, and two "
              + "registers reporting the same row would disagree the first time either changed")
            .doesNotContainAnyElementsOf(UnauthorisedChangeRegisterService.PAST_AUTHORISATION);
    }

    @Test
    @DisplayName("no committee engine reports unknown, not clean")
    void anAbsentEngineIsReportedAsAbsent() {
        ChangeCabQueueService noEngine = new ChangeCabQueueService(changeRepo, matrixRepo, null);
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));

        ChangeCabQueueService.CabQueue q = noEngine.queue();

        assertThat(q.engineAbsent()).isTrue();
        assertThat(q.gates()).hasSize(2).allSatisfy(g -> {
            assertThat(g.mapped()).isFalse();
            assertThat(g.canDecide()).isFalse();
            assertThat(g.fault()).contains("not available");
        });
        assertThat(q.byReason().get(ChangeCabQueueService.Pending.GATE_UNMAPPED))
            .as("with no engine every gate is unresolvable — S8h's rule: an absent engine reports "
              + "unknown, and a queue that read empty would report a clean CAB that nobody checked")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("the queue never asks the engine whether a decision approves")
    void theQueueDoesNotSecondGuessTheDecision() {
        when(changeRepo.findByStatuses(anyLong(), any())).thenReturn(List.of(
            change(4L, "CHG-4", "NORMAL", "HIGH", "CAB_PENDING")));

        service.queue();

        verify(committeePort, never()).decisionApproves(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(committeePort, never()).assertDecisionApproves(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(committeePort, never()).refer(any(), anyLong(), any(), any(), anyLong(), any(), any(), any());
    }

    // ── the boundary this slice had to respect ──────────────────────────────────────────────────

    @Test
    @DisplayName("CommitteePort.Sitting carries no quorum, minutes, attendance or tally field")
    void theSittingIsAnIdentityNotADecision() {
        List<String> fields = new ArrayList<>();
        for (RecordComponent c : CommitteePort.Sitting.class.getRecordComponents()) fields.add(c.getName());

        assertThat(fields)
            .as("the port's own javadoc forbids these: they are the engine's answers, and a second "
              + "answer to 'was this quorate?' is the defect (F-014's class, F2-28's surface)")
            .doesNotContain("quorumMet", "quorumPresent", "quorumRequired", "quorumSource",
                            "minutesEn", "minutesAr", "minutesApprovedAt",
                            "voteFor", "voteAgainst", "voteAbstain", "outcome", "attendees");
        assertThat(fields).containsExactly("id", "code", "titleEn", "titleAr", "scheduledAt",
                                           "status", "agendaItems", "tabledSubjects");
    }

    @Test
    @DisplayName("the subject-snapshot predicate lives once, in the engine, and awaitingDecision is a view over it")
    void onePredicateNotTwo() {
        String engine = read(Path.of("src/main/java/com/tss/grc/module/admin/committee/service",
                                     "CommitteeReferralService.java"));
        String code = strip(engine);

        assertThat(code)
            .as("awaitingDecision must delegate to queue(), not re-apply the filter — two copies of "
              + "one predicate is a mirror maintained by assertion, and it lies the first time one "
              + "side changes (S8h's keeper)")
            .contains("return queue(committeeId).stated();");
        assertThat(count(code, "getSourceCode() != null"))
            .as("exactly one place decides whether a referral can be named on an agenda")
            .isEqualTo(1);

        String adapter = strip(read(SRC.resolve("change_adapters/CommitteeAdapter.java")));
        // ⚠ This assertion first banned `getSourceCode()` outright and failed on a correct adapter:
        // `view()` reads that field to build the projection, which is translation, not the predicate.
        // A scan that matches too much invents defects just as surely as one that matches too little
        // misses them — so it bans the *comparison*, which is the thing that must live in one place.
        assertThat(adapter)
            .as("the adapter translates the partition; it must not recompute it")
            .doesNotContain("getSourceCode() != null")
            .doesNotContain("sourceCode() != null")
            .doesNotContain("getSourceCode().isBlank()")
            .doesNotContain("sourceCode().isBlank()");
    }

    @Test
    @DisplayName("one agenda reader — the export and the adapter both go through it")
    void oneAgendaReader() {
        String export = strip(read(SRC.resolve("reporting/service/ExportService.java")));
        assertThat(export)
            .as("this printed a dash for every agenda populateAgenda builds, because it read only "
              + "titleEn while the builder writes topicEn — 24 of 34 dev agendas on one side, the "
              + "rest on the other. Both key shapes now go through one reader.")
            .contains("CommitteeAgendaJson.topic(item, ar)")
            .contains("CommitteeAgendaJson.mandatory(item)");
        assertThat(export)
            .as("no local re-read of the title keys may come back alongside it")
            .doesNotContain("item.get(ar ? \"titleAr\" : \"titleEn\")");

        String adapter = strip(read(SRC.resolve("change_adapters/CommitteeAdapter.java")));
        assertThat(adapter)
            .as("tabling is read by reference, not by module tag — a charter standing item carries the "
              + "module too, and counting it reported a tabled change where none was tabled")
            .contains("CommitteeAgendaJson.tabledRefs(");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static ChangeCabQueueService.Gate cab(ChangeCabQueueService.CabQueue q) {
        return q.gates().stream().filter(g -> "CAB".equals(g.gate())).findFirst()
                .orElseThrow(() -> new AssertionError("the CAB gate is always in the payload"));
    }

    private static ChangeRequest change(Long id, String code, String type, String risk, String status) {
        ChangeRequest c = new ChangeRequest();
        c.setId(id); c.setOrganisationId(ORG); c.setChangeCode(code);
        c.setTitleEn("t"); c.setTitleAr("ت");
        c.setChangeType(type); c.setRiskLevel(risk); c.setStatus(status);
        return c;
    }

    private static ChangeAuthorisationMatrix cell(String type, String risk, String gate, int min) {
        ChangeAuthorisationMatrix m = new ChangeAuthorisationMatrix();
        m.setOrganisationId(ORG); m.setChangeType(type); m.setRiskLevel(risk);
        m.setGate(gate); m.setMinApprovals(min);
        return m;
    }

    private static Map<String, Object> gateRow(String gate, Long committeeId, String nameEn,
                                               boolean configured, String fault) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("gate", gate);
        row.put("committeeId", committeeId);
        if (nameEn != null) { row.put("committeeNameEn", nameEn); row.put("committeeStatus", "ACTIVE"); }
        row.put("configured", configured);
        row.put("fault", fault);
        return row;
    }

    private static CommitteePort.ReferralView referral(Long id, Long changeId, String code) {
        return new CommitteePort.ReferralView(id, ORG, "CHANGE", changeId, 13L, null, null,
            "CAB", "APPROVAL", "PENDING", "admin", null, LocalDateTime.now(), code, "subject", "موضوع", null);
    }

    private static CommitteePort.Sitting sitting(List<String> tabled) {
        return new CommitteePort.Sitting(24L, "MTG-IT-26Q3", "IT Steering — Q3", "لجنة تقنية المعلومات",
            LocalDateTime.now().plusDays(12), "SCHEDULED", 3 + tabled.size(), tabled);
    }

    private static String read(Path p) {
        try { return Files.readString(p, StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /**
     * Comments removed before any source assertion.
     *
     * <p>S8h found this the hard way, by mutation: a whole-file scan passed after the collaborator was
     * sabotaged, because the class javadoc named the correct call. A scan a comment can satisfy is a
     * test that cannot fail.
     */
    private static String strip(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
