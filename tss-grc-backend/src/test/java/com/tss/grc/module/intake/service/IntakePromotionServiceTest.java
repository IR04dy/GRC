package com.tss.grc.module.intake.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.dto.IntakeRecordDto;
import com.tss.grc.module.intake.dto.TriageDecisionRequest;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.PromotionRegistry;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2-172 — {@code triage(PROMOTE)} writes the record, and the id it records comes from that write.
 *
 * <p>The defect being pinned is not subtle once stated and was invisible for as long as nobody
 * called the endpoint: {@code triage()} copied {@code promotedToEntityType} and
 * {@code promotedToRecordId} straight out of the request body and created nothing. The intake row
 * then carried a permanent history note — <i>"Promoted to DPIA #482913"</i> — naming a record that
 * had never existed, and eleven register pages rendered live governance buttons on the client-side
 * placeholder that stood in for it (F2-171).
 *
 * <p>These are unit tests over mocks on purpose: what is being asserted is the <b>control flow</b>
 * of a promotion — that the port is called, that its return value is what gets stamped, that a
 * refusal is a refusal. Whether a DPIA row survives a real transaction is a live probe, and it is
 * recorded in the finding rather than simulated here.
 */
class IntakePromotionServiceTest {

    private IntakeRecordRepository repo;
    private IntakeService service;
    private final AtomicReference<IntakePromotionContext> seen = new AtomicReference<>();

    /** A port that records what it was handed and answers with a fixed id, as a real one would. */
    private final class SpyPort implements RegisterPromotionPort {
        private final String id;
        SpyPort(String id) { this.id = id; }
        @Override public String target() { return "DPIA"; }
        @Override public String requiredPermission() { return "PRIVACY:WRITE"; }
        @Override public String promote(IntakePromotionContext c) { seen.set(c); return id; }
    }

    private void wire(List<RegisterPromotionPort> ports, String... authorities) {
        // The real ModulePermissionEvaluator maps hasPermission('PRIVACY','WRITE') onto the
        // PRIVACY_WRITE authority; this stands in for it without dragging in the ownership registry.
        org.springframework.security.access.PermissionEvaluator evaluator =
            new org.springframework.security.access.PermissionEvaluator() {
                @Override public boolean hasPermission(Authentication a, Object target, Object perm) {
                    return a != null && a.getAuthorities().stream()
                        .anyMatch(g -> g.getAuthority().equals(target + "_" + perm));
                }
                @Override public boolean hasPermission(Authentication a, Serializable id, String type, Object p) {
                    return false;
                }
            };
        repo = mock(IntakeRecordRepository.class);
        when(repo.save(any(IntakeRecord.class))).thenAnswer(i -> i.getArgument(0));
        service = new IntakeService(repo, new PromotionRegistry(ports), evaluator, new ObjectMapper());

        Authentication auth = new UsernamePasswordAuthenticationToken("compliance", "x",
            java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static IntakeRecord dpiaIntake() {
        IntakeRecord r = new IntakeRecord();
        r.setId(41L);
        r.setOrganisationId(1L);
        r.setReferenceCode("DI-K7L9-Z4M2");
        r.setKind("DPIA_INITIATION");
        r.setStatus("TRIAGING");
        r.setSeverity("HIGH");
        r.setSubmitterName("Sara Al-Otaibi");
        r.setSubmitterEmail("sara@example.sa");
        r.setSubmitterIsAnonymous(false);
        r.setAcknowledgedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        r.setPayload("{\"processingActivityName\":\"Falcon loyalty scoring\"}");
        return r;
    }

    @BeforeEach void tenant() { TenantContext.setCurrentTenant(1L); }

    @AfterEach void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void promotingStampsTheIdTheRegisterActuallyReturned() {
        wire(List.of(new SpyPort("6")), "PRIVACY_WRITE");
        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));

        IntakeRecordDto out = service.triage(41L, decision("PROMOTE", null), "compliance");

        assertThat(out.getStatus()).isEqualTo("PROMOTED");
        assertThat(out.getPromotedToEntityType())
            .as("the target comes from the kind's catalogue entry, not from the request")
            .isEqualTo("DPIA");
        assertThat(out.getPromotedToRecordId())
            .as("the id must be the one the register returned — this is the whole finding")
            .isEqualTo("6");
        assertThat(out.getPromotedAt()).isNotNull();
        assertThat(out.getHistory()).last()
            .satisfies(e -> assertThat(e.getNote()).isEqualTo("Promoted to DPIA #6"));
    }

    @Test
    void thePortIsHandedTheSubmissionsOwnFacts() {
        wire(List.of(new SpyPort("6")), "PRIVACY_WRITE");
        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));

        service.triage(41L, decision("PROMOTE", "looks legitimate"), "compliance");

        IntakePromotionContext ctx = seen.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.referenceCode()).isEqualTo("DI-K7L9-Z4M2");
        assertThat(ctx.payload()).containsEntry("processingActivityName", "Falcon loyalty scoring");
        assertThat(ctx.actor()).isEqualTo("compliance");
        assertThat(ctx.triagerNote()).isEqualTo("looks legitimate");
        assertThat(ctx.catalogTitleAr())
            .as("the Arabic title every promoted record needs comes from the catalogue")
            .containsPattern("[\\u0600-\\u06FF]");
        assertThat(ctx.acknowledgedAt())
            .as("statutory clocks are counted from receipt, so the port needs receipt — not now")
            .isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    /**
     * The triager's note is <b>appended</b> to the promotion note, never substituted for it. Before
     * F2-172 a note replaced it entirely, so the one line in the permanent history that says which
     * record was created could be overwritten by whatever the triager happened to type.
     */
    @Test
    void aTriagerNoteCannotDisplaceTheRecordIdInTheHistory() {
        wire(List.of(new SpyPort("6")), "PRIVACY_WRITE");
        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));

        IntakeRecordDto out = service.triage(41L, decision("PROMOTE", "approved by DPO"), "compliance");

        assertThat(out.getHistory()).last()
            .satisfies(e -> assertThat(e.getNote()).isEqualTo("Promoted to DPIA #6 — approved by DPO"));
    }

    @Test
    void promotingTwiceIsRefusedRatherThanCreatingASecondRecord() {
        wire(List.of(new SpyPort("6")), "PRIVACY_WRITE");
        IntakeRecord already = dpiaIntake();
        already.setStatus("PROMOTED");
        already.setPromotedToEntityType("DPIA");
        already.setPromotedToRecordId("6");
        when(repo.findById(41L)).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> service.triage(41L, decision("PROMOTE", null), "compliance"))
            .isInstanceOf(IllegalStateException.class)          // → 409
            .hasMessageContaining("already promoted")
            .hasMessageContaining("#6");
        assertThat(seen.get()).as("no second record may be created").isNull();
    }

    /**
     * {@code INTAKE:TRIAGE} decides a submission; it does not create records in fourteen modules.
     * Without this the permission would be a create-anything door reachable without holding a single
     * one of the target modules' own guards — the F2-04 shape, two routes to one write with only one
     * of them guarded.
     */
    @Test
    void promotingWithoutTheTargetModulesWritePermissionIs403AndSaysWhichOne() {
        wire(List.of(new SpyPort("6")), "INTAKE_TRIAGE");        // deliberately no PRIVACY_WRITE
        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));

        assertThatThrownBy(() -> service.triage(41L, decision("PROMOTE", null), "compliance"))
            .isInstanceOf(DomainAccessDeniedException.class)     // → 403 carrying its own message
            .hasMessageContaining("PRIVACY:WRITE");
        assertThat(seen.get()).as("the record must not be created before the check").isNull();
    }

    @Test
    void aTargetNoRegisterServesIsRefusedAndNothingIsMarkedPromoted() {
        wire(List.of(), "PRIVACY_WRITE");                        // no ports at all
        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));

        assertThatThrownBy(() -> service.triage(41L, decision("PROMOTE", null), "compliance"))
            .isInstanceOf(IllegalStateException.class)           // → 409
            .hasMessageContaining("no register for 'DPIA'");
    }

    /**
     * Anti-vacuity for the four refusals above: a service that refused everything would satisfy
     * them all while breaking triage completely. Reject and close must still work — and must still
     * work <b>without</b> any target permission, because they create nothing.
     */
    @Test
    void rejectAndCloseStillWorkAndNeedNoTargetPermission() {
        wire(List.of(new SpyPort("6")), "INTAKE_TRIAGE");
        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));
        IntakeRecordDto rejected = service.triage(41L, decision("REJECT", "duplicate"), "compliance");
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectionReason()).isEqualTo("duplicate");

        when(repo.findById(41L)).thenReturn(Optional.of(dpiaIntake()));
        IntakeRecordDto closed = service.triage(41L, decision("CLOSE", null), "compliance");
        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getHistory()).last()
            .satisfies(e -> assertThat(e.getNote()).isEqualTo("Closed without promotion"));
        assertThat(seen.get()).isNull();
    }

    /** A payload that will not parse must name the intake, not surface from inside another module. */
    @Test
    void anUnreadablePayloadFailsHereRatherThanDeepInsideTheTargetModule() {
        wire(List.of(new SpyPort("6")), "PRIVACY_WRITE");
        IntakeRecord broken = dpiaIntake();
        broken.setPayload("{not json");
        when(repo.findById(41L)).thenReturn(Optional.of(broken));

        assertThatThrownBy(() -> service.triage(41L, decision("PROMOTE", null), "compliance"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DI-K7L9-Z4M2")
            .hasMessageContaining("unreadable payload");
    }

    /** An empty payload is normal (the public DSR form sends four optional fields), not an error. */
    @Test
    void anEmptyPayloadPromotesFine() {
        wire(List.of(new SpyPort("6")), "PRIVACY_WRITE");
        IntakeRecord bare = dpiaIntake();
        bare.setPayload(null);
        when(repo.findById(41L)).thenReturn(Optional.of(bare));

        assertThat(service.triage(41L, decision("PROMOTE", null), "compliance").getPromotedToRecordId())
            .isEqualTo("6");
        assertThat(seen.get().payload()).isEmpty();
    }

    private static TriageDecisionRequest decision(String decision, String note) {
        TriageDecisionRequest r = new TriageDecisionRequest();
        r.setDecision(decision);
        r.setNote(note);
        return r;
    }
}
