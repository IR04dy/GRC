package com.tss.grc.module.bcm.binder.service;

import com.tss.grc.common.exception.ExportException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.binder.dto.BcpBinderDtos.BinderResponse;
import com.tss.grc.module.bcm.binder.entity.BcpBinderExport;
import com.tss.grc.module.bcm.binder.repository.BcpBinderExportRepository;
import com.tss.grc.module.bcm.binder.service.BcpBinderPdfBuilder.BuildInput;
import com.tss.grc.module.bcm.binder.service.BcpBinderPdfBuilder.BuildResult;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpSectionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpVersionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Slice 10-F — Orchestrating service for the offline BCP/DRP PDF binder.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate plan / org / language inputs and create the binder row
 *       in {@code GENERATING} state.</li>
 *   <li>Run {@link BcpBinderPdfBuilder} on the loaded plan + sections +
 *       versions + distribution contacts.</li>
 *   <li>Persist the PDF bytes to
 *       {@code ${grc.storage.base-path}/binders/<org_id>/<external_id>.pdf}
 *       and stamp file_path / page_count / byte_size / duration_ms /
 *       status=READY back onto the binder row.</li>
 *   <li>F7 audit log + F8 domain event on generate / download / archive.</li>
 *   <li>Increment download_count on each download.</li>
 *   <li>Soft-delete (archive) with audit trail.</li>
 * </ul>
 *
 * <p>F8 trigger events emitted (the automation rule engine subscribes):
 * <ul>
 *   <li>{@code BCP_BINDER_GENERATED} — every successful generate</li>
 *   <li>{@code BCP_BINDER_DOWNLOADED} — every download</li>
 *   <li>{@code BCP_BINDER_ARCHIVED} — every archive</li>
 *   <li>{@code BCP_BINDER_FAILED} — on PDF generation exception</li>
 * </ul>
 *
 * <p>Multi-tenancy: every read filters by {@code TenantContext.getCurrentTenant()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BcpBinderService {

    private static final Set<String> LANGUAGES = Set.of("EN", "AR", "BILINGUAL");

    private final BcpBinderExportRepository binders;
    private final BcpPlanRepository plans;
    private final BcpSectionRepository sections;
    private final BcpVersionRepository versions;
    private final BcpPlanAcknowledgementRepository contacts;
    private final OrganisationRepository orgs;
    private final BcpBinderPdfBuilder pdfBuilder;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** Configured storage root — defaults to ./uploads as in application.yml. */
    @Value("${grc.storage.base-path:./uploads}")
    private String storageBasePath;

    // ─── Generate ─────────────────────────────────────────────────────

    /**
     * Generate a binder PDF for the given plan. The row is created in
     * {@code GENERATING} state, then on successful render flipped to
     * {@code READY} with the file_path / size / page_count populated.
     * On exception, the row is flipped to {@code FAILED} with the error
     * message captured. Either way, an F7 audit + F8 event fires.
     */
    @Transactional
    public BinderResponse generate(Long planId, String language) {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("Tenant context is not set");
        String lang = normaliseLanguage(language);

        BcpPlan plan = plans.findByIdAndOrganisationId(planId, org)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));

        long t0 = System.currentTimeMillis();

        BcpBinderExport b = new BcpBinderExport();
        b.setOrganisationId(org);
        b.setBcpPlanId(plan.getId());
        b.setExportCode(nextExportCode());
        b.setFormat("PDF");
        b.setLanguage(lang);
        b.setVersionLabel("v" + (plan.getCurrentVersion() == null ? 1 : plan.getCurrentVersion()));
        b.setStatus("GENERATING");
        b.setGeneratedAt(LocalDateTime.now());
        b.setGeneratedByUserId(currentUserId());
        b.setGeneratedByName(currentUserName());

        // F2-78 — ⚠ THERE IS DELIBERATELY NO PRE-SAVE HERE, and removing it is the fix.
        //
        // This used to `binders.save(b)` before the render and save again after it. Two flushes of
        // the same row, and `BcpBinderExport.row_version` is a SQL Server ROWVERSION mapped
        // `@Version ... insertable = false, updatable = false` — the database assigns it, so
        // Hibernate cannot predict the value it gets on the first flush. The second UPDATE therefore
        // carried the pre-insert version in its WHERE clause, matched nothing, and threw
        // StaleObjectStateException. **Every binder generation failed, on every call, with a 500.**
        // That is the ROWVERSION double-flush landmine in CLAUDE.md, and this is an instance of it
        // that had never been caught because the register's rows were seeded rather than generated
        // (F2-139) — so nobody had ever successfully pressed the button.
        //
        // The pre-save's stated reasons do not survive inspection:
        //   • "so we have an external_id" — `externalId` defaults to UUID.randomUUID() in the
        //     entity, so it exists before any save, and it is what the file path is built from.
        //   • "a DB-side id before render" — the render does not use the id.
        //   • "This is what the UI polls on (GENERATING / READY)" — the render is SYNCHRONOUS and
        //     the controller returns 202 with the terminal status already set, so no client can ever
        //     observe GENERATING. The comment described an asynchronous design this code is not.
        //
        // So each path below saves exactly once. GENERATING is still set, and is only ever persisted
        // if something fails before the row reaches a terminal state.
        boolean attemptedSave = false;

        try {
            List<BcpSection> secs = sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(org, plan.getId());
            List<BcpVersion> vers = versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(org, plan.getId());
            List<BcpPlanAcknowledgement> dist = contacts.findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(org, plan.getId());
            Organisation organisation = orgs.findById(org).orElse(null);

            BuildResult result = pdfBuilder.build(new BuildInput(b, plan, organisation, secs, vers, dist));

            // Persist bytes to disk under base-path/binders/<org>/<uuid>.pdf
            String relPath = String.format(Locale.ROOT, "binders/%d/%s.pdf",
                org, b.getExternalId().toString());
            Path full = Paths.get(storageBasePath, relPath).toAbsolutePath().normalize();
            Files.createDirectories(full.getParent());
            Files.write(full, result.bytes());

            b.setFilePath(relPath);
            b.setByteSize(result.bytes().length);
            b.setPageCount(result.pageCount());
            b.setGenerationDurationMs((int) (System.currentTimeMillis() - t0));
            b.setStatus("READY");
            b.setUpdatedAt(LocalDateTime.now());
            attemptedSave = true;
            b = binders.save(b);   // the only save on the success path

            audit.log("BCP_BINDER_GENERATED", "BcpBinderExport", b.getId(),
                "Generated " + b.getExportCode() + " for plan " + plan.getPlanCode()
                    + " (" + lang + ", " + result.pageCount() + " pages, "
                    + result.bytes().length + " bytes)");
            emit("BCP_BINDER_GENERATED", b, plan);

            return BinderResponse.from(b);
        } catch (IOException | RuntimeException ex) {
            // If the READY save itself is what threw, do NOT save again — the session is already in
            // an unknown state and a second write is how one failure becomes two. Record it and
            // rethrow; the transaction rolls back and no half-written row survives.
            if (attemptedSave) {
                log.error("BCP binder persist failed after render (planId={}, lang={})", planId, lang, ex);
                throw (ex instanceof RuntimeException re) ? re
                    : new ExportException("PDF", "Failed to persist binder", ex);
            }
            b.setStatus("FAILED");
            b.setErrorMessage(truncate(ex.getMessage(), 4000));
            b.setGenerationDurationMs((int) (System.currentTimeMillis() - t0));
            b.setUpdatedAt(LocalDateTime.now());
            binders.save(b);   // the only save on the failure path

            audit.log("BCP_BINDER_FAILED", "BcpBinderExport", b.getId(),
                "Failed to generate " + b.getExportCode() + ": " + ex.getMessage());
            emit("BCP_BINDER_FAILED", b, plan);

            log.error("BCP binder generation failed (planId={}, lang={})", planId, lang, ex);
            if (ex instanceof IOException io) {
                throw new ExportException("PDF", "Failed to persist binder to disk", io);
            }
            throw (RuntimeException) ex;
        }
    }

    // ─── Download ─────────────────────────────────────────────────────

    /** Returns the raw bytes of a READY binder and increments
     *  {@code download_count} + stamps {@code last_downloaded_at}.
     *  Emits an F7 audit + F8 event so downloads are auditable. */
    @Transactional
    public DownloadResult download(Long binderId) {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("Tenant context is not set");
        BcpBinderExport b = binders.findByOrganisationIdAndId(org, binderId)
            .orElseThrow(() -> new EntityNotFoundException("Binder not found: " + binderId));

        if (!"READY".equals(b.getStatus())) {
            throw new IllegalStateException("Binder is not READY (current=" + b.getStatus() + ")");
        }
        if (b.getFilePath() == null || b.getFilePath().isBlank()) {
            throw new IllegalStateException("Binder has no file_path");
        }
        Path full = Paths.get(storageBasePath, b.getFilePath()).toAbsolutePath().normalize();

        // F2-78 — a READY binder whose artefact is absent is a STATE problem, not a generation
        // failure, and the two had been collapsed into one 500.
        //
        // Everything here answered "PDF export failed / Document generation failed. Please try again
        // or contact your administrator." Every clause of that is wrong for this case: nothing was
        // generating (the bytes were written at generate time, before the row was marked READY), and
        // retrying cannot help because the file will still be missing. So the caller was told to do
        // the one thing guaranteed not to work, and an operator reading the log saw an export bug
        // rather than a missing artefact.
        //
        // Measured on dev: binders 1 and 2 are READY with byte_size 2,915,000 and 2,310,000 and
        // NO FILE ON DISK — `uploads/` has never contained a `binders` directory. Their file_path
        // uses `binders/<year>/<code>_<slug>_<version>.pdf`, a scheme `generate` above does not
        // produce (it writes `binders/<org>/<uuid>.pdf`), so those rows were seeded rather than
        // generated. See F2-139.
        //
        // Refused as a 409 to match the "not READY" refusal three lines up: from the caller's side
        // both mean "this binder is not downloadable right now", and the message carries the
        // difference. A distinct status would need frontend handling that has no other consumer.
        if (!Files.isRegularFile(full)) {
            throw new IllegalStateException(
                "Binder " + b.getExportCode() + " is marked READY but its PDF is missing from "
                + "storage, so there is nothing to download. Retrying will not help. Generate a new "
                + "binder version for this plan — a binder is an immutable record of what was "
                + "distributed, so the missing file is not silently rebuilt under the same code.");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(full);
        } catch (IOException io) {
            // A file that exists and cannot be read IS an export/IO failure — permissions, a bad
            // mount, a truncated volume. That is what ExportException and the 500 are for, and the
            // distinction is the whole point of the check above.
            throw new ExportException("PDF", "Cannot read binder file: " + full, io);
        }

        b.setDownloadCount((b.getDownloadCount() == null ? 0 : b.getDownloadCount()) + 1);
        b.setLastDownloadedAt(LocalDateTime.now());
        b.setLastDownloadedByUserId(currentUserId());
        b.setUpdatedAt(LocalDateTime.now());
        binders.save(b);

        // Resolve plan only for the audit message + event payload — best-effort.
        BcpPlan plan = plans.findByIdAndOrganisationId(b.getBcpPlanId(), org).orElse(null);

        audit.log("BCP_BINDER_DOWNLOADED", "BcpBinderExport", b.getId(),
            "Downloaded " + b.getExportCode() + " (count="
                + b.getDownloadCount() + ")");
        emit("BCP_BINDER_DOWNLOADED", b, plan);

        String filename = (b.getExportCode() == null ? ("binder-" + b.getId()) : b.getExportCode())
            + "-" + nz(b.getVersionLabel(), "v1") + ".pdf";
        return new DownloadResult(bytes, filename);
    }

    public record DownloadResult(byte[] bytes, String filename) {}

    // ─── Archive (soft-delete) ────────────────────────────────────────

    @Transactional
    public BinderResponse archive(Long binderId) {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("Tenant context is not set");
        BcpBinderExport b = binders.findByOrganisationIdAndId(org, binderId)
            .orElseThrow(() -> new EntityNotFoundException("Binder not found: " + binderId));
        if (Boolean.TRUE.equals(b.getIsDeleted())) return BinderResponse.from(b);

        b.setStatus("ARCHIVED");
        b.setIsDeleted(true);
        b.setDeletedAt(LocalDateTime.now());
        b.setDeletedBy(currentUserId());
        b.setUpdatedAt(LocalDateTime.now());
        binders.save(b);

        BcpPlan plan = plans.findByIdAndOrganisationId(b.getBcpPlanId(), org).orElse(null);

        audit.log("BCP_BINDER_ARCHIVED", "BcpBinderExport", b.getId(),
            "Archived " + b.getExportCode());
        emit("BCP_BINDER_ARCHIVED", b, plan);
        return BinderResponse.from(b);
    }

    // ─── Read ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BinderResponse> listForPlan(Long planId) {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("Tenant context is not set");
        // Confirm the plan exists in this tenant before exposing binders.
        plans.findByIdAndOrganisationId(planId, org)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));
        return binders.findByOrganisationIdAndBcpPlanIdOrderByGeneratedAtDesc(org, planId)
            .stream().map(BinderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BinderResponse get(Long binderId) {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) throw new IllegalStateException("Tenant context is not set");
        BcpBinderExport b = binders.findByOrganisationIdAndId(org, binderId)
            .orElseThrow(() -> new EntityNotFoundException("Binder not found: " + binderId));
        return BinderResponse.from(b);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private String nextExportCode() {
        int year = Year.now().getValue();
        // Short uppercase suffix — keeps codes readable and avoids
        // colliding within a tenant (UNIQUE constraint guards anyway).
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "BCP-EXP-%04d-%s", year, suffix);
    }

    private static String normaliseLanguage(String lang) {
        String up = lang == null ? "BILINGUAL" : lang.trim().toUpperCase(Locale.ROOT);
        if (!LANGUAGES.contains(up)) {
            throw new IllegalArgumentException("language must be one of EN/AR/BILINGUAL");
        }
        return up;
    }

    private static Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getPrincipal() == null) return null;
        try {
            // Reflection avoids a hard import on the security package that
            // tests don't always wire up. GrcUserDetails exposes
            // {@code getUserId()} — we probe both that and a generic
            // {@code getId()} so a future principal shape change still works.
            Object principal = a.getPrincipal();
            for (String name : new String[] { "getUserId", "getId" }) {
                try {
                    var getter = principal.getClass().getMethod(name);
                    Object v = getter.invoke(principal);
                    if (v instanceof Long l) return l;
                } catch (NoSuchMethodException ignored) { /* try next */ }
            }
        } catch (Exception ex) {
            // never bubble up — current user id is informational only
            log.debug("Could not resolve current user id from principal {}", a.getPrincipal().getClass().getName(), ex);
        }
        return null;
    }

    private static String currentUserName() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    private void emit(String trigger, BcpBinderExport b, BcpPlan plan) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("binderId",       str(b.getId()));
        payload.put("externalId",     str(b.getExternalId()));
        payload.put("exportCode",     str(b.getExportCode()));
        payload.put("bcpPlanId",      str(b.getBcpPlanId()));
        payload.put("planCode",       plan == null ? "" : str(plan.getPlanCode()));
        payload.put("language",       str(b.getLanguage()));
        payload.put("versionLabel",   str(b.getVersionLabel()));
        payload.put("status",         str(b.getStatus()));
        payload.put("pageCount",      str(b.getPageCount()));
        payload.put("byteSize",       str(b.getByteSize()));
        payload.put("downloadCount",  str(b.getDownloadCount()));
        publisher.publishEvent(new DomainEvent(
            b.getOrganisationId(), trigger, "BcpBinderExport", b.getId(),
            "bcp_binder:" + b.getId(), payload));
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String nz(String s, String fb) {
        return (s == null || s.isBlank()) ? fb : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
