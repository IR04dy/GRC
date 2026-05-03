import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { Workflow } from 'lucide-react';
import { Card } from '../ui/Card';

/**
 * ProcessLifecycle — one named end-to-end process the module supports.
 * Naming follows GRC market-leader conventions (ServiceNow IRM, RSA Archer,
 * MetricStream, OneTrust, NAVEX Global, AuditBoard).
 *
 * Renders as a ServiceNow-style "Process Launcher" tile: icon block, process
 * name, in-flight count chip, and a left-edge health stripe (green / amber
 * / red). Tap = open the filtered register at this process. Stages live on
 * the workflow detail page, not on the strip.
 */
export interface ProcessLifecycle {
  /** Industry-standard process name. */
  name: string;
  /** Arabic display name (rendered in RTL when lang === 'ar'). */
  nameAr?: string;
  /** Reference standard / clause — surfaced in the hover tooltip only. */
  reference?: string;
  /** Process icon. */
  Icon: LucideIcon;
  /** Items currently active. */
  activeCount: number;
  /** Items overdue / SLA-breached. */
  overdueCount: number;
  /** Where the user lands on click — typically a filtered register. */
  primary: { label: string; href: string };

  // Kept for catalogue compatibility — never rendered on the bar.
  stages?: string[];
  secondary?: { label: string; href: string };
  steward?: string;
  sla?: string;
}

/**
 * ServiceNow-style Process Launcher bar. Mounts under the KPI strip on
 * every module page so the user can fire off the right process in one
 * click without hunting through tabs.
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
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: 8,
      }}>
        {lifecycles.map((lc, idx) => <ProcessTile key={idx} lc={lc} lang={lang} />)}
      </div>
    </Card>
  );
}

function ProcessTile({ lc, lang }: { lc: ProcessLifecycle; lang: string }) {
  const { Icon } = lc;
  const overdue = lc.overdueCount;
  const active = lc.activeCount;

  // Health stripe — ServiceNow-style left-edge accent
  const stripe =
    overdue > 0 ? 'var(--danger)' :
    active  > 0 ? 'var(--accent-700)' :
    'var(--success)';

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
        display: 'grid',
        gridTemplateColumns: '4px 32px 1fr auto',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px 10px 0',
        borderRadius: 'var(--r-sm)',
        border: '1px solid var(--border)',
        background: 'var(--surface-1)',
        textDecoration: 'none',
        color: 'inherit',
        overflow: 'hidden',
        transition: 'background var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-fast) var(--ease-standard)',
      }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.background    = 'var(--primary-50)';
        el.style.borderColor   = 'var(--primary)';
        el.style.boxShadow     = '0 2px 6px rgba(0,0,0,0.06)';
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.background    = 'var(--surface-1)';
        el.style.borderColor   = 'var(--border)';
        el.style.boxShadow     = '';
      }}
    >
      {/* Health stripe */}
      <span style={{
        alignSelf: 'stretch',
        background: stripe,
      }} />

      {/* Icon block */}
      <div style={{
        width: 28, height: 28,
        borderRadius: 'var(--r-sm)',
        background: 'var(--surface-3)',
        color: 'var(--ink-1)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        marginInlineStart: 4,
      }}>
        <Icon size={15} strokeWidth={2} />
      </div>

      {/* Process name */}
      <div style={{ minWidth: 0 }}>
        <div style={{
          fontWeight: 600, fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.2,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>
          {lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name}
        </div>
      </div>

      {/* Count chip on the right */}
      <span style={{
        fontFamily: 'var(--font-mono)',
        fontSize: 11, fontWeight: 700,
        padding: '2px 8px',
        borderRadius: 'var(--r-pill)',
        background: overdue > 0 ? 'var(--danger-bg)'
                  : active  > 0 ? 'var(--surface-3)'
                  : 'transparent',
        color:    overdue > 0 ? 'var(--danger-fg)'
                  : active  > 0 ? 'var(--ink-1)'
                  : 'var(--ink-3)',
        marginInlineEnd: 0,
      }}>
        {overdue > 0 ? `${overdue}!` : active > 0 ? active : '—'}
      </span>
    </Link>
  );
}
