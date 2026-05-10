package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AmlAlertService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "OPEN",          Set.of("INVESTIGATING"),
        "INVESTIGATING", Set.of("CLEARED", "ESCALATED", "STR_FILED"),
        "CLEARED",       Set.of("CLOSED"),
        "ESCALATED",     Set.of("STR_FILED", "CLOSED"),
        "STR_FILED",     Set.of("CLOSED"),
        "CLOSED",        Set.of()
    );

    private final AmlAlertRepository repo;

    @Transactional(readOnly = true)
    public Page<AmlAlert> list(String status, String type, String severity, Pageable pageable) {
        return repo.findWithFilters(TenantContext.getCurrentTenant(),
            blank(status), blank(type), blank(severity), pageable);
    }

    @Transactional(readOnly = true)
    public AmlAlert get(Long id) { return require(id); }

    @Transactional
    public AmlAlert create(AmlAlert req) {
        AmlAlert a = new AmlAlert();
        a.setOrganisationId(TenantContext.getCurrentTenant());
        apply(a, req);
        if (a.getStatus() == null) a.setStatus("OPEN");
        if (a.getCurrency() == null) a.setCurrency("SAR");
        return repo.save(a);
    }

    @Transactional
    public AmlAlert update(Long id, AmlAlert req) {
        AmlAlert a = require(id);
        apply(a, req);
        return repo.save(a);
    }

    @Transactional
    public void delete(Long id) {
        AmlAlert a = require(id);
        a.setIsDeleted(true);
        repo.save(a);
    }

    @Transactional
    public AmlAlert assign(Long id, String analystName) {
        AmlAlert a = require(id);
        a.setAnalystName(analystName);
        if ("OPEN".equals(a.getStatus())) a.setStatus("INVESTIGATING");
        return repo.save(a);
    }

    @Transactional
    public AmlAlert transition(Long id, String target, Map<String, Object> body) {
        AmlAlert a = require(id);
        Set<String> allowed = TRANSITIONS.getOrDefault(a.getStatus(), Set.of());
        if (!allowed.contains(target))
            throw new IllegalArgumentException("Cannot transition from " + a.getStatus() + " to " + target);
        switch (target) {
            case "CLEARED" -> {
                a.setDisposition(body.get("disposition") != null ? body.get("disposition").toString() : "FALSE_POSITIVE");
                a.setDispositionNotes(body.get("notes") != null ? body.get("notes").toString() : null);
                a.setDecidedByName(body.get("decidedBy") != null ? body.get("decidedBy").toString() : null);
                a.setDecidedAt(LocalDateTime.now());
            }
            case "ESCALATED" -> {
                a.setDisposition(body.get("disposition") != null ? body.get("disposition").toString() : "TRUE_POSITIVE");
                a.setDispositionNotes(body.get("notes") != null ? body.get("notes").toString() : null);
            }
            case "STR_FILED" -> {
                a.setStrFiled(true);
                a.setStrReference(body.get("strReference") != null ? body.get("strReference").toString() : null);
                a.setStrFiledAt(LocalDateTime.now());
                a.setDisposition(body.get("disposition") != null ? body.get("disposition").toString() : "TRUE_POSITIVE");
                a.setDispositionNotes(body.get("notes") != null ? body.get("notes").toString() : null);
                a.setDecidedByName(body.get("decidedBy") != null ? body.get("decidedBy").toString() : null);
                if (a.getDecidedAt() == null) a.setDecidedAt(LocalDateTime.now());
            }
            case "CLOSED" -> { /* keep all decision metadata as-is */ }
            default -> { /* no-op */ }
        }
        a.setStatus(target);
        return repo.save(a);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total", repo.countByOrganisationId(org));

        Map<String, Long> byStatus = new HashMap<>();
        for (String s : List.of("OPEN","INVESTIGATING","CLEARED","ESCALATED","STR_FILED","CLOSED"))
            byStatus.put(s, repo.countByOrganisationIdAndStatus(org, s));
        out.put("byStatus", byStatus);

        Map<String, Long> bySeverity = new HashMap<>();
        for (String s : List.of("LOW","MEDIUM","HIGH","CRITICAL"))
            bySeverity.put(s, repo.countByOrganisationIdAndSeverity(org, s));
        out.put("bySeverity", bySeverity);

        long open = byStatus.getOrDefault("OPEN", 0L) + byStatus.getOrDefault("INVESTIGATING", 0L);
        long strs = repo.countByOrganisationIdAndStrFiled(org, true);
        out.put("openAlerts", open);
        out.put("strFiled",   strs);
        return out;
    }

    private void apply(AmlAlert t, AmlAlert s) {
        if (s.getAlertCode()           != null) t.setAlertCode(s.getAlertCode());
        if (s.getAlertType()           != null) t.setAlertType(s.getAlertType());
        if (s.getSeverity()            != null) t.setSeverity(s.getSeverity());
        if (s.getRuleId()              != null) t.setRuleId(s.getRuleId());
        if (s.getRuleName()            != null) t.setRuleName(s.getRuleName());
        if (s.getTitleEn()             != null) t.setTitleEn(s.getTitleEn());
        if (s.getTitleAr()             != null) t.setTitleAr(s.getTitleAr());
        if (s.getDescription()         != null) t.setDescription(s.getDescription());
        if (s.getSubjectId()           != null) t.setSubjectId(s.getSubjectId());
        if (s.getSubjectName()         != null) t.setSubjectName(s.getSubjectName());
        if (s.getCounterpartyName()    != null) t.setCounterpartyName(s.getCounterpartyName());
        if (s.getCounterpartyCountry() != null) t.setCounterpartyCountry(s.getCounterpartyCountry());
        if (s.getTransactionCount()    != null) t.setTransactionCount(s.getTransactionCount());
        if (s.getAggregateAmount()     != null) t.setAggregateAmount(s.getAggregateAmount());
        if (s.getCurrency()            != null) t.setCurrency(s.getCurrency());
        if (s.getPeriodFrom()          != null) t.setPeriodFrom(s.getPeriodFrom());
        if (s.getPeriodTo()            != null) t.setPeriodTo(s.getPeriodTo());
        if (s.getStatus()              != null) t.setStatus(s.getStatus());
        if (s.getAnalystName()         != null) t.setAnalystName(s.getAnalystName());
        if (s.getSlaDueAt()            != null) t.setSlaDueAt(s.getSlaDueAt());
        if (s.getFrameworkId()         != null) t.setFrameworkId(s.getFrameworkId());
        if (s.getClauseRef()           != null) t.setClauseRef(s.getClauseRef());
    }

    private AmlAlert require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(a -> a.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("AML alert not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
