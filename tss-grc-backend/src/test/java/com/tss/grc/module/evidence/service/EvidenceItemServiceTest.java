package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.*;
import com.tss.grc.module.evidence.entity.*;
import com.tss.grc.module.evidence.repository.*;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvidenceItemService — Sprint 4 §4.1 test backlog.
 *
 * Locks the evidence lifecycle:
 *   DRAFT → PENDING_REVIEW → APPROVED | REJECTED
 * Plus expiry (validTo &lt; today flips isExpired in the response DTO),
 * tenant-scope on every read/write, and link creation.
 */
@ExtendWith(MockitoExtension.class)
class EvidenceItemServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long ID        = 50L;

    @Mock private EvidenceItemRepository itemRepository;
    @Mock private EvidenceSubControlRepository subControlRepository;
    @Mock private EvidenceControlRepository controlRepository;
    @Mock private EvidenceFrameworkRepository frameworkRepository;
    @Mock private EvidenceLinkRepository linkRepository;
    @Mock private EvidenceRequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private EvidenceStorageService storage;
    @InjectMocks private EvidenceItemService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static EvidenceItem item(String status, Long orgId) {
        EvidenceItem e = new EvidenceItem();
        e.setId(ID);
        e.setExternalId(UUID.randomUUID());
        e.setOrganisationId(orgId);
        e.setTitleEn("Q1 Penetration Test Report");
        e.setEvidenceType("DOCUMENT");
        e.setStatus(status);
        return e;
    }

    private static EvidenceItem item(String status) { return item(status, ORG_ID); }

    private static EvidenceItemCreateRequest createReq() {
        return new EvidenceItemCreateRequest(
            "Q1 Pen Test", "اختبار الاختراق Q1", "Network test results",
            null, null, "DOCUMENT", null, null, null, "1.0",
            LocalDate.now().minusDays(30), LocalDate.now().plusDays(335),
            null, "pentest-q1.pdf"
        );
    }

    /** Like {@link #createReq()} but with a client-supplied (and forbidden) status. */
    private static EvidenceItemCreateRequest createReqWithStatus(String status) {
        return new EvidenceItemCreateRequest(
            "Q1 Pen Test", "اختبار الاختراق Q1", "Network test results",
            null, null, "DOCUMENT", null, null, null, "1.0",
            LocalDate.now().minusDays(30), LocalDate.now().plusDays(335),
            status, "pentest-q1.pdf"
        );
    }

    // ─── Lifecycle: status transitions are unguarded but tenant-scoped ──

    @Test
    void submitForReview_DRAFT_to_PENDING_REVIEW() {
        EvidenceItem e = item("DRAFT");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceItemResponse resp = service.submitForReview(ID);
        assertThat(resp.status()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void approve_PENDING_REVIEW_to_APPROVED() {
        EvidenceItem e = item("PENDING_REVIEW");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.approve(ID).status()).isEqualTo("APPROVED");
    }

    @Test
    void reject_PENDING_REVIEW_to_REJECTED() {
        EvidenceItem e = item("PENDING_REVIEW");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.reject(ID).status()).isEqualTo("REJECTED");
    }

    // ─── Expiry computation ──────────────────────────────────────────

    @Test
    void response_isExpired_true_when_validTo_in_past() {
        EvidenceItem e = item("APPROVED");
        e.setValidTo(LocalDate.now().minusDays(1));
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));

        EvidenceItemResponse resp = service.get(ID);
        assertThat(resp.isExpired()).isTrue();
    }

    @Test
    void response_isExpired_false_when_validTo_in_future_or_null() {
        EvidenceItem e = item("APPROVED");
        e.setValidTo(LocalDate.now().plusDays(30));
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        assertThat(service.get(ID).isExpired()).isFalse();

        // null validTo → never expires
        EvidenceItem e2 = item("APPROVED");
        e2.setValidTo(null);
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e2));
        assertThat(service.get(ID).isExpired()).isFalse();
    }

    @Test
    void response_evidenceCode_zero_padded() {
        EvidenceItem e = item("APPROVED");
        e.setId(7L);
        when(itemRepository.findById(7L)).thenReturn(Optional.of(e));

        EvidenceItemResponse resp = service.get(7L);
        assertThat(resp.evidenceCode()).isEqualTo("EV-00007");
    }

    // ─── Tenant scope ────────────────────────────────────────────────

    @Test
    void get_other_tenant_throws_EntityNotFound() {
        when(itemRepository.findById(ID)).thenReturn(Optional.of(item("DRAFT", OTHER_ORG)));

        assertThatThrownBy(() -> service.get(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Evidence not found");
    }

    @Test
    void approve_other_tenant_throws_EntityNotFound() {
        when(itemRepository.findById(ID)).thenReturn(Optional.of(item("PENDING_REVIEW", OTHER_ORG)));

        assertThatThrownBy(() -> service.approve(ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(itemRepository, never()).save(any());
    }

    // ─── Create / soft-delete ────────────────────────────────────────

    @Test
    void create_stamps_organisation() {
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> {
            EvidenceItem saved = inv.getArgument(0);
            saved.setId(ID);
            return saved;
        });

        service.create(createReq());

        ArgumentCaptor<EvidenceItem> captor = ArgumentCaptor.forClass(EvidenceItem.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    void create_ignores_client_supplied_status_and_forces_DRAFT() {
        // SEC-EVD-002: a caller must not be able to mint an item directly in
        // APPROVED (or any other state) and bypass the review state machine /
        // four-eyes SoD guard. The status field on the request is ignored.
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> {
            EvidenceItem saved = inv.getArgument(0);
            saved.setId(ID);
            return saved;
        });

        EvidenceItemResponse resp = service.create(createReqWithStatus("APPROVED"));

        assertThat(resp.status()).isEqualTo("DRAFT");

        ArgumentCaptor<EvidenceItem> captor = ArgumentCaptor.forClass(EvidenceItem.class);
        verify(itemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void update_does_not_mutate_existing_status_from_request_body() {
        // SEC-EVD-002: update() shares applyRequest(); it must never re-set the
        // workflow status. An existing APPROVED item stays APPROVED even when the
        // request body asks for REJECTED — status moves only via transition APIs.
        EvidenceItem existing = item("APPROVED");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceItemResponse resp = service.update(ID, createReqWithStatus("REJECTED"));

        assertThat(resp.status()).isEqualTo("APPROVED");
        assertThat(existing.getStatus()).isEqualTo("APPROVED");
    }

    // ─── F2-378: replacing an APPROVED item's file forces re-review ────

    private static EvidenceStorageService.StoredFile stored() {
        return new EvidenceStorageService.StoredFile(
            "q2-pentest.pdf", "evd/1/q2-pentest.pdf", 2048L,
            "application/pdf", "0123456789abcdef0123456789abcdef");
    }

    @Test
    void attachFile_to_APPROVED_item_resets_to_PENDING_REVIEW() {
        // SEC-EVD-002 / F2-378: a reviewer signed off a specific evidenceHash. Replacing
        // the bytes of an APPROVED item must send it back for re-review — leaving
        // un-reviewed content in an APPROVED state is a four-eyes bypass on the assurance
        // core (approve() re-applies the creator≠approver SoD gate on the new content).
        EvidenceItem e = item("APPROVED");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(eq(ORG_ID), any())).thenReturn(stored());

        EvidenceItemResponse resp = service.attachFile(ID, null);

        assertThat(resp.status()).isEqualTo("PENDING_REVIEW");
        assertThat(e.getStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void attachFile_to_APPROVED_item_emits_re_review_trigger() {
        // The reset must re-enter the reviewer queue: EVIDENCE_SUBMITTED fires with the
        // real previous status so downstream re-review workflows pick the item up again.
        EvidenceItem e = item("APPROVED");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(eq(ORG_ID), any())).thenReturn(stored());

        service.attachFile(ID, null);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publishEvent(ev.capture());
        DomainEvent submitted = ev.getAllValues().stream()
            .filter(x -> "EVIDENCE_SUBMITTED".equals(x.triggerEvent()))
            .findFirst().orElseThrow();
        assertThat(submitted.payload()).containsEntry("previousStatus", "APPROVED");
        assertThat(submitted.payload()).containsEntry("status", "PENDING_REVIEW");
    }

    @Test
    void attachFile_to_DRAFT_item_leaves_status_DRAFT() {
        // Working states (DRAFT/NEEDS_REVISION/REJECTED) and an in-flight PENDING_REVIEW
        // are content-editable — only a reviewed (APPROVED) item resets. No spurious
        // EVIDENCE_SUBMITTED fires here.
        EvidenceItem e = item("DRAFT");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(eq(ORG_ID), any())).thenReturn(stored());

        assertThat(service.attachFile(ID, null).status()).isEqualTo("DRAFT");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("EVIDENCE_FILE_ATTACHED");
    }

    @Test
    void delete_soft_deletes_via_save() {
        EvidenceItem e = item("APPROVED");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(ID);

        assertThat(e.getIsDeleted()).isTrue();
        verify(itemRepository).save(e);
        verify(itemRepository, never()).deleteById(any());
    }

    @Test
    void delete_other_tenant_throws_EntityNotFound() {
        when(itemRepository.findById(ID)).thenReturn(Optional.of(item("APPROVED", OTHER_ORG)));

        assertThatThrownBy(() -> service.delete(ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── Stats ───────────────────────────────────────────────────────

    @Test
    void getStats_returns_per_status_counts_plus_expired_and_frameworks() {
        when(itemRepository.countByOrganisationId(ORG_ID)).thenReturn(40L);
        when(itemRepository.countByOrganisationIdAndStatus(ORG_ID, "APPROVED")).thenReturn(20L);
        when(itemRepository.countByOrganisationIdAndStatus(ORG_ID, "PENDING_REVIEW")).thenReturn(10L);
        when(itemRepository.countByOrganisationIdAndStatus(ORG_ID, "DRAFT")).thenReturn(5L);
        when(itemRepository.countByOrganisationIdAndStatus(ORG_ID, "REJECTED")).thenReturn(5L);

        // Expired: 3 items past their validTo
        EvidenceItem expired1 = item("APPROVED");
        EvidenceItem expired2 = item("APPROVED");
        EvidenceItem expired3 = item("APPROVED");
        when(itemRepository.findExpired(eq(ORG_ID), any(LocalDate.class)))
            .thenReturn(List.of(expired1, expired2, expired3));

        EvidenceFramework fw1 = new EvidenceFramework();
        EvidenceFramework fw2 = new EvidenceFramework();
        when(frameworkRepository.findByIsActiveTrue()).thenReturn(List.of(fw1, fw2));

        EvidenceDashboardStats stats = service.getStats();
        assertThat(stats.totalEvidence()).isEqualTo(40);
        assertThat(stats.approved()).isEqualTo(20);
        assertThat(stats.pendingReview()).isEqualTo(10);
        assertThat(stats.draft()).isEqualTo(5);
        assertThat(stats.rejected()).isEqualTo(5);
        assertThat(stats.expired()).isEqualTo(3);
        assertThat(stats.frameworks()).isEqualTo(2);
    }

    // ─── Cross-entity link ───────────────────────────────────────────

    @Test
    void addLink_stamps_org_and_persists() {
        EvidenceLinkRequest req = new EvidenceLinkRequest(
            ID, "RISK", "RISK", 99L, "Linked to ERM-0099"
        );
        when(linkRepository.save(any(EvidenceLink.class))).thenAnswer(inv -> inv.getArgument(0));

        service.addLink(req);

        ArgumentCaptor<EvidenceLink> captor = ArgumentCaptor.forClass(EvidenceLink.class);
        verify(linkRepository).save(captor.capture());
        EvidenceLink link = captor.getValue();
        assertThat(link.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(link.getEvidenceId()).isEqualTo(ID);
        assertThat(link.getLinkedModule()).isEqualTo("RISK");
        assertThat(link.getLinkedEntityId()).isEqualTo(99L);
    }

    // ── F7 / F8 emission contract ─────────────────────────────────────────────

    @Test
    void create_emits_EVIDENCE_CREATED_and_writes_audit_log() {
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> {
            EvidenceItem saved = inv.getArgument(0);
            saved.setId(ID);
            return saved;
        });

        service.create(createReq());

        verify(audit).log(eq("CREATE"), eq("EvidenceItem"), eq(ID), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("EVIDENCE_CREATED");
        assertThat(emitted.entityType()).isEqualTo("EvidenceItem");
        assertThat(emitted.payload()).containsEntry("evidenceType", "DOCUMENT");
        assertThat(emitted.payload()).containsEntry("previousStatus", "");
    }

    @Test
    void submitForReview_emits_EVIDENCE_SUBMITTED_with_previousStatus() {
        EvidenceItem e = item("DRAFT");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submitForReview(ID);

        verify(audit).log(eq("TRANSITION"), eq("EvidenceItem"), eq(ID), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("EVIDENCE_SUBMITTED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "DRAFT");
        assertThat(emitted.payload()).containsEntry("status", "PENDING_REVIEW");
    }

    @Test
    void approve_emits_EVIDENCE_APPROVED() {
        EvidenceItem e = item("PENDING_REVIEW");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.approve(ID);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("EVIDENCE_APPROVED");
        assertThat(emitted.payload()).containsEntry("status", "APPROVED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "PENDING_REVIEW");
    }

    @Test
    void reject_emits_EVIDENCE_REJECTED() {
        EvidenceItem e = item("PENDING_REVIEW");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reject(ID);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("EVIDENCE_REJECTED");
        assertThat(emitted.payload()).containsEntry("status", "REJECTED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "PENDING_REVIEW");
    }

    @Test
    void delete_emits_EVIDENCE_DELETED_and_writes_audit() {
        EvidenceItem e = item("APPROVED");
        when(itemRepository.findById(ID)).thenReturn(Optional.of(e));
        when(itemRepository.save(any(EvidenceItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(ID);

        verify(audit).log(eq("DELETE"), eq("EvidenceItem"), eq(ID), any(String.class));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("EVIDENCE_DELETED");
        // Status is "APPROVED" since soft-delete doesn't change status
        assertThat(emitted.payload()).containsEntry("status", "APPROVED");
    }
}
