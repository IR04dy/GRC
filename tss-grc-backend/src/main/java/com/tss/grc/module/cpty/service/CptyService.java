package com.tss.grc.module.cpty.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cpty.dto.CptyDtos.AssignRatingRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.CounterpartyResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.CreateCounterpartyRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.CreateRatingActionRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.DashboardResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.EventResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.LimitResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.RatingActionResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.RatingResponse;
import com.tss.grc.module.cpty.dto.CptyDtos.RecordUtilisationRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.UpdateCounterpartyRequest;
import com.tss.grc.module.cpty.dto.CptyDtos.UpsertLimitRequest;
import com.tss.grc.module.cpty.entity.Counterparty;
import com.tss.grc.module.cpty.entity.CptyEvent;
import com.tss.grc.module.cpty.entity.CreditRating;
import com.tss.grc.module.cpty.entity.LargeExposureLimit;
import com.tss.grc.module.cpty.entity.RatingAction;
import com.tss.grc.module.cpty.repository.CounterpartyRepository;
import com.tss.grc.module.cpty.repository.CptyEventRepository;
import com.tss.grc.module.cpty.repository.CreditRatingRepository;
import com.tss.grc.module.cpty.repository.LargeExposureLimitRepository;
import com.tss.grc.module.cpty.repository.RatingActionRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Counterparty Credit Rating Registry service — Sprint 18 PR #1.
 *
 * <p>F7 audit + F8 emit codes:
 * <ul>
 *   <li>COUNTERPARTY_REGISTERED</li>
 *   <li>COUNTERPARTY_RATING_DOWNGRADED / UPGRADED</li>
 *   <li>COUNTERPARTY_DEFAULTED</li>
 *   <li>COUNTERPARTY_WATCHLIST_NEGATIVE / REMOVED</li>
 *   <li>LARGE_EXPOSURE_LIMIT_AMBER / RED / BREACH</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CptyService {

    private final CounterpartyRepository cptyRepo;
    private final CreditRatingRepository ratingRepo;
    private final RatingActionRepository actionRepo;
    private final LargeExposureLimitRepository limitRepo;
    private final CptyEventRepository eventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Counterparty ──────────────────────────────────────────────

    @Transactional
    public CounterpartyResponse createCounterparty(CreateCounterpartyRequest req) {
        String ref = (req.getReference() == null || req.getReference().isBlank())
                ? generateReference()
                : req.getReference();

        Counterparty c = new Counterparty();
        c.setOrganisationId(org());
        c.setReference(ref);
        c.setLei(req.getLei());
        c.setLegalNameEn(req.getLegalNameEn());
        c.setLegalNameAr(req.getLegalNameAr());
        c.setShortName(req.getShortName());
        c.setSector(req.getSector());
        c.setCountry(req.getCountry());
        c.setIndustry(req.getIndustry());
        c.setParentGroupReference(req.getParentGroupReference());
        c.setGreFlag(Boolean.TRUE.equals(req.getGreFlag()));
        c.setSovereignFlag(Boolean.TRUE.equals(req.getSovereignFlag()));
        c.setFiFlag(Boolean.TRUE.equals(req.getFiFlag()));
        c.setRelationshipManagerUserId(req.getRelationshipManagerUserId());
        c.setCreditOfficerUserId(req.getCreditOfficerUserId());
        c.setOnboardedAt(req.getOnboardedAt());

        Counterparty saved = cptyRepo.save(c);
        audit.log("COUNTERPARTY_REGISTERED", "Counterparty", saved.getId(),
                "Counterparty " + saved.getReference() + " (" + saved.getLegalNameEn()
                        + ", " + saved.getSector() + ") registered");
        emit("COUNTERPARTY_REGISTERED", "Counterparty", saved.getId(),
                saved.getReference(),
                Map.of("reference", saved.getReference(),
                        "sector", saved.getSector(),
                        "greFlag", String.valueOf(saved.getGreFlag()),
                        "sovereignFlag", String.valueOf(saved.getSovereignFlag()),
                        "fiFlag", String.valueOf(saved.getFiFlag())));
        timeline(saved.getId(), "REGISTERED", null, null, null,
                "Counterparty registered: " + saved.getLegalNameEn(),
                "تم تسجيل الطرف المقابل: " + saved.getLegalNameAr());
        return toCounterparty(saved);
    }

    @Transactional
    public CounterpartyResponse updateCounterparty(Long id, UpdateCounterpartyRequest req) {
        Counterparty c = load(id);
        if (req.getLegalNameEn() != null) c.setLegalNameEn(req.getLegalNameEn());
        if (req.getLegalNameAr() != null) c.setLegalNameAr(req.getLegalNameAr());
        if (req.getShortName() != null) c.setShortName(req.getShortName());
        if (req.getSector() != null) c.setSector(req.getSector());
        if (req.getCountry() != null) c.setCountry(req.getCountry());
        if (req.getIndustry() != null) c.setIndustry(req.getIndustry());
        if (req.getParentGroupReference() != null)
            c.setParentGroupReference(req.getParentGroupReference());
        if (req.getGreFlag() != null) c.setGreFlag(req.getGreFlag());
        if (req.getSovereignFlag() != null) c.setSovereignFlag(req.getSovereignFlag());
        if (req.getFiFlag() != null) c.setFiFlag(req.getFiFlag());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        if (req.getRelationshipManagerUserId() != null)
            c.setRelationshipManagerUserId(req.getRelationshipManagerUserId());
        if (req.getCreditOfficerUserId() != null)
            c.setCreditOfficerUserId(req.getCreditOfficerUserId());
        if (req.getKycRefreshedAt() != null) c.setKycRefreshedAt(req.getKycRefreshedAt());
        if (req.getTotalExposureSarM() != null)
            c.setTotalExposureSarM(req.getTotalExposureSarM());
        Counterparty saved = cptyRepo.save(c);
        audit.log("COUNTERPARTY_UPDATED", "Counterparty", saved.getId(),
                "Counterparty " + saved.getReference() + " updated");
        return toCounterparty(saved);
    }

    // ─── Credit rating ─────────────────────────────────────────────

    @Transactional
    public RatingResponse assignRating(Long counterpartyId, AssignRatingRequest req) {
        Counterparty c = load(counterpartyId);
        CreditRating r = new CreditRating();
        r.setOrganisationId(org());
        r.setCounterpartyId(c.getId());
        r.setRatingType(req.getRatingType());
        r.setEffectiveDate(req.getEffectiveDate());
        r.setGrade(req.getGrade());
        r.setScale(req.getScale());
        r.setAgency(req.getAgency());
        r.setPd(req.getPd());
        r.setLgd(req.getLgd());
        r.setEadSarM(req.getEadSarM());
        if (req.getOutlook() != null) r.setOutlook(req.getOutlook());
        r.setRationaleEn(req.getRationaleEn());
        r.setRationaleAr(req.getRationaleAr());
        r.setAssignedByUserId(currentUserId());
        // F2-223 — this route is guarded by CPTY:APPROVE, and this class's own javadoc says why:
        // "assignRating and upsertLimit are POSTs on APPROVE because deciding the rating and the
        // limit is the credit authority's." The caller of an APPROVE route IS the approver, so
        // naming somebody else in the body contradicted the guard's stated purpose — while the
        // line directly above already resolved that same principal. F2-222's filing recorded this
        // as blocked on "a principal→user-id resolution this slice did not build"; the resolution
        // is currentUserId(), one line up.
        r.setApprovedByUserId(currentUserId());
        CreditRating saved = ratingRepo.save(r);

        // Cache latest rating on the counterparty.
        if ("INTERNAL".equals(saved.getRatingType())) {
            c.setCurrentInternalGrade(saved.getGrade());
        } else if ("EXTERNAL".equals(saved.getRatingType())) {
            c.setCurrentExternalRating(saved.getGrade());
            c.setCurrentExternalAgency(saved.getAgency());
        }
        cptyRepo.save(c);

        audit.log("COUNTERPARTY_RATING_ASSIGNED", "CreditRating", saved.getId(),
                c.getReference() + " " + saved.getRatingType() + " "
                        + saved.getGrade() + " (outlook " + saved.getOutlook() + ")");
        timeline(c.getId(), "RATING_ASSIGNED", saved.getId(), null, null,
                saved.getRatingType() + " rating " + saved.getGrade() + " effective "
                        + saved.getEffectiveDate(),
                "تصنيف " + saved.getRatingType() + " " + saved.getGrade() + " اعتباراً من "
                        + saved.getEffectiveDate());
        return toRating(saved);
    }

    @Transactional
    public RatingActionResponse recordAction(Long counterpartyId,
                                              CreateRatingActionRequest req) {
        Counterparty c = load(counterpartyId);
        RatingAction a = new RatingAction();
        a.setOrganisationId(org());
        a.setCounterpartyId(c.getId());
        a.setActionType(req.getActionType());
        a.setActionDate(req.getActionDate());
        a.setGradeBefore(req.getGradeBefore());
        a.setGradeAfter(req.getGradeAfter());
        a.setOutlookBefore(req.getOutlookBefore());
        a.setOutlookAfter(req.getOutlookAfter());
        a.setTriggerSource(req.getTriggerSource());
        a.setRationaleEn(req.getRationaleEn());
        a.setRationaleAr(req.getRationaleAr());
        a.setActorUserId(currentUserId());
        RatingAction saved = actionRepo.save(a);

        // Status side-effects on counterparty.
        String trigger = null;
        switch (saved.getActionType()) {
            case "WATCHLIST_NEGATIVE" -> {
                c.setStatus("WATCHLIST");
                trigger = "COUNTERPARTY_WATCHLIST_NEGATIVE";
            }
            case "WATCHLIST_REMOVE" -> {
                c.setStatus("ACTIVE");
                trigger = "COUNTERPARTY_WATCHLIST_REMOVED";
            }
            case "DOWNGRADE" -> {
                trigger = "COUNTERPARTY_RATING_DOWNGRADED";
                if (saved.getGradeAfter() != null) c.setCurrentInternalGrade(saved.getGradeAfter());
            }
            case "UPGRADE" -> {
                trigger = "COUNTERPARTY_RATING_UPGRADED";
                if (saved.getGradeAfter() != null) c.setCurrentInternalGrade(saved.getGradeAfter());
            }
            case "DEFAULT" -> {
                c.setStatus("DEFAULT");
                trigger = "COUNTERPARTY_DEFAULTED";
            }
            case "RECOVERY" -> {
                c.setStatus("RESOLVED");
            }
            default -> {
                // OUTLOOK_CHANGE / WATCHLIST_POSITIVE — no trigger
            }
        }
        cptyRepo.save(c);

        audit.log(trigger != null ? trigger : "COUNTERPARTY_RATING_ACTION",
                "RatingAction", saved.getId(),
                c.getReference() + " " + saved.getActionType()
                        + " " + (saved.getGradeBefore() == null ? "" : saved.getGradeBefore())
                        + " → " + (saved.getGradeAfter() == null ? "" : saved.getGradeAfter()));
        if (trigger != null) {
            emit(trigger, "RatingAction", saved.getId(), c.getReference(),
                    Map.of("counterpartyId", String.valueOf(c.getId()),
                            "reference", c.getReference(),
                            "sector", c.getSector(),
                            "actionType", saved.getActionType(),
                            "gradeBefore", saved.getGradeBefore() == null ? "" : saved.getGradeBefore(),
                            "gradeAfter", saved.getGradeAfter() == null ? "" : saved.getGradeAfter(),
                            "triggerSource", saved.getTriggerSource() == null ? "" : saved.getTriggerSource()));
        }
        timeline(c.getId(), "RATING_ACTION", null, saved.getId(), null,
                saved.getActionType() + ": "
                        + (saved.getGradeBefore() == null ? "—" : saved.getGradeBefore())
                        + " → " + (saved.getGradeAfter() == null ? "—" : saved.getGradeAfter())
                        + (saved.getRationaleEn() == null ? "" : " · " + saved.getRationaleEn()),
                saved.getActionType() + ": "
                        + (saved.getGradeBefore() == null ? "—" : saved.getGradeBefore())
                        + " ← " + (saved.getGradeAfter() == null ? "—" : saved.getGradeAfter())
                        + (saved.getRationaleAr() == null ? "" : " · " + saved.getRationaleAr()));
        return toAction(saved);
    }

    // ─── Large-exposure limit ──────────────────────────────────────

    @Transactional
    public LimitResponse upsertLimit(UpsertLimitRequest req) {
        // Identify existing row for (scope, key) pair.
        LargeExposureLimit existing = null;
        if ("COUNTERPARTY".equals(req.getScope()) && req.getCounterpartyId() != null) {
            List<LargeExposureLimit> hits = limitRepo
                    .findByCounterpartyIdAndOrganisationId(req.getCounterpartyId(), org());
            existing = hits.isEmpty() ? null : hits.get(0);
        } else if ("GROUP".equals(req.getScope()) && req.getGroupReference() != null) {
            existing = limitRepo.findByOrganisationIdOrderByUtilisationPctDesc(org())
                    .stream().filter(l -> "GROUP".equals(l.getScope())
                            && req.getGroupReference().equals(l.getGroupReference()))
                    .findFirst().orElse(null);
        }

        LargeExposureLimit l = existing == null ? new LargeExposureLimit() : existing;
        String prevBand = l.getBand();
        l.setOrganisationId(org());
        l.setScope(req.getScope());
        l.setCounterpartyId(req.getCounterpartyId());
        l.setGroupReference(req.getGroupReference());
        l.setDisplayNameEn(req.getDisplayNameEn());
        l.setDisplayNameAr(req.getDisplayNameAr());
        l.setLimitSarM(req.getLimitSarM());
        l.setUtilisedSarM(req.getUtilisedSarM());
        l.setUtilisationPct(computeUtilisationPct(req.getLimitSarM(), req.getUtilisedSarM()));
        l.setBand(computeBand(l.getUtilisationPct()));
        // F2-223 — approvedAt and approvedByUserId record the SAMA large-exposure approval this
        // CPTY:APPROVE route performs, so both come from the server. The client was minting
        // approvedAt in the BROWSER (`new Date().toISOString()`), which also meant an upsert of
        // an existing limit silently re-dated an approval taken months earlier — 74 of 74 rows in
        // dev and prod carry the column. nextReviewAt stays the caller's: it is a plan, not a
        // record of something that happened (F2-222).
        l.setApprovedAt(java.time.LocalDate.now());
        l.setNextReviewAt(req.getNextReviewAt());
        l.setApprovedByUserId(currentUserId());
        l.setUtilisationRefreshedAt(java.time.LocalDate.now());
        LargeExposureLimit saved = limitRepo.save(l);

        audit.log("LARGE_EXPOSURE_LIMIT_UPSERT", "LargeExposureLimit", saved.getId(),
                saved.getScope() + " limit " + saved.getDisplayNameEn() + " "
                        + saved.getUtilisedSarM() + "/" + saved.getLimitSarM()
                        + " (" + saved.getBand() + ")");
        Long linkedCpty = saved.getCounterpartyId();
        if (linkedCpty != null) {
            timeline(linkedCpty, "LIMIT_APPROVED", null, null, saved.getId(),
                    "Limit " + saved.getLimitSarM() + " SAR M approved (utilisation "
                            + saved.getUtilisationPct() + "%, band " + saved.getBand() + ")",
                    "اعتماد حدّ " + saved.getLimitSarM() + " م.ر. (الاستخدام "
                            + saved.getUtilisationPct() + "%، النطاق " + saved.getBand() + ")");
        }
        maybeFireBandEvent(saved, prevBand);
        return toLimit(saved);
    }

    @Transactional
    public LimitResponse recordUtilisation(Long limitId, RecordUtilisationRequest req) {
        LargeExposureLimit l = limitRepo.findByIdAndOrganisationId(limitId, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Limit " + limitId + " not found"));
        String prevBand = l.getBand();
        l.setUtilisedSarM(req.getUtilisedSarM());
        l.setUtilisationPct(computeUtilisationPct(l.getLimitSarM(), req.getUtilisedSarM()));
        l.setBand(computeBand(l.getUtilisationPct()));
        l.setUtilisationRefreshedAt(req.getRefreshedAt());
        LargeExposureLimit saved = limitRepo.save(l);

        if (!saved.getBand().equals(prevBand)) {
            if (saved.getCounterpartyId() != null) {
                timeline(saved.getCounterpartyId(), "LIMIT_BAND_CHANGED",
                        null, null, saved.getId(),
                        "Limit band " + prevBand + " → " + saved.getBand()
                                + " (utilisation " + saved.getUtilisationPct() + "%)"
                                + (req.getNoteEn() == null ? "" : " — " + req.getNoteEn()),
                        "تغيير نطاق الحدّ من " + prevBand + " إلى " + saved.getBand()
                                + " (الاستخدام " + saved.getUtilisationPct() + "%)"
                                + (req.getNoteAr() == null ? "" : " — " + req.getNoteAr()),
                        prevBand, saved.getBand());
            }
            maybeFireBandEvent(saved, prevBand);
        }
        return toLimit(saved);
    }

    private void maybeFireBandEvent(LargeExposureLimit l, String prevBand) {
        if (l.getBand().equals(prevBand)) return;
        String trigger = switch (l.getBand()) {
            case "AMBER"  -> "LARGE_EXPOSURE_LIMIT_AMBER";
            case "RED"    -> "LARGE_EXPOSURE_LIMIT_RED";
            case "BREACH" -> "LARGE_EXPOSURE_LIMIT_BREACH";
            default       -> null;
        };
        if (trigger == null) return;
        boolean worsening = bandRank(l.getBand()) > bandRank(prevBand);
        audit.log(trigger, "LargeExposureLimit", l.getId(),
                l.getDisplayNameEn() + " " + prevBand + " → " + l.getBand()
                        + " (" + l.getUtilisationPct() + "%)");
        Map<String, Object> payload = new HashMap<>();
        payload.put("scope", l.getScope());
        payload.put("displayName", l.getDisplayNameEn());
        payload.put("bandBefore", prevBand == null ? "" : prevBand);
        payload.put("bandAfter", l.getBand());
        payload.put("worsening", String.valueOf(worsening));
        payload.put("utilisationPct", l.getUtilisationPct().toPlainString());
        payload.put("limitSarM", l.getLimitSarM().toPlainString());
        if (l.getCounterpartyId() != null)
            payload.put("counterpartyId", String.valueOf(l.getCounterpartyId()));
        if (l.getGroupReference() != null)
            payload.put("groupReference", l.getGroupReference());
        emit(trigger, "LargeExposureLimit", l.getId(),
                l.getDisplayNameEn(), payload);
    }

    // ─── Reads ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CounterpartyResponse> listCounterparties(int page, int size) {
        return cptyRepo.findByOrganisationIdOrderByReferenceAsc(
                org(), PageRequest.of(page, size))
                .map(this::toCounterparty);
    }

    @Transactional(readOnly = true)
    public CounterpartyResponse getCounterparty(Long id) {
        return toCounterparty(load(id));
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> listRatings(Long counterpartyId) {
        load(counterpartyId);
        return ratingRepo.findByCounterpartyIdAndOrganisationIdOrderByEffectiveDateDesc(
                        counterpartyId, org()).stream()
                .map(CptyService::toRating).toList();
    }

    @Transactional(readOnly = true)
    public List<RatingActionResponse> listActions(Long counterpartyId) {
        load(counterpartyId);
        return actionRepo.findByCounterpartyIdAndOrganisationIdOrderByActionDateDesc(
                        counterpartyId, org()).stream()
                .map(CptyService::toAction).toList();
    }

    @Transactional(readOnly = true)
    public List<LimitResponse> listLimits() {
        return limitRepo.findByOrganisationIdOrderByUtilisationPctDesc(org()).stream()
                .map(CptyService::toLimit).toList();
    }

    @Transactional(readOnly = true)
    public List<LimitResponse> listLimitsForCounterparty(Long counterpartyId) {
        load(counterpartyId);
        return limitRepo.findByCounterpartyIdAndOrganisationId(counterpartyId, org())
                .stream().map(CptyService::toLimit).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Long counterpartyId) {
        load(counterpartyId);
        return eventRepo.findByCounterpartyIdAndOrganisationIdOrderByOccurredAtDesc(
                        counterpartyId, org()).stream()
                .map(CptyService::toEvent).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long total = cptyRepo.countByOrganisationId(org());
        long active = cptyRepo.countByOrganisationIdAndStatus(org(), "ACTIVE");
        long watch = cptyRepo.countByOrganisationIdAndStatus(org(), "WATCHLIST");
        long def = cptyRepo.countByOrganisationIdAndStatus(org(), "DEFAULT");
        long breach = limitRepo.countByOrganisationIdAndBand(org(), "BREACH");
        long red = limitRepo.countByOrganisationIdAndBand(org(), "RED");
        long amber = limitRepo.countByOrganisationIdAndBand(org(), "AMBER");

        // F2-352 — aggregate over ALL counterparties, not a capped page. This block previously read
        // findByOrganisationIdOrderByReferenceAsc(org(), PageRequest.of(0, 50)), so on a tenant with
        // more than 50 counterparties the SAMA total exposure and the sector/grade concentration
        // breakdowns were computed over the first 50 by reference only — understating exposure (the
        // flattering direction) beside an all-time totalCounterparties count (F2-197 / F2-345: a
        // capped list is fine to render, wrong to aggregate). The grouping arithmetic is
        // byte-identical; only the source is now uncapped.
        List<Counterparty> all = cptyRepo.findByOrganisationIdOrderByReferenceAsc(org());
        Map<String, Long> bySector = new LinkedHashMap<>();
        Map<String, Long> byGrade = new LinkedHashMap<>();
        BigDecimal totalExp = BigDecimal.ZERO;
        for (Counterparty c : all) {
            bySector.merge(c.getSector(), 1L, Long::sum);
            if (c.getCurrentInternalGrade() != null) {
                byGrade.merge(c.getCurrentInternalGrade(), 1L, Long::sum);
            }
            if (c.getTotalExposureSarM() != null) {
                totalExp = totalExp.add(c.getTotalExposureSarM());
            }
        }

        List<LimitResponse> top = limitRepo.findByOrganisationIdOrderByUtilisationPctDesc(
                        org()).stream()
                .limit(10).map(CptyService::toLimit).toList();

        return DashboardResponse.builder()
                .totalCounterparties(total)
                .activeCount(active)
                .watchlistCount(watch)
                .defaultCount(def)
                .limitsBreach(breach)
                .limitsRed(red)
                .limitsAmber(amber)
                .totalExposureSarM(totalExp)
                .bySector(bySector)
                .byInternalGrade(byGrade)
                .recentCounterparties(all.stream().limit(10).map(this::toCounterparty).toList())
                .topUtilisedLimits(top)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private Counterparty load(Long id) {
        return cptyRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Counterparty " + id + " not found in tenant"));
    }

    /**
     * F2-353 — mint {@code CP-NNNNN} from {@code MAX(existing ordinal) + 1}, not
     * {@code count + 1}. A derived {@code countByOrganisationId} respects the entity's
     * {@code @Where(is_deleted = 0)}, so a soft-delete gap or a concurrent create re-minted a live
     * ordinal, which the filtered unique index {@code ux_cpty_reference} then 500'd on the INSERT
     * (the F2-337 / F2-351 collision shape). A MAX-over-all seed never re-issues a used ordinal; the
     * skip-loop absorbs the residual concurrency race (a peer create grabbing the same ordinal in the
     * window between the seed read and this INSERT), backstopped by the unique index and, after 1000
     * live collisions, a random suffix. Mirrors {@code ComplaintService#generateNextComplaintNo}.
     */
    private String generateReference() {
        Long org = org();
        int next = cptyRepo.findMaxReferenceSeq(org) + 1;
        for (int seq = next; seq < next + 1000; seq++) {
            String candidate = String.format(Locale.ROOT, "CP-%05d", seq);
            if (!cptyRepo.existsByOrganisationIdAndReference(org, candidate)) {
                return candidate;
            }
        }
        return String.format(Locale.ROOT, "CP-%05d-%s", next, UUID.randomUUID().toString().substring(0, 6));
    }

    static BigDecimal computeUtilisationPct(BigDecimal limit, BigDecimal utilised) {
        if (limit == null || utilised == null
                || limit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return utilised.multiply(BigDecimal.valueOf(100))
                .divide(limit, 4, RoundingMode.HALF_UP);
    }

    static String computeBand(BigDecimal utilisationPct) {
        if (utilisationPct == null) return "GREEN";
        double v = utilisationPct.doubleValue();
        if (v > 100) return "BREACH";
        if (v > 90) return "RED";
        if (v > 70) return "AMBER";
        return "GREEN";
    }

    private static int bandRank(String band) {
        return switch (band == null ? "" : band) {
            case "BREACH" -> 3;
            case "RED"    -> 2;
            case "AMBER"  -> 1;
            default       -> 0;
        };
    }

    private void timeline(Long cptyId, String eventType, Long linkedRatingId,
                           Long linkedActionId, Long linkedLimitId,
                           String noteEn, String noteAr) {
        timeline(cptyId, eventType, linkedRatingId, linkedActionId, linkedLimitId,
                noteEn, noteAr, null, null);
    }

    private void timeline(Long cptyId, String eventType, Long linkedRatingId,
                           Long linkedActionId, Long linkedLimitId,
                           String noteEn, String noteAr,
                           String bandBefore, String bandAfter) {
        CptyEvent e = new CptyEvent();
        e.setOrganisationId(org());
        e.setCounterpartyId(cptyId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setActorName(currentActorName());
        e.setLinkedRatingId(linkedRatingId);
        e.setLinkedActionId(linkedActionId);
        e.setLinkedLimitId(linkedLimitId);
        e.setBandBefore(bandBefore);
        e.setBandAfter(bandAfter);
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

    // ─── DTO mappers ───────────────────────────────────────────────

    private CounterpartyResponse toCounterparty(Counterparty c) {
        return CounterpartyResponse.builder()
                .id(c.getId()).reference(c.getReference()).lei(c.getLei())
                .legalNameEn(c.getLegalNameEn()).legalNameAr(c.getLegalNameAr())
                .shortName(c.getShortName()).sector(c.getSector())
                .country(c.getCountry()).industry(c.getIndustry())
                .parentGroupReference(c.getParentGroupReference())
                .greFlag(c.getGreFlag()).sovereignFlag(c.getSovereignFlag())
                .fiFlag(c.getFiFlag()).status(c.getStatus())
                .relationshipManagerUserId(c.getRelationshipManagerUserId())
                .creditOfficerUserId(c.getCreditOfficerUserId())
                .kycRefreshedAt(c.getKycRefreshedAt()).onboardedAt(c.getOnboardedAt())
                .totalExposureSarM(c.getTotalExposureSarM())
                .currentInternalGrade(c.getCurrentInternalGrade())
                .currentExternalRating(c.getCurrentExternalRating())
                .currentExternalAgency(c.getCurrentExternalAgency())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .build();
    }

    private static RatingResponse toRating(CreditRating r) {
        return RatingResponse.builder()
                .id(r.getId()).counterpartyId(r.getCounterpartyId())
                .ratingType(r.getRatingType()).effectiveDate(r.getEffectiveDate())
                .grade(r.getGrade()).scale(r.getScale()).agency(r.getAgency())
                .pd(r.getPd()).lgd(r.getLgd()).eadSarM(r.getEadSarM())
                .outlook(r.getOutlook())
                .rationaleEn(r.getRationaleEn()).rationaleAr(r.getRationaleAr())
                .assignedByUserId(r.getAssignedByUserId())
                .approvedByUserId(r.getApprovedByUserId())
                .build();
    }

    private static RatingActionResponse toAction(RatingAction a) {
        return RatingActionResponse.builder()
                .id(a.getId()).counterpartyId(a.getCounterpartyId())
                .actionType(a.getActionType()).actionDate(a.getActionDate())
                .gradeBefore(a.getGradeBefore()).gradeAfter(a.getGradeAfter())
                .outlookBefore(a.getOutlookBefore()).outlookAfter(a.getOutlookAfter())
                .triggerSource(a.getTriggerSource())
                .rationaleEn(a.getRationaleEn()).rationaleAr(a.getRationaleAr())
                .actorUserId(a.getActorUserId())
                .build();
    }

    private static LimitResponse toLimit(LargeExposureLimit l) {
        return LimitResponse.builder()
                .id(l.getId()).scope(l.getScope())
                .counterpartyId(l.getCounterpartyId())
                .groupReference(l.getGroupReference())
                .displayNameEn(l.getDisplayNameEn()).displayNameAr(l.getDisplayNameAr())
                .limitSarM(l.getLimitSarM()).utilisedSarM(l.getUtilisedSarM())
                .utilisationPct(l.getUtilisationPct()).band(l.getBand())
                .approvedAt(l.getApprovedAt()).nextReviewAt(l.getNextReviewAt())
                .approvedByUserId(l.getApprovedByUserId())
                .utilisationRefreshedAt(l.getUtilisationRefreshedAt())
                .build();
    }

    private static EventResponse toEvent(CptyEvent e) {
        return EventResponse.builder()
                .id(e.getId()).counterpartyId(e.getCounterpartyId())
                .occurredAt(e.getOccurredAt()).eventType(e.getEventType())
                .actorUserId(e.getActorUserId()).actorName(e.getActorName())
                .linkedRatingId(e.getLinkedRatingId())
                .linkedActionId(e.getLinkedActionId())
                .linkedLimitId(e.getLinkedLimitId())
                .bandBefore(e.getBandBefore()).bandAfter(e.getBandAfter())
                .noteEn(e.getNoteEn()).noteAr(e.getNoteAr())
                .build();
    }
}
