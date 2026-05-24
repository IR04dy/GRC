package com.tss.grc.module.cyber.asm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.LifecycleTransitionRequest;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceAsset;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceFinding;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceAssetRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceFindingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Attack-Surface Asset service — Sprint 11.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>CRUD on {@link AttackSurfaceAsset} (operator surface)</li>
 *   <li>Lifecycle state-machine enforcement (DISCOVERED → IN_REVIEW
 *       → APPROVED | SHADOW_IT_PENDING → RETIRED)</li>
 *   <li>Risk-score computation:
 *       {@code severityWeight × exposureMul × criticalityMul}</li>
 * </ul>
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F7 audit_log: CREATE, UPDATE, LIFECYCLE_TRANSITION, RISK_RECOMPUTED</li>
 *   <li>F8 events: {@code ASM_ASSET_CREATED},
 *       {@code ASM_ASSET_LIFECYCLE_TRANSITION},
 *       {@code ASM_ASSET_RISK_RECOMPUTED}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackSurfaceAssetService {

    // Risk-score components — visible-for-testing constants. The
    // formula and these tables are part of the public contract:
    //   risk_score = severityWeight × exposureMul × criticalityMul
    //
    // severity here means the highest-severity OPEN finding attached to
    // the asset, defaulting to LOW (15) when there are none open. The
    // result is clamped to [0, 100] and persisted at 2-dp precision.

    static final Map<String, Integer> SEVERITY_WEIGHT = Map.of(
            "CRITICAL", 100,
            "HIGH",     70,
            "MEDIUM",   40,
            "LOW",      15,
            "INFO",     5
    );

    static final Map<String, Double> EXPOSURE_MULTIPLIER = Map.of(
            "PUBLIC",     1.0,
            "PARTNER",    0.7,
            "INTERNAL",   0.4,
            "RESTRICTED", 0.2
    );

    static final Map<String, Double> CRITICALITY_MULTIPLIER = Map.of(
            "CRITICAL", 1.0,
            "HIGH",     0.85,
            "MEDIUM",   0.6,
            "LOW",      0.3
    );

    /** Severity values that count as "open" for risk-aggregation purposes. */
    private static final List<String> OPEN_FINDING_STATUSES = List.of("OPEN", "INVESTIGATING");

    /** Severity priority order used when picking the highest open finding. */
    private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");

    /** Allowed lifecycle transitions. Keyed by source stage, value is target set. */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "DISCOVERED",        Set.of("IN_REVIEW", "SHADOW_IT_PENDING", "RETIRED"),
            "IN_REVIEW",         Set.of("APPROVED", "SHADOW_IT_PENDING", "RETIRED"),
            "SHADOW_IT_PENDING", Set.of("APPROVED", "RETIRED"),
            "APPROVED",          Set.of("RETIRED"),
            "RETIRED",           Set.of()
    );

    private final AttackSurfaceAssetRepository assetRepo;
    private final AttackSurfaceFindingRepository findingRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssetResponse> listAssets() {
        Long orgId = TenantContext.getCurrentTenant();
        return assetRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId)
                .stream().map(AssetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AssetResponse getAsset(UUID externalId) {
        return AssetResponse.from(mustGet(externalId));
    }

    // ─── Mutations ────────────────────────────────────────────────

    @Transactional
    public AssetResponse createAsset(AssetRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        AttackSurfaceAsset a = new AttackSurfaceAsset();
        a.setOrganisationId(orgId);
        a.setAssetCode(req.assetCode() == null || req.assetCode().isBlank()
                ? generateCode(orgId) : req.assetCode());
        applyFields(a, req);
        a.setLifecycleStage(req.lifecycleStage() == null || req.lifecycleStage().isBlank()
                ? "DISCOVERED" : req.lifecycleStage());
        a.setDiscoveredAt(LocalDateTime.now());
        if ("APPROVED".equals(a.getLifecycleStage())) {
            a.setApprovedAt(LocalDateTime.now());
        }
        // Risk-score starts at the no-open-findings baseline (LOW
        // severityWeight × exposure × criticality).
        a.setRiskScore(computeRiskScore("LOW", a.getExposure(), a.getCriticality()));
        a = assetRepo.save(a);

        audit.log("CREATE", "AttackSurfaceAsset", a.getId(),
                "ASM asset created: " + a.getAssetCode() + " — " + safe(a.getTitleEn())
                        + " · type=" + a.getAssetType() + " · exposure=" + a.getExposure()
                        + " · criticality=" + a.getCriticality());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "ASM_ASSET_CREATED",
                "AttackSurfaceAsset", a.getId(), a.getAssetCode(),
                payload(a, null)));
        return AssetResponse.from(a);
    }

    @Transactional
    public AssetResponse updateAsset(UUID externalId, AssetRequest req) {
        AttackSurfaceAsset a = mustGet(externalId);
        applyFields(a, req);
        a = assetRepo.save(a);
        audit.log("UPDATE", "AttackSurfaceAsset", a.getId(),
                "ASM asset updated: " + a.getAssetCode());
        // Exposure or criticality may have changed → recompute.
        recomputeRiskScore(a.getId());
        return AssetResponse.from(mustGet(externalId));
    }

    /**
     * Apply a lifecycle transition. Throws {@link IllegalStateException}
     * if the transition is not allowed by the state machine.
     */
    @Transactional
    public AssetResponse transitionLifecycle(UUID externalId, LifecycleTransitionRequest req) {
        AttackSurfaceAsset a = mustGet(externalId);
        String from = a.getLifecycleStage();
        String to = req.targetStage();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "ASM asset lifecycle transition not allowed: " + from + " → " + to);
        }
        a.setLifecycleStage(to);
        LocalDateTime now = LocalDateTime.now();
        if ("APPROVED".equals(to) && a.getApprovedAt() == null) {
            a.setApprovedAt(now);
        }
        if ("RETIRED".equals(to)) {
            a.setRetiredAt(now);
        }
        a = assetRepo.save(a);

        audit.log("LIFECYCLE_TRANSITION", "AttackSurfaceAsset", a.getId(),
                "ASM asset " + a.getAssetCode() + " transition " + from + " → " + to
                        + (req.note() == null ? "" : " — " + req.note()));
        eventPublisher.publishEvent(new DomainEvent(
                a.getOrganisationId(), "ASM_ASSET_LIFECYCLE_TRANSITION",
                "AttackSurfaceAsset", a.getId(), a.getAssetCode(),
                payload(a, from)));
        return AssetResponse.from(a);
    }

    /**
     * Recompute the asset risk score from the currently-open findings.
     * Called by {@code AttackSurfaceFindingService} on every finding
     * mutation. F8 emits {@code ASM_ASSET_RISK_RECOMPUTED}.
     */
    @Transactional
    public AssetResponse recomputeRiskScore(Long assetId) {
        AttackSurfaceAsset a = assetRepo.findById(assetId)
                .orElseThrow(() -> new EntityNotFoundException("AttackSurfaceAsset id=" + assetId));
        BigDecimal previous = a.getRiskScore();
        String topSeverity = pickHighestOpenSeverity(a.getOrganisationId(), assetId);
        BigDecimal next = computeRiskScore(topSeverity, a.getExposure(), a.getCriticality());
        if (previous != null && previous.compareTo(next) == 0) {
            return AssetResponse.from(a);
        }
        a.setRiskScore(next);
        a = assetRepo.save(a);

        audit.log("RISK_RECOMPUTED", "AttackSurfaceAsset", a.getId(),
                "ASM risk recomputed for " + a.getAssetCode() + ": "
                        + (previous == null ? "—" : previous.toPlainString()) + " → "
                        + next.toPlainString() + " (top severity=" + topSeverity + ")");
        Map<String, Object> p = payload(a, null);
        p.put("previousRiskScore", previous == null ? null : previous.toPlainString());
        p.put("topOpenSeverity", topSeverity);
        eventPublisher.publishEvent(new DomainEvent(
                a.getOrganisationId(), "ASM_ASSET_RISK_RECOMPUTED",
                "AttackSurfaceAsset", a.getId(), a.getAssetCode(), p));
        return AssetResponse.from(a);
    }

    // ─── Internal helpers ─────────────────────────────────────────

    public AttackSurfaceAsset mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("AttackSurfaceAsset not found: " + externalId));
    }

    private void applyFields(AttackSurfaceAsset a, AssetRequest r) {
        if (r.titleEn()         != null) a.setTitleEn(r.titleEn());
        if (r.titleAr()         != null) a.setTitleAr(r.titleAr());
        if (r.descriptionEn()   != null) a.setDescriptionEn(r.descriptionEn());
        if (r.descriptionAr()   != null) a.setDescriptionAr(r.descriptionAr());
        if (r.assetType()       != null) a.setAssetType(r.assetType());
        if (r.hostname()        != null) a.setHostname(r.hostname());
        if (r.ipAddress()       != null) a.setIpAddress(r.ipAddress());
        if (r.port()            != null) a.setPort(r.port());
        if (r.protocol()        != null) a.setProtocol(r.protocol());
        if (r.certSubject()     != null) a.setCertSubject(r.certSubject());
        if (r.certIssuer()      != null) a.setCertIssuer(r.certIssuer());
        if (r.certExpiresAt()   != null) a.setCertExpiresAt(r.certExpiresAt());
        if (r.assetOwnerUserId()!= null) a.setAssetOwnerUserId(r.assetOwnerUserId());
        if (r.businessUnitEn()  != null) a.setBusinessUnitEn(r.businessUnitEn());
        if (r.businessUnitAr()  != null) a.setBusinessUnitAr(r.businessUnitAr());
        if (r.exposure()        != null) a.setExposure(r.exposure());
        if (r.criticality()     != null) a.setCriticality(r.criticality());
        if (r.discoverySource() != null) a.setDiscoverySource(r.discoverySource());
        if (r.tags()            != null) a.setTags(r.tags());
    }

    private String generateCode(Long orgId) {
        int year = LocalDateTime.now().getYear();
        String prefix = "ASM-" + year + "-";
        for (int i = 0; i < 50; i++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format("%s%03d", prefix, seq);
            if (assetRepo.countByOrganisationIdAndAssetCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String pickHighestOpenSeverity(Long orgId, Long assetId) {
        List<AttackSurfaceFinding> open = findingRepo
                .findByOrganisationIdAndAssetIdAndStatusInAndIsDeletedFalse(orgId, assetId, OPEN_FINDING_STATUSES);
        for (String sev : SEVERITY_ORDER) {
            for (AttackSurfaceFinding f : open) {
                if (sev.equals(f.getSeverity())) return sev;
            }
        }
        return "LOW";
    }

    /**
     * Compute the risk score from the three drivers. Exposed as
     * package-private static so the unit tests can lock the math
     * against the table in the package javadoc.
     */
    static BigDecimal computeRiskScore(String severity, String exposure, String criticality) {
        int weight = SEVERITY_WEIGHT.getOrDefault(severity, SEVERITY_WEIGHT.get("LOW"));
        double exMul = EXPOSURE_MULTIPLIER.getOrDefault(exposure, EXPOSURE_MULTIPLIER.get("INTERNAL"));
        double crMul = CRITICALITY_MULTIPLIER.getOrDefault(criticality, CRITICALITY_MULTIPLIER.get("MEDIUM"));
        double raw = weight * exMul * crMul;
        if (raw < 0) raw = 0;
        if (raw > 100) raw = 100;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> payload(AttackSurfaceAsset a, String previousStage) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("assetCode",      safe(a.getAssetCode()));
        p.put("externalId",     a.getExternalId() == null ? "" : a.getExternalId().toString());
        p.put("titleEn",        safe(a.getTitleEn()));
        p.put("assetType",      safe(a.getAssetType()));
        p.put("exposure",       safe(a.getExposure()));
        p.put("criticality",    safe(a.getCriticality()));
        p.put("lifecycleStage", safe(a.getLifecycleStage()));
        p.put("discoverySource", safe(a.getDiscoverySource()));
        p.put("riskScore",      a.getRiskScore() == null ? null : a.getRiskScore().toPlainString());
        if (previousStage != null) p.put("previousLifecycleStage", previousStage);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
