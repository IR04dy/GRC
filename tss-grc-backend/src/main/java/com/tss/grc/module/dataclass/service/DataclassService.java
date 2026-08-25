package com.tss.grc.module.dataclass.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.dataclass.dto.DataclassDtos.*;
import com.tss.grc.module.dataclass.entity.*;
import com.tss.grc.module.dataclass.repository.*;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Sprint 32 Data Classification + NDMO service. */
@Service
@Transactional
@RequiredArgsConstructor
public class DataclassService {

    private static final Map<String, Set<String>> ASSET_ALLOWED = Map.of(
            "DRAFT",        Set.of("CLASSIFIED"),
            "CLASSIFIED",   Set.of("APPROVED", "RECLASSIFY"),
            "APPROVED",     Set.of("IN_USE", "RECLASSIFY"),
            "IN_USE",       Set.of("REVIEW_DUE", "RECLASSIFY", "DECOMMISSIONED"),
            "REVIEW_DUE",   Set.of("IN_USE", "RECLASSIFY"),
            "RECLASSIFY",   Set.of("CLASSIFIED"),
            "DECOMMISSIONED", Set.of()
    );

    private static final Map<String, Set<String>> FLOW_ALLOWED = Map.of(
            "PROPOSED",   Set.of("APPROVED", "REJECTED"),
            "APPROVED",   Set.of("ACTIVE", "REJECTED"),
            "ACTIVE",     Set.of("SUSPENDED", "TERMINATED"),
            "SUSPENDED",  Set.of("ACTIVE", "TERMINATED"),
            "TERMINATED", Set.of(),
            "REJECTED",   Set.of()
    );

    private static final Map<String, Set<String>> EXCEPTION_ALLOWED = Map.of(
            "REQUESTED",  Set.of("APPROVED", "REJECTED"),
            "APPROVED",   Set.of("ACTIVE", "REJECTED"),
            "ACTIVE",     Set.of("EXPIRED", "REVOKED"),
            "EXPIRED",    Set.of(),
            "REJECTED",   Set.of(),
            "REVOKED",    Set.of()
    );

    private final DataAssetRepository assetRepo;
    private final DataFlowRepository flowRepo;
    private final DataExceptionRepository excRepo;
    private final DataclassEventRepository eventRepo;
    private final ApplicationEventPublisher publisher;

    // ─────────────────────────────────────── Asset CRUD + transitions

    public AssetResponse createAsset(CreateAssetRequest req) {
        Long o = org();
        if (assetRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Asset " + req.reference() + " exists");
        DataAsset a = new DataAsset();
        a.setOrganisationId(o);
        a.setReference(req.reference());
        a.setNameEn(req.nameEn()); a.setNameAr(req.nameAr());
        a.setDescriptionEn(req.descriptionEn()); a.setDescriptionAr(req.descriptionAr());
        a.setDomain(req.domain());
        a.setHostSystemLabel(req.hostSystemLabel());
        a.setPdplSpecialCategory(req.pdplSpecialCategory() != null ? req.pdplSpecialCategory() : "NONE");
        a.setPdplLawfulBasis(req.pdplLawfulBasis());
        a.setOwnerRole(req.ownerRole());
        a.setOwnerUserId(req.ownerUserId());
        a.setRetentionYears(req.retentionYears());
        a.setEncryptionAtRest(req.encryptionAtRest());
        a.setEncryptionInTransit(req.encryptionInTransit());
        a.setMaskingRequired(req.maskingRequired());
        a.setRowCountApprox(req.rowCountApprox());
        a.setLinkedDpiaRef(req.linkedDpiaRef());
        a.setStatus("DRAFT");
        assetRepo.save(a);
        timeline(a.getId(), null, null, "ASSET_REGISTERED", "DRAFT",
                "Data asset registered.", "تم تسجيل أصل البيانات.");
        emit("DATA_ASSET_REGISTERED", "DATA_ASSET", a.getId(),
                Map.of("reference", a.getReference()));
        return toAsset(a);
    }

    public AssetResponse classifyAsset(Long id, ClassifyAssetRequest req) {
        DataAsset a = loadAsset(id);
        if (!"DRAFT".equals(a.getStatus()) && !"RECLASSIFY".equals(a.getStatus()))
            throw new IllegalStateException("Asset must be DRAFT or RECLASSIFY to be classified");
        // F2-372 — normalise the caller-supplied classification to the canonical NDMO token. The
        // column has no CHECK and is case-insensitively collated, but the value is read
        // case-SENSITIVELY by classOrder() (the RECLASSIFIED_UP ladder), by the V210 automation rule
        // ({"classification":{"in":["CONFIDENTIAL","TOP_SECRET"]}}, matched with Objects.equals in
        // AutomationRuleEngine), and by the frontend label map — so a caller (or machine integration)
        // sending "confidential" would store it verbatim, bypass the high-classification automation
        // gate, mis-rank the upgrade ladder, and render unlabelled. Coerce, not require (F2-182): no
        // upstream coercion and no CHECK, so a 400 would break a request that succeeds today.
        String classification = normaliseClassification(req.classification());
        String prev = normaliseClassification(a.getClassification());
        a.setClassification(classification);
        if (req.pdplSpecialCategory() != null) a.setPdplSpecialCategory(req.pdplSpecialCategory());
        if (req.pdplLawfulBasis() != null) a.setPdplLawfulBasis(req.pdplLawfulBasis());
        if (req.nextReviewDue() != null) a.setNextReviewDue(req.nextReviewDue());
        a.setStatus("CLASSIFIED");
        a.setClassifiedAt(LocalDateTime.now());
        assetRepo.save(a);
        timeline(a.getId(), null, null, "ASSET_CLASSIFIED", "CLASSIFIED",
                "Classification " + classification + ".",
                "تصنيف " + classification + ".");
        // Reclassification upward (e.g. INTERNAL → CONFIDENTIAL or higher) emits an extra flag
        if (prev != null && classOrder(classification) > classOrder(prev)) {
            emit("DATA_ASSET_RECLASSIFIED_UP", "DATA_ASSET", a.getId(),
                    Map.of("from", prev, "to", classification));
        }
        emit("DATA_ASSET_CLASSIFIED", "DATA_ASSET", a.getId(),
                Map.of("classification", classification,
                        "specialCategory",
                        a.getPdplSpecialCategory() == null ? "NONE" : a.getPdplSpecialCategory()));
        return toAsset(a);
    }

    /** F2-372 — the canonical NDMO classification token (trim + upper-case, null-safe). */
    private static String normaliseClassification(String c) {
        return c == null ? null : c.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public AssetResponse transitionAsset(Long id, TransitionAssetRequest req) {
        DataAsset a = loadAsset(id);
        validate(ASSET_ALLOWED, a.getStatus(), req.toStatus(), "Asset");
        a.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "APPROVED" -> a.setApprovedAt(now);
            case "DECOMMISSIONED" -> a.setDecommissionedAt(LocalDate.now());
            default -> { /* no-op */ }
        }
        assetRepo.save(a);
        timeline(a.getId(), null, null, "ASSET_" + req.toStatus(), req.toStatus(),
                req.reasonEn(), req.reasonAr());
        emit("DATA_ASSET_" + req.toStatus(), "DATA_ASSET", a.getId(),
                Map.of("reference", a.getReference()));
        return toAsset(a);
    }

    public List<AssetResponse> listAssets() {
        return assetRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(DataclassService::toAsset).toList();
    }

    public AssetResponse getAsset(Long id) { return toAsset(loadAsset(id)); }

    // ────────────────────────────────────────────────────── Flow

    public FlowResponse createFlow(CreateFlowRequest req) {
        DataAsset a = loadAsset(req.dataAssetId());
        DataFlow f = new DataFlow();
        f.setOrganisationId(org());
        f.setDataAssetId(req.dataAssetId());
        f.setReference(req.reference());
        f.setSourceSystemLabel(req.sourceSystemLabel());
        f.setDestinationSystemLabel(req.destinationSystemLabel());
        f.setDestinationCountry(req.destinationCountry());
        f.setDestinationAdequacy(req.destinationAdequacy());
        f.setTransferMode(req.transferMode());
        f.setPurposeEn(req.purposeEn()); f.setPurposeAr(req.purposeAr());
        f.setPdplArt7TransferRef(req.pdplArt7TransferRef());
        f.setStatus("PROPOSED");
        flowRepo.save(f);
        timeline(req.dataAssetId(), f.getId(), null, "FLOW_PROPOSED", "PROPOSED",
                "Flow to " + req.destinationSystemLabel() + " proposed.",
                "اقتُرحت تدفّق إلى " + req.destinationSystemLabel() + ".");
        if (req.destinationCountry() != null
                && !"SA".equals(req.destinationCountry())
                && a.getClassification() != null
                && (("CONFIDENTIAL".equals(a.getClassification())
                  || "TOP_SECRET".equals(a.getClassification())))) {
            emit("DATA_FLOW_CROSS_BORDER_DETECTED", "DATA_FLOW", f.getId(),
                    Map.of("destinationCountry", req.destinationCountry(),
                            "sourceClassification", a.getClassification()));
        }
        emit("DATA_FLOW_PROPOSED", "DATA_FLOW", f.getId(),
                Map.of("dataAssetId", req.dataAssetId(),
                        "destinationCountry",
                        req.destinationCountry() == null ? "" : req.destinationCountry()));
        return toFlow(f);
    }

    public FlowResponse transitionFlow(Long id, TransitionFlowRequest req) {
        DataFlow f = loadFlow(id);
        validate(FLOW_ALLOWED, f.getStatus(), req.toStatus(), "Flow");
        // PDPL Art.7 gate for APPROVED on cross-border CONFIDENTIAL/TOP_SECRET
        if ("APPROVED".equals(req.toStatus())) {
            DataAsset a = loadAsset(f.getDataAssetId());
            boolean crossBorder = f.getDestinationCountry() != null
                    && !"SA".equals(f.getDestinationCountry());
            boolean sensitive = "CONFIDENTIAL".equals(a.getClassification())
                    || "TOP_SECRET".equals(a.getClassification());
            String ref = req.pdplArt7TransferRef() != null
                    ? req.pdplArt7TransferRef() : f.getPdplArt7TransferRef();
            if (crossBorder && sensitive && (ref == null || ref.isBlank())) {
                throw new IllegalStateException(
                    "Cross-border flow of CONFIDENTIAL/TOP_SECRET requires PDPL Art.7 transfer authorisation reference.");
            }
            if (ref != null) f.setPdplArt7TransferRef(ref);
        }
        f.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "APPROVED"   -> f.setApprovedAt(now);
            case "ACTIVE"     -> f.setActivatedAt(LocalDate.now());
            case "SUSPENDED"  -> f.setSuspendedAt(LocalDate.now());
            case "TERMINATED" -> f.setTerminatedAt(LocalDate.now());
            case "REJECTED" -> {
                f.setRejectionReasonEn(req.reasonEn());
                f.setRejectionReasonAr(req.reasonAr());
            }
            default -> { /* no-op */ }
        }
        flowRepo.save(f);
        timeline(f.getDataAssetId(), f.getId(), null,
                "FLOW_" + req.toStatus(), req.toStatus(),
                req.reasonEn(), req.reasonAr());
        emit("DATA_FLOW_" + req.toStatus(), "DATA_FLOW", f.getId(),
                Map.of("dataAssetId", f.getDataAssetId()));
        return toFlow(f);
    }

    public List<FlowResponse> listFlows(Long assetId) {
        if (assetId != null) {
            return flowRepo.findByOrganisationIdAndDataAssetId(org(), assetId)
                    .stream().map(DataclassService::toFlow).toList();
        }
        return flowRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(DataclassService::toFlow).toList();
    }

    // ────────────────────────────────────────────── Exceptions

    public ExceptionResponse createException(CreateExceptionRequest req) {
        // SEC-NDI-004: any referenced asset/flow must belong to the caller's org
        // before it is linked — exactly as createFlow() validates via loadAsset().
        // loadAsset/loadFlow throw EntityNotFoundException (404) on a cross-tenant
        // or non-existent id. Both ids are optional, so guard only when present.
        if (req.dataAssetId() != null) loadAsset(req.dataAssetId());
        if (req.dataFlowId() != null)  loadFlow(req.dataFlowId());
        DataException e = new DataException();
        e.setOrganisationId(org());
        e.setReference(req.reference());
        e.setDataAssetId(req.dataAssetId());
        e.setDataFlowId(req.dataFlowId());
        e.setExceptionType(req.exceptionType());
        e.setJustificationEn(req.justificationEn());
        e.setJustificationAr(req.justificationAr());
        e.setCompensatingControlsEn(req.compensatingControlsEn());
        e.setCompensatingControlsAr(req.compensatingControlsAr());
        e.setValidFrom(req.validFrom());
        e.setValidUntil(req.validUntil());
        e.setRequestedByUserId(currentUserId());
        e.setStatus("REQUESTED");
        excRepo.save(e);
        timeline(req.dataAssetId(), req.dataFlowId(), e.getId(),
                "EXCEPTION_REQUESTED", "REQUESTED",
                "Data exception requested (" + req.exceptionType() + ").",
                "طُلب استثناء بيانات (" + req.exceptionType() + ").");
        emit("DATA_EXCEPTION_REQUESTED", "DATA_EXCEPTION", e.getId(),
                Map.of("exceptionType", req.exceptionType()));
        return toException(e);
    }

    public ExceptionResponse transitionException(Long id, TransitionExceptionRequest req) {
        DataException e = loadException(id);
        validate(EXCEPTION_ALLOWED, e.getStatus(), req.toStatus(), "Exception");
        e.setStatus(req.toStatus());
        switch (req.toStatus()) {
            case "APPROVED" -> {
                e.setApprovedAt(LocalDateTime.now());
                e.setApproverUserId(currentUserId());
            }
            case "REVOKED" -> e.setRevokedAt(LocalDate.now());
            case "REJECTED" -> {
                e.setRejectionReasonEn(req.rejectionReasonEn());
                e.setRejectionReasonAr(req.rejectionReasonAr());
            }
            default -> { /* no-op */ }
        }
        excRepo.save(e);
        timeline(e.getDataAssetId(), e.getDataFlowId(), e.getId(),
                "EXCEPTION_" + req.toStatus(), req.toStatus(),
                req.rejectionReasonEn(), req.rejectionReasonAr());
        emit("DATA_EXCEPTION_" + req.toStatus(), "DATA_EXCEPTION", e.getId(),
                Map.of("exceptionType", e.getExceptionType()));
        return toException(e);
    }

    public List<ExceptionResponse> listExceptions() {
        return excRepo.findByOrganisationIdOrderByCreatedAtDesc(org())
                .stream().map(DataclassService::toException).toList();
    }

    // ─────────────────────────────────────────────────── Timeline

    public List<EventResponse> timeline(Long assetId) {
        loadAsset(assetId);
        return eventRepo.findByOrganisationIdAndDataAssetIdOrderByOccurredAtDesc(org(), assetId)
                .stream().map(DataclassService::toEvent).toList();
    }

    // ─────────────────────────────────────────────────── Dashboard

    public DashboardResponse dashboard() {
        Long o = org();
        List<DataAsset> allA = assetRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<DataFlow> allF = flowRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<DataException> allE = excRepo.findByOrganisationIdOrderByCreatedAtDesc(o);

        long aTotal = allA.size();
        long aTop  = allA.stream().filter(a -> "TOP_SECRET".equals(a.getClassification())).count();
        long aConf = allA.stream().filter(a -> "CONFIDENTIAL".equals(a.getClassification())).count();
        long aInt  = allA.stream().filter(a -> "INTERNAL".equals(a.getClassification())).count();
        long aPub  = allA.stream().filter(a -> "PUBLIC".equals(a.getClassification())).count();
        long aSpec = allA.stream()
                .filter(a -> a.getPdplSpecialCategory() != null
                        && !"NONE".equals(a.getPdplSpecialCategory())).count();
        long fAct = flowRepo.countByOrganisationIdAndStatus(o, "ACTIVE");
        long fCross = allF.stream()
                .filter(f -> "ACTIVE".equals(f.getStatus()))
                .filter(f -> f.getDestinationCountry() != null && !"SA".equals(f.getDestinationCountry())).count();
        long eAct = excRepo.countByOrganisationIdAndStatus(o, "ACTIVE");
        LocalDate today = LocalDate.now();
        long eExpiring = allE.stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .filter(e -> e.getValidUntil() != null
                        && !e.getValidUntil().isBefore(today)
                        && !e.getValidUntil().isAfter(today.plusDays(30))).count();
        // ── F2-128: an ACTIVE exception whose validity has already lapsed ───────────────────────
        //
        // The window above is [today, today+30], so its lower bound EXCLUDES anything already past
        // its valid_until — and nothing else in the module looks at that column. A lapsed control
        // waiver therefore appeared in no metric: it was counted in `eAct` as though still valid,
        // and absent from "expiring soon" precisely because it had already expired.
        //
        // Measured 2026-08-06 in dev: 3 of the 4 ACTIVE exceptions are past their valid_until by
        // 46–54 days, and all three waive a data-protection control (two MASKING_WAIVER, one
        // ENCRYPTION_AT_REST_WAIVER). The dashboard reported "4 active, 0 expiring".
        //
        // ⚠ Reported, not auto-expired. Nothing here changes state: whether a lapsed exception
        // should be swept to EXPIRED or derived as expired on read is the same open decision as
        // F2-125 in `openbanking`, and it should be taken once for both rather than differently in
        // each module. Making the population visible needs no such decision.
        long eLapsed = allE.stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .filter(e -> e.getValidUntil() != null && e.getValidUntil().isBefore(today))
                .count();

        Map<String, Long> assetsByStatus = allA.stream()
                .collect(Collectors.groupingBy(DataAsset::getStatus, Collectors.counting()));
        Map<String, Long> flowsByStatus = allF.stream()
                .collect(Collectors.groupingBy(DataFlow::getStatus, Collectors.counting()));
        Map<String, Long> excByType = allE.stream()
                .filter(e -> e.getExceptionType() != null)
                .collect(Collectors.groupingBy(DataException::getExceptionType, Collectors.counting()));

        List<AssetResponse> recentAssets = allA.stream().limit(8)
                .map(DataclassService::toAsset).toList();
        List<FlowResponse> recentXborder = allF.stream()
                .filter(f -> f.getDestinationCountry() != null && !"SA".equals(f.getDestinationCountry()))
                .limit(10).map(DataclassService::toFlow).toList();

        return new DashboardResponse(
                aTotal, aTop, aConf, aInt, aPub, aSpec,
                fAct, fCross, eAct, eExpiring, eLapsed,
                assetsByStatus, flowsByStatus, excByType,
                recentAssets, recentXborder);
    }

    // ─────────────────────────────────────────────────── Internals

    private static int classOrder(String c) {
        if (c == null) return 0;
        return switch (c) {
            case "PUBLIC" -> 1;
            case "INTERNAL" -> 2;
            case "CONFIDENTIAL" -> 3;
            case "TOP_SECRET" -> 4;
            default -> 0;
        };
    }

    private DataAsset loadAsset(Long id) {
        return assetRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Asset " + id + " not found"));
    }

    private DataFlow loadFlow(Long id) {
        return flowRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Flow " + id + " not found"));
    }

    private DataException loadException(Long id) {
        return excRepo.findByOrganisationIdAndId(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Exception " + id + " not found"));
    }

    private static void validate(Map<String, Set<String>> allowed, String from, String to, String label) {
        if (!allowed.getOrDefault(from, Set.of()).contains(to))
            throw new IllegalStateException(label + " cannot transition " + from + " → " + to);
    }

    private void timeline(Long assetId, Long flowId, Long excId,
                          String eventType, String statusAfter,
                          String noteEn, String noteAr) {
        DataclassEvent e = new DataclassEvent();
        e.setOrganisationId(org());
        e.setDataAssetId(assetId); e.setDataFlowId(flowId); e.setDataExceptionId(excId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setActorName(currentActorName());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn); e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private void emit(String trigger, String entityType, Long entityId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, null,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    /**
     * F2-257 — the display half of the actor pair, from the same primitive as {@link
     * #currentUserId()}.
     *
     * <p>Deliberately {@code username()} and not {@code requireUsername(...)}: the id half is
     * nullable and comes from the same place, so both halves are populated together or absent
     * together. Refusing here would let a timeline write abort the business operation it exists to
     * record.
     */
    private static String currentActorName() {
        return com.tss.grc.common.security.CurrentActor.username();
    }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private static AssetResponse toAsset(DataAsset a) {
        return new AssetResponse(
                a.getId(), a.getReference(), a.getNameEn(), a.getNameAr(),
                a.getDescriptionEn(), a.getDescriptionAr(),
                a.getDomain(), a.getClassification(),
                a.getPdplSpecialCategory(), a.getPdplLawfulBasis(),
                a.getStatus(), a.getOwnerRole(), a.getOwnerUserId(),
                a.getHostSystemLabel(), a.getRetentionYears(),
                a.getEncryptionAtRest(), a.getEncryptionInTransit(), a.getMaskingRequired(),
                a.getClassifiedAt(), a.getApprovedAt(),
                a.getNextReviewDue(), a.getDecommissionedAt(),
                a.getRowCountApprox(), a.getLinkedDpiaRef());
    }

    private static FlowResponse toFlow(DataFlow f) {
        return new FlowResponse(
                f.getId(), f.getDataAssetId(), f.getReference(),
                f.getSourceSystemLabel(), f.getDestinationSystemLabel(),
                f.getDestinationCountry(), f.getDestinationAdequacy(),
                f.getTransferMode(), f.getStatus(),
                f.getPurposeEn(), f.getPurposeAr(),
                f.getPdplArt7TransferRef(),
                f.getApprovedAt(), f.getActivatedAt(),
                f.getSuspendedAt(), f.getTerminatedAt(),
                f.getRejectionReasonEn(), f.getRejectionReasonAr());
    }

    private static ExceptionResponse toException(DataException e) {
        return new ExceptionResponse(
                e.getId(), e.getReference(), e.getDataAssetId(), e.getDataFlowId(),
                e.getExceptionType(), e.getStatus(),
                e.getJustificationEn(), e.getJustificationAr(),
                e.getCompensatingControlsEn(), e.getCompensatingControlsAr(),
                e.getRequestedByUserId(), e.getApproverUserId(),
                e.getApprovedAt(), e.getValidFrom(), e.getValidUntil(),
                e.getRevokedAt(),
                e.getRejectionReasonEn(), e.getRejectionReasonAr());
    }

    private static EventResponse toEvent(DataclassEvent e) {
        return new EventResponse(
                e.getId(), e.getDataAssetId(), e.getDataFlowId(), e.getDataExceptionId(),
                e.getOccurredAt(), e.getEventType(),
                e.getActorUserId(), e.getActorName(), e.getStatusAfter(),
                e.getNoteEn(), e.getNoteAr());
    }
}
