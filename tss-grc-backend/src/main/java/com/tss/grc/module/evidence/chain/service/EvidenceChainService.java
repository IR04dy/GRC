package com.tss.grc.module.evidence.chain.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.chain.dto.ChainDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evidence chain-of-custody verifier (gap-analysis item C).
 *
 * <p>Computes the SHA-256 chain for an evidence item's versions and
 * compares each link's recorded {@code chain_hash_sha256} against the
 * value derived from the previous link + current content hash. If a
 * mismatch is detected the version is marked BROKEN, the parent item
 * inherits the BROKEN status, and the first-break version is captured.
 */
@Service
@RequiredArgsConstructor
public class EvidenceChainService {

    @PersistenceContext private EntityManager em;

    /** List all evidence items with their current chain rollup. */
    @Transactional(readOnly = true)
    public List<ItemSummary> listItems(String statusFilter) {
        Long org = TenantContext.getCurrentTenant();
        String sql =
                "SELECT id, CONVERT(NVARCHAR(64), external_id), title_en, title_ar, " +
                "       current_version_no, chain_version_count, chain_status, " +
                "       chain_last_verified_at, chain_first_break_version " +
                "  FROM evidence.evidence_items " +
                " WHERE organisation_id = :org AND is_deleted = 0" +
                (statusFilter != null ? " AND chain_status = :st" : "") +
                " ORDER BY CASE chain_status WHEN 'BROKEN' THEN 0 WHEN 'UNVERIFIED' THEN 1 ELSE 2 END, " +
                "          chain_last_verified_at DESC";
        var q = em.createNativeQuery(sql).setParameter("org", org);
        if (statusFilter != null) q.setParameter("st", statusFilter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<ItemSummary> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new ItemSummary(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    (String) r[2], (String) r[3],
                    r[4] == null ? null : ((Number) r[4]).intValue(),
                    r[5] == null ? null : ((Number) r[5]).intValue(),
                    (String) r[6],
                    r[7] == null ? null : ((Timestamp) r[7]).toLocalDateTime(),
                    r[8] == null ? null : ((Number) r[8]).intValue()));
        }
        return out;
    }

    /**
     * Verify the chain for a single evidence item: walk all versions in
     * chain_position order, recompute each chain_hash, compare to stored
     * value, mark VALID / BROKEN as appropriate. Returns a full report.
     */
    @Transactional
    public ChainReport verifyChain(Long evidenceId, Long actorUserId) {
        Long org = TenantContext.getCurrentTenant();

        Object[] head = (Object[]) em.createNativeQuery(
                "SELECT id, CONVERT(NVARCHAR(64), external_id), title_en " +
                "  FROM evidence.evidence_items " +
                " WHERE id = :id AND organisation_id = :org AND is_deleted = 0")
                .setParameter("id", evidenceId).setParameter("org", org)
                .getSingleResult();
        Long id      = ((Number) head[0]).longValue();
        String ext   = (String) head[1];
        String title = (String) head[2];

        @SuppressWarnings("unchecked")
        List<Object[]> versions = em.createNativeQuery(
                "SELECT id, version_no, COALESCE(chain_position, version_no), " +
                "       content_hash_sha256, chain_hash_sha256, " +
                "       file_name, file_size, change_summary, snapshotted_at, chain_break_reason " +
                "  FROM evidence.evidence_versions " +
                " WHERE evidence_id = :id " +
                " ORDER BY COALESCE(chain_position, version_no) ASC")
                .setParameter("id", id).getResultList();

        List<VersionLink> outVersions = new ArrayList<>(versions.size());
        String prevChain = null;
        int valid = 0, broken = 0;
        Integer firstBreak = null;

        for (int i = 0; i < versions.size(); i++) {
            Object[] v = versions.get(i);
            Long verId       = ((Number) v[0]).longValue();
            Integer verNo    = ((Number) v[1]).intValue();
            Integer pos      = ((Number) v[2]).intValue();
            String content   = (String) v[3];
            String recorded  = (String) v[4];
            String fileName  = (String) v[5];
            Long fileSize    = v[6] == null ? null : ((Number) v[6]).longValue();
            String change    = (String) v[7];
            LocalDateTime ts = v[8] == null ? null : ((Timestamp) v[8]).toLocalDateTime();
            String existingBreak = (String) v[9];

            String input;
            if (prevChain == null) {
                input = "GENESIS:" + (ext == null ? "" : ext) + ":" + (content == null ? "" : content);
            } else {
                input = prevChain + ":" + (content == null ? "" : content);
            }
            String computed = sha256Hex(input);

            String linkStatus;
            String breakReason = null;
            if (recorded != null && recorded.equalsIgnoreCase(computed)) {
                linkStatus = "VALID"; valid++;
            } else {
                linkStatus = "BROKEN"; broken++;
                if (firstBreak == null) firstBreak = verNo;
                breakReason = existingBreak != null
                        ? existingBreak
                        : "Recomputed chain_hash differs from stored value (recorded=" +
                          shortHash(recorded) + " · computed=" + shortHash(computed) + ").";
            }
            outVersions.add(new VersionLink(
                    verId, verNo, pos, content, recorded, linkStatus, breakReason,
                    ts, fileName, fileSize, change));

            // Update the stored row's chain_status + verified_at
            em.createNativeQuery(
                    "UPDATE evidence.evidence_versions " +
                    "   SET chain_status      = :st, " +
                    "       chain_verified_at = SYSUTCDATETIME(), " +
                    "       chain_verified_by = :uid, " +
                    "       chain_break_reason = COALESCE(chain_break_reason, :br) " +
                    " WHERE id = :id")
                    .setParameter("st", linkStatus)
                    .setParameter("uid", actorUserId)
                    .setParameter("br", breakReason)
                    .setParameter("id", verId)
                    .executeUpdate();

            prevChain = recorded != null ? recorded : computed;
        }

        String rolled = broken == 0 ? "VALID" : "BROKEN";
        em.createNativeQuery(
                "UPDATE evidence.evidence_items " +
                "   SET chain_status              = :st, " +
                "       chain_last_verified_at    = SYSUTCDATETIME(), " +
                "       chain_last_verified_by    = :uid, " +
                "       chain_version_count       = :n, " +
                "       chain_first_break_version = :first " +
                " WHERE id = :id")
                .setParameter("st", rolled)
                .setParameter("uid", actorUserId)
                .setParameter("n", versions.size())
                .setParameter("first", firstBreak)
                .setParameter("id", id)
                .executeUpdate();

        return new ChainReport(
                id, ext, title, rolled, versions.size(), valid, broken,
                firstBreak, LocalDateTime.now(), outVersions);
    }

    @Transactional(readOnly = true)
    public ChainReport readReport(Long evidenceId) {
        // Same as verifyChain but read-only — recompute is exposed via verify().
        Long org = TenantContext.getCurrentTenant();
        Object[] head = (Object[]) em.createNativeQuery(
                "SELECT id, CONVERT(NVARCHAR(64), external_id), title_en, chain_status, " +
                "       chain_version_count, chain_first_break_version, chain_last_verified_at " +
                "  FROM evidence.evidence_items " +
                " WHERE id = :id AND organisation_id = :org AND is_deleted = 0")
                .setParameter("id", evidenceId).setParameter("org", org)
                .getSingleResult();
        Long id        = ((Number) head[0]).longValue();
        String ext     = (String) head[1];
        String title   = (String) head[2];
        String status  = (String) head[3];
        int n          = head[4] == null ? 0 : ((Number) head[4]).intValue();
        Integer firstB = head[5] == null ? null : ((Number) head[5]).intValue();
        LocalDateTime verifiedAt = head[6] == null ? null : ((Timestamp) head[6]).toLocalDateTime();

        @SuppressWarnings("unchecked")
        List<Object[]> versions = em.createNativeQuery(
                "SELECT id, version_no, COALESCE(chain_position, version_no), " +
                "       content_hash_sha256, chain_hash_sha256, chain_status, chain_break_reason, " +
                "       file_name, file_size, change_summary, snapshotted_at " +
                "  FROM evidence.evidence_versions " +
                " WHERE evidence_id = :id " +
                " ORDER BY COALESCE(chain_position, version_no) ASC")
                .setParameter("id", id).getResultList();

        List<VersionLink> links = new ArrayList<>(versions.size());
        int valid = 0, broken = 0;
        for (Object[] v : versions) {
            String st = (String) v[5];
            if ("VALID".equals(st)) valid++; else if ("BROKEN".equals(st)) broken++;
            links.add(new VersionLink(
                    ((Number) v[0]).longValue(),
                    ((Number) v[1]).intValue(),
                    ((Number) v[2]).intValue(),
                    (String) v[3], (String) v[4], st, (String) v[6],
                    v[10] == null ? null : ((Timestamp) v[10]).toLocalDateTime(),
                    (String) v[7],
                    v[8] == null ? null : ((Number) v[8]).longValue(),
                    (String) v[9]));
        }
        return new ChainReport(id, ext, title, status, versions.size(), valid, broken,
                firstB, verifiedAt, links);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String shortHash(String h) {
        if (h == null || h.length() < 12) return String.valueOf(h);
        return h.substring(0, 8) + "…" + h.substring(h.length() - 4);
    }
}
