package com.tss.grc.module.incident.rca.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.incident.rca.dto.RcaDtos.CreateRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.RejectRequest;
import com.tss.grc.module.incident.rca.dto.RcaDtos.UpdateRequest;
import com.tss.grc.module.incident.rca.entity.RootCauseAnalysis;
import com.tss.grc.module.incident.rca.repository.RootCauseAnalysisRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RCA lifecycle service.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT     ──submit()──▶ IN_REVIEW
 *   IN_REVIEW ──approve()─▶ APPROVED  (terminal)
 *             ──reject()──▶ REJECTED  (terminal)
 * </pre>
 *
 * <p>DRAFT permits free updates via {@link #update}; IN_REVIEW and
 * terminal states do not. Approval stamps the approver identity for
 * the audit trail; rejection stamps the reason.
 */
@Service
@RequiredArgsConstructor
public class RootCauseAnalysisService {

    private final RootCauseAnalysisRepository repo;
    private final CapaTaskService capaService;

    @Autowired(required = false)
    private com.tss.grc.platform.realtime.BroadcastHelper broadcaster;

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RootCauseAnalysis> list(Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public RootCauseAnalysis get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new EntityNotFoundException("RCA " + id));
    }

    @Transactional(readOnly = true)
    public java.util.List<RootCauseAnalysis> forEntity(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                orgId, entityType, entityId);
    }

    // ─── Writes ───────────────────────────────────────────────────────

    @Transactional
    public RootCauseAnalysis create(CreateRequest body, Long createdBy) {
        Long orgId = TenantContext.getCurrentTenant();
        RootCauseAnalysis r = new RootCauseAnalysis();
        r.setOrganisationId(orgId);
        r.setExternalId("RCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setRelatedEntityType(body.relatedEntityType());
        r.setRelatedEntityId(body.relatedEntityId());
        r.setRelatedEntityLabel(body.relatedEntityLabel());
        r.setMethodology(body.methodology() == null ? "FIVE_WHYS" : body.methodology());
        r.setProblemStatementEn(body.problemStatementEn());
        r.setProblemStatementAr(body.problemStatementAr());
        r.setFiveWhysJson(body.fiveWhysJson());
        r.setContributingFactorsJson(body.contributingFactorsJson());
        r.setRootCauseEn(body.rootCauseEn());
        r.setRootCauseAr(body.rootCauseAr());
        r.setRecommendationsEn(body.recommendationsEn());
        r.setRecommendationsAr(body.recommendationsAr());
        r.setFacilitator(body.facilitator());
        r.setParticipants(body.participants());
        r.setConductedAt(body.conductedAt());
        r.setCapaRefs(body.capaRefs());
        r.setCreatedBy(createdBy);
        r.setStatus("DRAFT");

        RootCauseAnalysis saved = repo.save(r);
        broadcast("created", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis update(Long id, UpdateRequest body) {
        RootCauseAnalysis r = get(id);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "DRAFT(edit)", Set.of("DRAFT"));
        }
        if (body.methodology() != null) r.setMethodology(body.methodology());
        if (body.problemStatementEn() != null) r.setProblemStatementEn(body.problemStatementEn());
        if (body.problemStatementAr() != null) r.setProblemStatementAr(body.problemStatementAr());
        if (body.fiveWhysJson() != null) r.setFiveWhysJson(body.fiveWhysJson());
        if (body.contributingFactorsJson() != null) r.setContributingFactorsJson(body.contributingFactorsJson());
        if (body.rootCauseEn() != null) r.setRootCauseEn(body.rootCauseEn());
        if (body.rootCauseAr() != null) r.setRootCauseAr(body.rootCauseAr());
        if (body.recommendationsEn() != null) r.setRecommendationsEn(body.recommendationsEn());
        if (body.recommendationsAr() != null) r.setRecommendationsAr(body.recommendationsAr());
        if (body.facilitator() != null) r.setFacilitator(body.facilitator());
        if (body.participants() != null) r.setParticipants(body.participants());
        if (body.conductedAt() != null) r.setConductedAt(body.conductedAt());
        if (body.capaRefs() != null) r.setCapaRefs(body.capaRefs());

        RootCauseAnalysis saved = repo.save(r);
        broadcast("updated", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis submit(Long id, Long userId) {
        RootCauseAnalysis r = get(id);
        if (!"DRAFT".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "IN_REVIEW", Set.of("DRAFT"));
        }
        r.setStatus("IN_REVIEW");
        r.setSubmittedAt(LocalDateTime.now());
        r.setSubmittedBy(userId);
        RootCauseAnalysis saved = repo.save(r);
        broadcast("submitted", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis approve(Long id, Long userId, String approverName) {
        RootCauseAnalysis r = get(id);
        if (!"IN_REVIEW".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "APPROVED", Set.of("IN_REVIEW"));
        }
        r.setStatus("APPROVED");
        r.setApprovedAt(LocalDateTime.now());
        r.setApprovedBy(userId);
        r.setApproverName(approverName);
        RootCauseAnalysis saved = repo.save(r);
        broadcast("approved", saved);
        return saved;
    }

    @Transactional
    public RootCauseAnalysis reject(Long id, RejectRequest body) {
        RootCauseAnalysis r = get(id);
        if (!"IN_REVIEW".equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), "REJECTED", Set.of("IN_REVIEW"));
        }
        r.setStatus("REJECTED");
        r.setRejectionReason(body.rejectionReason());
        RootCauseAnalysis saved = repo.save(r);
        broadcast("rejected", saved);
        return saved;
    }

    @Transactional
    public void softDelete(Long id) {
        RootCauseAnalysis r = get(id);
        r.setIsDeleted((short) 1);
        r.setDeletedAt(LocalDateTime.now());
        repo.save(r);
    }

    // ─── Cross-module: spawn CAPAs from the RCA's recommendations ─────

    /**
     * Parse the RCA's recommendations (EN) for a numbered list and spawn
     * one {@link com.tss.grc.module.capa.entity.CapaTask CAPA task} per
     * item. The CAPA carries:
     *   originType = "rca"
     *   originId   = RCA external id (e.g. "RCA-A1B2C3D4")
     *
     * <p>Only legal on APPROVED RCAs — an in-flight or rejected RCA must
     * not seed downstream work. The new CAPAs append their external ids
     * to the RCA's {@code capa_refs} column so the UI can show the chain.
     *
     * <p>Idempotency: this method always creates new CAPAs. Calling twice
     * intentionally creates duplicates — the operator owns deduplication
     * before clicking. Most flows will call it exactly once after
     * approval.
     */
    @Transactional
    public List<CapaTaskDto> generateCapasFromRecommendations(Long id, String defaultOwner) {
        RootCauseAnalysis r = get(id);
        if (!"APPROVED".equals(r.getStatus())) {
            throw new IllegalTransitionException(
                    r.getStatus(), "generate-capas", Set.of("APPROVED"));
        }
        if (r.getRecommendationsEn() == null || r.getRecommendationsEn().isBlank()) {
            return List.of();
        }

        List<String> items = parseRecommendations(r.getRecommendationsEn());
        if (items.isEmpty()) return List.of();

        String originId = r.getExternalId() != null ? r.getExternalId() : ("RCA-" + r.getId());
        LocalDate dueDate = LocalDate.now().plusDays(60); // default 60-day SLA
        String owner = (defaultOwner == null || defaultOwner.isBlank())
                ? (r.getFacilitator() != null ? r.getFacilitator() : "CISO Office")
                : defaultOwner;

        List<CapaTaskDto> created = new ArrayList<>();
        // Pre-allocate unique CAPA external ids ourselves. CapaTaskService's
        // built-in id generator collides on tight loops (millis-truncated to
        // 6 hex chars), and this code path is exactly that — N rapid
        // creates in the same transaction. Set CapaTaskDto.id and the
        // downstream service will respect it.
        int seq = 0;
        long base = System.currentTimeMillis();
        for (String item : items) {
            String externalId = String.format("CAPA-%d-%s-%02d",
                    LocalDate.now().getYear(),
                    Long.toHexString(base + seq).toUpperCase(),
                    seq);
            seq++;
            CapaTaskDto dto = CapaTaskDto.builder()
                    .id(externalId)
                    .originType("rca")
                    .originId(originId)
                    .titleEn(truncate(item, 200))
                    .titleAr(truncate(item, 200)) // fallback — facilitator can edit
                    .descriptionEn(item)
                    .capaType("CORRECTIVE")
                    .severity("MEDIUM")
                    .owner(owner)
                    .targetDate(dueDate)
                    .status("OPEN")
                    .build();
            created.add(capaService.create(dto));
        }

        // Append the new CAPA refs to the RCA so the UI shows the chain.
        String newRefs = created.stream().map(CapaTaskDto::getId).collect(java.util.stream.Collectors.joining(","));
        String existing = r.getCapaRefs();
        r.setCapaRefs(existing == null || existing.isBlank() ? newRefs : existing + "," + newRefs);
        repo.save(r);

        broadcast("capas-generated", r);
        return created;
    }

    /**
     * Split a recommendations block into individual action items.
     * <p>Supports two formats:
     * <ol>
     *   <li>Numbered prefix: {@code "1) foo. 2) bar. 3) baz."} — most common
     *       output from the RCA UI</li>
     *   <li>Newline-separated lines — fallback</li>
     * </ol>
     * Items shorter than 8 characters are dropped as noise.
     */
    static List<String> parseRecommendations(String text) {
        if (text == null) return List.of();
        // Split on numbered list markers like "1) " or "1. " or "1- " at start of token.
        // Pattern: number + ) . - followed by space, anywhere in the string.
        Pattern p = Pattern.compile("(?:^|\\s)(\\d{1,2}[\\)\\.\\-]\\s+)");
        Matcher m = p.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) starts.add(m.start() == 0 ? 0 : m.start() + 1);

        List<String> items;
        if (starts.size() >= 2) {
            items = new ArrayList<>();
            for (int i = 0; i < starts.size(); i++) {
                int s = starts.get(i);
                int e = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
                String raw = text.substring(s, e).trim();
                // Strip the leading numeric prefix.
                raw = raw.replaceFirst("^\\d{1,2}[\\)\\.\\-]\\s+", "").trim();
                if (raw.length() >= 8) items.add(raw);
            }
        } else {
            // Fallback: newline split.
            items = Arrays.stream(text.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> s.length() >= 8)
                    .collect(java.util.stream.Collectors.toList());
        }
        return items;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private void broadcast(String op, RootCauseAnalysis r) {
        if (broadcaster == null) return;
        broadcaster.entity(
                r.getOrganisationId(),
                "root_cause_analysis",
                r.getId(),
                java.util.Map.of("op", op, "status", r.getStatus()));
    }
}
