package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F2-31 — seals and verifies the {@code platform.audit_log} hash chain.
 *
 * <p>Read {@link AuditChainHasher}'s class javadoc first: it states exactly which threats this
 * control detects and which it does not, and that boundary is the point of the finding.
 *
 * <h3>Why sealing is a pass, not an insert-time write</h3>
 *
 * Hash-on-insert is the obvious design and it is wrong here. Audit rows are written on every
 * mutation across 57 modules, concurrently; two inserts racing would each read the same predecessor
 * and produce two rows claiming the same {@code prev_hash} — a FORK. Verification would then report
 * ordinary concurrency as tampering, and an integrity alarm that cries wolf on normal traffic is
 * worse than the gap it closes. A single-threaded periodic pass orders the rows by {@code id} and
 * links them once, at the cost of a sealing window that is stated rather than hidden.
 *
 * <h3>⚠ The sealer assumes ONE instance</h3>
 *
 * Two application instances sealing the same tenant will both see the same {@code rowHash IS NULL}
 * rows and the same predecessor, and can write two different link chains over them — a FORK, which
 * verification then reports as {@code LINK_BROKEN} on a ledger nobody attacked. Today's deployment
 * runs a single WinSW service so this cannot happen in production, and it is stated rather than
 * assumed: <b>horizontal scaling requires a shared lock around {@link #sealTenant} before it is
 * safe.</b>
 *
 * <p>This is not theoretical. It was observed in development: a second backend from another session
 * shared this working tree's {@code target/classes}, so a {@code mvn test} recompile made Spring
 * DevTools hot-restart it into this class before it was finished — and it sealed 5,000 rows with no
 * integrity key configured, into the same database. The isolation that mattered was not the port.
 *
 * <h3>Chains are per tenant</h3>
 *
 * One chain per {@code organisation_id}. A global chain would detect slightly more (cross-tenant
 * deletion) at the cost of making every verification a cross-tenant read, which contradicts the
 * platform's own isolation rule. Per-tenant keeps the house rule and still detects modification,
 * mid-chain deletion and insertion within the tenant whose evidence is being examined.
 */
@Service
@RequiredArgsConstructor
public class AuditChainService {

    private static final Logger log = LoggerFactory.getLogger(AuditChainService.class);

    /** Cap per pass so a long-unsealed backlog cannot hold a transaction open indefinitely. */
    private static final int SEAL_BATCH = 5_000;

    private final AuditLogRepository repo;
    private final AuditChainHasher hasher;

    @Value("${grc.audit.sealing-enabled:true}")
    private boolean sealingEnabled;

    // ─── Sealing ──────────────────────────────────────────────────────

    /**
     * Seals every tenant's unsealed rows. Runs on a timer; also callable by an operator so the
     * control can be exercised on demand rather than only observed after the fact.
     */
    @Scheduled(fixedDelayString = "${grc.audit.seal-interval-ms:300000}",
               initialDelayString = "${grc.audit.seal-initial-delay-ms:60000}")
    public void sealAllTenants() {
        if (!sealingEnabled) return;
        int total = 0;
        for (Long org : repo.findOrganisationIdsWithUnsealedRows()) {
            try {
                total += sealTenant(org);
            } catch (RuntimeException ex) {
                // One tenant's failure must not stop the others — and it must be LOUD, because a
                // sealing pass that silently stops is how DF-1/BL-C1 ran broken 48×/day for 8 days.
                log.error("[F2-31] sealing FAILED for org={} — rows stay unsealed: {}",
                    org, ex.getMessage(), ex);
            }
        }
        if (total > 0) log.info("[F2-31] sealed {} audit row(s) with {}", total, hasher.algorithm());
    }

    /** Seals one tenant's chain. Returns the number of rows sealed. */
    @Transactional
    public int sealTenant(Long organisationId) {
        List<AuditLogEntry> pending =
            repo.findUnsealed(organisationId, PageRequest.of(0, SEAL_BATCH));
        if (pending.isEmpty()) return 0;

        // Verification walks sealed rows by id ASC, so sealing MUST append: every pending row's id
        // has to be above every already-sealed one. IDENTITY guarantees that in normal operation —
        // but if a sealed row's integrity columns are ever cleared and re-offered (say, to "re-seal
        // history" after a key rotation), chaining it onto the current head would build a chain whose
        // link order contradicts its id order, and verification would then report LINK_BROKEN for a
        // ledger nobody attacked. Refuse instead of manufacturing that alarm.
        Long lastSealedId = repo.findLastSealedId(organisationId);
        if (lastSealedId != null && pending.get(0).getId() != null
            && pending.get(0).getId() < lastSealedId) {
            log.error("[F2-31] REFUSING to seal org={}: unsealed row id={} precedes the sealed head "
                    + "id={}. Sealing appends only — a re-offered historical row cannot be chained "
                    + "onto the current head without making the chain unwalkable.",
                organisationId, pending.get(0).getId(), lastSealedId);
            return 0;
        }

        String prev = repo.findLastSealedRowHash(organisationId);
        if (prev == null) prev = AuditChainHasher.GENESIS;

        LocalDateTime now = LocalDateTime.now();
        for (AuditLogEntry e : pending) {
            e.setPrevHash(prev);
            e.setHashAlg(hasher.algorithm());
            e.setSealedAt(now);
            e.setRowHash(hasher.hash(e, prev));
            prev = e.getRowHash();
        }
        repo.saveAll(pending);
        return pending.size();
    }

    // ─── Verification ─────────────────────────────────────────────────

    /**
     * Walks one tenant's sealed chain and reports the FIRST break, if any.
     *
     * <p>Three outcomes are kept distinct on purpose, because collapsing them is how an integrity
     * report becomes noise: a content/link <b>break</b> (tampering), an <b>algorithm mismatch</b>
     * (the integrity key was rotated — operational, not an attack), and <b>unsealed</b> rows (newer
     * than the last pass — not yet covered, and not a defect).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyTenant(Long organisationId) {
        List<AuditLogEntry> chain = repo.findSealedByOrganisationOrderById(organisationId);
        long unsealed = repo.countByOrganisationIdAndRowHashIsNull(organisationId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("organisationId", organisationId);
        out.put("algorithm", hasher.algorithm());
        out.put("keyed", hasher.isKeyed());
        out.put("sealedRows", chain.size());
        out.put("unsealedRows", unsealed);

        if (chain.isEmpty()) {
            // ANTI-VACUITY: an empty chain must NOT read "verified". "Nothing to check" and
            // "everything checked out" are different sentences, and only one of them is evidence.
            out.put("status", unsealed > 0 ? "UNSEALED_ONLY" : "EMPTY");
            out.put("verified", false);
            out.put("note", unsealed > 0
                ? unsealed + " row(s) exist but none is sealed yet — nothing has been verified"
                : "no audit rows for this tenant — nothing has been verified");
            return out;
        }

        List<String> algMismatches = new ArrayList<>();
        String prev = AuditChainHasher.GENESIS;
        for (AuditLogEntry e : chain) {
            if (!hasher.algorithm().equals(e.getHashAlg())) {
                algMismatches.add(String.valueOf(e.getId()));
                prev = e.getRowHash();   // cannot re-verify under a different algorithm; keep walking
                continue;
            }
            if (!prev.equals(e.getPrevHash())) {
                return broken(out, e.getId(), "LINK_BROKEN",
                    "row " + e.getId() + " claims prev_hash=" + shorten(e.getPrevHash())
                    + " but the preceding sealed row hashes to " + shorten(prev)
                    + " — a row was deleted, inserted or re-ordered before it", algMismatches);
            }
            String recomputed = hasher.hash(e, prev);
            if (!recomputed.equals(e.getRowHash())) {
                return broken(out, e.getId(), "CONTENT_ALTERED",
                    "row " + e.getId() + " no longer hashes to its stored row_hash — its content "
                    + "was modified after sealing", algMismatches);
            }
            prev = e.getRowHash();
        }

        // `verified` answers "was EVERY row re-checked?"; `tamperDetected` answers "did anything
        // fail?". Conflating them was a real defect in the first cut of this class: with 5,000 of
        // 6,088 rows skipped for an algorithm mismatch it still headlined `verified: true`, which is
        // precisely the "report strength you do not have" failure this programme keeps finding. A
        // row sealed under a key you no longer hold is UNVERIFIABLE — not verified, and not tampered.
        boolean complete = algMismatches.isEmpty();
        out.put("status", complete ? "VERIFIED" : "PARTIALLY_VERIFIED");
        out.put("verified", complete);
        out.put("tamperDetected", false);
        out.put("verifiedRows", chain.size() - algMismatches.size());
        out.put("unverifiableRows", algMismatches.size());
        out.put("headHash", prev);
        if (!complete) {
            // A bounded sample: 5,000 ids in a JSON body is a payload, not an explanation.
            out.put("unverifiableSampleIds", algMismatches.subList(0, Math.min(20, algMismatches.size())));
            out.put("unverifiableIdRange",
                algMismatches.get(0) + ".." + algMismatches.get(algMismatches.size() - 1));
            out.put("note", algMismatches.size() + " of " + chain.size() + " sealed row(s) were "
                + "sealed under a different algorithm and CANNOT be re-verified with the key in force "
                + "— this is what a key rotation looks like, not tampering, and no break was found in "
                + "the " + (chain.size() - algMismatches.size()) + " row(s) that could be checked. "
                + "Their integrity still rests on the previous key; re-sealing them would overwrite "
                + "evidence and is deliberately not offered.");
        }
        // Never let the caller read "VERIFIED" as "the ledger is complete": tail truncation is
        // undetectable from inside the same database, and saying so is part of the answer.
        out.put("limitation", "Detects modification, mid-chain deletion and insertion. Does NOT "
            + "detect truncation of the newest rows, and " + (hasher.isKeyed()
            ? "does not bind an actor holding both the database and the integrity key"
            : "is UNKEYED, so anyone with database write access can recompute this chain"));
        return out;
    }

    private static Map<String, Object> broken(Map<String, Object> out, Long id, String status,
                                              String detail, List<String> algMismatches) {
        out.put("status", status);
        out.put("verified", false);
        out.put("tamperDetected", true);
        out.put("firstBreakAtId", id);
        out.put("note", detail);
        if (!algMismatches.isEmpty()) {
            out.put("unverifiableRows", algMismatches.size());
            out.put("unverifiableIdRange",
                algMismatches.get(0) + ".." + algMismatches.get(algMismatches.size() - 1));
        }
        return out;
    }

    private static String shorten(String h) {
        if (h == null) return "<null>";
        return h.length() <= 12 ? h : h.substring(0, 12) + "…";
    }
}
