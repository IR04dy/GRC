package com.tss.grc.module.platform.controls.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlAssessment;
import com.tss.grc.module.platform.controls.entity.ControlMapping;
import com.tss.grc.module.platform.controls.entity.ControlObjective;
import com.tss.grc.module.platform.controls.entity.RiskControlLink;
import com.tss.grc.module.platform.controls.repository.ControlAssessmentRepository;
import com.tss.grc.module.platform.controls.repository.ControlMappingRepository;
import com.tss.grc.module.platform.controls.repository.ControlObjectiveRepository;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import com.tss.grc.module.platform.controls.repository.RiskControlLinkRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Unified Control Library service — F2.
 *
 * <p>Owns all five resources of the spine:
 * <ul>
 *   <li>{@link ControlObjective} — groups of controls.</li>
 *   <li>{@link Control} — the actual controls.</li>
 *   <li>{@link ControlMapping} — control ↔ citation cross-reference.</li>
 *   <li>{@link RiskControlLink} — risk-control matrix (review R1).</li>
 *   <li>{@link ControlAssessment} — periodic effectiveness tests.</li>
 * </ul>
 *
 * <p>See {@code docs/grc/framework-intersection-plan.md} §5.
 */
@Service
@RequiredArgsConstructor
public class ControlLibraryService {

    private static final Logger log = LoggerFactory.getLogger(ControlLibraryService.class);

    private static final Set<String> DOMAINS = Set.of(
        "RISK", "CYBER", "BCM", "PRIVACY", "TPRM", "COMPLIANCE", "GENERAL");
    private static final Set<String> CONTROL_TYPES = Set.of(
        "PREVENTIVE", "DETECTIVE", "CORRECTIVE", "DETERRENT", "RECOVERY");
    private static final Set<String> AUTOMATION_LEVELS = Set.of("MANUAL", "SEMI", "AUTOMATED");
    private static final Set<String> CONTROL_STATUSES = Set.of("DRAFT", "ACTIVE", "RETIRED");
    private static final Set<String> COVERAGES = Set.of("FULL", "PARTIAL", "COMPENSATING");
    private static final Set<String> LINK_TYPES = Set.of(
        "MITIGATES", "DETECTS", "TRANSFERS", "AVOIDS", "COMPENSATES");
    private static final Set<String> EFFECTIVENESS = Set.of(
        "EFFECTIVE", "NEEDS_IMPROVEMENT", "INEFFECTIVE", "NOT_TESTED");
    private static final Set<String> ASSESSMENT_STATUSES = Set.of(
        "DRAFT", "UNDER_REVIEW", "APPROVED", "REJECTED");

    private final ControlObjectiveRepository objectives;
    private final ControlRepository controls;
    private final ControlMappingRepository mappings;
    private final RiskControlLinkRepository riskControlLinks;
    private final ControlAssessmentRepository assessments;
    private final AuthorityDocumentCitationRepository citations;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── Control Objectives ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ControlObjective> listObjectives(String domain) {
        Long o = org();
        return (domain != null && !domain.isBlank())
            ? objectives.findByOrganisationIdAndDomainOrderByObjectiveCodeAsc(o, domain)
            : objectives.findByOrganisationIdOrderByDomainAscObjectiveCodeAsc(o);
    }

    @Transactional(readOnly = true)
    public ControlObjective getObjective(Long id) {
        return objectives.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Control objective not found: " + id));
    }

    @Transactional
    public ControlObjective createObjective(ControlObjective body) {
        if (body.getObjectiveCode() == null || body.getObjectiveCode().isBlank())
            throw new IllegalArgumentException("objectiveCode is required");
        if (body.getDomain() == null || !DOMAINS.contains(body.getDomain()))
            throw new IllegalArgumentException("domain must be one of " + DOMAINS);
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (objectives.findByOrganisationIdAndObjectiveCode(org(), body.getObjectiveCode()).isPresent())
            throw new IllegalArgumentException("Objective code already exists: " + body.getObjectiveCode());
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("ACTIVE");
        return objectives.save(body);
    }

    // ─── Controls ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Control> listControls(String domain, String status) {
        Long o = org();
        if (domain != null && !domain.isBlank())
            return controls.findByOrganisationIdAndDomainOrderByControlCodeAsc(o, domain);
        if (status != null && !status.isBlank())
            return controls.findByOrganisationIdAndStatusOrderByControlCodeAsc(o, status);
        return controls.findByOrganisationIdOrderByDomainAscControlCodeAsc(o);
    }

    @Transactional(readOnly = true)
    public List<Control> listControlsForObjective(Long objectiveId) {
        getObjective(objectiveId);   // tenant guard
        return controls.findByOrganisationIdAndObjectiveIdOrderByControlCodeAsc(org(), objectiveId);
    }

    @Transactional(readOnly = true)
    public Control getControl(Long id) {
        return controls.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Control not found: " + id));
    }

    @Transactional
    public Control createControl(Control body) {
        if (body.getControlCode() == null || body.getControlCode().isBlank())
            throw new IllegalArgumentException("controlCode is required");
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getDomain() == null || !DOMAINS.contains(body.getDomain()))
            throw new IllegalArgumentException("domain must be one of " + DOMAINS);
        if (body.getVersion() == null) body.setVersion("1.0");
        if (body.getControlType() == null) body.setControlType("PREVENTIVE");
        if (!CONTROL_TYPES.contains(body.getControlType()))
            throw new IllegalArgumentException("Invalid controlType: " + body.getControlType());
        if (body.getAutomationLevel() == null) body.setAutomationLevel("MANUAL");
        if (!AUTOMATION_LEVELS.contains(body.getAutomationLevel()))
            throw new IllegalArgumentException("Invalid automationLevel: " + body.getAutomationLevel());
        if (body.getStatus() == null) body.setStatus("ACTIVE");
        if (!CONTROL_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        if (controls.findByOrganisationIdAndControlCodeAndVersion(org(), body.getControlCode(), body.getVersion()).isPresent())
            throw new IllegalArgumentException("Control already exists: " + body.getControlCode() + " v" + body.getVersion());
        if (body.getObjectiveId() != null) getObjective(body.getObjectiveId()); // tenant guard
        body.setId(null);
        body.setOrganisationId(org());
        Control saved = controls.save(body);
        log.info("[controls] created {} v{} id={} domain={}",
            saved.getControlCode(), saved.getVersion(), saved.getId(), saved.getDomain());
        return saved;
    }

    @Transactional
    public Control updateControl(Long id, Control body) {
        Control c = getControl(id);
        if (body.getTitleEn() != null)        c.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)        c.setTitleAr(body.getTitleAr());
        if (body.getDescriptionEn() != null)  c.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr() != null)  c.setDescriptionAr(body.getDescriptionAr());
        if (body.getControlType() != null) {
            if (!CONTROL_TYPES.contains(body.getControlType()))
                throw new IllegalArgumentException("Invalid controlType: " + body.getControlType());
            c.setControlType(body.getControlType());
        }
        if (body.getAutomationLevel() != null) {
            if (!AUTOMATION_LEVELS.contains(body.getAutomationLevel()))
                throw new IllegalArgumentException("Invalid automationLevel: " + body.getAutomationLevel());
            c.setAutomationLevel(body.getAutomationLevel());
        }
        if (body.getFrequency() != null)      c.setFrequency(body.getFrequency());
        if (body.getOwnerUserId() != null)    c.setOwnerUserId(body.getOwnerUserId());
        if (body.getObjectiveId() != null) {
            getObjective(body.getObjectiveId()); // tenant guard
            c.setObjectiveId(body.getObjectiveId());
        }
        c.setUpdatedAt(LocalDateTime.now());
        return controls.save(c);
    }

    @Transactional
    public Control transitionControl(Long id, String to) {
        Control c = getControl(id);
        if (!CONTROL_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = c.getStatus();
        boolean ok = switch (from) {
            case "DRAFT"   -> "ACTIVE".equals(to);
            case "ACTIVE"  -> "RETIRED".equals(to);
            case "RETIRED" -> false;
            default        -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        c.setStatus(to);
        c.setUpdatedAt(LocalDateTime.now());
        return controls.save(c);
    }

    @Transactional
    public void deleteControl(Long id) {
        Control c = getControl(id);
        if ("ACTIVE".equals(c.getStatus()))
            throw new IllegalStateException("Cannot delete an ACTIVE control — transition to RETIRED first");
        c.setIsDeleted(true);
        controls.save(c);
    }

    // ─── Mappings ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ControlMapping> listMappingsForControl(Long controlId) {
        getControl(controlId);   // tenant guard
        return mappings.findByOrganisationIdAndControlIdOrderByIdAsc(org(), controlId);
    }

    @Transactional(readOnly = true)
    public List<ControlMapping> listMappingsForCitation(Long citationId) {
        Long o = org();
        citations.findByIdAndOrganisationId(citationId, o)
            .orElseThrow(() -> new EntityNotFoundException("Citation not found: " + citationId));
        return mappings.findByOrganisationIdAndCitationIdOrderByIdAsc(o, citationId);
    }

    @Transactional
    public ControlMapping createMapping(ControlMapping body) {
        if (body.getControlId() == null)  throw new IllegalArgumentException("controlId is required");
        if (body.getCitationId() == null) throw new IllegalArgumentException("citationId is required");
        Long o = org();
        getControl(body.getControlId());   // tenant guard
        citations.findByIdAndOrganisationId(body.getCitationId(), o)
            .orElseThrow(() -> new EntityNotFoundException("Citation not found: " + body.getCitationId()));
        if (mappings.findByControlIdAndCitationId(body.getControlId(), body.getCitationId()).isPresent())
            throw new IllegalArgumentException("Mapping already exists for this (control, citation) pair");
        if (body.getCoverage() == null) body.setCoverage("FULL");
        if (!COVERAGES.contains(body.getCoverage()))
            throw new IllegalArgumentException("coverage must be one of " + COVERAGES);
        if (body.getRationaleEn() == null || body.getRationaleEn().isBlank())
            throw new IllegalArgumentException("rationaleEn is required — discourages drive-by mapping");
        body.setId(null);
        body.setOrganisationId(o);
        body.setLastReviewedAt(LocalDateTime.now());
        return mappings.save(body);
    }

    @Transactional
    public ControlMapping updateMapping(Long id, ControlMapping body) {
        ControlMapping m = mappings.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Mapping not found: " + id));
        if (body.getCoverage() != null) {
            if (!COVERAGES.contains(body.getCoverage()))
                throw new IllegalArgumentException("coverage must be one of " + COVERAGES);
            m.setCoverage(body.getCoverage());
        }
        if (body.getRationaleEn() != null) m.setRationaleEn(body.getRationaleEn());
        if (body.getRationaleAr() != null) m.setRationaleAr(body.getRationaleAr());
        if (body.getReviewerUserId() != null) m.setReviewerUserId(body.getReviewerUserId());
        m.setLastReviewedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        return mappings.save(m);
    }

    @Transactional
    public void deleteMapping(Long id) {
        ControlMapping m = mappings.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Mapping not found: " + id));
        m.setIsDeleted(true);
        mappings.save(m);
    }

    /** For F6 (Compliance Posture Hub) — distinct authority doc ids covered. */
    @Transactional(readOnly = true)
    public List<Long> distinctMappedAuthorityDocumentIds() {
        return mappings.distinctMappedAuthorityDocumentIds(org());
    }

    // ─── Risk ↔ Control links ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RiskControlLink> listLinksForRisk(Long riskId) {
        return riskControlLinks.findByOrganisationIdAndRiskIdOrderByIdAsc(org(), riskId);
    }

    @Transactional(readOnly = true)
    public List<RiskControlLink> listLinksForControl(Long controlId) {
        getControl(controlId);   // tenant guard
        return riskControlLinks.findByOrganisationIdAndControlIdOrderByIdAsc(org(), controlId);
    }

    @Transactional
    public RiskControlLink linkRiskToControl(RiskControlLink body) {
        if (body.getRiskId() == null)    throw new IllegalArgumentException("riskId is required");
        if (body.getControlId() == null) throw new IllegalArgumentException("controlId is required");
        getControl(body.getControlId());   // tenant guard
        if (riskControlLinks.findByRiskIdAndControlId(body.getRiskId(), body.getControlId()).isPresent())
            throw new IllegalArgumentException("Risk-Control link already exists");
        if (body.getLinkType() == null) body.setLinkType("MITIGATES");
        if (!LINK_TYPES.contains(body.getLinkType()))
            throw new IllegalArgumentException("linkType must be one of " + LINK_TYPES);
        if (body.getEffectiveness() != null && !EFFECTIVENESS.contains(body.getEffectiveness()))
            throw new IllegalArgumentException("effectiveness must be one of " + EFFECTIVENESS);
        body.setId(null);
        body.setOrganisationId(org());
        return riskControlLinks.save(body);
    }

    @Transactional
    public RiskControlLink updateLink(Long id, RiskControlLink body) {
        RiskControlLink l = riskControlLinks.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Risk-control link not found: " + id));
        if (body.getLinkType() != null) {
            if (!LINK_TYPES.contains(body.getLinkType()))
                throw new IllegalArgumentException("linkType must be one of " + LINK_TYPES);
            l.setLinkType(body.getLinkType());
        }
        if (body.getEffectiveness() != null) {
            if (!EFFECTIVENESS.contains(body.getEffectiveness()))
                throw new IllegalArgumentException("effectiveness must be one of " + EFFECTIVENESS);
            l.setEffectiveness(body.getEffectiveness());
        }
        if (body.getRationaleEn() != null) l.setRationaleEn(body.getRationaleEn());
        if (body.getRationaleAr() != null) l.setRationaleAr(body.getRationaleAr());
        l.setLastReviewedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        return riskControlLinks.save(l);
    }

    @Transactional
    public void deleteLink(Long id) {
        RiskControlLink l = riskControlLinks.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Risk-control link not found: " + id));
        l.setIsDeleted(true);
        riskControlLinks.save(l);
    }

    // ─── Assessments ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ControlAssessment> listAssessmentsForControl(Long controlId) {
        getControl(controlId);   // tenant guard
        return assessments.findByOrganisationIdAndControlIdOrderByAssessmentDateDesc(org(), controlId);
    }

    @Transactional
    public ControlAssessment createAssessment(ControlAssessment body) {
        if (body.getControlId() == null) throw new IllegalArgumentException("controlId is required");
        Control c = getControl(body.getControlId());
        if (!"ACTIVE".equals(c.getStatus()))
            throw new IllegalStateException("Cannot assess a " + c.getStatus() + " control");
        if (body.getEffectiveness() == null) body.setEffectiveness("NOT_TESTED");
        if (!EFFECTIVENESS.contains(body.getEffectiveness()))
            throw new IllegalArgumentException("effectiveness must be one of " + EFFECTIVENESS);
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getWorkflowStatus() == null) body.setWorkflowStatus("DRAFT");
        return assessments.save(body);
    }

    @Transactional
    public ControlAssessment transitionAssessment(Long id, String to) {
        ControlAssessment a = assessments.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        if (!ASSESSMENT_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = a.getWorkflowStatus();
        boolean ok = switch (from) {
            case "DRAFT"        -> "UNDER_REVIEW".equals(to);
            case "UNDER_REVIEW" -> "APPROVED".equals(to) || "REJECTED".equals(to);
            case "REJECTED"     -> "DRAFT".equals(to);
            default             -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        a.setWorkflowStatus(to);
        a.setUpdatedAt(LocalDateTime.now());
        return assessments.save(a);
    }

    /** Helper: did this control get assessed within the last N days? */
    @Transactional(readOnly = true)
    public boolean hasRecentApprovedAssessment(Long controlId, int days) {
        List<ControlAssessment> list = assessments
            .findByOrganisationIdAndControlIdOrderByAssessmentDateDesc(org(), controlId);
        for (ControlAssessment a : list) {
            if ("APPROVED".equals(a.getWorkflowStatus())
                && a.getAssessmentDate() != null
                && a.getAssessmentDate().isAfter(java.time.LocalDate.now().minusDays(days)))
                return true;
        }
        return false;
    }

    /** Hide an Objects import if not used elsewhere — silence "unused" warnings. */
    @SuppressWarnings("unused")
    private void _touch() { Objects.requireNonNull(this); }
}
