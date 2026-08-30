package com.tss.grc.module.data_officer.service;

import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.entity.Entities.*;
import com.tss.grc.module.data_officer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only aggregator for the Saudi Data Officer Toolkit — the per-entity
 * timeline view and the cross-module dashboard.
 *
 * <p>Extracted from the former {@code DataOfficerService} god-class
 * (QUAL-ESG-001). The dashboard is genuinely cross-aggregate (it counts across
 * every sub-module), so it reads the repositories directly; behaviour is
 * unchanged.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DataOfficerQueryService {

    private final DataOfficerSupport support;
    private final DsrRepository dsrRepo;
    private final ConsentRepository consentRepo;
    private final DpiaRepository dpiaRepo;
    private final BreachRepository breachRepo;
    private final PrivacyNoticeRepository noticeRepo;
    private final RetentionRepository retentionRepo;
    private final DisposalJobRepository disposalRepo;
    private final NdrRepository ndrRepo;
    private final CatalogEntryRepository catalogRepo;
    private final QualityRuleRepository qualityRuleRepo;
    private final QualityRunRepository qualityRunRepo;
    private final EthicsRepository ethicsRepo;

    public List<EventResponse> timeline(String entityType, Long entityId) {
        return support.readTimeline(entityType, entityId);
    }

    public DashboardResponse dashboard() {
        Long o = DataOfficerSupport.org();
        LocalDateTime now = LocalDateTime.now();
        List<DsrRequest> allDsr = dsrRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<Breach> allBreach = breachRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<Dpia> allDpia = dpiaRepo.findByOrganisationIdOrderByCreatedAtDesc(o);

        long dsrOpen = allDsr.stream().filter(d -> !Set.of("FULFILLED","REJECTED","PARTIAL","CLOSED").contains(d.getStatus())).count();
        long dsrOverdue = dsrRepo.findOverdue(o, now).size();

        long consentActive = consentRepo.countByOrganisationIdAndStatus(o, "GRANTED");
        long consentWithdrawn30 = consentRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(c -> "WITHDRAWN".equals(c.getStatus()))
            .filter(c -> c.getWithdrawnAt() != null && c.getWithdrawnAt().isAfter(now.minusDays(30)))
            .count();

        // F2-402: APPROVED_WITH_CONDITIONS is a DECIDED state (transitionDpia stamps decidedAt for
        // it, like APPROVED/REJECTED — see PrivacyAssuranceService), so it must be excluded from
        // "open". Dropping it counted one AWC record as open here AND as approved in dpiaApprovedYtd
        // below — a double-count across two mutually-exclusive buckets (in-flight vs decided).
        long dpiaOpen = allDpia.stream()
            .filter(d -> !Set.of("APPROVED","APPROVED_WITH_CONDITIONS","REJECTED","ARCHIVED").contains(d.getStatus())).count();
        int year = LocalDate.now().getYear();
        long dpiaApprovedYtd = allDpia.stream()
            .filter(d -> "APPROVED".equals(d.getStatus()) || "APPROVED_WITH_CONDITIONS".equals(d.getStatus()))
            .filter(d -> d.getDecidedAt() != null && d.getDecidedAt().getYear() == year)
            .count();

        long breachOpen = allBreach.stream()
            .filter(b -> !Set.of("CLOSED","FALSE_POSITIVE").contains(b.getStatus())).count();
        long breachOverdueSdaia = breachRepo.findOverdueSdaia(o, now).size();

        long noticesPublished = noticeRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(n -> "PUBLISHED".equals(n.getStatus())).count();
        long noticesReConsent = noticeRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(n -> "PUBLISHED".equals(n.getStatus()) && Boolean.TRUE.equals(n.getRequiresReConsent()))
            .count();

        long retentionDue = retentionRepo.findReviewsDue(o, LocalDate.now()).size();
        long disposalScheduled = disposalRepo.findByOrganisationIdAndStatusOrderByScheduledForAsc(o, "SCHEDULED").size();

        long ndrThisYearInt = ndrRepo.findByOrganisationIdAndCycleYear(o, year)
            .map(n -> switch (n.getStatus()) {
                case "ACCEPTED" -> 3L;
                case "SUBMITTED" -> 2L;
                case "READY" -> 1L;
                default -> 0L;
            }).orElse(0L);

        long catEntries = catalogRepo.findByOrganisationIdOrderByCreatedAtDesc(o).size();
        long catPii = catalogRepo.countByOrganisationIdAndContainsPii(o, true);

        long qRules = qualityRuleRepo.findByOrganisationIdOrderByCreatedAtDesc(o).size();
        long qRed30 = qualityRunRepo.findByOrganisationIdOrderByRunAtDesc(o).stream()
            .filter(r -> "RED".equals(r.getBand()))
            .filter(r -> r.getRunAt() != null && r.getRunAt().isAfter(now.minusDays(30)))
            .count();

        // F2-402: the twin of dpiaOpen — APPROVED_WITH_CONDITIONS is a DECIDED ethics state (the
        // ethics machine stamps decidedAt for it too), so it is not open. ESCALATED stays open: it
        // is an in-flight re-review edge (ESCALATED -> COMMITTEE_REVIEW), not a decision.
        long ethicsOpen = ethicsRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(e -> !Set.of("APPROVED","APPROVED_WITH_CONDITIONS","REJECTED").contains(e.getStatus())).count();

        Map<String, Long> dsrByStatus = allDsr.stream()
            .collect(Collectors.groupingBy(DsrRequest::getStatus, Collectors.counting()));
        Map<String, Long> breachBySev = allBreach.stream()
            .filter(b -> b.getSeverity() != null)
            .collect(Collectors.groupingBy(Breach::getSeverity, Collectors.counting()));
        Map<String, Long> dpiaByStatus = allDpia.stream()
            .collect(Collectors.groupingBy(Dpia::getStatus, Collectors.counting()));

        List<DsrResponse> recentDsr = allDsr.stream().limit(8).map(DataOfficerMappers::toDsr).toList();
        List<BreachResponse> recentBreach = allBreach.stream().limit(8).map(DataOfficerMappers::toBreach).toList();

        return new DashboardResponse(
            dsrOpen, dsrOverdue,
            consentActive, consentWithdrawn30,
            dpiaOpen, dpiaApprovedYtd,
            breachOpen, breachOverdueSdaia,
            noticesPublished, noticesReConsent,
            retentionDue, disposalScheduled,
            ndrThisYearInt,
            catEntries, catPii,
            qRules, qRed30,
            ethicsOpen,
            dsrByStatus, breachBySev, dpiaByStatus,
            recentDsr, recentBreach);
    }
}
