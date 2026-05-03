import { Landmark } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { findAuthority, type AuthorityDto } from '../../lib/authorities';
import { type RegulatorAnchor, type ControlStatus } from '../../lib/evidenceAnchors';

interface PerAuthority {
  authority: AuthorityDto;
  total:           number;
  implemented:     number;
  partial:         number;
  compensating:    number;
  notImplemented:  number;
  notApplicable:   number;
}

/**
 * "Regulator Posture" card — drives ServiceNow IRM-style cross-authority
 * insight on every module overview. Aggregates anchor chips into per-
 * authority coverage stats and renders them as horizontal stacked bars
 * with a hard count on the right. Auditors recognise this as the
 * canonical cross-framework coverage view.
 */
export function RegulatorPostureCard({
  title, anchors,
}: {
  title?: string;
  anchors: RegulatorAnchor[];
}) {
  const stats = aggregateByAuthority(anchors);

  return (
    <Card title={title ?? 'Regulator Posture'} Icon={Landmark}
      action={<Badge tone="primary">{stats.length} authorities</Badge>}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>
        Coverage of cited controls per regulatory authority. Each row aggregates
        every evidence + control anchored to that authority across this domain.
      </div>
      {stats.length === 0
        ? <div style={{ padding: 12, color: 'var(--ink-3)' }}>No anchored evidence yet.</div>
        : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {stats.map(s => <PostureRow key={s.authority.code} s={s} />)}
          </div>
      }
    </Card>
  );
}

function PostureRow({ s }: { s: PerAuthority }) {
  const total = s.total || 1;
  const seg = (n: number) => `${(n / total) * 100}%`;
  const coveragePct = Math.round(((s.implemented + s.compensating + s.partial * 0.5) / total) * 100);

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr 60px 80px', gap: 10, alignItems: 'center' }}>
      <div style={{ minWidth: 0 }}>
        <div title={s.authority.nameEn + ' — ' + s.authority.nameAr}
             style={{ fontWeight: 700, fontSize: 13, color: s.authority.colorToken,
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {s.authority.code}
        </div>
        <div style={{ fontSize: 10, color: 'var(--ink-3)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {s.authority.nameEn}
        </div>
      </div>

      {/* Stacked bar */}
      <div style={{ display: 'flex', height: 8, borderRadius: 'var(--r-pill)', overflow: 'hidden', background: 'var(--surface-3)' }}>
        {s.implemented    > 0 && <span title={`Implemented: ${s.implemented}`}    style={{ width: seg(s.implemented),    background: 'var(--success)' }} />}
        {s.compensating   > 0 && <span title={`Compensating: ${s.compensating}`}  style={{ width: seg(s.compensating),   background: 'var(--accent-700)' }} />}
        {s.partial        > 0 && <span title={`Partial: ${s.partial}`}            style={{ width: seg(s.partial),        background: 'var(--cat-2)' }} />}
        {s.notImplemented > 0 && <span title={`Not implemented: ${s.notImplemented}`} style={{ width: seg(s.notImplemented), background: 'var(--danger)' }} />}
        {s.notApplicable  > 0 && <span title={`N/A: ${s.notApplicable}`}          style={{ width: seg(s.notApplicable),  background: 'var(--ink-4)' }} />}
      </div>

      <span className="mono" style={{ fontSize: 12, fontWeight: 700, textAlign: 'end',
        color: coveragePct >= 80 ? 'var(--success-fg)' :
               coveragePct >= 60 ? 'var(--primary-700)' :
               coveragePct >= 40 ? 'var(--accent-700)' : 'var(--danger-fg)' }}>
        {coveragePct}%
      </span>

      <span style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'end' }}>
        {s.total} cite{s.total !== 1 ? 's' : ''}
      </span>
    </div>
  );
}

function aggregateByAuthority(anchors: RegulatorAnchor[]): PerAuthority[] {
  const map = new Map<string, PerAuthority>();
  for (const a of anchors) {
    const auth = findAuthority(a.authorityCode);
    if (!auth) continue;
    const cur = map.get(a.authorityCode) ?? {
      authority: auth, total: 0,
      implemented: 0, partial: 0, compensating: 0, notImplemented: 0, notApplicable: 0,
    };
    cur.total += 1;
    incr(cur, a.controlStatus);
    map.set(a.authorityCode, cur);
  }
  return Array.from(map.values()).sort((a, b) => b.total - a.total);
}

function incr(s: PerAuthority, status: ControlStatus) {
  if (status === 'IMPLEMENTED')     s.implemented    += 1;
  else if (status === 'PARTIAL')        s.partial        += 1;
  else if (status === 'COMPENSATING')   s.compensating   += 1;
  else if (status === 'NOT_IMPLEMENTED')s.notImplemented += 1;
  else                                  s.notApplicable  += 1;
}
