package com.tss.grc.module.change_adapters;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.spi.EvidencePort;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.entity.EvidenceLink;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S8a / CE-9 — {@link EvidencePort} over {@code module.evidence}, the platform evidence subsystem.
 *
 * <p><b>The peer's vocabulary lives here, not in the port.</b> {@code linked_module = "CHANGE"} (V353
 * widened {@code CK_ev_links_module} 9 → 10 to permit it) and the two {@code linked_entity_type}
 * strings are the subsystem's spellings, matched to the existing rows' convention: the 21 links on
 * record use lower snake case for platform tables ({@code risk}, {@code incident},
 * {@code processing_activity}) and CamelCase only where a module chose to key on its Java class name.
 * Change follows the majority and the schema: {@code change_request} and {@code change_condition}.
 *
 * <p><b>⚠ Validates on the way in, deliberately — and it found two gaps in the peer while doing so
 * (F2-106).</b> {@code EvidenceItemService.addLink} takes {@code EvidenceLinkRequest} with only
 * {@code @NotBlank} on {@code linkedModule} and {@code @NotNull} on {@code evidenceId}: it never
 * checks that the module is one the CHECK permits (an unknown value surfaces as a <b>500</b> from
 * {@code CK_ev_links_module} — the {@code ck_ca_source} landmine in a second place), and it never
 * checks that the evidence item exists or belongs to the caller's tenant (a link row can be written
 * pointing at another tenant's item; the read side filters the link's own org, so the effect is a
 * silently-dropped reference rather than a disclosure). This adapter therefore resolves the item
 * itself before linking, which is also what its own contract requires — "attach an <em>existing</em>
 * evidence item".
 */
@Slf4j
@Component
public class EvidenceAdapter implements EvidencePort {

    /** The subsystem's own name for a change-module link. Widened into the CHECK by V353. */
    static final String LINKED_MODULE = "CHANGE";

    /** {@code linked_entity_type} per subject — the peer's spelling, matched to the schema. */
    static final Map<Subject, String> ENTITY_TYPES = Map.of(
        Subject.CHANGE,    "change_request",
        Subject.CONDITION, "change_condition");

    private final EvidenceItemRepository items;
    private final EvidenceLinkRepository links;

    public EvidenceAdapter(@Autowired(required = false) EvidenceItemRepository items,
                           @Autowired(required = false) EvidenceLinkRepository links) {
        this.items = items;
        this.links = links;
    }

    @Override
    @Transactional
    public Optional<EvidenceRef> attach(Subject subject, Long subjectId, Long evidenceId, String notes) {
        if (items == null || links == null || subject == null || subjectId == null || evidenceId == null)
            return Optional.empty();
        Long org = TenantContext.getCurrentTenant();
        // Resolve first: the port promises an EXISTING item, and the peer's addLink would happily
        // persist a link to an id in another tenant (F2-106).
        Optional<EvidenceItem> found = items.findById(evidenceId)
            .filter(e -> org != null && org.equals(e.getOrganisationId()))
            .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()));
        if (found.isEmpty()) {
            log.debug("S8a: evidence {} does not resolve in organisation {} — not linked", evidenceId, org);
            return Optional.empty();
        }
        String entityType = ENTITY_TYPES.get(subject);

        // Idempotent: attaching the same item to the same subject twice is a double-click, not a
        // second piece of evidence, and a duplicate row would double every count the guard reads.
        boolean already = links
            .findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(org, LINKED_MODULE, subjectId)
            .stream()
            .anyMatch(l -> evidenceId.equals(l.getEvidenceId()) && entityType.equals(l.getLinkedEntityType()));
        if (!already) {
            EvidenceLink link = new EvidenceLink();
            link.setOrganisationId(org);
            link.setEvidenceId(evidenceId);
            link.setLinkedModule(LINKED_MODULE);
            link.setLinkedEntityType(entityType);
            link.setLinkedEntityId(subjectId);
            link.setNotes(notes);
            links.save(link);
        }
        return Optional.of(ref(found.get()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceRef> attached(Subject subject, Long subjectId) {
        if (items == null || links == null || subject == null || subjectId == null) return List.of();
        Long org = TenantContext.getCurrentTenant();
        String entityType = ENTITY_TYPES.get(subject);
        List<Long> ids = links
            .findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(org, LINKED_MODULE, subjectId)
            .stream()
            .filter(l -> entityType.equals(l.getLinkedEntityType()))
            .map(EvidenceLink::getEvidenceId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) return List.of();
        // Re-filter by tenant on the ITEM as well, not only on the link: F2-106 means a legacy link
        // row can point outside the tenant, and such a row must vanish from the read rather than
        // surface a foreign title.
        return items.findAllById(ids).stream()
            .filter(e -> org != null && org.equals(e.getOrganisationId()))
            .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
            .sorted(Comparator.comparing(EvidenceItem::getId).reversed())
            .map(EvidenceAdapter::ref)
            .toList();
    }

    /**
     * {@code verified} is {@code APPROVED} and nothing else.
     *
     * <p>Measured identically in both databases: of <b>78 live items</b>, 29 are APPROVED, 29 EXPIRED,
     * 15 PENDING_REVIEW, 4 DRAFT and 1 REJECTED. Counting PENDING_REVIEW as verified would let
     * evidence somebody merely uploaded discharge a condition the CAB imposed; counting EXPIRED would
     * let evidence that has aged out do it. Both are the distinction the subsystem's review workflow
     * exists to draw — and EXPIRED alone is 29 rows, so it is not a hypothetical.
     */
    private static EvidenceRef ref(EvidenceItem e) {
        return new EvidenceRef(
            e.getId(),
            e.getExternalId() == null ? null : e.getExternalId().toString(),
            e.getTitleEn(), e.getTitleAr(),
            e.getEvidenceType(), e.getStatus(),
            "APPROVED".equals(e.getStatus()),
            e.getFileName(), e.getEvidenceHash());
    }
}
