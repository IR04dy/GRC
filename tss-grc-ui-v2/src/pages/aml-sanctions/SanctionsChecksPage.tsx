import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Filter, RefreshCw, ScanSearch } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  sanctionsApi, resultLabel, subjectTypeLabel,
  type CheckResult, type ScreeningCheck,
} from '../../lib/sanctions';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const RESULT_TONE: Record<CheckResult, BadgeTone> = {
  NO_HIT: 'success',
  POTENTIAL_HIT: 'warning',
  CONFIRMED_HIT: 'danger',
};

const FILTERS: { value: 'ALL' | CheckResult; en: string; ar: string }[] = [
  { value: 'ALL',           en: 'All',             ar: 'الكل' },
  { value: 'NO_HIT',        en: 'No hit',          ar: 'لا تطابق' },
  { value: 'POTENTIAL_HIT', en: 'Potential hit',   ar: 'تطابق احتمالي' },
  { value: 'CONFIRMED_HIT', en: 'Confirmed hit',   ar: 'تطابق مُؤكّد' },
];

/**
 * Sprint 13 — Screening check register.
 *
 * <p>Lists the most recent 100 screening checks (every subject screened —
 * customer onboarding, transaction counterparty, vendor KYC, …). Filter
 * by result. Click a row to open the check detail page.
 */
export function SanctionsChecksPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();

  const [state, setState] = useState<LoadState>('idle');
  const [checks, setChecks] = useState<ScreeningCheck[]>([]);
  const [filter, setFilter] = useState<'ALL' | CheckResult>('ALL');
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      const rows = await sanctionsApi.listChecks(filter === 'ALL' ? undefined : filter);
      setChecks(rows);
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [filter]);

  return (
    <Shell
      title={isAr ? 'سجلّ الفحوصات' : 'Screening check log'}
      sub={isAr
        ? 'سجلّ كامل لفحوصات الفحص ضد قوائم العقوبات لعملاء البنك ومعاملاته'
        : 'Audit-grade log of every sanctions screening check'}
    >
      <PageHeader
        eyebrow={isAr ? 'الفحص ضد قوائم العقوبات' : 'Sanctions screening'}
        title={isAr ? 'سجلّ الفحوصات' : 'Screening check log'}
        sub={isAr
          ? 'كل فحص يُسجَّل هنا برمز فريد (SCR-YYYY-NNNNNN) ويرتبط بالنتيجة والتطابقات الناتجة'
          : 'Every check is logged with a unique code (SCR-YYYY-NNNNNN) and linked to its result + hits'}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/aml/sanctions/screen">
              <Button variant="primary" size="sm">
                <ScanSearch size={14} /> {isAr ? 'فحص جديد' : 'New screening'}
              </Button>
            </Link>
          </>
        }
      />

      {/* Filter strip */}
      <Card>
        <div style={{
          padding: 12,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
          borderBottom: '1px solid var(--border)',
        }}>
          <Filter size={14} style={{ color: 'var(--ink-3)' }} />
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {isAr ? 'تصفية حسب النتيجة:' : 'Filter by result:'}
          </span>
          {FILTERS.map(f => (
            <button
              key={f.value}
              type="button"
              onClick={() => setFilter(f.value)}
              style={{
                fontSize: 11,
                padding: '4px 10px',
                borderRadius: 'var(--r-sm)',
                border: '1px solid var(--border)',
                background: filter === f.value ? 'var(--accent-50)' : 'transparent',
                color: filter === f.value ? 'var(--accent-700)' : 'var(--ink-2)',
                cursor: 'pointer',
              }}>
              {isAr ? f.ar : f.en}
            </button>
          ))}
        </div>

        {state === 'loading' && (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        )}

        {state === 'error' && (
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>{err}</div>
        )}

        {state === 'ready' && checks.length === 0 && (
          <div style={{ padding: 48, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'لا توجد فحوصات بهذه التصفية' : 'No checks under this filter'}
          </div>
        )}

        {state === 'ready' && checks.map(c => (
          <button
            key={c.id}
            type="button"
            onClick={() => navigate(`/aml/sanctions/checks/${c.id}`)}
            style={{
              width: '100%',
              textAlign: 'start',
              padding: '12px 16px',
              borderBottom: '1px solid var(--border)',
              background: 'transparent',
              cursor: 'pointer',
              display: 'grid',
              gridTemplateColumns: '120px 1fr 160px 130px 24px',
              gap: 12,
              alignItems: 'center',
            }}>
            <div style={{ fontFamily: 'monospace', fontSize: 11, fontWeight: 700 }}>
              {c.checkCode}
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 600 }}>
                {isAr && c.subjectNameAr ? c.subjectNameAr : c.subjectNameEn}
              </div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                {subjectTypeLabel(c.subjectType, isAr)}
                {c.subjectReference && ` · ${c.subjectReference}`}
              </div>
            </div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
              {c.checkAt.slice(0, 16).replace('T', ' ')}
              <br />
              <span style={{ fontFamily: 'monospace' }}>{c.checkSource}</span>
            </div>
            <div>
              <Badge tone={RESULT_TONE[c.result]}>{resultLabel(c.result, isAr)}</Badge>
              {c.hits.length > 0 && (
                <span style={{ fontSize: 10, color: 'var(--ink-3)', marginInlineStart: 4 }}>
                  · {c.hits.length} {isAr ? 'تطابق' : 'hit(s)'}
                </span>
              )}
            </div>
            <ArrowRight size={14} style={{ opacity: 0.5 }} />
          </button>
        ))}
      </Card>
    </Shell>
  );
}
