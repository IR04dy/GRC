package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeFreezeWindow;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeSchedulePublication;
import com.tss.grc.module.change_mgmt.repository.ChangeFreezeWindowRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeSchedulePublicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * S8g / CE-8 — the forward schedule of change (FSC), the projected service outage (PSO), and
 * schedule-wide collision detection.
 *
 * <p>Closes G-8 (M): <i>"No FSC/PSO — no published forward schedule, no declared outage, collision
 * detection unpublished."</i> All three clauses were separate gaps and the third was the one easiest
 * to miss.
 *
 * <h3>⚠ What already existed, and therefore is not rebuilt</h3>
 * {@code /calendar} has served the live schedule since CH-17, {@code change_freeze_window} holds the
 * blackout periods, and {@code /{id}/conflicts} finds overlaps <b>for one named change</b>. The plan's
 * {@code change_schedule_entry} table would have re-modelled the window that
 * {@code change_requests.scheduled_start}/{@code scheduled_end} already carries on 15 of 17 dev rows —
 * two answers to one question, which is the duplication S7e and S8f each refused. So this service
 * adds the three things measurement showed were genuinely absent:
 *
 * <ul>
 *   <li><b>Publication.</b> The live calendar changes whenever somebody reschedules and nothing
 *       recorded what the business was told. See {@link #publish}.</li>
 *   <li><b>Declared outage.</b> No table or column anywhere in 52 change columns named an outage or an
 *       affected service (measured platform-wide: 0 outage/maintenance tables).</li>
 *   <li><b>Schedule-wide collisions.</b> {@code conflicts(id)} requires you to already suspect a
 *       specific change. You cannot find a collision you have not guessed at, which is what "collision
 *       detection unpublished" means. See {@link #collisions}.</li>
 * </ul>
 *
 * <h3>⚠ Scheduled-inside-a-freeze IS detectable, unlike implemented-inside-a-freeze</h3>
 * S8f had to declare freeze violations undetectable because {@code implementation_started} is NULL on
 * every change, so the predicate would compare against NULL and report compliance forever. This
 * service checks a different and fully computable thing: {@code scheduled_start}, populated on 15 of
 * 17. "Did it happen during the freeze" is unknowable here; "is it planned to" is knowable — and it is
 * the more useful question, because it can still be acted on. That is the same catch-versus-breach
 * distinction the unauthorised-change register draws with {@code effective}.
 *
 * <p>What remains genuinely blind is stated in every response: a change with no
 * {@code scheduled_start} is invisible to collision detection, and {@code unscheduledCount} publishes
 * how many that is rather than letting a clean report imply coverage it lacks.
 */
@Slf4j
@Service
public class ChangeScheduleService {

    /** Statuses whose changes belong on a forward schedule — i.e. still ahead of, or in, delivery. */
    public static final List<String> FORWARD_STATUSES = List.of(
        "CAB_APPROVED", "EMERGENCY_APPROVED", "SCHEDULED", "IMPLEMENTING");

    // ── collision reasons. Closed vocabulary, i18n keys, each measured. ─────────────────────────

    /** Two changes whose windows overlap. The base case. */
    public static final String WINDOW_OVERLAP = "WINDOW_OVERLAP";
    /**
     * Two overlapping changes that both declare a service outage.
     *
     * <p>The worst case and the reason the PSO half and the collision half belong in one slice: two
     * simultaneous declared outages is a business-visible incident being scheduled on purpose.
     */
    public static final String OUTAGE_OVERLAP = "OUTAGE_OVERLAP";
    /** A change scheduled inside an active freeze window that does not exempt it. */
    public static final String INSIDE_FREEZE_WINDOW = "INSIDE_FREEZE_WINDOW";

    public static final List<String> COLLISION_REASONS =
        List.of(WINDOW_OVERLAP, OUTAGE_OVERLAP, INSIDE_FREEZE_WINDOW);

    private final ChangeRequestRepository changeRepo;
    private final ChangeFreezeWindowRepository freezeRepo;
    private final ChangeSchedulePublicationRepository pubRepo;

    public ChangeScheduleService(ChangeRequestRepository changeRepo,
                                @Autowired(required = false) ChangeFreezeWindowRepository freezeRepo,
                                @Autowired(required = false) ChangeSchedulePublicationRepository pubRepo) {
        this.changeRepo = changeRepo;
        this.freezeRepo = freezeRepo;
        this.pubRepo = pubRepo;
    }

    // ── records ─────────────────────────────────────────────────────────────────────────────────

    /** One entry on the forward schedule. */
    public record ScheduleEntry(
        Long changeId, String changeCode, String titleEn, String titleAr,
        String changeType, String riskLevel, String status,
        LocalDateTime windowStart, LocalDateTime windowEnd,
        boolean outageExpected, Integer outageMinutes, String servicesAffected,
        /** True when the declaration is complete — flag AND duration AND services. */
        boolean outageDeclared,
        /** Non-null when this entry sits inside an active freeze window that does not exempt it. */
        String freezeCode
    ) { }

    /** One collision between two scheduled changes, or between a change and a freeze window. */
    public record Collision(
        String reasonCode,
        Long changeId, String changeCode,
        /** The other change, or {@code null} for a freeze collision. */
        Long otherChangeId, String otherChangeCode,
        /** The freeze window, or {@code null} for a change-to-change collision. */
        String freezeCode, String freezeNameEn, String freezeNameAr,
        LocalDateTime overlapFrom, LocalDateTime overlapTo,
        boolean sameCategory
    ) { }

    /** The forward schedule, its collisions, and what it could not see. */
    public record ForwardSchedule(
        LocalDateTime from, LocalDateTime to,
        List<ScheduleEntry> entries,
        List<Collision> collisions,
        long outageCount, long totalOutageMinutes, long freezeCount,
        /** ⚠ Changes in a forward status with NO window — invisible to collision detection. */
        long unscheduledCount,
        Map<String, Long> collisionsByReason,
        /** The most recent publication, or {@code null} if the schedule has never been published. */
        PublicationView latestPublication,
        /** True when a publication exists but its window has already ended. */
        boolean latestPublicationStale
    ) { }

    /** A publication, without its snapshot payload. */
    public record PublicationView(
        Long id, LocalDateTime windowFrom, LocalDateTime windowTo,
        int entryCount, int outageCount, int freezeCount,
        String contentSha256, String publishedByName, LocalDateTime publishedAt, String notes
    ) { }

    // ── the forward schedule ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ForwardSchedule forwardSchedule(LocalDateTime from, LocalDateTime to) {
        Long org = TenantContext.getCurrentTenant();
        List<ChangeRequest> forward = changeRepo.findForwardScheduled(org, FORWARD_STATUSES, from, to);
        List<ChangeFreezeWindow> freezes = activeFreezes(org, from, to);

        List<ScheduleEntry> entries = new ArrayList<>();
        for (ChangeRequest c : forward) entries.add(entryFor(c, freezes));
        entries.sort(Comparator.comparing(ScheduleEntry::windowStart,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ScheduleEntry::changeId));

        List<Collision> collisions = detect(entries, freezes);
        Map<String, Long> byReason = new LinkedHashMap<>();
        for (String r : COLLISION_REASONS) byReason.put(r, 0L);
        for (Collision c : collisions) byReason.merge(c.reasonCode(), 1L, Long::sum);

        ChangeSchedulePublication latest = pubRepo == null ? null
            : pubRepo.findFirstByOrganisationIdOrderByPublishedAtDescIdDesc(org).orElse(null);

        return new ForwardSchedule(from, to, entries, collisions,
            entries.stream().filter(ScheduleEntry::outageDeclared).count(),
            entries.stream().filter(ScheduleEntry::outageDeclared)
                   .mapToLong(e -> e.outageMinutes() == null ? 0 : e.outageMinutes()).sum(),
            entries.stream().filter(e -> e.freezeCode() != null).count(),
            changeRepo.countForwardWithoutWindow(org, FORWARD_STATUSES),
            byReason,
            latest == null ? null : view(latest),
            latest != null && latest.getWindowTo().isBefore(LocalDateTime.now()));
    }

    /** Schedule-wide collisions for a window — the half {@code conflicts(id)} could not answer. */
    @Transactional(readOnly = true)
    public List<Collision> collisions(LocalDateTime from, LocalDateTime to) {
        return forwardSchedule(from, to).collisions();
    }

    // ── publication ─────────────────────────────────────────────────────────────────────────────

    /**
     * Publish the forward schedule for a window.
     *
     * <p>Snapshots the entries as they stand and digests them, so "what did we tell the business on
     * the 4th?" has an answer that today's rows cannot contradict. {@code publishedBy*} and
     * {@code publishedAt} come from the authenticated principal and the server clock — the same reason
     * {@code ck_ucd_disposed} and {@code ck_cmodel_preapproval} are sufficient only alongside a narrow
     * DTO.
     *
     * <p><b>Refuses to publish an empty schedule.</b> Publishing "nothing is planned" as an artefact
     * is indistinguishable from publishing before anyone entered the plan, and a tenant that has
     * genuinely nothing scheduled does not need a document to say so. The 409 says which.
     */
    @Transactional
    public ChangeSchedulePublication publish(LocalDateTime from, LocalDateTime to, String notes,
                                             Long actorId, String actorName) {
        if (pubRepo == null)
            throw new IllegalStateException("Schedule publication storage is not available (V356 not applied)");
        if (from == null || to == null || !to.isAfter(from))
            throw new IllegalArgumentException(
                "The published window must end after it starts — ck_csp_window refuses anything else.");

        Long org = TenantContext.getCurrentTenant();
        ForwardSchedule fsc = forwardSchedule(from, to);
        if (fsc.entries().isEmpty())
            throw new IllegalStateException(
                "There is nothing scheduled between " + from + " and " + to + ", so there is no forward "
              + "schedule to publish. Publishing an empty document would be indistinguishable from "
              + "publishing before the plan was entered.");

        String json = toJson(fsc.entries());
        ChangeSchedulePublication p = new ChangeSchedulePublication();
        p.setOrganisationId(org);
        p.setWindowFrom(from);
        p.setWindowTo(to);
        p.setEntryCount(fsc.entries().size());
        p.setOutageCount((int) fsc.outageCount());
        p.setFreezeCount((int) fsc.freezeCount());
        p.setEntriesJson(json);
        p.setContentSha256(sha256(json));
        p.setPublishedById(actorId);
        p.setPublishedByName(actorName == null || actorName.isBlank() ? "unknown" : actorName.trim());
        p.setPublishedAt(LocalDateTime.now());
        p.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        p.setCreatedAt(LocalDateTime.now());
        return pubRepo.save(p);
    }

    /** Publication history, newest first. The FSC's own audit trail. */
    @Transactional(readOnly = true)
    public List<PublicationView> publications() {
        if (pubRepo == null) return List.of();
        return pubRepo.findByOrganisationIdOrderByPublishedAtDescIdDesc(TenantContext.getCurrentTenant())
                      .stream().map(ChangeScheduleService::view).toList();
    }

    /**
     * One publication with its snapshot — what was actually published, not what is true now.
     *
     * <p>Re-digests the stored JSON and reports whether it still matches {@code contentSha256}, so a
     * reader is told about tampering rather than having to check. A mismatch is not an exception: the
     * document is still the best record of what was published, and hiding it behind a 500 would
     * destroy the evidence rather than surface it.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> publication(Long id) {
        if (pubRepo == null)
            throw new IllegalStateException("Schedule publication storage is not available");
        ChangeSchedulePublication p = pubRepo
            .findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new IllegalArgumentException("No published schedule " + id));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publication", view(p));
        out.put("entriesJson", p.getEntriesJson());
        String recomputed = sha256(p.getEntriesJson());
        out.put("digestMatches", recomputed.equals(p.getContentSha256()));
        out.put("recomputedSha256", recomputed);
        return out;
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────────

    private List<ChangeFreezeWindow> activeFreezes(Long org, LocalDateTime from, LocalDateTime to) {
        if (freezeRepo == null) return List.of();
        return freezeRepo
            .findByOrganisationIdAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(org, to, from);
    }

    private ScheduleEntry entryFor(ChangeRequest c, List<ChangeFreezeWindow> freezes) {
        return new ScheduleEntry(c.getId(), c.getChangeCode(), c.getTitleEn(), c.getTitleAr(),
            c.getChangeType(), c.getRiskLevel(), c.getStatus(),
            c.getScheduledStart(), c.getScheduledEnd(),
            Boolean.TRUE.equals(c.getOutageExpected()), c.getOutageMinutes(), c.getServicesAffected(),
            c.hasDeclaredOutage(), freezeHit(c, freezes));
    }

    /**
     * The code of the freeze window this change is scheduled inside, or {@code null}.
     *
     * <p>⚠ {@code allowEmergency} is honoured: a freeze that permits emergency work does not collide
     * with an EMERGENCY change, and reporting it as a violation would train operators to ignore the
     * list. That flag is set on all three seeded windows, so getting it wrong would have produced a
     * false positive on every emergency change in every tenant.
     */
    private String freezeHit(ChangeRequest c, List<ChangeFreezeWindow> freezes) {
        LocalDateTime start = c.getScheduledStart();
        if (start == null) return null;
        LocalDateTime end = c.getScheduledEnd() == null ? start : c.getScheduledEnd();
        for (ChangeFreezeWindow f : freezes) {
            if (Boolean.TRUE.equals(f.getAllowEmergency()) && "EMERGENCY".equals(c.getChangeType()))
                continue;
            if (f.getStartsAt() == null || f.getEndsAt() == null) continue;
            if (overlaps(start, end, f.getStartsAt(), f.getEndsAt())) return f.getCode();
        }
        return null;
    }

    private List<Collision> detect(List<ScheduleEntry> entries, List<ChangeFreezeWindow> freezes) {
        List<Collision> out = new ArrayList<>();
        Map<String, ChangeFreezeWindow> byCode = new HashMap<>();
        for (ChangeFreezeWindow f : freezes) byCode.put(f.getCode(), f);

        for (ScheduleEntry e : entries) {
            if (e.freezeCode() == null) continue;
            ChangeFreezeWindow f = byCode.get(e.freezeCode());
            out.add(new Collision(INSIDE_FREEZE_WINDOW, e.changeId(), e.changeCode(),
                null, null, e.freezeCode(),
                f == null ? null : f.getNameEn(), f == null ? null : f.getNameAr(),
                e.windowStart(), e.windowEnd(), false));
        }

        // Pairwise, each pair once. The population is a single tenant's forward window, so this is
        // tens of rows — the readable loop is the right trade against a self-join here.
        for (int i = 0; i < entries.size(); i++) {
            ScheduleEntry a = entries.get(i);
            if (a.windowStart() == null) continue;
            LocalDateTime aEnd = a.windowEnd() == null ? a.windowStart() : a.windowEnd();
            for (int j = i + 1; j < entries.size(); j++) {
                ScheduleEntry b = entries.get(j);
                if (b.windowStart() == null) continue;
                LocalDateTime bEnd = b.windowEnd() == null ? b.windowStart() : b.windowEnd();
                if (!overlaps(a.windowStart(), aEnd, b.windowStart(), bEnd)) continue;
                boolean bothOut = a.outageDeclared() && b.outageDeclared();
                out.add(new Collision(bothOut ? OUTAGE_OVERLAP : WINDOW_OVERLAP,
                    a.changeId(), a.changeCode(), b.changeId(), b.changeCode(),
                    null, null, null,
                    max(a.windowStart(), b.windowStart()), min(aEnd, bEnd),
                    Objects.equals(a.changeType(), b.changeType())));
            }
        }
        // Outage clashes first: two simultaneous declared outages is the one an operator must see.
        out.sort(Comparator.comparingInt(c -> COLLISION_REASONS.indexOf(c.reasonCode()) == 1 ? 0 : 1));
        return out;
    }

    private static boolean overlaps(LocalDateTime aFrom, LocalDateTime aTo,
                                    LocalDateTime bFrom, LocalDateTime bTo) {
        return !aFrom.isAfter(bTo) && !bFrom.isAfter(aTo);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }

    private static PublicationView view(ChangeSchedulePublication p) {
        return new PublicationView(p.getId(), p.getWindowFrom(), p.getWindowTo(),
            p.getEntryCount(), p.getOutageCount(), p.getFreezeCount(),
            p.getContentSha256(), p.getPublishedByName(), p.getPublishedAt(), p.getNotes());
    }

    /**
     * SHA-256 of the snapshot, lower-case hex.
     *
     * <p>Same algorithm the evidence subsystem uses. {@code ck_csp_published} requires 64 characters,
     * so a truncated or absent digest cannot reach the table.
     */
    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }

    /**
     * The snapshot, as deterministic JSON.
     *
     * <p>Hand-built rather than via an {@code ObjectMapper} field order, because the digest must be
     * reproducible: a serialiser that reorders keys between versions would make every stored digest
     * fail to verify, and the tamper-evidence claim would degrade into noise the first time the
     * library was upgraded.
     */
    static String toJson(List<ScheduleEntry> entries) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            ScheduleEntry e = entries.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
              .append("\"changeId\":").append(e.changeId()).append(',')
              .append("\"changeCode\":").append(q(e.changeCode())).append(',')
              .append("\"titleEn\":").append(q(e.titleEn())).append(',')
              .append("\"titleAr\":").append(q(e.titleAr())).append(',')
              .append("\"changeType\":").append(q(e.changeType())).append(',')
              .append("\"riskLevel\":").append(q(e.riskLevel())).append(',')
              .append("\"status\":").append(q(e.status())).append(',')
              .append("\"windowStart\":").append(q(str(e.windowStart()))).append(',')
              .append("\"windowEnd\":").append(q(str(e.windowEnd()))).append(',')
              .append("\"outageDeclared\":").append(e.outageDeclared()).append(',')
              .append("\"outageMinutes\":").append(e.outageMinutes() == null ? "null" : e.outageMinutes())
              .append(',')
              .append("\"servicesAffected\":").append(q(e.servicesAffected())).append(',')
              .append("\"freezeCode\":").append(q(e.freezeCode()))
              .append('}');
        }
        return sb.append(']').toString();
    }

    private static String str(LocalDateTime t) { return t == null ? null : t.toString(); }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
