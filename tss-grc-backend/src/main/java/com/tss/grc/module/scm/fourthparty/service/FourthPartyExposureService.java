package com.tss.grc.module.scm.fourthparty.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.common.validation.OrderedVocabulary;
import com.tss.grc.module.scm.service.VendorReferenceResolver;
import com.tss.grc.module.scm.fourthparty.dto.FourthPartyExposureDtos.CreateRequest;
import com.tss.grc.module.scm.fourthparty.dto.FourthPartyExposureDtos.UpdateRequest;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.fourthparty.repository.FourthPartyExposureRepository;
import com.tss.grc.module.tprm.automation.TprmAutomationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service surface for {@link FourthPartyExposure}.
 *
 * <p>{@code concentration_risk} is an <b>assessment</b> of how systemically important a fourth party
 * is, which the platform may raise but never lower: when the same fourth party appears behind ≥3
 * live primaries it is auto-elevated to at least HIGH; ≥2 → at least MEDIUM. See
 * {@link ConcentrationTier} for the ladder and for what the word "elevated" cost when the code said
 * it and did something else (F2-229).
 */
@Service
@RequiredArgsConstructor
public class FourthPartyExposureService {

    /**
     * The four vocabularies this register enforces, each naming the {@code CHECK} V377 adds. Before
     * V377 the table had <b>no check constraints at all</b>, so these sets were the only narrowing
     * and nothing compared them with the column.
     *
     * <p>{@code require} rather than {@code coerce} throughout, per F2-182's test: the previous
     * hand-rolled sets already <em>refused</em> anything outside the vocabulary, so every value that
     * succeeds today is a case-variant of a legal token and refusing cannot break a working request.
     * What changes is that {@code 'high'} is now normalised to {@code HIGH} instead of 400-ing, and
     * the 400s that remain name the field and the allowed set.
     */
    private static final ClosedVocabulary DEPENDENCY_TYPE = ClosedVocabulary.of(
            "dependencyType", "CK_fpe_dependency_type",
            "DATA_PROCESSING", "INFRASTRUCTURE", "SOFTWARE", "LOGISTICS", "FINANCIAL", "SUPPORT");
    /**
     * F2-230 promoted this from a plain {@code ClosedVocabulary} to an {@link OrderedVocabulary}, and
     * the reason is a hole F2-230's own anti-vacuity floor exposed: F2-229 wrote a {@code CASE} ladder
     * for this column into {@code findRegisterByConcentration}'s {@code @Query} — the register's
     * secondary sort — and there was <b>no declaration anywhere for that ladder to be checked
     * against</b>. The SQL held an order nothing in Java could contradict.
     *
     * <p>🔑 A ranking written only in SQL is still a second copy of a vocabulary's order; it just has
     * nothing to disagree with. {@code OrderedVocabularySortContractTest} compares it now.
     */
    public static final OrderedVocabulary DOWNSTREAM_CRITICALITY_LADDER = OrderedVocabulary.of(
            "downstreamCriticality", "CK_fpe_downstream_criticality",
            "LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final ClosedVocabulary DOWNSTREAM_CRITICALITY =
            DOWNSTREAM_CRITICALITY_LADDER.vocabulary();
    static final ClosedVocabulary STATUS = ClosedVocabulary.of(
            "status", "CK_fpe_status", "DISCOVERED", "VALIDATED", "MITIGATED", "RETIRED");

    /** The workflow states this service writes. Server-owned: no caller supplies a status. */
    static final String DISCOVERED = "DISCOVERED";
    static final String VALIDATED = "VALIDATED";
    static final String MITIGATED = "MITIGATED";
    static final String RETIRED = "RETIRED";

    private final FourthPartyExposureRepository repo;

    /** F2-246 - the single writer of this register's (vendor id, vendor name) pair. */
    private final VendorReferenceResolver vendorRefs;
    @Autowired(required = false) private TprmAutomationService automation;

    @Transactional(readOnly = true)
    public Page<FourthPartyExposure> list(Pageable pageable) {
        return repo.findRegisterByConcentration(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<FourthPartyExposure> listByStatus(String status, Pageable pageable) {
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), status, pageable);
    }

    @Transactional(readOnly = true)
    public FourthPartyExposure get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("FourthPartyExposure " + id));
    }

    @Transactional
    public FourthPartyExposure create(CreateRequest req) {
        // Build a fresh entity from the DTO. Server-controlled fields (id,
        // organisationId, status, concentrationRisk, soft-delete, audit and
        // validation columns) are NOT taken from the client (SEC-FIN-004) —
        // they keep their entity defaults / are stamped here.
        FourthPartyExposure e = new FourthPartyExposure();
        // F2-246 - the PRIMARY vendor is ours and has an id column, so it must resolve;
        // fourthPartyName is the downstream party, which by design has no id column
        // because a fourth party is not one of our vendors.
        // The tenant is read directly rather than from the half-built row: a resolve
        // placed above the organisation stamp would pass null, the filter could never
        // match, and EVERY linked create would answer 404. Found by the live probe -
        // and the probe legs that expect a refusal cannot see it, because a refusal
        // caused by a null tenant looks exactly like a correct one.
        var vendor = vendorRefs.resolve(req.primaryVendorId(), req.primaryVendorName(),
                TenantContext.getCurrentTenant());
        e.setPrimaryVendorId(vendor.id());
        e.setPrimaryVendorName(vendor.name());
        e.setFourthPartyName(req.fourthPartyName());
        e.setFourthPartyCountry(req.fourthPartyCountry());
        e.setDependencyType(DEPENDENCY_TYPE.require(req.dependencyType()));
        e.setDownstreamCriticality(DOWNSTREAM_CRITICALITY.require(req.downstreamCriticality()));
        e.setServiceDescriptionEn(req.serviceDescriptionEn());
        e.setServiceDescriptionAr(req.serviceDescriptionAr());
        e.setDataCategoriesShared(req.dataCategoriesShared());
        e.setContractChainClauses(req.contractChainClauses());
        if (req.subContractingAllowed() != null) e.setSubContractingAllowed(req.subContractingAllowed());
        if (req.flowDownClausesPresent() != null) e.setFlowDownClausesPresent(req.flowDownClausesPresent());
        e.setMitigationNotesEn(req.mitigationNotesEn());
        e.setMitigationNotesAr(req.mitigationNotesAr());
        validate(e);
        e.setId(null);
        e.setOrganisationId(TenantContext.getCurrentTenant());
        e.setStatus(DISCOVERED);             // force initial workflow state
        // The initial assessment. recomputeConcentration() may RAISE it if this fourth party already
        // sits behind two or more live primaries; nothing lowers it afterwards.
        e.setConcentrationRisk(ConcentrationTier.ladder().get(0));
        e.setIsDeleted((short) 0);
        e.setIsActive((short) 1);
        FourthPartyExposure saved = repo.save(e);
        recomputeConcentration();
        return repo.findByIdAndOrganisationId(saved.getId(), TenantContext.getCurrentTenant())
                .orElse(saved);
    }

    @Transactional
    public FourthPartyExposure update(Long id, UpdateRequest req) {
        FourthPartyExposure existing = get(id);
        // F2-246 - a LINKED row's label belongs to the vendor register: an echo is
        // accepted so a form that round-trips the whole row keeps working, a CHANGE is
        // refused. Without this, the create could be closed and an update could still
        // make a resolved reference describe the wrong vendor.
        if (req.primaryVendorName() != null) {
            existing.setPrimaryVendorName(vendorRefs.relabel(
                    existing.getPrimaryVendorId(), existing.getPrimaryVendorName(),
                    req.primaryVendorName()));
        }
        if (req.fourthPartyName() != null) existing.setFourthPartyName(req.fourthPartyName());
        existing.setFourthPartyCountry(req.fourthPartyCountry());
        if (req.dependencyType() != null)
            existing.setDependencyType(DEPENDENCY_TYPE.require(req.dependencyType()));
        if (req.downstreamCriticality() != null)
            existing.setDownstreamCriticality(DOWNSTREAM_CRITICALITY.require(req.downstreamCriticality()));
        if (req.serviceDescriptionEn() != null) existing.setServiceDescriptionEn(req.serviceDescriptionEn());
        existing.setServiceDescriptionAr(req.serviceDescriptionAr());
        existing.setDataCategoriesShared(req.dataCategoriesShared());
        existing.setContractChainClauses(req.contractChainClauses());
        if (req.subContractingAllowed() != null) existing.setSubContractingAllowed(req.subContractingAllowed());
        if (req.flowDownClausesPresent() != null) existing.setFlowDownClausesPresent(req.flowDownClausesPresent());
        existing.setMitigationNotesEn(req.mitigationNotesEn());
        existing.setMitigationNotesAr(req.mitigationNotesAr());
        validate(existing);
        FourthPartyExposure saved = repo.save(existing);
        recomputeConcentration();
        return repo.findByIdAndOrganisationId(saved.getId(), TenantContext.getCurrentTenant())
                .orElse(saved);
    }

    @Transactional
    /**
     * F2-236 — the validator is the authenticated principal. The controller defaulted to the
     * literal {@code "system"}, which reads as an automated validation of a SAMA fourth-party
     * concentration record when a human performed it — or when nobody did.
     */
    public FourthPartyExposure validate(Long id) {
        FourthPartyExposure ex = get(id);
        ex.setStatus(VALIDATED);
        ex.setLastValidatedAt(LocalDateTime.now());
        ex.setValidatorName(CurrentActor.requireUsername("a fourth-party validation"));
        return repo.save(ex);
    }

    @Transactional
    public FourthPartyExposure mitigate(Long id, String notesEn) {
        FourthPartyExposure ex = get(id);
        ex.setStatus(MITIGATED);
        ex.setMitigationNotesEn(notesEn);
        return repo.save(ex);
    }

    @Transactional
    public FourthPartyExposure retire(Long id) {
        FourthPartyExposure ex = get(id);
        ex.setStatus(RETIRED);
        return repo.save(ex);
    }

    /**
     * Status is the only field left for this to check: {@code dependencyType} and
     * {@code downstreamCriticality} are now normalised by their vocabularies at the two sites where a
     * caller's value arrives, so re-checking them here would be a second opinion nobody reads
     * (F2-190). Status never comes from a caller at all — it is checked because this method runs on
     * both write paths and a status the vocabulary does not declare would be a defect somewhere else
     * in this class.
     */
    private void validate(FourthPartyExposure r) {
        if (r.getStatus() != null && !STATUS.tokens().contains(r.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUS.tokens());
    }

    /**
     * Raise every row in the tenant to the concentration tier its live multiplicity justifies.
     *
     * <p><b>Elevation only.</b> See {@link ConcentrationTier} for what this method used to do and
     * what it cost: it assigned the computed tier unconditionally, and because all 13 live rows carry
     * distinct fourth-party names the computed tier is LOW for every one of them, so any create or
     * update anywhere in the register flattened nine of thirteen — every HIGH row and the sole
     * CRITICAL one — in both dev and prod.
     *
     * <p><b>A RETIRED edge earns nobody a floor.</b> Concentration is the exposure that exists
     * because several primaries depend on the same fourth party; a retired dependency is not one of
     * them. This matters more under elevation than it did under assignment: an inflated floor is now
     * permanent, because nothing lowers a tier. Latent today — no fourth-party name appears twice in
     * either database — so it is asserted by test rather than observed in data.
     */
    private void recomputeConcentration() {
        Long org = TenantContext.getCurrentTenant();
        List<FourthPartyExposure> all = repo.findByOrganisationId(org);

        Map<String, Long> livePrimaries = new HashMap<>();
        for (FourthPartyExposure ex : all) {
            if (RETIRED.equals(ex.getStatus())) continue;
            livePrimaries.merge(ex.getFourthPartyName(), 1L, Long::sum);
        }

        for (FourthPartyExposure ex : all) {
            String stored = ex.getConcentrationRisk();
            String floor = ConcentrationTier.multiplicityFloor(
                    livePrimaries.getOrDefault(ex.getFourthPartyName(), 0L));
            if (!ConcentrationTier.outranks(floor, stored)) continue;

            ex.setConcentrationRisk(floor);
            FourthPartyExposure saved = repo.save(ex);
            // Enhancement E: fire the auto-CAPA only on the rising edge into HIGH. Asking `atLeast`
            // rather than `equals` is what keeps that honest now CRITICAL exists: a row already at
            // CRITICAL has not newly become highly concentrated.
            if (ConcentrationTier.atLeast(floor, "HIGH")
                    && !ConcentrationTier.atLeast(stored, "HIGH")
                    && automation != null) {
                automation.onFourthPartyHighConcentration(saved);
            }
        }
    }
}
