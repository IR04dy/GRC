package com.tss.grc.module.platform.help.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpLookupResponse;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicRequest;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicResponse;
import com.tss.grc.module.platform.help.entity.HelpTopic;
import com.tss.grc.module.platform.help.repository.HelpTopicRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HelpTopicService} — Sprint 12 Embedded Help.
 *
 * <p>Locks the contract:
 * <ul>
 *   <li>route lookup — exact match, wildcard match, normalisation</li>
 *   <li>org-override precedence — tenant row wins over global</li>
 *   <li>route-pattern precedence — exact wins over wildcard</li>
 *   <li>search by title / body</li>
 *   <li>F7 audit + F8 event emission on every mutation + track-view</li>
 *   <li>tenant isolation</li>
 *   <li>topic_code uniqueness on CREATE</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HelpTopicServiceTest {

    private static final Long ORG_ID = 1L;
    private static final UUID EX = UUID.randomUUID();

    @Mock private HelpTopicRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher events;

    private HelpTopicService service;

    @BeforeEach
    void setUp() {
        service = new HelpTopicService(repo, audit, events);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static HelpTopic topic(Long orgId, String code, String route, String module) {
        HelpTopic h = new HelpTopic();
        h.setOrganisationId(orgId);
        h.setTopicCode(code);
        h.setTitleEn("Help title " + code);
        h.setTitleAr("عنوان " + code);
        h.setBodyEn("## Body");
        h.setBodyAr("## النص");
        h.setRoutePattern(route);
        h.setModule(module);
        h.setSeverity("INFO");
        h.setDisplayOrder(100);
        return h;
    }

    private static HelpTopicRequest req(String code, String route) {
        return new HelpTopicRequest(
                code, "Title", "العنوان", null, null,
                "## Body", "## النص",
                route, "RISK",
                "SAMA CSF 2.0", "https://sama.gov.sa",
                null, null, "INFO", 50);
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── lookupForRoute ───────────────────────────────────────────

    @Test
    void lookup_exactMatch_returnsTopic() {
        HelpTopic h = topic(null, "HELP-RISK", "/risk", "RISK");
        when(repo.findAllVisible(ORG_ID)).thenReturn(List.of(h));

        HelpLookupResponse out = service.lookupForRoute("/risk");

        assertThat(out.route()).isEqualTo("/risk");
        assertThat(out.topics()).hasSize(1);
        assertThat(out.topics().get(0).topicCode()).isEqualTo("HELP-RISK");
    }

    @Test
    void lookup_wildcardMatch_returnsTopic() {
        HelpTopic h = topic(null, "HELP-OBSERVER", "/bcm/exercises/run/*/observe", "BCM");
        when(repo.findAllVisible(ORG_ID)).thenReturn(List.of(h));

        HelpLookupResponse out = service.lookupForRoute("/bcm/exercises/run/run-42/observe");

        assertThat(out.topics()).hasSize(1);
        assertThat(out.topics().get(0).topicCode()).isEqualTo("HELP-OBSERVER");
    }

    @Test
    void lookup_normalisesTrailingSlashAndQueryString() {
        HelpTopic h = topic(null, "HELP-RISK", "/risk", "RISK");
        when(repo.findAllVisible(ORG_ID)).thenReturn(List.of(h));

        HelpLookupResponse a = service.lookupForRoute("/risk/");
        HelpLookupResponse b = service.lookupForRoute("/risk?foo=bar");
        HelpLookupResponse c = service.lookupForRoute("/risk#section");

        assertThat(a.topics()).hasSize(1);
        assertThat(b.topics()).hasSize(1);
        assertThat(c.topics()).hasSize(1);
        assertThat(a.route()).isEqualTo("/risk");
        assertThat(b.route()).isEqualTo("/risk");
        assertThat(c.route()).isEqualTo("/risk");
    }

    @Test
    void lookup_orgOverrideTakesPrecedenceOverGlobal() {
        HelpTopic global = topic(null, "HELP-RISK-REGISTER", "/risk", "RISK");
        global.setTitleEn("Global title");
        HelpTopic override = topic(ORG_ID, "HELP-RISK-REGISTER", "/risk", "RISK");
        override.setTitleEn("Org override title");
        when(repo.findAllVisible(ORG_ID)).thenReturn(List.of(global, override));

        HelpLookupResponse out = service.lookupForRoute("/risk");

        assertThat(out.topics()).hasSize(1);
        // Same topic_code; the tenant row wins.
        assertThat(out.topics().get(0).titleEn()).isEqualTo("Org override title");
        assertThat(out.topics().get(0).organisationId()).isEqualTo(ORG_ID);
    }

    @Test
    void lookup_exactBeatsWildcardOnPrecedenceSort() {
        // Two distinct topic_codes both match the same wildcard-friendly
        // route. Exact path is more specific than wildcard, so it sorts
        // first; the wildcard pattern uses a child segment so both rows
        // legitimately match `/cyber/findings`.
        HelpTopic exact = topic(null, "HELP-EXACT", "/cyber/findings", "CYBER");
        HelpTopic wild  = topic(null, "HELP-WILD",  "/cyber/*", "CYBER");
        when(repo.findAllVisible(ORG_ID)).thenReturn(List.of(wild, exact));

        HelpLookupResponse out = service.lookupForRoute("/cyber/findings");

        assertThat(out.topics()).hasSize(2);
        assertThat(out.topics().get(0).topicCode()).isEqualTo("HELP-EXACT");
        assertThat(out.topics().get(1).topicCode()).isEqualTo("HELP-WILD");
    }

    @Test
    void lookup_unknownRoute_returnsEmpty() {
        when(repo.findAllVisible(ORG_ID)).thenReturn(List.of(
                topic(null, "HELP-RISK", "/risk", "RISK")
        ));
        HelpLookupResponse out = service.lookupForRoute("/some-completely-unmapped-path");
        assertThat(out.topics()).isEmpty();
    }

    @Test
    void normaliseRoute_lowercasesAndStripsTrailing() {
        assertThat(HelpTopicService.normaliseRoute("/Risk/Register/")).isEqualTo("/risk/register");
        assertThat(HelpTopicService.normaliseRoute("")).isEqualTo("/");
        assertThat(HelpTopicService.normaliseRoute(null)).isEqualTo("/");
        assertThat(HelpTopicService.normaliseRoute("risk")).isEqualTo("/risk");
    }

    @Test
    void matches_wildcardSegmentDoesNotCrossSlash() {
        // /cyber/* must NOT match /cyber/findings/123 — single-segment wildcard.
        assertThat(HelpTopicService.matches("/cyber/*", "/cyber/findings")).isTrue();
        assertThat(HelpTopicService.matches("/cyber/*", "/cyber/findings/123")).isFalse();
        assertThat(HelpTopicService.matches("/cyber/*/details", "/cyber/findings/details")).isTrue();
    }

    // ─── search ───────────────────────────────────────────────────

    @Test
    void search_dispatchesQueryAndMapsResults() {
        HelpTopic h = topic(null, "HELP-RISK", "/risk", "RISK");
        when(repo.search(ORG_ID, "risk")).thenReturn(List.of(h));

        List<HelpTopicResponse> out = service.searchTopics(" risk ");

        assertThat(out).hasSize(1);
        verify(repo).search(ORG_ID, "risk");
    }

    @Test
    void search_blankQuery_returnsEmptyWithoutDbHit() {
        List<HelpTopicResponse> out = service.searchTopics("   ");
        assertThat(out).isEmpty();
        verify(repo, never()).search(anyLong(), anyString());
    }

    // ─── create ───────────────────────────────────────────────────

    @Test
    void create_stampsTenantAndEmitsEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(repo.save(any(HelpTopic.class))).thenAnswer(assignId(seq));
        when(repo.countByTopicCodeAndIsDeletedFalse("HELP-NEW")).thenReturn(0L);

        HelpTopicResponse out = service.create(req("HELP-NEW", "/new"));

        assertThat(out.topicCode()).isEqualTo("HELP-NEW");
        assertThat(out.organisationId()).isEqualTo(ORG_ID);

        ArgumentCaptor<HelpTopic> cap = ArgumentCaptor.forClass(HelpTopic.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);

        verify(audit).log(eq("CREATE"), eq("HelpTopic"), anyLong(), anyString());

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishEvent(evCap.capture());
        DomainEvent ev = evCap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("HELP_TOPIC_CREATED");
        assertThat(ev.entityType()).isEqualTo("HelpTopic");
        assertThat(ev.payload()).containsEntry("topicCode", "HELP-NEW");
        assertThat(ev.payload()).containsEntry("module", "RISK");
    }

    @Test
    void create_duplicateCode_rejected() {
        when(repo.countByTopicCodeAndIsDeletedFalse("HELP-DUP")).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req("HELP-DUP", "/dup")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(repo, never()).save(any());
    }

    // ─── update ───────────────────────────────────────────────────

    @Test
    void update_loadsByExternalIdAndEmitsUpdatedEvent() {
        HelpTopic existing = topic(ORG_ID, "HELP-RISK", "/risk", "RISK");
        existing.setId(42L);
        existing.setExternalId(EX);
        when(repo.findByExternalIdAndIsDeletedFalse(EX)).thenReturn(Optional.of(existing));
        when(repo.save(any(HelpTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        HelpTopicRequest r = new HelpTopicRequest(
                "HELP-RISK", "New title", "عنوان جديد", null, null,
                "## Body 2", "## النص 2",
                "/risk", "RISK", null, null, null, null, "TIP", 50);
        HelpTopicResponse out = service.update(EX, r);

        assertThat(out.titleEn()).isEqualTo("New title");
        assertThat(out.severity()).isEqualTo("TIP");

        verify(audit).log(eq("UPDATE"), eq("HelpTopic"), anyLong(), anyString());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("HELP_TOPIC_UPDATED");
    }

    @Test
    void update_unknownExternalId_throws() {
        when(repo.findByExternalIdAndIsDeletedFalse(EX)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(EX, req("HELP-X", "/x")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── delete ───────────────────────────────────────────────────

    @Test
    void delete_softDeletesAndEmitsEvent() {
        HelpTopic existing = topic(ORG_ID, "HELP-RISK", "/risk", "RISK");
        existing.setId(42L);
        existing.setExternalId(EX);
        when(repo.findByExternalIdAndIsDeletedFalse(EX)).thenReturn(Optional.of(existing));
        when(repo.save(any(HelpTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(EX);

        ArgumentCaptor<HelpTopic> saved = ArgumentCaptor.forClass(HelpTopic.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getIsDeleted()).isTrue();

        verify(audit).log(eq("DELETE"), eq("HelpTopic"), anyLong(), anyString());

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishEvent(evCap.capture());
        assertThat(evCap.getValue().triggerEvent()).isEqualTo("HELP_TOPIC_DELETED");
    }

    // ─── trackView ────────────────────────────────────────────────

    @Test
    void trackView_writesAuditAndEmitsViewedEvent() {
        HelpTopic existing = topic(null, "HELP-RISK", "/risk", "RISK");
        existing.setId(42L);
        existing.setExternalId(EX);
        when(repo.findByExternalIdAndIsDeletedFalse(EX)).thenReturn(Optional.of(existing));

        service.trackView(EX, "/risk/register");

        verify(audit).log(eq("VIEW"), eq("HelpTopic"), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("HELP_TOPIC_VIEWED");
        assertThat(ev.payload()).containsEntry("viewRoute", "/risk/register");
        assertThat(ev.payload()).containsEntry("topicCode", "HELP-RISK");
    }

    // ─── Tenant isolation ─────────────────────────────────────────

    @Test
    void get_unknownExternalId_throwsNotFound() {
        when(repo.findByExternalIdAndIsDeletedFalse(EX)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void lookup_missingTenant_throws() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.lookupForRoute("/risk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant");
    }

    @Test
    void listForModule_dispatchesToRepoWithTenant() {
        when(repo.findByModule(ORG_ID, "RISK")).thenReturn(List.of(
                topic(null, "HELP-RISK", "/risk", "RISK")
        ));
        List<HelpTopicResponse> out = service.listForModule("RISK");
        assertThat(out).hasSize(1);
        verify(repo).findByModule(ORG_ID, "RISK");
    }
}
