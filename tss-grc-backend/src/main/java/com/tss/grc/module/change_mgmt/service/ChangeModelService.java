package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.dto.ChangeModelRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeModel;
import com.tss.grc.module.change_mgmt.entity.ChangeModelStatus;
import com.tss.grc.module.change_mgmt.entity.ChangeModelUsage;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeModelRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeModelUsageRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S8e / CE-2 — the change model catalogue, and the pre-approval it records.
 *
 * <h3>The three acts, and why they are three</h3>
 * <ol>
 *   <li><b>Author</b> ({@code CHANGE:WRITE}) — produces a {@code DRAFT}. Writing down a procedure is
 *       clerical work.</li>
 *   <li><b>Approve</b> ({@code CHANGE:APPROVE}, <b>and not the author</b>) — this is the
 *       pre-approval. It stamps the authority, the moment and the expiry, and it is the only path to
 *       {@code pre_approved = 1}. The DTO cannot carry those fields, so the four-eyes rule is not
 *       something a caller can route around.</li>
 *   <li><b>Apply</b> ({@code CHANGE:WRITE}) — binds a model to a change. Refused unless the model is
 *       a <em>live</em> pre-approval, which means APPROVED and not expired.</li>
 * </ol>
 *
 * <p>The split is F2-29's classification applied to a new surface rather than discovered on it later:
 * imposing an authority is an approval act, using one is operational.
 *
 * <h3>What this service deliberately does not do</h3>
 * <p>It does not decide the gate. {@code ChangeManagementService} owns the transition guard and reads
 * {@link #liveModelFor} through this service — because a second place that computes "is this change
 * authorised?" is how two answers appear (F2-101's advisory gate column is the platform's own
 * example).
 */
@Service
@RequiredArgsConstructor
public class ChangeModelService {

    private final ChangeModelRepository modelRepo;
    private final ChangeModelUsageRepository usageRepo;
    private final ChangeRequestRepository changeRepo;

    // ── catalogue ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeModel> models() {
        return modelRepo.findByOrganisationIdOrderByCodeAsc(TenantContext.getCurrentTenant());
    }

    @Transactional
    public ChangeModel create(ChangeModelRequest req) {
        Long org = TenantContext.getCurrentTenant();
        modelRepo.findByOrganisationIdAndCode(org, req.code()).ifPresent(existing -> {
            throw new IllegalStateException(
                "A change model with code '" + req.code() + "' already exists in this organisation. "
                + "Codes are the key a change cites, so they cannot be reused — retire the old model "
                + "or choose another code.");
        });
        ChangeModel m = new ChangeModel();
        m.setOrganisationId(org);
        apply(m, req);
        // Always DRAFT. The pre-approval is a separate act by a different person.
        m.setStatus(ChangeModelStatus.DRAFT);
        m.setPreApproved(false);
        m.setCreatedBy(actorName());
        return modelRepo.save(m);
    }

    @Transactional
    public ChangeModel update(Long id, ChangeModelRequest req) {
        ChangeModel m = require(id);
        // ⚠ An APPROVED model is a live authority. Editing its steps would change what was
        // pre-approved without anybody re-approving it — the "configured then quietly altered" shape.
        if (ChangeModelStatus.APPROVED.equals(m.getStatus())) {
            throw new IllegalStateException(
                "This model is APPROVED and is conferring pre-approval, so its procedure cannot be "
                + "edited in place — that would change what was authorised without anybody "
                + "re-authorising it. Suspend it, or supersede it with a new model.");
        }
        apply(m, req);
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedBy(actorName());
        return modelRepo.save(m);
    }

    /**
     * The pre-approval act. {@code CHANGE:APPROVE} at the controller; four-eyes here.
     *
     * @param validForDays how long the pre-approval lasts. Required and bounded — "what stops a
     *                     tenant choosing 3,650 days?" is the first question an examiner asks about
     *                     any self-set validity period (ADR-017 D-3 asked it of the ratification SLA).
     */
    @Transactional
    public ChangeModel approve(Long id, Integer validForDays, String note) {
        ChangeModel m = require(id);
        if (ChangeModelStatus.APPROVED.equals(m.getStatus())) {
            throw new IllegalStateException("This model is already APPROVED.");
        }
        if (ChangeModelStatus.RETIRED.equals(m.getStatus())) {
            throw new IllegalStateException(
                "A RETIRED model cannot be re-approved — changes that cited it must keep resolving to "
                + "the row they actually used. Create a successor model instead.");
        }
        if (validForDays == null || validForDays < MIN_VALID_DAYS || validForDays > MAX_VALID_DAYS) {
            throw new IllegalStateException(
                "A pre-approval must state how long it lasts, between " + MIN_VALID_DAYS + " and "
                + MAX_VALID_DAYS + " days. A pre-approval with no expiry is not a control: nothing "
                + "ever brings it back for review.");
        }
        String actorName = actorName();
        Long actorId = actorId();
        // Four eyes, on the author name — and here that is sound, unlike DF-4/F2-24. Those findings
        // were about comparing against a CLIENT-SUPPLIED claimed name, which a caller defeats by
        // claiming somebody else's. `created_by` is written by this service from the authenticated
        // principal and is not on any request DTO, so the comparison is between two server-recorded
        // identities. (The table carries no `created_by_id`; adding one would let this resolve on the
        // id, and is the only thing that would make it stronger.)
        if (actorName != null && actorName.equalsIgnoreCase(m.getCreatedBy())) {
            throw new IllegalStateException(
                "Segregation of duty: the author of a change model may not approve it. A pre-approval "
                + "somebody granted to their own procedure authorises their own future changes with "
                + "no second pair of eyes anywhere in the chain.");
        }

        LocalDateTime now = LocalDateTime.now();
        m.setStatus(ChangeModelStatus.APPROVED);
        m.setPreApproved(true);
        m.setAuthorisedById(actorId);
        m.setAuthorisedByName(actorName);
        m.setAuthorisedAt(now);
        m.setAuthorisedUntil(now.plusDays(validForDays));
        if (note != null && !note.isBlank()) {
            m.setDescriptionEn((m.getDescriptionEn() == null ? "" : m.getDescriptionEn() + "\n")
                + "[approval] " + note);
        }
        m.setUpdatedAt(now);
        m.setUpdatedBy(actorName);
        return modelRepo.save(m);
    }

    /** Withdraw the authority without erasing the record. */
    @Transactional
    public ChangeModel setStatus(Long id, String status) {
        if (!ChangeModelStatus.ALL.contains(status)) {
            throw new IllegalStateException(
                "Unknown model status '" + status + "'. Permitted: " + ChangeModelStatus.ALL);
        }
        if (ChangeModelStatus.APPROVED.equals(status)) {
            throw new IllegalStateException(
                "Use the approve endpoint to grant a pre-approval — it records the authority and the "
                + "expiry, which a bare status change cannot.");
        }
        ChangeModel m = require(id);
        m.setStatus(status);
        // Suspending or retiring withdraws the pre-approval. The authorisation history stays.
        m.setPreApproved(false);
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedBy(actorName());
        return modelRepo.save(m);
    }

    // ── binding a model to a change ─────────────────────────────────────────

    @Transactional
    public ChangeModelUsage applyToChange(Long changeId, Long modelId, String deviationNotes) {
        Long org = TenantContext.getCurrentTenant();
        ChangeRequest c = changeRepo.findById(changeId)
            .filter(x -> org.equals(x.getOrganisationId()))
            .orElseThrow(() -> new IllegalStateException("Change " + changeId + " not found"));
        ChangeModel m = require(modelId);

        if (!ChangeModelStatus.isApplicable(m.getStatus())) {
            throw new IllegalStateException(
                "Model " + m.getCode() + " is " + m.getStatus() + ", so it confers no pre-approval and "
                + "cannot be applied. Only an APPROVED model may authorise a change.");
        }
        if (m.isExpiredPreApproval(LocalDateTime.now())) {
            throw new IllegalStateException(
                "The pre-approval on model " + m.getCode() + " expired on " + m.getAuthorisedUntil()
                + ". An expired pre-approval authorises nothing — have the model re-approved, or "
                + "route this change to the CAB.");
        }
        if (m.getChangeType() != null && c.getChangeType() != null
                && !m.getChangeType().equals(c.getChangeType())) {
            throw new IllegalStateException(
                "Model " + m.getCode() + " describes a " + m.getChangeType() + " change and this one is "
                + c.getChangeType() + ". Applying it would claim a pre-approval that was never granted "
                + "for this kind of work.");
        }

        ChangeModelUsage u = usageRepo.findByOrganisationIdAndChangeRequestId(org, changeId)
            .orElseGet(ChangeModelUsage::new);
        u.setOrganisationId(org);
        u.setChangeRequestId(changeId);
        u.setChangeModelId(modelId);
        u.setAppliedById(actorId());
        u.setAppliedByName(actorName());
        u.setDeviationNotes(deviationNotes == null || deviationNotes.isBlank() ? null : deviationNotes);
        return usageRepo.save(u);
    }

    @Transactional(readOnly = true)
    public Optional<ChangeModelUsage> usageFor(Long changeId) {
        return usageRepo.findByOrganisationIdAndChangeRequestId(
            TenantContext.getCurrentTenant(), changeId);
    }

    /**
     * The model backing this change <b>if it is a live pre-approval right now</b>, else empty.
     *
     * <p>The single predicate the transition guard consults. Returns empty for a suspended model, a
     * retired one, an expired one, and for a change that cites a model but deviated from it — a
     * change that departed from the procedure is not doing the pre-approved thing.
     */
    @Transactional(readOnly = true)
    public Optional<ChangeModel> liveModelFor(Long changeId) {
        Long org = TenantContext.getCurrentTenant();
        return usageRepo.findByOrganisationIdAndChangeRequestId(org, changeId)
            .filter(u -> !u.hasDeviation())
            .flatMap(u -> modelRepo.findByIdAndOrganisationId(u.getChangeModelId(), org))
            .filter(m -> m.isLivePreApproval(LocalDateTime.now()));
    }

    /** What the authorisation resolution reports about model backing. Never a bare boolean. */
    @Transactional(readOnly = true)
    public Map<String, Object> backingFor(Long changeId) {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<ChangeModelUsage> usage =
            usageRepo.findByOrganisationIdAndChangeRequestId(org, changeId);
        if (usage.isEmpty()) {
            out.put("modelBacked", false);
            out.put("reason", "NO_MODEL_CITED");
            return out;
        }
        ChangeModelUsage u = usage.get();
        Optional<ChangeModel> model = modelRepo.findByIdAndOrganisationId(u.getChangeModelId(), org);
        out.put("modelCode", model.map(ChangeModel::getCode).orElse(null));
        out.put("modelNameEn", model.map(ChangeModel::getNameEn).orElse(null));
        out.put("modelNameAr", model.map(ChangeModel::getNameAr).orElse(null));
        out.put("modelStatus", model.map(ChangeModel::getStatus).orElse(null));
        out.put("authorisedByName", model.map(ChangeModel::getAuthorisedByName).orElse(null));
        out.put("authorisedUntil", model.map(ChangeModel::getAuthorisedUntil).orElse(null));
        out.put("deviation", u.hasDeviation());
        out.put("deviationNotes", u.getDeviationNotes());

        LocalDateTime now = LocalDateTime.now();
        boolean live = model.map(m -> m.isLivePreApproval(now)).orElse(false);
        boolean backed = live && !u.hasDeviation();
        out.put("modelBacked", backed);
        // The reason a reader needs when the answer is no. "false" alone is the F2-95 shape.
        out.put("reason", backed ? "LIVE_PRE_APPROVAL"
            : u.hasDeviation() ? "DEVIATED_FROM_MODEL"
            : model.isEmpty() ? "MODEL_NOT_FOUND"
            : model.get().isExpiredPreApproval(now) ? "PRE_APPROVAL_EXPIRED"
            : !ChangeModelStatus.APPROVED.equals(model.get().getStatus()) ? "MODEL_NOT_APPROVED"
            : "NOT_PRE_APPROVED");
        return out;
    }

    // ── internals ───────────────────────────────────────────────────────────

    /** Bounds on a self-set validity period, so "365 days" cannot answer "what stops 365 days?". */
    static final int MIN_VALID_DAYS = 30;
    static final int MAX_VALID_DAYS = 730;

    private ChangeModel require(Long id) {
        return modelRepo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new IllegalStateException("Change model " + id + " not found"));
    }

    private void apply(ChangeModel m, ChangeModelRequest r) {
        m.setCode(r.code());
        m.setNameEn(r.nameEn());
        m.setNameAr(r.nameAr());
        m.setDescriptionEn(r.descriptionEn());
        m.setDescriptionAr(r.descriptionAr());
        m.setChangeType(r.changeType());
        m.setRiskDefault(blankToNull(r.riskDefault()));
        m.setGateOverride(blankToNull(r.gateOverride()));
        m.setStandardSteps(r.standardSteps());
        m.setRollbackTemplate(r.rollbackTemplate());
        m.setTestTemplate(r.testTemplate());
        m.setMaxLeadTimeHours(r.maxLeadTimeHours());
        m.setReviewCadence(blankToNull(r.reviewCadence()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String actorName() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    private static Long actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return null;
        Object p = a.getPrincipal();
        if (p instanceof com.tss.grc.security.GrcUserDetails u) return u.getUserId();
        return null;
    }
}
