package com.tss.grc.module.platform.regulatory.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlMapping;
import com.tss.grc.module.platform.controls.repository.ControlMappingRepository;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compliance Posture service — Framework Intersection slices F5 + F6.
 *
 * <p>Pivots the spine (controls + mappings + citations + authority
 * documents) into the two views the GRC team actually needs:
 *
 * <ul>
 *   <li><b>Per-document coverage</b> — for each authority document,
 *       what % of its citations have at least one mapped control,
 *       split by coverage strength (FULL / PARTIAL / COMPENSATING),
 *       plus the list of gap citations (zero controls).</li>
 *   <li><b>Posture heatmap</b> — rows = authority documents,
 *       columns = domains, cell = number of mapped controls coming
 *       from that domain. Feeds the F6 hub.</li>
 *   <li><b>Citation resolution</b> — for the frontend to render a
 *       mapping table without N+1 calls (F5 component).</li>
 * </ul>
 *
 * <p>All math is in memory because the volumes are small (≤ 100K
 * mapping rows × ≤ 50 authority documents × ≤ 7 domains for any
 * realistic tenant). Pre-aggregation tables can come later if the
 * posture page ever shows up in a slow-query report.
 */
@Service
@RequiredArgsConstructor
public class CompliancePostureService {

    private final AuthorityDocumentRepository documents;
    private final AuthorityDocumentCitationRepository citations;
    private final ControlMappingRepository mappings;
    private final ControlRepository controls;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── Citation resolution (F5 helper) ──────────────────────────

    public record CitationView(
        Long id, Long authorityDocumentId, String citationCode,
        String titleEn, String titleAr,
        String authorityCode, String authorityFamily) {}

    @Transactional(readOnly = true)
    public CitationView getCitationView(Long citationId) {
        Long o = org();
        AuthorityDocumentCitation c = citations.findByIdAndOrganisationId(citationId, o)
            .orElseThrow(() -> new EntityNotFoundException("Citation not found: " + citationId));
        AuthorityDocument d = documents.findByIdAndOrganisationId(c.getAuthorityDocumentId(), o)
            .orElseThrow(() -> new EntityNotFoundException("Authority document not found: " + c.getAuthorityDocumentId()));
        return new CitationView(
            c.getId(), d.getId(), c.getCitationCode(),
            c.getTitleEn(), c.getTitleAr(),
            d.getCode(), d.getFamily());
    }

    // ─── Per-document coverage (F5 + F6) ──────────────────────────

    public record DocumentCoverage(
        Long authorityDocumentId, String code, String family,
        String titleEn, String titleAr,
        int totalCitations,
        int mappedCitations,
        int fullCoverage, int partialCoverage, int compensatingCoverage,
        int gapCitations,
        Map<String, Integer> controlsByDomain,
        List<CitationView> gaps,
        double coveragePct) {}

    @Transactional(readOnly = true)
    public DocumentCoverage coverageFor(Long authorityDocumentId) {
        Long o = org();
        AuthorityDocument d = documents.findByIdAndOrganisationId(authorityDocumentId, o)
            .orElseThrow(() -> new EntityNotFoundException("Authority document not found: " + authorityDocumentId));
        List<AuthorityDocumentCitation> cits = citations
            .findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(o, authorityDocumentId);

        // Group all org mappings by citationId once, then walk citations.
        Map<Long, List<ControlMapping>> byCit = new HashMap<>();
        for (AuthorityDocumentCitation cit : cits) {
            List<ControlMapping> ms = mappings.findByOrganisationIdAndCitationIdOrderByIdAsc(o, cit.getId());
            byCit.put(cit.getId(), ms);
        }

        // Pre-fetch all controls referenced by any mapping (single query
        // would be ideal — for now the volumes are small enough to pull
        // per id).
        Set<Long> controlIds = new HashSet<>();
        for (List<ControlMapping> ms : byCit.values()) for (ControlMapping m : ms) controlIds.add(m.getControlId());
        Map<Long, Control> ctrlById = new HashMap<>();
        for (Long id : controlIds)
            controls.findByIdAndOrganisationId(id, o).ifPresent(c -> ctrlById.put(id, c));

        int mapped = 0, full = 0, partial = 0, compensating = 0, gaps = 0;
        Map<String, Integer> byDomain = new TreeMap<>();
        List<CitationView> gapList = new ArrayList<>();
        for (AuthorityDocumentCitation c : cits) {
            List<ControlMapping> ms = byCit.getOrDefault(c.getId(), List.of());
            if (ms.isEmpty()) {
                gaps++;
                if (gapList.size() < 25)   // cap the payload — gap browsing happens through the citations endpoint
                    gapList.add(new CitationView(
                        c.getId(), d.getId(), c.getCitationCode(),
                        c.getTitleEn(), c.getTitleAr(), d.getCode(), d.getFamily()));
                continue;
            }
            mapped++;
            // Coverage breakdown — one citation can be FULL by one
            // control AND PARTIAL by another; count strongest.
            boolean hasFull = false, hasPartial = false, hasComp = false;
            for (ControlMapping m : ms) {
                if      ("FULL".equals(m.getCoverage()))         hasFull = true;
                else if ("PARTIAL".equals(m.getCoverage()))      hasPartial = true;
                else if ("COMPENSATING".equals(m.getCoverage())) hasComp = true;
                Control ctrl = ctrlById.get(m.getControlId());
                if (ctrl != null) byDomain.merge(ctrl.getDomain(), 1, Integer::sum);
            }
            if (hasFull) full++;
            else if (hasPartial) partial++;
            else if (hasComp) compensating++;
        }
        double pct = cits.isEmpty() ? 0.0 : Math.round(((double) mapped / cits.size()) * 1000.0) / 10.0;

        return new DocumentCoverage(
            d.getId(), d.getCode(), d.getFamily(),
            d.getTitleEn(), d.getTitleAr(),
            cits.size(), mapped, full, partial, compensating, gaps,
            byDomain, gapList, pct);
    }

    // ─── Posture heatmap (F6) ─────────────────────────────────────

    public record PostureRow(
        Long authorityDocumentId, String code, String family,
        String titleEn, String titleAr,
        int totalCitations, int mappedCitations, double coveragePct,
        Map<String, Integer> controlsByDomain) {}

    public record PostureHeatmap(
        List<PostureRow> rows,
        List<String> domains,
        Map<String, Long> familyTotals) {}

    @Transactional(readOnly = true)
    public PostureHeatmap heatmap(String familyFilter) {
        Long o = org();
        List<AuthorityDocument> docs = (familyFilter != null && !familyFilter.isBlank())
            ? documents.findByOrganisationIdAndFamilyOrderByCodeAsc(o, familyFilter)
            : documents.findByOrganisationIdOrderByFamilyAscCodeAsc(o);

        List<PostureRow> rows = new ArrayList<>();
        Set<String> domainsSeen = new HashSet<>();
        Map<String, Long> familyTotals = new TreeMap<>();
        for (AuthorityDocument d : docs) {
            familyTotals.merge(d.getFamily(), 1L, Long::sum);
            DocumentCoverage c = coverageFor(d.getId());
            rows.add(new PostureRow(
                d.getId(), d.getCode(), d.getFamily(),
                d.getTitleEn(), d.getTitleAr(),
                c.totalCitations(), c.mappedCitations(), c.coveragePct(),
                c.controlsByDomain()));
            domainsSeen.addAll(c.controlsByDomain().keySet());
        }
        List<String> domains = new ArrayList<>(domainsSeen);
        domains.sort(Comparator.naturalOrder());
        return new PostureHeatmap(rows, domains, familyTotals);
    }

    // ─── Per-domain rollup (F5 component) ─────────────────────────

    public record DomainAlignment(
        String domain,
        List<DocumentCoverage> documents,
        int totalCitations, int mappedCitations, int gapCitations,
        double averageCoveragePct) {}

    @Transactional(readOnly = true)
    public DomainAlignment domainAlignment(String domain, List<String> familyFilter) {
        Long o = org();
        // For each authority document whose family is in the filter,
        // compute coverage but only count citations covered by controls
        // in THIS domain (the alignment lens).
        List<AuthorityDocument> docs;
        if (familyFilter == null || familyFilter.isEmpty()) {
            docs = documents.findByOrganisationIdOrderByFamilyAscCodeAsc(o);
        } else {
            docs = new ArrayList<>();
            for (String fam : familyFilter)
                docs.addAll(documents.findByOrganisationIdAndFamilyOrderByCodeAsc(o, fam));
        }
        List<DocumentCoverage> per = new ArrayList<>();
        int total = 0, mapped = 0, gap = 0;
        double pctSum = 0.0;
        int pctCount = 0;
        for (AuthorityDocument d : docs) {
            DocumentCoverage all = coverageFor(d.getId());
            int domainHere = all.controlsByDomain().getOrDefault(domain, 0);
            if (domainHere == 0 && all.totalCitations() == 0) continue;
            per.add(all);
            total += all.totalCitations();
            mapped += all.mappedCitations();
            gap += all.gapCitations();
            pctSum += all.coveragePct();
            pctCount++;
        }
        double avg = pctCount == 0 ? 0.0
            : Math.round((pctSum / pctCount) * 10.0) / 10.0;
        return new DomainAlignment(domain, per, total, mapped, gap, avg);
    }
}
