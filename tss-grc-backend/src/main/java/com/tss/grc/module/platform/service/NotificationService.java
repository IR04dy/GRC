package com.tss.grc.module.platform.service;

import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.dto.NotificationResponse;
import com.tss.grc.module.platform.entity.Notification;
import com.tss.grc.module.platform.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Hard cap on a single broadcast — anything above this is almost certainly a misconfigured rule. */
    private static final int BROADCAST_FANOUT_CAP = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    /** F2-94 — needed to tell "nobody holds this role" from "this is not a role". */
    private final RoleRepository roleRepository;

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    /**
     * Outcome of a role-addressed broadcast, split so the caller can tell the two
     * failures apart. F2-94: for as long as this method returned a bare {@code int},
     * "nobody holds the CISO role here" and "there is no such role as CTO" were the
     * same observable — {@code 0} — and 143 audit rows in dev recorded the platform's
     * entire escalation fan-out as working.
     *
     * @param delivered      notifications actually written (recipients deduplicated
     *                       across roles — a user holding two addressed roles gets one)
     * @param deliveredRoles roles that exist and have at least one active holder
     * @param unheldRoles    roles that exist in this tenant but nobody active holds —
     *                       an <b>operational</b> gap; the remedy is to grant the role
     * @param unknownRoles   names that are not a role in this tenant at all — a
     *                       <b>configuration defect</b>; the remedy is to fix the rule
     *                       or create the role, and no amount of granting will help
     */
    public record RoleFanout(int delivered,
                             List<String> deliveredRoles,
                             List<String> unheldRoles,
                             List<String> unknownRoles) {

        static final RoleFanout NOTHING_ADDRESSED =
            new RoleFanout(0, List.of(), List.of(), List.of());

        /** True when the rule fired and no human was told — whatever the reason. */
        public boolean reachedNobody() {
            return delivered == 0;
        }

        /** True when at least one addressed name is not a role — never a grant problem. */
        public boolean hasConfigurationDefect() {
            return !unknownRoles.isEmpty();
        }
    }

    /**
     * Broadcast a notification to every active user in the given tenant who holds
     * the named role. Returns the number of recipients actually notified.
     *
     * <p>Used by the F8 automation engine to deliver NOTIFY / ESCALATE actions to
     * a role rather than a specific user. Fan-out is hard-capped at
     * {@link #BROADCAST_FANOUT_CAP} — a rule that targets a role with hundreds of
     * holders is almost certainly misconfigured, and shipping hundreds of identical
     * notifications would make the UI unusable.
     *
     * <p>Returns 0 (without throwing) when no users match the role — the caller
     * should fall back to writing an audit row so the rule fire is still
     * inspectable.
     *
     * <p>Retained for the eleven single-role callers (schedulers and services that
     * address one known role). Delegates to {@link #broadcastToRoles} so all callers
     * share one fan-out, and logs the unknown-role case at WARN rather than INFO —
     * a name that is not a role cannot be fixed by granting anything.
     */
    @Transactional
    public int broadcastToRole(Long organisationId, String roleName,
                               String titleEn, String titleAr, String messageEn,
                               String notificationType,
                               String entityType, Long entityId) {
        if (roleName == null || roleName.isBlank()) return 0;
        return broadcastToRoles(organisationId, List.of(roleName), titleEn, titleAr,
            messageEn, notificationType, entityType, entityId).delivered();
    }

    /**
     * Fan a notification out to every active holder of <em>any</em> of the named roles,
     * de-duplicating recipients so a user holding two of them is notified once.
     *
     * <p>F8 rules address audiences in the plural ({@code "roles": ["CISO","CTO"]}) —
     * 147 of the platform's 164 NOTIFY/ESCALATE rules do. A singular parameter could
     * not express that, which is half of why F2-94 stayed invisible: the executor read
     * a singular key, the rules wrote a plural one, and every metric the engine keeps
     * ({@code fire_count}, {@code automation_rule_runs}, {@code RULE_FIRED}) increments
     * identically whether one person or nobody was reached.
     *
     * <p>Role existence is resolved once per call against the tenant's visible roles
     * (its own plus the global ones) — so the classification in {@link RoleFanout}
     * costs one extra query regardless of how many roles are addressed.
     */
    @Transactional
    public RoleFanout broadcastToRoles(Long organisationId, List<String> roleNames,
                                       String titleEn, String titleAr, String messageEn,
                                       String notificationType,
                                       String entityType, Long entityId) {
        if (organisationId == null || roleNames == null || roleNames.isEmpty()) {
            return RoleFanout.NOTHING_ADDRESSED;
        }

        // Case-insensitive, because a rule author types CISO and the seed may hold Ciso.
        Set<String> knownRoles = roleRepository
            .findByOrganisationIdOrOrganisationIdIsNull(organisationId).stream()
            .map(Role::getRoleName)
            .filter(Objects::nonNull)
            .map(r -> r.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

        List<String> deliveredRoles = new ArrayList<>();
        List<String> unheld = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Set<Long> alreadyNotified = new LinkedHashSet<>();
        boolean capped = false;

        for (String raw : roleNames) {
            if (raw == null || raw.isBlank()) continue;
            String role = raw.trim();
            if (!knownRoles.contains(role.toUpperCase(Locale.ROOT))) {
                unknown.add(role);
                continue;
            }
            List<User> recipients =
                userRepository.findActiveByOrganisationIdAndRoleName(organisationId, role);
            if (recipients.isEmpty()) {
                unheld.add(role);
                continue;
            }
            // The role IS held — record it as delivered even if every holder was already
            // notified via another addressed role. Classifying on the dedupe result would
            // report a satisfied audience as an unheld role.
            deliveredRoles.add(role);
            for (User u : recipients) {
                if (u.getId() == null || !alreadyNotified.add(u.getId())) continue;
                if (alreadyNotified.size() > BROADCAST_FANOUT_CAP) {
                    capped = true;
                    break;
                }
                Notification n = new Notification();
                n.setOrganisationId(organisationId);
                n.setRecipientId(u.getId());
                n.setTitleEn(titleEn);
                n.setTitleAr(titleAr);
                n.setMessageEn(messageEn);
                n.setNotificationType(notificationType);
                n.setEntityType(entityType);
                n.setEntityId(entityId);
                notificationRepository.save(n);
            }
            if (capped) break;
        }

        int delivered = Math.min(alreadyNotified.size(), BROADCAST_FANOUT_CAP);
        if (capped) {
            log.warn("[notify] broadcast to {} in org {} capped at {} recipients",
                roleNames, organisationId, BROADCAST_FANOUT_CAP);
        }
        if (!unknown.isEmpty()) {
            log.warn("[notify] org {}: addressed {} which {} not a role in this tenant — "
                   + "a configuration defect, not a missing grant (F2-94)",
                organisationId, unknown, unknown.size() == 1 ? "is" : "are");
        }
        if (!unheld.isEmpty()) {
            log.info("[notify] org {}: no active user holds {} — nothing delivered for those",
                organisationId, unheld);
        }
        return new RoleFanout(delivered, List.copyOf(deliveredRoles),
            List.copyOf(unheld), List.copyOf(unknown));
    }

    /**
     * Deliver a single notification to a specific user. No-op (returns false) if
     * recipientId is null. Used by schedulers/services that target a known user
     * (e.g. an evidence-request assignee), as opposed to {@link #broadcastToRole}.
     */
    @Transactional
    public boolean notifyUser(Long organisationId, Long recipientId,
                              String titleEn, String titleAr, String messageEn,
                              String notificationType, String entityType, Long entityId) {
        if (recipientId == null) return false;
        Notification n = new Notification();
        n.setOrganisationId(organisationId);
        n.setRecipientId(recipientId);
        n.setTitleEn(titleEn);
        n.setTitleAr(titleAr);
        n.setMessageEn(messageEn);
        n.setNotificationType(notificationType);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        notificationRepository.save(n);
        return true;
    }

    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification n = notificationRepository.findById(id)
            .filter(notif -> notif.getRecipientId().equals(userId))
            .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
        n.setIsRead(true);
        n.setReadAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(Long userId) {
        // Load all unread for this user and mark them read
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
            .stream()
            .filter(n -> Boolean.FALSE.equals(n.getIsRead()))
            .forEach(n -> {
                n.setIsRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            });
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getTitleEn(),
            n.getTitleAr(),
            n.getMessageEn(),
            null,
            n.getNotificationType(),
            null,
            n.getIsRead(),
            null,
            n.getCreatedAt()
        );
    }
}
