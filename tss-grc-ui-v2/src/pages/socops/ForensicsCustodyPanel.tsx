// SOC-D — chain-of-custody timeline for a forensic artefact. Renders the
// append-only ledger (CAPTURE then a row per transfer) on the artefact detail
// page. Read-only: the chain can only be extended via transfer-custody, never
// edited here (a DB trigger enforces immutability server-side).
import { useEffect, useState } from 'react';
import { Fingerprint, ArrowRight } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type ForensicsCustodyEvent } from '../../lib/socops';

const EVENT_TONE: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'neutral'> = {
  CAPTURE: 'info', TRANSFER: 'neutral', VERIFY: 'success', DISPOSE: 'danger', RELEASE: 'warning',
};

export function ForensicsCustodyPanel({ artefactId }: { artefactId: number }) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const [rows, setRows] = useState<ForensicsCustodyEvent[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    socopsApi.forensicsCustodyChain(artefactId)
      .then(r => { if (alive) setRows(r); })
      .catch(() => { if (alive) setRows([]); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [artefactId]);

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, marginBottom: 10 }}>
        <Fingerprint size={16} /> {ar ? 'سلسلة الحفظ' : 'Chain of custody'}
        <Badge tone="neutral">{rows.length}</Badge>
        <span style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 500 }}>
          {ar ? '· سجل غير قابل للتعديل' : '· append-only ledger'}
        </span>
      </div>
      <Card padded={false}>
        {loading ? (
          <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 12 }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>
        ) : rows.length === 0 ? (
          <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 12 }}>{ar ? 'لا توجد أحداث حفظ بعد.' : 'No custody events yet.'}</div>
        ) : (
          <div>
            {rows.map((e, i) => (
              <div key={e.id} style={{
                display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 12, alignItems: 'center',
                padding: '10px 14px', borderBottom: i < rows.length - 1 ? '1px solid var(--border)' : 'none',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', minWidth: 22 }}>#{e.seqNo}</span>
                  <Badge tone={EVENT_TONE[e.eventType] ?? 'neutral'}>{e.eventType}</Badge>
                </div>
                <div style={{ fontSize: 12, minWidth: 0 }}>
                  <span style={{ fontWeight: 600 }}>
                    {e.fromCustodian ? <>{e.fromCustodian} <ArrowRight size={11} style={{ verticalAlign: 'middle' }} /> </> : null}
                    {e.toCustodian}
                  </span>
                  {(e.toLocation ?? e.fromLocation) && (
                    <span style={{ color: 'var(--ink-3)' }}> · {e.toLocation ?? e.fromLocation}</span>
                  )}
                  {e.notes && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{e.notes}</div>}
                  {e.artefactHash && (
                    <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 340 }}>
                      sha256 {e.artefactHash}
                    </div>
                  )}
                </div>
                <div style={{ textAlign: ar ? 'left' : 'right', fontSize: 10, color: 'var(--ink-3)' }}>
                  <div className="mono">{e.transferredAt?.slice(0, 16).replace('T', ' ')}</div>
                  {e.actor && <div>{ar ? 'بواسطة ' : 'by '}{e.actor}</div>}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
