package com.tss.grc.module.risk.scenario.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.CreateRequest;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.UpdateRequest;
import com.tss.grc.module.risk.scenario.entity.RiskScenario;
import com.tss.grc.module.risk.scenario.repository.RiskScenarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle service for {@link RiskScenario}.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT ──approve()──▶ APPROVED
 *         ──reject()──▶ REJECTED   (reason required)
 *         ──archive()─▶ ARCHIVED   (reason required)
 *
 *   APPROVED ──archive()─▶ ARCHIVED
 * </pre>
 *
 * <p>REJECTED is terminal; ARCHIVED is terminal. Editing is allowed only
 * while DRAFT — once APPROVED the model is locked for audit.
 */
@Service
@RequiredArgsConstructor
public class RiskScenarioService {

    private static final Set<String> ALLOWED_TYPES = Set.of("MONTE_CARLO", "STRESS_TEST", "WHAT_IF");

    private final RiskScenarioRepository repo;

    /**
     * The ERM register this scenario models. Intra-module (both live under
     * {@code module/risk}), so no ADR-002 edge is created — checked against
     * {@code ModuleGraphRatchetTest}, not assumed.
     *
     * <p>⚠ {@code module/ndi}'s {@code NdiCrossLinkService} is the same idea and could
     * not be reused: ADR-012/018 forbid a peer importing another module's internals,
     * which is precisely why every module that needs this hand-rolls it. Its javadoc
     * calls itself <i>"the single place where those soft links are validated against
     * their real targets"</i> — true of NDI, and the phrase is what made seven peers
     * writing the same column look covered (F2-215's two-of-N).
     */
    private final com.tss.grc.module.risk.repository.RiskRepository risks;

    @Autowired(required = false)
    private com.tss.grc.platform.realtime.BroadcastHelper broadcaster;

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RiskScenario> list(Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RiskScenario> listByStatus(String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(orgId, status, pageable);
    }

    @Transactional(readOnly = true)
    public RiskScenario get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new EntityNotFoundException("RiskScenario " + id));
    }

    @Transactional(readOnly = true)
    public List<RiskScenario> forRisk(Long riskId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(orgId, riskId);
    }

    // ─── Writes ───────────────────────────────────────────────────────

    /**
     * Bind a scenario to the ERM risk it models — the id, and the identity derived
     * <b>from that risk</b>.
     *
     * <p>⚠⚠ F2-245. This is the only writer of all three columns, and that is the fix.
     * {@code create} used to take {@code riskId}, {@code riskCode} and
     * {@code riskTitleEn} from the request: an id saying <i>which row</i> and two
     * strings saying <i>what it is called</i>, with nothing comparing them. Measured in
     * dev and prod, <b>11 of 11</b> live scenarios disagreed — scenario 1, <i>"Aramco
     * contract delay — Monte Carlo (base case)"</i>, stored
     * {@code risk_title_en = 'Aramco contract renewal delay'} against
     * {@code risk_id = 4}, which is <i>"Non-Compliance with NCA ECC Controls"</i>. Every
     * stored label was coherent, and none of them was the title of the risk it named.
     *
     * <p>🔑 The stored copy is what the register renders and the id is what
     * {@code /by-risk/{riskId}} joins on, so the two halves were read by different
     * surfaces and nothing ever put them side by side. The FAIR/Monte-Carlo analysis a
     * risk committee reads under one risk's name was, in the database, hanging off
     * another.
     *
     * <p>⚠ {@code risk_code} was worse than stale: it stored {@code RSK-2026-NNN} while
     * {@code risk.risks} has <b>no code column at all</b> — {@code external_id} is its
     * only reference and it is a UUID. So the register's risk column named a code space
     * that exists nowhere in the platform (F2-203's disjoint-code-space shape), and the
     * create form placeholdered it {@code 'RSK-2026-042'}, teaching the operator to
     * invent one.
     *
     * <p>Refusing an unresolvable id can break a request that succeeds today, and here
     * that is correct (F2-182's test): nothing coerces upstream, {@code risk_id} carries
     * no foreign key, and the caller is a present operator who can act on a 400 that
     * names the id — not the public-intake case where a lost record is worse than a
     * mis-graded one. A soft-deleted risk is refused too, because {@code Risk} carries
     * {@code @Where(is_deleted = 0)}: a new scenario may not be opened against a risk
     * the register has retired.
     *
     * <p>One message for <i>"no such risk"</i> and <i>"another tenant's risk"</i>,
     * copied from {@code CoiService#requireOwnUser} (F2-244), which records why:
     * distinguishing them lets a caller enumerate foreign ids from the error text.
     */
    private void applyParentRisk(RiskScenario s, Long riskId, Long orgId) {
        if (riskId == null) {
            // No parent. Both copies must be null — a risk label on a scenario with no
            // risk is the same false claim in a different shape (F2-209's three states:
            // a value, empty, and nothing to derive).
            s.setRiskId(null);
            s.setRiskCode(null);
            s.setRiskTitleEn(null);
            return;
        }
        com.tss.grc.module.risk.entity.Risk parent = risks.findById(riskId)
                .filter(r -> orgId.equals(r.getOrganisationId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Risk " + riskId + " is not a risk in this organisation, so a scenario "
                      + "cannot be modelled against it. A quantitative scenario whose parent "
                      + "risk resolves to nobody cannot be found from the risk register it is "
                      + "supposed to inform."));
        s.setRiskId(parent.getId());
        s.setRiskCode(parent.getExternalId() == null ? null : parent.getExternalId().toString());
        s.setRiskTitleEn(parent.getTitleEn());
    }

    @Transactional
    public RiskScenario create(CreateRequest req) {
        if (!ALLOWED_TYPES.contains(req.scenarioType())) {
            throw new IllegalArgumentException(
                    "Unknown scenarioType '" + req.scenarioType() + "'; expected one of " + ALLOWED_TYPES);
        }
        Long orgId = TenantContext.getCurrentTenant();
        RiskScenario s = new RiskScenario();
        s.setOrganisationId(orgId);
        s.setScenarioCode(generateScenarioCode());
        s.setExternalId(generateExternalId());

        applyParentRisk(s, req.riskId(), orgId);

        s.setTitleEn(req.titleEn());
        s.setTitleAr(req.titleAr());
        s.setDescriptionEn(req.descriptionEn());
        s.setDescriptionAr(req.descriptionAr());

        s.setScenarioType(req.scenarioType());
        s.setHorizonMonths(req.horizonMonths() != null ? req.horizonMonths() : 12);
        s.setCurrency(req.currency() != null ? req.currency() : "SAR");

        s.setInputJson(req.inputJson());
        s.setResultJson(req.resultJson());
        s.setExpectedLoss(req.expectedLoss());
        s.setP95Loss(req.p95Loss());
        s.setP99Loss(req.p99Loss());

        s.setAssumptionsEn(req.assumptionsEn());
        s.setAssumptionsAr(req.assumptionsAr());
        s.setMethodologyNotesEn(req.methodologyNotesEn());
        s.setMethodologyNotesAr(req.methodologyNotesAr());

        s.setStatus("DRAFT");
        RiskScenario saved = repo.save(s);
        broadcast("created", saved);
        return saved;
    }

    @Transactional
    public RiskScenario update(Long id, UpdateRequest req) {
        RiskScenario s = get(id);
        if (!"DRAFT".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "DRAFT", Set.of("DRAFT"));
        }
        if (req.titleEn() != null) s.setTitleEn(req.titleEn());
        if (req.titleAr() != null) s.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null) s.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null) s.setDescriptionAr(req.descriptionAr());
        if (req.inputJson() != null) s.setInputJson(req.inputJson());
        if (req.resultJson() != null) s.setResultJson(req.resultJson());
        if (req.expectedLoss() != null) s.setExpectedLoss(req.expectedLoss());
        if (req.p95Loss() != null) s.setP95Loss(req.p95Loss());
        if (req.p99Loss() != null) s.setP99Loss(req.p99Loss());
        if (req.assumptionsEn() != null) s.setAssumptionsEn(req.assumptionsEn());
        if (req.assumptionsAr() != null) s.setAssumptionsAr(req.assumptionsAr());
        if (req.methodologyNotesEn() != null) s.setMethodologyNotesEn(req.methodologyNotesEn());
        if (req.methodologyNotesAr() != null) s.setMethodologyNotesAr(req.methodologyNotesAr());
        if (req.horizonMonths() != null) s.setHorizonMonths(req.horizonMonths());
        if (req.currency() != null) s.setCurrency(req.currency());
        broadcast("updated", s);
        return s;
    }

    @Transactional
    public RiskScenario approve(Long id, String approverName, Long approverId) {
        RiskScenario s = get(id);
        if (!"DRAFT".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "APPROVED", Set.of("DRAFT"));
        }
        s.setStatus("APPROVED");
        s.setApprovedAt(LocalDateTime.now());
        s.setApprovedBy(approverId);
        s.setApproverName(approverName);
        broadcast("approved", s);
        return s;
    }

    @Transactional
    public RiskScenario reject(Long id, String reason) {
        RiskScenario s = get(id);
        if (!"DRAFT".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "REJECTED", Set.of("DRAFT"));
        }
        s.setStatus("REJECTED");
        s.setRejectionReason(reason);
        broadcast("rejected", s);
        return s;
    }

    @Transactional
    public RiskScenario archive(Long id, String reason, Long actorId) {
        RiskScenario s = get(id);
        if ("ARCHIVED".equals(s.getStatus()) || "REJECTED".equals(s.getStatus())) {
            throw new IllegalTransitionException(s.getStatus(), "ARCHIVED", Set.of("DRAFT", "APPROVED"));
        }
        s.setStatus("ARCHIVED");
        s.setArchivedAt(LocalDateTime.now());
        s.setArchivedBy(actorId);
        s.setArchiveReason(reason);
        broadcast("archived", s);
        return s;
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Mint a human-readable scenario code: SCN-{year}-{6 random hex}.
     * Collisions are vanishingly rare at this scale; we just retry once.
     */
    private String generateScenarioCode() {
        String code = "SCN-" + Year.now().getValue() + "-" + shortHex();
        Long orgId = TenantContext.getCurrentTenant();
        if (repo.findByOrganisationIdAndScenarioCode(orgId, code).isPresent()) {
            code = "SCN-" + Year.now().getValue() + "-" + shortHex();
        }
        return code;
    }

    private String generateExternalId() { return "SCN_" + UUID.randomUUID().toString().substring(0, 12); }

    private String shortHex() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private void broadcast(String event, RiskScenario s) {
        if (broadcaster != null) {
            broadcaster.entity(s.getOrganisationId(), "risk_scenario", s.getId(),
                    java.util.Map.of("event", event, "code", s.getScenarioCode(), "status", s.getStatus()));
        }
    }
}
