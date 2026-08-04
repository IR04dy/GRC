package com.tss.grc.module.change_adapters;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.spi.EvidencePort;
import com.tss.grc.module.change_mgmt.spi.EvidencePort.Subject;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.entity.EvidenceLink;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceLinkRepository;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S8a / CE-9 — the adapter's job is to hold the peer's vocabulary and the tenant check, so those are
 * what get tested.
 *
 * <p><b>The tenant check is not belt-and-braces here, it is the feature.</b> F2-106: the evidence
 * subsystem's own {@code addLink} validates {@code evidenceId} with {@code @NotNull} and nothing else
 * — it never confirms the item exists or belongs to the caller's organisation. This adapter resolves
 * the item itself before writing a link, which is also what its contract promises ("attach an
 * <em>existing</em> evidence item"). {@link #attachingAnItemFromAnotherTenantWritesNoLink} is the
 * regression test for the gap, on this side of it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenceAdapterTest {

    private static final Long ORG   = 1L;
    private static final Long OTHER = 2L;
    private static final Long SUBJ  = 500L;

    @Mock private EvidenceItemRepository items;
    @Mock private EvidenceLinkRepository links;

    private EvidenceAdapter adapter;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        adapter = new EvidenceAdapter(items, links);
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                anyLong(), anyString(), anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static EvidenceItem item(long id, Long org, String status) {
        EvidenceItem e = new EvidenceItem();
        e.setId(id);
        e.setOrganisationId(org);
        e.setExternalId(UUID.nameUUIDFromBytes(("ev" + id).getBytes()));
        e.setTitleEn("DR rehearsal report");
        e.setTitleAr("تقرير تمرين التعافي");
        e.setEvidenceType("REPORT");
        e.setStatus(status);
        e.setFileName("dr-" + id + ".pdf");
        e.setEvidenceHash("sha-" + id);
        e.setIsDeleted(false);
        return e;
    }

    private static EvidenceLink link(long evidenceId, Long org, String entityType) {
        EvidenceLink l = new EvidenceLink();
        l.setOrganisationId(org);
        l.setEvidenceId(evidenceId);
        l.setLinkedModule("CHANGE");
        l.setLinkedEntityType(entityType);
        l.setLinkedEntityId(SUBJ);
        return l;
    }

    // ── vocabulary ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the module string the adapter writes is the one V353 permits")
    void theModuleStringMatchesTheMigration() throws Exception {
        Path v353 = Path.of("src/main/resources/db/migration/sqlserver",
                            "V353__evidence_link_change_module.sql");
        assertThat(v353).as("V353 is what widened CK_ev_links_module").exists();
        String sql = Files.readString(v353);

        assertThat(sql)
            .as("a literal that the CHECK forbids is a 500 at insert time — the ck_ca_source landmine. "
              + "The constant and the constraint have to be asserted against each other somewhere, and "
              + "this is the only place that knows both.")
            .contains("N'" + EvidenceAdapter.LINKED_MODULE + "'");
    }

    @Test
    @DisplayName("every Subject has an entity type — adding one without a mapping fails here, not at runtime")
    void theSubjectMapIsTotal() {
        assertThat(EvidenceAdapter.ENTITY_TYPES.keySet())
            .as("a missing mapping is a null linked_entity_type, which the column rejects — but only "
              + "when somebody exercises the new subject in production")
            .containsExactlyInAnyOrder(Subject.values());
        assertThat(EvidenceAdapter.ENTITY_TYPES.values())
            .as("matched to the schema's own spelling, and to the majority convention of the 21 "
              + "links already on record")
            .containsExactlyInAnyOrder("change_request", "change_condition");
    }

    // ── the tenant check (F2-106) ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⚠ attaching an item from another tenant writes no link (F2-106)")
    void attachingAnItemFromAnotherTenantWritesNoLink() {
        when(items.findById(9001L)).thenReturn(Optional.of(item(9001L, OTHER, "APPROVED")));

        assertThat(adapter.attach(Subject.CHANGE, SUBJ, 9001L, null))
            .as("the peer's addLink would persist this link happily; the service turns the empty "
              + "Optional into a 404 rather than a change that claims foreign evidence")
            .isEmpty();
        verify(links, never()).save(any(EvidenceLink.class));
    }

    @Test
    @DisplayName("attaching an id that does not exist writes no link")
    void attachingAMissingItemWritesNoLink() {
        when(items.findById(4242L)).thenReturn(Optional.empty());

        assertThat(adapter.attach(Subject.CHANGE, SUBJ, 4242L, null)).isEmpty();
        verify(links, never()).save(any(EvidenceLink.class));
    }

    @Test
    @DisplayName("attaching a soft-deleted item writes no link")
    void attachingADeletedItemWritesNoLink() {
        EvidenceItem gone = item(9002L, ORG, "APPROVED");
        gone.setIsDeleted(true);
        when(items.findById(9002L)).thenReturn(Optional.of(gone));

        assertThat(adapter.attach(Subject.CHANGE, SUBJ, 9002L, null))
            .as("@Where covers JPQL, not findById on an entity the caller resolved by primary key")
            .isEmpty();
        verify(links, never()).save(any(EvidenceLink.class));
    }

    // ── writing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a valid attach writes exactly one link, in the caller's tenant, with the peer's vocabulary")
    void aValidAttachWritesOneLink() {
        when(items.findById(9003L)).thenReturn(Optional.of(item(9003L, ORG, "APPROVED")));

        Optional<EvidencePort.EvidenceRef> ref = adapter.attach(Subject.CONDITION, SUBJ, 9003L, "proof");

        ArgumentCaptor<EvidenceLink> saved = ArgumentCaptor.forClass(EvidenceLink.class);
        verify(links).save(saved.capture());
        EvidenceLink l = saved.getValue();
        assertThat(l.getOrganisationId()).as("from TenantContext, never from the caller").isEqualTo(ORG);
        assertThat(l.getEvidenceId()).isEqualTo(9003L);
        assertThat(l.getLinkedModule()).isEqualTo("CHANGE");
        assertThat(l.getLinkedEntityType()).isEqualTo("change_condition");
        assertThat(l.getLinkedEntityId()).isEqualTo(SUBJ);
        assertThat(l.getNotes()).isEqualTo("proof");
        assertThat(ref).isPresent();
        assertThat(ref.get().verified()).isTrue();
    }

    @Test
    @DisplayName("attaching the same item twice is one link — a double-click is not a second artefact")
    void attachingTwiceIsIdempotent() {
        when(items.findById(9003L)).thenReturn(Optional.of(item(9003L, ORG, "APPROVED")));
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9003L, ORG, "change_condition")));

        assertThat(adapter.attach(Subject.CONDITION, SUBJ, 9003L, null))
            .as("still returns the item: the caller asked for it to be attached and it is")
            .isPresent();
        verify(links, never()).save(any(EvidenceLink.class));
    }

    @Test
    @DisplayName("the same id on a change and on a condition are different links, not a duplicate")
    void subjectsWithTheSameIdDoNotCollide() {
        when(items.findById(9003L)).thenReturn(Optional.of(item(9003L, ORG, "APPROVED")));
        // A link already exists for CONDITION 500; change 500 is a different subject that happens to
        // share the number, and the entity type is the only thing telling them apart.
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9003L, ORG, "change_condition")));

        adapter.attach(Subject.CHANGE, SUBJ, 9003L, null);

        ArgumentCaptor<EvidenceLink> saved = ArgumentCaptor.forClass(EvidenceLink.class);
        verify(links).save(saved.capture());
        assertThat(saved.getValue().getLinkedEntityType()).isEqualTo("change_request");
    }

    // ── reading ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reads are filtered by entity type, so a change's evidence is not a condition's")
    void readsAreFilteredByEntityType() {
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9003L, ORG, "change_request"),
                                                         link(9004L, ORG, "change_condition")));
        when(items.findAllById(List.of(9004L))).thenReturn(List.of(item(9004L, ORG, "APPROVED")));

        assertThat(adapter.attached(Subject.CONDITION, SUBJ))
            .as("the repository query keys on module + entity id only; the type filter is ours")
            .extracting(EvidencePort.EvidenceRef::id).containsExactly(9004L);
    }

    @Test
    @DisplayName("a link pointing outside the tenant vanishes from the read")
    void crossTenantLinksAreNotSurfaced() {
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9005L, ORG, "change_request")));
        when(items.findAllById(List.of(9005L))).thenReturn(List.of(item(9005L, OTHER, "APPROVED")));

        assertThat(adapter.attached(Subject.CHANGE, SUBJ))
            .as("F2-106 means a legacy link row can point at a foreign item; such a row must vanish "
              + "rather than surface a foreign title")
            .isEmpty();
    }

    @Test
    @DisplayName("reads come back newest first")
    void readsAreNewestFirst() {
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9006L, ORG, "change_request"),
                                                         link(9008L, ORG, "change_request"),
                                                         link(9007L, ORG, "change_request")));
        when(items.findAllById(any())).thenReturn(List.of(item(9006L, ORG, "APPROVED"),
                                                          item(9008L, ORG, "DRAFT"),
                                                          item(9007L, ORG, "EXPIRED")));

        assertThat(adapter.attached(Subject.CHANGE, SUBJ))
            .extracting(EvidencePort.EvidenceRef::id).containsExactly(9008L, 9007L, 9006L);
    }

    @Test
    @DisplayName("verified is APPROVED and nothing else")
    void onlyApprovedCountsAsVerified() {
        for (String status : List.of("DRAFT", "PENDING_REVIEW", "EXPIRED", "REJECTED", "SUPERSEDED")) {
            when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                    ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9009L, ORG, "change_request")));
            when(items.findAllById(any())).thenReturn(List.of(item(9009L, ORG, status)));

            assertThat(adapter.attached(Subject.CHANGE, SUBJ).get(0).verified())
                .as("%s must not discharge a CAB condition — that is what the review workflow is for",
                    status)
                .isFalse();
        }
        when(items.findAllById(any())).thenReturn(List.of(item(9009L, ORG, "APPROVED")));
        assertThat(adapter.attached(Subject.CHANGE, SUBJ).get(0).verified()).isTrue();
    }

    @Test
    @DisplayName("the projection carries the hash and the file name — the reason for using the subsystem")
    void theProjectionCarriesTheHash() {
        when(links.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
                ORG, "CHANGE", SUBJ)).thenReturn(List.of(link(9010L, ORG, "change_request")));
        when(items.findAllById(any())).thenReturn(List.of(item(9010L, ORG, "APPROVED")));

        EvidencePort.EvidenceRef r = adapter.attached(Subject.CHANGE, SUBJ).get(0);

        assertThat(r.sha256()).isEqualTo("sha-9010");
        assertThat(r.fileName()).isEqualTo("dr-9010.pdf");
        assertThat(r.externalId()).as("the stable id an auditor cites").isNotBlank();
        assertThat(r.titleAr()).as("house rule 7 — bilingual all the way to the UI").isNotBlank();
    }

    // ── optional wiring ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("with the evidence repositories absent the adapter is inert, not explosive")
    void anUnwiredAdapterIsInert() {
        EvidenceAdapter unwired = new EvidenceAdapter(null, null);

        assertThat(unwired.attached(Subject.CHANGE, SUBJ)).isEmpty();
        assertThat(unwired.attach(Subject.CHANGE, SUBJ, 9001L, null)).isEmpty();
    }

    @Test
    @DisplayName("null arguments are refused rather than written as nulls")
    void nullArgumentsAreRefused() {
        assertThat(adapter.attach(Subject.CHANGE, null, 9001L, null)).isEmpty();
        assertThat(adapter.attach(Subject.CHANGE, SUBJ, null, null)).isEmpty();
        assertThat(adapter.attach(null, SUBJ, 9001L, null)).isEmpty();
        assertThat(adapter.attached(null, SUBJ)).isEmpty();
        verify(links, never()).save(any(EvidenceLink.class));
    }
}
