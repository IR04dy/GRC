package com.tss.grc.module.cyber.threatintel;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberAsset;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Threat Intelligence correlation service — Sprint-8 Slice 8-D.
 *
 * <p>Accepts an IOC bundle from an external feed (SAMA, NCA threat-intel
 * portal, MISP, commercial vendor) and correlates it against the tenant's
 * existing {@code cyber.cyber_assets} + {@code cyber.vulnerabilities}
 * records. Returns a per-IOC hit list with severity blending plus a
 * tenant-level summary.
 *
 * <p>Three IOC dimensions supported in v1:
 *
 * <ul>
 *   <li>{@code CVE} — matched against {@code Vulnerability.cveId}</li>
 *   <li>{@code IP} — matched against {@code CyberAsset.ipAddress}</li>
 *   <li>{@code HOSTNAME} — matched against {@code CyberAsset.hostname} (case-insensitive substring)</li>
 * </ul>
 *
 * <p>Pure read aggregator — no schema changes. Tenant-scoped via
 * {@link TenantContext}; no data persisted. A future slice can add
 * {@code cyber.threat_intel_ingestions} for an audit trail; this stub
 * keeps the surface minimal so it can be wired to partner feeds today.
 *
 * <p>Saudi-native context: the response includes a {@code regulatorEscalation}
 * boolean that fires when:
 * <ul>
 *   <li>A CVE hit lands on an asset with criticality = CRITICAL, OR</li>
 *   <li>The IP / hostname matches a HIGH-criticality asset</li>
 * </ul>
 * SAMA Cyber Framework §3.5 expects the bank to act on such matches
 * within 4h. The frontend can use this flag to surface a "SAMA Cyber
 * 4h clock starts" banner.
 */
@Service
@RequiredArgsConstructor
public class ThreatIntelCorrelationService {

    private final VulnerabilityRepository vulns;
    private final CyberAssetRepository assets;

    @Transactional(readOnly = true)
    public CorrelationReport correlate(CorrelationRequest req) {
        if (req == null || req.indicators() == null || req.indicators().isEmpty()) {
            return new CorrelationReport(
                req == null ? null : req.feedName(),
                LocalDateTime.now(),
                List.of(), 0, 0, 0, false);
        }

        Long org = TenantContext.getCurrentTenant();

        // Build search indexes once per request.
        List<Vulnerability> vulnList = vulns.findAllByOrganisationId(org);
        Map<String, List<Vulnerability>> cveIndex = new HashMap<>();
        for (Vulnerability v : vulnList) {
            String cve = v.getCveId();
            if (cve != null && !cve.isBlank()) {
                cveIndex.computeIfAbsent(cve.toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(v);
            }
        }

        List<CyberAsset> assetList = assets.findAllByOrganisationId(org);
        Map<String, List<CyberAsset>> ipIndex = new HashMap<>();
        for (CyberAsset a : assetList) {
            String ip = a.getIpAddress();
            if (ip != null && !ip.isBlank()) {
                ipIndex.computeIfAbsent(ip.trim(), k -> new ArrayList<>()).add(a);
            }
        }

        // Hostname is fuzzy — keep the raw list for substring matching.

        List<CorrelationHit> hits = new ArrayList<>();
        boolean regulatorEscalation = false;

        for (IndicatorOfCompromise ioc : req.indicators()) {
            if (ioc.type() == null || ioc.value() == null || ioc.value().isBlank()) continue;
            switch (ioc.type().toUpperCase(Locale.ROOT)) {
                case "CVE" -> {
                    List<Vulnerability> matches = cveIndex.getOrDefault(ioc.value().toUpperCase(Locale.ROOT), List.of());
                    for (Vulnerability v : matches) {
                        boolean isCritical = "CRITICAL".equalsIgnoreCase(v.getSeverity());
                        if (isCritical) regulatorEscalation = true;
                        hits.add(new CorrelationHit(
                            ioc.type(), ioc.value(),
                            ioc.severity(),
                            "VULNERABILITY", v.getId(),
                            v.getTitleEn(), v.getTitleAr(),
                            v.getSeverity(),
                            blendedSeverity(ioc.severity(), v.getSeverity()),
                            v.getAssetName(),
                            ioc.sourceFeed() == null ? req.feedName() : ioc.sourceFeed()
                        ));
                    }
                }
                case "IP" -> {
                    List<CyberAsset> matches = ipIndex.getOrDefault(ioc.value().trim(), List.of());
                    for (CyberAsset a : matches) {
                        if ("CRITICAL".equalsIgnoreCase(a.getCriticality())
                            || "HIGH".equalsIgnoreCase(a.getCriticality())) {
                            regulatorEscalation = true;
                        }
                        hits.add(new CorrelationHit(
                            ioc.type(), ioc.value(),
                            ioc.severity(),
                            "ASSET", a.getId(),
                            a.getAssetName(), null,
                            a.getCriticality(),
                            blendedSeverity(ioc.severity(), a.getCriticality()),
                            a.getAssetName(),
                            ioc.sourceFeed() == null ? req.feedName() : ioc.sourceFeed()
                        ));
                    }
                }
                case "HOSTNAME" -> {
                    String needle = ioc.value().toLowerCase(Locale.ROOT).trim();
                    for (CyberAsset a : assetList) {
                        if (a.getHostname() == null) continue;
                        if (!a.getHostname().toLowerCase(Locale.ROOT).contains(needle)) continue;
                        if ("CRITICAL".equalsIgnoreCase(a.getCriticality())
                            || "HIGH".equalsIgnoreCase(a.getCriticality())) {
                            regulatorEscalation = true;
                        }
                        hits.add(new CorrelationHit(
                            ioc.type(), ioc.value(),
                            ioc.severity(),
                            "ASSET", a.getId(),
                            a.getAssetName(), null,
                            a.getCriticality(),
                            blendedSeverity(ioc.severity(), a.getCriticality()),
                            a.getAssetName(),
                            ioc.sourceFeed() == null ? req.feedName() : ioc.sourceFeed()
                        ));
                    }
                }
                default -> {
                    // Unknown IOC type — silently skipped in v1; future slice
                    // can add DOMAIN, URL, FILE_HASH, EMAIL types.
                }
            }
        }

        // Sort: blended severity desc, then by IOC type.
        hits.sort(Comparator.comparingInt((CorrelationHit h) -> severityRank(h.blendedSeverity())).reversed()
            .thenComparing(CorrelationHit::iocType));

        int matched = hits.size();
        int distinctIocs = (int) hits.stream().map(h -> h.iocType() + ":" + h.iocValue()).distinct().count();
        int distinctEntities = (int) hits.stream().map(h -> h.matchedEntityType() + ":" + h.matchedEntityId()).distinct().count();

        return new CorrelationReport(
            req.feedName(), LocalDateTime.now(),
            hits, matched, distinctIocs, distinctEntities,
            regulatorEscalation
        );
    }

    // ─── Severity blending ───────────────────────────────────────────

    private static String blendedSeverity(String iocSev, String entitySev) {
        int a = severityRank(iocSev);
        int b = severityRank(entitySev);
        return rankToSeverity(Math.max(a, b));
    }

    private static int severityRank(String s) {
        if (s == null) return 0;
        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH"     -> 3;
            case "MEDIUM"   -> 2;
            case "LOW"      -> 1;
            default         -> 0;
        };
    }

    private static String rankToSeverity(int rank) {
        return switch (rank) {
            case 4 -> "CRITICAL";
            case 3 -> "HIGH";
            case 2 -> "MEDIUM";
            case 1 -> "LOW";
            default -> "INFO";
        };
    }

    // ─── DTO types ───────────────────────────────────────────────────

    /** Incoming bundle from an external feed (SAMA portal, NCA, MISP, vendor). */
    public record CorrelationRequest(
        String feedName,                       // e.g. "SAMA-CYBER-WEEKLY-2026-21"
        String feedTimestamp,                  // ISO-8601, optional
        List<IndicatorOfCompromise> indicators
    ) {}

    public record IndicatorOfCompromise(
        String type,             // CVE | IP | HOSTNAME (DOMAIN/HASH/URL reserved)
        String value,            // CVE-2025-12345 | 10.0.1.5 | malicious.example.com
        String severity,         // CRITICAL | HIGH | MEDIUM | LOW | INFO
        String sourceFeed,       // optional override if mixed-source bundle
        String context           // free-form attribution/notes
    ) {}

    /** Tenant-scoped correlation result. */
    public record CorrelationReport(
        String feedName,
        LocalDateTime evaluatedAt,
        List<CorrelationHit> hits,
        int matchCount,
        int distinctIocsMatched,
        int distinctEntitiesMatched,
        boolean regulatorEscalation      // true → SAMA Cyber 4h clock should start
    ) {}

    public record CorrelationHit(
        String iocType,
        String iocValue,
        String iocSeverity,
        String matchedEntityType,        // VULNERABILITY | ASSET
        Long matchedEntityId,
        String matchedTitleEn,
        String matchedTitleAr,
        String matchedSeverity,
        String blendedSeverity,
        String assetName,
        String sourceFeed
    ) {}
}
