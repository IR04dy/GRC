package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.entity.Framework;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.compliance.repository.FrameworkRepository;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.export.ExportAuditContext;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.policy.entity.PolicyDocumentFrameworkMapping;
import com.tss.grc.module.policy.repository.PolicyDocumentFrameworkMappingRepository;
import com.tss.grc.module.reporting.dto.BoardPackAggregate;
import com.tss.grc.module.reporting.dto.BoardPackAggregate.Scope;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.sust.dashboard.service.SustainabilityDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * D-5 — the one place the board pack is computed.
 *
 * <p>{@link BoardPackService} (PDF), {@link BoardPackPptxService} (deck) and
 * {@code GET /api/v1/reporting/board-pack.json} all call {@link #build(Scope)} and then only
 * render. Before this class the first two each held their own copy of the whole computation and
 * were free to disagree; see {@link BoardPackAggregate} for what they already disagreed about.
 *
 * <h3>The rules this class keeps, which the duplicated versions could not</h3>
 * <ul>
 *   <li><b>One query per fact.</b> Where the two renderers used different queries for the same
 *       number, the cheaper correct one won: one {@code findLatestPerKriIn} instead of the deck's
 *       2N per-KRI finder, one {@code GROUP BY} instead of the deck's five status counts, and
 *       {@code COUNT} queries instead of 1,000-row pages read for {@code getTotalElements()}.</li>
 *   <li><b>Rank and limit in SQL</b> (F2-58). The top-risk list is ordered by the database with a
 *       total order — score descending, id ascending — so it is deterministic across runs. The
 *       deck's "top 10" is the first 10 of that list, which is exactly the top 10 <em>because</em>
 *       the order is total; with the old score-only sort it would not have been.</li>
 *   <li><b>Count tiers over the whole population</b>, not over a page of it.</li>
 * </ul>
 *
 * <h3>Volume, for the export audit trail</h3>
 * <p>{@link #build} deposits {@link BoardPackAggregate#rowCount()} in {@link ExportAuditContext},
 * so all three board-pack routes now report how many itemised rows left the building (F2-41).
 * Until D-5 the board pack — the most sensitive export the platform has — was the one that
 * reported {@code rowCountSource: UNINSTRUMENTED}, because instrumenting it would have meant
 * doing it twice in two renderers. Collapsing them made it a single line.
 */
@Service
@RequiredArgsConstructor
public class BoardPackAggregateService {

    /** The escalation tiers the pack renders, in days. Order matters: widest last. */
    private static final int TIER_EXEC = 90, TIER_DIRECTOR = 60, TIER_MANAGER = 30;

    /** Rows in the PDF's risk table. The deck renders the first 10 of the same ranked list. */
    private static final int TOP_RISKS = 20;

    /** Rows in the findings table. */
    private static final int OLDEST_FINDINGS = 15;

    /** Change statuses that count as in-flight — one list, previously written out twice. */
    private static final List<String> IN_FLIGHT_STATUSES = List.of(
        "IMPLEMENTING", "SCHEDULED", "CAB_PENDING", "IMPACT_ASSESSED", "EMERGENCY_APPROVED");

    private final OrganisationRepository orgRepo;
    private final RiskRepository riskRepo;
    private final AuditFindingRepository findingRepo;
    private final KriDefinitionRepository kriRepo;
    private final KriMeasurementRepository measRepo;
    private final FrameworkRepository frameworkRepo;
    private final PolicyDocumentFrameworkMappingRepository docMappingRepo;
    private final ControlRepository controlRepo;
    private final ChangeRequestRepository changeRepo;
    private final EthicsCaseRepository caseRepo;
    private final AmlAlertRepository amlRepo;
    private final SustainabilityDashboardService sustDashboard;

    /**
     * Build the pack for the calling tenant.
     *
     * @param scope which sections the caller renders — {@link Scope#DECK} skips the two expensive
     *              sections the deck does not show, so collapsing the renderers did not make the
     *              PPTX endpoint slower
     */
    @Transactional(readOnly = true)
    public BoardPackAggregate build(Scope scope) {
        Long org = TenantContext.getCurrentTenant();
        LocalDate today = LocalDate.now();

        BoardPackAggregate agg = new BoardPackAggregate(
            header(org, today),
            executive(org),
            topRisks(org),
            aging(org, today),
            oldestOpenFindings(org, today),
            kris(org),
            scope.includesFrameworks() ? frameworkCoverage(org) : null,
            changes(org),
            ethics(org),
            scope.includesSustainability() ? sustainability() : null
        );

        // F2-41 — the volume half of the export audit trail. Recorded here rather than in each
        // renderer so the PDF, the deck and the JSON all report the same number.
        ExportAuditContext.record(agg.rowCount(), sectionCount(scope), "BoardPackAggregateService");
        return agg;
    }

    private static int sectionCount(Scope scope) {
        return scope == Scope.FULL ? 9 : 7;
    }

    // ── Sections ───────────────────────────────────────────────────────────

    private BoardPackAggregate.Header header(Long org, LocalDate today) {
        Organisation o = orgRepo.findById(org).orElse(null);
        return new BoardPackAggregate.Header(
            org,
            o == null ? null : o.getNameEn(),
            o == null ? null : o.getNameAr(),
            LocalDateTime.now(),
            today);
    }

    private BoardPackAggregate.Executive executive(Long org) {
        return new BoardPackAggregate.Executive(
            riskRepo.countByOrganisationId(org),
            riskRepo.countByOrganisationIdAndRiskLevel(org, "CRITICAL"),
            riskRepo.countByOrganisationIdAndStatus(org, "OPEN"),
            findingRepo.countOpenByOrganisationId(org),
            findingRepo.countByOrganisationId(org),
            kriRepo.countByOrganisationIdAndIsDeletedFalse(org),
            controlRepo.countByOrganisationId(org),
            caseRepo.countByOrganisationId(org),
            amlRepo.countByOrganisationIdAndStrFiled(org, true),
            amlRepo.countByOrganisationId(org));
    }

    /**
     * F2-58 — ranked and limited by the database.
     *
     * <p>Was: a 200-row page with <b>no</b> {@code Sort}, sorted in memory and trimmed. The page
     * carried no {@code ORDER BY}, so "Top 20" was the top 20 of an arbitrary 200-row sample of the
     * register. The {@code id} tiebreaker makes the order total, which is what lets the deck take
     * the first 10 of this list and legitimately call them the top 10.
     */
    private List<BoardPackAggregate.RiskRow> topRisks(Long org) {
        List<Risk> risks = riskRepo.findByOrganisationId(org, PageRequest.of(0, TOP_RISKS,
            Sort.by(Sort.Direction.DESC, "inherentScore").and(Sort.by(Sort.Direction.ASC, "id"))))
            .getContent();

        List<BoardPackAggregate.RiskRow> out = new ArrayList<>(risks.size());
        for (Risk r : risks) {
            out.add(new BoardPackAggregate.RiskRow(
                r.getId(),
                "R-" + String.format("%05d", r.getId()),
                r.getTitleEn(),
                r.getTitleAr(),
                r.getInherentScore(),
                r.getResidualScore(),
                r.getStatus()));
        }
        return out;
    }

    /**
     * F2-58 — tiers counted in the database over every open finding, not bucketed from a page.
     *
     * <p>Three cumulative counts become four disjoint tiers by subtraction. Findings with no
     * {@code created_at} satisfy no {@code <} comparison and so land in the on-track remainder,
     * which is where the Java loop's {@code days = 0} put them.
     */
    private BoardPackAggregate.Aging aging(Long org, LocalDate today) {
        long totalOpen  = findingRepo.countOpenByOrganisationId(org);
        long exec       = findingRepo.countOpenByOrganisationIdOlderThan(org, cutoff(today, TIER_EXEC));
        long dirOrWorse = findingRepo.countOpenByOrganisationIdOlderThan(org, cutoff(today, TIER_DIRECTOR));
        long mgrOrWorse = findingRepo.countOpenByOrganisationIdOlderThan(org, cutoff(today, TIER_MANAGER));
        return new BoardPackAggregate.Aging(
            totalOpen,
            totalOpen - mgrOrWorse,      // on track
            mgrOrWorse - dirOrWorse,     // manager
            dirOrWorse - exec,           // director
            exec,                        // executive
            findingRepo.countByOrganisationIdAndStatus(org, "CLOSED"));
    }

    /**
     * F2-58 — the timestamp boundary that makes a SQL {@code createdAt < cutoff} count identical to
     * the Java loop it replaced, {@code ChronoUnit.DAYS.between(createdAt.toLocalDate(), today) >= days}.
     *
     * <p>That predicate holds iff the creation date is on or before {@code today - days}, which is
     * iff the timestamp precedes the start of day {@code today - (days - 1)}. The off-by-one
     * matters: {@code LocalDateTime.now().minusDays(90)} would drop a finding created 90 days ago
     * at 15:00 out of the Executive tier when the report runs at 10:00, while the loop counted it.
     *
     * <p>Pinned by {@code BoardPackAgingCutoffTest} across every age the tiers can distinguish.
     */
    static LocalDateTime cutoff(LocalDate today, int days) {
        return today.minusDays(days - 1L).atStartOfDay();
    }

    /** F2-58 — the 15 oldest OPEN findings, ordered and limited in SQL (closed excluded there too). */
    private List<BoardPackAggregate.FindingRow> oldestOpenFindings(Long org, LocalDate today) {
        List<AuditFinding> findings =
            findingRepo.findOldestOpenByOrganisationId(org, PageRequest.of(0, OLDEST_FINDINGS));

        List<BoardPackAggregate.FindingRow> out = new ArrayList<>(findings.size());
        for (AuditFinding f : findings) {
            LocalDateTime created = f.getCreatedAt();
            out.add(new BoardPackAggregate.FindingRow(
                f.getId(),
                f.getTitle(),
                f.getSeverity(),
                f.getOwner(),
                created == null ? null : ChronoUnit.DAYS.between(created.toLocalDate(), today),
                f.getStatus()));
        }
        return out;
    }

    /**
     * KRI bands plus the KRI table.
     *
     * <p>One {@code findLatestPerKriIn} for every KRI's newest reading. The deck used to call a
     * per-KRI finder <b>twice</b> per KRI — once to count bands, once to fill its table — so a
     * 40-KRI tenant issued 80 queries for what this does in one. The two finders were checked for
     * equivalence on live data before collapsing onto the cheaper one: the per-KRI version also
     * filtered {@code organisation_id}, and zero measurement rows carry an org that disagrees with
     * their KRI's org, so the predicate was redundant rather than load-bearing.
     */
    private BoardPackAggregate.Kris kris(Long org) {
        List<KriDefinition> kris = kriRepo.findByOrganisationIdAndIsDeletedFalseOrderByKriCodeAsc(org);

        // key present ⇒ a measurement exists; value [0] = status, [1] = value
        Map<Long, Object[]> latestByKri = new HashMap<>();
        List<Long> kriIds = kris.stream().map(KriDefinition::getId).toList();
        if (!kriIds.isEmpty()) {
            for (Object[] row : measRepo.findLatestPerKriIn(kriIds)) {
                latestByKri.put((Long) row[0], new Object[]{ row[1], row[2] });
            }
        }

        long green = 0, amber = 0, red = 0, noData = 0;
        List<BoardPackAggregate.KriRow> rows = new ArrayList<>(kris.size());
        for (KriDefinition k : kris) {
            Object[] latest = latestByKri.get(k.getId());
            String band = latest == null ? null : (String) latest[0];
            if (band == null) noData++;
            else if ("GREEN".equals(band)) green++;
            else if ("AMBER".equals(band)) amber++;
            else if ("RED".equals(band)) red++;

            rows.add(new BoardPackAggregate.KriRow(
                k.getId(),
                k.getKriCode(),
                k.getTitleEn(),
                k.getTitleAr(),
                band,
                latest == null ? null : (BigDecimal) latest[1],
                k.getGreenThreshold(),
                k.getAmberThreshold()));
        }
        return new BoardPackAggregate.Kris(green, amber, red, noData, rows);
    }

    /**
     * Per-framework coverage, for the frameworks that have any footprint in this tenant.
     *
     * <p>The zero-footprint filter is a data decision, not a layout one — a framework with no
     * policies, controls or findings tells a board nothing — so it lives here and every consumer
     * sees the same framework list.
     */
    private List<BoardPackAggregate.FrameworkRow> frameworkCoverage(Long org) {
        List<Framework> frameworks = frameworkRepo.findByIsActiveTrueOrderByFamilyAscCodeAsc();
        List<BoardPackAggregate.FrameworkRow> out = new ArrayList<>();
        for (Framework f : frameworks) {
            // CH-6: canonical mapping table, org-scoped by its own organisation_id.
            List<PolicyDocumentFrameworkMapping> mappings =
                docMappingRepo.findByOrganisationIdAndFrameworkId(org, f.getId());
            long full = 0;
            for (PolicyDocumentFrameworkMapping m : mappings) {
                if ("FULL".equals(m.getSatisfaction())) full++;
            }
            int policies = mappings.size();
            long controls = controlRepo.countByOrganisationIdAndFrameworkId(org, f.getId());
            long openFindings = findingRepo.countOpenByOrganisationIdAndFrameworkId(org, f.getId());

            if (policies == 0 && controls == 0 && openFindings == 0) continue;

            out.add(new BoardPackAggregate.FrameworkRow(
                f.getCode(),
                f.getNameEn(),
                f.getNameAr(),
                controls,
                policies,
                policies == 0 ? null : (int) Math.round(((double) full / policies) * 100),
                openFindings));
        }
        return out;
    }

    /** One {@code GROUP BY status} for in-flight, where the deck used five {@code COUNT}s. */
    private BoardPackAggregate.Changes changes(Long org) {
        Map<String, Long> byStatus = new HashMap<>();
        for (Object[] row : changeRepo.countByStatusGrouped(org)) {
            byStatus.put((String) row[0], (Long) row[1]);
        }
        long inFlight = 0;
        for (String s : IN_FLIGHT_STATUSES) inFlight += byStatus.getOrDefault(s, 0L);

        return new BoardPackAggregate.Changes(
            changeRepo.countByOrganisationId(org),
            inFlight,
            changeRepo.countByOrganisationIdAndOutcome(org, "SUCCESS"),
            changeRepo.countByOrganisationIdAndOutcome(org, "ROLLED_BACK"),
            changeRepo.countByOrganisationIdAndChangeType(org, "EMERGENCY"));
    }

    private BoardPackAggregate.Ethics ethics(Long org) {
        return new BoardPackAggregate.Ethics(
            caseRepo.countByOrganisationId(org),
            // F2-290 — was INTAKE + TRIAGE + INVESTIGATION, a three-token inclusion list, so this
            // slide and this PDF line reported 10 open misconduct cases where 26 were open (dev and
            // prod alike): SUBSTANTIATED, NO_VIOLATION and the 11 rows holding a status the machine
            // does not declare were all silently absent, and so were REMEDIATION and ESCALATED,
            // which analytics() at least counted. "Open" is the complement of the declared terminal
            // set, asked of the repository so no reader can hold its own idea of it (F2-196).
            caseRepo.countOpen(org),
            caseRepo.countByOrganisationIdAndStatus(org, "SUBSTANTIATED")
                + caseRepo.countByOrganisationIdAndStatus(org, "REMEDIATION"),
            caseRepo.countByOrganisationIdAndCategory(org, "BRIBERY")
                + caseRepo.countByOrganisationIdAndCategory(org, "KICKBACK"),
            caseRepo.countByOrganisationIdAndCategory(org, "AML_SUSPICION"),
            caseRepo.countByOrganisationIdAndCategory(org, "RETALIATION"),
            amlRepo.countByOrganisationIdAndStrFiled(org, true));
    }

    /**
     * S-N — the ESG snapshot, from the composed sustainability dashboard.
     *
     * <p>Wrapped defensively because a sustainability-data problem must never break the board pack;
     * {@code available=false} distinguishes "the subsystem did not answer" from "every metric is
     * null", which a board reading zeros could not otherwise tell apart.
     */
    private BoardPackAggregate.Sustainability sustainability() {
        Map<String, Object> d;
        boolean available = true;
        try {
            d = sustDashboard.dashboard((short) Year.now().getValue());
        } catch (RuntimeException ex) {
            d = Collections.emptyMap();
            available = false;
        }
        Map<String, Object> overall = asMap(d.get("overall"));
        Map<String, Object> ghg     = asMap(d.get("ghg"));
        Map<String, Object> cma     = asMap(d.get("cma"));
        Map<String, Object> tcfd    = asMap(d.get("tcfd"));
        Map<String, Object> byTier  = asMap(asMap(d.get("sgi")).get("byTier"));
        Map<String, Object> capa    = asMap(d.get("capa"));

        int onTrack = intOf(byTier.get("ON_TRACK"));
        int sgiTotal = onTrack + intOf(byTier.get("AT_RISK")) + intOf(byTier.get("DELAYED"));
        Object gaps = d.get("topGaps");

        return new BoardPackAggregate.Sustainability(
            available,
            dbl(overall.get("score")),
            str(overall.get("band")),
            dbl(ghg.get("grandTotal")),
            dbl(ghg.get("scope3Total")),
            dbl(cma.get("readinessPct")),
            dbl(tcfd.get("alignmentPct")),
            onTrack,
            sgiTotal,
            dbl(capa.get("open")),
            gaps instanceof List<?> l ? l.size() : 0);
    }

    // ── Untyped-map narrowing ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : Collections.emptyMap();
    }

    private static int intOf(Object o) { return (o instanceof Number n) ? n.intValue() : 0; }

    /** @return the value as a double, or {@code null} when absent or not a number */
    private static Double dbl(Object o) { return (o instanceof Number n) ? n.doubleValue() : null; }

    private static String str(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }
}
