package com.tss.grc.module.sust.climate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.entity.ClimateReport;
import com.tss.grc.module.sust.climate.repository.ClimateReportRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Persists, approval-gates, and content-locks the IFRS S2 / TCFD annual report
 * (S-G). {@link #generate} snapshots the full assembled pack (see
 * {@link TcfdReportService}) as JSON into a DRAFT {@link ClimateReport};
 * {@code DRAFT → IN_REVIEW → APPROVED → PUBLISHED}. On APPROVE the content is
 * SHA-256 hashed and the row is locked (approver ≠ generator — separation of
 * duties). Re-generating a year opens a fresh version rather than mutating an
 * approved snapshot.
 */
@Service
@RequiredArgsConstructor
public class ClimateReportService {

    private final TcfdReportService reportService;
    private final ClimateReportRepository repo;
    private final ObjectMapper objectMapper;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ClimateReport> list() {
        return repo.findByOrganisationIdOrderByReportingYearDescVersionDesc(org());
    }

    @Transactional(readOnly = true)
    public ClimateReport get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("Climate report " + id));
    }

    // ── Generate (snapshot) ────────────────────────────────────────────
    /**
     * Assemble the complete pack for the year and persist it as a new DRAFT
     * version. Never touches an existing (possibly APPROVED/locked) row —
     * regeneration bumps the version.
     */
    @Transactional
    public ClimateReport generate(int year, GrcUserDetails me) {
        Long orgId = org();
        AnnualClimateReport pack = reportService.generateAnnualReportData(year);
        String json;
        try {
            json = objectMapper.writeValueAsString(pack);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise climate report content", e);
        }

        int nextVersion = repo.findFirstByOrganisationIdAndReportingYearOrderByVersionDesc(orgId, year)
                .map(r -> r.getVersion() + 1).orElse(1);

        ClimateReport r = new ClimateReport();
        r.setOrganisationId(orgId);
        r.setReportingYear(year);
        r.setVersion(nextVersion);
        r.setReportCode("CLIM-" + year + "-v" + nextVersion);
        r.setTitleEn("IFRS S2 / TCFD Climate Report " + year);
        r.setTitleAr("تقرير المناخ IFRS S2 / TCFD لعام " + year);
        r.setStatus("DRAFT");
        r.setContentJson(json);
        r.setGeneratedAt(LocalDateTime.now());
        r.setGeneratedBy(me == null ? null : me.getUsername());
        r.setIsLocked(false);
        ClimateReport saved = repo.save(r);
        audit.log("CREATE", "ClimateReport", saved.getId(),
                "Generated IFRS S2/TCFD report " + saved.getReportCode() + " (DRAFT)");
        return saved;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────
    @Transactional
    public ClimateReport submit(Long id) {
        ClimateReport r = get(id);
        requireStatus(r, "DRAFT", "IN_REVIEW");
        r.setStatus("IN_REVIEW");
        ClimateReport saved = repo.save(r);
        audit.log("TRANSITION", "ClimateReport", saved.getId(), "Climate report " + saved.getReportCode() + " → IN_REVIEW");
        return saved;
    }

    @Transactional
    public ClimateReport approve(Long id, GrcUserDetails me) {
        ClimateReport r = get(id);
        requireStatus(r, "IN_REVIEW", "APPROVED");
        // Separation of duties — the generator cannot approve their own report.
        String approver = me == null ? null : me.getUsername();
        if (approver != null && Objects.equals(approver, r.getGeneratedBy())) {
            throw new IllegalArgumentException(
                    "Separation of duties: the report generator (" + approver + ") cannot approve it — a different reviewer must.");
        }
        r.setStatus("APPROVED");
        r.setApprovedBy(approver);
        r.setApprovedAt(LocalDateTime.now());
        r.setContentHash(sha256(r.getContentJson()));   // freeze + hash the content
        r.setIsLocked(true);
        ClimateReport saved = repo.save(r);
        audit.log("APPROVE", "ClimateReport", saved.getId(),
                "Climate report " + saved.getReportCode() + " APPROVED + locked (sha256=" + saved.getContentHash() + ")");
        return saved;
    }

    @Transactional
    public ClimateReport publish(Long id) {
        ClimateReport r = get(id);
        requireStatus(r, "APPROVED", "PUBLISHED");
        r.setStatus("PUBLISHED");
        r.setPublishedAt(LocalDateTime.now());
        ClimateReport saved = repo.save(r);
        audit.log("TRANSITION", "ClimateReport", saved.getId(), "Climate report " + saved.getReportCode() + " → PUBLISHED");
        return saved;
    }

    // ── Helpers ────────────────────────────────────────────────────────
    /** Linear DRAFT → IN_REVIEW → APPROVED → PUBLISHED; {@code from} must be the state before {@code to}. */
    private void requireStatus(ClimateReport r, String expectedFrom, String to) {
        if (!expectedFrom.equals(r.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), to, java.util.Set.of(expectedFrom));
        }
    }

    static String sha256(String s) {
        if (s == null) return null;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
