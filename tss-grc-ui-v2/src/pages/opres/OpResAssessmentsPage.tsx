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
  opresApi, STATUS_LABEL, STATUS_TONE,
  type AssessmentResponse,
} from '../../lib/opres';

/**
 * OpRes Assessments Register — /opres/assessments.
 *
 * Lists annual + ad-hoc assessments for the tenant. Row click drills
 * into the cell editor. "Open new" CTA opens the inline wizard.
 */
export function OpResAssessmentsPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<AssessmentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Inline new-assessment wizard.
  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const today = new Date();
  const [fy, setFy] = useState<number>(today.getFullYear());
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [dueDate, setDueDate] = useState<string>('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true);
    setError(null);
    try {
      const r = await opresApi.listAssessments(0, 100);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل التقييمات' : 'Failed to load assessments'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!titleEn.trim() || !titleAr.trim()) return;
    setCreating(true);
    try {
      const created = await opresApi.createAssessment({
        fiscalYear: fy,
        titleEn: titleEn.trim(),
        titleAr: titleAr.trim(),
        dueDate: dueDate || undefined,
      });
      setShowNew(false);
      setTitleEn(''); setTitleAr(''); setDueDate('');
      nav(`/opres/assessments/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح التقييم' : 'Failed to create assessment'));
    } finally {
      setCreating(false);
    }
  };

  const cols: Column<AssessmentResponse>[] = [
    {
      header: lang === 'ar' ? 'السنة المالية' : 'FY', width: '90px',
      cell: a => <code style={{ fontSize: 12 }}>FY{a.fiscalYear}</code>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: a => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? a.titleAr : a.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: a => (
        <Badge tone={STATUS_TONE[a.status]}>
          {STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الاكتمال' : 'Completeness', width: '120px',
      cell: a => <span style={{ fontSize: 12 }}>{a.completeness ?? '0/5'}</span>,
    },
    {
      header: lang === 'ar' ? 'النضج الحالي' : 'Avg Current', width: '110px',
      cell: a => (
        <span style={{ fontSize: 12, fontWeight: 600 }}>
          {a.avgCurrentScore != null ? Number(a.avgCurrentScore).toFixed(1) : '—'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النضج المستهدف' : 'Avg Target', width: '110px',
      cell: a => (
        <span style={{ fontSize: 12 }}>
          {a.avgTargetScore != null ? Number(a.avgTargetScore).toFixed(1) : '—'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due', width: '120px',
      cell: a => (
        <span style={{ fontSize: 12 }}>
          {a.dueDate ? new Date(a.dueDate)
            .toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB') : '—'}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'تقييمات الصمود التشغيلي'
                            : 'Op-Res Assessments'}
      sub={lang === 'ar'
        ? 'سِجِل التقييمات السنوية والمؤقّتة بحسب إطار الصمود التشغيلي 2024 لدى ساما.'
        : 'Annual and ad-hoc self-assessments under SAMA Op-Res 2024.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الصمود التشغيلي · المرحلة ١٤'
                                : 'Operational Resilience · Sprint 14'}
        title={lang === 'ar' ? 'تقييمات الصمود التشغيلي'
                              : 'Op-Res Assessments'}
        sub={lang === 'ar' ? 'سجل التقييمات السنوية والمؤقّتة'
                            : 'Register of annual + ad-hoc assessments'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'فتح تقييم جديد' : 'New assessment')}
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
          <Card padded title={lang === 'ar' ? 'فتح تقييم جديد' : 'New assessment'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'السنة المالية' : 'Fiscal year'}
                </span>
                <input type="number" min={2000} max={2100}
                  value={fy}
                  onChange={e => setFy(parseInt(e.target.value, 10) || today.getFullYear())}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due date'}
                </span>
                <input type="date"
                  value={dueDate}
                  onChange={e => setDueDate(e.target.value)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}
                </span>
                <input type="text" maxLength={256}
                  value={titleEn}
                  onChange={e => setTitleEn(e.target.value)}
                  placeholder="FY2026 SAMA Op-Res Annual Self-Assessment"
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}
                </span>
                <input type="text" maxLength={256} dir="rtl"
                  value={titleAr}
                  onChange={e => setTitleAr(e.target.value)}
                  placeholder="التقييم الذاتي السنوي للصمود التشغيلي 2026"
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button onClick={submit} disabled={creating || !titleEn.trim() || !titleAr.trim()}
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

      <Card padded title={lang === 'ar' ? 'كل التقييمات' : 'All assessments'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            rowHref={a => `/opres/assessments/${a.id}`}
            empty={<EmptyState
              Icon={Shield}
              title={lang === 'ar' ? 'لا توجد تقييمات' : 'No assessments yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض التقييمات.' : 'Log in to view assessments.')
                : (lang === 'ar'
                  ? 'افتح تقييماً جديداً لبدء دورة الصمود التشغيلي السنوية.'
                  : 'Open a new assessment to start the annual Op-Res cycle.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}
