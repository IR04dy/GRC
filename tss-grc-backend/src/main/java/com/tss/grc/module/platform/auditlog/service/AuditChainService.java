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
     * <p>Outcomes are kept distinct on purpose, because collapsing them is how an integrity report
     * becomes noise: a content/link <b>break</b> (tampering), an <b>algorithm mismatch</b> (this key
     * cannot re-hash the row), and <b>unsealed</b> rows (newer than the last pass — not yet covered,
     * and not a defect).
     *
     * <h3>⚠ F2-434 — what an "algorithm mismatch" is, and what it is not</h3>
     *
     * This javadoc used to gloss that middle outcome as <i>"the integrity key was rotated —
     * operational, not an attack"</i>. Both halves were wrong, and both in the reassuring direction.
     *
     * <ul>
     *   <li><b>It is not a key rotation.</b> {@code hash_alg} records the algorithm FAMILY, never
     *       which key. Rotating from key A to key B leaves every row reading
     *       {@code HMAC-SHA256}, so those rows are not skipped at all — they are re-hashed with B,
     *       fail, and are reported {@code CONTENT_ALTERED}. What actually reaches the skip is a
     *       change of MODE between keyed and unkeyed. The branch built to make a rotation benign
     *       does not fire on one; a rotation is reported as tampering. Recording a non-secret key
     *       fingerprint alongside the family would separate them, and is filed rather than done
     *       here: it changes the seal's stored format and makes already-sealed keyed rows
     *       permanently unverifiable, which is a decision about existing evidence.</li>
     *   <li><b>It is not "not an attack".</b> The skip condition is {@code hash_alg} — a column in
     *       the same database as the rows it classifies, writable by exactly the actor
     *       {@link AuditChainHasher}'s keyed mode claims to stop ("SQL injection, a stolen
     *       {@code sa} password, a DBA without filesystem access"). Marking a row unverifiable is
     *       cheaper than forging its hash, and needs no key.</li>
     * </ul>
     *
     * <p>So the skip now skips exactly what it must — the content re-hash — and nothing else:
     * a value outside the sealer's own vocabulary is tampering ({@code ALG_UNRECOGNISED}), and the
     * LINK is checked for every sealed row, because comparing two stored strings needs no key.
     * Before that, marking one row unverifiable stopped its {@code prev_hash} being compared with
     * anything, which hid an arbitrary-length deletion immediately before it.
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
        List<String> algRuns = new ArrayList<>();
        String runAlg = null;
        Long runFrom = null;
        Long runTo = null;
        int runLen = 0;

        String prev = AuditChainHasher.GENESIS;
        for (AuditLogEntry e : chain) {
            // Contiguity of hash_alg is REPORTED, never adjudicated. A sealing pass stamps one
            // algorithm over a contiguous id range, so a mode change shows up as a boundary between
            // long runs; a single interior row of its own is what a forged skip looks like. That is
            // a shape an operator can read — and it is not a proof, because a deployment may flip
            // modes repeatedly (the dev ledger has three runs from exactly that). Handing over the
            // measurement is the honest move; manufacturing a verdict from it is not.
            if (!java.util.Objects.equals(runAlg, e.getHashAlg())) {
                if (runAlg != null) algRuns.add(runAlg + "[" + runFrom + ".." + runTo + " x" + runLen + "]");
                runAlg = e.getHashAlg();
                runFrom = e.getId();
                runLen = 0;
            }
            runTo = e.getId();
            runLen++;

            // (1) A value outside the sealer's own two constants cannot have been written by a
            // sealing pass — see AuditChainHasher#isRecognisedAlgorithm. NULL is the cheapest form
            // of it. Before F2-434 this row was skipped as "an algorithm I cannot handle", which
            // is the opposite of what it is.
            if (!AuditChainHasher.isRecognisedAlgorithm(e.getHashAlg())) {
                return broken(out, e.getId(), "ALG_UNRECOGNISED",
                    "row " + e.getId() + " stores hash_alg=" + (e.getHashAlg() == null
                        ? "<null>" : "'" + e.getHashAlg() + "'")
                    + ", which no sealing pass can write — the sealer stamps '"
                    + AuditChainHasher.ALG_KEYED + "' or '" + AuditChainHasher.ALG_UNKEYED
                    + "'. The column was written directly", algMismatches);
            }

            // (2) F2-434: the LINK is checked for EVERY sealed row, including one this key cannot
            // re-hash. It compares two STORED strings — no key, no recomputation — so there was
            // never a reason to skip it, and skipping it is what let a single hash_alg write hide an
            // arbitrary-length deletion: mark the row after the gap unverifiable and its prev_hash
            // was never compared with anything. Proven algorithm-independent on the live ledger:
            // both boundary rows of a real SHA-256 → HMAC-SHA256 → SHA-256 mode change link
            // intact, because sealing writes prev_hash from the predecessor's row_hash whatever
            // sealed either row.
            if (!prev.equals(e.getPrevHash())) {
                return broken(out, e.getId(), "LINK_BROKEN",
                    "row " + e.getId() + " claims prev_hash=" + shorten(e.getPrevHash())
                    + " but the preceding sealed row hashes to " + shorten(prev)
                    + " — a row was deleted, inserted or re-ordered before it", algMismatches);
            }

            // (3) Only the CONTENT re-hash needs the key, so only it is skipped.
            if (!hasher.algorithm().equals(e.getHashAlg())) {
                algMismatches.add(String.valueOf(e.getId()));
                prev = e.getRowHash();
                continue;
            }
            String recomputed = hasher.hash(e, prev);
            if (!recomputed.equals(e.getRowHash())) {
                return broken(out, e.getId(), "CONTENT_ALTERED",
                    "row " + e.getId() + " no longer hashes to its stored row_hash"
                    + (hasher.isKeyed()
                        ? " under the integrity key in force. Its content was modified after"
                          + " sealing — OR the key was ROTATED and this row was sealed under the"
                          + " previous one. hash_alg records the algorithm and not which key, so"
                          + " this control cannot tell those apart: check whether"
                          + " grc.audit.integrity-key changed before treating it as tampering"
                        : " — its content was modified after sealing"), algMismatches);
            }
            prev = e.getRowHash();
        }
        if (runAlg != null) algRuns.add(runAlg + "[" + runFrom + ".." + runTo + " x" + runLen + "]");

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
        out.put("algorithmRuns", algRuns);
        if (!complete) {
            // A bounded sample: 5,000 ids in a JSON body is a payload, not an explanation.
            out.put("unverifiableSampleIds", algMismatches.subList(0, Math.min(20, algMismatches.size())));
            out.put("unverifiableIdRange",
                algMismatches.get(0) + ".." + algMismatches.get(algMismatches.size() - 1));
            // F2-434 — what this sentence used to say, and why it was the wrong sentence:
            //
            //   "this is what a key rotation looks like, not tampering"
            //
            // Two things wrong with it, in the reassuring direction both times. It named a cause
            // this branch cannot have — rotating the key leaves hash_alg='HMAC-SHA256' unchanged, so
            // those rows are NOT skipped, they are re-hashed with the new key and reported
            // CONTENT_ALTERED (see that message). What actually lands here is a change of MODE
            // between keyed and unkeyed. And it asserted the benign reading of a skip whose
            // condition is a COLUMN IN THIS DATABASE — writable by the same actor the keyed mode
            // exists to detect. "Not ruled out" is what the verifier can support; "not tampering"
            // is not, and it is the one line an operator reads to decide whether to investigate.
            out.put("note", algMismatches.size() + " of " + chain.size() + " sealed row(s) record a "
                + "different hash_alg from the '" + hasher.algorithm() + "' in force, so their "
                + "CONTENT cannot be re-hashed. Their link WAS checked and no break was found, here "
                + "or in the " + (chain.size() - algMismatches.size()) + " fully re-verified row(s). "
                + "This is the shape a change of mode between keyed and unkeyed leaves — it is NOT a "
                + "key rotation, which keeps hash_alg and would surface as CONTENT_ALTERED. Tampering "
                + "is NOT RULED OUT for these rows: hash_alg is a column in this database, so an "
                + "actor with write access can mark a row unverifiable rather than forge its hash. "
                + "Read algorithmRuns — a mode change leaves a boundary between long contiguous runs, "
                + "while an isolated interior row is what a forged skip looks like. Re-sealing them "
                + "would overwrite evidence and is deliberately not offered.");
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
