package com.tss.grc.module.openbanking.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.openbanking.dto.ObDtos.ApiCallResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.ConsentResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.CreateTppRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.DashboardResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.EventResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.GrantConsentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.IncidentResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.NotifySamaRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.RaiseIncidentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.RecordApiCallRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.RevokeConsentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.TppResponse;
import com.tss.grc.module.openbanking.dto.ObDtos.TransitionIncidentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.TransitionTppRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.UpdateTppRequest;
import com.tss.grc.module.openbanking.entity.ObApiCall;
import com.tss.grc.module.openbanking.entity.ObConsent;
import com.tss.grc.module.openbanking.entity.ObEvent;
import com.tss.grc.module.openbanking.entity.ObIncident;
import com.tss.grc.module.openbanking.entity.ObTpp;
import com.tss.grc.module.openbanking.repository.ObApiCallRepository;
import com.tss.grc.module.openbanking.repository.ObConsentRepository;
import com.tss.grc.module.openbanking.repository.ObEventRepository;
import com.tss.grc.module.openbanking.repository.ObIncidentRepository;
import com.tss.grc.module.openbanking.repository.ObTppRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Open Banking Compliance service — Sprint 19 PR #1.
 *
 * <p>State machines:
 * <pre>
 *   TPP:     PENDING_KYB → ACTIVE → SUSPENDED → REVOKED
 *                            ↑         ↓
 *                            └ reinstate ┘
 *
 *   Consent: AUTHORISED → ACTIVE → EXPIRED | REVOKED
 *
 *   Incident: OPEN → INVESTIGATING → CONTAINED → RESOLVED → CLOSED
 *                                              ↘ ESCALATED_SAMA
 * </pre>
 *
 * <p>F7+F8 codes emitted:
 * <ul>
 *   <li>OB_TPP_ONBOARDED / SUSPENDED / REVOKED / REINSTATED</li>
 *   <li>OB_CONSENT_GRANTED / REVOKED / EXPIRED</li>
 *   <li>OB_CONSENT_BULK_REVOCATION — ≥100 revokes in 1h (fraud signal)</li>
 *   <li>OB_API_ABUSE — rate-limit / scope-creep detection (fired externally)</li>
 *   <li>OB_INCIDENT_RAISED / RESOLVED</li>
 *   <li>OB_CERT_EXPIRING — fired by F8 scheduler</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ObService {

    private static final Map<String, Set<String>> TPP_ALLOWED = Map.of(
            "PENDING_KYB", Set.of("ACTIVE", "REVOKED"),
            "ACTIVE",      Set.of("SUSPENDED", "REVOKED"),
            "SUSPENDED",   Set.of("ACTIVE", "REVOKED"),
            "REVOKED",     Set.of());

    private static final Map<String, Set<String>> INCIDENT_ALLOWED = Map.of(
            "OPEN",            Set.of("INVESTIGATING", "RESOLVED"),
            "INVESTIGATING",   Set.of("CONTAINED", "RESOLVED"),
            "CONTAINED",       Set.of("RESOLVED"),
            "RESOLVED",        Set.of("CLOSED"),
            "CLOSED",          Set.of());

    private static final long BULK_REVOCATION_THRESHOLD = 100L;

    /** Row cap for the dashboard's recent-TPP and open-incident preview lists. */
    private static final int DASHBOARD_RECENT_LIMIT = 10;

    private final ObTppRepository tppRepo;
    private final ObConsentRepository consentRepo;
    private final ObApiCallRepository apiCallRepo;
    private final ObIncidentRepository incidentRepo;
    private final ObEventRepository eventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── TPP writes ────────────────────────────────────────────────

    @Transactional
    public TppResponse createTpp(CreateTppRequest req) {
        tppRepo.findByReferenceAndOrganisationId(req.getReference(), org())
                .ifPresent(x -> {
                    throw new IllegalStateException(
                            "TPP " + req.getReference() + " already exists");
                });
        ObTpp t = new ObTpp();
        t.setOrganisationId(org());
        t.setReference(req.getReference());
        t.setLegalNameEn(req.getLegalNameEn());
        t.setLegalNameAr(req.getLegalNameAr());
        t.setSamaLicenceRef(req.getSamaLicenceRef());
        t.setFapiClientId(req.getFapiClientId());
        t.setRoles(req.getRoles());
        t.setCountry(req.getCountry());
        t.setCertFingerprint(req.getCertFingerprint());
        t.setCertExpiresAt(req.getCertExpiresAt());
        t.setRateLimitPerSec(req.getRateLimitPerSec());
        t.setAccountManagerUserId(req.getAccountManagerUserId());
        t.setComplianceOfficerUserId(req.getComplianceOfficerUserId());
        ObTpp saved = tppRepo.save(t);

        audit.log("OB_TPP_REGISTERED", "ObTpp", saved.getId(),
                "TPP " + saved.getReference() + " (" + saved.getRoles()
                        + ") registered, PENDING_KYB");
        timeline(saved.getId(), null, null, "TPP_ONBOARDED", saved.getStatus(),
                "TPP " + saved.getReference() + " entered the register",
                "تسجيل مزوّد الخدمة " + saved.getReference() + " في السجل");
        return toTpp(saved);
    }

    @Transactional
    public TppResponse updateTpp(Long id, UpdateTppRequest req) {
        ObTpp t = loadTpp(id);
        if (req.getLegalNameEn() != null) t.setLegalNameEn(req.getLegalNameEn());
        if (req.getLegalNameAr() != null) t.setLegalNameAr(req.getLegalNameAr());
        if (req.getSamaLicenceRef() != null) t.setSamaLicenceRef(req.getSamaLicenceRef());
        if (req.getFapiClientId() != null) t.setFapiClientId(req.getFapiClientId());
        if (req.getRoles() != null) t.setRoles(req.getRoles());
        if (req.getCountry() != null) t.setCountry(req.getCountry());
        if (req.getCertFingerprint() != null) t.setCertFingerprint(req.getCertFingerprint());
        if (req.getCertExpiresAt() != null) t.setCertExpiresAt(req.getCertExpiresAt());
        if (req.getRateLimitPerSec() != null) t.setRateLimitPerSec(req.getRateLimitPerSec());
        if (req.getAccountManagerUserId() != null)
            t.setAccountManagerUserId(req.getAccountManagerUserId());
        if (req.getComplianceOfficerUserId() != null)
            t.setComplianceOfficerUserId(req.getComplianceOfficerUserId());
        ObTpp saved = tppRepo.save(t);
        audit.log("OB_TPP_UPDATED", "ObTpp", saved.getId(),
                "TPP " + saved.getReference() + " updated");
        return toTpp(saved);
    }

    @Transactional
    public TppResponse transitionTpp(Long id, TransitionTppRequest req) {
        ObTpp t = loadTpp(id);
        String from = t.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = TPP_ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal TPP transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        t.setStatus(to);
        String trigger;
        switch (to) {
            case "ACTIVE" -> {
                if ("PENDING_KYB".equals(from)) {
                    t.setOnboardedAt(LocalDate.now());
                    trigger = "OB_TPP_ONBOARDED";
                } else {
                    trigger = "OB_TPP_REINSTATED";
                }
            }
            case "SUSPENDED" -> {
                t.setSuspendedAt(LocalDateTime.now());
                t.setSuspensionReasonEn(req.getReasonEn());
                t.setSuspensionReasonAr(req.getReasonAr());
                trigger = "OB_TPP_SUSPENDED";
            }
            case "REVOKED" -> {
                t.setRevokedAt(LocalDateTime.now());
                trigger = "OB_TPP_REVOKED";
            }
            default -> trigger = "OB_TPP_TRANSITIONED";
        }
        ObTpp saved = tppRepo.save(t);
        audit.log(trigger, "ObTpp", saved.getId(),
                "TPP " + saved.getReference() + " " + from + " → " + to);
        emit(trigger, "ObTpp", saved.getId(), saved.getReference(),
                Map.of("reference", saved.getReference(),
                        "from", from, "to", to,
                        "roles", saved.getRoles() == null ? "" : saved.getRoles()));
        timeline(saved.getId(), null, null, mapToEvent(to), to,
                req.getReasonEn(), req.getReasonAr());

        // If revoked or suspended, auto-revoke active consents.
        if ("REVOKED".equals(to) || "SUSPENDED".equals(to)) {
            List<ObConsent> active = consentRepo
                    .findByTppIdAndOrganisationIdOrderByGrantedAtDesc(saved.getId(), org());
            for (ObConsent c : active) {
                if ("AUTHORISED".equals(c.getStatus()) || "ACTIVE".equals(c.getStatus())) {
                    c.setStatus("REVOKED");
                    c.setRevokedAt(LocalDateTime.now());
                    c.setRevokedBy("BANK");
                    c.setRevocationReasonEn("TPP " + to.toLowerCase() + " by bank — "
                            + (req.getReasonEn() == null ? "regulatory action" : req.getReasonEn()));
                    c.setRevocationReasonAr("سحب موافقة لأنّ المزوّد " + to.toLowerCase());
                    consentRepo.save(c);
                }
            }
            recountTpp(saved);
        }
        return toTpp(saved);
    }

    // ─── Consent writes ────────────────────────────────────────────

    @Transactional
    public ConsentResponse grantConsent(GrantConsentRequest req) {
        ObTpp t = loadTpp(req.getTppId());
        if (!"ACTIVE".equals(t.getStatus())) {
            throw new IllegalStateException(
                    "Cannot grant consent — TPP status is " + t.getStatus());
        }
        ObConsent c = new ObConsent();
        c.setOrganisationId(org());
        c.setTppId(t.getId());
        c.setConsentReference(req.getConsentReference());
        c.setCustomerHash(req.getCustomerHash());
        c.setCustomerLabelEn(req.getCustomerLabelEn());
        c.setCustomerLabelAr(req.getCustomerLabelAr());
        c.setConsentType(req.getConsentType());
        c.setScopes(req.getScopes());
        c.setStatus("ACTIVE");
        c.setGrantedAt(LocalDateTime.now());
        c.setExpiresAt(req.getExpiresAt() != null
                ? req.getExpiresAt() : LocalDate.now().plusDays(180));
        c.setAmountCapSar(req.getAmountCapSar());
        c.setFrequencyWindowDays(req.getFrequencyWindowDays());
        ObConsent saved = consentRepo.save(c);
        recountTpp(t);
        audit.log("OB_CONSENT_GRANTED", "ObConsent", saved.getId(),
                "Consent " + saved.getConsentReference()
                        + " granted to " + t.getReference()
                        + ", scopes [" + saved.getScopes() + "]");
        emit("OB_CONSENT_GRANTED", "ObConsent", saved.getId(),
                saved.getConsentReference(),
                Map.of("tppId", String.valueOf(t.getId()),
                        "tppReference", t.getReference(),
                        "consentType", saved.getConsentType(),
                        "scopes", saved.getScopes()));
        timeline(t.getId(), saved.getId(), null, "CONSENT_GRANTED", "ACTIVE",
                "Consent " + saved.getConsentReference() + " granted ("
                        + saved.getConsentType() + ")",
                "منح الموافقة " + saved.getConsentReference() + " ("
                        + saved.getConsentType() + ")");
        return toConsent(saved);
    }

    @Transactional
    public ConsentResponse revokeConsent(Long id, RevokeConsentRequest req) {
        ObConsent c = consentRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Consent " + id + " not found"));
        if ("REVOKED".equals(c.getStatus()) || "EXPIRED".equals(c.getStatus())) {
            throw new IllegalStateException(
                    "Consent already " + c.getStatus());
        }
        c.setStatus("REVOKED");
        c.setRevokedAt(LocalDateTime.now());
        c.setRevokedBy(req.getRevokedBy());
        c.setRevocationReasonEn(req.getReasonEn());
        c.setRevocationReasonAr(req.getReasonAr());
        ObConsent saved = consentRepo.save(c);
        ObTpp t = loadTpp(c.getTppId());
        recountTpp(t);

        audit.log("OB_CONSENT_REVOKED", "ObConsent", saved.getId(),
                "Consent " + saved.getConsentReference()
                        + " revoked by " + saved.getRevokedBy());
        emit("OB_CONSENT_REVOKED", "ObConsent", saved.getId(),
                saved.getConsentReference(),
                Map.of("tppId", String.valueOf(t.getId()),
                        "tppReference", t.getReference(),
                        "revokedBy", saved.getRevokedBy() == null ? "" : saved.getRevokedBy()));
        timeline(t.getId(), saved.getId(), null, "CONSENT_REVOKED", "REVOKED",
                req.getReasonEn(), req.getReasonAr());

        // Bulk revocation fraud signal — ≥100 consents revoked in last hour for this TPP.
        long recentRevokes = consentRepo
                .findByTppIdAndOrganisationIdOrderByGrantedAtDesc(t.getId(), org())
                .stream()
                .filter(x -> "REVOKED".equals(x.getStatus())
                        && x.getRevokedAt() != null
                        && x.getRevokedAt().isAfter(LocalDateTime.now().minusHours(1)))
                .count();
        if (recentRevokes >= BULK_REVOCATION_THRESHOLD) {
            audit.log("OB_CONSENT_BULK_REVOCATION", "ObTpp", t.getId(),
                    "Bulk consent revocation detected for TPP "
                            + t.getReference() + " — " + recentRevokes
                            + " consents revoked in last hour");
            emit("OB_CONSENT_BULK_REVOCATION", "ObTpp", t.getId(),
                    t.getReference(),
                    Map.of("tppReference", t.getReference(),
                            "revokesInLastHour", String.valueOf(recentRevokes),
                            "threshold", String.valueOf(BULK_REVOCATION_THRESHOLD)));
            timeline(t.getId(), null, null, "BULK_REVOCATION_DETECTED", null,
                    "Bulk revocation: " + recentRevokes + " consents in last hour",
                    "سحب مفاجئ: " + recentRevokes + " موافقات في الساعة الأخيرة");
        }
        return toConsent(saved);
    }

    // ─── API call audit ────────────────────────────────────────────

    @Transactional
    public ApiCallResponse recordApiCall(RecordApiCallRequest req) {
        ObTpp t = loadTpp(req.getTppId());
        ObApiCall a = new ObApiCall();
        a.setOrganisationId(org());
        a.setTppId(t.getId());
        a.setConsentReference(req.getConsentReference());
        a.setOccurredAt(LocalDateTime.now());
        a.setHttpMethod(req.getHttpMethod());
        a.setEndpoint(req.getEndpoint());
        a.setStatusCode(req.getStatusCode());
        a.setLatencyMs(req.getLatencyMs());
        a.setScopesUsed(req.getScopesUsed());
        a.setCustomerHash(req.getCustomerHash());
        a.setSourceIpHash(req.getSourceIpHash());
        a.setInteractionId(req.getInteractionId());
        a.setResponseBytes(req.getResponseBytes());
        a.setErrorReason(req.getErrorReason());
        ObApiCall saved = apiCallRepo.save(a);

        // Update consent call count if linked.
        if (req.getConsentReference() != null) {
            consentRepo.findByConsentReferenceAndOrganisationId(
                            req.getConsentReference(), org())
                    .ifPresent(c -> {
                        c.setCallCount((c.getCallCount() == null ? 0L : c.getCallCount()) + 1L);
                        c.setLastUsedAt(LocalDateTime.now());
                        consentRepo.save(c);
                    });
        }
        return toApiCall(saved);
    }

    // ─── Incident writes ───────────────────────────────────────────

    @Transactional
    public IncidentResponse raiseIncident(RaiseIncidentRequest req) {
        ObIncident i = new ObIncident();
        i.setOrganisationId(org());
        i.setReference(generateIncidentReference());
        i.setTppId(req.getTppId());
        i.setIncidentType(req.getIncidentType());
        i.setSeverity(req.getSeverity());
        i.setStatus("OPEN");
        i.setTitleEn(req.getTitleEn());
        i.setTitleAr(req.getTitleAr());
        i.setDescriptionEn(req.getDescriptionEn());
        i.setDescriptionAr(req.getDescriptionAr());
        i.setDetectedAt(LocalDateTime.now());
        i.setSamaReportable(Boolean.TRUE.equals(req.getSamaReportable()));
        i.setAffectedCustomerCount(req.getAffectedCustomerCount());
        i.setAssignedToUserId(req.getAssignedToUserId());
        ObIncident saved = incidentRepo.save(i);
        if (req.getTppId() != null) {
            recountTpp(loadTpp(req.getTppId()));
        }

        audit.log("OB_INCIDENT_RAISED", "ObIncident", saved.getId(),
                "Incident " + saved.getReference() + " raised — "
                        + saved.getIncidentType() + " " + saved.getSeverity()
                        + (saved.getSamaReportable() ? " (SAMA-reportable)" : ""));
        Map<String, Object> payload = new HashMap<>();
        payload.put("reference", saved.getReference());
        payload.put("incidentType", saved.getIncidentType());
        payload.put("severity", saved.getSeverity());
        payload.put("samaReportable", String.valueOf(saved.getSamaReportable()));
        if (saved.getTppId() != null)
            payload.put("tppId", String.valueOf(saved.getTppId()));
        if (saved.getAffectedCustomerCount() != null)
            payload.put("affectedCustomers", String.valueOf(saved.getAffectedCustomerCount()));
        emit("OB_INCIDENT_RAISED", "ObIncident", saved.getId(),
                saved.getReference(), payload);
        timeline(saved.getTppId(), null, saved.getId(),
                "INCIDENT_RAISED", "OPEN",
                "Incident " + saved.getReference() + " raised: " + saved.getTitleEn(),
                "رفع الحادثة " + saved.getReference() + ": " + saved.getTitleAr());
        return toIncident(saved);
    }

    @Transactional
    public IncidentResponse transitionIncident(Long id, TransitionIncidentRequest req) {
        ObIncident i = incidentRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incident " + id + " not found"));
        String from = i.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = INCIDENT_ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal incident transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        i.setStatus(to);
        switch (to) {
            case "CONTAINED" -> i.setContainedAt(LocalDateTime.now());
            case "RESOLVED" -> i.setResolvedAt(LocalDateTime.now());
            case "CLOSED" -> i.setClosedAt(LocalDateTime.now());
            default -> { }
        }
        ObIncident saved = incidentRepo.save(i);
        if (saved.getTppId() != null) recountTpp(loadTpp(saved.getTppId()));

        String trigger = "RESOLVED".equals(to) ? "OB_INCIDENT_RESOLVED"
                : "OB_INCIDENT_TRANSITIONED";
        audit.log(trigger, "ObIncident", saved.getId(),
                "Incident " + saved.getReference() + " " + from + " → " + to);
        emit(trigger, "ObIncident", saved.getId(), saved.getReference(),
                Map.of("reference", saved.getReference(),
                        "from", from, "to", to,
                        "severity", saved.getSeverity()));
        timeline(saved.getTppId(), null, saved.getId(),
                "INCIDENT_STATUS_CHANGED", to,
                req.getNoteEn(), req.getNoteAr());
        return toIncident(saved);
    }

    @Transactional
    public IncidentResponse notifySama(Long id, NotifySamaRequest req) {
        ObIncident i = incidentRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incident " + id + " not found"));
        i.setSamaNotificationRef(req.getSamaNotificationRef());
        i.setSamaNotifiedAt(LocalDateTime.now());
        i.setSamaReportable(true);
        ObIncident saved = incidentRepo.save(i);
        audit.log("OB_INCIDENT_SAMA_NOTIFIED", "ObIncident", saved.getId(),
                "SAMA notified for " + saved.getReference()
                        + " ref " + saved.getSamaNotificationRef());
        timeline(saved.getTppId(), null, saved.getId(),
                "INCIDENT_SAMA_NOTIFIED", saved.getStatus(),
                "SAMA notified — ref " + saved.getSamaNotificationRef(),
                "إبلاغ ساما — المرجع " + saved.getSamaNotificationRef());
        return toIncident(saved);
    }

    // ─── Reads ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TppResponse> listTpps(int page, int size) {
        return tppRepo.findByOrganisationIdOrderByReferenceAsc(
                        org(), PageRequest.of(page, size))
                .map(this::toTpp);
    }

    @Transactional(readOnly = true)
    public TppResponse getTpp(Long id) { return toTpp(loadTpp(id)); }

    @Transactional(readOnly = true)
    public Page<ConsentResponse> listConsents(int page, int size) {
        return consentRepo.findByOrganisationIdOrderByGrantedAtDesc(
                        org(), PageRequest.of(page, size))
                .map(ObService::toConsent);
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> listConsentsForTpp(Long tppId) {
        loadTpp(tppId);
        return consentRepo.findByTppIdAndOrganisationIdOrderByGrantedAtDesc(tppId, org())
                .stream().map(ObService::toConsent).toList();
    }

    @Transactional(readOnly = true)
    public Page<ApiCallResponse> listApiCalls(int page, int size) {
        return apiCallRepo.findByOrganisationIdOrderByOccurredAtDesc(
                        org(), PageRequest.of(page, size))
                .map(ObService::toApiCall);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> listIncidents(int page, int size) {
        return incidentRepo.findByOrganisationIdOrderByDetectedAtDesc(
                        org(), PageRequest.of(page, size))
                .map(ObService::toIncident);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id) {
        return toIncident(incidentRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incident " + id + " not found")));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listTppEvents(Long tppId) {
        loadTpp(tppId);
        return eventRepo.findByTppIdAndOrganisationIdOrderByOccurredAtDesc(tppId, org())
                .stream().map(ObService::toEvent).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long activeTpps = tppRepo.countByOrganisationIdAndStatus(org(), "ACTIVE");
        long suspendedTpps = tppRepo.countByOrganisationIdAndStatus(org(), "SUSPENDED");
        long pendingKyb = tppRepo.countByOrganisationIdAndStatus(org(), "PENDING_KYB");
        long activeConsents = consentRepo.countByOrganisationIdAndStatus(org(), "ACTIVE");

        // QUAL-TI-008: "revoked last 24h" via a count query instead of a paged scan.
        long revokedRecent = consentRepo.countByOrganisationIdAndStatusAndRevokedAtAfter(
                org(), "REVOKED", LocalDateTime.now().minusDays(1));

        long openIncidents = incidentRepo.countByOrganisationIdAndStatus(org(), "OPEN")
                + incidentRepo.countByOrganisationIdAndStatus(org(), "INVESTIGATING")
                + incidentRepo.countByOrganisationIdAndStatus(org(), "CONTAINED");
        long criticalOpen = incidentRepo.countByOrganisationIdAndSeverityAndStatusNotIn(
                org(), "CRITICAL", Set.of("RESOLVED", "CLOSED"));
        // QUAL-TI-008: SAMA-reportable-open via a count query instead of a paged scan.
        long samaReportableOpen = incidentRepo.countByOrganisationIdAndSamaReportableAndStatusNotIn(
                org(), Boolean.TRUE, Set.of("CLOSED", "RESOLVED"));

        long apiCalls24h = apiCallRepo.countByOrganisationIdAndOccurredAtAfter(
                org(), LocalDateTime.now().minusDays(1));
        long apiErrors24h = apiCallRepo
                .countByOrganisationIdAndStatusCodeGreaterThanEqualAndOccurredAtAfter(
                        org(), 400, LocalDateTime.now().minusDays(1));

        long certExpiring30 = tppRepo.findByOrganisationIdAndCertExpiresAtBefore(
                        org(), LocalDate.now().plusDays(30)).stream()
                .filter(t -> !"REVOKED".equals(t.getStatus()))
                .count();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : new String[] {"PENDING_KYB", "ACTIVE", "SUSPENDED", "REVOKED"}) {
            long n = tppRepo.countByOrganisationIdAndStatus(org(), s);
            if (n > 0) byStatus.put(s, n);
        }
        // QUAL-TI-008: consent counts grouped by type in one query instead of a paged scan.
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : consentRepo.countByConsentTypeGrouped(org())) {
            byType.put((String) row[0], (Long) row[1]);
        }

        List<TppResponse> recent = tppRepo.findByOrganisationIdOrderByReferenceAsc(
                        org(), PageRequest.of(0, DASHBOARD_RECENT_LIMIT)).stream()
                .map(this::toTpp).toList();
        List<IncidentResponse> openList = incidentRepo
                .findByOrganisationIdOrderByDetectedAtDesc(org(), PageRequest.of(0, DASHBOARD_RECENT_LIMIT))
                .stream()
                .filter(x -> !Set.of("CLOSED", "RESOLVED").contains(x.getStatus()))
                .map(ObService::toIncident).toList();

        return DashboardResponse.builder()
                .activeTpps(activeTpps).suspendedTpps(suspendedTpps).pendingKybTpps(pendingKyb)
                .activeConsents(activeConsents)
                .revokedConsents24h(revokedRecent)
                .openIncidents(openIncidents).criticalOpenIncidents(criticalOpen)
                .samaReportableOpen(samaReportableOpen)
                .apiCalls24h(apiCalls24h).apiErrors24h(apiErrors24h)
                .certExpiring30d(certExpiring30)
                .tppsByStatus(byStatus).consentsByType(byType)
                .recentTpps(recent).openIncidentList(openList)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private ObTpp loadTpp(Long id) {
        return tppRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "TPP " + id + " not found in tenant"));
    }

    /**
     * F2-337 — mint the next {@code OBI-<year>-NNN} reference from the highest existing ordinal
     * for this org+year, never from a COUNT. A count re-mints an ordinal already in use whenever a
     * reference is missing from the counted set — beyond the query window (the old code counted only
     * the 500 most-recent incidents, so a tenant past 500/year re-minted every subsequent ordinal)
     * or after a soft-delete — and the filtered unique index {@code ux_ob_incident_reference} then
     * rejects the insert with a 500 on a SAMA-reportable register (F2-48's collision minter). The
     * skip loop absorbs a concurrent create, backstopped by that same index — this mirrors
     * {@code ComplaintService#generateNextComplaintNo}, the platform's own sequential-minter idiom.
     */
    private String generateIncidentReference() {
        int year = LocalDate.now().getYear();
        Long org = org();
        String prefix = "OBI-" + year + "-";
        int next = 1;
        Optional<ObIncident> top = incidentRepo
                .findTopByOrganisationIdAndReferenceStartingWithOrderByReferenceDesc(org, prefix);
        if (top.isPresent() && top.get().getReference() != null
                && top.get().getReference().matches("OBI-\\d{4}-\\d{3,}")) {
            String ref = top.get().getReference();
            next = Integer.parseInt(ref.substring(ref.lastIndexOf('-') + 1)) + 1;
        }
        for (int seq = next; seq < next + 1000; seq++) {
            String candidate = prefix + String.format(Locale.ROOT, "%03d", seq);
            if (!incidentRepo.existsByOrganisationIdAndReference(org, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to allocate an open-banking incident reference for " + year);
    }

    private void recountTpp(ObTpp t) {
        long active = consentRepo.countByOrganisationIdAndTppIdAndStatus(
                org(), t.getId(), "ACTIVE");
        long openInc = incidentRepo.countByOrganisationIdAndTppIdAndStatusNotIn(
                org(), t.getId(), Set.of("RESOLVED", "CLOSED"));
        t.setActiveConsentCount((int) active);
        t.setOpenIncidentCount((int) openInc);
        tppRepo.save(t);
    }

    private static String mapToEvent(String tppStatus) {
        return switch (tppStatus) {
            case "ACTIVE"      -> "TPP_ONBOARDED";
            case "SUSPENDED"   -> "TPP_SUSPENDED";
            case "REVOKED"     -> "TPP_REVOKED";
            default            -> "TPP_STATUS_CHANGED";
        };
    }

    private void timeline(Long tppId, Long consentId, Long incidentId,
                           String eventType, String statusAfter,
                           String noteEn, String noteAr) {
        ObEvent e = new ObEvent();
        e.setOrganisationId(org());
        e.setTppId(tppId);
        e.setConsentId(consentId);
        e.setIncidentId(incidentId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setActorName(currentActorName());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    /**
     * F2-257 — the display half of the actor pair, from the same primitive as {@link
     * #currentUserId()}.
     *
     * <p>Deliberately {@code username()} and not {@code requireUsername(...)}: the id half is
     * nullable and comes from the same place, so both halves are populated together or absent
     * together. Refusing here would let a timeline write abort the business operation it exists to
     * record.
     */
    private static String currentActorName() {
        return com.tss.grc.common.security.CurrentActor.username();
    }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    // ─── Mappers ───────────────────────────────────────────────────

    private TppResponse toTpp(ObTpp t) {
        return TppResponse.builder()
                .id(t.getId()).reference(t.getReference())
                .legalNameEn(t.getLegalNameEn()).legalNameAr(t.getLegalNameAr())
                .samaLicenceRef(t.getSamaLicenceRef()).fapiClientId(t.getFapiClientId())
                .roles(t.getRoles()).country(t.getCountry())
                .status(t.getStatus())
                .onboardedAt(t.getOnboardedAt())
                .suspendedAt(t.getSuspendedAt()).revokedAt(t.getRevokedAt())
                .suspensionReasonEn(t.getSuspensionReasonEn())
                .suspensionReasonAr(t.getSuspensionReasonAr())
                .certFingerprint(t.getCertFingerprint())
                .certExpiresAt(t.getCertExpiresAt())
                .rateLimitPerSec(t.getRateLimitPerSec())
                .accountManagerUserId(t.getAccountManagerUserId())
                .complianceOfficerUserId(t.getComplianceOfficerUserId())
                .activeConsentCount(t.getActiveConsentCount())
                .openIncidentCount(t.getOpenIncidentCount())
                .createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
                .build();
    }

    private static ConsentResponse toConsent(ObConsent c) {
        return ConsentResponse.builder()
                .id(c.getId()).tppId(c.getTppId())
                .consentReference(c.getConsentReference())
                .customerHash(c.getCustomerHash())
                .customerLabelEn(c.getCustomerLabelEn())
                .customerLabelAr(c.getCustomerLabelAr())
                .consentType(c.getConsentType()).scopes(c.getScopes())
                .status(c.getStatus()).grantedAt(c.getGrantedAt())
                .expiresAt(c.getExpiresAt()).revokedAt(c.getRevokedAt())
                .revokedBy(c.getRevokedBy())
                .revocationReasonEn(c.getRevocationReasonEn())
                .revocationReasonAr(c.getRevocationReasonAr())
                .amountCapSar(c.getAmountCapSar())
                .frequencyWindowDays(c.getFrequencyWindowDays())
                .callCount(c.getCallCount()).lastUsedAt(c.getLastUsedAt())
                .build();
    }

    private static ApiCallResponse toApiCall(ObApiCall a) {
        return ApiCallResponse.builder()
                .id(a.getId()).tppId(a.getTppId())
                .consentReference(a.getConsentReference())
                .occurredAt(a.getOccurredAt())
                .httpMethod(a.getHttpMethod()).endpoint(a.getEndpoint())
                .statusCode(a.getStatusCode()).latencyMs(a.getLatencyMs())
                .scopesUsed(a.getScopesUsed())
                .customerHash(a.getCustomerHash()).sourceIpHash(a.getSourceIpHash())
                .interactionId(a.getInteractionId())
                .responseBytes(a.getResponseBytes()).errorReason(a.getErrorReason())
                .build();
    }

    private static IncidentResponse toIncident(ObIncident i) {
        return IncidentResponse.builder()
                .id(i.getId()).reference(i.getReference()).tppId(i.getTppId())
                .incidentType(i.getIncidentType()).severity(i.getSeverity())
                .status(i.getStatus())
                .titleEn(i.getTitleEn()).titleAr(i.getTitleAr())
                .descriptionEn(i.getDescriptionEn()).descriptionAr(i.getDescriptionAr())
                .detectedAt(i.getDetectedAt()).containedAt(i.getContainedAt())
                .resolvedAt(i.getResolvedAt()).closedAt(i.getClosedAt())
                .samaReportable(i.getSamaReportable())
                .samaNotificationRef(i.getSamaNotificationRef())
                .samaNotifiedAt(i.getSamaNotifiedAt())
                .affectedCustomerCount(i.getAffectedCustomerCount())
                .assignedToUserId(i.getAssignedToUserId())
                .build();
    }

    private static EventResponse toEvent(ObEvent e) {
        return EventResponse.builder()
                .id(e.getId()).tppId(e.getTppId())
                .consentId(e.getConsentId()).incidentId(e.getIncidentId())
                .occurredAt(e.getOccurredAt()).eventType(e.getEventType())
                .actorUserId(e.getActorUserId()).actorName(e.getActorName())
                .statusAfter(e.getStatusAfter())
                .noteEn(e.getNoteEn()).noteAr(e.getNoteAr())
                .build();
    }
}
