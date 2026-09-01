package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
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

import java.util.Locale;
import java.math.BigDecimal;
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
    /**
     * F2-182 — package-private on purpose: {@link KriMeasurementService} reads this column back and
     * must compare against the same vocabulary, which is what a bare private {@code Set} prevented.
     *
     * <p><b>⚠ Correcting F2-182's own register row.</b> It claimed a lower-case
     * {@code higher_is_better} <em>inverts KRI breach detection</em> through an open writer. The
     * inversion half is real and worth stating precisely — with exactly two tokens, a value that
     * fails to match {@code HIGHER_IS_BETTER} does not degrade, it silently selects the opposite
     * branch, so the indicator reads healthy exactly when it has breached. <b>The open-writer half is
     * false</b>: {@code validateEnums} runs on both {@code create} and {@code update}, so the column
     * has never been reachable with that casing. The scan that produced the claim looked at
     * {@code applyFields}, where the setter is, and never saw the validator two lines up the call
     * chain. 🔑 <b>Reachability and correctness are separate questions, and a scan that answers the
     * first one wrongly makes the second one look urgent.</b>
     */
    static final ClosedVocabulary VALUE_DIRECTION = ClosedVocabulary.of(
            "value_direction", "CK_kri_def_direction", "LOWER_IS_BETTER", "HIGHER_IS_BETTER");

    /** The direction whose bands run upward — the token {@code evaluateStatus} branches on. */
    static final String HIGHER_IS_BETTER = "HIGHER_IS_BETTER";

    private static final Set<String> FREQUENCIES = Set.of(
            "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "ANNUAL");

    /** Also package-private: the collection scheduler skips MANUAL KRIs and must agree on the token. */
    static final ClosedVocabulary SOURCE_QUERY_TYPE = ClosedVocabulary.of(
            "source_query_type", "CK_kri_def_source", "MANUAL", "SQL", "CALLBACK", "METRIC_API");

    /** A KRI whose value a human enters — the scheduler must not try to auto-collect it. */
    static final String SOURCE_MANUAL = "MANUAL";

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
        if (req.frequency() != null && !FREQUENCIES.contains(req.frequency()))
            throw new IllegalArgumentException("Invalid frequency: " + req.frequency());
        // F2-182 — value_direction and source_query_type moved out of validateEnums and into
        // applyFields, because a refusal and a normalisation are not interchangeable: validating here
        // and assigning there means the assigned value is the caller's string, so 'higher_is_better'
        // would have to be refused rather than accepted. Normalising at the assignment lets it succeed.
    }

    private static void applyFields(KriDefinition d, KriDefinitionRequest req) {
        if (req.titleEn() != null)         d.setTitleEn(req.titleEn());
        if (req.titleAr() != null)         d.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null)   d.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null)   d.setDescriptionAr(req.descriptionAr());
        if (req.category() != null)        d.setCategory(req.category());
        if (req.measurementUnit() != null) d.setMeasurementUnit(req.measurementUnit());
        if (req.valueDirection() != null)  d.setValueDirection(VALUE_DIRECTION.require(req.valueDirection()));
        if (req.targetValue() != null)     d.setTargetValue(req.targetValue());
        if (req.greenThreshold() != null)  d.setGreenThreshold(req.greenThreshold());
        if (req.amberThreshold() != null)  d.setAmberThreshold(req.amberThreshold());
        if (req.redThreshold() != null)    d.setRedThreshold(req.redThreshold());
        if (req.frequency() != null)       d.setFrequency(req.frequency());
        if (req.sourceQueryType() != null) d.setSourceQueryType(SOURCE_QUERY_TYPE.require(req.sourceQueryType()));
        if (req.sourceQuery() != null)     d.setSourceQuery(req.sourceQuery());
        if (req.ownerUserId() != null)     d.setOwnerUserId(req.ownerUserId());
        if (req.linkedRiskId() != null)    d.setLinkedRiskId(req.linkedRiskId());
        if (req.linkedFrameworkId() != null) d.setLinkedFrameworkId(req.linkedFrameworkId());
        if (req.clauseRef() != null)       d.setClauseRef(req.clauseRef());
        validateThresholdOrder(d.getValueDirection(), d.getGreenThreshold(), d.getAmberThreshold());
    }

    /**
     * F2-397 — a KRI is a green/amber RAG whose GREEN/AMBER thresholds must be strictly ordered
     * per direction for {@link KriMeasurementService#evaluateStatus} to produce correct statuses:
     * HIGHER_IS_BETTER needs {@code green > amber}, LOWER_IS_BETTER needs {@code green < amber}
     * (see the {@link KriDefinition} javadoc). An inverted pair silently mis-bands — a breaching
     * KRI evaluates GREEN, so {@code evaluateBreaches} opens no {@code KriBreach}, the sustained-RED
     * auto-CAPA ladder never advances, and the breach never escalates. Validates the MERGED entity
     * because {@code update} is a partial update — a request that sets only one threshold could
     * invert against the stored other (F2-292). Mirrors the house pattern
     * (VulnerabilitySlaPolicyService.validatePctOrder, RrpService.validateThresholdOrder / F2-396).
     */
    static void validateThresholdOrder(String direction, BigDecimal green, BigDecimal amber) {
        if (green == null || amber == null) return;   // evaluateStatus returns NA — nothing to order
        boolean higherBetter = VALUE_DIRECTION.is(direction, HIGHER_IS_BETTER);
        if (higherBetter && green.compareTo(amber) <= 0) {
            throw new IllegalArgumentException(
                    "greenThreshold (" + green + ") must be greater than amberThreshold (" + amber
                            + ") for a HIGHER_IS_BETTER KRI");
        }
        if (!higherBetter && green.compareTo(amber) >= 0) {
            throw new IllegalArgumentException(
                    "greenThreshold (" + green + ") must be less than amberThreshold (" + amber
                            + ") for a LOWER_IS_BETTER KRI");
        }
    }

    private String generateCode(Long orgId, String category) {
        String safeCat = (category == null || category.isBlank()) ? "GEN" : category;
        String prefix = "KRI-" + safeCat;
        for (int i = 0; i < 20; i++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format(Locale.ROOT, "%s-%03d", prefix, seq);
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
