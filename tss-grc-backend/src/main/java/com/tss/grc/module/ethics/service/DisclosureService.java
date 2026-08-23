package com.tss.grc.module.ethics.service;

import com.tss.grc.common.security.CurrentActor;
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

    /**
     * The state a newly filed disclosure starts in.
     *
     * <p>⚠⚠ <b>F2-291 derived {@code EthicsCaseLifecycle.INITIAL} as "the one state that is never a
     * transition target", and that derivation CANNOT be copied here.</b> This machine has
     * {@code REJECTED → DRAFT}, so DRAFT <em>is</em> a target and the root set is <b>EMPTY</b> —
     * copying the derivation would have made every disclosure start nowhere, or thrown at class
     * initialisation. F2-290's rule, one register over: <b>copying a derivation copies its
     * convention</b>, and this is the case where the convention does not hold. So the initial state
     * is DECLARED, and the static block below asserts both halves of that reasoning so the next
     * reader can see the derivation was tried rather than overlooked.
     */
    private static final String INITIAL_STATUS = "DRAFT";

    /** The reporting currency a disclosure defaults to when the filer states none. */
    private static final String DEFAULT_CURRENCY = "SAR";

    static {
        if (!TRANSITIONS.containsKey(INITIAL_STATUS))
            throw new IllegalStateException(
                "INITIAL_STATUS " + INITIAL_STATUS + " is not a state this machine declares: "
                + new java.util.TreeSet<>(TRANSITIONS.keySet()));

        // The tripwire that tells a future reader to switch to F2-291's derivation: it fires the
        // moment this machine gains a state that is never a target, i.e. the moment the convention
        // `EthicsCaseLifecycle` relies on starts to hold here too.
        java.util.Set<String> targets = new java.util.TreeSet<>();
        TRANSITIONS.values().forEach(targets::addAll);
        java.util.Set<String> roots = new java.util.TreeSet<>(TRANSITIONS.keySet());
        roots.removeAll(targets);
        if (!roots.isEmpty())
            throw new IllegalStateException(
                "This machine now has a state that is never a transition target " + roots
                + ", so F2-291's derivation applies and INITIAL_STATUS should be DERIVED rather "
                + "than declared. Today REJECTED -> DRAFT makes the root set empty, which is why "
                + "it is a literal.");
    }

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
        requireStatusIsNotBodySet(null, req, true);
        apply(d, req);
        // F2-292 — the status is the machine's, never the caller's, and this line is now the only
        // writer of it on the create path. Before this, `apply` copied `req.getStatus()`, which was
        // never null because the entity initialised it, so the `if (d.getStatus() == null)` default
        // that stood here was DEAD CODE and a caller could file a disclosure already APPROVED —
        // skipping DRAFT -> SUBMITTED -> REVIEW and the transition route that is the only writer of
        // `decidedByName` / `decidedAt` (F2-220's shape, F2-223's attribution).
        // `NeverFiringNullGuardContractTest#everyRemovedInitialiserHasAWriterOnTheCreatePath` pins
        // this method as the writer for both fields, because `status` is NOT NULL and a create that
        // stops setting it turns a silent reset into a 500 that nothing else in the tree can see
        // under `ddl-auto=none`.
        d.setStatus(INITIAL_STATUS);
        if (d.getCurrency() == null) d.setCurrency(DEFAULT_CURRENCY);
        return repo.save(d);
    }

    @Transactional
    public Disclosure update(Long id, Disclosure req) {
        Disclosure d = require(id);
        requireStatusIsNotBodySet(d, req, false);
        apply(d, req);
        return repo.save(d);
    }

    /**
     * The status belongs to {@link #transition}, so an update may ECHO it and may not CHANGE it.
     *
     * <p>⚠⚠ <b>F2-292.</b> `DisclosureController` binds the entity on PUT, and `Disclosure` used to
     * initialise `status` to {@code "DRAFT"} — so an OMITTED status arrived as DRAFT, `apply`'s null
     * guard fired on every request, and a partial update reverted the disclosure. 18 of 18 live rows
     * in dev and prod hold a non-DRAFT status (APPROVED 11, REVIEW 4, SUBMITTED 2, REJECTED 1), so
     * every conflict-of-interest disclosure in the register was destroyable this way. APPROVED,
     * RECUSED and MITIGATED are TERMINAL in {@link #TRANSITIONS}, so the reset escaped a terminal
     * state by a route the machine cannot see.
     *
     * <p>⚠ <b>API-reachable, not screen-reachable</b>, and saying which is the point (F2-283).
     * {@code EthicsPage}'s form declares no status field, but {@code reqFromDto} spreads the whole
     * DTO into the edit modal, so the screen's PUT echoed the stored status and those 18 rows
     * survived. The reachable caller is anything sending a genuinely partial body — which is what
     * the client's own {@code Partial<DisclosureDto>} type invites (F2-179).
     *
     * <p>That same echo is why the fix TOLERATES one rather than refusing the field: refusing it
     * would break the screen that works today, which is the one failure a fix must never have
     * (F2-214). A CHANGE is refused with a 400 naming the route that performs it, because silently
     * ignoring it would leave the caller believing the status moved (F2-184's shape at a response).
     *
     * <p>Compared through {@link #normalise} rather than {@code equals}: this column is
     * {@code Latin1_General_100_CI_AS_SC_UTF8} and `ethics.disclosures` has <b>zero CHECK
     * constraints</b>, so {@code 'approved'} is storable and a bare equals would refuse an echo that
     * differs only in case (F2-179's family). Tolerating a case-variant echo and still refusing a
     * real change is the safe direction.
     */
    private void requireStatusIsNotBodySet(Disclosure current, Disclosure src, boolean isCreate) {
        if (src.getStatus() == null) return;
        String cur = isCreate ? INITIAL_STATUS : current.getStatus();
        if (normalise(src.getStatus()).equals(normalise(cur))) return;
        throw new IllegalArgumentException(
            (isCreate
                ? "A new disclosure starts at " + INITIAL_STATUS + " (received: " + src.getStatus()
                  + ")."
                : "Disclosure " + current.getId() + " is at " + cur + " and cannot be moved to "
                  + src.getStatus() + " by an update.")
            + " Status changes go through POST /api/v1/ethics/disclosures/"
            + (isCreate ? "{id}" : current.getId()) + "/transition, which enforces the chain and"
            + " stamps decision / decisionNotes / decidedByName / decidedAt."
            + (isCreate ? "" : " Allowed from " + cur + ": "
                + new java.util.TreeSet<>(TRANSITIONS.getOrDefault(cur, Set.of())) + "."));
    }

    /**
     * Case-insensitively normalise a stored status for comparison — the column collates CI and
     * carries no CHECK, so a bare equals is not a comparison (F2-179).
     */
    private static String normalise(String status) {
        return status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
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
            // F2-223 — see AmlAlertService#transition and KycService#disposition. The officer who
            // approves, rejects, recuses or mitigates a conflict-of-interest disclosure is the
            // authenticated principal; the clock on the next line already was.
            d.setDecidedByName(CurrentActor.username());
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
        // F2-292 — `status` is NOT copied here. It belongs to `transition`, which is the only route
        // that enforces the chain and stamps the decision, its notes, its decider and its clock.
        // See requireStatusIsNotBodySet for what the two callers enforce instead: an echo of the
        // current status is tolerated, a change is refused.
    }

    private Disclosure require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(d -> d.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Disclosure not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
