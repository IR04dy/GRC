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

    @Test void aKeyRotationReadsAsARotation_notAsTampering() {
        // Without the per-row hash_alg column, changing the integrity key would make every
        // historical row fail verification — an integrity alarm firing on a routine operation, which
        // is how alarms get ignored.
        AuditChainHasher h = keyed();
        List<AuditLogEntry> chain = sealedChain(h);
        chain.forEach(e -> e.setHashAlg(AuditChainHasher.ALG_UNKEYED));   // sealed before the key

        Map<String, Object> r = serviceWith(h, chain, List.of()).verifyTenant(ORG);

        assertThat(r).containsEntry("tamperDetected", false);
        assertThat((String) r.get("note")).contains("key rotation").contains("not tampering");
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
        assertThat((String) r.get("note")).contains("CANNOT be re-verified");
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
