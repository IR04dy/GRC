package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeCondition;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeConditionRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.spi.EvidencePort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S8a / CE-9 — the gate S7e shipped without: a condition is SATISFIED only when <b>verified</b>
 * evidence says so.
 *
 * <p>Two things are being pinned here and they pull in opposite directions, which is why the polarity
 * is tested explicitly rather than left to read from the code:
 *
 * <ul>
 *   <li><b>Reads fail open.</b> {@code evidenceForChange} on a deployment with no evidence subsystem
 *       returns an empty list; a missing optional module must not 500 a detail page.</li>
 *   <li><b>The satisfaction gate fails CLOSED.</b> A guard that passes because it cannot see the
 *       evidence is F2-95's defect verbatim — "satisfied" reading true over a requirement nothing
 *       verified. So the same null port that makes a read empty makes satisfaction refuse.</li>
 * </ul>
 *
 * <p>A single test class covers both because the interesting assertion is the <em>difference</em>.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeConditionEvidenceTest {

    private static final Long ORG        = 1L;
    private static final Long CHANGE_ID  = 9L;
    private static final long COND_ID    = 501L;

    @Mock private ChangeRequestRepository    changeRepo;
    @Mock private ChangeConditionRepository  conditionRepo;
    @Mock private AuditLogService            audit;
    @Mock private ApplicationEventPublisher  publisher;
    @Mock private EvidencePort               evidencePort;

    private ChangeManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new ChangeManagementService(changeRepo, null, null, null, null, audit, publisher);
        ReflectionTestUtils.setField(service, "conditionRepo", conditionRepo);
        ReflectionTestUtils.setField(service, "evidencePort", evidencePort);
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change()));
        when(conditionRepo.findByIdAndOrganisationId(COND_ID, ORG)).thenReturn(Optional.of(open()));
        when(conditionRepo.save(any(ChangeCondition.class))).thenAnswer(i -> i.getArgument(0));
        authenticate("dba-lead", 88L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    private static void authenticate(String username, Long userId) {
        var authorities = List.of(new SimpleGrantedAuthority("CHANGE_WRITE"),
                                  new SimpleGrantedAuthority("CHANGE_APPROVE"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new GrcUserDetails(userId, ORG, username, "n/a", true, true, authorities),
                "n/a", authorities));
    }

    private static ChangeRequest change() {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-2026-0009");
        c.setTitleEn("Payment gateway TLS upgrade");
        c.setTitleAr("ترقية بروتوكول التشفير");
        c.setStatus("SCHEDULED");
        return c;
    }

    private static ChangeCondition open() {
        ChangeCondition k = new ChangeCondition();
        k.setId(COND_ID);
        k.setOrganisationId(ORG);
        k.setChangeRequestId(CHANGE_ID);
        k.setTextEn("Re-run the DR rehearsal and attach the report");
        k.setTextAr("إعادة تنفيذ تمرين التعافي وإرفاق التقرير");
        k.setStatus(ChangeConditionStatus.OPEN);
        k.setOwnerName("DBA Lead");
        k.setImposedAt(LocalDateTime.now());
        return k;
    }

    private static EvidencePort.EvidenceRef ev(long id, String title, String status) {
        return new EvidencePort.EvidenceRef(id, "ev-" + id, title, "تقرير", "REPORT", status,
                                            "APPROVED".equals(status), title + ".pdf", "h" + id);
    }

    private void attachedToCondition(EvidencePort.EvidenceRef... refs) {
        when(evidencePort.attached(EvidencePort.Subject.CONDITION, COND_ID)).thenReturn(List.of(refs));
    }

    // ── the gate ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("satisfying a condition with nothing attached is refused, and says so")
    void nothingAttachedRefuses() {
        attachedToCondition();

        assertThatThrownBy(() -> service.satisfyCondition(COND_ID, "done"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no APPROVED evidence")
            .hasMessageContaining("none attached")
            // The refusal has to name the way out, or the only way to move the change on is to
            // discover waive() by reading the source.
            .hasMessageContaining("waive");
        verify(conditionRepo, never()).save(any(ChangeCondition.class));
    }

    @Test
    @DisplayName("PENDING_REVIEW evidence does not discharge an obligation — and the refusal names it")
    void uploadedButUnreviewedEvidenceIsNotEvidence() {
        attachedToCondition(ev(9101L, "DR rehearsal report", "PENDING_REVIEW"));

        assertThatThrownBy(() -> service.satisfyCondition(COND_ID, "attached the report"))
            .isInstanceOf(IllegalStateException.class)
            // ⚠ Naming what IS attached is the difference between a usable refusal and a puzzle: the
            // operator has attached something and believes the obligation met. Tell them the status.
            .hasMessageContaining("#9101")
            .hasMessageContaining("PENDING_REVIEW");
        verify(conditionRepo, never()).save(any(ChangeCondition.class));
    }

    @Test
    @DisplayName("EXPIRED evidence does not discharge it either")
    void agedOutEvidenceIsNotEvidence() {
        attachedToCondition(ev(9102L, "Last year's rehearsal", "EXPIRED"));

        assertThatThrownBy(() -> service.satisfyCondition(COND_ID, "we did this last year"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EXPIRED");
    }

    @Test
    @DisplayName("one APPROVED item is enough, even among unapproved ones")
    void approvedEvidenceSatisfies() {
        attachedToCondition(ev(9103L, "Draft notes", "DRAFT"),
                            ev(9104L, "DR rehearsal report", "APPROVED"),
                            ev(9105L, "Superseded run", "EXPIRED"));

        ChangeCondition saved = service.satisfyCondition(COND_ID, "report reviewed and approved");

        assertThat(saved.getStatus())
            .as("any-match, not all-match: a condition accumulates artefacts over its life and the "
              + "presence of a stale one cannot un-discharge it")
            .isEqualTo("SATISFIED");
        assertThat(saved.getClosedByName()).isEqualTo("dba-lead");
        assertThat(saved.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("⚠ the gate fails CLOSED when the evidence subsystem is absent")
    void anAbsentSubsystemRefusesRatherThanWavesThrough() {
        ReflectionTestUtils.setField(service, "evidencePort", null);

        assertThatThrownBy(() -> service.satisfyCondition(COND_ID, "trust me"))
            .as("F2-95 was exactly this: a verification flag reading true because nothing could "
              + "check it. An optional peer makes reads empty; it must not make gates pass.")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no APPROVED evidence");
        verify(conditionRepo, never()).save(any(ChangeCondition.class));
    }

    @Test
    @DisplayName("waiving is untouched by the gate — it is the auditable escape hatch")
    void waivingNeedsNoEvidence() {
        attachedToCondition();
        authenticate("cab-chair", 77L);   // not the requester; the S7e self-waiver rule still applies

        ChangeCondition saved = service.waiveCondition(COND_ID, "The DR site was decommissioned");

        assertThat(saved.getStatus())
            .as("a waiver stands on a stated reason and an independent approver, not on evidence — "
              + "requiring evidence to withdraw an obligation would strand every change whose "
              + "condition became moot")
            .isEqualTo("WAIVED");
        assertThat(saved.getClosureNotes())
            .as("the reason lives in closure_notes, which ck_cc_waiver_reason (V352) requires to be "
              + "non-empty for a WAIVED row — a waiver with no stated reason cannot be persisted")
            .contains("decommissioned");
    }

    @Test
    @DisplayName("waiving still works with the subsystem absent")
    void waivingWorksWithoutTheSubsystem() {
        ReflectionTestUtils.setField(service, "evidencePort", null);
        authenticate("cab-chair", 77L);

        assertThat(service.waiveCondition(COND_ID, "No longer applicable").getStatus())
            .as("otherwise a deployment without the evidence module could neither satisfy nor waive, "
              + "and IMPLEMENTING would be unreachable for every conditional approval")
            .isEqualTo("WAIVED");
    }

    // ── attaching ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("attaching an id that does not resolve is a 404, not a silent success")
    void anUnresolvableEvidenceIdIsRefused() {
        when(evidencePort.attach(any(), anyLong(), anyLong(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attachEvidenceToChange(CHANGE_ID, 4242L, null))
            .as("the adapter returns empty for an id outside the tenant; swallowing that would leave "
              + "a change claiming evidence it has not got — worse than the free text this replaces")
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("4242");
        verify(audit, never()).log(eq("EVIDENCE_ATTACHED"), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("attaching records the evidence's status in the ledger, not just its id")
    void attachingIsAudited() {
        when(evidencePort.attach(eq(EvidencePort.Subject.CHANGE), eq(CHANGE_ID), eq(9104L), any()))
            .thenReturn(Optional.of(ev(9104L, "DR rehearsal report", "PENDING_REVIEW")));

        service.attachEvidenceToChange(CHANGE_ID, 9104L, "back-out proof");

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("EVIDENCE_ATTACHED"), eq("ChangeRequest"), eq(CHANGE_ID), msg.capture());
        assertThat(msg.getValue())
            .as("the status belongs in the entry: an attachment that never got reviewed is a fact the "
              + "ledger should show without joining to the evidence subsystem")
            .contains("9104").contains("PENDING_REVIEW").contains("CHG-2026-0009");
    }

    @Test
    @DisplayName("attaching to a condition audits against the parent change, so one timeline holds both")
    void conditionAttachmentIsAuditedOnTheChange() {
        when(evidencePort.attach(eq(EvidencePort.Subject.CONDITION), eq(COND_ID), eq(9104L), any()))
            .thenReturn(Optional.of(ev(9104L, "DR rehearsal report", "APPROVED")));

        service.attachEvidenceToCondition(COND_ID, 9104L, null);

        verify(audit).log(eq("EVIDENCE_ATTACHED"), eq("ChangeRequest"), eq(CHANGE_ID), anyString());
    }

    @Test
    @DisplayName("attaching refuses outright when the subsystem is absent")
    void attachingRefusesWithoutTheSubsystem() {
        ReflectionTestUtils.setField(service, "evidencePort", null);

        assertThatThrownBy(() -> service.attachEvidenceToChange(CHANGE_ID, 9104L, null))
            .as("a write that no-ops is a write the caller believes happened")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not available");
    }

    // ── reads, and the tenant boundary ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("reads fail OPEN — an absent subsystem is an empty list, not a 500")
    void readsAreEmptyWithoutTheSubsystem() {
        ReflectionTestUtils.setField(service, "evidencePort", null);

        assertThat(service.evidenceForChange(CHANGE_ID)).isEmpty();
        assertThat(service.evidenceForCondition(COND_ID)).isEmpty();
    }

    @Test
    @DisplayName("a foreign condition's evidence is not readable")
    void readingEvidenceIsTenantScoped() {
        when(conditionRepo.findByIdAndOrganisationId(777L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evidenceForCondition(777L))
            .as("the port takes a bare id, so the tenant check has to happen on this side of it")
            .isInstanceOf(EntityNotFoundException.class);
        verify(evidencePort, never()).attached(any(), eq(777L));
    }

    @Test
    @DisplayName("a foreign change's evidence is not readable")
    void readingChangeEvidenceIsTenantScoped() {
        when(changeRepo.findById(888L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evidenceForChange(888L))
            .isInstanceOf(EntityNotFoundException.class);
        verify(evidencePort, never()).attached(eq(EvidencePort.Subject.CHANGE), eq(888L));
    }

    @Test
    @DisplayName("what the read returns is what the port returned, in order")
    void readsPassThroughUnmangled() {
        attachedToCondition(ev(9104L, "DR rehearsal report", "APPROVED"),
                            ev(9101L, "Draft notes", "DRAFT"));

        List<EvidencePort.EvidenceRef> got = service.evidenceForCondition(COND_ID);

        assertThat(got).extracting(EvidencePort.EvidenceRef::id).containsExactly(9104L, 9101L);
        assertThat(got).extracting(EvidencePort.EvidenceRef::verified).containsExactly(true, false);
        assertThat(got.get(0).sha256())
            .as("the hash is the point of using the subsystem — it must reach the UI")
            .isEqualTo("h9104");
    }
}
