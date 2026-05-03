package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.Disclosure;
import com.tss.grc.module.ethics.repository.DisclosureRepository;
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

/**
 * Disclosure lifecycle service.
 * State machine: DRAFT → SUBMITTED → REVIEW → APPROVED|REJECTED|RECUSED|MITIGATED.
 */
@Service
@RequiredArgsConstructor
public class DisclosureService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT",     Set.of("SUBMITTED"),
        "SUBMITTED", Set.of("REVIEW", "REJECTED"),
        "REVIEW",    Set.of("APPROVED", "REJECTED", "RECUSED", "MITIGATED"),
        "APPROVED",  Set.of(),
        "REJECTED",  Set.of("DRAFT"),
        "RECUSED",   Set.of(),
        "MITIGATED", Set.of()
    );

    private final DisclosureRepository repo;

    @Transactional(readOnly = true)
    public Page<Disclosure> list(String type, String status, Pageable pageable) {
        return repo.findWithFilters(TenantContext.getCurrentTenant(), blank(type), blank(status), pageable);
    }

    @Transactional(readOnly = true)
    public Disclosure get(Long id) { return require(id); }

    @Transactional
    public Disclosure create(Disclosure req) {
        Disclosure d = new Disclosure();
        d.setOrganisationId(TenantContext.getCurrentTenant());
        apply(d, req);
        if (d.getStatus() == null) d.setStatus("DRAFT");
        if (d.getCurrency() == null) d.setCurrency("SAR");
        return repo.save(d);
    }

    @Transactional
    public Disclosure update(Long id, Disclosure req) {
        Disclosure d = require(id);
        apply(d, req);
        return repo.save(d);
    }

    @Transactional
    public void delete(Long id) {
        Disclosure d = require(id);
        d.setIsDeleted(true);
        repo.save(d);
    }

    @Transactional
    public Disclosure transition(Long id, String target, Map<String, Object> body) {
        Disclosure d = require(id);
        Set<String> allowed = TRANSITIONS.getOrDefault(d.getStatus(), Set.of());
        if (!allowed.contains(target))
            throw new IllegalArgumentException("Cannot transition from " + d.getStatus() + " to " + target);
        if (List.of("APPROVED", "REJECTED", "RECUSED", "MITIGATED").contains(target)) {
            d.setDecision(body.get("decision") != null ? body.get("decision").toString() : target);
            d.setDecisionNotes(body.get("notes") != null ? body.get("notes").toString() : null);
            d.setDecidedByName(body.get("decidedBy") != null ? body.get("decidedBy").toString() : null);
            d.setDecidedAt(LocalDateTime.now());
        }
        d.setStatus(target);
        return repo.save(d);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total", repo.countByOrganisationId(org));

        Map<String, Long> byStatus = new HashMap<>();
        for (String s : List.of("DRAFT","SUBMITTED","REVIEW","APPROVED","REJECTED","RECUSED","MITIGATED"))
            byStatus.put(s, repo.countByOrganisationIdAndStatus(org, s));
        out.put("byStatus", byStatus);

        Map<String, Long> byType = new HashMap<>();
        for (String t : List.of("CONFLICT_OF_INTEREST","GIFT_RECEIVED","GIFT_GIVEN","HOSPITALITY",
                                "OUTSIDE_BUSINESS","RELATED_PARTY","POLITICAL_DONATION","CHARITABLE"))
            byType.put(t, repo.countByOrganisationIdAndDisclosureType(org, t));
        out.put("byType", byType);
        return out;
    }

    private void apply(Disclosure target, Disclosure src) {
        if (src.getDisclosureCode()         != null) target.setDisclosureCode(src.getDisclosureCode());
        if (src.getDisclosureType()         != null) target.setDisclosureType(src.getDisclosureType());
        if (src.getTitleEn()                != null) target.setTitleEn(src.getTitleEn());
        if (src.getTitleAr()                != null) target.setTitleAr(src.getTitleAr());
        if (src.getDescription()            != null) target.setDescription(src.getDescription());
        if (src.getDiscloserName()          != null) target.setDiscloserName(src.getDiscloserName());
        if (src.getDiscloserEmail()         != null) target.setDiscloserEmail(src.getDiscloserEmail());
        if (src.getDiscloserDepartment()    != null) target.setDiscloserDepartment(src.getDiscloserDepartment());
        if (src.getDiscloserRole()          != null) target.setDiscloserRole(src.getDiscloserRole());
        if (src.getCounterpartyName()       != null) target.setCounterpartyName(src.getCounterpartyName());
        if (src.getCounterpartyRelationship() != null) target.setCounterpartyRelationship(src.getCounterpartyRelationship());
        if (src.getMonetaryValue()          != null) target.setMonetaryValue(src.getMonetaryValue());
        if (src.getCurrency()               != null) target.setCurrency(src.getCurrency());
        if (src.getOccurredOn()             != null) target.setOccurredOn(src.getOccurredOn());
        if (src.getFrameworkId()            != null) target.setFrameworkId(src.getFrameworkId());
        if (src.getClauseRef()              != null) target.setClauseRef(src.getClauseRef());
        if (src.getStatus()                 != null) target.setStatus(src.getStatus());
    }

    private Disclosure require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(d -> d.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Disclosure not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
