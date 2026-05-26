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
  aiLifecycleApi,
  RECERT_STATUS_LABEL, RECERT_STATUS_TONE,
  type RecertificationResponse,
} from '../../lib/aiLifecycle';

/** Recertifications register — Sprint 31 frontend. */
export function AiLifecycleRecertificationsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();
  const [rows, setRows] = useState<RecertificationResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await aiLifecycleApi.listRecerts();
        if (!cancelled) setRows(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل سجل إعادة الاعتماد' : 'Failed to load recertifications'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const today = new Date().toISOString().slice(0, 10);

  const cols: Column<RecertificationResponse>[] = [
    { header: lang === 'ar' ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span> },
    { header: lang === 'ar' ? 'الدورة' : 'Cycle',
      cell: c => <span style={{ fontSize: 13 }}>{c.cycleLabel ?? '—'}</span> },
    { header: lang === 'ar' ? 'الاستحقاق' : 'Due', width: '130px',
      cell: c => {
        if (!c.dueDate) return <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>;
        const overdue = c.status === 'DUE' && c.dueDate < today;
        return overdue
          ? <Badge tone="danger">{c.dueDate}</Badge>
          : <span style={{ fontSize: 12 }}>{c.dueDate}</span>;
      } },
    { header: lang === 'ar' ? 'الدورة التالية' : 'Next due', width: '130px',
      cell: c => <span style={{ fontSize: 12 }}>{c.nextDueDate ?? '—'}</span> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '220px',
      cell: c => <Badge tone={RECERT_STATUS_TONE[c.status]}>
        {RECERT_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'إعادة اعتماد النماذج' : 'Model Recertifications'}
        sub={lang === 'ar'
          ? 'إعادة الاعتماد الدوري — سنوي للنماذج الحرجة والعالية وفق مبادئ SDAIA. سلّم تذكير T+0/T+15/T+30 على المتأخرة (AR-278)'
          : 'Periodic recertification — annual for CRITICAL/HIGH-tier per SDAIA. T+0/T+15/T+30 ladder on overdue (AR-278)'}
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
