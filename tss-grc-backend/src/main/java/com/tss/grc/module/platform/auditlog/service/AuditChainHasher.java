package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * F2-31 — tamper-evidence for {@code platform.audit_log}.
 *
 * <h3>What this control does, and what it does not</h3>
 *
 * Committee minutes carry a SHA-256 signature. The audit ledger — which every control in this
 * platform cites as its proof, and which F2-21 showed will faithfully corroborate a forgery — had
 * nothing. NCA ECC 2-12 requires event-log protection and ≥12-month retention; ISO 27001 A.8.15 and
 * SAMA CSF say the same. This class supplies the missing half.
 *
 * <p><b>It is DETECTION, not prevention, and the boundary is worth stating precisely rather than
 * implying strength this design does not have:</b>
 *
 * <ul>
 *   <li><b>Detected:</b> any modification of a sealed row's content; deletion of a sealed row from
 *       the middle of a chain; insertion of a fabricated row between sealed rows. All three break
 *       the chain at a specific id, which {@code AuditChainService.verify} names.</li>
 *   <li><b>Detected only when keyed:</b> wholesale re-computation of the chain. With
 *       {@code grc.audit.integrity-key} set, the key lives in {@code external.properties} beside the
 *       jar and NOT in the database — so a database-only compromise (SQL injection, a stolen
 *       {@code sa} password, a DBA without filesystem access) cannot forge a valid chain. Unkeyed,
 *       it can: plain SHA-256 over public inputs is reproducible by anyone.
 *       <p>⚠ F2-434: that holds only because a row cannot opt itself out of being checked. It
 *       could — {@code hash_alg} is a column in the same database, and a value the verifier does
 *       not recognise used to make the row skip its content hash AND its link. Marking a row
 *       unverifiable is cheaper than forging one, so the verifier now treats an unrecognised
 *       algorithm as tampering ({@link #isRecognisedAlgorithm}) and checks the link regardless.
 *       A row whose content genuinely cannot be re-hashed is reported as <i>not ruled out</i>,
 *       never as benign.</li>
 *   <li><b>NOT detected:</b> truncation of the chain's TAIL. Removing the newest N rows leaves a
 *       shorter but internally valid chain. Nothing stored inside the same database can fix that —
 *       it needs an external anchor (an off-box append-only sink, or publishing the head hash
 *       somewhere the operator does not control). That work is not in this slice, and pretending
 *       otherwise would be the exact defect this programme keeps finding.</li>
 *   <li><b>NOT detected:</b> anything done by an actor holding both the database and the key file.
 *       On this deployment the DBA, platform admin and deployment operator are the same person on
 *       the same SQL box, so against that actor this control is deterrence and evidence-of-effort,
 *       not prevention. Separating key custody is a decision for the GRC owner, not a code change.</li>
 *   <li><b>Sealing window:</b> rows are sealed by a periodic pass, so a row deleted before it is
 *       ever sealed leaves no trace. The window is {@code grc.audit.seal-interval-ms} wide.
 *       Hash-on-insert would close it and was rejected for a concrete reason: concurrent inserts
 *       would each read the same predecessor and FORK the chain, and a control that reports
 *       ordinary concurrency as tampering is worse than the gap it closes.</li>
 * </ul>
 *
 * <h3>Why the canonical form is length-prefixed</h3>
 *
 * Fields are joined as {@code <byteLength>:<value>} rather than delimited. With a plain delimiter,
 * content can be moved across a field boundary without changing the digest — an actor could shift
 * text from {@code message} into {@code entity_code} and the hash would still verify. Length
 * prefixing makes the field split part of what is signed.
 */
@Component
public class AuditChainHasher {

    private static final Logger log = LoggerFactory.getLogger(AuditChainHasher.class);

    /** The prev_hash of a tenant's first sealed row. Not a hash — deliberately not 64 chars. */
    public static final String GENESIS = "GENESIS";

    public static final String ALG_KEYED   = "HMAC-SHA256";
    public static final String ALG_UNKEYED = "SHA-256";

    private final byte[] key;      // null when unconfigured
    private final String algorithm;

    public AuditChainHasher(@Value("${grc.audit.integrity-key:}") String configuredKey) {
        String k = configuredKey == null ? "" : configuredKey.trim();
        // A short or placeholder key is worse than no key: it reads as "keyed" in the audit report
        // while being trivially guessable. Refuse to call it keyed.
        boolean usable = k.length() >= 32 && !k.toLowerCase(Locale.ROOT).contains("change-this")
                         && !k.toLowerCase(Locale.ROOT).contains("placeholder");
        if (!usable && !k.isEmpty())
            log.warn("[F2-31] grc.audit.integrity-key is set but unusable (needs >=32 chars, no "
                   + "placeholder text) — falling back to UNKEYED SHA-256. A database-only "
                   + "compromise can forge the chain in this mode.");
        this.key = usable ? k.getBytes(StandardCharsets.UTF_8) : null;
        this.algorithm = usable ? ALG_KEYED : ALG_UNKEYED;
        if (!usable)
            log.warn("[F2-31] audit-ledger integrity is UNKEYED (plain SHA-256): modification and "
                   + "deletion are still detected, but the chain can be re-computed by anyone with "
                   + "database write access. Set grc.audit.integrity-key in external.properties.");
    }

    /** {@link #ALG_KEYED} or {@link #ALG_UNKEYED} — reported to the operator, stored per row. */
    public String algorithm() { return algorithm; }

    public boolean isKeyed() { return key != null; }

    /**
     * Is {@code stored} a value some sealing pass could have written?
     *
     * <p>F2-434. {@link AuditChainService#sealTenant} stamps {@link #algorithm()}, which is one of
     * exactly two constants, so <b>no code path in this platform can produce anything else — NULL
     * included.</b> A sealed row carrying a third value therefore did not come from a sealing pass;
     * it came from a direct write to the column, which is the actor the keyed mode exists to
     * detect. The verifier treats that as tampering rather than as an algorithm it cannot handle.
     *
     * <p>This is the vocabulary's ONE declaration, read by the sealer (which writes it) and the
     * verifier (which checks membership). A second list would be free to drift from the constants
     * above, which is the defect this platform keeps finding rather than a hypothetical.
     */
    public static boolean isRecognisedAlgorithm(String stored) {
        return ALG_KEYED.equals(stored) || ALG_UNKEYED.equals(stored);
    }

    /**
     * The MAC of one row, chained to its predecessor. The three integrity columns and
     * {@code sealed_at} are excluded — they are written BY sealing, so including them would make the
     * hash depend on itself.
     */
    public String hash(AuditLogEntry e, String prevHash) {
        List<String> fields = new ArrayList<>(22);
        fields.add(str(e.getId()));
        fields.add(str(e.getOrganisationId()));
        fields.add(str(e.getExternalId()));
        fields.add(str(e.getUserId()));
        fields.add(str(e.getUserUsername()));
        fields.add(str(e.getUserRole()));
        fields.add(str(e.getUserRoleSource()));      // F2-54's provenance is part of the evidence
        fields.add(str(e.getActionType()));
        fields.add(str(e.getEntityType()));
        fields.add(str(e.getEntityId()));
        fields.add(str(e.getEntityExternalId()));
        fields.add(str(e.getEntityCode()));
        fields.add(str(e.getOldValueJson()));
        fields.add(str(e.getNewValueJson()));
        fields.add(str(e.getWorkflowStatusBefore()));
        fields.add(str(e.getWorkflowStatusAfter()));
        fields.add(str(e.getIpAddress()));
        fields.add(str(e.getUserAgent()));
        fields.add(str(e.getSourceModule()));
        fields.add(str(e.getSourceEndpoint()));
        fields.add(str(e.getCorrelationId()));
        fields.add(str(e.getParentAuditId()));
        fields.add(str(e.getDigitalSignatureRef()));
        fields.add(str(e.getSeverity()));
        fields.add(str(e.getMessage()));
        fields.add(str(e.getCreatedAt()));
        fields.add(prevHash == null ? GENESIS : prevHash);
        return mac(canonical(fields));
    }

    /**
     * {@code <byteLength>:<value>|} per field, with NULL encoded as {@code -1:|}. See the class
     * javadoc for why the length is in there — without it, content can be shifted between fields
     * without changing the digest.
     *
     * <p>The {@code -1} matters: encoding null as any placeholder STRING makes it collide with a
     * field whose real value is that string. A negative length cannot be produced by a present
     * value, so "the field was null" and "the field contained a space" hash differently — which is
     * what makes a CLEARED field visible.
     */
    static String canonical(List<String> fields) {
        StringBuilder sb = new StringBuilder(512);
        for (String f : fields) {
            if (f == null) { sb.append("-1:|"); continue; }
            byte[] b = f.getBytes(StandardCharsets.UTF_8);
            sb.append(b.length).append(':').append(f).append('|');
        }
        return sb.toString();
    }

    private String mac(String canonical) {
        byte[] in = canonical.getBytes(StandardCharsets.UTF_8);
        try {
            if (key == null)
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in));
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(m.doFinal(in));
        } catch (Exception ex) {
            // A hashing failure must not swallow the audit row; it must be loud.
            throw new IllegalStateException("audit-chain hashing failed: " + ex.getMessage(), ex);
        }
    }

    /** Null stays null into {@link #canonical}, which encodes it as an impossible byte length. */
    private static String str(Object v) { return v == null ? null : v.toString(); }
}
