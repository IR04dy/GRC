package com.tss.grc.module.privacy.dsa.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.privacy.dsa.entity.DataSharingAgreement;
import com.tss.grc.module.privacy.dsa.repository.DataSharingAgreementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Lifecycle service for {@link DataSharingAgreement}.
 *
 * <p>State machine:
 * {@code DRAFT → IN_REVIEW → ACTIVE → EXPIRED | TERMINATED}.
 */
@Service
@RequiredArgsConstructor
public class DataSharingAgreementService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "IN_REVIEW", "ACTIVE", "EXPIRED", "TERMINATED");

    /** F2-219 — the one state an agreement may be CREATED in; the rest are {@link #submit}'s,
     *  {@link #activate}'s, {@link #expire}'s and {@link #terminate}'s to write. */
    private static final String INITIAL_STATUS = "DRAFT";

    private final DataSharingAgreementRepository repo;

    @Transactional(readOnly = true)
    public Page<DataSharingAgreement> list(Pageable p) {
        return repo.findByOrganisationIdOrderByEffectiveFromDescIdDesc(TenantContext.getCurrentTenant(), p);
    }

    @Transactional(readOnly = true)
    public Page<DataSharingAgreement> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByEffectiveFromDescIdDesc(
                TenantContext.getCurrentTenant(), status, p);
    }

    @Transactional(readOnly = true)
    public DataSharingAgreement get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("DataSharingAgreement " + id));
    }

    @Transactional
    public DataSharingAgreement create(DataSharingAgreement req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        // ⚠⚠ F2-219 — `if (status == null) DRAFT` followed by a membership test is a DEFAULT plus a
        // VOCABULARY check, and neither is a guard. 🔑 Checking that a value is in the vocabulary is
        // not checking that it is a value a record may START in (F2-217's sentence about audit_lc).
        // Probed live as `compliance`: POST with {status: "ACTIVE", signerName: "Nobody Who Signed
        // This"} answered 201 and stored both — a data-sharing agreement live and signed without
        // submit() (DRAFT -> IN_REVIEW) or activate() (which stamps signed_at from the clock).
        req.setStatus(ServerOwnedState.requireInitial(
            req.getStatus(), INITIAL_STATUS, "a data sharing agreement",
            "POST /{id}/submit then POST /{id}/activate"));
        // The signatures belong to activate(). `signedAt` already carries
        // @JsonProperty(access = READ_ONLY) so the wire cannot reach it; these two do not, and a
        // DRAFT naming its signatories asserts an execution that has not happened.
        req.setSignerName(null);
        req.setCounterpartySigner(null);
        return repo.save(req);
    }

    @Transactional
    public DataSharingAgreement submit(Long id) {
        DataSharingAgreement a = get(id);
        if (!"DRAFT".equals(a.getStatus()))
            throw new IllegalStateException("submit only allowed from DRAFT (was " + a.getStatus() + ")");
        a.setStatus("IN_REVIEW");
        return repo.save(a);
    }

    @Transactional
    /**
     * F2-236 — {@code signerName} is the authenticated principal; {@code counterpartySigner} stays
     * caller data <b>by design</b>, because the counterparty's signatory is a person at another
     * organisation and can never be a principal here (measured: all 8 live rows name an external
     * party — an NCA liaison officer, a MoCI director, a university dean).
     *
     * <p>This method stamps {@code signedAt} from the server's clock, so the signing it records is
     * THIS call; the frontend passed the literals {@code 'admin'} and {@code 'counterparty'}.
     */
    public DataSharingAgreement activate(Long id, String counterpartySigner) {
        DataSharingAgreement a = get(id);
        if (!"IN_REVIEW".equals(a.getStatus()) && !"DRAFT".equals(a.getStatus()))
            throw new IllegalStateException("activate only allowed from DRAFT|IN_REVIEW (was " + a.getStatus() + ")");
        a.setStatus("ACTIVE");
        a.setSignedAt(LocalDateTime.now());
        a.setSignerName(CurrentActor.requireUsername("a data-sharing agreement signature"));
        if (counterpartySigner != null) a.setCounterpartySigner(counterpartySigner);
        return repo.save(a);
    }

    @Transactional
    public DataSharingAgreement expire(Long id) {
        DataSharingAgreement a = get(id);
        if (!"ACTIVE".equals(a.getStatus()))
            throw new IllegalStateException("expire only allowed from ACTIVE (was " + a.getStatus() + ")");
        a.setStatus("EXPIRED");
        return repo.save(a);
    }

    @Transactional
    /**
     * F2-272 — the termination reason is now bilingual (V388).
     *
     * <p>⚠ Both reasons arrive as SCALAR PARAMETERS, so whether they are caller data is a
     * fact about the CALL SITE and not about this signature (F2-222). They are: the
     * controller reads them from the request body, which is correct — why an agreement was
     * terminated is the terminating officer's statement, not something the server can derive.
     */
    public DataSharingAgreement terminate(Long id, String reasonEn, String reasonAr) {
        DataSharingAgreement a = get(id);
        a.setStatus("TERMINATED");
        a.setTerminationReasonEn(reasonEn);
        a.setTerminationReasonAr(reasonAr);
        return repo.save(a);
    }
}
