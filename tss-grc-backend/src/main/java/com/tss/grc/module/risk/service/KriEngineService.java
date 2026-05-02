package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
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
 * KRI threshold engine — assessment §5.1 ERM #5.
 *
 * On every reading we evaluate the band (GREEN/AMBER/RED) using direction +
 * green_max + amber_max, persist the band on the reading, and auto-generate
 * a kri_breach_event whenever the new band is AMBER or RED **and** worse than
 * the previous reading's band.
 */
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
    public KriDefinition createDefinition(KriDefinition req) {
        KriDefinition k = new KriDefinition();
        k.setOrganisationId(TenantContext.getCurrentTenant());
        apply(k, req);
        if (k.getDirection() == null) k.setDirection("HIGHER_IS_WORSE");
        return kriRepo.save(k);
    }

    @Transactional
    public KriDefinition updateDefinition(Long id, KriDefinition req) {
        KriDefinition k = require(id);
        apply(k, req);
        return kriRepo.save(k);
    }

    @Transactional
    public void deleteDefinition(Long id) {
        KriDefinition k = require(id);
        k.setIsDeleted(true);
        kriRepo.save(k);
    }

    private void apply(KriDefinition target, KriDefinition src) {
        if (src.getRiskId()        != null) target.setRiskId(src.getRiskId());
        if (src.getCategoryId()    != null) target.setCategoryId(src.getCategoryId());
        if (src.getCode()          != null) target.setCode(src.getCode());
        if (src.getNameEn()        != null) target.setNameEn(src.getNameEn());
        if (src.getNameAr()        != null) target.setNameAr(src.getNameAr());
        if (src.getDescriptionEn() != null) target.setDescriptionEn(src.getDescriptionEn());
        if (src.getDescriptionAr() != null) target.setDescriptionAr(src.getDescriptionAr());
        if (src.getUnit()          != null) target.setUnit(src.getUnit());
        if (src.getDirection()     != null) target.setDirection(src.getDirection());
        if (src.getGreenMax()      != null) target.setGreenMax(src.getGreenMax());
        if (src.getAmberMax()      != null) target.setAmberMax(src.getAmberMax());
        if (src.getTargetValue()   != null) target.setTargetValue(src.getTargetValue());
        if (src.getFrequency()     != null) target.setFrequency(src.getFrequency());
        if (src.getOwner()         != null) target.setOwner(src.getOwner());
        if (src.getDataSource()    != null) target.setDataSource(src.getDataSource());
        if (src.getFramework()     != null) target.setFramework(src.getFramework());
        if (src.getIsActive()      != null) target.setIsActive(src.getIsActive());
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
