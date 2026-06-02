import { useEffect, useState } from 'react';
import { Globe2, ShieldCheck } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  subprocessorsApi,
  SAFEGUARD_LABEL, SAFEGUARD_TONE,
  type PublicSubprocessorResponse,
} from '../../lib/subprocessors';

/**
 * Public PDPL sub-processor transparency list — Sprint 11.
 *
 * <p>PDPL Article 12 transparency obligation: the bank publishes the
 * list of sub-processors so data subjects (and anyone reading the
 * public privacy notice) can see where their data goes. No
 * authentication required — backend route is permitAll'd.
 *
 * <p>This page is a standalone shell — no sidebar, no header chrome —
 * so it can be embedded inside the bank's marketing site / privacy
 * notice via iframe or copied content.
 */
export function PublicSubprocessorListPage() {
  const { lang } = useI18n();
  const [rows, setRows] = useState<PublicSubprocessorResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    subprocessorsApi.publicList(1)
      .then(setRows)
      .catch(err => setError(err instanceof ApiError ? err.message : 'Failed to load'))
      .finally(() => setLoading(false));
  }, []);

  // DataTable requires { id }. PublicSubprocessorResponse has no id by
  // design (PDPL transparency — internal ids are stripped) so we wrap
  // with the subprocessor_code as a stable client-side row key.
  type Row = PublicSubprocessorResponse & { id: string };
  const tableRows: Row[] = rows.map(r => ({ ...r, id: r.subprocessorCode }));

  const cols: Column<Row>[] = [
    {
      header: lang === 'ar' ? 'المعرّف' : 'Code', width: '160px',
      cell: r => <code style={{ fontSize: 12 }}>{r.subprocessorCode}</code>,
    },
    {
      header: lang === 'ar' ? 'الاسم النظامي' : 'Legal name',
      cell: r => (
        <span style={{ fontWeight: 500 }}>
          {lang === 'ar' && r.subProcessorLegalNameAr ? r.subProcessorLegalNameAr : r.subProcessorLegalName}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الدولة' : 'Country', width: '90px', align: 'center',
      cell: r => <code style={{ fontSize: 12 }}>{r.countryOfProcessing}</code>,
    },
    {
      header: lang === 'ar' ? 'المنطقة' : 'Region', width: '180px',
      cell: r => <span style={{ fontSize: 12 }}>{r.regionOfProcessing || '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'الغرض' : 'Purpose',
      cell: r => (
        <span style={{ fontSize: 12 }}>
          {lang === 'ar' && r.processingPurposeAr ? r.processingPurposeAr : r.processingPurposeEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'ضمانة النقل عبر الحدود' : 'Cross-border safeguard',
      width: '220px',
      cell: r => r.crossBorderSafeguardType ? (
        <Badge tone={SAFEGUARD_TONE[r.crossBorderSafeguardType]}>
          {SAFEGUARD_LABEL[r.crossBorderSafeguardType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ) : <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>,
    },
  ];

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: 24 }}>
      <div style={{ marginBottom: 16 }}>
        <h1 style={{ fontSize: 28, margin: 0, marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
          <ShieldCheck size={28} />
          {lang === 'ar' ? 'قائمة معالجي البيانات الفرعيين' : 'Sub-processor list'}
        </h1>
        <p style={{ color: 'var(--ink-3)', fontSize: 14, margin: 0 }}>
          {lang === 'ar'
            ? 'إفصاح عام وفق المادة 12 من نظام حماية البيانات الشخصية — يبيّن كل معالج بيانات فرعي يستعين به البنك مع الضمانات المطبّقة للنقل عبر الحدود.'
            : 'Public disclosure under PDPL Article 12 — every sub-processor the bank engages, with the cross-border safeguards applied.'}
        </p>
      </div>

      <Card padded>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : error ? (
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        ) : (
          <DataTable
            columns={cols}
            rows={tableRows}
            empty={<EmptyState
              Icon={Globe2}
              title={lang === 'ar' ? 'لا يوجد معالجون فرعيون' : 'No sub-processors'}
              sub={lang === 'ar'
                ? 'سيتم تحديث هذه القائمة عند إضافة معالج بيانات فرعي معتمد.'
                : 'This list updates as approved sub-processors are added.'} />}
          />
        )}
      </Card>

      <div style={{ marginTop: 16, fontSize: 12, color: 'var(--ink-3)', textAlign: 'center' }}>
        {lang === 'ar'
          ? 'لمزيد من التفاصيل عن مسؤول حماية البيانات والاعتراضات، يُرجى مراجعة سياسة الخصوصية.'
          : 'For details on the Data Protection Officer and objection mechanisms, see the privacy notice.'}
      </div>
    </div>
  );
}
