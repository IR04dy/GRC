import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  rrpApi,
  CYCLE_STATUS_LABEL, CYCLE_STATUS_TONE,
  type CycleResponse,
} from '../../lib/rrp';

export function RrpCyclesPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<CycleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const [fiscalYear, setFiscalYear] = useState(new Date().getFullYear() + 1);
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [submissionDueDate, setSubmissionDueDate] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await rrpApi.listCycles(0, 100);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الدورات' : 'Failed to load cycles'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!titleEn.trim() || !titleAr.trim()) return;
    setCreating(true);
    try {
      const created = await rrpApi.createCycle({
        fiscalYear,
        titleEn: titleEn.trim(),
        titleAr: titleAr.trim(),
        submissionDueDate: submissionDueDate || undefined,
      });
      setShowNew(false);
      setTitleEn(''); setTitleAr(''); setSubmissionDueDate('');
      nav(`/rrp/cycles/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح الدورة' : 'Failed to create cycle'));
    } finally { setCreating(false); }
  };

  const cols: Column<CycleResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference',
      width: '180px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'السنة' : 'FY', width: '70px',
      cell: c => <strong>{c.fiscalYear}</strong>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: c => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? c.titleAr : c.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '150px',
      cell: c => (
        <Badge tone={CYCLE_STATUS_TONE[c.status]}>
          {CYCLE_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'خيارات' : 'Options', width: '90px',
      cell: c => <span style={{ fontSize: 12 }}>{c.recoveryOptionCount ?? 0}</span>,
    },
    {
      header: lang === 'ar' ? 'وظائف حيوية' : 'Critical fns', width: '110px',
      cell: c => <span style={{ fontSize: 12 }}>{c.criticalFunctionCount ?? 0}</span>,
    },
    {
      header: lang === 'ar' ? 'مؤشّرات' : 'Indicators', width: '180px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {(c.indicatorsRed ?? 0) > 0 && (
            <Badge tone="danger" className="m-r-6">{c.indicatorsRed}</Badge>
          )}
          {(c.indicatorsAmber ?? 0) > 0 && (
            <Badge tone="warning" className="m-r-6">{c.indicatorsAmber}</Badge>
          )}
          {c.indicatorCount ?? 0} {lang === 'ar' ? 'إجمالي' : 'total'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الاستحقاق' : 'Due', width: '120px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {c.submissionDueDate
            ? new Date(c.submissionDueDate).toLocaleDateString(
                lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'دورات خطّة الاستعادة' : 'RRP Cycles'}
      sub={lang === 'ar'
        ? 'دورات خطّة الاستعادة والحلّ السنوية.'
        : 'Annual RRP cycle register.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الاستعادة والحلّ'
                                : 'RRP'}
        title={lang === 'ar' ? 'دورات الخطّة' : 'RRP Cycles'}
        sub={lang === 'ar' ? 'سجل كل دورات خطّة الاستعادة والحلّ'
                            : 'Register of all RRP cycles'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'دورة جديدة' : 'New cycle')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded title={lang === 'ar' ? 'فتح دورة خطّة جديدة'
                                              : 'Open new RRP cycle'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'السنة المالية' : 'Fiscal year'}
                </span>
                <input type="number" min={2024} max={2035}
                  value={fiscalYear}
                  onChange={e => setFiscalYear(parseInt(e.target.value, 10) || 0)}
                  style={inputStyle} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'تاريخ التقديم' : 'Submission due'}
                </span>
                <input type="date" value={submissionDueDate}
                  onChange={e => setSubmissionDueDate(e.target.value)}
                  style={inputStyle} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                              gridColumn: 'span 2' }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}
                </span>
                <input value={titleEn} onChange={e => setTitleEn(e.target.value)}
                  placeholder="FY2026 Recovery & Resolution Plan" style={inputStyle} maxLength={256} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                              gridColumn: 'span 2' }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}
                </span>
                <input value={titleAr} dir="rtl"
                  onChange={e => setTitleAr(e.target.value)}
                  placeholder="خطّة الاستعادة والحلّ للسنة المالية 2026"
                  style={inputStyle} maxLength={256} />
              </label>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submit}
                      disabled={creating || !titleEn.trim() || !titleAr.trim()}
                      variant="primary">
                {creating
                  ? (lang === 'ar' ? 'جارٍ الفتح…' : 'Creating…')
                  : (lang === 'ar' ? 'فتح' : 'Create')}
              </Button>
              <Button onClick={() => setShowNew(false)} variant="secondary">
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded title={lang === 'ar' ? 'كل الدورات' : 'All cycles'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            rowHref={c => `/rrp/cycles/${c.id}`}
            empty={<EmptyState
              Icon={Shield}
              title={lang === 'ar' ? 'لا توجد دورات' : 'No cycles yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الدورات.' : 'Log in to view cycles.')
                : (lang === 'ar'
                  ? 'افتح دورة جديدة للسنة المالية القادمة.'
                  : 'Open a new RRP cycle for the upcoming fiscal year.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--border)',
  borderRadius: 4,
  fontSize: 13,
};
