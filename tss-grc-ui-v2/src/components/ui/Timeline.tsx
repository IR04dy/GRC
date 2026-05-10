import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';

export interface TimelineEvent {
  id: string | number;
  time: string;
  title: string;
  body?: ReactNode;
  Icon?: LucideIcon;
  tone?: 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info';
}

const TONE_FG: Record<NonNullable<TimelineEvent['tone']>, string> = {
  primary: 'var(--primary)', accent: 'var(--accent-700)',
  success: 'var(--success-fg)', warning: 'var(--warning-fg)',
  danger: 'var(--danger-fg)', info: 'var(--info-fg)',
};
const TONE_BG: Record<NonNullable<TimelineEvent['tone']>, string> = {
  primary: 'var(--primary-50)', accent: 'var(--accent-50)',
  success: 'var(--success-bg)', warning: 'var(--warning-bg)',
  danger: 'var(--danger-bg)', info: 'var(--info-bg)',
};

/**
 * Vertical timeline with dot markers. The connecting line is drawn through the dots.
 * Used for incident chronologies, audit-trail summaries, and onboarding history.
 */
export function Timeline({ events }: { events: TimelineEvent[] }) {
  return (
    <ol className="timeline">
      {events.map(e => {
        const tone = e.tone ?? 'primary';
        return (
          <li key={e.id} className="timeline-row">
            <div className="timeline-dot" style={{ background: TONE_BG[tone], color: TONE_FG[tone] }}>
              {e.Icon ? <e.Icon size={12} strokeWidth={2.2} /> : <span className="timeline-dot-pip" />}
            </div>
            <div className="timeline-content">
              <div className="timeline-meta">
                <span className="timeline-title">{e.title}</span>
                <span className="timeline-time mono">{e.time}</span>
              </div>
              {e.body && <div className="timeline-body">{e.body}</div>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
