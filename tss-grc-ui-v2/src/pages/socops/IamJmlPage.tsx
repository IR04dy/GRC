import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, RefreshCw, ShieldAlert, UserCheck, UserX, KeyRound, CalendarClock } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type IamJmlEvent, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'sod_violations' | 'privileged' | 'pending' | 'completed';

const REVIEW_WINDOWS = [7, 14, 30, 60] as const;
type ReviewWindow = typeof REVIEW_WINDOWS[number];

/** Whole days from now until an ISO timestamp (negative = overdue). */
function daysUntil(iso?: string | null): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime() - Date.now();
  return Math.ceil(ms / 86_400_000);
}

/** Recertification urgency → badge tone. */
function dueTone(days: number | null): 'danger' | 'warning' | 'info' | 'neutral' {
  if (days == null) return 'neutral';
  if (days <= 7) return 'danger';
  if (days <= 14) return 'warning';
  return 'info';
}

const TYPE_TONE: Record<string, 'success' | 'info' | 'danger' | 'warning' | 'neutral'> = {
  JOINER: 'success', MOVER: 'info', LEAVER: 'danger',
  PRIVILEGED_GRANT: 'warning', PRIVILEGED_REVOKE: 'info', RECERTIFICATION: 'neutral',
};

const OUTCOME_TONE: Record<string, 'warning' | 'success' | 'info' | 'danger' | 'neutral'> = {
  PENDING: 'warning', APPROVED: 'info', COMPLETED: 'success', REJECTED: 'danger', CANCELLED: 'neutral',
};

export function IamJmlPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<IamJmlEvent> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('sod_violations');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  // Privileged-access recertification queue (orphaned endpoint:
  // /api/v1/socops/iam-jml/privileged-reviews-due?days=N).
  const [reviewWindow, setReviewWindow] = useState<ReviewWindow>(30);
  const [reviewsDue, setReviewsDue] = useState<IamJmlEvent[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(false);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.iamJml(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  async function loadReviews(days: ReviewWindow) {
    setReviewsLoading(true);
    try {
      const list = await socopsApi.iamJmlReviewsDue(days);
      setReviewsDue([...list].sort((a, b) => (a.reviewDueAt ?? '').localeCompare(b.reviewDueAt ?? '')));
    } catch { setReviewsDue([]); }
    finally { setReviewsLoading(false); }
  }
  useEffect(() => { void loadReviews(reviewWindow); }, [reviewWindow]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all')            return all;
    if (filter === 'sod_violations') return all.filter(e => e.sodViolationDetected);
    if (filter === 'privileged')     return all.filter(e => e.privileged);
    if (filter === 'pending')        return all.filter(e => e.outcome === 'PENDING');
    if (filter === 'completed')      return all.filter(e => e.outcome === 'COMPLETED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      sod: all.filter(e => e.sodViolationDetected).length,
      privileged: all.filter(e => e.privileged).length,
      pending: all.filter(e => e.outcome === 'PENDING').length,
      completed: all.filter(e => e.outcome === 'COMPLETED').length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'دورة حياة الهويات (JML)' : 'IAM joiner-mover-leaver'} sub={isAr ? 'ECC-2 ضوابط ٢ — سجل من-كان-يملك-ماذا' : 'ECC-2 Family 2 — who-had-what audit'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'دورة حياة الهويات والصلاحيات' : 'IAM joiner-mover-leaver + privileged access'}
        sub={isAr ? 'كل تغيير صلاحية موثّق — يكشف فصل المهام تلقائياً'
                  : 'Every entitlement change documented — SoD violations flagged automatically'}
        actions={<Button variant="ghost" size="sm" onClick={() => { void load(); void loadReviews(reviewWindow); }}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ShieldAlert} label={isAr ? 'مخالفات SoD' : 'SoD violations'}        value={String(counts.sod)}        tone={counts.sod > 0 ? 'danger' : 'success'} onClick={() => setFilter('sod_violations')} active={filter === 'sod_violations'} />
        <Kpi Icon={Users}       label={isAr ? 'صلاحيات حرجة' : 'Privileged events'}    value={String(counts.privileged)} tone={counts.privileged > 0 ? 'warning' : 'neutral'} onClick={() => setFilter('privileged')} active={filter === 'privileged'} />
        <Kpi Icon={KeyRound}    label={isAr ? `مراجعات مستحقة (${reviewWindow}ي)` : `Reviews due (${reviewWindow}d)`} value={String(reviewsDue.length)} tone={reviewsDue.length > 0 ? 'warning' : 'success'} to="/socops" />
        <Kpi Icon={UserCheck}   label={isAr ? 'قيد الانتظار' : 'Pending'}              value={String(counts.pending)}    tone={counts.pending > 0 ? 'warning' : 'success'} onClick={() => setFilter('pending')} active={filter === 'pending'} />
        <Kpi Icon={UserX}       label={isAr ? 'مكتمل' : 'Completed'}                    value={String(counts.completed)}  tone="info" onClick={() => setFilter('completed')} active={filter === 'completed'} />
      </div>

      {/* Privileged-access recertification queue — SAMA CSF 3.3.5 / NCA ECC
          2-2-3 require privileged entitlements to be periodically re-attested.
          Driven by the dedicated privileged-reviews-due endpoint. */}
      <Card
        title={isAr ? 'مراجعات الصلاحيات الحرجة المستحقة' : 'Privileged access reviews due'}
        Icon={CalendarClock}
        action={
          <div role="group" aria-label={isAr ? 'نافذة المراجعة' : 'Review window'}
            style={{ display: 'inline-flex', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', overflow: 'hidden' }}>
            {REVIEW_WINDOWS.map(w => (
              <button key={w} onClick={() => setReviewWindow(w)} aria-pressed={reviewWindow === w}
                style={{ padding: '5px 11px', fontSize: 12, fontWeight: 600, border: 'none', cursor: 'pointer',
                  background: reviewWindow === w ? 'var(--primary)' : 'var(--surface-2)',
                  color: reviewWindow === w ? '#fff' : 'var(--ink-2)' }}>
                {isAr ? `${w}ي` : `${w}d`}
              </button>
            ))}
          </div>
        }>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', margin: '0 0 12px' }}>
          {isAr
            ? 'صلاحيات حرجة قائمة تستحق إعادة الاعتماد خلال النافذة المحددة — وفق إطار ساما للأمن السيبراني ٣-٣-٥ والضوابط الأساسية للأمن السيبراني ٢-٢-٣.'
            : 'Standing privileged entitlements whose recertification falls due inside the selected window — per SAMA CSF 3.3.5 and NCA ECC 2-2-3.'}
        </p>
        {reviewsLoading
          ? <div style={{ padding: 20, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : reviewsDue.length === 0
            ? <div style={{ padding: 20, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                {isAr ? 'لا مراجعات مستحقة في هذه النافذة 👍' : 'No reviews due in this window 👍'}
              </div>
            : <table className="tbl" style={{ width: '100%' }}>
                <thead><tr>
                  <th>{isAr ? 'الشخص' : 'Subject'}</th>
                  <th>{isAr ? 'الدور/الصلاحية' : 'Role / entitlement'}</th>
                  <th>{isAr ? 'النظام المستهدف' : 'Target system'}</th>
                  <th>{isAr ? 'تاريخ الاستحقاق' : 'Review due'}</th>
                  <th>{isAr ? 'المتبقّي' : 'Countdown'}</th>
                </tr></thead>
                <tbody>{reviewsDue.map(e => {
                  const d = daysUntil(e.reviewDueAt);
                  return (
                    <tr key={e.id}>
                      <td style={{ fontSize: 11 }}>
                        <Link to={`/socops/iam-jml/${e.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                          {e.subjectUsername ?? e.eventCode}
                        </Link>
                        {e.subjectJobTitle && <div style={{ fontSize: 9, color: 'var(--ink-3)' }}>{e.subjectJobTitle}</div>}
                      </td>
                      <td style={{ fontSize: 10 }}>{e.roleOrEntitlement ?? '—'}</td>
                      <td style={{ fontSize: 10 }}>{e.targetSystem ?? '—'}</td>
                      <td className="mono" style={{ fontSize: 10 }}>{e.reviewDueAt?.slice(0, 10)}</td>
                      <td>
                        <Badge tone={dueTone(d)}>
                          {d == null ? '—'
                            : d < 0 ? (isAr ? `متأخّر ${Math.abs(d)}ي` : `${Math.abs(d)}d overdue`)
                            : d === 0 ? (isAr ? 'اليوم' : 'today')
                            : (isAr ? `خلال ${d}ي` : `in ${d}d`)}
                        </Badge>
                      </td>
                    </tr>
                  );
                })}</tbody>
              </table>}
      </Card>

      <div style={{ height: 16 }} />

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'sod_violations', label: isAr ? 'مخالفات SoD'  : 'SoD violations', count: counts.sod },
          { id: 'privileged',     label: isAr ? 'حرجة'         : 'Privileged',     count: counts.privileged },
          { id: 'pending',        label: isAr ? 'قيد الانتظار' : 'Pending',         count: counts.pending },
          { id: 'completed',      label: isAr ? 'مكتمل'        : 'Completed',       count: counts.completed },
          { id: 'all',            label: isAr ? 'الكل'         : 'All',             count: counts.total },
        ] as { id: Filter; label: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setFilter(td.id)} style={{ padding: '8px 12px', fontSize: 12, fontWeight: 600,
            border: `1px solid ${filter === td.id ? 'var(--primary)' : 'var(--border)'}`,
            background: filter === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
            borderRadius: 'var(--r-sm)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {td.label}<Badge tone={filter === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}><div style={{ color: 'var(--danger-fg)' }}>{errMsg}</div></div>}
        {state === 'ready' && rows.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th>
              <th>{isAr ? 'الوقت' : 'When'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الشخص' : 'Subject'}</th>
              <th>{isAr ? 'القسم/الوظيفة' : 'Dept / Title'}</th>
              <th>{isAr ? 'النظام' : 'Source → target'}</th>
              <th>{isAr ? 'الدور/الصلاحية' : 'Role / entitlement'}</th>
              <th>{isAr ? 'صلاحية حرجة' : 'Priv.'}</th>
              <th>SoD</th>
              <th>{isAr ? 'النتيجة' : 'Outcome'}</th>
            </tr></thead>
            <tbody>{rows.map(e => (
              <tr key={e.id}>
                <td className="mono" style={{ fontSize: 10 }}>
                  <Link to={`/socops/iam-jml/${e.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {e.eventCode}
                  </Link>
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{e.occurredAt?.slice(0, 16).replace('T', ' ')}</td>
                <td><Badge tone={TYPE_TONE[e.eventType] ?? 'neutral'}>{e.eventType}</Badge></td>
                <td style={{ fontSize: 11 }}>
                  <strong>{e.subjectUsername ?? '—'}</strong>
                  {e.subjectEmail && <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{e.subjectEmail}</div>}
                </td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                  {e.subjectDepartment ?? '—'}{e.subjectJobTitle && <div>{e.subjectJobTitle}</div>}
                </td>
                <td style={{ fontSize: 10 }}>{e.sourceSystem}{e.targetSystem && <> → {e.targetSystem}</>}</td>
                <td style={{ fontSize: 10 }}>{e.roleOrEntitlement ?? '—'}</td>
                <td>{e.privileged ? <Badge tone="warning">{isAr ? 'نعم' : 'Priv'}</Badge> : '—'}</td>
                <td>
                  {e.sodViolationDetected
                    ? <Badge tone="danger">{isAr ? 'مخالفة' : 'Violation'}</Badge>
                    : <Badge tone="success">{isAr ? 'سليم' : 'OK'}</Badge>}
                  {e.sodViolationDetail && <div style={{ fontSize: 9, color: 'var(--danger-fg)' }}>{e.sodViolationDetail.slice(0, 60)}</div>}
                </td>
                <td><Badge tone={OUTCOME_TONE[e.outcome] ?? 'neutral'}>{e.outcome}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
