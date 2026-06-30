package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.dto.KriDefinitionRequest;
import com.tss.grc.module.risk.entity.KriBreachEvent;
import com.tss.grc.module.risk.entity.KriDefinition;
import com.tss.grc.module.risk.entity.KriReading;
import com.tss.grc.module.risk.repository.KriBreachEventRepository;
import com.tss.grc.module.risk.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.repository.KriReadingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy KRI threshold engine — assessment §5.1 ERM #5.
 *
 * On every reading we evaluate the band (GREEN/AMBER/RED) using direction +
 * green_max + amber_max, persist the band on the reading, and auto-generate
 * a kri_breach_event whenever the new band is AMBER or RED **and** worse than
 * the previous reading's band.
 *
 * <p><b>Deprecated (ERM epic E-E).</b> Superseded by the canonical Sprint-13
 * KRI engine under {@code com.tss.grc.module.risk.kri} (UUID external ids,
 * full breach open/escalate/de-escalate/close lifecycle, F8 domain events,
 * sustained-RED auto-CAPA, scheduler). This numeric-id engine and its tables
 * ({@code risk.kri_definitions}/{@code kri_readings}/{@code kri_breach_events})
 * are retained only until the frontend + reporting/board-pack/framework-anchor
 * consumers are repointed to canonical and the legacy rows are migrated; see
 * the remediation plan §20 E-E for the remaining work.
 *
 * @deprecated use {@link com.tss.grc.module.risk.kri.service.KriDefinitionService},
 *             {@link com.tss.grc.module.risk.kri.service.KriMeasurementService} and
 *             {@link com.tss.grc.module.risk.kri.service.KriBreachService} instead.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class KriEngineService {

    private final KriDefinitionRepository kriRepo;
    private final KriReadingRepository readingRepo;
    private final KriBreachEventRepository breachRepo;

    // ── Definitions ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<KriDefinition> listDefinitions(Pageable pageable) {
        return kriRepo.findByOrganisationIdOrderByCodeAsc(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public KriDefinition getDefinition(Long id) {
        return require(id);
    }

    @Transactional
    public KriDefinition createDefinition(KriDefinitionRequest req) {
        // SEC-RISK-002: organisationId is stamped from the tenant context, never
        // from the request body; id/externalId/createdBy are server-managed.
        KriDefinition k = new KriDefinition();
        k.setOrganisationId(TenantContext.getCurrentTenant());
        apply(k, req);
        if (k.getDirection() == null) k.setDirection("HIGHER_IS_WORSE");
        return kriRepo.save(k);
    }

    @Transactional
    public KriDefinition updateDefinition(Long id, KriDefinitionRequest req) {
        KriDefinition k = require(id);  // tenant-scoped load
        apply(k, req);
        return kriRepo.save(k);
    }

    @Transactional
    public void deleteDefinition(Long id) {
        KriDefinition k = require(id);
        k.setIsDeleted(true);
        kriRepo.save(k);
    }

    private void apply(KriDefinition target, KriDefinitionRequest src) {
        if (src.riskId()        != null) target.setRiskId(src.riskId());
        if (src.categoryId()    != null) target.setCategoryId(src.categoryId());
        if (src.code()          != null) target.setCode(src.code());
        if (src.nameEn()        != null) target.setNameEn(src.nameEn());
        if (src.nameAr()        != null) target.setNameAr(src.nameAr());
        if (src.descriptionEn() != null) target.setDescriptionEn(src.descriptionEn());
        if (src.descriptionAr() != null) target.setDescriptionAr(src.descriptionAr());
        if (src.unit()          != null) target.setUnit(src.unit());
        if (src.direction()     != null) target.setDirection(src.direction());
        if (src.greenMax()      != null) target.setGreenMax(src.greenMax());
        if (src.amberMax()      != null) target.setAmberMax(src.amberMax());
        if (src.targetValue()   != null) target.setTargetValue(src.targetValue());
        if (src.frequency()     != null) target.setFrequency(src.frequency());
        if (src.owner()         != null) target.setOwner(src.owner());
        if (src.dataSource()    != null) target.setDataSource(src.dataSource());
        if (src.framework()     != null) target.setFramework(src.framework());
        if (src.isActive()      != null) target.setIsActive(src.isActive());
    }

    // ── Readings + auto-breach generation ───────────────────────────────────

    @Transactional
    public Map<String, Object> recordReading(Long kriId, BigDecimal value, String notes) {
        KriDefinition k = require(kriId);

        // Compute previous band (for breach detection)
        String previousBand = readingRepo.findFirstByKriIdOrderByReadingAtDesc(kriId)
                .map(KriReading::getBand).orElse("GREEN");

        // Evaluate new band
        String newBand = evaluateBand(k, value);

        KriReading r = new KriReading();
        r.setKriId(kriId);
        r.setReadingValue(value);
        r.setReadingAt(LocalDateTime.now());
        r.setBand(newBand);
        r.setNotes(notes);
        readingRepo.save(r);

        Map<String, Object> out = new HashMap<>();
        out.put("readingId",     r.getId());
        out.put("band",          newBand);
        out.put("previousBand",  previousBand);
        out.put("breachCreated", false);

        // Auto-breach: only when new band is worse than previous
        if (worseThan(newBand, previousBand) && !"GREEN".equals(newBand)) {
            KriBreachEvent ev = new KriBreachEvent();
            ev.setKriId(kriId);
            ev.setReadingId(r.getId());
            ev.setBreachBand(newBand);
            ev.setReadingValue(value);
            ev.setThresholdCrossed("RED".equals(newBand) ? safeOr(k.getAmberMax(), BigDecimal.ZERO)
                                                          : safeOr(k.getGreenMax(), BigDecimal.ZERO));
            ev.setBreachedAt(LocalDateTime.now());
            ev.setStatus("OPEN");
            breachRepo.save(ev);
            out.put("breachCreated", true);
            out.put("breachId", ev.getId());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<KriReading> listReadings(Long kriId) {
        require(kriId);
        return readingRepo.findTop60ByKriIdOrderByReadingAtDesc(kriId);
    }

    @Transactional(readOnly = true)
    public List<KriBreachEvent> listBreaches(Long kriId) {
        require(kriId);
        return breachRepo.findByKriIdOrderByBreachedAtDesc(kriId);
    }

    @Transactional
    public KriBreachEvent acknowledgeBreach(Long breachId, Long userId) {
        KriBreachEvent ev = breachRepo.findById(breachId)
            .orElseThrow(() -> new EntityNotFoundException("Breach not found: " + breachId));
        require(ev.getKriId());
        ev.setAcknowledgedAt(LocalDateTime.now());
        ev.setAcknowledgedById(userId);
        ev.setStatus("ACKNOWLEDGED");
        return breachRepo.save(ev);
    }

    @Transactional
    public KriBreachEvent resolveBreach(Long breachId, String notes) {
        KriBreachEvent ev = breachRepo.findById(breachId)
            .orElseThrow(() -> new EntityNotFoundException("Breach not found: " + breachId));
        require(ev.getKriId());
        ev.setResolvedAt(LocalDateTime.now());
        ev.setResolutionNotes(notes);
        ev.setStatus("RESOLVED");
        return breachRepo.save(ev);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Long org = TenantContext.getCurrentTenant();
        List<KriDefinition> kris = kriRepo
            .findByOrganisationIdOrderByCodeAsc(org, Pageable.ofSize(200))
            .getContent();

        int green = 0, amber = 0, red = 0, noData = 0;
        long openBreaches = 0;
        for (KriDefinition k : kris) {
            String latestBand = readingRepo.findFirstByKriIdOrderByReadingAtDesc(k.getId())
                .map(KriReading::getBand).orElse(null);
            if (latestBand == null) noData++;
            else if ("GREEN".equals(latestBand)) green++;
            else if ("AMBER".equals(latestBand)) amber++;
            else if ("RED".equals(latestBand))   red++;
            openBreaches += breachRepo.findByKriIdAndStatusOrderByBreachedAtDesc(k.getId(), "OPEN").size();
        }
        Map<String, Object> out = new HashMap<>();
        out.put("totalKris",     kris.size());
        out.put("green",         green);
        out.put("amber",         amber);
        out.put("red",           red);
        out.put("noData",        noData);
        out.put("openBreaches",  openBreaches);
        return out;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    static String evaluateBand(KriDefinition k, BigDecimal v) {
        if (v == null) return null;
        BigDecimal green = k.getGreenMax();
        BigDecimal amber = k.getAmberMax();
        boolean higher = !"LOWER_IS_WORSE".equalsIgnoreCase(k.getDirection());
        if (higher) {
            // green ≤ green_max < amber ≤ amber_max < red
            if (green != null && v.compareTo(green) <= 0) return "GREEN";
            if (amber != null && v.compareTo(amber) <= 0) return "AMBER";
            return "RED";
        } else {
            // green ≥ green_max > amber ≥ amber_max > red
            if (green != null && v.compareTo(green) >= 0) return "GREEN";
            if (amber != null && v.compareTo(amber) >= 0) return "AMBER";
            return "RED";
        }
    }

    private static int rank(String band) {
        return switch (band == null ? "" : band) {
            case "GREEN" -> 0;
            case "AMBER" -> 1;
            case "RED"   -> 2;
            default      -> 0;
        };
    }

    private static boolean worseThan(String a, String b) {
        return rank(a) > rank(b);
    }

    private static BigDecimal safeOr(BigDecimal v, BigDecimal fallback) {
        return v == null ? fallback : v;
    }

    private KriDefinition require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return kriRepo.findById(id)
            .filter(k -> k.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("KRI not found: " + id));
    }
}
