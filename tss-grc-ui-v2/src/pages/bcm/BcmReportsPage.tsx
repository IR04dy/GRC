import { useEffect, useState } from 'react';
import { FileBarChart, Download } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { listReportCatalog, generateReport, type BcmReportDef } from '../../lib/bcmReports';

/**
 * BCM Reports page — BCM-7 catalogue + generic viewer.
 *
 * <p>Two-pane layout: catalogue list on the left, generated report payload
 * on the right. Payload is rendered generically — primitive values surface as
 * KPI chips, arrays as tables, nested objects as labelled blocks. That keeps
 * the page useful for all 15 reports without 15 bespoke renderers.
 *
 * <p>Each generation hits the backend which writes an audit row, so the
 * report timeline is reconstructable from /api/v1/audit/logs?actionType=REPORT.
 */
export function BcmReportsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [catalog, setCatalog] = useState<BcmReportDef[]>([]);
  const [selected, setSelected] = useState<BcmReportDef | null>(null);
  const [payload, setPayload]   = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const cat = await listReportCatalog();
        // Defensive: backend should always return an array, but if a proxy
        // or refresh-flow ever hands us undefined/null, fall back to [] so
        // the render path below never blows up on `.length`/`.map`.
        const safe = Array.isArray(cat) ? cat : [];
        setCatalog(safe);
        if (safe.length) setSelected(safe[0]);
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message
          : (isAr ? 'تعذر تحميل التقارير' : 'Failed to load report catalog'));
      } finally {
        setLoading(false);
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function runReport(def: BcmReportDef) {
    setSelected(def);
    setGenerating(true);
    try {
      const body = await generateReport(def.code);
      setPayload(body);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل توليد التقرير' : 'Report generation failed'));
    } finally {
      setGenerating(false);
    }
  }

  function downloadJson() {
    if (!payload || !selected) return;
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `bcm-${selected.code.toLowerCase()}-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تقارير الاستمرارية' : 'BCM reports'}
        sub={isAr
          ? 'خمسة عشر تقريراً جاهزاً تغطي الجاهزية والفجوات والإجراءات التصحيحية وأكثر.'
          : 'Fifteen ready-to-run reports across readiness, gaps, CAPAs and more.'}
        eyebrow={isAr ? 'BCM' : 'BCM'}
      />

      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>
        {/* ── Catalogue (left) ── */}
        <Card title={isAr ? 'الكتالوج' : 'Catalog'}>
          {loading && <div className="text-muted" style={{ fontSize: 11, marginBottom: 6 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>}
          {catalog.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد تقارير' : 'No reports'} />
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: 4 }}>
              {catalog.map(def => (
                <li key={def.code}>
                  <button
                    onClick={() => void runReport(def)}
                    style={{
                      width: '100%', textAlign: 'start',
                      padding: '8px 12px', borderRadius: 6,
                      background: selected?.code === def.code ? 'var(--surface-2)' : 'transparent',
                      border: '1px solid transparent', cursor: 'pointer',
                      display: 'flex', alignItems: 'center', gap: 8, fontSize: 13,
                    }}>
                    <FileBarChart size={14} style={{ flexShrink: 0 }} />
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {isAr ? def.titleAr : def.titleEn}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* ── Payload viewer (right) ── */}
        <Card
          title={selected ? (isAr ? selected.titleAr : selected.titleEn)
                          : (isAr ? 'اختر تقريراً' : 'Pick a report')}
          action={selected && payload ? (
            <Button variant="ghost" onClick={downloadJson}>
              <Download size={14} /> {isAr ? 'تنزيل JSON' : 'Download JSON'}
            </Button>
          ) : null}
        >
          {!selected
            ? <InlineEmpty text={isAr ? 'اختر تقريراً من الكتالوج' : 'Select a report from the catalog'} />
            : !payload
              ? <InlineEmpty text={generating
                  ? (isAr ? 'يتم التوليد…' : 'Generating…')
                  : (isAr ? 'اضغط على التقرير لتوليده' : 'Click the report to generate')} />
              : <ReportRenderer body={payload} isAr={isAr} />}
        </Card>
      </div>
    </Shell>
  );
}

// ─── Generic payload renderer ─────────────────────────────────────

/** camelCase / snake_case JSON key → human-readable Title Case label */
function humanLabel(key: string): string {
  return key
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, c => c.toUpperCase());
}

/** Format a raw JSON value into something readable for end-users */
function formatValue(val: unknown): string {
  if (val == null) return '—';
  if (typeof val === 'boolean') return val ? 'Yes' : 'No';
  if (typeof val === 'number') {
    // Percentage heuristic: field names like "Rate", "Percent", "Score" often carry 0–1 fractions
    return Number.isInteger(val) ? val.toLocaleString() : val.toFixed(2);
  }
  if (typeof val === 'string') {
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(val)) {
      return new Date(val).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    }
    if (/^\d{4}-\d{2}-\d{2}$/.test(val)) {
      return new Date(val).toLocaleDateString(undefined, { dateStyle: 'medium' });
    }
  }
  return String(val);
}

function ReportRenderer({ body, isAr }: { body: Record<string, unknown>; isAr: boolean }) {
  const entries = Object.entries(body).filter(([k]) => k !== 'reportCode' && k !== 'generatedAt');
  const generatedAt = body.generatedAt as string | undefined;

  // Split: primitive values → KPI strip; arrays of rows → tables; objects → labelled blocks.
  const primitives = entries.filter(([, v]) => typeof v !== 'object' || v === null);
  const arrays     = entries.filter(([, v]) => Array.isArray(v));
  const objects    = entries.filter(([, v]) => v != null && typeof v === 'object' && !Array.isArray(v));

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      {generatedAt && (
        <div className="text-muted" style={{ fontSize: 11 }}>
          {isAr ? 'تم التوليد في' : 'Generated at'}:{' '}
          <span className="mono">{formatValue(generatedAt)}</span>
        </div>
      )}

      {primitives.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(160px,1fr))', gap: 8 }}>
          {primitives.map(([k, v]) => (
            <div key={k} style={{
              border: '1px solid var(--border)', borderRadius: 6, padding: '10px 12px',
              display: 'grid', gap: 4,
            }}>
              <div className="text-muted" style={{ fontSize: 11 }}>{humanLabel(k)}</div>
              <div style={{ fontSize: 18, fontWeight: 600 }}>{formatValue(v)}</div>
            </div>
          ))}
        </div>
      )}

      {objects.map(([k, v]) => (
        <div key={k}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{humanLabel(k)}</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 6 }}>
            {Object.entries(v as Record<string, unknown>).map(([sk, sv]) => (
              <div key={sk} style={{ display: 'flex', justifyContent: 'space-between',
                  border: '1px solid var(--border)', borderRadius: 4, padding: '6px 10px', fontSize: 12 }}>
                <span className="text-muted">{humanLabel(sk)}</span>
                <strong>{formatValue(sv)}</strong>
              </div>
            ))}
          </div>
        </div>
      ))}

      {arrays.map(([k, v]) => {
        const rows = v as Record<string, unknown>[];
        if (rows.length === 0) {
          return (
            <div key={k}>
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{humanLabel(k)}</div>
              <Badge tone="neutral">{isAr ? 'لا توجد بيانات' : 'No rows'}</Badge>
            </div>
          );
        }
        const cols = Object.keys(rows[0]);
        return (
          <div key={k}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{humanLabel(k)}</div>
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ width: '100%', fontSize: 12 }}>
                <thead>
                  <tr>{cols.map(c => <th key={c} style={{ textAlign: 'start' }}>{humanLabel(c)}</th>)}</tr>
                </thead>
                <tbody>
                  {rows.slice(0, 100).map((row, i) => (
                    <tr key={i}>{cols.map(c => <td key={c}>{formatValue(row[c])}</td>)}</tr>
                  ))}
                </tbody>
              </table>
              {rows.length > 100 && (
                <div className="text-muted" style={{ fontSize: 11, marginTop: 4 }}>
                  {isAr ? `… و ${rows.length - 100} صف إضافي` : `… and ${rows.length - 100} more rows`}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
