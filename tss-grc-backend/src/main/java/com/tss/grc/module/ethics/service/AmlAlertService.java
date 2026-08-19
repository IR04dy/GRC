package com.tss.grc.module.ethics.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
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

    /** Package-visible so the derivation of {@link #INITIAL_STATUS} can be asserted against it. */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "OPEN",          Set.of("INVESTIGATING"),
        "INVESTIGATING", Set.of("CLEARED", "ESCALATED", "STR_FILED"),
        "CLEARED",       Set.of("CLOSED"),
        "ESCALATED",     Set.of("STR_FILED", "CLOSED"),
        "STR_FILED",     Set.of("CLOSED"),
        "CLOSED",        Set.of()
    );

    private final AmlAlertRepository repo;
    private final TenantUserDirectory directory;
    private final KycSubjectDirectory subjects;

    @Transactional(readOnly = true)
    public Page<AmlAlert> list(String status, String type, String severity, Pageable pageable) {
        return repo.findWithFilters(TenantContext.getCurrentTenant(),
            blank(status), blank(type), blank(severity), pageable);
    }

    @Transactional(readOnly = true)
    public AmlAlert get(Long id) { return require(id); }

    /**
     * The register's initial state, <b>derived</b> from the machine above: the only state with no
     * incoming edge. Typing {@code "OPEN"} a second time is how the value the server picks and the
     * value it accepts drift apart (F2-196/F2-217), so it is computed and self-checked instead.
     */
    static final String INITIAL_STATUS = TRANSITIONS.keySet().stream()
        .filter(s -> TRANSITIONS.values().stream().noneMatch(targets -> targets.contains(s)))
        .reduce((a, b) -> { throw new IllegalStateException(
            "the AML alert machine has more than one state with no incoming edge (" + a + ", " + b
          + "), so there is no single initial state to hold a create to"); })
        .orElseThrow(() -> new IllegalStateException(
            "the AML alert machine has no state with no incoming edge, so every state is "
          + "reachable and a create cannot be held to an initial one"));

    @Transactional
    public AmlAlert create(AmlAlert req) {
        AmlAlert a = new AmlAlert();
        a.setOrganisationId(TenantContext.getCurrentTenant());
        applyEditable(a, req);
        applySubject(a, req);
        // The machine owns the status and /assign owns the analyst, so a create that could name
        // either would be a transition nobody guarded (F2-217/F2-219). requireEcho against a
        // brand-new row's nulls is the same rule as on update — one helper, both paths.
        a.setStatus(ServerOwnedState.requireInitial(req.getStatus(), INITIAL_STATUS,
            "an AML alert", "POST /{id}/transition"));
        requireEcho("analystName", req.getAnalystName(), a.getAnalystName(), ASSIGN_ROUTE);
        if (a.getCurrency() == null) a.setCurrency("SAR");
        return repo.save(a);
    }

    /**
     * F2-250 — the entity-bound PUT may edit the alert's own facts and may not perform another
     * route's control.
     *
     * <p>Before this it called one blanket {@code apply()} that copied every column the caller
     * sent, so it could do three things the routes beside it refuse. Probed live as
     * {@code auditor} (COMPLIANCE:WRITE, the permission on all of these routes, so no escalation
     * — the object-level controls were simply the only ones and this route sat beside them):
     *
     * <ul>
     *   <li><b>It re-opened F2-249's fix.</b> {@code analystName} was writable and
     *       {@code analystId} was not, so a PUT could leave the id naming user 4 and change the
     *       label to somebody who was never assigned — <i>worse</i> than before F2-249, because
     *       now there is an id for the label to contradict.</li>
     *   <li><b>It bypassed the state machine.</b> {@code PUT {"status":"STR_FILED"}} filed a
     *       Suspicious Transaction Report with no disposition and no decider, while
     *       {@code /transition} answers 400 for the same edge (measured).</li>
     *   <li><b>It let the subject's two halves disagree</b> — see {@link #applySubject}.</li>
     * </ul>
     *
     * <p>🔑 The fix is F2-213's own prescription: <b>name the editable set</b>, so everything else
     * is safe by construction and a gate can check it. A scrub is a list of fields somebody
     * remembered; an allow-list is the set of fields that exist.
     */
    @Transactional
    public AmlAlert update(Long id, AmlAlert req) {
        AmlAlert a = require(id);
        applyEditable(a, req);
        applySubject(a, req);
        // An ECHO is accepted and a CHANGE is refused (F2-231/F2-234), so a form that round-trips
        // the whole row keeps working and an update cannot re-open what another route closed.
        requireEcho("status", req.getStatus(), a.getStatus(), TRANSITION_ROUTE);
        requireEcho("analystName", req.getAnalystName(), a.getAnalystName(), ASSIGN_ROUTE);
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
        // INITIAL_STATUS, not a literal: assigning an alert advances it out of the machine's root,
        // and a second spelling of that root is exactly what F2-217 warns drifts.
        if (INITIAL_STATUS.equals(a.getStatus())) a.setStatus("INVESTIGATING");
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

    private static final String ASSIGN_ROUTE =
        "POST /{id}/assign, which resolves the analyst in this tenant and derives the label";
    private static final String TRANSITION_ROUTE =
        "POST /{id}/transition, which checks the state machine and records the decision";

    /**
     * The alert's own facts — the set a caller owns, named exhaustively so that everything the
     * entity declares and this method omits is closed by construction.
     *
     * <p>⚙ Two members are recorded rather than defended, because closing them is a different
     * shape with its own gate and its own population to measure first:
     * <ul>
     *   <li>{@code alertCode} is the reference an FIU submission cites and <b>nothing mints it</b>
     *       — F2-172(d)'s family. Measured: all 10 live codes in dev and prod are
     *       {@code AML-2025-000n} written by the seed, there is no generator anywhere, and there is
     *       <b>no UNIQUE index</b> on the column, so a duplicate is storable (probed: a second
     *       {@code AML-2025-0001} was accepted). {@code ClientSuppliedReferenceRatchetTest} holds
     *       {@code setExternalId}/{@code setRefNumber} at zero and cannot see {@code setAlertCode}
     *       — the gate's key is a spelling (F2-248's headline). Filed.</li>
     *   <li>{@code slaDueAt} has no server computation anywhere: this line is its only writer, so
     *       the deadline is the caller's by design rather than by oversight. Filed against F2-202's
     *       rule that only the server may issue a deadline.</li>
     * </ul>
     */
    private void applyEditable(AmlAlert t, AmlAlert s) {
        if (s.getAlertCode()           != null) t.setAlertCode(s.getAlertCode());
        if (s.getAlertType()           != null) t.setAlertType(s.getAlertType());
        if (s.getSeverity()            != null) t.setSeverity(s.getSeverity());
        if (s.getRuleId()              != null) t.setRuleId(s.getRuleId());
        if (s.getRuleName()            != null) t.setRuleName(s.getRuleName());
        if (s.getTitleEn()             != null) t.setTitleEn(s.getTitleEn());
        if (s.getTitleAr()             != null) t.setTitleAr(s.getTitleAr());
        if (s.getDescription()         != null) t.setDescription(s.getDescription());
        if (s.getCounterpartyName()    != null) t.setCounterpartyName(s.getCounterpartyName());
        if (s.getCounterpartyCountry() != null) t.setCounterpartyCountry(s.getCounterpartyCountry());
        if (s.getTransactionCount()    != null) t.setTransactionCount(s.getTransactionCount());
        if (s.getAggregateAmount()     != null) t.setAggregateAmount(s.getAggregateAmount());
        if (s.getCurrency()            != null) t.setCurrency(s.getCurrency());
        if (s.getPeriodFrom()          != null) t.setPeriodFrom(s.getPeriodFrom());
        if (s.getPeriodTo()            != null) t.setPeriodTo(s.getPeriodTo());
        if (s.getSlaDueAt()            != null) t.setSlaDueAt(s.getSlaDueAt());
        if (s.getFrameworkId()         != null) t.setFrameworkId(s.getFrameworkId());
        if (s.getClauseRef()           != null) t.setClauseRef(s.getClauseRef());
    }

    /**
     * F2-250 — the alert's subject: one reference, and the register may not describe it twice.
     *
     * <p>{@code subject_id} and {@code subject_name} used to be two independent null-guarded
     * copies, so a caller could store an id naming one KYC subject beside a label naming another —
     * F2-245's travelling-label defect, live and probed. And {@code FK_aml_subject} does not help:
     * a foreign key refuses a <em>nonexistent</em> id and knows nothing about tenancy, so another
     * tenant's subject was a perfectly valid reference (F2-242/F2-243).
     *
     * <p>Measured in dev and prod, identically: 10 live alerts, 2 linked (both agreeing) and <b>8
     * carrying a label with no id — and all 8 labels match a real KYC subject exactly</b>. So the
     * register knew which of the 16 registered subjects each alert was about and could not join to
     * it. There is even an index for the join ({@code ix_aml_alerts_subject_id}) and no query that
     * performs one.
     *
     * <p>⚠ A label with no id stays legal, because the schema says so — nullable id beside a
     * nullable name (F2-246) — and because it is right: an alert can concern a party nobody has
     * onboarded, which is why {@code counterparty_name} exists with no id at all. What is closed is
     * the <em>appearance</em> of a link.
     *
     * <p>⚠ Editing only the label of an <b>already linked</b> alert is refused rather than silently
     * unlinking it. An entity binding cannot tell {@code subjectId: null} from an absent key, so
     * "unlink" is not expressible here; treating the label edit as an unlink would quietly undo a
     * resolved reference, which is the defect rather than the fix.
     */
    private void applySubject(AmlAlert t, AmlAlert s) {
        Long id = s.getSubjectId();
        String label = blank(s.getSubjectName());
        if (id == null && label == null) return;               // neither half stated

        if (id != null) {
            String derived = subjects.requireLabel(id, "an AML alert's subject");
            if (label != null && !label.equalsIgnoreCase(derived))
                throw new IllegalArgumentException(
                    "subjectName '" + label + "' does not name KYC subject " + id + " ('" + derived
                  + "'). Send subjectId alone and the register will name the subject, or send "
                  + "subjectName alone to record an alert about a party who is not in the KYC "
                  + "register.");
            t.setSubjectId(id);
            t.setSubjectName(derived);
            return;
        }

        if (t.getSubjectId() != null) {
            String stored = t.getSubjectName();
            if (stored != null && label.equalsIgnoreCase(stored)) return;   // an echo
            throw new IllegalArgumentException(
                "subjectName: this alert is linked to KYC subject " + t.getSubjectId()
              + (stored == null ? "" : " ('" + stored + "')")
              + ". Send subjectId with the subject you mean, or send subjectName unchanged. A "
              + "label on its own would leave the alert naming one party and linked to another.");
        }
        t.setSubjectName(label);
    }

    /**
     * A field another route owns: not stated is fine, an echo of what is stored is fine, anything
     * else is refused and told where to go (F2-231/F2-234).
     *
     * <p>⚙ The comparison is case-insensitive because the columns collate case-insensitively
     * (F2-179), and on an echo <b>nothing is written</b> — so unlike F2-249's assignment there is
     * no question of whose spelling wins.
     */
    private static void requireEcho(String field, String supplied, String stored, String route) {
        if (supplied == null || supplied.isBlank()) return;
        if (stored != null && supplied.equalsIgnoreCase(stored)) return;
        throw new IllegalArgumentException(
            field + ": this register's " + field + " is set by " + route + ". "
          + (stored == null
                ? "Nothing has set it on this record yet, so it cannot be supplied here."
                : "It currently reads '" + stored + "' — send that value unchanged, or omit the "
                + "field.")
          + " A different value here would change the record without passing the control that "
          + "owns it.");
    }

    private AmlAlert require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(a -> a.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("AML alert not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
