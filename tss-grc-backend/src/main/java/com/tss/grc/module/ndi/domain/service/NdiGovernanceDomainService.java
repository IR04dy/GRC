package com.tss.grc.module.ndi.domain.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.domain.entity.NdiGovernanceDomain;
import com.tss.grc.module.ndi.domain.repository.NdiGovernanceDomainRepository;
import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import com.tss.grc.module.ndi.maturity.repository.NdiMaturityAssessmentRepository;
import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import com.tss.grc.module.ndi.opex.repository.NdiOperationalExcellenceKpiRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * NDI Governance Domain service — slice 6.
 *
 * <p>Owns the master catalog of governance domains and the cross-slice
 * rollup that combines maturity, compliance and operational-excellence
 * scores into a single per-domain (and overall) NDI index.
 */
@Service
@Transactional
public class NdiGovernanceDomainService {

    private final NdiGovernanceDomainRepository domains;
    private final NdiMaturityAssessmentRepository maturity;
    private final NdiComplianceAssessmentRepository compliance;
    private final NdiOperationalExcellenceKpiRepository opex;
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;

    public NdiGovernanceDomainService(NdiGovernanceDomainRepository domains,
                                      NdiMaturityAssessmentRepository maturity,
                                      NdiComplianceAssessmentRepository compliance,
                                      NdiOperationalExcellenceKpiRepository opex,
                                      com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit) {
        this.domains = domains;
        this.maturity = maturity;
        this.compliance = compliance;
        this.opex = opex;
        this.audit = audit;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── CRUD ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NdiGovernanceDomain> list(boolean activeOnly) {
        return activeOnly
            ? domains.findAllByOrganisationIdAndIsActiveOrderBySortOrderAscIdAsc(org(), true)
            : domains.findAllByOrganisationIdOrderBySortOrderAscIdAsc(org());
    }

    @Transactional(readOnly = true)
    public NdiGovernanceDomain get(Long id) {
        return domains.findById(id)
            .filter(d -> Objects.equals(d.getOrganisationId(), org()))
            .orElseThrow(() -> new EntityNotFoundException("Domain not found: " + id));
    }

    public NdiGovernanceDomain create(NdiGovernanceDomain body) {
        if (body.getCode() == null || body.getCode().isBlank())
            throw new IllegalArgumentException("code is required");
        if (body.getNameEn() == null || body.getNameEn().isBlank())
            throw new IllegalArgumentException("nameEn is required");
        if (domains.findByOrganisationIdAndCode(org(), body.getCode()).isPresent())
            throw new IllegalArgumentException("Domain code already exists: " + body.getCode());
        body.setId(null);
        body.setOrganisationId(org());
        validateWeights(body);
        return domains.save(body);
    }

    public NdiGovernanceDomain update(Long id, NdiGovernanceDomain body) {
        NdiGovernanceDomain d = get(id);
        if (body.getNameEn() != null)        d.setNameEn(body.getNameEn());
        if (body.getNameAr() != null)        d.setNameAr(body.getNameAr());
        if (body.getDescriptionEn() != null) d.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr() != null) d.setDescriptionAr(body.getDescriptionAr());
        if (body.getDomainAliases() != null) d.setDomainAliases(body.getDomainAliases());
        if (body.getWeight() != null)        d.setWeight(body.getWeight());
        if (body.getSortOrder() != null)     d.setSortOrder(body.getSortOrder());
        if (body.getWeightMaturity() != null)   d.setWeightMaturity(body.getWeightMaturity());
        if (body.getWeightCompliance() != null) d.setWeightCompliance(body.getWeightCompliance());
        if (body.getWeightOpex() != null)       d.setWeightOpex(body.getWeightOpex());
        if (body.getOwnerName() != null)     d.setOwnerName(body.getOwnerName());
        if (body.getOwnerEmail() != null)    d.setOwnerEmail(body.getOwnerEmail());
        if (body.getIsActive() != null)      d.setIsActive(body.getIsActive());
        if (body.getNotes() != null)         d.setNotes(body.getNotes());
        validateWeights(d);
        return domains.save(d);
    }

    public void delete(Long id) {
        NdiGovernanceDomain d = get(id);
        d.setIsDeleted(true);
        domains.save(d);
    }

    /** Flag domain as no longer placeholder — admin must do this only after an official import. */
    public NdiGovernanceDomain markOfficial(Long id) {
        NdiGovernanceDomain d = get(id);
        d.setIsPlaceholder(false);
        NdiGovernanceDomain saved = domains.save(d);
        audit.publish("DOMAIN", saved.getId(), saved.getCode(), null,
            "MARK_OFFICIAL", "Domain " + saved.getCode() + " flagged as official (placeholder banner removed)");
        return saved;
    }

    /** Reset a domain to placeholder (e.g. for re-import). */
    public NdiGovernanceDomain markPlaceholder(Long id) {
        NdiGovernanceDomain d = get(id);
        d.setIsPlaceholder(true);
        NdiGovernanceDomain saved = domains.save(d);
        audit.publish("DOMAIN", saved.getId(), saved.getCode(), null,
            "MARK_PLACEHOLDER", "WARN", "Domain " + saved.getCode() + " reset to placeholder");
        return saved;
    }

    private void validateWeights(NdiGovernanceDomain d) {
        BigDecimal sum = d.getWeightMaturity().add(d.getWeightCompliance()).add(d.getWeightOpex());
        BigDecimal diff = sum.subtract(BigDecimal.ONE).abs();
        if (diff.compareTo(new BigDecimal("0.0050")) > 0)
            throw new IllegalArgumentException(
                "Component weights must sum to 1.00 (±0.005). Got " + sum + " for domain " + d.getCode());
    }

    // ─── Rollup ────────────────────────────────────────────────────

    public record DomainRollup(
        Long domainId,
        String code,
        String nameEn,
        String nameAr,
        BigDecimal weight,
        boolean isPlaceholder,
        int maturityCount,
        BigDecimal maturityAvgLevel,    // 0..5
        BigDecimal maturityScorePct,    // 0..100 (level/5 × 100)
        int complianceCount,
        BigDecimal compliancePct,       // 0..100 mean of scored items
        int opexCount,
        BigDecimal opexScorePct,        // 0..100 mean of per-KPI progress fraction
        BigDecimal blendedScorePct,     // weighted blend per domain weights
        String[] unmatchedDomains        // upstream domains not bound to any domain row
    ) {}

    public record CycleRollup(
        Long cycleId,
        BigDecimal overallNdiPct,
        BigDecimal totalWeight,
        List<DomainRollup> domains
    ) {}

    @Transactional(readOnly = true)
    public CycleRollup rollupForCycle(Long cycleId) {
        Long orgId = org();
        List<NdiGovernanceDomain> domainList = domains.findAllByOrganisationIdAndIsActiveOrderBySortOrderAscIdAsc(orgId, true);

        List<NdiMaturityAssessment> mat = maturity.findByOrganisationIdAndCycleId(orgId, cycleId);
        List<NdiComplianceAssessment> cmp = compliance.findByOrganisationIdAndCycleId(orgId, cycleId);
        List<NdiOperationalExcellenceKpi> ops = opex.findByOrganisationIdAndCycleId(orgId, cycleId);

        // Track every upstream domain string we've seen so we can flag unmatched ones.
        Set<String> allUpstream = new HashSet<>();
        for (var m : mat)    if (m.getDomain()   != null) allUpstream.add(m.getDomain().trim().toLowerCase(Locale.ROOT));
        for (var c : cmp)    if (c.getDomain()   != null) allUpstream.add(c.getDomain().trim().toLowerCase(Locale.ROOT));
        for (var k : ops)    if (k.getCategory() != null) allUpstream.add(k.getCategory().trim().toLowerCase(Locale.ROOT));
        Set<String> matchedUpstream = new HashSet<>();

        List<DomainRollup> result = new ArrayList<>();
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;

        for (NdiGovernanceDomain d : domainList) {
            Set<String> aliases = aliasSet(d);

            // Maturity rollup (avg of current_level, 0..5)
            int matCount = 0;
            int matSum = 0;
            for (var m : mat) {
                if (m.getCurrentLevel() != null && aliases.contains(norm(m.getDomain()))) {
                    matSum += m.getCurrentLevel();
                    matCount++;
                    matchedUpstream.add(norm(m.getDomain()));
                }
            }
            BigDecimal matAvg = matCount == 0 ? null
                : BigDecimal.valueOf((double) matSum / matCount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal matPct = matAvg == null ? null
                : matAvg.multiply(BigDecimal.valueOf(20)).setScale(2, RoundingMode.HALF_UP);

            // Compliance rollup (avg of complianceScore where set)
            int cmpCount = 0;
            BigDecimal cmpSum = BigDecimal.ZERO;
            for (var c : cmp) {
                if (aliases.contains(norm(c.getDomain()))) {
                    matchedUpstream.add(norm(c.getDomain()));
                    if (c.getComplianceScore() != null) {
                        cmpSum = cmpSum.add(c.getComplianceScore());
                        cmpCount++;
                    }
                }
            }
            BigDecimal cmpPct = cmpCount == 0 ? null
                : cmpSum.divide(BigDecimal.valueOf(cmpCount), 2, RoundingMode.HALF_UP);

            // Opex rollup (mean of direction-aware progress fraction × 100)
            int opCount = 0;
            BigDecimal opSum = BigDecimal.ZERO;
            for (var k : ops) {
                if (aliases.contains(norm(k.getCategory()))) {
                    matchedUpstream.add(norm(k.getCategory()));
                    BigDecimal frac = progressFraction(k);
                    if (frac != null) {
                        opSum = opSum.add(frac);
                        opCount++;
                    }
                }
            }
            BigDecimal opPct = opCount == 0 ? null
                : opSum.multiply(BigDecimal.valueOf(100))
                       .divide(BigDecimal.valueOf(opCount), 2, RoundingMode.HALF_UP);

            BigDecimal blended = blend(matPct, cmpPct, opPct,
                d.getWeightMaturity(), d.getWeightCompliance(), d.getWeightOpex());

            if (blended != null) {
                weightedSum = weightedSum.add(blended.multiply(d.getWeight()));
                weightTotal = weightTotal.add(d.getWeight());
            }

            result.add(new DomainRollup(
                d.getId(), d.getCode(), d.getNameEn(), d.getNameAr(),
                d.getWeight(), d.getIsPlaceholder(),
                matCount, matAvg, matPct,
                cmpCount, cmpPct,
                opCount, opPct,
                blended,
                new String[0]   // per-domain unmatched list reserved for future use
            ));
        }

        BigDecimal overall = weightTotal.signum() == 0 ? BigDecimal.ZERO
            : weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);

        // Inject unmatched-upstream list into the first rollup record's array
        // so the UI can show a 'these domain strings have no mapping' banner.
        allUpstream.removeAll(matchedUpstream);
        if (!result.isEmpty()) {
            DomainRollup first = result.get(0);
            result.set(0, new DomainRollup(
                first.domainId, first.code, first.nameEn, first.nameAr,
                first.weight, first.isPlaceholder,
                first.maturityCount, first.maturityAvgLevel, first.maturityScorePct,
                first.complianceCount, first.compliancePct,
                first.opexCount, first.opexScorePct, first.blendedScorePct,
                allUpstream.toArray(new String[0])
            ));
        }

        return new CycleRollup(cycleId, overall, weightTotal, result);
    }

    private Set<String> aliasSet(NdiGovernanceDomain d) {
        Set<String> set = new HashSet<>();
        set.add(norm(d.getNameEn()));
        if (d.getDomainAliases() != null && !d.getDomainAliases().isBlank()) {
            for (String alias : d.getDomainAliases().split("\\|")) {
                String a = norm(alias);
                if (!a.isEmpty()) set.add(a);
            }
        }
        return set;
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Direction-aware progress fraction [0..1] mirroring lib/ndiOpex.ts. */
    private BigDecimal progressFraction(NdiOperationalExcellenceKpi k) {
        if (k.getActual() == null || k.getTarget() == null) return null;
        BigDecimal baseline = k.getBaseline() == null ? BigDecimal.ZERO : k.getBaseline();
        BigDecimal denom = k.getTarget().subtract(baseline);
        if (denom.signum() == 0) {
            return k.getActual().compareTo(k.getTarget()) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        BigDecimal raw = k.getActual().subtract(baseline)
            .divide(denom, 4, RoundingMode.HALF_UP);
        if (raw.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (raw.compareTo(BigDecimal.ONE) > 0)  return BigDecimal.ONE;
        return raw;
    }

    /** Weighted blend, gracefully skipping any null components by reallocating their weight pro-rata. */
    private BigDecimal blend(BigDecimal mat, BigDecimal cmp, BigDecimal op,
                             BigDecimal wm, BigDecimal wc, BigDecimal wo) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal wsum = BigDecimal.ZERO;
        if (mat != null) { sum = sum.add(mat.multiply(wm)); wsum = wsum.add(wm); }
        if (cmp != null) { sum = sum.add(cmp.multiply(wc)); wsum = wsum.add(wc); }
        if (op  != null) { sum = sum.add(op .multiply(wo)); wsum = wsum.add(wo); }
        if (wsum.signum() == 0) return null;
        return sum.divide(wsum, 2, RoundingMode.HALF_UP);
    }
}
