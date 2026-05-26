import { useEffect, useState } from 'react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  dataclassApi,
  FLOW_STATUS_LABEL, FLOW_STATUS_TONE,
  ADEQUACY_LABEL,
  type FlowResponse,
} from '../../lib/dataclass';

/** Data flows register — Sprint 32 frontend. */
export function DataclassFlowsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();
  const [rows, setRows] = useState<FlowResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await dataclassApi.listFlows();
        if (!cancelled) setRows(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل سجل التدفقات' : 'Failed to load flows'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const cols: Column<FlowResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '150px', mono: true,
      cell: f => <span style={{ fontSize: 12 }}>{f.reference}</span> },
    { header: lang === 'ar' ? 'المصدر' : 'Source',
      cell: f => <span style={{ fontSize: 13 }}>{f.sourceSystemLabel ?? '—'}</span> },
    { header: lang === 'ar' ? 'الوجهة' : 'Destination',
      cell: f => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>{f.destinationSystemLabel ?? '—'}</div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>{f.destinationCountry ?? ''}</div>
        </div>
      ) },
    { header: lang === 'ar' ? 'النمط' : 'Mode', width: '110px',
      cell: f => f.transferMode
        ? <Badge tone="info">{f.transferMode}</Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'مستوى الحماية' : 'Adequacy', width: '170px',
      cell: f => f.destinationAdequacy
        ? <Badge tone={
            f.destinationAdequacy === 'IN_KINGDOM' ? 'success' :
            f.destinationAdequacy === 'GCC' ? 'info' :
            f.destinationAdequacy === 'ADEQUATE' ? 'warning' : 'danger'}>
            {ADEQUACY_LABEL[f.destinationAdequacy][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span> },
    { header: lang === 'ar' ? 'مرجع المادة ٧' : 'PDPL Art.7', width: '160px',
      cell: f => f.pdplArt7TransferRef
        ? <span style={{ fontSize: 11, fontFamily: 'monospace' }}>{f.pdplArt7TransferRef}</span>
        : (f.destinationCountry && f.destinationCountry !== 'SA'
          ? <Badge tone="danger">{lang === 'ar' ? 'مفقود' : 'Missing'}</Badge>
          : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>) },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: f => <Badge tone={FLOW_STATUS_TONE[f.status]}>
        {FLOW_STATUS_LABEL[f.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'سجل تدفقات البيانات' : 'Data Flows Register'}
        sub={lang === 'ar'
          ? 'تدفقات النظم المتعدّدة — بوّابة المادة ٧ على التدفقات السرّية+ عبر الحدود (AR-285)'
          : 'Cross-system flows — PDPL Art.7 gate on CONFIDENTIAL+ cross-border flows (AR-285)'}
      />

      {error && (
        <section style={{ padding: 12, marginBottom: 16, borderInlineStart: '4px solid var(--color-danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <strong>{lang === 'ar' ? 'خطأ' : 'Error'}:</strong> {error}
        </section>
      )}

      <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <DataTable columns={cols} rows={rows} empty={<InlineEmpty />} />
      </section>
    </Shell>
  );
}
