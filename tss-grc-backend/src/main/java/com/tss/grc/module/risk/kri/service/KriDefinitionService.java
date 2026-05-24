package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionResponse;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KRI definition CRUD — Sprint 13.
 *
 * <p>The "register half" of the KRI engine. Owns:
 * <ul>
 *   <li>List / get / create / update / soft-delete on
 *       {@link KriDefinition}</li>
 *   <li>F7 audit log entries on every mutation
 *       (CREATE / UPDATE / ACTIVATE / DEACTIVATE / DELETE)</li>
 *   <li>F8 domain events: {@code KRI_DEFINED}, {@code KRI_UPDATED},
 *       {@code KRI_ACTIVATED}, {@code KRI_DEACTIVATED},
 *       {@code KRI_DELETED}</li>
 * </ul>
 *
 * <p>Measurement-side concerns live in
 * {@link KriMeasurementService}; breach-side concerns in
 * {@link KriBreachService}.
 */
@Service
@RequiredArgsConstructor
public class KriDefinitionService {

    private static final Set<String> CATEGORIES = Set.of(
            "CYBER", "OPERATIONAL", "CREDIT", "MARKET", "LIQUIDITY",
            "COMPLIANCE", "REPUTATIONAL", "STRATEGIC", "AML",
            "PRIVACY", "BCM", "ESG");
    private static final Set<String> UNITS = Set.of(
            "COUNT", "PERCENTAGE", "RATIO", "CURRENCY_SAR",
            "DAYS", "HOURS", "BPS");
    private static final Set<String> DIRECTIONS = Set.of(
            "LOWER_IS_BETTER", "HIGHER_IS_BETTER");
    private static final Set<String> FREQUENCIES = Set.of(
            "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "ANNUAL");
    private static final Set<String> SOURCE_TYPES = Set.of(
            "MANUAL", "SQL", "CALLBACK", "METRIC_API");

    private final KriDefinitionRepository defRepo;
    private final KriMeasurementRepository measRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KriDefinitionResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return defRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(orgId).stream()
                .map(this::withLatest)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KriDefinitionResponse> listByCategory(String category) {
        Long orgId = TenantContext.getCurrentTenant();
        return defRepo.findByOrganisationIdAndCategoryAndIsDeletedFalseOrderByKriCodeAsc(
                        orgId, category).stream()
                .map(this::withLatest)
                .toList();
    }

    @Transactional(readOnly = true)
    public KriDefinitionResponse get(UUID externalId) {
        return withLatest(mustGet(externalId));
    }

    /**
     * Internal lookup by external id with tenant scoping. Throws
     * {@link EntityNotFoundException} on miss — Spring's
     * {@code GlobalExceptionHandler} maps it to a 404 for the REST
     * surface.
     */
    public KriDefinition mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("KriDefinition not found: " + externalId));
    }

    // ─── Mutations ────────────────────────────────────────────────

    @Transactional
    public KriDefinitionResponse create(KriDefinitionRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        validateEnums(req);

        KriDefinition d = new KriDefinition();
        d.setOrganisationId(orgId);
        d.setKriCode((req.kriCode() == null || req.kriCode().isBlank())
                ? generateCode(orgId, req.category())
                : req.kriCode().trim());
        applyFields(d, req);
        d.setIsActive(req.isActive() == null ? Boolean.TRUE : req.isActive());
        d = defRepo.save(d);

        audit.log("CREATE", "KriDefinition", d.getId(),
                "KRI defined: " + d.getKriCode() + " — " + safe(d.getTitleEn())
                        + " · category=" + d.getCategory() + " · unit=" + d.getMeasurementUnit());
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "KRI_DEFINED",
                "KriDefinition", d.getId(),
                "kri:" + d.getKriCode(),
                payload(d)));
        return withLatest(d);
    }

    @Transactional
    public KriDefinitionResponse update(UUID externalId, KriDefinitionRequest req) {
        KriDefinition d = mustGet(externalId);
        validateEnums(req);
        applyFields(d, req);
        if (req.isActive() != null) d.setIsActive(req.isActive());
        d = defRepo.save(d);

        audit.log("UPDATE", "KriDefinition", d.getId(),
                "KRI updated: " + d.getKriCode());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_UPDATED",
                "KriDefinition", d.getId(),
                "kri:" + d.getKriCode(),
                payload(d)));
        return withLatest(d);
    }

    @Transactional
    public KriDefinitionResponse activate(UUID externalId) {
        KriDefinition d = mustGet(externalId);
        if (Boolean.TRUE.equals(d.getIsActive())) return withLatest(d);
        d.setIsActive(Boolean.TRUE);
        d = defRepo.save(d);
        audit.log("ACTIVATE", "KriDefinition", d.getId(),
                "KRI activated: " + d.getKriCode());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_ACTIVATED",
                "KriDefinition", d.getId(),
                "kri:" + d.getKriCode(),
                payload(d)));
        return withLatest(d);
    }

    @Transactional
    public KriDefinitionResponse deactivate(UUID externalId) {
        KriDefinition d = mustGet(externalId);
        if (Boolean.FALSE.equals(d.getIsActive())) return withLatest(d);
        d.setIsActive(Boolean.FALSE);
        d = defRepo.save(d);
        audit.log("DEACTIVATE", "KriDefinition", d.getId(),
                "KRI deactivated: " + d.getKriCode());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_DEACTIVATED",
                "KriDefinition", d.getId(),
                "kri:" + d.getKriCode(),
                payload(d)));
        return withLatest(d);
    }

    @Transactional
    public void delete(UUID externalId, Long deletedByUserId) {
        KriDefinition d = mustGet(externalId);
        d.softDelete(deletedByUserId);
        defRepo.save(d);
        audit.log("DELETE", "KriDefinition", d.getId(),
                "KRI soft-deleted: " + d.getKriCode());
        eventPublisher.publishEvent(new DomainEvent(
                d.getOrganisationId(), "KRI_DELETED",
                "KriDefinition", d.getId(),
                "kri:" + d.getKriCode(),
                payload(d)));
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private KriDefinitionResponse withLatest(KriDefinition d) {
        KriMeasurement latest = measRepo
                .findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                        d.getOrganisationId(), d.getId())
                .orElse(null);
        return KriDefinitionResponse.from(d, latest);
    }

    private static void validateEnums(KriDefinitionRequest req) {
        if (req.category() != null && !CATEGORIES.contains(req.category()))
            throw new IllegalArgumentException("Invalid category: " + req.category());
        if (req.measurementUnit() != null && !UNITS.contains(req.measurementUnit()))
            throw new IllegalArgumentException("Invalid measurement_unit: " + req.measurementUnit());
        if (req.valueDirection() != null && !DIRECTIONS.contains(req.valueDirection()))
            throw new IllegalArgumentException("Invalid value_direction: " + req.valueDirection());
        if (req.frequency() != null && !FREQUENCIES.contains(req.frequency()))
            throw new IllegalArgumentException("Invalid frequency: " + req.frequency());
        if (req.sourceQueryType() != null && !SOURCE_TYPES.contains(req.sourceQueryType()))
            throw new IllegalArgumentException("Invalid source_query_type: " + req.sourceQueryType());
    }

    private static void applyFields(KriDefinition d, KriDefinitionRequest req) {
        if (req.titleEn() != null)         d.setTitleEn(req.titleEn());
        if (req.titleAr() != null)         d.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null)   d.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null)   d.setDescriptionAr(req.descriptionAr());
        if (req.category() != null)        d.setCategory(req.category());
        if (req.measurementUnit() != null) d.setMeasurementUnit(req.measurementUnit());
        if (req.valueDirection() != null)  d.setValueDirection(req.valueDirection());
        if (req.targetValue() != null)     d.setTargetValue(req.targetValue());
        if (req.greenThreshold() != null)  d.setGreenThreshold(req.greenThreshold());
        if (req.amberThreshold() != null)  d.setAmberThreshold(req.amberThreshold());
        if (req.redThreshold() != null)    d.setRedThreshold(req.redThreshold());
        if (req.frequency() != null)       d.setFrequency(req.frequency());
        if (req.sourceQueryType() != null) d.setSourceQueryType(req.sourceQueryType());
        if (req.sourceQuery() != null)     d.setSourceQuery(req.sourceQuery());
        if (req.ownerUserId() != null)     d.setOwnerUserId(req.ownerUserId());
        if (req.linkedRiskId() != null)    d.setLinkedRiskId(req.linkedRiskId());
    }

    private String generateCode(Long orgId, String category) {
        String safeCat = (category == null || category.isBlank()) ? "GEN" : category;
        String prefix = "KRI-" + safeCat;
        for (int i = 0; i < 20; i++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format("%s-%03d", prefix, seq);
            if (defRepo.countByOrganisationIdAndKriCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /** Visible for testing — payload assembled for every KRI_* domain event. */
    static Map<String, Object> payload(KriDefinition d) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kriCode",         safe(d.getKriCode()));
        p.put("externalId",      d.getExternalId() == null ? "" : d.getExternalId().toString());
        p.put("titleEn",         safe(d.getTitleEn()));
        p.put("category",        safe(d.getCategory()));
        p.put("measurementUnit", safe(d.getMeasurementUnit()));
        p.put("valueDirection",  safe(d.getValueDirection()));
        p.put("frequency",       safe(d.getFrequency()));
        p.put("sourceType",      safe(d.getSourceQueryType()));
        p.put("isActive",        d.getIsActive() != null && d.getIsActive());
        return p;
    }

    static String safe(String v) { return v == null ? "" : v; }
}
