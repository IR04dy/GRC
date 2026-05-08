import { useEffect, useState } from 'react';
import { X, Clock, ArrowRight, UserPlus, FileText, CheckCircle2, RotateCcw } from 'lucide-react';
import { activityFor, activityUser, subscribeActivity, type ActivityEvent } from '../../lib/activityLog';
import { getUser } from '../../lib/userDirectory';
import { Avatar } from '../ui/UserPicker';
import { useI18n } from '../../lib/i18n';

/**
 * ActivityFeedDrawer — slide-out drawer showing the audit trail of an entity.
 *
 * Shows every action (transitioned, assigned, created, closed, commented)
 * with the user, timestamp, and from/to context. Inspired by Linear's
 * activity feed + ServiceNow Activity Log.
 */
export function ActivityFeedDrawer({ open, onClose, entityType, entityId }: {
  open: boolean;
  onClose: () => void;
  entityType: string;
  entityId: string;
}) {
  const { t, lang } = useI18n();
  const [events, setEvents] = useState<ActivityEvent[]>(() => activityFor(entityType, entityId));

  useEffect(() => {
    if (!open) return;
    setEvents(activityFor(entityType, entityId));
    return subscribeActivity(() => setEvents(activityFor(entityType, entityId)));
  }, [open, entityType, entityId]);

  if (!open) return null;

  return (
    <div onClick={onClose} style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      zIndex: 1000, display: 'flex', justifyContent: 'flex-end',
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        width: 420, maxWidth: '100vw', height: '100vh',
        background: 'var(--surface-1)',
        boxShadow: '-8px 0 24px rgba(0,0,0,0.15)',
        display: 'flex', flexDirection: 'column',
      }}>
        {/* Header */}
        <div style={{
          padding: 14, borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {t('alFeedTitle')}
            </div>
            <div className="mono" style={{ fontSize: 13, fontWeight: 600, marginTop: 2 }}>
              {entityId}
            </div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
              {t('alFeedSub')}
            </div>
          </div>
          <button onClick={onClose} style={{
            border: 'none', background: 'transparent', cursor: 'pointer',
            padding: 6, color: 'var(--ink-2)',
          }}>
            <X size={18} />
          </button>
        </div>

        {/* Feed */}
        <div style={{ flex: 1, overflow: 'auto', padding: 14 }}>
          {events.length === 0 ? (
            <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
              {t('alFeedEmpty')}
            </div>
          ) : events.map((e, i) => (
            <FeedRow key={e.id} event={e} isFirst={i === 0} isLast={i === events.length - 1} t={t} lang={lang} />
          ))}
        </div>
      </div>
    </div>
  );
}

function FeedRow({ event, isFirst, isLast, t, lang }: {
  event: ActivityEvent;
  isFirst: boolean;
  isLast: boolean;
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  const user = activityUser(event);
  const assignee = event.assigneeId ? getUser(event.assigneeId) : undefined;

  const Icon =
    event.action === 'CREATED' ? FileText :
    event.action === 'TRANSITIONED' ? ArrowRight :
    event.action === 'ASSIGNED' ? UserPlus :
    event.action === 'CLOSED' ? CheckCircle2 :
    event.action === 'REOPENED' ? RotateCcw :
    Clock;

  const verbKey =
    event.action === 'CREATED' ? 'alFeedCreated' :
    event.action === 'TRANSITIONED' ? 'alFeedMoved' :
    event.action === 'ASSIGNED' ? 'alFeedAssigned' :
    event.action === 'CLOSED' ? 'alFeedClosed' :
    'alFeedNote';

  return (
    <div style={{
      display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 10,
      padding: '8px 0',
      position: 'relative',
    }}>
      {/* Timeline dot + line */}
      <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <div style={{
          width: 26, height: 26, borderRadius: '50%',
          background: 'var(--surface-2)', border: '1.5px solid var(--border)',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          color: 'var(--ink-2)', zIndex: 1,
        }}>
          <Icon size={12} />
        </div>
        {!isLast && (
          <div style={{
            position: 'absolute', top: 26, bottom: -8,
            width: 1, background: 'var(--border)',
          }} />
        )}
      </div>

      {/* Body */}
      <div style={{ paddingTop: 2 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
          {user && <Avatar user={user} size={18} showOnCall={false} />}
          <span style={{ fontSize: 12, fontWeight: 600 }}>
            {user ? (lang === 'ar' ? user.nameAr : user.nameEn) : 'system'}
          </span>
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {t(verbKey)}
          </span>
        </div>

        {event.action === 'TRANSITIONED' && (
          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginInlineStart: 24 }}>
            <span style={{ padding: '1px 6px', background: 'var(--surface-2)', borderRadius: 3, fontFamily: 'var(--font-mono)' }}>
              {event.fromStatus}
            </span>
            <ArrowRight size={11} style={{ display: 'inline-block', verticalAlign: 'middle', margin: '0 4px', color: 'var(--ink-3)' }} />
            <span style={{ padding: '1px 6px', background: 'var(--primary-50)', borderRadius: 3, fontFamily: 'var(--font-mono)', color: 'var(--primary)', fontWeight: 600 }}>
              {event.toStatus}
            </span>
          </div>
        )}
        {event.action === 'ASSIGNED' && assignee && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginInlineStart: 24, marginTop: 2 }}>
            <Avatar user={assignee} size={16} />
            <span style={{ fontSize: 11, color: 'var(--ink-2)' }}>{lang === 'ar' ? assignee.nameAr : assignee.nameEn}</span>
          </div>
        )}
        {(event.noteEn || event.noteAr) && (
          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginInlineStart: 24, marginTop: 4, padding: 6, background: 'var(--surface-2)', borderRadius: 4, lineHeight: 1.5 }}>
            {lang === 'ar' ? event.noteAr : event.noteEn}
          </div>
        )}

        <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 4, marginInlineStart: 24 }}>
          {new Date(event.ts).toLocaleString()}
        </div>
      </div>
    </div>
  );
}
