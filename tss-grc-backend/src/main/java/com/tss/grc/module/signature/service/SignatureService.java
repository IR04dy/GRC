package com.tss.grc.module.signature.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
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
    private final UserRepository userRepository;

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
        s.setSignerName(resolveSignerName(userId));
        s.setSignerRole(currentRole());
        LocalDateTime signedAt = LocalDateTime.now();
        s.setSignedAt(signedAt);
        s.setPayloadHash(sha256(entityType + "|" + entityId + "|" + normIntent + "|" + userId + "|" + signedAt));
        return toResponse(repository.save(s));
    }

    private String resolveSignerName(Long userId) {
        if (userId == null) return null;
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return null;
        return u.getFullNameEn() != null && !u.getFullNameEn().isBlank() ? u.getFullNameEn() : u.getUsername();
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
            s.getSignerId(), s.getSignerName(), s.getSignerRole(), s.getPayloadHash(), s.getSignedAt());
    }
}
