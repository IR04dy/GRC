package com.tss.grc.module.privacy.bridge.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.bridge.entity.CyberBreachBridgeLog;
import com.tss.grc.module.privacy.bridge.repository.CyberBreachBridgeLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cyber → PDPL breach bridge (gap-analysis item A).
 *
 * <p>Read API for the bridge_log table + a manual-trigger entry point.
 * The classification of a cyber incident as PII-affecting writes:
 * <ul>
 *   <li>UPDATE cyber.incidents — sets affects_personal_data + classification metadata</li>
 *   <li>INSERT privacy.data_breaches — auto-generated row linked back to the incident</li>
 *   <li>INSERT privacy.cyber_breach_bridge_log — BRIDGE_FIRED + BREACH_CREATED entries</li>
 * </ul>
 *
 * <p>We use native SQL for the cross-table writes rather than depending on the
 * existing PrivacyService.createBreach() to keep the bridge as a thin
 * standalone module that doesn't require modifying upstream cyber/privacy
 * services.
 */
@Service
@RequiredArgsConstructor
public class CyberPdplBridgeService {

    private static final String RULE_NAME = "CYBER_TO_PDPL_V1";

    private final CyberBreachBridgeLogRepository logRepo;

    @PersistenceContext private EntityManager em;

    @Transactional(readOnly = true)
    public Page<CyberBreachBridgeLog> list(String outcome, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        return outcome == null
                ? logRepo.findByOrganisationIdOrderByTriggeredAtDesc(org, pageable)
                : logRepo.findByOrganisationIdAndOutcomeOrderByTriggeredAtDesc(org, outcome, pageable);
    }

    @Transactional(readOnly = true)
    public List<CyberBreachBridgeLog> forIncident(Long cyberIncidentId) {
        return logRepo.findByOrganisationIdAndCyberIncidentIdOrderByTriggeredAtDesc(
                TenantContext.getCurrentTenant(), cyberIncidentId);
    }

    /**
     * Manually trigger the bridge rule for a cyber incident. Used by the UI
     * "Classify as PII-affecting" button + (later) an event listener on
     * CyberIncident save when an automatic classification field flips.
     */
    @Transactional
    public CyberBreachBridgeLog triggerForIncident(Long cyberIncidentId,
                                                   Integer dataSubjectsAffectedEst,
                                                   String dataCategoriesAffected,
                                                   String piiAssessment,
                                                   Long actorUserId,
                                                   String actorUsername) {
        Long org = TenantContext.getCurrentTenant();

        // Load incident; also see if it's already linked.
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT id, title, linked_data_breach_id FROM cyber.incidents " +
                "WHERE id = :id AND organisation_id = :org AND is_deleted = 0")
                .setParameter("id", cyberIncidentId).setParameter("org", org)
                .getSingleResult();
        Long id          = ((Number) row[0]).longValue();
        String title     = (String) row[1];
        Long existingFk  = row[2] == null ? null : ((Number) row[2]).longValue();
        String incNumber = "INC-" + String.format(Locale.ROOT, "%05d", id);

        if (existingFk != null) {
            // Already linked — record SKIPPED and return.
            return writeLog(org, "BRIDGE_SKIPPED", id, incNumber,
                    existingFk, null,
                    "Re-evaluation requested but incident already linked to breach " + existingFk,
                    "SKIPPED",
                    "DataBreach already exists for this cyber incident; no action taken.",
                    actorUserId, actorUsername);
        }

        // 1) update cyber.incidents — classify
        em.createNativeQuery(
                "UPDATE cyber.incidents " +
                "   SET affects_personal_data        = 1, " +
                "       data_subjects_affected_est   = :n, " +
                "       data_categories_affected     = :cats, " +
                "       personal_data_pii_assessment = :assess, " +
                "       pii_classified_at            = SYSUTCDATETIME(), " +
                "       pii_classified_by            = :uid " +
                " WHERE id = :id")
                .setParameter("n", dataSubjectsAffectedEst)
                .setParameter("cats", dataCategoriesAffected)
                .setParameter("assess", piiAssessment)
                .setParameter("uid", actorUserId)
                .setParameter("id", id)
                .executeUpdate();

        // 2) insert privacy.data_breaches — auto-generated
        UUID extId = UUID.randomUUID();
        String breachTitle = "Auto-generated breach from cyber incident " + incNumber + " — " + title;
        String breachDesc = "Cyber → PDPL bridge rule " + RULE_NAME +
                " created this breach automatically when cyber incident " + incNumber +
                " was classified as affecting personal data. " +
                (dataSubjectsAffectedEst == null ? "" : dataSubjectsAffectedEst + " data subjects estimated. ") +
                "See linked cyber incident for full IR detail.";
        String breachType = dataCategoriesAffected != null && dataCategoriesAffected.contains("NATIONAL_ID")
                ? "UNAUTHORIZED_ACCESS" : "ACCIDENTAL_DISCLOSURE";
        boolean pdpcNotifyRequired = (dataSubjectsAffectedEst != null && dataSubjectsAffectedEst >= 500)
                || (dataCategoriesAffected != null && dataCategoriesAffected.contains("NATIONAL_ID"));

        em.createNativeQuery(
                "INSERT INTO privacy.data_breaches " +
                "  (organisation_id, external_id, title_en, description_en, " +
                "   breach_type, discovered_at, data_subjects_affected_count, " +
                "   pdpc_notification_required, status, " +
                "   source_cyber_incident_id, source_cyber_incident_number, " +
                "   auto_generated, auto_generation_rule, auto_generated_at, " +
                "   is_deleted, created_at) " +
                "VALUES (:org, :ext, :title, :desc, :btype, SYSUTCDATETIME(), :n, " +
                "        :notify, :status, :incid, :incnum, " +
                "        1, :rule, SYSUTCDATETIME(), 0, SYSUTCDATETIME())")
                .setParameter("org", org)
                .setParameter("ext", extId)
                .setParameter("title", breachTitle)
                .setParameter("desc", breachDesc)
                .setParameter("btype", breachType)
                .setParameter("n", dataSubjectsAffectedEst)
                .setParameter("notify", pdpcNotifyRequired ? 1 : 0)
                .setParameter("status", "OPEN")
                .setParameter("incid", id)
                .setParameter("incnum", incNumber)
                .setParameter("rule", RULE_NAME)
                .executeUpdate();

        Long newBreachId = ((Number) em.createNativeQuery(
                "SELECT TOP 1 id FROM privacy.data_breaches " +
                "WHERE source_cyber_incident_id = :id ORDER BY id DESC")
                .setParameter("id", id).getSingleResult()).longValue();

        // 3) cross-link
        em.createNativeQuery(
                "UPDATE cyber.incidents " +
                "   SET linked_data_breach_id = :bid, linked_data_breach_code = :extstr " +
                " WHERE id = :id")
                .setParameter("bid", newBreachId)
                .setParameter("extstr", extId.toString())
                .setParameter("id", id)
                .executeUpdate();

        // 4) log
        writeLog(org, "BRIDGE_FIRED", id, incNumber, null, null,
                "Cyber incident classified as PII-affecting (" + dataSubjectsAffectedEst + " subjects, "
                        + dataCategoriesAffected + ")",
                "CREATED",
                "Auto-created DataBreach " + breachType + "; SDAIA notification "
                        + (pdpcNotifyRequired ? "required (72h SLA)." : "optional."),
                actorUserId, actorUsername);
        return writeLog(org, "BREACH_CREATED", id, incNumber, newBreachId, extId.toString(),
                "Auto-created DataBreach for cyber incident " + incNumber,
                "CREATED",
                "DataBreach " + extId + " created and bidirectionally linked to incident " + incNumber,
                actorUserId, actorUsername);
    }

    private CyberBreachBridgeLog writeLog(Long org, String eventCode,
                                          Long incidentId, String incidentNumber,
                                          Long breachId, String breachExtId,
                                          String triggerReason, String outcome, String detail,
                                          Long actorUserId, String actorUsername) {
        CyberBreachBridgeLog l = new CyberBreachBridgeLog();
        l.setOrganisationId(org);
        l.setEventCode(eventCode);
        l.setCyberIncidentId(incidentId);
        l.setCyberIncidentNumber(incidentNumber);
        l.setDataBreachId(breachId);
        l.setDataBreachExternalId(breachExtId);
        l.setRuleName(RULE_NAME);
        l.setTriggerReason(triggerReason);
        l.setOutcome(outcome);
        l.setOutcomeDetail(detail);
        l.setTriggeredAt(LocalDateTime.now());
        l.setTriggeredBy(actorUserId);
        l.setTriggeredByUsername(actorUsername);
        return logRepo.save(l);
    }
}
