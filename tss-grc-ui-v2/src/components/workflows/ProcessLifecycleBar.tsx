import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { ArrowRight, AlertTriangle, Activity } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

/**
 * ProcessLifecycle describes one named end-to-end process / lifecycle that
 * applies to a domain module — e.g. "Corrective Action Plan (CAP) Closure"
 * or "Data Subject Rights Fulfilment". Naming follows the conventions used
 * by GRC market leaders: ServiceNow IRM, RSA Archer, MetricStream, OneTrust,
 * NAVEX Global and AuditBoard.
 */
export interface ProcessLifecycle {
  /** Industry-standard process name — must match what auditors expect to see. */
  name: string;
  /** Short Arabic display name (rendered in RTL when lang === 'ar'). */
  nameAr?: string;
  /** Reference standard / clause ("ISO 31000 §6", "PDPL Article 23", "ITIL v4"). */
  reference?: string;
  /** Process icon. */
  Icon: LucideIcon;
  /** Canonical stages, in order (e.g. ['Identified', 'Implementing', 'Owner-Tested', 'Independent-Tested', 'Sign-off', 'Closed']). */
  stages: string[];
  /** Number of items currently active in this process. */
  activeCount: number;
  /** Number of items overdue / breached. */
  overdueCount: number;
  /** Primary CTA — opens the relevant module + filter. */
  primary: { label: string; href: string };
  /** Optional secondary CTA — typically "View workflow definition". */
  secondary?: { label: string; href: string };
  /** Optional one-line owner / steward hint ("CISO Office", "DPO", "TPRM Lead"). */
  steward?: string;
  /** Optional SLA hint ("30-day PDPL", "7-day critical CVE"). */
  sla?: string;
}

/**
 * Renders a horizontal bar of named lifecycle cards above the module body.
 * Each card summarises one process (stages + counts + actions) so a user
 * landing on the module immediately knows what processes run here, how
 * many items are in flight, what's overdue, and where to go to act on
 * each one.
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
    <Card title={title} Icon={Activity} action={<Badge tone="primary">{lifecycles.length}</Badge>}>
      {subtitle && <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>{subtitle}</div>}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 12,
      }}>
        {lifecycles.map((lc, idx) => <LifecycleCard key={idx} lc={lc} lang={lang} />)}
      </div>
    </Card>
  );
}

function LifecycleCard({ lc, lang }: { lc: ProcessLifecycle; lang: string }) {
  const { Icon } = lc;
  const overdue = lc.overdueCount;
  return (
    <div style={{
      border: '1px solid var(--border)',
      borderRadius: 'var(--r-md)',
      background: 'var(--surface-2)',
      padding: 12,
      display: 'flex', flexDirection: 'column', gap: 8,
      transition: 'transform var(--dur-fast) var(--ease-standard), box-shadow var(--dur-fast) var(--ease-standard)',
    }}
      onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.transform = 'translateY(-2px)'; (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.transform = ''; (e.currentTarget as HTMLElement).style.boxShadow = ''; }}
    >
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
        <div style={{
          width: 30, height: 30, flexShrink: 0,
          borderRadius: 'var(--r-sm)',
          background: 'var(--primary-50)',
          color: 'var(--primary-700)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon size={16} strokeWidth={2.2} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.2 }}>
            {lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name}
          </div>
          {lc.reference && (
            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2, fontFamily: 'var(--font-mono)' }}>
              {lc.reference}
            </div>
          )}
        </div>
      </div>

      {/* Stages strip — compact dot row */}
      <div style={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
        {lc.stages.map((s, i) => (
          <span key={s} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <span style={{
              width: 6, height: 6, borderRadius: '50%',
              background: i === 0 ? 'var(--primary)' : 'var(--ink-4)',
            }} />
            <span style={{ fontSize: 10, color: 'var(--ink-2)' }}>{s}</span>
            {i < lc.stages.length - 1 && (
              <span style={{ color: 'var(--ink-4)', fontSize: 9, marginInline: 1 }}>›</span>
            )}
          </span>
        ))}
      </div>

      {/* Counters + steward */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
        <Badge tone="info">
          <Activity size={9} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }} />
          {lc.activeCount} active
        </Badge>
        {overdue > 0 && (
          <Badge tone="danger">
            <AlertTriangle size={9} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }} />
            {overdue} overdue
          </Badge>
        )}
        {lc.sla && <Badge tone="warning">{lc.sla}</Badge>}
        {lc.steward && (
          <span style={{ fontSize: 10, color: 'var(--ink-3)', marginInlineStart: 'auto' }}>
            {lc.steward}
          </span>
        )}
      </div>

      {/* Action row */}
      <div style={{ display: 'flex', gap: 8, marginTop: 'auto' }}>
        <Link to={lc.primary.href}
          style={{
            flex: 1,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 4,
            padding: '6px 10px', borderRadius: 'var(--r-sm)',
            background: 'var(--primary)',
            color: '#fff',
            fontSize: 12, fontWeight: 600,
            textDecoration: 'none',
            transition: 'background var(--dur-fast) var(--ease-standard)',
          }}>
          {lc.primary.label} <ArrowRight size={11} />
        </Link>
        {lc.secondary && (
          <Link to={lc.secondary.href}
            style={{
              padding: '6px 10px', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-3)',
              color: 'var(--ink-1)',
              fontSize: 12, fontWeight: 500,
              textDecoration: 'none',
            }}>
            {lc.secondary.label}
          </Link>
        )}
      </div>
    </div>
  );
}
