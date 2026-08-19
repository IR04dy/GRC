package com.tss.grc.module.ethics.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.dto.CaseActionRequest;
import com.tss.grc.module.ethics.entity.CaseAction;
import com.tss.grc.module.ethics.entity.CaseLink;
import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.repository.CaseActionRepository;
import com.tss.grc.module.ethics.repository.CaseLinkRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private BroadcastHelper bcast;
    private void emit(EthicsCase c) {
        if (bcast != null) bcast.entity(c.getOrganisationId(), "ethics_case", c.getId(), c);
    }

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
        requireLifecycleIsNotBodySet(c, req, true);
        apply(c, req);
        if (c.getStatus()        == null) c.setStatus("INTAKE");
        if (c.getSeverity()      == null) c.setSeverity("MEDIUM");
        if (c.getIntakeChannel() == null) c.setIntakeChannel("HOTLINE");
        EthicsCase saved = caseRepo.save(c);
        emit(saved);
        return saved;
    }

    @Transactional
    public EthicsCase update(Long id, EthicsCase req) {
        EthicsCase c = require(id);
        requireLifecycleIsNotBodySet(c, req, false);
        apply(c, req);
        EthicsCase saved = caseRepo.save(c);
        emit(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        EthicsCase c = require(id);
        c.setIsDeleted(true);
        caseRepo.save(c);
        emit(c);
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
        EthicsCase saved = caseRepo.save(c);
        emit(saved);
        return saved;
    }

    /**
     * Record an action on an ethics case — an interview, a finding, an HR referral, closure.
     *
     * <p>F2-248 — <b>the actor is the acting principal.</b> This used to bind {@link CaseAction}
     * and never touch {@code actorName} or {@code actorId}, so both were stored exactly as they
     * arrived. {@code action_at} is {@code insertable = false}, so the database has always owned
     * <i>when</i> while the caller owned <i>who</i>: F2-223's split attribution, with the
     * server-clock half arriving through the one filling mechanism a setter-matching scan cannot
     * see (F2-226).
     *
     * <p>🔑 The symptom here is the <b>inverse</b> of its twin in {@code change_mgmt}:
     * {@code EthicsPage} sends no actor at all, so every action added through the platform's own
     * screen recorded {@code actor_name = NULL} and {@code actor_id = NULL} — the action log of a
     * misconduct investigation saying what happened and when, and not who did it. A register that
     * does not own WHO either stores a claim or stores nothing, and neither is an attribution.
     *
     * <p>Both halves are required, not merely read: an authenticated principal on this route always
     * carries an id, so refusing cannot break a working request, and a half-recorded attribution is
     * the state that reads as complete while resolving to nobody (F2-236).
     */
    @Transactional
    public CaseAction addAction(Long caseId, CaseActionRequest req) {
        require(caseId);
        if (req.actionType() == null || req.actionType().isBlank())
            throw new IllegalArgumentException("actionType is required");
        if (req.title() == null || req.title().isBlank())
            throw new IllegalArgumentException("title is required");

        CaseAction body = new CaseAction();
        body.setCaseId(caseId);
        body.setActionType(req.actionType().trim());
        body.setTitle(req.title().trim());
        body.setDescription(req.description());
        if (req.isConfidential() != null) body.setIsConfidential(req.isConfidential());
        body.setActorName(CurrentActor.requireUsername("a case action"));
        body.setActorId(CurrentActor.requireUserId("a case action"));
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
        if (src.getOutcomeSummary()     != null) target.setOutcomeSummary(src.getOutcomeSummary());
        if (src.getRegulatorReference() != null) target.setRegulatorReference(src.getRegulatorReference());
        if (src.getRetaliationObserved()!= null) target.setRetaliationObserved(src.getRetaliationObserved());
        // F2-22b(v): `status` and `outcome` are DELIBERATELY not copied here — they belong to
        // transition(). Same decision CM-C3 made for ChangeRequest#apply, and for the same reason.
        // See requireLifecycleIsNotBodySet for what the callers enforce instead.
    }

    /**
     * F2-22b(v) — the case lifecycle belongs to {@code transition()}, not to the request body.
     *
     * <p>{@code apply} used to copy {@code status} and {@code outcome} straight from the body on both
     * create and update, so {@code POST /api/v1/ethics/cases {"status":"CLOSED",
     * "outcome":"UNSUBSTANTIATED"}} filed a whistleblower report that was already investigated and
     * dismissed, and a {@code PUT} could move any case to any state at any time. That skipped the
     * whole chain (INTAKE → TRIAGE → INVESTIGATION → SUBSTANTIATED/UNSUBSTANTIATED/NO_VIOLATION →
     * REMEDIATION → CLOSED) — but the sharper loss is the **timestamps**, because
     * {@code transition()} is the only thing that stamps {@code triagedAt},
     * {@code investigationStarted}, {@code investigationEnded}, {@code closureDate} and
     * {@code regulatorNotified}. A case closed through the body therefore carries a verdict with no
     * triage date, no investigation window and no closure date: on a whistleblowing register that is
     * indistinguishable from a report that was quietly made to go away.
     *
     * <p><b>Why this rejects a CHANGE but tolerates an ECHO.</b> `EthicsPage`'s edit modal seeds
     * itself with {@code reqFromDto(c) = { ...c }} — the whole DTO — so an ordinary metadata edit
     * PUTs the case's <em>current</em> status and outcome straight back. Refusing any present value
     * would break the edit form on every case past INTAKE. This is the standing lesson from tranche
     * (ii) that <b>the UI is what falsifies a "just refuse the caller's value" fix</b>, so: an
     * unchanged value is ignored, a different one is refused and names the endpoint that owns it.
     *
     * <p>(READ_ONLY on the entity would also have tolerated the echo, and is the right tool for a
     * field the UI merely round-trips — it is what tranches (ii)–(iv) used. It is wrong <em>here</em>
     * because {@code status} must still be settable on the way in for an integration that legitimately
     * opens a case at INTAKE, and because a silent drop would leave a caller believing it had closed
     * a case. F2-44's rule: say so.)
     */
    private void requireLifecycleIsNotBodySet(EthicsCase current, EthicsCase src, boolean isCreate) {
        String curStatus  = isCreate ? "INTAKE" : current.getStatus();
        String curOutcome = isCreate ? null     : current.getOutcome();

        if (src.getStatus() != null && !src.getStatus().equals(curStatus)) {
            throw new IllegalArgumentException(
                (isCreate
                    ? "A new ethics case starts at INTAKE (received: " + src.getStatus() + ")."
                    : "Case " + current.getId() + " is at " + curStatus + " and cannot be moved to "
                      + src.getStatus() + " by an update.")
                + " Status changes go through POST /api/v1/ethics/cases/"
                + (isCreate ? "{id}" : current.getId()) + "/transition, which enforces the chain and"
                + " stamps triagedAt / investigationStarted / investigationEnded / closureDate."
                + " Allowed from " + curStatus + ": " + new java.util.TreeSet<>(
                    TRANSITIONS.getOrDefault(curStatus, Set.of())) + ".");
        }
        if (src.getOutcome() != null && !src.getOutcome().equals(curOutcome)) {
            throw new IllegalArgumentException(
                (isCreate ? "A new ethics case has no outcome" : "The outcome of case "
                    + current.getId() + " is not editable")
                + " — it is recorded by the REMEDIATION transition (received: "
                + src.getOutcome() + "). An outcome that did not come from an investigation is"
                + " not a finding.");
        }
    }

    private EthicsCase require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return caseRepo.findById(id)
            .filter(c -> c.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Case not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
