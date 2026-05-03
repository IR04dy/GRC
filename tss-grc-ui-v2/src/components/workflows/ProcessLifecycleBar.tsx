import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { Workflow, Play } from 'lucide-react';
import { DirectionalArrow } from '../rtl/DirectionalArrow';
import { Card } from '../ui/Card';

/**
 * ProcessAction — a single click target on a process tile.
 * Either navigates (`href`) or fires a callback (`onClick`).
 * The callback shape is what lets module pages wire each tile's primary
 * "Start" button to its actual modal opener (e.g. open NewRiskModal,
 * open NewDsrModal, open NewIncidentModal).
 */
export interface ProcessAction {
  label: string;
  href?: string;
  onClick?: () => void;
  /** When true, the action is disabled (greyed-out, non-interactive). */
  disabled?: boolean;
}

/**
 * One named end-to-end process — modelled on ServiceNow IRM's Process
 * Center. Naming follows ISO / SAMA / NCA standards rather than internal
 * jargon so auditors recognise the steps.
 *
 * The tile renders **two** affordances:
 *   - `primary`   →  the verb-first launcher ("Start Assessment",
 *                    "Submit DSR", "Report Incident"). When clicked
 *                    the module opens the relevant new-record modal
 *                    or routes to a wizard.
 *   - `secondary` →  "View register" — opens the filtered list.
 */
export interface ProcessLifecycle {
  /** Industry-standard process name. */
  name: string;
  /** Arabic display name (rendered in RTL when lang === 'ar'). */
  nameAr?: string;
  /** Standards reference for the tooltip (ISO 31000 §6.4, PDPL Article 23, etc.). */
  reference?: string;
  /** Icon. */
  Icon: LucideIcon;
  /** Items currently active in this process. */
  activeCount: number;
  /** Items overdue / SLA-breached. */
  overdueCount: number;
  /** Verb-first launcher — wired by the host page. */
  primary: ProcessAction;
  /** "View register" — usually a link to the filtered list. */
  secondary?: ProcessAction;
  /** Optional steward/owner shown in the tooltip. */
  steward?: string;
  /** Optional SLA hint shown in the tooltip ("30-day PDPL"). */
  sla?: string;

  // Catalogue compatibility (not rendered).
  stages?: string[];
}

/**
 * ServiceNow-style Process Center bar. Mounts under the KPI strip on every
 * module. Each tile is one named process the module supports, with a
 * launcher button and a register link.
 */
export function ProcessLifecycleBar({
  title, subtitle, lifecycles, lang, compact,
}: {
  title: string;
  subtitle?: string;
  lifecycles: ProcessLifecycle[];
  lang: string;
  /** When true, renders one compact dense row (no subtitle, smaller tiles, no register link). */
  compact?: boolean;
}) {
  if (lifecycles.length === 0) return null;
  if (compact) {
    return (
      <div style={{
        display: 'flex', gap: 6, flexWrap: 'wrap',
        padding: '8px 12px',
        background: 'var(--surface-2)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        marginBottom: 12,
      }}>
        {lifecycles.map((lc, idx) => <ProcessChip key={idx} lc={lc} lang={lang} />)}
      </div>
    );
  }
  return (
    <Card title={title} Icon={Workflow}>
      {subtitle && <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>{subtitle}</div>}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
        gap: 8,
      }}>
        {lifecycles.map((lc, idx) => <ProcessTile key={idx} lc={lc} lang={lang} />)}
      </div>
    </Card>
  );
}

/** Tight one-line chip used by the compact / collapsed bar. */
function ProcessChip({ lc, lang }: { lc: ProcessLifecycle; lang: string }) {
  const { Icon } = lc;
  const overdue = lc.overdueCount;
  const stripe =
    overdue > 0 ? 'var(--danger)' :
    lc.activeCount > 0 ? 'var(--accent-700)' :
    'var(--success)';

  const label = lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name;

  const trigger = (e: React.MouseEvent | React.KeyboardEvent) => {
    e.preventDefault();
    if (lc.primary.onClick) lc.primary.onClick();
    else if (lc.primary.href) window.location.href = lc.primary.href;
  };

  return (
    <button type="button"
      onClick={trigger}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') trigger(e); }}
      title={`${label} — ${lc.primary.label}`}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        padding: '4px 10px 4px 0',
        background: 'var(--surface-1)',
        border: '1px solid var(--border)',
        borderInlineStartWidth: 3,
        borderInlineStartColor: stripe,
        borderRadius: 'var(--r-sm)',
        cursor: 'pointer',
        fontSize: 12,
        color: 'var(--ink-1)',
        transition: 'background var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard)',
      }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.background  = 'var(--primary-50)';
        el.style.borderColor = 'var(--primary-300)';
        el.style.borderInlineStartColor = stripe;
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.background  = 'var(--surface-1)';
        el.style.borderColor = 'var(--border)';
        el.style.borderInlineStartColor = stripe;
      }}
    >
      <span style={{ marginInlineStart: 8, color: 'var(--primary-700)', display: 'inline-flex' }}>
        <Icon size={13} strokeWidth={2.1} />
      </span>
      <span style={{ fontWeight: 600 }}>{label}</span>
      {lc.activeCount > 0 && (
        <span className="mono" style={{
          fontSize: 10, fontWeight: 700,
          padding: '0 6px',
          borderRadius: 'var(--r-pill)',
          background: 'var(--surface-3)',
          color: 'var(--ink-1)',
        }}>{lc.activeCount}</span>
      )}
      {overdue > 0 && (
        <span className="mono" style={{
          fontSize: 10, fontWeight: 700,
          padding: '0 5px',
          borderRadius: 'var(--r-pill)',
          background: 'var(--danger-bg)',
          color: 'var(--danger-fg)',
        }}>{overdue}!</span>
      )}
    </button>
  );
}

function ProcessTile({ lc, lang }: { lc: ProcessLifecycle; lang: string }) {
  const { Icon } = lc;
  const overdue = lc.overdueCount;
  const active  = lc.activeCount;

  // ServiceNow-style left-edge health stripe
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
    <div
      title={tooltip}
      style={{
        display: 'grid',
        gridTemplateColumns: '4px 36px 1fr auto auto',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px 10px 0',
        borderRadius: 'var(--r-sm)',
        border: '1px solid var(--border)',
        background: 'var(--surface-1)',
        overflow: 'hidden',
        transition: 'box-shadow var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard)',
      }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.borderColor = 'var(--primary-300)';
        el.style.boxShadow   = '0 2px 8px rgba(0,0,0,0.06)';
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.borderColor = 'var(--border)';
        el.style.boxShadow   = '';
      }}
    >
      {/* Health stripe */}
      <span style={{ alignSelf: 'stretch', background: stripe }} />

      {/* Icon block */}
      <div style={{
        width: 36, height: 36,
        borderRadius: 'var(--r-sm)',
        background: 'var(--primary-50)',
        color: 'var(--primary-700)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        marginInlineStart: 4,
      }}>
        <Icon size={18} strokeWidth={2.1} />
      </div>

      {/* Title + reference */}
      <div style={{ minWidth: 0 }}>
        <div style={{
          fontWeight: 700, fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.2,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>
          {lang === 'ar' && lc.nameAr ? lc.nameAr : lc.name}
        </div>
        <div style={{
          fontSize: 10, color: 'var(--ink-3)', marginTop: 2,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>
          {lc.reference ?? ' '}
        </div>
      </div>

      {/* Counter chip */}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2 }}>
        <span style={{
          fontFamily: 'var(--font-mono)',
          fontSize: 11, fontWeight: 700,
          padding: '1px 8px',
          borderRadius: 'var(--r-pill)',
          background: 'var(--surface-3)',
          color: 'var(--ink-1)',
          minWidth: 24, textAlign: 'center',
        }}>{active}</span>
        {overdue > 0 && (
          <span style={{
            fontFamily: 'var(--font-mono)',
            fontSize: 10, fontWeight: 700,
            padding: '1px 6px',
            borderRadius: 'var(--r-pill)',
            background: 'var(--danger-bg)',
            color: 'var(--danger-fg)',
          }}>{overdue} !</span>
        )}
      </div>

      {/* Action buttons: primary "Start ..." + secondary register icon link */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginInlineStart: 4 }}>
        <PrimaryButton action={lc.primary} />
        {lc.secondary && <SecondaryButton action={lc.secondary} />}
      </div>
    </div>
  );
}

/** ServiceNow-style green/blue primary button. */
function PrimaryButton({ action }: { action: ProcessAction }) {
  const content = (
    <>
      <Play size={11} strokeWidth={2.6} fill="currentColor" />
      <span style={{ fontWeight: 600, fontSize: 12 }}>{action.label}</span>
    </>
  );
  const style: React.CSSProperties = {
    display: 'inline-flex', alignItems: 'center', gap: 5,
    padding: '6px 10px',
    borderRadius: 'var(--r-sm)',
    background: action.disabled ? 'var(--surface-3)' : 'var(--primary)',
    color: action.disabled ? 'var(--ink-3)' : '#fff',
    border: 'none',
    cursor: action.disabled ? 'not-allowed' : 'pointer',
    textDecoration: 'none',
    transition: 'background var(--dur-fast) var(--ease-standard)',
    whiteSpace: 'nowrap',
  };
  if (action.onClick) {
    return (
      <button type="button" onClick={action.onClick} disabled={action.disabled} style={style}
        onMouseEnter={(e) => { if (!action.disabled) (e.currentTarget as HTMLElement).style.background = 'var(--primary-700)'; }}
        onMouseLeave={(e) => { if (!action.disabled) (e.currentTarget as HTMLElement).style.background = 'var(--primary)'; }}
      >{content}</button>
    );
  }
  if (action.href && !action.disabled) {
    return <Link to={action.href} style={style}
      onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--primary-700)'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--primary)'; }}
    >{content}</Link>;
  }
  return <span style={style}>{content}</span>;
}

/** Quiet secondary "View register" affordance. */
function SecondaryButton({ action }: { action: ProcessAction }) {
  const content = (
    <>
      <span style={{ fontSize: 11 }}>{action.label}</span>
      <DirectionalArrow size={11} />
    </>
  );
  const style: React.CSSProperties = {
    display: 'inline-flex', alignItems: 'center', gap: 4,
    padding: '6px 8px',
    borderRadius: 'var(--r-sm)',
    background: 'transparent',
    color: 'var(--primary-700)',
    fontWeight: 500,
    border: '1px solid transparent',
    cursor: 'pointer',
    textDecoration: 'none',
    transition: 'background var(--dur-fast) var(--ease-standard)',
    whiteSpace: 'nowrap',
  };
  if (action.onClick) {
    return <button type="button" onClick={action.onClick} style={style}
      onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--primary-50)'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
    >{content}</button>;
  }
  if (action.href) {
    return <Link to={action.href} style={style}
      onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'var(--primary-50)'; }}
      onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
    >{content}</Link>;
  }
  return null;
}
