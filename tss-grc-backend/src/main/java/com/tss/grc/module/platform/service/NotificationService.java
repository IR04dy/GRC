package com.tss.grc.module.platform.service;

import com.tss.grc.module.platform.dto.NotificationResponse;
import com.tss.grc.module.platform.entity.Notification;
import com.tss.grc.module.platform.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
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
