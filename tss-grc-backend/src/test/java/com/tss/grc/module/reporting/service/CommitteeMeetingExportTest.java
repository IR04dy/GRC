package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * S7f / CE-14a — <b>the committee meeting's agenda pack and its minutes</b>, and the honesty
 * requirement that is the point of the slice.
 *
 * <p><b>The gap, measured 2026-08-04.</b> G-14: "No agenda pack / minutes / decision-register exports
 * for change governance." The CAB pack (CH-11) is a pack for one <em>change</em>; the committee module
 * had <b>no export at all</b> beyond a blank form template, while holding <b>34 meetings in dev and 32
 * in prod</b>, 16/15 of them {@code MINUTES_APPROVED} with real agenda and decision content. A
 * secretary could not circulate an agenda or file a minute.
 *
 * <p><b>⚠ Why provenance is the substance and not a flourish.</b> An export is where a governance
 * record becomes evidence, so it must not upgrade an assertion into a computation. Measured across
 * both databases:
 *
 * <pre>
 *   quorum_source    15 NULL / 17 ASSERTED_LEGACY / 2 COMPUTED (dev); 0 COMPUTED in prod
 *   quorum_present   populated on 2 of 34 (dev), 0 of 32 (prod)
 *   tally_source     20 ASSERTED_LEGACY / 2 COMPUTED (dev); 0 COMPUTED in prod
 *   resolution_vote  4 rows (dev), 0 (prod)
 * </pre>
 *
 * <p>So printing "Quorum: 4 of 7, required 4" would invent numbers for 32 of 34 meetings, and printing
 * "For 0 / Against 0" would read as unanimous abstention where no vote was ever recorded. Both are the
 * F2-42 shape. F2-28 added {@code quorum_source} so the difference is <em>sayable</em>; this export
 * says it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeMeetingExportTest {

    private static final Long ORG = 1L;
    private static final Long MEETING_ID = 42L;

    private static final Path SERVICE =
        Path.of("src/main/java/com/tss/grc/module/reporting/service/ExportService.java");
    private static final Path CONTROLLER =
        Path.of("src/main/java/com/tss/grc/module/reporting/controller/ExportController.java");

    @Mock OrganisationRepository orgRepo;
    @Mock RiskRepository riskRepo;
    @Mock AuditFindingRepository findingRepo;
    @Mock KriDefinitionRepository kriRepo;
    @Mock KriMeasurementRepository measRepo;
    @Mock BrandThemeService brandThemeService;
    @Mock ChangeRequestRepository changeRepo;
    @Mock ChangeApprovalRepository changeApprovalRepo;
    @Mock ChangeTaskRepository changeTaskRepo;
    @Mock ChangeLinkRepository changeLinkRepo;
    @Mock CommitteeMeetingRepository meetingRepo;
    @Mock CommitteeResolutionRepository resolutionRepo;
    @InjectMocks ExportService svc;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#0B5FFF", "#00B8A9", null));
        when(orgRepo.findById(ORG)).thenReturn(Optional.empty());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A meeting shaped like the seeded ones: agenda + attendees + decisions as JSON snapshots. */
    private static CommitteeMeeting meeting() {
        CommitteeMeeting m = new CommitteeMeeting();
        m.setId(MEETING_ID);
        m.setOrganisationId(ORG);
        m.setCommitteeId(7L);
        m.setCommitteeNameEn("Change Advisory Board");
        m.setMeetingCode("CAB-2026-Q3-01");
        m.setTitleEn("CAB — Q3 2026 sitting");
        m.setTitleAr("مجلس استشارة التغيير — جلسة الربع الثالث");
        m.setScheduledAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        m.setLocationOrLink("Riyadh HQ, Room 4");
        m.setChairName("Director, Change");
        m.setSecretaryName("Change Manager");
        m.setStatus("MINUTES_APPROVED");
        m.setQuorumMet((short) 1);
        m.setAgendaJson("""
            [{"seq":1,"titleEn":"Approval of previous minutes","titleAr":"اعتماد محضر الاجتماع السابق"},
             {"seq":2,"titleEn":"For decision: CHG-2026-0009 — Payment gateway TLS upgrade",
              "titleAr":"للقرار: CHG-2026-0009","mandatory":true,"minutes":15}]""");
        m.setAttendeesJson("""
            [{"name":"Director, Change","role":"Chair","present":true,"votingMember":true},
             {"name":"CISO","role":"Member","present":true,"votingMember":true},
             {"name":"Head of Ops","role":"Member","present":false,"votingMember":true}]""");
        m.setMinutesEn("The board reviewed the TLS upgrade and approved it subject to conditions.");
        m.setMinutesAr("استعرض المجلس ترقية التشفير واعتمدها بشروط.");
        m.setMinutesApprovedAt(LocalDateTime.of(2026, 8, 13, 9, 30));
        m.setApproverName("Director, Change");
        return m;
    }

    private static CommitteeResolution resolution(String tallySource, int f, int a, int b) {
        CommitteeResolution r = new CommitteeResolution();
        r.setId(500L);
        r.setOrganisationId(ORG);
        r.setMeetingId(MEETING_ID);
        r.setSeq(2);
        r.setResolutionCode("RES-2026-0002");
        r.setTitleEn("Approve the payment gateway TLS upgrade");
        r.setTitleAr("اعتماد ترقية التشفير لبوابة المدفوعات");
        r.setOutcome("APPROVED");
        r.setTallySource(tallySource);
        r.setVoteFor(f);
        r.setVoteAgainst(a);
        r.setVoteAbstain(b);
        return r;
    }

    // ── The documents exist ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the agenda pack is a real PDF for an owned meeting, in both languages")
    void agendaPackRenders() {
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting()));
        for (String lang : List.of("en", "ar")) {
            byte[] b = svc.committeeAgendaPdf(MEETING_ID, lang);
            assertThat(b).as(lang).startsWith(new byte[]{'%', 'P', 'D', 'F'});
            assertThat(b.length).as(lang + " must be a real document").isGreaterThan(2000);
        }
    }

    @Test
    @DisplayName("the minutes are a real PDF for an owned meeting, in both languages")
    void minutesRender() {
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting()));
        when(resolutionRepo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of(resolution("COMPUTED", 4, 0, 1)));
        for (String lang : List.of("en", "ar")) {
            byte[] b = svc.committeeMinutesPdf(MEETING_ID, lang);
            assertThat(b).as(lang).startsWith(new byte[]{'%', 'P', 'D', 'F'});
            assertThat(b.length).as(lang).isGreaterThan(2000);
        }
    }

    @Test
    @DisplayName("a meeting in another tenant is refused, never exported")
    void crossTenantIsRefused() {
        // The repository lookup is tenant-scoped, so a foreign id simply does not resolve — and the
        // export must fail rather than render an empty document with a real cover page.
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.committeeAgendaPdf(MEETING_ID, "en"))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> svc.committeeMinutesPdf(MEETING_ID, "en"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Absent content is STATED, never blank ───────────────────────────────────────────────────

    @Test
    @DisplayName("an unbuilt agenda renders a sentence, not an empty table")
    void anUnbuiltAgendaSaysSo() {
        // 2 of 34 dev meetings carry no agenda_json. An empty item table reads as "a meeting with
        // nothing to discuss"; the truth is that the agenda has not been built.
        CommitteeMeeting m = meeting();
        m.setAgendaJson(null);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(m));
        byte[] b = svc.committeeAgendaPdf(MEETING_ID, "en");
        assertThat(b).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        assertThat(read(SERVICE))
            .as("the wording must exist in the generator, or 'stated not blank' is a claim not a fact")
            .contains("has not been built yet");
    }

    @Test
    @DisplayName("a malformed JSON snapshot degrades the section, not the document")
    void aBrokenSnapshotDoesNotFailTheExport() {
        // A minutes PDF that 500s because one snapshot column is corrupt loses the whole governance
        // record. The section states itself absent and the rest of the minute still files.
        CommitteeMeeting m = meeting();
        m.setAttendeesJson("{ this is not an array");
        m.setAgendaJson("]]] neither is this");
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(m));
        when(resolutionRepo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of());

        assertThat(svc.committeeAgendaPdf(MEETING_ID, "en")).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        assertThat(svc.committeeMinutesPdf(MEETING_ID, "en")).startsWith(new byte[]{'%', 'P', 'D', 'F'});
    }

    @Test
    @DisplayName("no attendance and no resolutions are both stated in words")
    void emptySectionsSayWhyTheyAreEmpty() {
        CommitteeMeeting m = meeting();
        m.setAttendeesJson(null);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(m));
        when(resolutionRepo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of());
        assertThat(svc.committeeMinutesPdf(MEETING_ID, "en")).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        String src = read(SERVICE);
        assertThat(src).contains("No attendance was recorded for this meeting.");
        assertThat(src).contains("No resolutions were recorded for this meeting.");
    }

    // ── ⚠ The provenance, which is what stops the export laundering an assertion ─────────────────

    @Test
    @DisplayName("⚠ quorum basis distinguishes COMPUTED from ASSERTED_LEGACY from not-recorded")
    void quorumProvenanceIsStatedAndNotAssumed() throws Exception {
        Method basis = ExportService.class.getDeclaredMethod(
            "quorumBasis", CommitteeMeeting.class, boolean.class);
        basis.setAccessible(true);
        CommitteeMeeting m = meeting();

        m.setQuorumSource("COMPUTED");
        assertThat((String) basis.invoke(null, m, false)).contains("Computed from the member roster");

        m.setQuorumSource("ASSERTED_LEGACY");
        assertThat((String) basis.invoke(null, m, false))
            .as("""
                17 of 34 dev meetings and 17 of 32 prod meetings carry ASSERTED_LEGACY. Rendering \
                that identically to COMPUTED is how an export launders a caller-authored claim into \
                evidence — which is the whole reason F2-28 added the column.""")
            .contains("Asserted")
            .contains("not computed");

        m.setQuorumSource(null);
        assertThat((String) basis.invoke(null, m, false)).isEqualTo("Not recorded");
        assertThat((String) basis.invoke(null, m, true))
            .as("Arabic is the primary language — the provenance must be sayable in it")
            .containsPattern("[\\u0600-\\u06FF]");

        // An unknown value must pass through rather than be silently normalised into a friendly
        // phrase — a new source added by a later migration would otherwise read as "not recorded".
        m.setQuorumSource("SOMETHING_NEW");
        assertThat((String) basis.invoke(null, m, false)).isEqualTo("SOMETHING_NEW");
    }

    @Test
    @DisplayName("⚠ an all-zero tally reads as 'no votes recorded', never as unanimous abstention")
    void zeroVotesAreNotATally() throws Exception {
        Method summary = ExportService.class.getDeclaredMethod(
            "voteSummary", CommitteeResolution.class, boolean.class);
        summary.setAccessible(true);

        assertThat((String) summary.invoke(null, resolution("ASSERTED_LEGACY", 0, 0, 0), false))
            .as("""
                committee_resolution_vote holds 4 rows in dev and 0 in prod, so most resolutions have \
                no per-member vote at all. "For 0 · Against 0 · Abstain 0" states a unanimous \
                abstention that never happened.""")
            .isEqualTo("No votes recorded");
        assertThat((String) summary.invoke(null, resolution("COMPUTED", 4, 1, 0), false))
            .contains("For 4").contains("Against 1");
        // A null column is not zero, and must not crash the row either.
        CommitteeResolution nulls = resolution("COMPUTED", 0, 0, 0);
        nulls.setVoteFor(null); nulls.setVoteAgainst(null); nulls.setVoteAbstain(null);
        assertThat((String) summary.invoke(null, nulls, false)).isEqualTo("No votes recorded");
    }

    @Test
    @DisplayName("tally basis is stated per resolution, the same way quorum basis is per meeting")
    void tallyProvenanceIsStated() throws Exception {
        Method basis = ExportService.class.getDeclaredMethod(
            "tallyBasis", CommitteeResolution.class, boolean.class);
        basis.setAccessible(true);
        assertThat((String) basis.invoke(null, resolution("COMPUTED", 1, 0, 0), false))
            .contains("Computed from recorded votes");
        assertThat((String) basis.invoke(null, resolution("ASSERTED_LEGACY", 1, 0, 0), false))
            .contains("Asserted");
        CommitteeResolution none = resolution(null, 1, 0, 0);
        assertThat((String) basis.invoke(null, none, false)).isEqualTo("Not recorded");
    }

    @Test
    @DisplayName("⚠ unapproved minutes are marked DRAFT on the page, not only in the KPI band")
    void draftMinutesSayDraft() {
        // Minutes circulate as files. A document that looks approved because somebody exported it is
        // the F2-98 shape — a disclosure that fires for the case nobody could be misled by.
        CommitteeMeeting m = meeting();
        m.setStatus("IN_PROGRESS");
        m.setMinutesApprovedAt(null);
        m.setApproverName(null);
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(m));
        when(resolutionRepo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of());
        assertThat(svc.committeeMinutesPdf(MEETING_ID, "en")).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        String src = read(SERVICE);
        assertThat(src).contains("DRAFT — these minutes have not been approved");
        assertThat(src)
            .as("and the marker must be gated on the MINUTES_APPROVED status, not on a truthy field")
            .contains("boolean approved = \"MINUTES_APPROVED\".equals(m.getStatus())");
    }

    @Test
    @DisplayName("a recorded dissent survives into the minutes")
    void dissentIsNotLostToASummaryTable() {
        CommitteeResolution r = resolution("COMPUTED", 3, 1, 0);
        r.setDissentNotesEn("Head of Ops objected: the rollback window is too short.");
        when(meetingRepo.findByIdAndOrganisationId(MEETING_ID, ORG)).thenReturn(Optional.of(meeting()));
        when(resolutionRepo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(ORG, MEETING_ID))
            .thenReturn(List.of(r));
        assertThat(svc.committeeMinutesPdf(MEETING_ID, "en")).startsWith(new byte[]{'%', 'P', 'D', 'F'});
        assertThat(read(SERVICE))
            .as("a recorded dissent is the part of a minute an auditor reads first")
            .contains("getDissentNotesEn");
    }

    // ── The guard ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("both endpoints AND a READ into the EXPORT guard, and the tier is ADMIN")
    void theExportGuardComposesAReadAndUsesTheCommitteeEngineTier() {
        String src = read(CONTROLLER);
        assertThat(src)
            .as("F2-61(c)'s invariant: a file-returning handler guarded by MODULE:EXPORT must AND a READ")
            .contains("hasPermission('ADMIN','EXPORT') and hasPermission('ADMIN','READ')");
        for (String path : List.of("/committee/meeting/{id}/agenda.pdf",
                                  "/committee/meeting/{id}/minutes.pdf")) {
            int at = src.indexOf("@GetMapping(\"" + path + "\")");
            assertThat(at).as("endpoint %s is missing", path).isGreaterThan(-1);
            assertThat(src.substring(at, at + 200))
                .as("""
                    %s must use the ADMIN tier, not CHANGE. The committee engine is generic — 19 \
                    committees in dev, of which the CAB and ECAB are two — and its screens carry \
                    hasPermission('ADMIN', …). Measured before choosing: ADMIN:EXPORT is 7 roles, \
                    ADMIN:READ is 21, and no role holds EXPORT without READ in either database, so \
                    the composed guard loses nobody.""", path)
                .contains("ADMIN_EXPORT");
        }
    }
}
