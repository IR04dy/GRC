package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.CheckResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ScreenRequest;
import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import com.tss.grc.module.aml.sanctions.entity.SanctionsListEntry;
import com.tss.grc.module.aml.sanctions.entity.ScreeningCheck;
import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListEntryRepository;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningCheckRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sprint 13 — Sanctions screening service. Performs the actual
 * subject-vs-list-entry matching and persists checks + hits.
 *
 * <h3>Fuzzy matching scoring formula</h3>
 *
 * <p>For each candidate list entry, the matcher computes a base name
 * score and adds modifier boosts. The result is capped at 100:
 *
 * <pre>
 *   exact normalized name match          → base 100, match_type EXACT_NAME
 *   normalized name match after token    → base  90, match_type FUZZY_NAME
 *      sort + diacritic strip
 *   alias hit (after the same normalize) → base  85, match_type ALIAS
 *
 *   boosts (added on top of the base name score):
 *     subject DOB == entry DOB          → +5    match_type promoted to COMPOSITE
 *     subject country == entry country  → +3    match_type promoted to COMPOSITE
 *     subject ID doc == entry ID doc    → +10   match_type promoted to COMPOSITE
 *
 *   final = min(100, base + boosts)
 * </pre>
 *
 * <p>Threshold for {@code POTENTIAL_HIT} = 75. Entries scoring below 75
 * are dropped; entries at or above 75 produce a hit row in
 * {@code aml.screening_hit}. The check's aggregate {@code result} is
 * NO_HIT when zero hits cross the threshold, POTENTIAL_HIT otherwise.
 * A reviewer later promotes a hit to TRUE_POSITIVE/CONFIRMED_HIT (in
 * {@link SanctionsHitService}); the check's {@code result} is updated
 * accordingly when that happens.
 *
 * <p>F8 events emitted: {@code SANCTIONS_CHECK_PERFORMED} (every check),
 * {@code SANCTIONS_HIT_OPENED} (per new hit).
 */
@Service
@RequiredArgsConstructor
public class SanctionsScreeningService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreeningService.class);

    /** Score at or above which a candidate is persisted as a hit. */
    public static final int POTENTIAL_HIT_THRESHOLD = 75;
    public static final int EXACT_NAME_SCORE = 100;
    public static final int FUZZY_NAME_SCORE = 90;
    public static final int ALIAS_SCORE = 85;
    public static final int DOB_BOOST = 5;
    public static final int COUNTRY_BOOST = 3;
    public static final int DOCUMENT_BOOST = 10;

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern WS = Pattern.compile("\\s+");

    private final ScreeningCheckRepository checkRepo;
    private final ScreeningHitRepository hitRepo;
    private final SanctionsListRepository listRepo;
    private final SanctionsListEntryRepository entryRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() { return TenantContext.requireOrganisationId(); }

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CheckResponse> listChecks() {
        Long orgId = org();
        return checkRepo.findTop100ByOrganisationIdAndIsDeletedFalseOrderByCheckAtDesc(orgId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CheckResponse> listChecksByResult(String result) {
        Long orgId = org();
        return checkRepo.findByOrganisationIdAndResultAndIsDeletedFalseOrderByCheckAtDesc(orgId, result)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CheckResponse getCheck(Long id) {
        return toResponse(loadCheckOrThrow(id));
    }

    // ─── Writes ───────────────────────────────────────────────────────

    /**
     * Run the screening matcher and persist the check + any hits in
     * one transaction. {@link ScreenRequest#listsToScreen()} narrows
     * the candidate set when supplied; null / empty = every active
     * visible list.
     */
    @Transactional
    public CheckResponse screen(ScreenRequest req) {
        Long orgId = org();
        // 1. Resolve which lists to screen against
        List<String> listCodes = req.listsToScreen() == null || req.listsToScreen().isEmpty()
            ? listRepo.findActiveVisibleTo(orgId).stream().map(SanctionsList::getListCode).toList()
            : req.listsToScreen();

        // 2. Load candidate entries
        List<SanctionsListEntry> candidates = listCodes.isEmpty()
            ? List.of()
            : entryRepo.findActiveByListCodesVisibleTo(listCodes, orgId);

        // 3. Score each candidate
        List<MatchResult> matches = new ArrayList<>();
        String subjectNorm = normalise(req.subjectNameEn());
        String subjectArNorm = normalise(req.subjectNameAr());
        for (SanctionsListEntry e : candidates) {
            MatchResult m = score(req, subjectNorm, subjectArNorm, e);
            if (m != null) matches.add(m);
        }

        // 4. Persist the check
        ScreeningCheck c = new ScreeningCheck();
        c.setOrganisationId(orgId);
        c.setCheckCode(nextCheckCode(orgId));
        c.setSubjectType(req.subjectType());
        c.setSubjectReference(req.subjectReference());
        c.setSubjectNameEn(req.subjectNameEn());
        c.setSubjectNameAr(req.subjectNameAr());
        c.setSubjectDob(req.subjectDob());
        c.setSubjectCountry(req.subjectCountry());
        c.setSubjectIdDocument(req.subjectIdDocument());
        c.setCheckAt(LocalDateTime.now());
        c.setCheckSource(req.checkSource() == null ? "MANUAL" : req.checkSource());
        c.setListsScreenedCodes(String.join(",", listCodes));
        c.setResult(matches.isEmpty() ? "NO_HIT" : "POTENTIAL_HIT");
        ScreeningCheck savedCheck = checkRepo.save(c);

        // 5. Persist the hits
        List<ScreeningHit> hits = new ArrayList<>();
        for (MatchResult m : matches) {
            ScreeningHit h = new ScreeningHit();
            h.setOrganisationId(orgId);
            h.setCheckId(savedCheck.getId());
            h.setListEntryId(m.entry.getId());
            h.setMatchScore(BigDecimal.valueOf(m.score).setScale(2, RoundingMode.HALF_UP));
            h.setMatchType(m.matchType);
            h.setStatus("PENDING_REVIEW");
            hits.add(hitRepo.save(h));
        }

        // 6. Audit log + F8 events
        audit.log("SCREEN", "ScreeningCheck", savedCheck.getId(),
            "Screening check " + savedCheck.getCheckCode() + " — " + savedCheck.getResult()
                + " (" + hits.size() + " hit(s) across " + listCodes.size() + " list(s))");

        emitCheckPerformed(savedCheck, hits.size());
        for (ScreeningHit h : hits) {
            emitHitOpened(h, savedCheck);
        }

        return toResponse(savedCheck);
    }

    // ─── Fuzzy matcher ────────────────────────────────────────────────

    record MatchResult(SanctionsListEntry entry, double score, String matchType) {}

    /**
     * Score one candidate entry against the subject. Returns null when
     * the candidate does not cross the threshold; otherwise returns
     * {@code (entry, score, matchType)} where {@code score} is in
     * {@code [POTENTIAL_HIT_THRESHOLD, 100]}.
     *
     * <p>Visible for the unit tests.
     */
    MatchResult score(ScreenRequest req, String subjectNorm, String subjectArNorm,
                      SanctionsListEntry entry) {
        // 1. Name base score — try exact, then fuzzy, then alias
        int base = 0;
        String matchType = null;
        String entryNorm = normalise(entry.getPrimaryNameEn());
        String entryArNorm = normalise(entry.getPrimaryNameAr());

        if (subjectNorm.equals(entryNorm) && !subjectNorm.isEmpty()) {
            base = EXACT_NAME_SCORE;
            matchType = "EXACT_NAME";
        } else if (subjectArNorm != null && !subjectArNorm.isEmpty()
            && entryArNorm != null && subjectArNorm.equals(entryArNorm)) {
            base = EXACT_NAME_SCORE;
            matchType = "EXACT_NAME";
        } else {
            // Token-sort fuzzy match (any-order, normalised)
            if (tokenSortMatch(subjectNorm, entryNorm) ||
                (subjectArNorm != null && entryArNorm != null && tokenSortMatch(subjectArNorm, entryArNorm))) {
                base = FUZZY_NAME_SCORE;
                matchType = "FUZZY_NAME";
            } else {
                // Try aliases
                List<String> aliases = parseAliases(entry.getAliasesJson());
                for (String alias : aliases) {
                    String aliasNorm = normalise(alias);
                    if (aliasNorm.isEmpty()) continue;
                    if (subjectNorm.equals(aliasNorm)
                        || (subjectArNorm != null && subjectArNorm.equals(aliasNorm))
                        || tokenSortMatch(subjectNorm, aliasNorm)
                        || (subjectArNorm != null && tokenSortMatch(subjectArNorm, aliasNorm))) {
                        base = ALIAS_SCORE;
                        matchType = "ALIAS";
                        break;
                    }
                }
            }
        }

        if (base == 0) return null; // no name signal — bail before threshold check

        // 2. Modifier boosts — DOB, country, document.
        int score = base;
        boolean composite = false;
        if (req.subjectDob() != null && entry.getDateOfBirth() != null
            && req.subjectDob().equals(entry.getDateOfBirth())) {
            score += DOB_BOOST;
            composite = true;
        }
        if (req.subjectCountry() != null && entry.getCountry() != null
            && req.subjectCountry().trim().equalsIgnoreCase(entry.getCountry().trim())) {
            score += COUNTRY_BOOST;
            composite = true;
        }
        if (req.subjectIdDocument() != null && !req.subjectIdDocument().isBlank()
            && entry.getIdDocumentsJson() != null
            && documentMatches(req.subjectIdDocument(), entry.getIdDocumentsJson())) {
            score += DOCUMENT_BOOST;
            composite = true;
        }
        if (score > 100) score = 100;
        if (composite) matchType = "COMPOSITE";

        if (score < POTENTIAL_HIT_THRESHOLD) return null;
        return new MatchResult(entry, score, matchType);
    }

    /**
     * Lower-case, strip diacritics, strip punctuation, collapse whitespace.
     * Visible for tests.
     */
    static String normalise(String s) {
        if (s == null) return "";
        String stripped = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            // Arabic harakat (vowels + tatweel) too
            .replaceAll("[ً-ٰٟـ]", "");
        String cleaned = NON_WORD.matcher(stripped).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        return WS.matcher(cleaned).replaceAll(" ");
    }

    /**
     * True when both strings contain the same set of tokens (any order)
     * after normalisation. Catches "Mohammed Saeed Khalifa" vs.
     * "Saeed Mohammed Khalifa" without becoming overly permissive.
     */
    static boolean tokenSortMatch(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        Set<String> ta = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> tb = new HashSet<>(Arrays.asList(b.split(" ")));
        // Strip stub tokens (single letter / empties) on both sides
        ta.removeIf(s -> s.length() <= 1);
        tb.removeIf(s -> s.length() <= 1);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        return ta.equals(tb);
    }

    /** Extract alias strings from a JSON array; tolerant of malformed JSON. */
    static List<String> parseAliases(String aliasesJson) {
        if (aliasesJson == null || aliasesJson.isBlank()) return List.of();
        // Lightweight regex parser — avoids the Jackson dep on this hot path.
        // We accept ["alias1","alias2",...] and tolerate spaces/newlines.
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        var m = p.matcher(aliasesJson);
        while (m.find()) {
            String raw = m.group(1).replace("\\\"", "\"");
            if (!raw.isBlank()) out.add(raw);
        }
        return out;
    }

    /**
     * True when the supplied document number appears anywhere in the
     * entry's id_documents_json blob. Trivial substring match — the
     * goal is the +10 boost, not bullet-proof document validation.
     */
    static boolean documentMatches(String subjectDoc, String idDocsJson) {
        if (subjectDoc == null || idDocsJson == null) return false;
        String needle = subjectDoc.trim();
        if (needle.length() < 4) return false; // avoid noisy 1-char matches
        return idDocsJson.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private ScreeningCheck loadCheckOrThrow(Long id) {
        return checkRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Screening check not found: " + id));
    }

    /**
     * Generate the next SCR-{year}-{6-digit-seq} code for the tenant.
     *
     * <p>F2-375 — a bare {@code count(...) + 1} seed collides on the filtered unique index
     * {@code UX_screening_check_org_code} when two screens run concurrently for the same tenant
     * (both read the same count, both mint the same code) → an opaque 500 and a LOST sanctions
     * screening. Skip-loop backstopped by the unique index (mirrors
     * {@code WhistleblowerCaseService#generateCode}): the loser retries the next free ordinal, and
     * a random-suffix fallback guarantees termination. {@code existsBy...IsDeletedFalse} and the
     * filtered index agree, so the check sees exactly what the constraint enforces.
     */
    String nextCheckCode(Long orgId) {
        int year = Year.now().getValue();
        long total = checkRepo.countByOrganisationIdAndIsDeletedFalse(orgId);
        for (long seq = total + 1; seq < total + 1000; seq++) {
            String candidate = String.format(Locale.ROOT, "SCR-%d-%06d", year, seq);
            if (!checkRepo.existsByOrganisationIdAndCheckCodeAndIsDeletedFalse(orgId, candidate)) {
                return candidate;
            }
        }
        return "SCR-" + year + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    CheckResponse toResponse(ScreeningCheck c) {
        Long orgId = c.getOrganisationId();
        List<ScreeningHit> hitRows = hitRepo
            .findByOrganisationIdAndCheckIdAndIsDeletedFalseOrderByMatchScoreDesc(orgId, c.getId());
        // Materialise list_code per hit for the UI
        Map<Long, String> listCodeByEntry = new HashMap<>();
        Map<Long, String> nameEnByEntry = new HashMap<>();
        Map<Long, String> nameArByEntry = new HashMap<>();
        for (ScreeningHit h : hitRows) {
            if (!nameEnByEntry.containsKey(h.getListEntryId())) {
                entryRepo.findById(h.getListEntryId()).ifPresent(e -> {
                    nameEnByEntry.put(e.getId(), e.getPrimaryNameEn());
                    nameArByEntry.put(e.getId(), e.getPrimaryNameAr());
                    listRepo.findById(e.getListId()).ifPresent(l ->
                        listCodeByEntry.put(e.getId(), l.getListCode()));
                });
            }
        }
        List<HitResponse> hits = hitRows.stream().map(h -> new HitResponse(
            h.getId(), h.getExternalId(), h.getCheckId(), h.getListEntryId(),
            listCodeByEntry.get(h.getListEntryId()),
            nameEnByEntry.get(h.getListEntryId()),
            nameArByEntry.get(h.getListEntryId()),
            h.getMatchScore(), h.getMatchType(), h.getStatus(),
            h.getReviewerUserId(), h.getReviewedAt(),
            h.getDispositionEn(), h.getDispositionAr(),
            h.getSlaBreach(),
            h.getEscalatedToUserId(), h.getEscalatedAt(),
            h.getCapaTaskId(), h.getCreatedAt()
        )).collect(Collectors.toList());

        return new CheckResponse(
            c.getId(), c.getExternalId(), c.getOrganisationId(),
            c.getCheckCode(), c.getSubjectType(), c.getSubjectReference(),
            c.getSubjectNameEn(), c.getSubjectNameAr(),
            c.getSubjectDob(), c.getSubjectCountry(), c.getSubjectIdDocument(),
            c.getCheckAt(), c.getCheckSource(), c.getListsScreenedCodes(),
            c.getResult(), hits,
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    void emitCheckPerformed(ScreeningCheck c, int hitCount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("checkId", String.valueOf(c.getId()));
        payload.put("checkCode", c.getCheckCode());
        payload.put("subjectType", c.getSubjectType());
        payload.put("subjectReference", c.getSubjectReference());
        payload.put("subjectNameEn", c.getSubjectNameEn());
        payload.put("result", c.getResult());
        payload.put("hitCount", hitCount);
        publisher.publishEvent(new DomainEvent(
            c.getOrganisationId(), "SANCTIONS_CHECK_PERFORMED",
            "ScreeningCheck", c.getId(),
            "sanctions-check:" + c.getId(), payload));
    }

    void emitHitOpened(ScreeningHit h, ScreeningCheck c) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hitId", String.valueOf(h.getId()));
        payload.put("checkId", String.valueOf(h.getCheckId()));
        payload.put("checkCode", c.getCheckCode());
        payload.put("listEntryId", String.valueOf(h.getListEntryId()));
        payload.put("matchScore", h.getMatchScore().toPlainString());
        payload.put("matchType", h.getMatchType());
        payload.put("subjectNameEn", c.getSubjectNameEn());
        publisher.publishEvent(new DomainEvent(
            h.getOrganisationId(), "SANCTIONS_HIT_OPENED",
            "ScreeningHit", h.getId(),
            "sanctions-hit:" + h.getId(), payload));
    }
}
