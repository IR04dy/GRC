package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * F2-250 — the one place a KYC subject reference is resolved, and the one place a register
 * derives the label that says which subject it names.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code ethics.aml_alerts} declares {@code subject_id} beside {@code subject_name} and
 * {@code AmlAlertService.apply} wrote the two <b>independently</b>, each behind its own null
 * guard. So a caller could send a label with no id (which is what the platform's own screen
 * did: a free-text box and no picker), or an id with a label naming somebody else, or another
 * tenant's subject — {@code FK_aml_subject} exists, and a foreign key knows nothing about
 * tenancy (F2-242).
 *
 * <p>🔑 The correct artefact was already in this package, in the sibling register:
 * {@code ScreeningHit.subjectId} is {@code nullable = false} and {@link KycService} resolves it
 * tenant-scoped with a hard throw before storing it. F2-223 had already cited
 * {@code KycService} as the exemplar for the <em>decider</em> of an AML disposition — <b>the same
 * class was the exemplar for the reference too, and nobody read that half</b> (F2-215's
 * two-of-N, across two registers of one module).
 *
 * <h2>⚠ What this deliberately does NOT decide</h2>
 *
 * <p>Whether a register may record a subject it cannot resolve. {@code subject_id} is nullable
 * beside a nullable {@code subject_name}, which is the schema stating that an unregistered party
 * is representable (F2-246's rule) — and it is right: a transaction-monitoring alert can concern
 * a counterparty nobody has onboarded, which is why {@code counterparty_name} exists with no id
 * at all. All 8 label-only rows in dev and prod are of that shape, so refusing them would break
 * every request that works today (F2-182). Each caller decides; this only makes the two halves
 * agree when there IS an id.
 *
 * <p>⚙ Bean name {@code kycSubjectDirectory}, deliberately distinguishing rather than aliasing:
 * two {@code @Component}s with the same simple name stop the application starting, and the whole
 * unit suite passes over it because nothing in the suite boots a context (F2-247).
 */
@Component
@RequiredArgsConstructor
public class KycSubjectDirectory {

    private final KycSubjectRepository subjects;

    /**
     * The subject with this id <b>in the caller's own tenant</b>, or empty.
     *
     * <p>Empty covers all three misses on purpose — no such id, another tenant's, or no tenant in
     * context — because a caller who can tell them apart can enumerate foreign subject ids from
     * the error text. That is the reason {@code CommitteeMemberRegistryService} gives for using
     * one message, carried over here.
     *
     * <p>⚙ No {@code isDeleted} filter, and that is checked rather than forgotten: {@link
     * KycSubject} carries {@code @Where(clause = "is_deleted = 0")}, so a soft-deleted subject
     * never comes back from {@code findById} at all. Adding a second, redundant filter would be an
     * unkillable mutant — code whose removal changes no observable behaviour (F2-206).
     */
    @Transactional(readOnly = true)
    public Optional<KycSubject> resolve(Long subjectId) {
        if (subjectId == null) return Optional.empty();
        Long org = TenantContext.getCurrentTenant();
        if (org == null) return Optional.empty();
        return subjects.findById(subjectId)
                .filter(s -> org.equals(s.getOrganisationId()));
    }

    /**
     * As {@link #resolve}, refusing with a 400 that names the id and says what could not be
     * recorded against it.
     *
     * @param what the thing being recorded, carrying its own article ("an AML alert's subject")
     */
    @Transactional(readOnly = true)
    public KycSubject require(Long subjectId, String what) {
        return resolve(subjectId).orElseThrow(() -> new IllegalArgumentException(
                "KYC subject " + subjectId + " is not a subject in this organisation's register, "
              + "so " + what + " cannot be recorded against it. A reference that resolves to "
              + "nobody leaves the record unjoinable to the party it is about."));
    }

    /**
     * What to call this subject. {@code full_name} is NOT NULL, so the fallback is only reached by
     * a blank — and it goes to {@code subject_code} rather than to an empty cell, because F2-249
     * found two hand-written copies of a directory disagreeing on exactly that and rendering a
     * party as an empty cell in one place and an identifier in the other.
     */
    public String labelFor(KycSubject s) {
        if (s == null) return null;
        String full = s.getFullName();
        if (full != null && !full.isBlank()) return full;
        String code = s.getSubjectCode();
        return (code != null && !code.isBlank()) ? code : null;
    }

    /**
     * The Arabic half of the same label, or {@code null}.
     *
     * <p>F2-277 — {@code ethics.kyc_subjects.full_name_ar} is populated on 16 of 16 rows, so the
     * party an AML alert is ABOUT had an Arabic name throughout and the derivation read only the
     * English one.
     *
     * <p>⚙ No {@code subject_code} fallback, unlike {@link #labelFor}: the code is a Latin
     * identifier, the English half already carries it when the name is blank, and putting it in an
     * Arabic column would claim an Arabic name that does not exist.
     */
    public String labelArFor(KycSubject s) {
        if (s == null) return null;
        String full = s.getFullNameAr();
        return (full != null && !full.isBlank()) ? full : null;
    }

    // ⚠ F2-277 removed `requireLabel(Long, String)`. It was `require` then `labelFor`, and its one
    // caller now needs BOTH halves of the label — which a resolve-and-label convenience cannot
    // give without resolving twice. The call site holds the resolved subject and asks this class
    // for each half, so the two halves of one label always come from one read. An unused
    // convenience method should not ship (F2-201).
}
