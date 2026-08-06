# Notification Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the notification system for the TSS GRC Platform, covering in-app and email notification channels, template management, event-driven delivery, and bilingual support.

---

## Notification Channels

| Channel | Delivery Method | Use Case |
|---------|----------------|---------|
| **In-App** | Database-stored, polled by browser | All events; visible in notification bell |
| **Email** | SMTP | Workflow tasks, SLA breaches, scheduled reports, system alerts |
| **SMS** | Future (configurable gateway) | Critical incidents only; designed but not in Phase 1 |

---

## Event-Driven Architecture

Notifications are triggered by **Spring ApplicationEvents** published from service layer:

```
Domain Event published → NotificationEventListener → NotificationService
    → Determine recipients (by role, user ID, or explicit list)
    → Resolve template (by event type + locale)
    → Create in-app notification record
    → Enqueue email delivery (async)
```

### Key Notification Events

| Event | Trigger | Recipients |
|-------|---------|-----------|
| `WorkflowStepAssigned` | Workflow step reaches a user | Assigned user |
| `WorkflowStepOverdue` | Step SLA exceeded | Assigned user + escalation role |
| `WorkflowCompleted` | All steps done | Initiator |
| `RiskCreated` | New high/critical risk | Risk Manager, CISO |
| `IncidentReported` | New cyber incident | CISO, assigned responder |
| `BreachRecorded` | Privacy breach created | DPO, Compliance Officer |
| `LicenseExpiringSoon` | License expires in 30/15/7 days | Platform Admin |
| `ControlOverdue` | Control review date passed | Control Owner |
| `BCMExerciseDue` | Exercise due within 7 days | BCM Manager |
| `ReportReady` | Async report generation complete | Requesting user |

---

## Template Engine

### Template Storage

`platform.notification_templates` stores:
- `event_type` (matches Spring event class name)
- `channel` (EMAIL, IN_APP)
- `subject_en`, `subject_ar` (for email)
- `body_template_en`, `body_template_ar` (Thymeleaf template fragments)

### Template Variables

Templates support variable substitution using Spring Expression Language (SpEL):
- `${entity.title}` — entity name
- `${recipient.displayName}` — recipient's name
- `${workflow.currentStepName}` — current workflow step
- `${actionUrl}` — deep link to the entity
- `${dueDate}` — formatted due date

### Locale Resolution

1. Recipient's `preferred_locale` determines which template variant is used
2. If Arabic template not available, falls back to English
3. All dynamic values (dates, numbers) formatted per locale

---

## Email Delivery

```java
@Async
public void sendEmailNotification(NotificationRequest request) {
    // 1. Resolve template
    // 2. Render HTML content using Thymeleaf template engine
    // 3. Create MimeMessage with Spring JavaMailSender
    // 4. Set Content-Type: text/html; charset=UTF-8
    // 5. Send via configured SMTP server
    // 6. Log delivery attempt to notification_delivery_log
    // 7. Retry on failure (max 3 attempts, exponential backoff)
}
```

### SMTP Configuration

```yaml
spring:
  mail:
    host: ${SMTP_HOST}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

---

## In-App Notifications

- Stored in `platform.notifications` table
- Browser polls `/api/v1/notifications/unread-count` every 30 seconds
- Notification bell badge shows unread count
- Clicking bell opens notification panel (last 20 notifications)
- Mark as read individually or bulk
- "View all" link navigates to full notification history page

---

## User Preferences

Users can configure (stored in `platform.user_notification_preferences`):
- Which event types they want email notifications for
- Which event types generate in-app notifications only
- Digest mode: consolidate low-priority notifications into daily email

---

## Admin Configuration

- Admins can edit notification template content via admin UI
- Admins can configure system-level SMTP settings
- Admins can view notification delivery logs and resend failed notifications
