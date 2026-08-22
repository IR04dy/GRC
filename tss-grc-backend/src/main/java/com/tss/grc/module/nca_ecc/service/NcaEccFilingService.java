package com.tss.grc.module.nca_ecc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.nca_ecc.dto.NcaEccFilingDto;
import com.tss.grc.module.nca_ecc.entity.NcaEccFiling;
import com.tss.grc.module.nca_ecc.repository.NcaEccFilingRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NCA ECC annual filing service. State machine:
 *   DRAFT          → PEER_REVIEW, CANCELLED
 *   PEER_REVIEW    → CISO_APPROVAL, DRAFT (revisions), CANCELLED
 *   CISO_APPROVAL  → SUBMITTED, PEER_REVIEW (rework), CANCELLED
 *   SUBMITTED      → ACKNOWLEDGED
 *   ACKNOWLEDGED, CANCELLED → terminal
 */
@Service
@RequiredArgsConstructor
public class NcaEccFilingService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "DRAFT",         Set.of("PEER_REVIEW", "CANCELLED"),
        "PEER_REVIEW",   Set.of("CISO_APPROVAL", "DRAFT", "CANCELLED"),
        "CISO_APPROVAL", Set.of("SUBMITTED", "PEER_REVIEW", "CANCELLED"),
        "SUBMITTED",     Set.of("ACKNOWLEDGED")
    );

    private final NcaEccFilingRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<NcaEccFilingDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByFiscalYearDesc(orgId).stream()
            .map(NcaEccFilingDto::from).collect(Collectors.toList());
    }

    public NcaEccFilingDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(NcaEccFilingDto::from)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found: " + externalId));
    }

    public NcaEccFilingDto byYear(Integer year) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndFiscalYear(orgId, year)
            .map(NcaEccFilingDto::from)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found for year: " + year));
    }

    /**
     * The authenticated principal, or a refusal (F2-223/F2-225).
     *
     * <p>Refusing rather than storing null is safe precisely because both fields were
     * already mandatory in practice — {@code drafted_by} is NOT NULL and
     * {@code cisoApprover} was the only thing the CISO_APPROVAL branch recorded — so no
     * request that works today starts failing (F2-182). What it stops is a filing whose
     * attribution nothing can vouch for.
     */
    private static String requireActor(String role, Integer fiscalYear) {
        String who = CurrentActor.username();
        if (who == null || who.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the " + role
                + " of the NCA ECC filing for " + fiscalYear
                + ". A regulator self-assessment with no named " + role + " is not a filing.");
        }
        return who;
    }

    @Transactional
    public NcaEccFilingDto create(NcaEccFilingDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        NcaEccFiling f = new NcaEccFiling();
        // ⚠⚠ F2-208 — swept. See ClientSuppliedReferenceRatchetTest. This is the reference on the
        // National Cybersecurity Authority ECC self-assessment return, so it is the sharpest
        // member of the family: a caller could name a filing after a year it does not cover.
        //
        // ⚠ The constant "-001" suffix is CORRECT here and must not be "fixed" into a sequence:
        // uk_ncaecc_year is UNIQUE on (organisation_id, fiscal_year), so a tenant can hold at
        // most one filing per year and the suffix can never collide. Checked against sys.indexes
        // rather than assumed — the identical shape WOULD be F2-48's collision bug on any table
        // without that constraint.
        f.setExternalId(String.format("NCA-%d-001", req.getFiscalYear()));
        f.setOrganisationId(orgId);
        f.setFiscalYear(req.getFiscalYear());
        f.setFrameworkVersion(req.getFrameworkVersion() != null ? req.getFrameworkVersion() : "ECC-2:2024");
        f.setTitleEn(req.getTitleEn()); f.setTitleAr(req.getTitleAr());
        applyScores(f, req);
        f.setResponsesJson(req.getResponsesJson());
        f.setGapSummaryEn(req.getGapSummaryEn()); f.setGapSummaryAr(req.getGapSummaryAr());
        // ⚠ F2-225. draftedBy used to be @NotBlank from the request, beside a drafted_at the
        // entity fills from its own FIELD INITIALISER (= LocalDateTime.now()). That is F2-223's
        // split attribution — the server owns WHEN and the caller was naming WHO — and it is the
        // one shape no setter-matching scan can find, because the clock never appears as a
        // setX(now()) call anywhere. It was found by reading the entity, and it is why F2-215's
        // four filling mechanisms matter to a detector, not just to a fix.
        //
        // Probed live: a filing created as `compliance` with draftedBy "Nobody Who Drafted This"
        // stored that string on the NCA ECC self-assessment return.
        f.setDraftedBy(requireActor("drafter", req.getFiscalYear()));
        f.setStatus("DRAFT");
        NcaEccFilingDto out = NcaEccFilingDto.from(repo.save(f));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "nca_ecc_filing", f.getExternalId(), out);
        return out;
    }

    private void applyScores(NcaEccFiling f, NcaEccFilingDto req) {
        f.setDomain1Score(req.getDomain1Score()); f.setDomain2Score(req.getDomain2Score());
        f.setDomain3Score(req.getDomain3Score()); f.setDomain4Score(req.getDomain4Score());
        f.setDomain5Score(req.getDomain5Score());
        Integer[] scores = { req.getDomain1Score(), req.getDomain2Score(), req.getDomain3Score(),
                              req.getDomain4Score(), req.getDomain5Score() };
        int sum = 0, count = 0;
        for (Integer s : scores) if (s != null) { sum += s; count++; }
        if (count > 0) f.setOverallScore(BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
    }

    @Transactional
    public NcaEccFilingDto transition(String externalId, NcaEccFilingDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        NcaEccFiling f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(f.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(f.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        f.setStatus(req.getStatus());
        switch (req.getStatus()) {
            case "PEER_REVIEW" -> {
                if (f.getPeerReviewStartedAt() == null) f.setPeerReviewStartedAt(now);
                if (req.getPeerReviewer() != null) f.setPeerReviewer(req.getPeerReviewer());
                if (req.getPeerReviewCommentsEn() != null) f.setPeerReviewCommentsEn(req.getPeerReviewCommentsEn());
            }
            case "CISO_APPROVAL" -> {
                f.setPeerReviewCompletedAt(now);
                f.setCisoApprovalAt(now);
                // ⚠ F2-225. This branch stamps cisoApprovalAt from the server's own clock and
                // used to copy cisoApprover out of the request body — F2-223's split attribution
                // on the return this organisation files with the National Cybersecurity
                // Authority. The clock half is the proof: by timestamping the approval itself the
                // server has already declared that it is the recorder of this act, so the actor
                // half was never the caller's to give.
                //
                // 🔑 The blind spot that hid it is a NOMINALISATION, not the role-noun F2-224
                // filed: `cisoApprover` reduces to `approv` and `cisoApprovalAt` to `approval`,
                // so the two halves of one attribution share no root and no root-matching
                // detector can pair them. Probed live as `compliance`: "Nobody Who Approved
                // This" was stored as the CISO who approved the filing.
                //
                // peerReviewer above is deliberately NOT changed: at PEER_REVIEW entry the
                // reviewer is being NOMINATED (peerReviewStartedAt), not recording a review they
                // have performed, and peerReviewCompletedAt is stamped here without touching it.
                // That is assignment-then-completion, which is a legitimate two-step (F2-222).
                f.setCisoApprover(requireActor("CISO approver", f.getFiscalYear()));
            }
            case "SUBMITTED" -> {
                f.setSubmittedAt(now);
                if (req.getSubmissionReference() != null) f.setSubmissionReference(req.getSubmissionReference());
            }
            case "ACKNOWLEDGED" -> f.setAcknowledgedAt(now);
            case "CANCELLED" -> {
                f.setCancelledAt(now);
                if (req.getCancellationReasonEn() != null) f.setCancellationReasonEn(req.getCancellationReasonEn());
                // F2-275 - the Arabic half of the same reason, under its OWN null guard so an
                // omitted Arabic reason leaves the stored one alone rather than clearing it.
                if (req.getCancellationReasonAr() != null) f.setCancellationReasonAr(req.getCancellationReasonAr());
            }
            default -> { /* DRAFT (revision) — no extra side-effects */ }
        }
        if (req.getLinkedCapaIds() != null) {
            f.setLinkedCapaIds(req.getLinkedCapaIds());
            f.setCapaCount(req.getLinkedCapaIds().split(",").length);
        }
        if (req.getLinkedEvidence() != null) f.setLinkedEvidence(req.getLinkedEvidence());

        NcaEccFilingDto out = NcaEccFilingDto.from(repo.save(f));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "nca_ecc_filing", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        NcaEccFiling f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("NCA ECC filing not found: " + externalId));
        f.setIsDeleted((short) 1);
        repo.save(f);
    }
}
