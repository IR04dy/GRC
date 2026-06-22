import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Shield, BookOpen } from 'lucide-react';
import { Card } from '../ui/Card';
import { Kpi } from '../ui/Kpi';
import { Badge } from '../ui/Badge';
import { InlineEmpty } from '../ui/InlineEmpty';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  getDomainAlignment, coverageBandTone, authorityStatusTone,
  type DomainAlignment, type DocumentCoverage,
} from '../../lib/regulatoryAuthority';

/**
 * Reusable "Regulatory Alignment" tab — Framework Intersection F5.
 *
 * Mounted on every domain page (Risk, Cyber, BCM, Privacy, TPRM) so
 * users see, on the page they already use, how their domain's
 * controls cover the authority documents that apply to that domain.
 *
 * Props:
 *   - domain: the platform's control domain enum value (CYBER, BCM, …).
 *   - families: which authority document families to show
 *               (e.g. for BCM → ['BCM', 'SAMA', 'Resilience']).
 *
 * Backend: GET /api/v1/regulatory/domain-alignment.
 */
export interface RegulatoryAlignmentTabProps {
  domain: 'RISK' | 'CYBER' | 'BCM' | 'PRIVACY' | 'TPRM' | 'COMPLIANCE' | 'GENERAL';
  /** Authority document families relevant to this domain. */
  families: string[];
}

export function RegulatoryAlignmentTab({ domain, families }: RegulatoryAlignmentTabProps) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [data, setData] = useState<DomainAlignment | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getDomainAlignment(domain, families)
      .then(d => { if (!cancelled) setData(d); })
      .catch((e: unknown) => {
        if (cancelled) return;
        toast.danger(e instanceof ApiError ? e.message
          : (isAr ? 'فشل تحميل التغطية التنظيمية' : 'Failed to load regulatory alignment'));
        setData(null);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [domain, families.join('|')]); // eslint-disable-line react-hooks/exhaustive-deps

  const placeholderCount = useMemo(() => 0, []);   // future: detect placeholder docs in payload
  void placeholderCount;

  if (loading) {
    return (
      <Card title={isAr ? 'التوافق التنظيمي' : 'Regulatory alignment'}>
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
          {isAr ? 'جاري التحميل…' : 'Loading…'}
        </div>
      </Card>
    );
  }
  if (!data || data.documents.length === 0) {
    return (
      <Card title={isAr ? 'التوافق التنظيمي' : 'Regulatory alignment'}>
        <InlineEmpty>
          <div style={{ fontWeight: 600, marginBottom: 4 }}>
            {isAr ? 'لا توجد مرجعيات لهذا المجال' : 'No authority documents for this domain'}
          </div>
          <div className="text-2xs text-muted">
            {isAr ? `العائلات المُتوقّعة: ${families.join('، ')}`
                  : `Expected families: ${families.join(', ')}`}
          </div>
        </InlineEmpty>
      </Card>
    );
  }

  return (
    <div className="dash-grid">
      {/* KPI strip */}
      <div style={{ gridColumn: '1 / -1', display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        <Kpi Icon={BookOpen}
          label={isAr ? 'المرجعيات' : 'Authority documents'}
          value={data.documents.length}
          to="/regulatory/library" />
        <Kpi Icon={Shield}
          label={isAr ? 'البنود المغطّاة' : 'Citations covered'}
          value={`${data.mappedCitations} / ${data.totalCitations}`}
          to="/compliance/posture" />
        <Kpi Icon={AlertTriangle}
          label={isAr ? 'فجوات' : 'Gap citations'}
          value={data.gapCitations}
          to="/controls/library"
          tone={data.gapCitations > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Shield}
          label={isAr ? 'متوسط التغطية' : 'Avg coverage'}
          value={`${data.averageCoveragePct}%`}
          to="/compliance/posture"
          tone={coverageBandTone(data.averageCoveragePct)} />
      </div>

      {/* Per-document table */}
      <Card title={isAr ? 'التغطية حسب المرجعية' : 'Coverage by authority document'}
        action={<Badge tone="primary">{data.documents.length}</Badge>}>
        <table className="grc-table">
          <thead><tr>
            <th>{isAr ? 'الكود' : 'Code'}</th>
            <th>{isAr ? 'العنوان' : 'Title'}</th>
            <th>{isAr ? 'العائلة' : 'Family'}</th>
            <th>{isAr ? 'البنود' : 'Citations'}</th>
            <th>{isAr ? 'التغطية' : 'Coverage'}</th>
            <th>{isAr ? 'فجوات' : 'Gaps'}</th>
          </tr></thead>
          <tbody>
            {data.documents.map(d => <DocRow key={d.authorityDocumentId} d={d} isAr={isAr} />)}
          </tbody>
        </table>
      </Card>

      {/* Gap citations — the actionable list */}
      <Card title={isAr ? 'فجوات تحتاج معالجة' : 'Gap citations needing controls'}
        action={<Badge tone="warning">{data.gapCitations}</Badge>}>
        {data.gapCitations === 0
          ? <InlineEmpty>
              <div style={{ fontWeight: 600 }}>
                {isAr ? 'لا توجد فجوات — كل البنود مغطّاة بضوابط'
                      : 'No gaps — every citation has at least one mapped control'}
              </div>
            </InlineEmpty>
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {data.documents.flatMap(d => d.gaps.map(g => (
                <div key={`${d.authorityDocumentId}-${g.id}`} style={{
                  display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 8,
                  alignItems: 'center', padding: '6px 10px',
                  background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                  borderInlineStart: '3px solid var(--warning, #f6c149)',
                }}>
                  <span className="mono text-2xs" style={{ minWidth: 80 }}>{d.code}</span>
                  <div>
                    <div className="mono text-2xs text-muted">{g.citationCode}</div>
                    <div style={{ fontSize: 12 }}>{isAr && g.titleAr ? g.titleAr : g.titleEn}</div>
                  </div>
                  <Badge tone="warning">{isAr ? 'فجوة' : 'gap'}</Badge>
                </div>
              )))}
            </div>}
      </Card>
    </div>
  );
}

function DocRow({ d, isAr }: { d: DocumentCoverage; isAr: boolean }) {
  return (
    <tr>
      <td className="mono text-2xs">{d.code}</td>
      <td>
        <div style={{ fontWeight: 600 }}>{isAr && d.titleAr ? d.titleAr : d.titleEn}</div>
      </td>
      <td className="text-xs">{d.family}</td>
      <td className="mono text-2xs">
        {d.mappedCitations} / {d.totalCitations}
      </td>
      <td>
        <Badge tone={coverageBandTone(d.coveragePct)}>{d.coveragePct}%</Badge>
        {d.fullCoverage + d.partialCoverage + d.compensatingCoverage > 0 && (
          <span className="text-2xs text-muted" style={{ marginInlineStart: 6 }}>
            ({d.fullCoverage}F · {d.partialCoverage}P · {d.compensatingCoverage}C)
          </span>
        )}
      </td>
      <td>
        {d.gapCitations === 0
          ? <Badge tone="success">{isAr ? 'مكتمل' : 'covered'}</Badge>
          : <Badge tone="warning">{d.gapCitations}</Badge>}
      </td>
    </tr>
  );
}

// Re-export so call-sites can import a single symbol if they want the
// "status tone" helper without pulling in the whole regulatory client.
export { authorityStatusTone };
