package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F2-31 — the audit ledger must be able to show that it has not been edited.
 *
 * <p>The ledger this platform sells as SAMA/NCA/CMA evidence had no tamper-evidence of its own: no
 * chaining, no WORM, no protection from administrative deletion — while F2-21 had already shown it
 * would faithfully corroborate a forgery.
 *
 * <p>These tests assert the <b>biconditional</b>, which is the part that is easy to get wrong: an
 * intact chain must verify, AND each of the three real attacks must fail verification <em>at the
 * right row</em>. A test that only proves the happy path proves that hashing runs, not that it
 * detects anything. Two further cases guard the ways this control could LIE: an empty chain must not
 * read "verified", and a key rotation must not read as tampering.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditChainServiceTest {

    private static final Long ORG = 1L;
    private static final String KEY = "audit-chain-test-key-0123456789-abcdefghij";

    @Mock private AuditLogRepository repo;

    private AuditChainService serviceWith(AuditChainHasher hasher, List<AuditLogEntry> sealed,
                                          List<AuditLogEntry> unsealed) {
        AuditChainService s = new AuditChainService(repo, hasher);
        ReflectionTestUtils.setField(s, "sealingEnabled", true);
        when(repo.findSealedByOrganisationOrderById(eq(ORG))).thenReturn(sealed);
        when(repo.countByOrganisationIdAndRowHashIsNull(eq(ORG))).thenReturn((long) unsealed.size());
        when(repo.findUnsealed(eq(ORG), any())).thenReturn(unsealed);
        when(repo.findLastSealedRowHash(eq(ORG)))
            .thenReturn(sealed.isEmpty() ? null : sealed.get(sealed.size() - 1).getRowHash());
        when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        return s;
    }

    private static AuditLogEntry row(long id, String action, String message) {
        AuditLogEntry e = new AuditLogEntry();
        e.setId(id);
        e.setOrganisationId(ORG);
        e.setExternalId(UUID.nameUUIDFromBytes(("row-" + id).getBytes()));
        e.setActionType(action);
        e.setMessage(message);
        e.setUserUsername("compliance");
        e.setUserRole("GRC_ADMINISTRATOR");
        e.setUserRoleSource(AuditLogService.SRC_RESOLVED);
        e.setSeverity("INFO");
        e.setCreatedAt(LocalDateTime.of(2026, 8, 1, 12, 0, (int) (id % 60)));
        return e;
    }

    /** Seal a fresh chain of three rows and hand back the sealed list. */
    private List<AuditLogEntry> sealedChain(AuditChainHasher hasher) {
        List<AuditLogEntry> rows = new ArrayList<>(List.of(
            row(1, "CREATE", "control created"),
            row(2, "UPDATE", "control updated"),
            row(3, "DELETE", "control retired")));
        AuditChainService s = serviceWith(hasher, List.of(), rows);
        int n = s.sealTenant(ORG);
        assertThat(n).as("the seal pass must actually seal, or every assertion below is vacuous")
            .isEqualTo(3);
        return rows;
    }

    private static AuditChainHasher keyed()   { return new AuditChainHasher(KEY); }
    private static AuditChainHasher unkeyed() { return new AuditChainHasher(""); }

    // ── The chain links, and it verifies ─────────────────────────────────────────────────────

    @Test void sealingLinksEachRowToItsPredecessor() {
        List<AuditLogEntry> chain = sealedChain(keyed());

        assertThat(chain.get(0).getPrevHash()).isEqualTo(AuditChainHasher.GENESIS);
        assertThat(chain.get(1).getPrevHash()).isEqualTo(chain.get(0).getRowHash());
        assertThat(chain.get(2).getPrevHash()).isEqualTo(chain.get(1).getRowHash());
        assertThat(chain).allSatisfy(e -> {
            assertThat(e.getRowHash()).hasSize(64);
            assertThat(e.getSealedAt()).isNotNull();
            assertThat(e.getHashAlg()).isEqualTo(AuditChainHasher.ALG_KEYED);
        });
    }

    @Test void anIntactChainVerifies() {
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "VERIFIED").containsEntry("verified", true)
            .containsEntry("sealedRows", 3).containsEntry("keyed", true);
        assertThat((String) r.get("limitation"))
            .as("a VERIFIED verdict must carry what it did NOT check, or it misleads an examiner")
            .contains("truncation");
    }

    // ── The three attacks. Each must fail, and name the row. ─────────────────────────────────

    @Test void modifyingASealedRowIsDetected_andTheRowIsNamed() {
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);

        // The forgery F2-21 demonstrated: rewrite what an audit row says happened.
        chain.get(1).setMessage("control updated — approved by the board");

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "CONTENT_ALTERED").containsEntry("verified", false)
            .containsEntry("firstBreakAtId", 2L);
    }

    @Test void deletingARowFromTheMiddleIsDetected() {
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        List<AuditLogEntry> gapped = new ArrayList<>(List.of(chain.get(0), chain.get(2)));

        Map<String, Object> r = serviceWith(h, gapped, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "LINK_BROKEN").containsEntry("verified", false)
            .containsEntry("firstBreakAtId", 3L);
        assertThat((String) r.get("note")).contains("deleted");
    }

    @Test void aFabricatedRowInsertedIntoTheChainIsDetected() {
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);

        // An actor with database write access invents an approval and hashes it themselves. Without
        // the key they cannot produce a MAC that verifies, so the forgery surfaces at its own id.
        AuditLogEntry forged = row(99, "APPROVE", "approved by CISO");
        forged.setPrevHash(chain.get(0).getRowHash());
        forged.setHashAlg(AuditChainHasher.ALG_KEYED);
        forged.setSealedAt(LocalDateTime.now());
        forged.setRowHash(unkeyed().hash(forged, chain.get(0).getRowHash()));   // no key
        List<AuditLogEntry> spliced = new ArrayList<>(List.of(chain.get(0), forged));

        Map<String, Object> r = serviceWith(h, spliced, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("verified", false).containsEntry("firstBreakAtId", 99L);
    }

    @Test void clearingAFieldToBlankIsDetected_notJustChangingIt() {
        // Regression on the canonical form: NULL is encoded with an impossible byte length (-1), so
        // "the field was null" and "the field held a space" hash differently. With a placeholder
        // string instead, wiping a field to that placeholder would have been invisible.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.get(2).setUserRole(null);

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);
        assertThat(r).containsEntry("status", "CONTENT_ALTERED").containsEntry("firstBreakAtId", 3L);
    }

    @Test void movingContentAcrossAFieldBoundaryIsDetected() {
        // Why the canonical form is length-prefixed rather than delimited: with a plain delimiter,
        // shifting text from one field into the next leaves the concatenation unchanged and the
        // digest identical. Length prefixing makes the field split part of what is signed.
        AuditChainHasher h = keyed();
        AuditLogEntry a = row(1, "CREATE", "control created");
        a.setEntityCode("CTRL-1");
        String before = h.hash(a, AuditChainHasher.GENESIS);

        a.setEntityCode("CTRL-1control created");
        a.setMessage("");
        assertThat(h.hash(a, AuditChainHasher.GENESIS)).isNotEqualTo(before);
    }

    // ── The two ways this control could LIE ──────────────────────────────────────────────────

    @Test void anEmptyChainDoesNotReadAsVerified() {
        // ANTI-VACUITY, and the single most important assertion here. "Nothing to check" and
        // "everything checked out" are different sentences and only one is evidence — if an empty
        // ledger reported VERIFIED, wiping the table would produce a clean integrity report.
        Map<String, Object> r = serviceWith(keyed(), List.of(), List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "EMPTY").containsEntry("verified", false);
        assertThat((String) r.get("note")).contains("nothing has been verified");
    }

    @Test void rowsNotYetSealedAreReportedAsUnsealed_notAsBroken() {
        // The sealing window is real and stated. Rows newer than the last pass are outside the
        // control's coverage; calling them tampering would train operators to ignore the alarm.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        List<AuditLogEntry> fresh = new ArrayList<>(List.of(row(4, "CREATE", "not sealed yet")));

        Map<String, Object> r = serviceWith(h, chain, fresh).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "VERIFIED").containsEntry("verified", true)
            .containsEntry("unsealedRows", 1L);
    }

    @Test void aModeChangeIsNotReportedAsTampering_butIsNotWavedThroughEither() {
        // This test was called `aKeyRotationReadsAsARotation_notAsTampering`, and F2-434 found the
        // NAME was the defect: the fixture below sets hash_alg to the UNKEYED constant, which is a
        // change of MODE, not a rotation. A rotation keeps hash_alg='HMAC-SHA256' — see
        // aRealKeyRotationIsReportedAsCONTENT_ALTERED. So the name asserted a scenario the branch
        // cannot reach, and a reader trusts a test's name before its body (F2-418).
        //
        // The half worth keeping is real: a mode change must not fire an integrity alarm. What it
        // must ALSO not do is claim the rows are fine — the skip condition is a column an attacker
        // can write, so "not ruled out" is the strongest thing the verifier can say.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.forEach(e -> e.setHashAlg(AuditChainHasher.ALG_UNKEYED));   // sealed before the key

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("tamperDetected", false)
            .containsEntry("status", "PARTIALLY_VERIFIED").containsEntry("verified", false);
        assertThat((String) r.get("note"))
            .as("the operator-facing sentence must not name a cause the branch cannot have")
            .doesNotContain("not tampering")
            .contains("NOT RULED OUT")
            .contains("mode");
    }

    @Test void aRealKeyRotationIsReportedAsCONTENT_ALTERED_andTheMessageSaysSo() {
        // F2-434, the half that is FILED rather than fixed: hash_alg records the algorithm family
        // and never which key, so rotating from key A to key B leaves every row reading
        // HMAC-SHA256. Those rows are therefore NOT skipped — they are re-hashed with B and fail.
        //
        // This pins the behaviour as it is, because the fix is a stored key fingerprint: a format
        // change to the seal that makes already-sealed keyed rows permanently unverifiable, which
        // is a decision about existing evidence rather than a bug fix. What this slice CAN do is
        // stop the message accusing an operator of tampering for a routine operation.
        List<AuditLogEntry> chain = sealedChain(new AuditChainHasher(KEY));
        AuditChainHasher rotated = new AuditChainHasher("audit-chain-ROTATED-key-9876543210-zyxwvu");

        Map<String, Object> r = serviceWith(rotated, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "CONTENT_ALTERED").containsEntry("firstBreakAtId", 1L);
        assertThat((String) r.get("note"))
            .as("a rotation is indistinguishable from tampering here, so the message must say so")
            .contains("ROTATED").contains("grc.audit.integrity-key");
    }

    // ── F2-434: a row must not be able to opt itself out of being checked ────────────────────

    @Test void anUnrecognisedHashAlgIsTampering_becauseNoSealingPassCanWriteOne() {
        // The sealer stamps one of exactly two constants, so a third value did not come from a
        // sealing pass. Before F2-434 it made the row skip its content hash AND its link — i.e.
        // the cheapest way to hide a row was to declare it unverifiable rather than forge it.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.get(1).setHashAlg("SHA-256-v2");

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "ALG_UNRECOGNISED")
            .containsEntry("tamperDetected", true).containsEntry("firstBreakAtId", 2L);
        assertThat((String) r.get("note")).contains("SHA-256-v2").contains("written directly");
    }

    @Test void aNulledHashAlgIsTampering_theCheapestFormOfTheSameWrite() {
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.get(2).setHashAlg(null);

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "ALG_UNRECOGNISED")
            .containsEntry("tamperDetected", true).containsEntry("firstBreakAtId", 3L);
        assertThat((String) r.get("note")).contains("<null>");
    }

    @Test void markingARowUnverifiableNoLongerHidesADeletionBeforeIt() {
        // THE defect. Delete rows from the middle, then mark the row that follows the gap with the
        // other declared algorithm: before F2-434 its prev_hash was never compared with anything,
        // `prev` was re-anchored on the row's own stored hash, and the walk completed reporting
        // tamperDetected=false. One column write on one row hid an arbitrary-length deletion.
        //
        // The link needs no key — it compares two stored strings — so it is now checked for every
        // sealed row and the deletion surfaces at the row that follows it.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> full = new ArrayList<>(List.of(
            row(1, "CREATE", "control created"),
            row(2, "BREAK_GLASS", "emergency access granted"),
            row(3, "UPDATE", "control updated"),
            row(4, "EXPORT", "register exported")));
        assertThat(serviceWith(h, List.of(), full).sealTenant(ORG)).isEqualTo(4);

        List<AuditLogEntry> pruned = new ArrayList<>(List.of(full.get(0), full.get(2), full.get(3)));
        pruned.get(1).setHashAlg(AuditChainHasher.ALG_UNKEYED);   // "you cannot check me"

        Map<String, Object> r = serviceWith(h, pruned, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "LINK_BROKEN")
            .containsEntry("tamperDetected", true).containsEntry("firstBreakAtId", 3L);
    }

    @Test void aModeChangeBoundaryStillLinks_soTheLinkCheckIsNotANewFalseAlarm() {
        // Enabling the link check for a row this key cannot re-hash is only safe because sealing
        // writes prev_hash from the predecessor's row_hash whatever sealed either row. Measured on
        // the live ledger: both boundary rows of a real SHA-256 → HMAC-SHA256 → SHA-256 mode change
        // link intact. Here the same property, in both directions, over one chain.
        List<AuditLogEntry> rows = new ArrayList<>(List.of(
            row(1, "CREATE", "before the key"),
            row(2, "UPDATE", "before the key")));
        assertThat(serviceWith(unkeyed(), List.of(), rows).sealTenant(ORG)).isEqualTo(2);

        AuditChainHasher h = keyed();
        List<AuditLogEntry> after = new ArrayList<>(List.of(row(3, "EXPORT", "after the key")));
        assertThat(serviceWith(h, rows, after).sealTenant(ORG)).isEqualTo(1);
        assertThat(after.get(0).getPrevHash())
            .as("the link must cross the mode boundary, or the fix invents an alarm")
            .isEqualTo(rows.get(1).getRowHash());

        List<AuditLogEntry> whole = new ArrayList<>(rows);
        whole.addAll(after);
        Map<String, Object> r = serviceWith(h, whole, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "PARTIALLY_VERIFIED")
            .containsEntry("tamperDetected", false)
            .containsEntry("unverifiableRows", 2).containsEntry("verifiedRows", 1);
    }

    @Test void theAlgorithmRunsAreReported_soAnOperatorCanSeeContiguity() {
        // Reported, never adjudicated: a mode change leaves a boundary between long contiguous
        // runs, an isolated interior row is what a forged skip looks like — and a deployment may
        // legitimately flip modes repeatedly, which the dev ledger does (three runs). Handing over
        // the shape is honest; deciding from it would be manufacturing a verdict.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.get(0).setHashAlg(AuditChainHasher.ALG_UNKEYED);

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        @SuppressWarnings("unchecked")
        List<String> runs = (List<String>) r.get("algorithmRuns");
        assertThat(runs).containsExactly(
            AuditChainHasher.ALG_UNKEYED + "[1..1 x1]",
            AuditChainHasher.ALG_KEYED + "[2..3 x2]");
    }

    @Test void theRecognisedVocabularyIsTheSealersOwnTwoConstants() {
        // One declaration, two readers: the sealer writes algorithm(), the verifier checks
        // membership. A second list would be free to drift from the constants.
        assertThat(AuditChainHasher.isRecognisedAlgorithm(AuditChainHasher.ALG_KEYED)).isTrue();
        assertThat(AuditChainHasher.isRecognisedAlgorithm(AuditChainHasher.ALG_UNKEYED)).isTrue();
        assertThat(AuditChainHasher.isRecognisedAlgorithm(null)).isFalse();
        assertThat(AuditChainHasher.isRecognisedAlgorithm("")).isFalse();
        assertThat(AuditChainHasher.isRecognisedAlgorithm("hmac-sha256")).isFalse();
        assertThat(AuditChainHasher.isRecognisedAlgorithm(keyed().algorithm())).isTrue();
        assertThat(AuditChainHasher.isRecognisedAlgorithm(unkeyed().algorithm())).isTrue();
    }

    @Test void rowsSealedUnderAnotherKeyAreUNVERIFIABLE_notVerified() {
        // The defect the live probe found in the first cut of this class. With 5,000 of 6,088 real
        // rows skipped for an algorithm mismatch it answered `verified: true` — claiming an
        // assurance over rows it had not checked, which is the exact failure this programme hunts.
        //
        // Two questions, kept separate: `verified` = was EVERY row re-checked; `tamperDetected` =
        // did anything fail. A row sealed under a key no longer held is neither verified nor tampered.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.get(0).setHashAlg(AuditChainHasher.ALG_UNKEYED);

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("status", "PARTIALLY_VERIFIED")
            .containsEntry("verified", false)          // ← the assertion that was wrong before
            .containsEntry("tamperDetected", false)
            .containsEntry("verifiedRows", 2)
            .containsEntry("unverifiableRows", 1);
        assertThat((String) r.get("note"))
            .as("F2-434: the row's CONTENT is what cannot be checked — its link was, and the "
              + "report must not turn 'unverifiable' into 'fine'")
            .contains("cannot be re-hashed").contains("NOT RULED OUT");
    }

    @Test void theUnverifiableIdListIsBounded_notAPayload() {
        // 5,000 ids in a JSON body is a payload, not an explanation — measured on the real dev
        // ledger, where an accidental unkeyed pass sealed exactly that many rows.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> many = new ArrayList<>();
        for (int i = 1; i <= 60; i++) many.add(row(i, "CREATE", "row " + i));
        serviceWith(h, List.of(), many).sealTenant(ORG);
        many.forEach(e -> e.setHashAlg(AuditChainHasher.ALG_UNKEYED));

        Map<String, Object> r = serviceWith(h, many, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("unverifiableRows", 60).containsEntry("unverifiableIdRange", "1..60");
        assertThat((List<?>) r.get("unverifiableSampleIds")).hasSize(20);
    }

    @Test void sealingRefusesToChainARowThatPrecedesTheSealedHead() {
        // Sealing appends only, because verification walks by id ASC. If a historical row's integrity
        // columns are ever cleared and re-offered — the tempting "re-seal history after a key
        // rotation" move — chaining it onto the current head would build a chain whose link order
        // contradicts its id order, and verification would cry LINK_BROKEN over a ledger nobody
        // attacked. Refusing beats manufacturing that alarm.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> sealed = sealedChain(h);            // ids 1..3
        List<AuditLogEntry> reoffered = new ArrayList<>(List.of(row(1, "CREATE", "control created")));

        AuditChainService s = new AuditChainService(repo, h);
        ReflectionTestUtils.setField(s, "sealingEnabled", true);
        when(repo.findUnsealed(eq(ORG), any())).thenReturn(reoffered);
        when(repo.findLastSealedId(eq(ORG))).thenReturn(3L);
        when(repo.findLastSealedRowHash(eq(ORG))).thenReturn(sealed.get(2).getRowHash());

        assertThat(s.sealTenant(ORG)).as("the out-of-order row must not be sealed").isZero();
        assertThat(reoffered.get(0).getRowHash()).isNull();
    }

    // ── Keying: the difference between deterrence and detection ──────────────────────────────

    @Test void theKeyIsWhatStopsTheChainBeingRecomputed() {
        AuditLogEntry e = row(1, "CREATE", "control created");
        String withKey    = keyed().hash(e, AuditChainHasher.GENESIS);
        String withoutKey = unkeyed().hash(e, AuditChainHasher.GENESIS);

        assertThat(withKey).isNotEqualTo(withoutKey).hasSize(64);
        assertThat(keyed().isKeyed()).isTrue();
        assertThat(unkeyed().isKeyed()).isFalse();
        assertThat(unkeyed().algorithm()).isEqualTo(AuditChainHasher.ALG_UNKEYED);
    }

    @Test void aWeakOrPlaceholderKeyIsRefusedRatherThanReportedAsKeyed() {
        // "Keyed" appearing in an integrity report while the key is `changeme` is a worse outcome
        // than "unkeyed", because it is the report that is wrong rather than the configuration.
        assertThat(new AuditChainHasher("short").isKeyed()).isFalse();
        assertThat(new AuditChainHasher("change-this-please-0123456789-abcdefghij").isKeyed())
            .isFalse();
        assertThat(new AuditChainHasher("placeholder-0123456789-abcdefghijklmnop").isKeyed())
            .isFalse();
        assertThat(new AuditChainHasher(KEY).isKeyed()).isTrue();
    }

    @Test void anUnkeyedChainStillDetectsModification_andSaysItIsUnkeyed() {
        // Unkeyed is degraded, not useless: it still catches modification and deletion. The response
        // must say which mode produced the verdict, so the verdict can be weighed.
        AuditChainHasher h = unkeyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.get(0).setMessage("tampered");

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("verified", false).containsEntry("keyed", false)
            .containsEntry("firstBreakAtId", 1L);
        assertThat((String) r.get("status")).isEqualTo("CONTENT_ALTERED");
    }

    @Test void sealingIsResumable_soASecondPassChainsOntoTheFirst() {
        AuditChainHasher h = keyed();
        List<AuditLogEntry> first = sealedChain(h);

        List<AuditLogEntry> second = new ArrayList<>(List.of(row(4, "EXPORT", "register exported")));
        AuditChainService s = serviceWith(h, first, second);
        assertThat(s.sealTenant(ORG)).isEqualTo(1);
        assertThat(second.get(0).getPrevHash()).isEqualTo(first.get(2).getRowHash());

        List<AuditLogEntry> whole = new ArrayList<>(first);
        whole.addAll(second);
        assertThat(serviceWith(h, whole, List.of()).verifyTenant(ORG))
            .containsEntry("status", "VERIFIED").containsEntry("sealedRows", 4);
    }
}
