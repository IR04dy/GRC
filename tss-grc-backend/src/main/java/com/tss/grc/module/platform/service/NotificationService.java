package com.tss.grc.module.platform.service;

import com.tss.grc.module.iam.entity.User;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Hard cap on a single broadcast — anything above this is almost certainly a misconfigured rule. */
    private static final int BROADCAST_FANOUT_CAP = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
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
     */
    @Transactional
    public int broadcastToRole(Long organisationId, String roleName,
                               String titleEn, String titleAr, String messageEn,
                               String notificationType,
                               String entityType, Long entityId) {
        if (organisationId == null || roleName == null || roleName.isBlank()) return 0;
        List<User> recipients = userRepository.findActiveByOrganisationIdAndRoleName(organisationId, roleName);
        if (recipients.isEmpty()) {
            log.info("[notify] no active users hold role {} in org {} — skipping broadcast",
                    roleName, organisationId);
            return 0;
        }
        int delivered = 0;
        for (User u : recipients) {
            if (delivered >= BROADCAST_FANOUT_CAP) {
                log.warn("[notify] broadcast to role {} in org {} capped at {} recipients",
                        roleName, organisationId, BROADCAST_FANOUT_CAP);
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
            delivered++;
        }
        return delivered;
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
