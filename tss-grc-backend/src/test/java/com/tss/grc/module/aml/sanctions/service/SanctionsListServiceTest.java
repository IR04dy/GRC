package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryCreateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListCreateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListUpdateRequest;
import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import com.tss.grc.module.aml.sanctions.entity.SanctionsListEntry;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListEntryRepository;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link SanctionsListService} unit tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code create} happy path and duplicate-code rejection</li>
 *   <li>{@code update} blocks edits on platform-global (org-NULL) lists</li>
 *   <li>{@code refresh} stamps {@code lastRefreshedAt} and emits F8</li>
 *   <li>{@code bulkImportEntries} idempotent — second import skips duplicates</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SanctionsListServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long LIST_ID = 100L;

    @Mock private SanctionsListRepository listRepo;
    @Mock private SanctionsListEntryRepository entryRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private SanctionsListService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new SanctionsListService(listRepo, entryRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static SanctionsList orgList() {
        SanctionsList l = new SanctionsList();
        l.setId(LIST_ID);
        l.setOrganisationId(ORG_ID);
        l.setListCode("CUSTOM-LIST");
        l.setTitleEn("Custom internal list");
        l.setTitleAr("قائمة داخلية مُخصَّصة");
        l.setSourceType("OTHER");
        l.setRefreshFrequency("ON_DEMAND");
        l.setEntryCount(0);
        l.setIsActive(true);
        return l;
    }

    private static SanctionsList globalList() {
        SanctionsList l = new SanctionsList();
        l.setId(200L);
        l.setOrganisationId(null); // platform-global
        l.setListCode("OFAC-SDN");
        l.setTitleEn("OFAC SDN");
        l.setTitleAr("OFAC SDN");
        l.setSourceType("OFAC");
        l.setRefreshFrequency("DAILY");
        l.setEntryCount(16000);
        l.setIsActive(true);
        return l;
    }

    // ─── create ──────────────────────────────────────────────────────

    @Test void create_persistsOrgScopedList() {
        ListCreateRequest req = new ListCreateRequest(
            "PEP-RIYADH", "Riyadh PEP register",
            "سجلّ الأشخاص المعرَّضين سياسياً في الرياض",
            "https://internal.example/pep.json", "OTHER",
            "WEEKLY", true);
        when(listRepo.findByListCodeVisibleTo("PEP-RIYADH", ORG_ID)).thenReturn(Optional.empty());
        when(listRepo.save(any(SanctionsList.class))).thenAnswer(inv -> {
            SanctionsList l = inv.getArgument(0);
            l.setId(LIST_ID);
            return l;
        });

        ListResponse out = service.create(req);
        assertThat(out.listCode()).isEqualTo("PEP-RIYADH");
        assertThat(out.organisationId()).isEqualTo(ORG_ID);
        assertThat(out.titleAr()).contains("الرياض");
    }

    @Test void create_rejectsDuplicateCode() {
        ListCreateRequest req = new ListCreateRequest(
            "OFAC-SDN", "Custom OFAC", "OFAC مخصص",
            null, "OTHER", "DAILY", true);
        when(listRepo.findByListCodeVisibleTo("OFAC-SDN", ORG_ID))
            .thenReturn(Optional.of(globalList()));
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    // ─── update ──────────────────────────────────────────────────────

    @Test void update_blockedOnPlatformGlobalList() {
        when(listRepo.findByIdVisibleTo(200L, ORG_ID)).thenReturn(Optional.of(globalList()));
        ListUpdateRequest req = new ListUpdateRequest("Hacked title", null, null, null, null);
        assertThatThrownBy(() -> service.update(200L, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("platform-global");
    }

    @Test void update_succeedsOnOrgScopedList() {
        when(listRepo.findByIdVisibleTo(LIST_ID, ORG_ID)).thenReturn(Optional.of(orgList()));
        when(listRepo.save(any(SanctionsList.class))).thenAnswer(inv -> inv.getArgument(0));

        ListUpdateRequest req = new ListUpdateRequest(
            "Updated EN", "محدَّث", "https://updated", "MONTHLY", false);
        ListResponse out = service.update(LIST_ID, req);
        assertThat(out.titleEn()).isEqualTo("Updated EN");
        assertThat(out.isActive()).isFalse();
        assertThat(out.refreshFrequency()).isEqualTo("MONTHLY");
    }

    // ─── refresh ─────────────────────────────────────────────────────

    @Test void refresh_stampsTimestampAndEmitsF8() {
        SanctionsList l = globalList();
        when(listRepo.findByIdVisibleTo(200L, ORG_ID)).thenReturn(Optional.of(l));
        when(entryRepo.countByListIdAndIsDeletedFalse(200L)).thenReturn(4L);
        when(listRepo.save(any(SanctionsList.class))).thenAnswer(inv -> inv.getArgument(0));

        ListResponse out = service.refresh(200L);
        assertThat(out.lastRefreshedAt()).isNotNull();
        // Preserves the realistic OFAC count (16000) rather than the demo row count (4)
        assertThat(out.entryCount()).isEqualTo(16000);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("SANCTIONS_LIST_REFRESHED");
        assertThat(ev.getValue().payload()).containsEntry("listCode", "OFAC-SDN");
    }

    // ─── bulk import entries ─────────────────────────────────────────

    @Test void bulkImport_idempotent_skipsDuplicates() {
        SanctionsList l = orgList();
        when(listRepo.findByIdVisibleTo(LIST_ID, ORG_ID)).thenReturn(Optional.of(l));
        when(listRepo.save(any(SanctionsList.class))).thenAnswer(inv -> inv.getArgument(0));
        // First entry exists, second does not
        when(entryRepo.existsByListIdAndEntryExternalIdAndIsDeletedFalse(LIST_ID, "EXISTING"))
            .thenReturn(true);
        when(entryRepo.existsByListIdAndEntryExternalIdAndIsDeletedFalse(LIST_ID, "NEW"))
            .thenReturn(false);
        when(entryRepo.save(any(SanctionsListEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepo.countByListIdAndIsDeletedFalse(LIST_ID)).thenReturn(1L);

        EntryBulkImportRequest req = new EntryBulkImportRequest(LIST_ID, List.of(
            new EntryCreateRequest("EXISTING", "INDIVIDUAL", "Existing Person", "اسم",
                null, null, null, null, null, null, null, null, null),
            new EntryCreateRequest("NEW", "INDIVIDUAL", "New Person", "اسم جديد",
                null, null, null, null, null, null, null, null, null)
        ));
        EntryBulkImportResponse out = service.bulkImportEntries(req);
        assertThat(out.totalSubmitted()).isEqualTo(2);
        assertThat(out.created()).isEqualTo(1);
        assertThat(out.skippedExisting()).isEqualTo(1);
    }

    // ─── listVisible / get ───────────────────────────────────────────

    @Test void listVisible_returnsBothGlobalAndOrgScoped() {
        when(listRepo.findVisibleTo(ORG_ID)).thenReturn(List.of(globalList(), orgList()));
        List<ListResponse> out = service.listVisible();
        assertThat(out).hasSize(2);
        assertThat(out).extracting(ListResponse::listCode).containsExactly("OFAC-SDN", "CUSTOM-LIST");
        // One of them is org-NULL (platform-global)
        assertThat(out).anyMatch(r -> r.organisationId() == null);
    }

    @Test void listEntries_loadsByListId() {
        when(listRepo.findByIdVisibleTo(LIST_ID, ORG_ID)).thenReturn(Optional.of(orgList()));
        SanctionsListEntry e = new SanctionsListEntry();
        e.setId(1L);
        e.setListId(LIST_ID);
        e.setEntryExternalId("X-001");
        e.setEntityType("INDIVIDUAL");
        e.setPrimaryNameEn("Person A");
        e.setSeverity("REVIEW_REQUIRED");
        when(entryRepo.findByListIdAndIsDeletedFalseOrderByPrimaryNameEnAsc(LIST_ID))
            .thenReturn(List.of(e));

        var out = service.listEntries(LIST_ID);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).entryExternalId()).isEqualTo("X-001");
    }

    @Test void auditLogCalled_onAllWrites() {
        SanctionsList l = orgList();
        when(listRepo.findByIdVisibleTo(LIST_ID, ORG_ID)).thenReturn(Optional.of(l));
        when(entryRepo.countByListIdAndIsDeletedFalse(LIST_ID)).thenReturn(0L);
        when(listRepo.save(any(SanctionsList.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refresh(LIST_ID);
        verify(audit).log(anyString(), anyString(), any(Long.class), anyString());
    }
}
