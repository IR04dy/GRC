package com.tss.grc.module.governance.threelines.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.module.audit_lc.service.FindingService;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.governance.threelines.dto.RoleMappingUpdateRequest;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesArtefactDto;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesOverviewDto;
import com.tss.grc.module.governance.threelines.dto.UserBandDto;
import com.tss.grc.module.governance.threelines.entity.ThreeLinesRoleMapping;
import com.tss.grc.module.governance.threelines.repository.ThreeLinesRoleMappingRepository;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Three Lines of Defense (3LoD) service — Sprint 11.
 *
 * <p>Three responsibilities:
 * <ol>
 *   <li><b>Aggregation</b> — counts risks / findings / CAPA / policies /
 *       DSRs grouped by LoD band for the current tenant. Powers the
 *       dashboard {@code overview} endpoint.</li>
 *   <li><b>User resolution</b> — given a user, return the LoD band the
 *       user belongs to (highest band wins for assurance independence).
 *       Used by both the frontend "my band" highlighter and the JWT claim
 *       builder.</li>
 *   <li><b>Mapping mutation</b> — admins can override the role→band
 *       mapping per tenant (e.g. small banks may merge COMPLIANCE_OFFICER
 *       and DPO into one LINE_2 role). Every mutation writes an F7 audit
 *       row + emits an F8 {@code LOD_ROLE_MAPPING_CHANGED} domain event.</li>
 * </ol>
 *
 * <p>All counts are tenant-scoped via {@link TenantContext}.
 */
@Service
@RequiredArgsConstructor
public class ThreeLinesService {

    private static final Logger log = LoggerFactory.getLogger(ThreeLinesService.class);

    public static final String LINE_1 = "LINE_1";
    public static final String LINE_2 = "LINE_2";
    public static final String LINE_3 = "LINE_3";
    public static final String BOARD  = "BOARD";

    /** IIA assurance-independence ordering: when a user holds roles in
     *  several bands, the highest one wins. Higher index = higher band. */
    private static final List<String> BAND_PRECEDENCE =
            List.of(LINE_1, LINE_2, LINE_3, BOARD);

    /**
     * Statuses that are NOT counted as open, per register.
     *
     * <p>F2-370: the CAPA and FINDING closed-sets were hand-typed copies of their registers' own
     * state machines. They now derive from the ONE declared terminal set each register owns —
     * {@link CapaTaskService#TERMINAL} ({@code capa.tasks}) and {@link FindingService#TERMINAL}
     * ({@code audit_lc.findings}) — so a new terminal state is reflected here automatically rather
     * than silently dropped (F2-196/F2-336). The {@code DSR_CLOSED_STATUSES} list was already dead
     * (F2-196 replaced its use with {@code dsrRepo.countOpen}, which derives the complement of the
     * DSR machine's terminal set) and is removed.
     *
     * <p>RISK stays hand-typed: {@code risk} has no declared {@code Map<state,edges>} machine (its
     * lifecycle is five hand-coded transition methods), so there is no single terminal set to
     * derive from. Filed rather than forced (F2-188).
     */
    private static final List<String> RISK_CLOSED_STATUSES = List.of("CLOSED", "CANCELLED");

    /** Finding severity that triggers Audit Committee / Board escalation. */
    private static final String CRITICAL = "CRITICAL";

    /** Saudi-native column subtitles — see CLAUDE.md memory
     *  {@code feedback_saudi_native_arabic.md}. */
    private static final Map<String, String[]> SAUDI_SUBTITLES = Map.of(
        LINE_1, new String[]{"Operations & risk owners",   "أصحاب المخاطر التشغيلية"},
        LINE_2, new String[]{"Risk, Compliance, DPO, CISO", "وظائف الإشراف"},
        LINE_3, new String[]{"Internal Audit",              "المراجعة الداخلية"},
        BOARD,  new String[]{"Audit Committee & Board",     "لجنة المراجعة ومجلس الإدارة"}
    );

    private static final Map<String, String[]> BAND_TITLES = Map.of(
        LINE_1, new String[]{"First Line of Defense",  "خط الدفاع الأول"},
        LINE_2, new String[]{"Second Line of Defense", "خط الدفاع الثاني"},
        LINE_3, new String[]{"Third Line of Defense",  "خط الدفاع الثالث"},
        BOARD,  new String[]{"Audit Committee / Board", "لجنة المراجعة / المجلس"}
    );

    private final ThreeLinesRoleMappingRepository mappingRepo;
    private final UserRepository                  userRepo;
    private final RiskRepository                  riskRepo;
    private final FindingRepository               findingRepo;
    private final CapaTaskRepository              capaRepo;
    private final PolicyDocumentRepository        policyRepo;
    private final DsrCaseRepository               dsrRepo;
    private final AuditLogService                 audit;
    private final ApplicationEventPublisher       events;

    // ─── Aggregation ────────────────────────────────────────────────

    /** Build the per-band counts for the dashboard. */
    @Transactional(readOnly = true)
    public ThreeLinesOverviewDto overview() {
        Long orgId = TenantContext.requireOrganisationId();

        // 1. Resolve role → band map for this tenant.
        Map<String, List<String>> rolesByBand = rolesByBand(orgId);

        // 2. Pull "open" artefacts once and bucket in memory. The
        //    registers are small (hundreds of rows in dev, thousands in
        //    prod) — pulling them once is cheaper than 4×N count queries.
        List<Risk>          openRisks    = openRisksFor(orgId);
        List<Finding>       openFindings = openFindingsFor(orgId);
        List<CapaTask>      openCapas    = openCapasFor(orgId);
        List<PolicyDocument> pendingPols = policyRepo.findByOrganisationIdAndStatusOrderByPolicyCodeAscVersionDesc(orgId, "REVIEW");
        long dsrOpen   = dsrOpenCount(orgId);

        LocalDate today = LocalDate.now();

        // 3. Bucketing rules (intentionally simple — every artefact has a
        //    canonical owning band; we project a per-band view of the same
        //    artefact set rather than try to "move" artefacts between
        //    bands. The IIA model says assurance flows up, not that
        //    artefacts physically belong to multiple lines.).
        //
        //    Risks       → LINE_1 (owned by ops). Counted under LINE_2 too
        //                  for the "oversight" view, but as a watch-count,
        //                  not double-counted in totals.
        //    Findings    → LINE_3 (raised by Internal Audit). CRITICAL
        //                  findings also escalate to BOARD.
        //    CAPA        → LINE_1 (executed by ops); shown as a "monitor"
        //                  metric for LINE_2.
        //    Policies pending approval → LINE_2 (set by oversight functions).
        //    DSR cases   → LINE_2 (DPO-owned per PDPL Art.32).
        long line1Risks    = openRisks.size();
        long line1Capa     = openCapas.size();
        long line1Overdue  = openCapas.stream()
                .filter(c -> c.getTargetDate() != null && c.getTargetDate().isBefore(today))
                .count();

        long line2Policies = pendingPols.size();
        long line2Dsr      = dsrOpen;

        long line3Findings = openFindings.size();
        long line3Critical = openFindings.stream()
                .filter(f -> CRITICAL.equalsIgnoreCase(f.getSeverity()))
                .count();

        long boardEscalations = line3Critical; // CRITICAL findings escalate.

        List<ThreeLinesOverviewDto.BandCounts> bands = List.of(
            band(LINE_1, rolesByBand.getOrDefault(LINE_1, List.of()),
                 line1Risks, 0, line1Capa, 0, 0, line1Overdue, 0),
            band(LINE_2, rolesByBand.getOrDefault(LINE_2, List.of()),
                 0, 0, 0, line2Policies, line2Dsr, 0, line2Policies),
            band(LINE_3, rolesByBand.getOrDefault(LINE_3, List.of()),
                 0, line3Findings, 0, 0, 0, 0, 0),
            band(BOARD, rolesByBand.getOrDefault(BOARD, List.of()),
                 0, 0, 0, 0, 0, 0, boardEscalations)
        );

        long totalOverdue = line1Overdue
            + openFindings.stream()
                .filter(f -> f.getTargetDate() != null && f.getTargetDate().isBefore(today))
                .count();

        return new ThreeLinesOverviewDto(
            bands,
            openRisks.size(),
            openFindings.size(),
            openCapas.size(),
            pendingPols.size(),
            totalOverdue
        );
    }

    private ThreeLinesOverviewDto.BandCounts band(String code, List<String> roleNames,
                                                  long risks, long findings, long capa,
                                                  long policies, long dsr, long overdue,
                                                  long approvals) {
        String[] title    = BAND_TITLES.get(code);
        String[] subtitle = SAUDI_SUBTITLES.get(code);
        return new ThreeLinesOverviewDto.BandCounts(
            code,
            title[0], title[1],
            subtitle[0], subtitle[1],
            risks, findings, capa, policies, dsr, overdue, approvals,
            roleNames
        );
    }

    // ─── Per-band drill-down ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ThreeLinesArtefactDto> drillDown(String band) {
        Long orgId = TenantContext.requireOrganisationId();
        String b = validateBand(band);
        LocalDate today = LocalDate.now();

        List<ThreeLinesArtefactDto> rows = new ArrayList<>();
        switch (b) {
            case LINE_1 -> {
                for (Risk r : openRisksFor(orgId)) {
                    String ext = r.getExternalId() == null ? null : r.getExternalId().toString();
                    rows.add(new ThreeLinesArtefactDto(
                        "RISK", r.getId(), ext,
                        r.getTitleEn(), r.getTitleAr(),
                        r.getRiskLevel(), r.getStatus(),
                        r.getOwnerId() == null ? "" : ("user#" + r.getOwnerId()),
                        r.getReviewDate(),
                        slaStatus(r.getReviewDate(), today),
                        false
                    ));
                }
                for (CapaTask c : openCapasFor(orgId)) {
                    rows.add(new ThreeLinesArtefactDto(
                        "CAPA", c.getId(), c.getExternalId(),
                        c.getTitleEn(), c.getTitleAr(),
                        c.getSeverity(), c.getStatus(),
                        c.getOwner(), c.getTargetDate(),
                        slaStatus(c.getTargetDate(), today),
                        false
                    ));
                }
            }
            case LINE_2 -> {
                List<PolicyDocument> pending = policyRepo.findByOrganisationIdAndStatusOrderByPolicyCodeAscVersionDesc(orgId, "REVIEW");
                for (PolicyDocument p : pending) {
                    rows.add(new ThreeLinesArtefactDto(
                        "POLICY", p.getId(), p.getExternalId(),
                        p.getTitleEn(), p.getTitleAr(),
                        null, p.getStatus(),
                        p.getApproverName() != null ? p.getApproverName() : p.getOwnerName(),
                        p.getNextReviewDate() != null ? p.getNextReviewDate() : p.getExpiryDate(),
                        slaStatus(p.getNextReviewDate() != null ? p.getNextReviewDate() : p.getExpiryDate(), today),
                        false
                    ));
                }
            }
            case LINE_3 -> {
                for (Finding f : openFindingsFor(orgId)) {
                    boolean escalated = CRITICAL.equalsIgnoreCase(f.getSeverity());
                    rows.add(new ThreeLinesArtefactDto(
                        "FINDING", f.getId(), f.getExternalId(),
                        f.getTitleEn(), f.getTitleAr(),
                        f.getSeverity(), f.getStatus(),
                        f.getOwnerRole() == null ? "" : f.getOwnerRole(),
                        f.getTargetDate(),
                        slaStatus(f.getTargetDate(), today),
                        escalated
                    ));
                }
            }
            case BOARD -> {
                // BOARD-band view = the CRITICAL escalations from Line 3.
                for (Finding f : openFindingsFor(orgId)) {
                    if (!CRITICAL.equalsIgnoreCase(f.getSeverity())) continue;
                    rows.add(new ThreeLinesArtefactDto(
                        "FINDING", f.getId(), f.getExternalId(),
                        f.getTitleEn(), f.getTitleAr(),
                        f.getSeverity(), f.getStatus(),
                        f.getOwnerRole() == null ? "" : f.getOwnerRole(),
                        f.getTargetDate(),
                        slaStatus(f.getTargetDate(), today),
                        true
                    ));
                }
            }
            default -> throw new IllegalArgumentException("Unknown band: " + band);
        }
        return rows;
    }

    // ─── User → band lookup ─────────────────────────────────────────

    /**
     * Effective LoD band for a user — highest band wins.
     *
     * @return the band code, or {@code null} when the user has no roles
     *         that map to any band. Never {@code null} for the seeded
     *         users in dev (admin/riskmanager/auditor).
     */
    @Transactional(readOnly = true)
    public String bandForUser(Long userId) {
        if (userId == null) return null;
        User u = userRepo.findById(userId).orElse(null);
        if (u == null || u.getRoles() == null || u.getRoles().isEmpty()) return null;
        return effectiveBand(bandsForRoles(u.getOrganisationId(), roleNamesOf(u)));
    }

    /** All bands a user touches — multiple when the user has roles in
     *  several bands. Useful for the frontend "my bands" highlighter. */
    @Transactional(readOnly = true)
    public List<String> bandsForUser(Long userId) {
        if (userId == null) return List.of();
        User u = userRepo.findById(userId).orElse(null);
        if (u == null) return List.of();
        return new ArrayList<>(bandsForRoles(u.getOrganisationId(), roleNamesOf(u)));
    }

    @Transactional(readOnly = true)
    public UserBandDto userBandFor(Long userId) {
        if (userId == null) throw new EntityNotFoundException("user id required");
        User u = userRepo.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        List<String> roleNames = roleNamesOf(u);
        Set<String> all = bandsForRoles(u.getOrganisationId(), roleNames);
        String effective = effectiveBand(all);
        return new UserBandDto(
            u.getId(),
            u.getUsername(),
            roleNames,
            new ArrayList<>(all),
            effective
        );
    }

    // ─── Mapping mutation (F7 + F8) ─────────────────────────────────

    @Transactional
    public ThreeLinesRoleMapping mapRoleToBand(RoleMappingUpdateRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        String roleName = req.roleName();
        String newBand  = req.lodBand();

        // Reject unknown bands (defence in depth — DTO has @Pattern but
        // service must not trust its caller).
        validateBand(newBand);

        Optional<ThreeLinesRoleMapping> existing =
            mappingRepo.findByOrganisationIdAndRoleName(orgId, roleName);

        String oldBand;
        ThreeLinesRoleMapping m;
        if (existing.isPresent()) {
            m = existing.get();
            oldBand = m.getLodBand();
            m.setLodBand(newBand);
            if (req.titleEn() != null)  m.setTitleEn(req.titleEn());
            if (req.titleAr() != null)  m.setTitleAr(req.titleAr());
            if (req.rationaleEn() != null) m.setRationaleEn(req.rationaleEn());
            if (req.rationaleAr() != null) m.setRationaleAr(req.rationaleAr());
        } else {
            // Promote/copy a system-default into a tenant-specific row.
            ThreeLinesRoleMapping def = mappingRepo.findByOrganisationIdIsNullAndRoleName(roleName)
                .orElse(null);
            oldBand = def == null ? null : def.getLodBand();
            m = new ThreeLinesRoleMapping();
            m.setOrganisationId(orgId);
            m.setRoleName(roleName);
            m.setLodBand(newBand);
            m.setTitleEn(coalesce(req.titleEn(),  def == null ? roleName : def.getTitleEn()));
            m.setTitleAr(coalesce(req.titleAr(),  def == null ? roleName : def.getTitleAr()));
            m.setRationaleEn(coalesce(req.rationaleEn(), def == null ? null : def.getRationaleEn()));
            m.setRationaleAr(coalesce(req.rationaleAr(), def == null ? null : def.getRationaleAr()));
        }
        ThreeLinesRoleMapping saved = mappingRepo.save(m);

        // F7 — audit log row. Captures the SAMA CSF 1.1.1 / NCA ECC-2 1-1
        // "who changed responsibility allocation" answer.
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType("LOD_ROLE_MAPPING_CHANGED")
            .entityType("ThreeLinesRoleMapping")
            .entityId(saved.getId())
            .entityCode(saved.getRoleName())
            .workflowStatusBefore(oldBand)
            .workflowStatusAfter(newBand)
            .sourceModule("GOVERNANCE")
            .severity("INFO")
            .message("LoD band for role " + roleName + " changed "
                  + (oldBand == null ? "(none)" : oldBand) + " → " + newBand
                  + " — NCA ECC-2 1-1 / SAMA CSF 1.1.1 governance allocation."));

        // F8 — domain event for automation rules + downstream listeners.
        events.publishEvent(DomainEvent.of(
            orgId,
            "LOD_ROLE_MAPPING_CHANGED",
            "ThreeLinesRoleMapping",
            saved.getId(),
            Map.of(
                "role_name", roleName,
                "old_band", oldBand == null ? "" : oldBand,
                "new_band", newBand
            )
        ));

        log.info("[3lod] org={} role={} band: {} → {}", orgId, roleName, oldBand, newBand);
        return saved;
    }

    // ─── Internal helpers ───────────────────────────────────────────

    /** Returns the role → band map effective for this tenant (per-tenant
     *  rows override system defaults). */
    private Map<String, String> roleBandMapFor(Long orgId) {
        Map<String, String> out = new HashMap<>();
        for (ThreeLinesRoleMapping m : mappingRepo.findEffectiveForOrg(orgId)) {
            out.put(m.getRoleName(), m.getLodBand());
        }
        return out;
    }

    private Map<String, List<String>> rolesByBand(Long orgId) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put(LINE_1, new ArrayList<>());
        out.put(LINE_2, new ArrayList<>());
        out.put(LINE_3, new ArrayList<>());
        out.put(BOARD,  new ArrayList<>());
        roleBandMapFor(orgId).forEach((role, band) ->
            out.computeIfAbsent(band, k -> new ArrayList<>()).add(role));
        return out;
    }

    private Set<String> bandsForRoles(Long orgId, List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return Set.of();
        Map<String, String> map = roleBandMapFor(orgId);
        Set<String> bands = new LinkedHashSet<>();
        for (String r : roleNames) {
            String band = map.get(r);
            if (band != null) bands.add(band);
        }
        return bands;
    }

    /** Highest band wins (LINE_3 &gt; LINE_2 &gt; LINE_1; BOARD treated
     *  as the top of the precedence stack — an Audit Committee Chair
     *  outranks Internal Audit for dashboard membership). */
    public static String effectiveBand(Collection<String> bands) {
        if (bands == null || bands.isEmpty()) return null;
        // BOARD outranks LINE_3 outranks LINE_2 outranks LINE_1.
        if (bands.contains(BOARD))  return BOARD;
        if (bands.contains(LINE_3)) return LINE_3;
        if (bands.contains(LINE_2)) return LINE_2;
        if (bands.contains(LINE_1)) return LINE_1;
        // Unknown band — return the first one we don't recognise so the
        // caller can surface it rather than silently dropping.
        return bands.iterator().next();
    }

    private static String validateBand(String band) {
        if (band == null || !BAND_PRECEDENCE.contains(band))
            throw new IllegalArgumentException("Unknown LoD band: " + band);
        return band;
    }

    private static List<String> roleNamesOf(User u) {
        if (u.getRoles() == null) return List.of();
        List<String> out = new ArrayList<>(u.getRoles().size());
        for (Role r : u.getRoles()) out.add(r.getRoleName());
        return out;
    }

    private static String coalesce(String a, String b) { return a != null ? a : b; }

    // ─── Cheap data accessors (re-used across overview + drill-down) ─

    private List<Risk> openRisksFor(Long orgId) {
        // No "status NOT IN" finder on Risk — pull and filter in memory.
        //
        // F2-422: this read `Pageable.ofSize(1000)` under a comment saying it paged "with a
        // generous size so the dashboard sees the whole open set" and that "the alternative
        // is a native query". The first half stated the intent the code did not deliver -- an
        // unsorted cap keeps whichever 1000 rows the plan yields -- and the second half was
        // false: openFindingsFor and openCapasFor, the two methods directly below, both use an
        // UNCAPPED derived finder. The alternative was the idiom already in use three lines
        // down, so this was the lone outlier of three sibling accessors.
        return riskRepo.findByOrganisationId(orgId).stream()
            .filter(r -> !RISK_CLOSED_STATUSES.contains(r.getStatus()))
            .toList();
    }

    private List<Finding> openFindingsFor(Long orgId) {
        return findingRepo.findByOrganisationIdOrderByRaisedAtDesc(orgId).stream()
            .filter(f -> !FindingService.TERMINAL.contains(f.getStatus()))
            .toList();
    }

    private List<CapaTask> openCapasFor(Long orgId) {
        return capaRepo.findByOrganisationIdOrderByCreatedAtDesc(orgId).stream()
            .filter(c -> !CapaTaskService.TERMINAL.contains(c.getStatus()))
            .toList();
    }

    private long dsrOpenCount(Long orgId) {
        // F2-196: this summed an INCLUSION list of five open states. It happened to be
        // complete for today's seven-token vocabulary, but an inclusion list over an open
        // column silently drops any state nobody remembered to add — and the sibling sweep
        // in platform/completion had made exactly that class of error in the other
        // direction. Ask for the complement of the declared terminal set instead, so a new
        // lifecycle state is counted as open (and chased) rather than quietly excused.
        return dsrRepo.countOpen(orgId);
    }

    private static String slaStatus(LocalDate due, LocalDate today) {
        if (due == null) return "ON_TRACK";
        if (due.isBefore(today)) return "OVERDUE";
        if (!due.isAfter(today.plusDays(7))) return "DUE_SOON";
        return "ON_TRACK";
    }
}
