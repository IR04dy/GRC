package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.CaseAction;
import com.tss.grc.module.ethics.entity.CaseLink;
import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.repository.CaseActionRepository;
import com.tss.grc.module.ethics.repository.CaseLinkRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ethics Case lifecycle service.
 *
 * State machine:
 *   INTAKE → TRIAGE → INVESTIGATION ┬─► SUBSTANTIATED → REMEDIATION → CLOSED
 *                                   ├─► UNSUBSTANTIATED → CLOSED
 *                                   └─► NO_VIOLATION → CLOSED
 *   Any state → ESCALATED (regulator notified) or DISMISSED.
 */
@Service
@RequiredArgsConstructor
public class EthicsCaseService {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "INTAKE",          Set.of("TRIAGE", "DISMISSED", "ESCALATED"),
        "TRIAGE",          Set.of("INVESTIGATION", "DISMISSED", "ESCALATED"),
        "INVESTIGATION",   Set.of("SUBSTANTIATED", "UNSUBSTANTIATED", "NO_VIOLATION", "ESCALATED", "DISMISSED"),
        "SUBSTANTIATED",   Set.of("REMEDIATION", "ESCALATED", "CLOSED"),
        "UNSUBSTANTIATED", Set.of("CLOSED"),
        "NO_VIOLATION",    Set.of("CLOSED"),
        "REMEDIATION",     Set.of("CLOSED"),
        "ESCALATED",       Set.of("CLOSED"),
        "DISMISSED",       Set.of(),
        "CLOSED",          Set.of()
    );

    private final EthicsCaseRepository caseRepo;
    private final CaseActionRepository actionRepo;
    private final CaseLinkRepository linkRepo;

    @Transactional(readOnly = true)
    public Page<EthicsCase> list(String status, String category, String severity, Pageable pageable) {
        return caseRepo.findWithFilters(
            TenantContext.getCurrentTenant(),
            blank(status), blank(category), blank(severity), pageable);
    }

    @Transactional(readOnly = true)
    public EthicsCase get(Long id) { return require(id); }

    @Transactional
    public EthicsCase create(EthicsCase req) {
        EthicsCase c = new EthicsCase();
        c.setOrganisationId(TenantContext.getCurrentTenant());
        apply(c, req);
        if (c.getStatus()        == null) c.setStatus("INTAKE");
        if (c.getSeverity()      == null) c.setSeverity("MEDIUM");
        if (c.getIntakeChannel() == null) c.setIntakeChannel("HOTLINE");
        return caseRepo.save(c);
    }

    @Transactional
    public EthicsCase update(Long id, EthicsCase req) {
        EthicsCase c = require(id);
        apply(c, req);
        return caseRepo.save(c);
    }

    @Transactional
    public void delete(Long id) {
        EthicsCase c = require(id);
        c.setIsDeleted(true);
        caseRepo.save(c);
    }

    @Transactional
    public EthicsCase transition(Long id, String target, Map<String, Object> body) {
        EthicsCase c = require(id);
        Set<String> allowed = TRANSITIONS.getOrDefault(c.getStatus(), Set.of());
        if (!allowed.contains(target))
            throw new IllegalArgumentException("Cannot transition from " + c.getStatus() + " to " + target);

        switch (target) {
            case "TRIAGE" -> {
                if (c.getTriagedAt() == null) c.setTriagedAt(LocalDateTime.now());
                if (body.get("triageDecision") != null) c.setTriageDecision(body.get("triageDecision").toString());
            }
            case "INVESTIGATION" -> {
                if (c.getInvestigationStarted() == null) c.setInvestigationStarted(LocalDateTime.now());
                if (body.get("investigatorName") != null) c.setInvestigatorName(body.get("investigatorName").toString());
                if (body.get("committeeName")    != null) c.setCommitteeName(body.get("committeeName").toString());
            }
            case "SUBSTANTIATED", "UNSUBSTANTIATED", "NO_VIOLATION" -> {
                if (c.getInvestigationEnded() == null) c.setInvestigationEnded(LocalDateTime.now());
                if (body.get("outcomeSummary") != null) c.setOutcomeSummary(body.get("outcomeSummary").toString());
            }
            case "REMEDIATION" -> {
                if (body.get("outcome") != null) c.setOutcome(body.get("outcome").toString());
                if (body.get("outcomeSummary") != null) c.setOutcomeSummary(body.get("outcomeSummary").toString());
            }
            case "ESCALATED" -> {
                c.setRegulatorNotified(true);
                if (body.get("regulatorReference") != null) c.setRegulatorReference(body.get("regulatorReference").toString());
            }
            case "CLOSED" -> {
                if (c.getClosureDate() == null) c.setClosureDate(LocalDate.now());
            }
            case "DISMISSED" -> {
                if (c.getClosureDate() == null) c.setClosureDate(LocalDate.now());
                if (body.get("outcomeSummary") != null) c.setOutcomeSummary(body.get("outcomeSummary").toString());
            }
            default -> { /* no-op */ }
        }
        c.setStatus(target);
        return caseRepo.save(c);
    }

    @Transactional
    public CaseAction addAction(Long caseId, CaseAction body) {
        require(caseId);
        body.setCaseId(caseId);
        if (body.getActionType() == null) throw new IllegalArgumentException("actionType is required");
        if (body.getTitle() == null || body.getTitle().isBlank()) throw new IllegalArgumentException("title is required");
        return actionRepo.save(body);
    }

    @Transactional(readOnly = true)
    public List<CaseAction> actions(Long caseId) {
        require(caseId);
        return actionRepo.findByCaseIdOrderByActionAtAsc(caseId);
    }

    @Transactional
    public CaseLink addLink(Long caseId, CaseLink body) {
        require(caseId);
        if (body.getEntityType() == null || body.getEntityId() == null)
            throw new IllegalArgumentException("entityType and entityId are required");
        body.setCaseId(caseId);
        if (body.getLinkType() == null) body.setLinkType("RELATES_TO");
        return linkRepo.save(body);
    }

    @Transactional(readOnly = true)
    public List<CaseLink> links(Long caseId) {
        require(caseId);
        return linkRepo.findByCaseId(caseId);
    }

    @Transactional
    public void deleteLink(Long linkId) {
        CaseLink l = linkRepo.findById(linkId)
            .orElseThrow(() -> new EntityNotFoundException("Link not found: " + linkId));
        require(l.getCaseId());
        l.setIsDeleted(true);
        linkRepo.save(l);
    }

    /** Inverse lookup — used by other modules to render "Linked Cases" panels. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> linksForEntity(String entityType, Long entityId) {
        Long org = TenantContext.getCurrentTenant();
        return linkRepo.findByEntityTypeAndEntityId(entityType, entityId).stream()
            .map(l -> caseRepo.findById(l.getCaseId())
                .filter(c -> c.getOrganisationId().equals(org) && !Boolean.TRUE.equals(c.getIsDeleted()))
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("linkId",    l.getId());
                    m.put("linkType",  l.getLinkType());
                    m.put("caseId",    c.getId());
                    m.put("caseCode",  c.getCaseCode());
                    m.put("titleEn",   c.getTitleEn());
                    m.put("titleAr",   c.getTitleAr());
                    m.put("status",    c.getStatus());
                    m.put("severity",  c.getSeverity());
                    m.put("category",  c.getCategory());
                    return m;
                })
                .orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total", caseRepo.countByOrganisationId(org));

        Map<String, Long> byStatus = new HashMap<>();
        for (String s : List.of("INTAKE","TRIAGE","INVESTIGATION","SUBSTANTIATED",
                                "UNSUBSTANTIATED","NO_VIOLATION","REMEDIATION",
                                "ESCALATED","DISMISSED","CLOSED"))
            byStatus.put(s, caseRepo.countByOrganisationIdAndStatus(org, s));
        out.put("byStatus", byStatus);

        Map<String, Long> byCategory = new HashMap<>();
        for (String c : List.of("BRIBERY","KICKBACK","CONFLICT_OF_INTEREST","FRAUD",
                                "THEFT","HARASSMENT","DISCRIMINATION","RETALIATION",
                                "DATA_MISUSE","AML_SUSPICION","POLICY_VIOLATION",
                                "SAFETY","OTHER"))
            byCategory.put(c, caseRepo.countByOrganisationIdAndCategory(org, c));
        out.put("byCategory", byCategory);

        long open = byStatus.getOrDefault("INTAKE", 0L)
                  + byStatus.getOrDefault("TRIAGE", 0L)
                  + byStatus.getOrDefault("INVESTIGATION", 0L)
                  + byStatus.getOrDefault("REMEDIATION", 0L)
                  + byStatus.getOrDefault("ESCALATED", 0L);
        long closed = byStatus.getOrDefault("CLOSED", 0L)
                    + byStatus.getOrDefault("DISMISSED", 0L);
        long substantiated = byStatus.getOrDefault("SUBSTANTIATED", 0L)
                           + byStatus.getOrDefault("REMEDIATION", 0L);
        out.put("open", open);
        out.put("closed", closed);
        out.put("substantiated", substantiated);
        return out;
    }

    private void apply(EthicsCase target, EthicsCase src) {
        if (src.getCaseCode()           != null) target.setCaseCode(src.getCaseCode());
        if (src.getTitleEn()            != null) target.setTitleEn(src.getTitleEn());
        if (src.getTitleAr()            != null) target.setTitleAr(src.getTitleAr());
        if (src.getCategory()           != null) target.setCategory(src.getCategory());
        if (src.getSeverity()           != null) target.setSeverity(src.getSeverity());
        if (src.getIntakeChannel()      != null) target.setIntakeChannel(src.getIntakeChannel());
        if (src.getReporterName()       != null) target.setReporterName(src.getReporterName());
        if (src.getReporterEmail()      != null) target.setReporterEmail(src.getReporterEmail());
        if (src.getIsAnonymous()        != null) target.setIsAnonymous(src.getIsAnonymous());
        if (src.getSubjectSummary()     != null) target.setSubjectSummary(src.getSubjectSummary());
        if (src.getDescription()        != null) target.setDescription(src.getDescription());
        if (src.getLocation()           != null) target.setLocation(src.getLocation());
        if (src.getDateOfIncident()     != null) target.setDateOfIncident(src.getDateOfIncident());
        if (src.getFrameworkId()        != null) target.setFrameworkId(src.getFrameworkId());
        if (src.getClauseRef()          != null) target.setClauseRef(src.getClauseRef());
        if (src.getInvestigatorName()   != null) target.setInvestigatorName(src.getInvestigatorName());
        if (src.getCommitteeName()      != null) target.setCommitteeName(src.getCommitteeName());
        if (src.getOutcome()            != null) target.setOutcome(src.getOutcome());
        if (src.getOutcomeSummary()     != null) target.setOutcomeSummary(src.getOutcomeSummary());
        if (src.getRegulatorReference() != null) target.setRegulatorReference(src.getRegulatorReference());
        if (src.getRetaliationObserved()!= null) target.setRetaliationObserved(src.getRetaliationObserved());
        if (src.getStatus()             != null) target.setStatus(src.getStatus());
    }

    private EthicsCase require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return caseRepo.findById(id)
            .filter(c -> c.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Case not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
