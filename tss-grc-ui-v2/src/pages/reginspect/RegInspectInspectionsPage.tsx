import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, Plus } from 'lucide-react';
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
  reginspectApi,
  INSPECTION_STATUS_LABEL, INSPECTION_STATUS_TONE, REGULATOR_LABEL,
  type InspectionResponse, type Regulator,
} from '../../lib/reginspect';

/**
 * Inspections Register — /reginspect/inspections.
 *
 * Lists every inspection workspace (active + closed). Inline "open
 * new" wizard captures regulator + bilingual title + key dates.
 */
const REGULATORS: Regulator[] = ['SAMA', 'NCA', 'CMA', 'SDAIA', 'NCEMA', 'OTHER'];

export function RegInspectInspectionsPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<InspectionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // New inspection wizard.
  const [showNew, setShowNew] = useState(false);
  const [creating, setCreating] = useState(false);
  const [regulator, setRegulator] = useState<Regulator>('SAMA');
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [notifiedAt, setNotifiedAt] = useState('');
  const [entryDate, setEntryDate] = useState('');
  const [exitDate, setExitDate] = useState('');
  const [scopeEn, setScopeEn] = useState('');
  const [scopeAr, setScopeAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await reginspectApi.listInspections(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الورش' : 'Failed to load inspections'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submit = async () => {
    if (!titleEn.trim() || !titleAr.trim()) return;
    setCreating(true);
    try {
      const created = await reginspectApi.createInspection({
        regulator,
        titleEn: titleEn.trim(),
        titleAr: titleAr.trim(),
        notifiedAt: notifiedAt || undefined,
        entryDate: entryDate || undefined,
        exitDate: exitDate || undefined,
        scopeEn: scopeEn || undefined,
        scopeAr: scopeAr || undefined,
      });
      setShowNew(false);
      setTitleEn(''); setTitleAr(''); setNotifiedAt('');
      setEntryDate(''); setExitDate(''); setScopeEn(''); setScopeAr('');
      nav(`/reginspect/inspections/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح الورشة' : 'Failed to create inspection'));
    } finally { setCreating(false); }
  };

  const cols: Column<InspectionResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'External Ref',
      width: '180px', mono: true,
      cell: i => <span style={{ fontSize: 12 }}>{i.externalReference}</span>,
    },
    {
      header: lang === 'ar' ? 'الجهة' : 'Regulator', width: '120px',
      cell: i => (
        <Badge tone="info">
          {REGULATOR_LABEL[i.regulator][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: i => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? i.titleAr : i.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '160px',
      cell: i => (
        <Badge tone={INSPECTION_STATUS_TONE[i.status]}>
          {INSPECTION_STATUS_LABEL[i.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الطلبات' : 'DRs', width: '140px',
      cell: i => (
        <span style={{ fontSize: 12 }}>
          {(i.openDrCount ?? 0)}/{(i.openDrCount ?? 0) + (i.submittedDrCount ?? 0)}{' '}
          {(i.overdueDrCount ?? 0) > 0 && (
            <Badge tone="danger">{i.overdueDrCount} {lang === 'ar' ? 'متأخّر' : 'overdue'}</Badge>
          )}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الافتتاح' : 'Entry', width: '110px',
      cell: i => (
        <span style={{ fontSize: 12 }}>
          {i.entryDate
            ? new Date(i.entryDate).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الإغلاق' : 'Exit', width: '110px',
      cell: i => (
        <span style={{ fontSize: 12 }}>
          {i.exitDate
            ? new Date(i.exitDate).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'ورش التفتيش الرقابي' : 'Inspection Workspaces'}
      sub={lang === 'ar'
        ? 'كل ورش التفتيش الرقابي الميداني'
        : 'All on-site regulatory inspection workspaces'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'التفتيش الرقابي · المرحلة ١٥'
                                : 'Regulatory Inspection · Sprint 15'}
        title={lang === 'ar' ? 'ورش التفتيش الرقابي' : 'Inspection Workspaces'}
        sub={lang === 'ar' ? 'سجل كل ورش التفتيش' : 'Register of all inspections'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'فتح ورشة جديدة' : 'New inspection')}
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
          <Card padded title={lang === 'ar' ? 'فتح ورشة تفتيش جديدة'
                                              : 'New inspection workspace'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'الجهة الرقابية' : 'Regulator'}
                </span>
                <select value={regulator}
                  onChange={e => setRegulator(e.target.value as Regulator)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }}>
                  {REGULATORS.map(r => (
                    <option key={r} value={r}>
                      {REGULATOR_LABEL[r][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'تاريخ الإشعار' : 'Notified at'}
                </span>
                <input type="date" value={notifiedAt}
                  onChange={e => setNotifiedAt(e.target.value)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'تاريخ الافتتاح' : 'Entry date'}
                </span>
                <input type="date" value={entryDate}
                  onChange={e => setEntryDate(e.target.value)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'تاريخ الإغلاق' : 'Exit date'}
                </span>
                <input type="date" value={exitDate}
                  onChange={e => setExitDate(e.target.value)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                              gridColumn: 'span 2' }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}
                </span>
                <input type="text" maxLength={256}
                  value={titleEn} onChange={e => setTitleEn(e.target.value)}
                  placeholder="FY2026 SAMA on-site — Cyber+AML+OpRes posture"
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                              gridColumn: 'span 2' }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}
                </span>
                <input type="text" maxLength={256} dir="rtl"
                  value={titleAr} onChange={e => setTitleAr(e.target.value)}
                  placeholder="تفتيش ساما الميداني 2026 — الوضع السيبراني ومكافحة غسل الأموال والصمود التشغيلي"
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                              gridColumn: 'span 2' }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'النطاق (إنجليزي)' : 'Scope (English)'}
                </span>
                <textarea rows={2} maxLength={4000}
                  value={scopeEn} onChange={e => setScopeEn(e.target.value)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                              gridColumn: 'span 2' }}>
                <span style={{ fontSize: 12, fontWeight: 600 }}>
                  {lang === 'ar' ? 'النطاق (عربي)' : 'Scope (Arabic)'}
                </span>
                <textarea rows={2} maxLength={4000} dir="rtl"
                  value={scopeAr} onChange={e => setScopeAr(e.target.value)}
                  style={{ padding: 6, border: '1px solid var(--border)',
                           borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
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

      <Card padded title={lang === 'ar' ? 'كل الورش' : 'All inspections'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            rowHref={i => `/reginspect/inspections/${i.id}`}
            empty={<EmptyState
              Icon={ClipboardList}
              title={lang === 'ar' ? 'لا توجد ورش تفتيش' : 'No inspections yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك.' : 'Log in.')
                : (lang === 'ar'
                  ? 'افتح ورشة عند استلام إشعار تفتيش.'
                  : 'Open a workspace when notified by a regulator.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}
