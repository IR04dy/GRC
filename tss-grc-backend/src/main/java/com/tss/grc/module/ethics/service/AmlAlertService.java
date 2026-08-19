package com.tss.grc.module.ethics.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.dto.AssignAnalystRequest;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
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
    private final TenantUserDirectory directory;

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

    /**
     * F2-249 — assign the alert to an analyst, and make the two halves of that assignment agree.
     *
     * <p>{@code ethics.aml_alerts} declares {@code analyst_id} beside {@code analyst_name}. Before
     * this, only the label was ever written — from {@code body.get("analystName")}, which the
     * frontend collected through a {@code window.prompt} — so {@code analyst_id} was NULL on
     * <b>10 of 10</b> live rows in dev and prod and <b>0 of 3</b> stored labels resolved to any
     * real user. Probed live as {@code auditor}: an alert moved {@code OPEN -> INVESTIGATING}
     * naming an analyst who does not exist, at HTTP 200.
     *
     * <p>🔑 {@code decidedByName} twenty lines below has taken the acting principal from
     * {@link CurrentActor} since F2-223. <b>That is why this looked covered</b> — F2-215's
     * two-of-N, inside one service, and on the same screen: F2-223 deleted this page's
     * {@code amlDecidedByPrompt} and left the {@code amlAssignPrompt} beside it.
     *
     * <p>⚠ A free-text label is still accepted, because the schema says an unresolvable assignee
     * is representable and live rows use it for a <em>team</em> ({@code 'Financial Crime Unit'}).
     * Refusing it would be a fix that fails on correct usage (F2-214). What changed is that a
     * caller can no longer produce a row that <b>looks</b> linked: an id is resolved in-tenant and
     * the label is derived from what resolved, so the register can tell an assignment to a named
     * person from an assignment to a function — and it discloses which.
     */
    @Transactional
    public AmlAlert assign(Long id, AssignAnalystRequest req) {
        AmlAlert a = require(id);
        boolean hasId = req != null && req.analystUserId() != null;
        String label = req == null ? null : blank(req.analystName());
        if (!hasId && label == null)
            throw new IllegalArgumentException(
                "An assignment needs an assignee: send analystUserId for a platform user, or "
              + "analystName for a team or external party.");

        if (hasId) {
            String derived = directory.requireLabel(req.analystUserId(), "an AML analyst assignment");
            // An echo of the derived label is fine; a label that says something else is not,
            // because the row would then assert two different assignees (F2-231/F2-234).
            if (label != null && !label.equalsIgnoreCase(derived))
                throw new IllegalArgumentException(
                    "analystName '" + label + "' does not name user " + req.analystUserId()
                  + " ('" + derived + "'). Send the id alone and the register will name them, or "
                  + "send the name alone to record an assignment to a team.");
            a.setAnalystId(req.analystUserId());
            a.setAnalystName(derived);
        } else {
            a.setAnalystId(null);
            a.setAnalystName(label);
        }
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
                // F2-223 — the decision-maker is the authenticated principal. KycService#disposition,
                // in this same package and over the same concept, already does exactly this and
                // says why: "An AML disposition with no decider is not a disposition." Here the
                // client filled it from a window.prompt(), and `?? ''` on Cancel meant the STR
                // filed with the Saudi FIU could be attributed to the empty string.
                a.setDecidedByName(CurrentActor.username());
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
                // F2-223 — the decision-maker is the authenticated principal. KycService#disposition,
                // in this same package and over the same concept, already does exactly this and
                // says why: "An AML disposition with no decider is not a disposition." Here the
                // client filled it from a window.prompt(), and `?? ''` on Cancel meant the STR
                // filed with the Saudi FIU could be attributed to the empty string.
                a.setDecidedByName(CurrentActor.username());
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
