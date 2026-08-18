package com.tss.grc.module.intake.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.intake.entity.IntakeEvent;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import com.tss.grc.module.intake.spi.PromotionRegistry;
import com.tss.grc.module.intake.spi.SystemIntakePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.PermissionEvaluator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-175 — a system-filed submission is a submission, with every property a human one has.
 *
 * <h3>What this class is really testing</h3>
 *
 * <p>Not "the new method works". The finding was that a <em>second writer</em> to
 * {@code intake.intake_records} existed, and each property F2-172 installed on {@code submit()} was
 * silently optional on that path. So each test below names one of those properties and asserts the
 * system path has it too — because that is the class of defect, and a fix that restores four of five
 * is the same defect with a smaller population.
 *
 * <p>⚠ The reason none of this was caught: the old path had a test, and the test
 * <b>asserted the defect as correct</b> — {@code assertThat(issue.getReferenceCode()).startsWith("ISS-")}
 * and {@code assertThat(issue.getStatus()).isEqualTo("SUBMITTED")}. It described the code accurately,
 * which is what makes this shape hard to see (CLAUDE.md records the same trap from F2-159, where a test
 * asserted that a targetId overload correctly discarded its targetId). A test written by reading the
 * implementation can only ever agree with it.
 *
 * <p>Constructed with a <b>real</b> {@link ObjectMapper} rather than a mock, deliberately: the payload
 * assertion here is about actual JSON serialisation, and a stubbed mapper would assert nothing.
 */
@ExtendWith(MockitoExtension.class)
class IntakeSystemSubmissionTest {

    private static final Long ORG = 7L;

    @Mock private IntakeRecordRepository repo;
    @Mock private PromotionRegistry promotions;
    @Mock private PermissionEvaluator permissions;

    private IntakeService service;

    @BeforeEach
    void setUp() {
        service = new IntakeService(repo, promotions, permissions, new ObjectMapper());
    }

    /** Hands back the entity that was saved, with an id, the way the repository would. */
    private IntakeRecord captureSave() {
        when(repo.save(any(IntakeRecord.class))).thenAnswer(inv -> {
            IntakeRecord r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });
        return null;
    }

    private IntakeRecord saved() {
        ArgumentCaptor<IntakeRecord> cap = ArgumentCaptor.forClass(IntakeRecord.class);
        verify(repo).save(cap.capture());
        return cap.getValue();
    }

    private static SystemIntakePort.SystemSubmission drift(Map<String, Object> payload) {
        return new SystemIntakePort.SystemSubmission(
            ORG, "RISK_ISSUE", "API_INGESTION", "HIGH",
            "AI Governance — drift monitor", payload);
    }

    private static Map<String, Object> payload(String title) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("trigger", "AI_MODEL_CRITICAL_DRIFT");
        p.put("modelTitleEn", title);
        p.put("metricValue", new BigDecimal("0.831"));
        return p;
    }

    // ─── The five properties the second writer did not have ──────────────────────────────────────

    @Test
    @DisplayName("the reference code comes from the catalogue prefix, not a second minter")
    void referenceCodeIsCatalogued() {
        captureSave();
        SystemIntakePort.Receipt r = service.fileSystemSubmission(drift(payload("Credit scoring")));

        // RI is RISK_ISSUE's catalogued prefix. The retired writer minted ISS-{epoch36}-{4hex}: three
        // prefix letters where every other code has two, under a pair no catalogue entry uses.
        assertThat(saved().getReferenceCode()).matches("RI-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(r.referenceCode()).isEqualTo(saved().getReferenceCode());
        assertThat(r.referenceCode()).doesNotStartWith("ISS-");
    }

    @Test
    @DisplayName("the assignee comes from the catalogue — one source, and it names a real role")
    void assigneeIsTheCataloguedStakeholder() {
        captureSave();
        SystemIntakePort.Receipt r = service.fileSystemSubmission(drift(payload("Credit scoring")));

        // The retired writer hard-coded RISK_MANAGER, lifted from MlModelDriftController's own
        // hasAnyRole guard. What matters is that the value is DERIVED — one catalogue, consulted —
        // rather than which literal it happens to equal.
        //
        // ⚠ This assertion read RISK_OFFICER until F2-174, and the comment above it argued the
        // point backwards: "assigned_to holds stakeholder codes; a role name in it routes to
        // nobody, and there was never any reason for the two vocabularies to agree." Measured
        // against both real databases, the reverse is true — RISK_OFFICER matched no row in
        // iam.roles, so it was the stakeholder code that routed to nobody, and a role name is the
        // only value a tenant administrator can attach people to. F2-175's own register entry had
        // already recorded the honest half ("do not read it as 'the assignee now works'"); this
        // test was written from the implementation and so could only agree with it.
        //
        // The closure worth noticing: the hard-code F2-175 deleted was RIGHT about the name and
        // wrong about the mechanism. F2-174 makes the catalogue say the same thing, so the value
        // is now both derived and resolvable.
        assertThat(saved().getAssignedTo()).isEqualTo("RISK_MANAGER");
        assertThat(r.assignedTo()).isEqualTo("RISK_MANAGER");
    }

    @Test
    @DisplayName("the SLA is the catalogue's hours, not a second piece of arithmetic that agrees today")
    void slaComesFromTheCatalogue() {
        captureSave();
        LocalDateTime before = LocalDateTime.now();
        SystemIntakePort.Receipt r = service.fileSystemSubmission(drift(payload("Credit scoring")));

        // RISK_ISSUE is 24 * 14 hours. plusDays(14) happens to equal that today — which is precisely
        // why nothing noticed: the divergence is one catalogue edit away and nothing compares them.
        assertThat(saved().getSlaDueAt())
            .isAfterOrEqualTo(before.plusHours(24 * 14))
            .isBefore(before.plusHours(24 * 14).plusMinutes(5));
        assertThat(r.slaDueAt()).isEqualTo(saved().getSlaDueAt());
    }

    @Test
    @DisplayName("…and a kind whose SLA is NOT 14 days proves the derivation rather than the coincidence")
    void slaIsDerivedNotCoincidental() {
        captureSave();
        LocalDateTime before = LocalDateTime.now();
        // ⚠ This test exists because writing the mutation for the one above exposed that it cannot
        // fail: RISK_ISSUE's catalogued SLA is 24 * 14 hours, which plusDays(14) equals exactly, so a
        // mutation restoring the retired arithmetic survives. WORKPLACE_INJURY is the 24-hour GOSI
        // notification — statutory, and 14 days late is the whole cost. Asserting derivation needs a
        // kind where the two answers differ; asserting it on a kind where they agree asserts nothing.
        service.fileSystemSubmission(new SystemIntakePort.SystemSubmission(
            ORG, "WORKPLACE_INJURY", "API_INGESTION", "HIGH", "safety monitor", Map.of()));

        assertThat(saved().getSlaDueAt())
            .isAfterOrEqualTo(before.plusHours(24))
            .isBefore(before.plusHours(24).plusMinutes(5));
        assertThat(saved().getReferenceCode()).startsWith("WI-");
        assertThat(saved().getAssignedTo()).isEqualTo("HR_LEAD");
    }

    @Test
    @DisplayName("it lands in the triage queue with the same status a human submission gets")
    void statusIsTriaging() {
        captureSave();
        service.fileSystemSubmission(drift(payload("Credit scoring")));

        // The retired writer left it at SUBMITTED. Both read as "open" in the inbox, so this one was
        // cosmetic — said plainly rather than dressed up, because the next reader deserves to know
        // which of the five had teeth and which did not.
        assertThat(saved().getStatus()).isEqualTo("TRIAGING");
        assertThat(saved().getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("it has a history: who filed it and where it was routed")
    void historyNamesTheSystemAndTheRoute() {
        captureSave();
        service.fileSystemSubmission(drift(payload("Credit scoring")));

        List<IntakeEvent> h = saved().getHistory();
        assertThat(h).hasSize(2);
        // The submitter columns are empty for a system record, so this line is its only provenance.
        assertThat(h.get(0).getStatus()).isEqualTo("SUBMITTED");
        assertThat(h.get(0).getNote()).contains("AI Governance — drift monitor");
        assertThat(h.get(1).getStatus()).isEqualTo("TRIAGING");
        // F2-174: this is the second place the assignee reaches a human — the audit trail, alongside
        // the acknowledgement text IntakeFormPage renders. Both said RISK_OFFICER, a name matching no
        // row in iam.roles, so the record's own history told a reader it had been routed somewhere.
        assertThat(h.get(1).getNote()).contains("RISK_MANAGER");
    }

    // ─── The one with a live consequence ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a control character in the payload still yields JSON promotion can read")
    void payloadWithControlCharactersStaysPromotable() throws Exception {
        captureSave();
        // A tab is how a model title arrives when it is pasted out of a spreadsheet. The retired
        // esc() escaped backslash, quote, \n and \r — JSON requires every C0 control character
        // escaped, so this produced a payload Jackson refuses (verified against Jackson directly:
        // "Illegal unquoted character ((CTRL-CHAR, code 9))").
        String nasty = "Credit\tscoring \"v2\"\001 model\nrevision";
        service.fileSystemSubmission(drift(payload(nasty)));

        String json = saved().getPayload();
        // The property that matters is not how it is escaped but that promotion can still read it:
        // IntakeService#contextFor parses this string, and on failure the submission can never be
        // escalated into the register it was raised for — reported as an intake fault, from a payload
        // another module wrote.
        Map<String, Object> parsed = new ObjectMapper().readValue(
            json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        assertThat(parsed.get("modelTitleEn")).isEqualTo(nasty);
        assertThat(parsed).containsEntry("trigger", "AI_MODEL_CRITICAL_DRIFT");
    }

    @Test
    @DisplayName("an unserialisable payload is refused at the door, naming the kind")
    void unserialisablePayloadIsRefused() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("self", new Object() {           // no serialiser, and self-referential accessors
            @SuppressWarnings("unused")
            public Object getSelf() { return this; }
        });

        assertThatThrownBy(() -> service.fileSystemSubmission(drift(p)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RISK_ISSUE");
    }

    // ─── Fail-closed on the two things a system caller must say ──────────────────────────────────

    @Test
    @DisplayName("a null organisation is refused rather than resolved from the ambient tenant")
    void nullOrganisationIsRefused() {
        assertThatThrownBy(() -> service.fileSystemSubmission(new SystemIntakePort.SystemSubmission(
                null, "RISK_ISSUE", "API_INGESTION", "HIGH", "monitor", Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("organisation");
        // ⚠ The assertion that matters is this one: no record was written. A caller that forgot the
        // tenant must not get a row filed under whatever thread-local happened to be set.
        verify(repo, org.mockito.Mockito.never()).save(any(IntakeRecord.class));
    }

    @Test
    @DisplayName("a blank system actor is refused — it is the record's only provenance")
    void blankActorIsRefused() {
        assertThatThrownBy(() -> service.fileSystemSubmission(new SystemIntakePort.SystemSubmission(
                ORG, "RISK_ISSUE", "API_INGESTION", "HIGH", "   ", Map.of())))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repo, org.mockito.Mockito.never()).save(any(IntakeRecord.class));
    }

    @Test
    @DisplayName("an uncatalogued kind is refused on the system path too, not stored with no deadline")
    void uncataloguedKindIsRefused() {
        assertThatThrownBy(() -> service.fileSystemSubmission(new SystemIntakePort.SystemSubmission(
                ORG, "MODEL_DRIFT", "API_INGESTION", "HIGH", "monitor", Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown intake kind");
        verify(repo, org.mockito.Mockito.never()).save(any(IntakeRecord.class));
    }

    // ─── A machine is not a whistleblower ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a system record is not anonymous, and carries no user identity to be bound to")
    void systemRecordIsAttributedButHasNoUser() {
        captureSave();
        service.fileSystemSubmission(drift(payload("Credit scoring")));

        IntakeRecord r = saved();
        // submitter_user_id NULL because there is no user — but submitterIsAnonymous is FALSE, and the
        // distinction is load-bearing: an honoured anonymous submission is a whistleblower protection
        // (nothing identifying persisted, by design), and a system record must not be filed under it.
        // SystemSubmission has no anonymity field at all, so a caller cannot ask.
        assertThat(r.getSubmitterUserId()).isNull();
        assertThat(r.getSubmitterIsAnonymous()).isFalse();
        assertThat(r.getSubmitterName()).isEqualTo("AI Governance — drift monitor");
        assertThat(r.getSubmitterEmail()).isNull();
        assertThat(r.getSubmitterPhone()).isNull();
        // No display language: a machine has no reader, and "en" on an Arabic-primary platform is a
        // claim about somebody who does not exist. The retired writer hard-coded it.
        assertThat(r.getLocale()).isNull();
        assertThat(r.getOrganisationId()).isEqualTo(ORG);
    }

    @Test
    @DisplayName("severity defaults the same way the human path defaults it")
    void severityDefaultsToMedium() {
        captureSave();
        service.fileSystemSubmission(new SystemIntakePort.SystemSubmission(
            ORG, "RISK_ISSUE", "API_INGESTION", null, "monitor", Map.of()));

        assertThat(saved().getSeverity()).isEqualTo("MEDIUM");
        // An empty payload stays null rather than becoming the string "{}", which would parse to an
        // empty map and read in the inbox as a submission that said nothing on purpose.
        assertThat(saved().getPayload()).isNull();
    }
}
