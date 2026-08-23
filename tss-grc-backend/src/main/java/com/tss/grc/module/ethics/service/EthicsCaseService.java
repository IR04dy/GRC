package com.tss.grc.module.ethics.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.EthicsCaseLifecycle;
import com.tss.grc.module.ethics.dto.CaseActionRequest;
import com.tss.grc.module.ethics.entity.CaseAction;
import com.tss.grc.module.ethics.entity.CaseLink;
import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.repository.CaseActionRepository;
import com.tss.grc.module.ethics.repository.CaseLinkRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
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

    // F2-290 — the machine moved to EthicsCaseLifecycle, which is now its ONE declaration and
    // which DERIVES the terminal set from it (a state with no outgoing edges). It had to move
    // because three artefacts answered "is this case still open?" and no two agreed: the client
    // with the correct complement of {CLOSED, DISMISSED}, this service's analytics() with a
    // five-token inclusion list, and BoardPackAggregateService with a three-token one that prints
    // "Open N" into a board-pack PDF and PPTX. Measured 26 open, both server readers said 10.

    private final EthicsCaseRepository caseRepo;
    private final CaseActionRepository actionRepo;
    private final CaseLinkRepository linkRepo;
    private final TenantUserDirectory directory;

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
        Set<String> allowed = EthicsCaseLifecycle.allowedTargets(c.getStatus());
        if (!allowed.contains(target))
            throw new IllegalArgumentException("Cannot transition from " + c.getStatus() + " to " + target);

        switch (target) {
            case "TRIAGE" -> {
                if (c.getTriagedAt() == null) c.setTriagedAt(LocalDateTime.now());
                if (body.get("triageDecision") != null) c.setTriageDecision(body.get("triageDecision").toString());
            }
            case "INVESTIGATION" -> {
                if (c.getInvestigationStarted() == null) c.setInvestigationStarted(LocalDateTime.now());
                applyInvestigator(c, body);
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

        // F2-290 — both bucket loops are DERIVED from the module's one declaration. They were
        // typed inclusion lists, so a state or category nobody remembered was silently absent from
        // the chart rather than chased, and neither column carries a CHECK constraint.
        Map<String, Long> byStatus = new HashMap<>();
        for (String s : EthicsCaseLifecycle.STATES)
            byStatus.put(s, caseRepo.countByOrganisationIdAndStatus(org, s));
        out.put("byStatus", byStatus);

        Map<String, Long> byCategory = new HashMap<>();
        for (String c : EthicsCaseLifecycle.CATEGORY_TAXONOMY)
            byCategory.put(c, caseRepo.countByOrganisationIdAndCategory(org, c));
        out.put("byCategory", byCategory);

        // "Open" is the COMPLEMENT of the declared terminal set, and it is asked of the repository
        // so that this service and BoardPackAggregateService cannot answer it differently (F2-196).
        // It was INTAKE+TRIAGE+INVESTIGATION+REMEDIATION+ESCALATED, which answered 10 of 26.
        out.put("open",   caseRepo.countOpen(org));
        out.put("closed", caseRepo.countClosed(org));

        // Deliberately narrower and NOT a restatement of a vocabulary: "substantiated" is a
        // finding, so a case in REMEDIATION is still a substantiated one (F2-250's distinction).
        // It is a status question by necessity — `outcome` records the REMEDIATION result
        // (POLICY_UPDATE ...), not the verdict, so there is no durable column to count instead and
        // inventing one to make this figure stable would be F2-196's own defect. Consequence,
        // stated rather than glossed: the count falls when a substantiated case is CLOSED.
        out.put("substantiated", caseRepo.countByOrganisationIdAndStatus(org, "SUBSTANTIATED")
                               + caseRepo.countByOrganisationIdAndStatus(org, "REMEDIATION"));

        // The rows no bucket above can show: 11 statuses (INVESTIGATING, TRIAGED) and 6 categories
        // in both databases today. Surfaced rather than left to be inferred from a total that does
        // not add up, because an unrecognised value on a misconduct register must be chased, not
        // excused — and collapsing the synonyms rewrites live rows, which is the owner's call.
        out.put("undeclaredStatus",   caseRepo.countWithUndeclaredStatus(org));
        out.put("undeclaredCategory", caseRepo.countWithUndeclaredCategory(org));
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
                    EthicsCaseLifecycle.allowedTargets(curStatus)) + ".");
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

    /**
     * F2-249 — who is investigating this case, with the id and the label made to agree.
     *
     * <p>{@code ethics.cases} declares {@code investigator_id} beside {@code investigator_name},
     * and nothing had ever written the id: it is populated on 6 of 30 live rows in dev and prod,
     * all of them by the seed, and <b>0 of the 14 stored labels resolve to any real user</b>. The
     * label came straight from {@code body.get("investigatorName")}, so a misconduct or
     * whistleblowing case entered INVESTIGATION naming an investigator who need not exist —
     * probed live as {@code auditor}, HTTP 200.
     *
     * <p>⚠ Free text stays legal, because the labels the register really holds are
     * <b>functions</b>: {@code 'Internal Audit'}, {@code 'Group Legal'}, {@code 'External
     * Investigator'}, {@code 'Forensic Accountant'}. Several are deliberately outside the
     * platform — an external investigator has no login by definition — so requiring a resolvable
     * user here would refuse the very cases where independence matters most. The nullable id
     * beside the nullable name is the schema saying exactly that (F2-246's rule).
     *
     * <p>What is closed is the <em>appearance</em> of a link: an id is resolved against this
     * tenant and the label is derived from it, so no caller can produce a row whose two halves
     * describe different people.
     */
    private void applyInvestigator(EthicsCase c, Map<String, Object> body) {
        Object idRaw = body.get("investigatorUserId");
        Object nameRaw = body.get("investigatorName");
        String name = nameRaw == null ? null : blankToNull(nameRaw.toString());

        if (idRaw != null) {
            Long userId;
            try {
                userId = Long.valueOf(idRaw.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "investigatorUserId '" + idRaw + "' is not a user id.");
            }
            String derived = directory.requireLabel(userId, "an investigator assignment");
            if (name != null && !name.equalsIgnoreCase(derived))
                throw new IllegalArgumentException(
                    "investigatorName '" + name + "' does not name user " + userId + " ('"
                  + derived + "'). Send the id alone to assign a platform user, or the name alone "
                  + "to record an external or functional investigator.");
            c.setInvestigatorId(userId);
            c.setInvestigatorName(derived);
        } else if (name != null) {
            c.setInvestigatorId(null);
            c.setInvestigatorName(name);
        }
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private EthicsCase require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return caseRepo.findById(id)
            .filter(c -> c.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Case not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
