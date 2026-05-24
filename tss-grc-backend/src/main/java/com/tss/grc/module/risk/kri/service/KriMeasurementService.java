package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementResponse;
import com.tss.grc.module.risk.kri.entity.KriBreach;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.kri.repository.KriBreachRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KRI measurement engine — Sprint 13.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>{@link #record(UUID, KriMeasurementRequest)} — persist one
 *       measurement, evaluate its status, and open / close breaches
 *       accordingly.</li>
 *   <li>{@link #evaluateStatus(BigDecimal, KriDefinition)} — pure
 *       function from (value, definition) to GREEN/AMBER/RED/NA. Kept
 *       static + visible for tests because this is the contract the
 *       audit committee inspects.</li>
 *   <li>{@link #evaluateAll(Long)} — invoked by the scheduler to
 *       re-evaluate every active SQL/CALLBACK KRI within a tenant.</li>
 * </ul>
 *
 * <p>F7 audit + F8 events fired for every measurement and every
 * breach lifecycle transition:
 * <ul>
 *   <li>{@code KRI_MEASURED} — every measurement (any status).</li>
 *   <li>{@code KRI_BREACH_OPENED} — fresh breach (AMBER or RED).</li>
 *   <li>{@code KRI_BREACH_ESCALATED} — AMBER row closed, RED row
 *       opened on the same KRI.</li>
 *   <li>{@code KRI_BREACH_CLOSED} — breach closed by the first
 *       GREEN measurement after the breach.</li>
 * </ul>
 */
@Slf4j
@Service
public class KriMeasurementService {

    private static final Set<String> SOURCES = Set.of(
            "AUTO_SQL", "AUTO_CALLBACK", "MANUAL", "IMPORTED");

    private final KriDefinitionRepository defRepo;
    private final KriMeasurementRepository measRepo;
    private final KriBreachRepository breachRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;
    /**
     * Injected lazily to avoid a circular Spring dependency:
     * {@link KriBreachService} depends on this service indirectly via
     * the measurement repository, and we depend on it for the
     * sustained-red CAPA ladder.
     */
    private final KriBreachService breachService;

    public KriMeasurementService(KriDefinitionRepository defRepo,
                                 KriMeasurementRepository measRepo,
                                 KriBreachRepository breachRepo,
                                 AuditLogService audit,
                                 ApplicationEventPublisher eventPublisher,
                                 @Lazy KriBreachService breachService) {
        this.defRepo = defRepo;
        this.measRepo = measRepo;
        this.breachRepo = breachRepo;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
        this.breachService = breachService;
    }

    // ─── Read ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KriMeasurementResponse> recent(UUID kriExternalId, int limit) {
        Long orgId = TenantContext.getCurrentTenant();
        KriDefinition d = mustGet(kriExternalId);
        return measRepo.findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                        orgId, d.getId(), PageRequest.of(0, Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(m -> KriMeasurementResponse.from(m, d))
                .toList();
    }

    // ─── Manual record from the controller ────────────────────────

    @Transactional
    public KriMeasurementResponse record(UUID kriExternalId, KriMeasurementRequest req) {
        KriDefinition d = mustGet(kriExternalId);
        return recordInternal(d, req.value(), req.source(),
                req.measuredAt(), null, req.notesEn(), req.notesAr());
    }

    /**
     * Internal entry point shared by the controller, the scheduler and
     * tests. Always returns the recorded measurement; opens/closes
     * breaches as a side-effect on the same transaction.
     */
    @Transactional
    public KriMeasurementResponse recordInternal(KriDefinition d, BigDecimal value, String source,
                                                  LocalDateTime measuredAt,
                                                  Long measuredByUserId,
                                                  String notesEn, String notesAr) {
        if (value == null) throw new IllegalArgumentException("KRI measurement value is required");
        String normalisedSource = (source == null || source.isBlank()) ? "MANUAL" : source;
        if (!SOURCES.contains(normalisedSource))
            throw new IllegalArgumentException("Invalid measurement source: " + normalisedSource);

        KriMeasurement m = new KriMeasurement();
        m.setOrganisationId(d.getOrganisationId());
        m.setKriId(d.getId());
        m.setMeasuredAt(measuredAt == null ? LocalDateTime.now() : measuredAt);
        m.setValue(value);
        m.setStatus(evaluateStatus(value, d));
        m.setSource(normalisedSource);
        m.setMeasuredByUserId(measuredByUserId);
        m.setNotesEn(notesEn);
        m.setNotesAr(notesAr);
        m = measRepo.save(m);

        // Breach evaluation runs against the freshly-persisted row so
        // measurement_id can be set on any opened breach.
        evaluateBreaches(d, m);

        // After every RED measurement, check whether the trailing
        // run of measurements has been RED for long enough to warrant
        // an auto-CAPA Issue. The breach service is responsible for
        // the idempotency check (skips if Issue already opened).
        if ("RED".equals(m.getStatus())) {
            breachService.checkSustainedRed(d);
        }

        audit.log("KRI_MEASURED", "KriDefinition", d.getId(),
                "KRI measured: " + d.getKriCode() + " value=" + value
                        + " status=" + m.getStatus() + " source=" + normalisedSource);
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_MEASURED",
                "KriDefinition", d.getId(),
                "kri:" + d.getKriCode(),
                measurementPayload(d, m)));
        return KriMeasurementResponse.from(m, d);
    }

    // ─── Scheduled evaluation ─────────────────────────────────────

    /**
     * Re-evaluate every active KRI for a tenant. SQL/CALLBACK/METRIC_API
     * KRIs would call into their respective fetchers; for Sprint-13
     * we ship the orchestration without bound fetchers (a future slice
     * adds the SQL runner and metric-API client). MANUAL KRIs are
     * skipped here because they require human input. The method is
     * idempotent: invoking it without any new data source simply
     * walks the active set and returns 0.
     */
    @Transactional
    public int evaluateAll(Long orgId) {
        List<KriDefinition> active = defRepo
                .findByOrganisationIdAndIsActiveTrueAndIsDeletedFalse(orgId);
        int dispatched = 0;
        for (KriDefinition d : active) {
            if ("MANUAL".equals(d.getSourceQueryType())) continue;
            // Sprint-13 ships the orchestration shell; auto-fetchers
            // are a future slice. Each iteration is a no-op for now,
            // but the audit log records that the evaluator considered
            // the KRI so operators see the scheduler tick is alive.
            audit.log("KRI_EVALUATION_SKIPPED", "KriDefinition", d.getId(),
                    "Auto-fetcher not yet bound for source_type="
                            + d.getSourceQueryType() + " (KRI " + d.getKriCode() + ")");
            dispatched++;
        }
        return dispatched;
    }

    // ─── Status evaluation — pure, visible for testing ───────────

    /**
     * Pure-function status evaluator.
     *
     * <ul>
     *   <li>If thresholds are missing → NA</li>
     *   <li>LOWER_IS_BETTER:  value ≤ green → GREEN;
     *                          value ≤ amber → AMBER;
     *                          otherwise     → RED</li>
     *   <li>HIGHER_IS_BETTER: value ≥ green → GREEN;
     *                          value ≥ amber → AMBER;
     *                          otherwise     → RED</li>
     * </ul>
     *
     * <p>Equality on the green-band boundary is GREEN (the more
     * forgiving direction) — matches the "≤ green_threshold" reading
     * banks use in the regulator-facing artefacts.
     */
    public static String evaluateStatus(BigDecimal value, KriDefinition d) {
        if (value == null || d == null) return "NA";
        if (d.getGreenThreshold() == null || d.getAmberThreshold() == null) return "NA";
        boolean higherBetter = "HIGHER_IS_BETTER".equals(d.getValueDirection());
        BigDecimal green = d.getGreenThreshold();
        BigDecimal amber = d.getAmberThreshold();
        if (higherBetter) {
            if (value.compareTo(green) >= 0) return "GREEN";
            if (value.compareTo(amber) >= 0) return "AMBER";
            return "RED";
        } else {
            if (value.compareTo(green) <= 0) return "GREEN";
            if (value.compareTo(amber) <= 0) return "AMBER";
            return "RED";
        }
    }

    // ─── Breach lifecycle ─────────────────────────────────────────

    private void evaluateBreaches(KriDefinition d, KriMeasurement m) {
        String status = m.getStatus();
        Long orgId = d.getOrganisationId();
        KriBreach open = breachRepo
                .findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                        orgId, d.getId())
                .orElse(null);

        switch (status) {
            case "GREEN":
                if (open != null) closeBreach(d, m, open);
                break;
            case "AMBER":
                if (open == null) {
                    openBreach(d, m, "AMBER");
                } else if ("RED".equals(open.getBreachLevel())) {
                    // De-escalation: close the RED row, open a fresh AMBER row.
                    closeBreach(d, m, open);
                    openBreach(d, m, "AMBER");
                }
                // AMBER → AMBER: existing row stays open.
                break;
            case "RED":
                if (open == null) {
                    openBreach(d, m, "RED");
                } else if ("AMBER".equals(open.getBreachLevel())) {
                    // Escalation: close AMBER row, open RED row.
                    closeBreach(d, m, open);
                    KriBreach red = openBreach(d, m, "RED");
                    // Distinct event so notifications/Issue rules fire on the
                    // escalation specifically.
                    eventPublisher.publishEvent(new DomainEvent(
                            orgId, "KRI_BREACH_ESCALATED",
                            "KriBreach", red.getId(),
                            "kri:" + d.getKriCode(),
                            breachPayload(d, m, red)));
                }
                // RED → RED: existing row stays open.
                break;
            default:
                // NA — no breach action taken; the measurement is
                // recorded so the gap is visible in the trend.
                break;
        }
    }

    private KriBreach openBreach(KriDefinition d, KriMeasurement m, String level) {
        KriBreach b = new KriBreach();
        b.setOrganisationId(d.getOrganisationId());
        b.setKriId(d.getId());
        b.setMeasurementId(m.getId());
        b.setBreachLevel(level);
        b.setOpenedAt(m.getMeasuredAt());
        b = breachRepo.save(b);
        audit.log("KRI_BREACH_OPENED", "KriBreach", b.getId(),
                "KRI breach opened: " + d.getKriCode() + " level=" + level
                        + " value=" + m.getValue());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_BREACH_OPENED",
                "KriBreach", b.getId(),
                "kri:" + d.getKriCode(),
                breachPayload(d, m, b)));
        return b;
    }

    private void closeBreach(KriDefinition d, KriMeasurement m, KriBreach open) {
        open.setClosedAt(m.getMeasuredAt());
        breachRepo.save(open);
        audit.log("KRI_BREACH_CLOSED", "KriBreach", open.getId(),
                "KRI breach closed: " + d.getKriCode()
                        + " (was " + open.getBreachLevel() + ")"
                        + " value=" + m.getValue());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_BREACH_CLOSED",
                "KriBreach", open.getId(),
                "kri:" + d.getKriCode(),
                breachPayload(d, m, open)));
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private KriDefinition mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("KriDefinition not found: " + externalId));
    }

    static Map<String, Object> measurementPayload(KriDefinition d, KriMeasurement m) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kriCode",       safe(d.getKriCode()));
        p.put("titleEn",       safe(d.getTitleEn()));
        p.put("category",      safe(d.getCategory()));
        p.put("status",        safe(m.getStatus()));
        p.put("value",         m.getValue() == null ? "" : m.getValue().toPlainString());
        p.put("measurementUnit", safe(d.getMeasurementUnit()));
        p.put("source",        safe(m.getSource()));
        p.put("measuredAt",    m.getMeasuredAt() == null ? "" : m.getMeasuredAt().toString());
        return p;
    }

    static Map<String, Object> breachPayload(KriDefinition d, KriMeasurement m, KriBreach b) {
        Map<String, Object> p = new HashMap<>(measurementPayload(d, m));
        p.put("breachLevel", safe(b.getBreachLevel()));
        p.put("breachId",    b.getId() == null ? "" : b.getId().toString());
        p.put("openedAt",    b.getOpenedAt() == null ? "" : b.getOpenedAt().toString());
        p.put("closedAt",    b.getClosedAt() == null ? "" : b.getClosedAt().toString());
        return p;
    }

    static String safe(String v) { return v == null ? "" : v; }
}
