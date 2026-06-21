package com.tss.grc.module.privacy.subprocessor.service;

import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.PublicSubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PDPL Article 12 transparency endpoint — public list of APPROVED
 * sub-processors with internal fields stripped.
 *
 * <p>A data subject (or anyone reading the bank's public privacy
 * notice) can hit {@code /api/v1/privacy/subprocessors/public} without
 * authentication and get the current list of sub-processors. This is
 * the artefact PDPL Art.12 expects in a "where does my data go"
 * disclosure.
 *
 * <p>Fields surfaced publicly: code, legal name (EN+AR), country,
 * region, processing purpose, cross-border safeguard type. Everything
 * else (vendor link, DPA reference, DPO sign-off identity, review
 * dates) is internal and stripped.
 *
 * <p>This service is org-agnostic — the controller passes
 * {@code organisationId} explicitly because the endpoint is
 * permitAll'd and there's no JWT to feed {@code TenantContext}.
 */
@Service
@RequiredArgsConstructor
public class SubprocessorPublicListService {

    private final SubprocessorRepository repo;

    @Transactional(readOnly = true)
    public List<PublicSubprocessorResponse> publicList(Long organisationId) {
        // SEC-PRIV-003 defence-in-depth: the caller (the permitAll public
        // endpoint) is responsible for resolving the tenant from an opaque
        // identifier and rejecting unknown orgs. Refuse a null/unresolved
        // org here too so this method can never run an unscoped read.
        if (organisationId == null) {
            return List.of();
        }
        return repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(organisationId, "APPROVED")
                .stream().map(PublicSubprocessorResponse::from).toList();
    }
}
