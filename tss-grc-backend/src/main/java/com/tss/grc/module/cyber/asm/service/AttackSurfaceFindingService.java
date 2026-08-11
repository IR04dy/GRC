package com.tss.grc.module.cyber.asm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingTransitionRequest;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Attack-Surface Finding service — Sprint 11.
 *
 * <p>Records issues against an asset and walks the state machine
 * {@code OPEN → INVESTIGATING → FIXED | FALSE_POSITIVE | ACCEPTED_RISK
 * | OUT_OF_SCOPE}. Every mutation triggers a recompute of the parent
 * asset's risk score so the dashboard stays in sync.
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F7 audit_log: FINDING_RECORDED, FINDING_TRANSITION, FINDING_CLOSED, FINDING_CAPA_LINKED</li>
 *   <li>F8 events: {@code ASM_FINDING_RECORDED}, {@code ASM_FINDING_CLOSED}</li>
 * </ul>
 *
 * <p><b>F2-182 — severity was the open writer with the worst arithmetic behind it.</b>
 * {@code severity} arrived validated only by {@code @NotBlank @Size(max = 20)}, and
 * {@code CK_asm_finding_severity} is enforced on a case-<b>insensitive</b> collation — so
 * {@code severity: "critical"} was accepted and stored verbatim. Everything downstream then
 * compares case-sensitively:
 * {@code AttackSurfaceAssetService.SEVERITY_WEIGHT.getOrDefault(severity, …get("LOW"))} scored the
 * parent asset <b>15 instead of 100</b>, {@code pickHighestOpenSeverity} skipped the row and
 * returned {@code "LOW"}, and the critical-findings KPI
 * ({@code "CRITICAL".equals(f.severity())}) omitted it. An internet-facing asset with an open
 * critical finding therefore read as the platform's lowest risk band — the inverted-indicator
 * shape, and it fails <b>open</b>: the number that exists to raise an alarm is the number that
 * goes quiet.
 *
 * <p>⚙ {@code require} rather than {@code coerce} here, and the reason generalises: every value
 * that succeeds today is a case-variant of a legal token, because {@code CK_asm_finding_severity}
 * already rejects anything else. So normalising cannot break a request that currently works — it
 * can only turn a CHECK violation into a 400 that names the field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackSurfaceFindingService {

    /**
     * The severity bands, in the spelling {@code SEVERITY_WEIGHT}, {@code SEVERITY_ORDER} and the
     * dashboard KPI all assume. Declared against the constraint it mirrors so
     * {@code ClosedVocabularyPinTest} checks it against the migration rather than trusting this list.
     */
    static final ClosedVocabulary SEVERITY = ClosedVocabulary.of(
            "severity", "CK_asm_finding_severity",
            "INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    /** Allowed transitions for findings. */
    static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "OPEN",          Set.of("INVESTIGATING", "FIXED", "FALSE_POSITIVE", "ACCEPTED_RISK", "OUT_OF_SCOPE"),
            "INVESTIGATING", Set.of("OPEN", "FIXED", "FALSE_POSITIVE", "ACCEPTED_RISK", "OUT_OF_SCOPE"),
            "FIXED",         Set.of("OPEN"),
            "FALSE_POSITIVE", Set.of(),
            "ACCEPTED_RISK", Set.of("OPEN"),
            "OUT_OF_SCOPE",  Set.of("OPEN")
    );

    /** Terminal-closure statuses — once entered, closed_at is stamped. */
    static final Set<String> CLOSING_STATUSES = Set.of("FIXED", "FALSE_POSITIVE", "OUT_OF_SCOPE", "ACCEPTED_RISK");

    private final AttackSurfaceFindingRepository findingRepo;
    private final AttackSurfaceAssetRepository assetRepo;
    private final AttackSurfaceAssetService assetService;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FindingResponse> listFindings() {
        Long orgId = TenantContext.getCurrentTenant();
        List<AttackSurfaceFinding> rows = findingRepo
                .findByOrganisationIdAndIsDeletedFalseOrderByOpenedAtDesc(orgId);
        return mapWithAssets(orgId, rows);
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> listFindingsForAsset(UUID assetExternalId) {
        Long orgId = TenantContext.getCurrentTenant();
        AttackSurfaceAsset a = assetService.mustGet(assetExternalId);
        List<AttackSurfaceFinding> rows = findingRepo
                .findByOrganisationIdAndAssetIdAndIsDeletedFalseOrderByOpenedAtDesc(orgId, a.getId());
        return rows.stream().map(f -> FindingResponse.from(f, a)).toList();
    }

    @Transactional(readOnly = true)
    public FindingResponse getFinding(UUID externalId) {
        AttackSurfaceFinding f = mustGet(externalId);
        AttackSurfaceAsset a = assetRepo.findById(f.getAssetId()).orElse(null);
        return FindingResponse.from(f, a);
    }

    // ─── Mutations ────────────────────────────────────────────────

    @Transactional
    public FindingResponse recordFinding(FindingRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        AttackSurfaceAsset asset = assetService.mustGet(req.assetExternalId());

        AttackSurfaceFinding f = new AttackSurfaceFinding();
        f.setOrganisationId(orgId);
        f.setAssetId(asset.getId());
        f.setFindingType(req.findingType());
        // F2-182 — normalise before the asset recompute below reads it back through SEVERITY_WEIGHT.
        f.setSeverity(SEVERITY.require(req.severity()));
        f.setSource(req.source());
        f.setSourceRef(req.sourceRef());
        f.setDescriptionEn(req.descriptionEn());
        f.setDescriptionAr(req.descriptionAr());
        f.setRecommendationEn(req.recommendationEn());
        f.setRecommendationAr(req.recommendationAr());
        f.setOpenedAt(LocalDateTime.now());
        f.setStatus("OPEN");
        f = findingRepo.save(f);

        audit.log("FINDING_RECORDED", "AttackSurfaceFinding", f.getId(),
                "ASM finding recorded on " + asset.getAssetCode()
                        + ": " + f.getFindingType() + " · severity=" + f.getSeverity()
                        + " · source=" + f.getSource());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "ASM_FINDING_RECORDED",
                "AttackSurfaceFinding", f.getId(),
                asset.getAssetCode(),
                payload(f, asset, null)));

        // Risk-score follow-on — open finding may raise the asset score.
        assetService.recomputeRiskScore(asset.getId());
        return FindingResponse.from(f, asset);
    }

    @Transactional
    public FindingResponse transition(UUID externalId, FindingTransitionRequest req) {
        AttackSurfaceFinding f = mustGet(externalId);
        String from = f.getStatus();
        String to = req.targetStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "ASM finding transition not allowed: " + from + " → " + to);
        }
        f.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        if (CLOSING_STATUSES.contains(to) && f.getClosedAt() == null) {
            f.setClosedAt(now);
        }
        if ("OPEN".equals(to)) {
            // Re-opening clears closed_at so the SLA counter restarts.
            f.setClosedAt(null);
        }
        f = findingRepo.save(f);

        audit.log("FINDING_TRANSITION", "AttackSurfaceFinding", f.getId(),
                "ASM finding " + from + " → " + to
                        + (req.note() == null ? "" : " — " + req.note()));

        AttackSurfaceAsset asset = assetRepo.findById(f.getAssetId()).orElse(null);
        String trigger = CLOSING_STATUSES.contains(to) ? "ASM_FINDING_CLOSED" : "ASM_FINDING_RECORDED";
        eventPublisher.publishEvent(new DomainEvent(
                f.getOrganisationId(), trigger,
                "AttackSurfaceFinding", f.getId(),
                asset == null ? null : asset.getAssetCode(),
                payload(f, asset, from)));

        // Risk-score follow-on — closure / re-open may shift the top severity.
        assetService.recomputeRiskScore(f.getAssetId());
        return FindingResponse.from(f, asset);
    }

    /**
     * Attach a CAPA task id to the finding. Idempotent — calling twice
     * with the same id is a no-op; calling with a different id rejects.
     */
    @Transactional
    public FindingResponse linkCapaTask(UUID externalId, Long capaTaskId) {
        if (capaTaskId == null) {
            throw new IllegalArgumentException("capaTaskId is required");
        }
        AttackSurfaceFinding f = mustGet(externalId);
        if (f.getCapaTaskId() != null && !f.getCapaTaskId().equals(capaTaskId)) {
            throw new IllegalStateException(
                    "ASM finding already linked to CAPA " + f.getCapaTaskId() + "; refuse to overwrite");
        }
        if (f.getCapaTaskId() == null) {
            f.setCapaTaskId(capaTaskId);
            f = findingRepo.save(f);
            audit.log("FINDING_CAPA_LINKED", "AttackSurfaceFinding", f.getId(),
                    "ASM finding linked to CAPA task " + capaTaskId);
        }
        AttackSurfaceAsset asset = assetRepo.findById(f.getAssetId()).orElse(null);
        return FindingResponse.from(f, asset);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private AttackSurfaceFinding mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return findingRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("AttackSurfaceFinding not found: " + externalId));
    }

    private List<FindingResponse> mapWithAssets(Long orgId, List<AttackSurfaceFinding> findings) {
        if (findings.isEmpty()) return List.of();
        Set<Long> assetIds = findings.stream().map(AttackSurfaceFinding::getAssetId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, AttackSurfaceAsset> assetMap = assetRepo.findAllById(assetIds).stream()
                .filter(a -> orgId.equals(a.getOrganisationId()))
                .collect(java.util.stream.Collectors.toMap(AttackSurfaceAsset::getId, x -> x));
        return findings.stream()
                .map(f -> FindingResponse.from(f, assetMap.get(f.getAssetId())))
                .toList();
    }

    private static Map<String, Object> payload(AttackSurfaceFinding f, AttackSurfaceAsset asset, String previousStatus) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("findingType",  safe(f.getFindingType()));
        p.put("severity",     safe(f.getSeverity()));
        p.put("source",       safe(f.getSource()));
        p.put("status",       safe(f.getStatus()));
        if (asset != null) {
            p.put("assetCode",      safe(asset.getAssetCode()));
            p.put("assetType",      safe(asset.getAssetType()));
            p.put("assetExposure",  safe(asset.getExposure()));
            p.put("assetCriticality", safe(asset.getCriticality()));
        }
        if (previousStatus != null) p.put("previousStatus", previousStatus);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
