package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberSkill;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberSkillRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Member-registry service: appoint members, end terms, renew terms,
 * maintain the skills matrix.
 *
 * <p>Independence-ratio is computed on the fly so the committee detail
 * page can show "5/7 independent (71%)" against the CMA Article 54
 * threshold ("majority of members must be independent" for Audit
 * Committee).
 *
 * <p>F8 events:
 * <ul>
 *   <li>{@code COMMITTEE_MEMBER_APPOINTED} — fires AR-156 (welcome packet,
 *       skills-matrix prompt)</li>
 *   <li>{@code COMMITTEE_MEMBER_TERM_ENDED} — fires AR-157 (offboarding
 *       checklist, succession reminder)</li>
 *   <li>{@code COMMITTEE_TERM_EXPIRING_60D} — fired by sweep when
 *       termEndsAt within 60 days</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CommitteeMemberRegistryService {

    private final CommitteeMemberTermRepository  terms;
    private final CommitteeMemberSkillRepository skills;
    private final AuditLogService                audit;
    private final ApplicationEventPublisher      publisher;

    // ── Member terms ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommitteeMemberTerm> listTerms(Long committeeId) {
        return terms.findByOrganisationIdAndCommitteeIdOrderByAppointedAtDesc(
            TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberTerm> listActiveMembers(Long committeeId) {
        return terms.findActiveByCommittee(
            TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public CommitteeMemberTerm getTerm(Long id) {
        return terms.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeMemberTerm " + id));
    }

    /** Appoint a member. Creates a new term row with {@code renewalStatus=ACTIVE}. */
    @Transactional
    public CommitteeMemberTerm appoint(CommitteeMemberTerm in) {
        // Mass-assignment defense — controller accepts the entity directly
        // via @RequestBody. Null id forces save() into INSERT instead of
        // merge() which could overwrite a cross-tenant row.
        in.setId(null);
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        if (in.getRenewalStatus() == null) in.setRenewalStatus("ACTIVE");
        if (in.getTermNumber() == null) in.setTermNumber(1);
        CommitteeMemberTerm saved = terms.save(in);

        audit.log("MEMBER_APPOINT", "CommitteeMemberTerm", saved.getId(),
            saved.getMemberName() + " · " + saved.getRole() + " · committee=" + saved.getCommitteeId()
                + " · term#" + saved.getTermNumber());

        publisher.publishEvent(DomainEvent.of(
            org, "COMMITTEE_MEMBER_APPOINTED", "CommitteeMemberTerm", saved.getId(),
            payload(saved)));

        return saved;
    }

    /** End a term — sets {@code renewalStatus} (RESIGNED/REMOVED/EXPIRED)
     *  and records the reason. */
    @Transactional
    public CommitteeMemberTerm endTerm(Long id, String renewalStatus, String reason) {
        CommitteeMemberTerm t = getTerm(id);
        String prev = t.getRenewalStatus();
        if (!"ACTIVE".equals(prev)) {
            throw new IllegalStateException("Cannot end term from status " + prev);
        }
        if (!List.of("EXPIRED", "RESIGNED", "REMOVED", "RENEWED").contains(renewalStatus)) {
            throw new IllegalArgumentException("renewalStatus must be EXPIRED|RESIGNED|REMOVED|RENEWED");
        }
        t.setRenewalStatus(renewalStatus);
        t.setEndReason(reason);
        t.setEndedAt(LocalDateTime.now());
        CommitteeMemberTerm saved = terms.save(t);

        audit.log("MEMBER_TERM_END", "CommitteeMemberTerm", id,
            "Status ACTIVE → " + renewalStatus + " · reason=" + (reason == null ? "—" : reason));

        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "COMMITTEE_MEMBER_TERM_ENDED",
            "CommitteeMemberTerm", saved.getId(),
            payload(saved, Map.of("renewalStatus", renewalStatus,
                                  "endReason",     reason == null ? "" : reason))));

        return saved;
    }

    /** Renew a term — closes the current row as RENEWED and creates a
     *  new ACTIVE row with {@code termNumber + 1} and the new dates. */
    @Transactional
    public CommitteeMemberTerm renew(Long oldTermId, LocalDate newStart, LocalDate newEnd,
                                       String resolutionCode) {
        CommitteeMemberTerm old = getTerm(oldTermId);
        if (!"ACTIVE".equals(old.getRenewalStatus())) {
            throw new IllegalStateException("Cannot renew from status " + old.getRenewalStatus());
        }
        // Close old row.
        old.setRenewalStatus("RENEWED");
        old.setEndedAt(LocalDateTime.now());
        terms.save(old);

        // Create new row.
        CommitteeMemberTerm next = new CommitteeMemberTerm();
        next.setOrganisationId(old.getOrganisationId());
        next.setCommitteeId(old.getCommitteeId());
        next.setMemberName(old.getMemberName());
        next.setMemberUserId(old.getMemberUserId());
        next.setRole(old.getRole());
        next.setIsVotingMember(old.getIsVotingMember());
        next.setIsIndependent(old.getIsIndependent());
        next.setIndependenceBasisEn(old.getIndependenceBasisEn());
        next.setAppointedAt(LocalDate.now());
        next.setAppointedByReso(resolutionCode);
        next.setTermStartsAt(newStart);
        next.setTermEndsAt(newEnd);
        next.setTermNumber(old.getTermNumber() + 1);
        next.setRenewalStatus("ACTIVE");
        next.setCreatedAt(LocalDateTime.now());
        CommitteeMemberTerm saved = terms.save(next);

        audit.log("MEMBER_RENEW", "CommitteeMemberTerm", saved.getId(),
            old.getMemberName() + " · term#" + saved.getTermNumber()
                + " · resolution=" + (resolutionCode == null ? "—" : resolutionCode));

        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "COMMITTEE_MEMBER_RENEWED",
            "CommitteeMemberTerm", saved.getId(),
            payload(saved, Map.of("oldTermId", String.valueOf(oldTermId),
                                  "termNumber", String.valueOf(saved.getTermNumber())))));

        return saved;
    }

    /** Independence stats for a committee: numerator + denominator. */
    @Transactional(readOnly = true)
    public Map<String, Object> independenceStats(Long committeeId) {
        Long org = TenantContext.getCurrentTenant();
        long active = listActiveMembers(committeeId).size();
        long independent = terms.countActiveIndependents(org, committeeId);
        return Map.of(
            "activeMembers",        active,
            "independentMembers",   independent,
            "independenceRatioPct", active == 0 ? 0 : Math.round(100.0 * independent / active));
    }

    // ── Skills matrix ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommitteeMemberSkill> listSkills(Long committeeId) {
        return skills.findByOrganisationIdAndCommitteeIdOrderByMemberNameAsc(
            TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberSkill> listSkillsByDimension(Long committeeId, String dimension) {
        return skills.findByDimension(
            TenantContext.getCurrentTenant(), committeeId, dimension);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> skillCoverage(Long committeeId) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : skills.countByDimension(
                TenantContext.getCurrentTenant(), committeeId)) {
            out.put((String) row[0], ((Number) row[1]).longValue());
        }
        return out;
    }

    @Transactional
    public CommitteeMemberSkill addSkill(CommitteeMemberSkill in) {
        // Mass-assignment defense — see appoint() above.
        in.setId(null);
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        if (in.getLastReviewedAt() == null) {
            in.setLastReviewedAt(LocalDate.now());
        }
        CommitteeMemberSkill saved = skills.save(in);

        audit.log("SKILL_ADD", "CommitteeMemberSkill", saved.getId(),
            saved.getMemberName() + " · " + saved.getSkillDimension() + " · " + saved.getSkillLevel());

        return saved;
    }

    @Transactional
    public void removeSkill(Long id) {
        CommitteeMemberSkill s = skills.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeMemberSkill " + id));
        skills.delete(s);
        audit.log("SKILL_REMOVE", "CommitteeMemberSkill", id,
            s.getMemberName() + " · " + s.getSkillDimension());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Map<String, Object> payload(CommitteeMemberTerm t) {
        return payload(t, Map.of());
    }

    private Map<String, Object> payload(CommitteeMemberTerm t, Map<String, String> extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("memberName",   t.getMemberName() == null ? "" : t.getMemberName());
        m.put("role",         t.getRole() == null ? "" : t.getRole());
        m.put("committeeId",  t.getCommitteeId() == null ? "" : String.valueOf(t.getCommitteeId()));
        m.put("termNumber",   t.getTermNumber() == null ? "" : String.valueOf(t.getTermNumber()));
        m.put("isIndependent", t.getIsIndependent() != null && t.getIsIndependent() == 1 ? "true" : "false");
        m.putAll(extra);
        return m;
    }
}
