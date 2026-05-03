import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { ArrowRight } from 'lucide-react';
import { Card } from '../ui/Card';
import { Workflow } from 'lucide-react';

/**
 * ProcessLifecycle — one named end-to-end process the module supports.
 * Naming follows GRC market-leader conventions (ServiceNow IRM, RSA Archer,
 * MetricStream, OneTrust, NAVEX Global, AuditBoard).
 *
 * The landing-strip renders these as **action buttons that launch the
 * process** — stages live on the detail page, not the index. The customer
 * wants to *trigger* a process from here, not study it.
 */
export interface ProcessLifecycle {
  /** Industry-standard process name. */
  name: string;
  /** Arabic display name (rendered in RTL when lang === 'ar'). */
  nameAr?: string;
  /** Reference standard / clause — kept for tooltip only. */
  reference?: string;
  /** Process icon. */
  Icon: LucideIcon;
  /** Items currently active. */
  activeCount: number;
  /** Items overdue / SLA-breached. */
  overdueCount: number;
  /** Where to go when the user clicks the button — typically a filtered register. */
  primary: { label: string; href: string };

  // ── Kept for compatibility with the catalogue but not rendered on the strip ──
  stages?: string[];
  secondary?: { label: string; href: string };
  steward?: string;
  sla?: string;
}

/**
 * Compact action-bar of named process triggers, mounted under the KPI strip
 * on every module page. Each tile is a single click-target: name, icon,
 * counter chip, and a quiet overdue indicator. No stages, no stewards —
 * those belong in the workflow detail view.
 */
export function ProcessLifecycleBar({
  title, subtitle, lifecycles, lang,
}: {
  title: string;
  subtitle?: string;
  lifecycles: ProcessLifecycle[];
  lang: string;
}) {
  if (lifecycles.length === 0) return null;
  return (
    <Card title={title} Icon={Workflow}>
      {subtitle && <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>{subtitle}</div>}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 8,
      }}>
        {lifecycles.map((lc, idx) => <ProcessButton key={idx} lc={lc} lang={lang} />)}
      </div>
    </Card>
  );
}

function ProcessButton({ lc, lang }: { lc: ProcessLifecycle; lang: string }) {
  const { Icon } = lc;
  const overdue = lc.overdueCount;
  const tooltip = [
    lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name,
    lc.reference,
    lc.steward && `Owner: ${lc.steward}`,
    lc.sla && `SLA: ${lc.sla}`,
  ].filter(Boolean).join(' · ');

  return (
    <Link
      to={lc.primary.href}
      title={tooltip}
      style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '10px 12px',
        borderRadius: 'var(--r-md)',
        border: '1px solid var(--border)',
        background: 'var(--surface-2)',
        textDecoration: 'none',
        color: 'inherit',
        transition: 'background var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard), transform var(--dur-fast) var(--ease-standard)',
        position: 'relative',
      }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.background = 'var(--primary-50)';
        el.style.borderColor = 'var(--primary)';
        el.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.background = 'var(--surface-2)';
        el.style.borderColor = 'var(--border)';
        el.style.transform = '';
      }}
    >
      <div style={{
        width: 32, height: 32, flexShrink: 0,
        borderRadius: 'var(--r-sm)',
        background: 'var(--primary-50)',
        color: 'var(--primary-700)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        position: 'relative',
      }}>
        <Icon size={16} strokeWidth={2.2} />
        {overdue > 0 && (
          <span style={{
            position: 'absolute', top: -4, insetInlineEnd: -4,
            width: 8, height: 8, borderRadius: '50%',
            background: 'var(--danger)',
            border: '1.5px solid var(--surface-1)',
          }} aria-label={`${overdue} overdue`} />
        )}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontWeight: 600, fontSize: 13, color: 'var(--ink-1)',
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>
          {lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
          {lc.activeCount > 0 ? `${lc.activeCount} in flight` : 'No items in flight'}
          {overdue > 0 && (
            <span style={{ color: 'var(--danger-fg)', fontWeight: 600, marginInlineStart: 6 }}>
              · {overdue} overdue
            </span>
          )}
        </div>
      </div>

      <ArrowRight size={14} style={{ color: 'var(--ink-3)', flexShrink: 0 }} />
    </Link>
  );
}
