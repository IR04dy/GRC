package com.tss.grc.module.signature.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import com.tss.grc.module.signature.dto.SignatureResponse;
import com.tss.grc.module.signature.entity.Signature;
import com.tss.grc.module.signature.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SignatureService {

    private static final Set<String> INTENTS =
        Set.of("APPROVE", "REVIEW", "ACKNOWLEDGE", "ATTEST", "REJECT");

    private final SignatureRepository repository;
    private final TenantUserDirectory directory;

    @Transactional(readOnly = true)
    public List<SignatureResponse> list(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repository.findByOrganisationIdAndEntityTypeAndEntityIdOrderBySignedAtDesc(orgId, entityType, entityId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public SignatureResponse sign(String entityType, Long entityId, String intent, String statementEn, Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        String normIntent = intent == null ? "ATTEST" : intent.trim().toUpperCase();
        if (!INTENTS.contains(normIntent)) {
            throw new IllegalArgumentException("Unknown intent '" + intent + "'. Valid: " + INTENTS);
        }
        Signature s = new Signature();
        s.setOrganisationId(orgId);
        s.setEntityType(entityType);
        s.setEntityId(entityId);
        s.setIntent(normIntent);
        s.setStatementEn(statementEn);
        s.setSignerId(userId);
        applySignerLabels(s, userId);
        s.setSignerRole(currentRole());
        LocalDateTime signedAt = LocalDateTime.now();
        s.setSignedAt(signedAt);
        s.setPayloadHash(sha256(entityType + "|" + entityId + "|" + normIntent + "|" + userId + "|" + signedAt));
        return toResponse(repository.save(s));
    }

    /**
     * F2-249 — this register is the platform's <b>exemplar</b> of the (id + identity copy) shape:
     * {@code signer_id} comes from the authenticated principal and {@code signer_name} has always
     * been DERIVED from the row that id names, so the two halves cannot disagree. Four other
     * registers declared the same pair and wrote only the label.
     *
     * <p>Two things changed here and neither alters behaviour for a well-formed call. The label
     * spelling now comes from {@link TenantUserDirectory#labelFor} rather than being the fourth
     * hand-written copy of it. And the lookup is <b>tenant-scoped</b>: the id has always come from
     * the principal, so a cross-tenant resolution was never reachable — but that was a fact about
     * the call sites rather than a property of this method (F2-182), and
     * {@code CommitteeService.approveMinutes} passes its own {@code approverId} in as a scalar.
     */
    private void applySignerLabels(Signature s, Long userId) {
        // F2-277 — one resolve, both halves. This was `resolveSignerName`, returning the English
        // name alone while `iam.users` stored `full_name_ar` beside it, so the signatory of an
        // e-signed governance record rendered in English on an Arabic-primary UI. Both directory
        // methods are null-safe, so an unresolvable id clears the pair rather than half of it.
        var u = directory.resolve(userId).orElse(null);
        s.setSignerName(directory.labelFor(u));
        s.setSignerNameAr(directory.labelArFor(u));
    }

    /** First ROLE_ authority of the current principal, prefix stripped. */
    private String currentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return auth.getAuthorities().stream()
            .map(Object::toString)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .findFirst().orElse(null);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private SignatureResponse toResponse(Signature s) {
        return new SignatureResponse(
            s.getId(), s.getEntityType(), s.getEntityId(), s.getIntent(), s.getStatementEn(),
            s.getSignerId(), s.getSignerName(), s.getSignerNameAr(), s.getSignerRole(),
            s.getPayloadHash(), s.getSignedAt());
    }
}
