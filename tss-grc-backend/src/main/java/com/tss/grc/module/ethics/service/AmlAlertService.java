package com.tss.grc.module.ethics.service;

import com.tss.grc.common.reference.ReferenceCodeMint;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
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

    /**
     * The analyst's conclusion about an alert. F2-289.
     *
     * <p>Two tokens, and they are the two this service's own transition branches already default
     * to — not a vocabulary chosen here. {@code ethics.aml_alerts} had <b>zero</b> CHECK constraints
     * of any kind, so before V393 the column was narrowed by nothing at all and
     * {@code body.get("disposition").toString()} stored whatever arrived at three sites.
     *
     * <p>⚠ It is deliberately <b>not</b> reconciled with {@link KycService#HIT_DISPOSITION}, which
     * governs {@code ethics.screening_hits.disposition} over the same word. Those two registers hold
     * disjoint near-synonyms ({@code TRUE_POSITIVE} against {@code TRUE_MATCH}) and F2-288 filed
     * collapsing them as an owner decision, because it changes what the platform reports to the
     * Saudi FIU. Inventing a third, merged vocabulary here would be that defect (F2-196).
     */
    static final ClosedVocabulary DISPOSITION = ClosedVocabulary.of(
        "disposition", "CK_aml_alerts_disposition", "TRUE_POSITIVE", "FALSE_POSITIVE");

    /**
     * F2-289 — the alert's severity band, closed because {@link #analytics()} buckets by an
     * <b>inclusion list</b> of exactly these four tokens.
     *
     * <p>{@link #applyEditable} writes this column straight from an entity-bound PUT, so a caller
     * could store {@code SEVERE} and the alert would then be invisible to the {@code bySeverity}
     * KPI and carry no tone on the register (F2-197's shape). Every stored value in both databases
     * is already a member, so {@code require} cannot break a request that works today (F2-182).
     *
     * <p>⚙ {@code alert_type} is deliberately left open beside it: 10 distinct values are stored and
     * <b>no vocabulary for it is declared anywhere</b> — no reader's inclusion list, no dropdown, no
     * CHECK — so constraining it would mean inventing one (F2-183's rule about
     * {@code consumer.complaint.subcategory}, recorded as a decision rather than an absence).
     */
    static final ClosedVocabulary SEVERITY = ClosedVocabulary.of(
        "severity", "CK_aml_alerts_severity", "LOW", "MEDIUM", "HIGH", "CRITICAL");

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
        // F2-266 — the reference an FIU submission cites is the server's to issue
        // (F2-172(d)). Refused rather than silently dropped: a caller who sends one has to
        // learn the value is not theirs, and the minted code is on the response.
        requireEcho("alertCode", req.getAlertCode(), null, MINTED_BY_SERVER);
        a.setAlertCode(ReferenceCodeMint.mint("AML-" + LocalDate.now().getYear()));
        // F2-287 — the create is the ONLY path that may state this, because the column is
        // updatable = false. Deliberately not in applyEditable: that helper is shared with
        // update, where the write would compile, run and change nothing.
        a.setFirstDetectedAt(detectionTime(req.getFirstDetectedAt()));
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
        requireEcho("alertCode", req.getAlertCode(), a.getAlertCode(), MINTED_BY_SERVER);
        requireEcho("status", req.getStatus(), a.getStatus(), TRANSITION_ROUTE);
        requireEcho("analystName", req.getAnalystName(), a.getAnalystName(), ASSIGN_ROUTE);
        // F2-287 — the mapping refuses a change to this column, so the wire must not appear to
        // accept one. An echo is fine (a form that round-trips the row keeps working); a
        // different value is refused rather than silently dropped.
        requireEchoInstant("firstDetectedAt", req.getFirstDetectedAt(), a.getFirstDetectedAt());
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
            case "CLEARED"   -> recordDisposition(a, body, "FALSE_POSITIVE");
            case "ESCALATED" -> recordDisposition(a, body, "TRUE_POSITIVE");
            case "STR_FILED" -> {
                a.setStrFiled(true);
                a.setStrReference(body.get("strReference") != null ? body.get("strReference").toString() : null);
                a.setStrFiledAt(LocalDateTime.now());
                recordDisposition(a, body, "TRUE_POSITIVE");
            }
            case "CLOSED" -> { /* keep all decision metadata as-is */ }
            default -> { /* no-op */ }
        }
        a.setStatus(target);
        return repo.save(a);
    }

    /**
     * The <b>only</b> writer of an AML alert's decision tuple — disposition, notes, decider, clock.
     *
     * <p>F2-289. Before this, the reaction lived inline in the {@code switch} above and reached
     * <b>two of its three</b> decision branches: {@code CLEARED} and {@code STR_FILED} each stamped
     * {@code decidedByName} and {@code decidedAt}, and {@code ESCALATED} — which records a
     * {@code TRUE_POSITIVE} disposition, i.e. the analyst's conclusion that a monitored transaction
     * is genuinely suspicious — stamped neither. Since {@code ESCALATED → CLOSED} is a declared edge
     * and {@code CLOSED} keeps decision metadata as-is, an alert could be escalated as a confirmed
     * true positive and closed with <b>no decider recorded anywhere, permanently</b>. The Escalate
     * control is offered on the register for every {@code INVESTIGATING} alert, and three real
     * alerts sit in that state in both databases; 0 rows are {@code ESCALATED}, so this was latent
     * and reachable from the platform's own screen (F2-182 — reachability is a fact about today).
     *
     * <p>🔑🔑 <b>Why nobody saw it: the comment explaining the mechanism was byte-identical in the
     * two branches that HAD it, and the third sat between them.</b> Repetition reads as a rule, so a
     * reader scrolling the switch met the attribution twice and the gap once — and the duplicated
     * comment's own worked example was <i>"the STR filed with the Saudi FIU"</i>, i.e. one of the two
     * branches it appeared in. F2-215's two-of-N, sharpened: <b>a comment duplicated across the
     * branches that have a mechanism is stronger evidence of coverage than one stated once.</b>
     *
     * <p>🔑 And F2-223 is the slice that added the attribution — to two of the three branches. Same
     * shape as F2-287's headline: <b>the slice that establishes a rule is the worst-placed slice to
     * apply it to its own leftovers</b>, because the branch it did not touch is the one its new
     * comment makes look finished.
     *
     * <p>⚠ <b>All four columns move together, latest decision wins.</b> {@code STR_FILED} used to
     * preserve an existing {@code decidedAt} while overwriting {@code decidedByName}
     * unconditionally, which is F2-223's split waiting for a second decision: the name from
     * decision 2 beside the clock from decision 1. The only two-disposition path the machine allows
     * is {@code ESCALATED → STR_FILED}, and because {@code ESCALATED} wrote no clock the old
     * conditional always fired — so this is behaviour-identical on every reachable path today and
     * removes the split. Nothing is lost: {@code str_filed_at} records when the STR was filed.
     *
     * <p>⚠ {@code requireUsername}, never {@code username()}: this method stamps the clock from the
     * server, which is the service declaring that <b>it</b> is the recorder of the act, so a blank
     * attribution beside a real timestamp would read as an attribution (F2-236, whose javadoc states
     * exactly this rule).
     */
    private void recordDisposition(AmlAlert a, Map<String, Object> body, String fallback) {
        Object raw = body.get("disposition");
        a.setDisposition(raw != null ? DISPOSITION.require(raw.toString()) : fallback);
        a.setDispositionNotes(body.get("notes") != null ? body.get("notes").toString() : null);
        a.setDecidedByName(CurrentActor.requireUsername("an AML disposition"));
        a.setDecidedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total", repo.countByOrganisationId(org));

        // F2-289 — DERIVED from the machine and the vocabulary, never a second typed list. These
        // two loops were a third and fourth copy of vocabularies this file already declares, and an
        // inclusion list over a column nothing constrained is how F2-196's defect starts: a token
        // nobody remembered is silently absent from the KPI rather than chased.
        Map<String, Long> byStatus = new HashMap<>();
        for (String s : TRANSITIONS.keySet())
            byStatus.put(s, repo.countByOrganisationIdAndStatus(org, s));
        out.put("byStatus", byStatus);

        Map<String, Long> bySeverity = new HashMap<>();
        for (String s : SEVERITY.tokens())
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
    /**
     * F2-266 — {@code alertCode} has no owning ROUTE, because the server mints it.
     *
     * <p>{@link #requireEcho} takes a route name so its diagnostic can send the caller
     * somewhere; here the answer is "nowhere, it is not yours", so the string names the
     * mint instead of a path. A diagnostic that cites a route which does not own the field
     * is worse than none (F2-243 shipped one that named a path that did not exist).
     */
    private static final String MINTED_BY_SERVER =
        "the server, which mints it when the alert is created";

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
        if (s.getAlertType()           != null) t.setAlertType(s.getAlertType());
        // F2-289 — closed because analytics() buckets by an inclusion list of exactly these four
        // tokens, so a severity outside them is invisible to the KPI strip rather than merely odd.
        if (s.getSeverity()            != null) t.setSeverity(SEVERITY.require(s.getSeverity()));
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
            // F2-277 — resolve ONCE and derive both halves from that one row. requireLabel()
            // returned the English name alone, and asking a second method for the Arabic one
            // would have resolved the subject twice, so the two halves of a single label could
            // come from two reads.
            var subject = subjects.require(id, "an AML alert's subject");
            String derived = subjects.labelFor(subject);
            if (label != null && !label.equalsIgnoreCase(derived))
                throw new IllegalArgumentException(
                    "subjectName '" + label + "' does not name KYC subject " + id + " ('" + derived
                  + "'). Send subjectId alone and the register will name the subject, or send "
                  + "subjectName alone to record an alert about a party who is not in the KYC "
                  + "register.");
            t.setSubjectId(id);
            t.setSubjectName(derived);
            t.setSubjectNameAr(subjects.labelArFor(subject));
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
        // Unlinked: the label is the caller's, so there is no parent to derive an Arabic half
        // from. Cleared with it rather than left standing beside a name it no longer describes.
        t.setSubjectName(label);
        t.setSubjectNameAr(null);
    }

    /**
     * A field another route owns: not stated is fine, an echo of what is stored is fine, anything
     * else is refused and told where to go (F2-231/F2-234).
     *
     * <p>⚙ The comparison is case-insensitive because the columns collate case-insensitively
     * (F2-179), and on an echo <b>nothing is written</b> — so unlike F2-249's assignment there is
     * no question of whose spelling wins.
     */
    /**
     * F2-287 — the detection time the create records: the caller's if stated, the server's
     * clock if not.
     *
     * <p>The default is not tidiness. The column is NOT NULL and now insertable, so an omitted
     * value reaches the INSERT as null — supplying the clock here is what stops the widening
     * breaking the create outright.
     *
     * <p>An alert cannot have been detected in the future, and refusing that is what makes the
     * widening safe: it is the one value that could only ever be a forgery. Five minutes of
     * tolerance so a clock-skewed TM feed is not refused for being a second early.
     */
    private static LocalDateTime detectionTime(LocalDateTime supplied) {
        if (supplied == null) return LocalDateTime.now();
        if (supplied.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new IllegalArgumentException(
                "firstDetectedAt cannot be in the future — activity that has not happened yet "
              + "cannot have been detected. Omit the field to record the detection as now.");
        }
        return supplied;
    }

    /**
     * F2-287 — the {@link java.time.LocalDateTime} counterpart of {@link #requireEcho}, for a
     * column the mapping refuses to update. Compared exactly: a truncated value is a different
     * detection time, not the same one, and on an FIU clock that difference is the point.
     */
    private static void requireEchoInstant(String field, LocalDateTime supplied,
                                           LocalDateTime stored) {
        if (supplied == null || supplied.equals(stored)) return;
        throw new IllegalArgumentException(
            field + ": this register's " + field + " is recorded when the alert is created and "
          + "the column cannot be updated. It currently reads '" + stored + "' — send that "
          + "value unchanged, or omit the field. A different value here would look accepted "
          + "and change nothing.");
    }

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
