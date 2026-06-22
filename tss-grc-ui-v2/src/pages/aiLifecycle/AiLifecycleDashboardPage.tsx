import { useEffect, useState } from 'react';
import { Bot, ShieldCheck, ShieldAlert, RefreshCw, Clock, Activity } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  aiLifecycleApi,
  VALIDATION_STATUS_LABEL, VALIDATION_STATUS_TONE,
  RETRAINING_STATUS_LABEL, RETRAINING_STATUS_TONE,
  RECERT_STATUS_LABEL, RECERT_STATUS_TONE,
  type DashboardResponse, type ValidationResponse,
  type RetrainingResponse, type RecertificationResponse,
} from '../../lib/aiLifecycle';

/**
 * Sprint 31 Frontend — AI/ML Model Lifecycle dashboard.
 * SDAIA AI Ethics + PDPL Art.4 + EU AI Act risk tiers.
 * Workspace for Model Validator + DPO + CRO + Sharia Officer (where applicable).
 */
export function AiLifecycleDashboardPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setError(null);
      try {
        const r = await aiLifecycleApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة دورة حياة النماذج' : 'Failed to load AI Lifecycle dashboard'));
        }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const valCols: Column<ValidationResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: v => <span style={{ fontSize: 12 }}>{v.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'المُدقِّق' : 'Validator',
      cell: v => <span style={{ fontSize: 13 }}>{v.validatorOrgLabel ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'تفاوت العدالة' : 'Fairness disparity', width: '140px',
      cell: v => v.fairnessDisparityPct != null
        ? (
          <Badge tone={v.fairnessDisparityPct > 10 ? 'danger' : v.fairnessDisparityPct > 5 ? 'warning' : 'success'}>
            {v.fairnessDisparityPct.toFixed(1)}%
          </Badge>
        )
        : <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '180px',
      cell: v => (
        <Badge tone={VALIDATION_STATUS_TONE[v.status]}>
          {VALIDATION_STATUS_LABEL[v.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  const retCols: Column<RetrainingResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '160px', mono: true,
      cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الإصدار' : 'Version', width: '140px',
      cell: r => (
        <span style={{ fontSize: 12 }}>
          {r.fromVersionLabel ?? '—'} → <strong>{r.toVersionLabel ?? '—'}</strong>
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: r => (
        <Badge tone={RETRAINING_STATUS_TONE[r.status]}>
          {RETRAINING_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  const recertCols: Column<RecertificationResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الدورة' : 'Cycle',
      cell: c => <span style={{ fontSize: 13 }}>{c.cycleLabel ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'الاستحقاق' : 'Due', width: '130px',
      cell: c => <span style={{ fontSize: 12 }}>{c.dueDate ?? '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '200px',
      cell: c => (
        <Badge tone={RECERT_STATUS_TONE[c.status]}>
          {RECERT_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell>
      <PageHeader
        title={lang === 'ar' ? 'دورة حياة نماذج الذكاء الاصطناعي' : 'AI/ML Model Lifecycle'}
        sub={lang === 'ar'
          ? 'مبادئ أخلاقيات الذكاء الاصطناعي SDAIA + المادة ٤ من نظام حماية البيانات + قانون الاتحاد الأوروبي للذكاء الاصطناعي'
          : 'SDAIA AI Ethics + PDPL Art.4 + EU AI Act — validation, retraining, recertification'}
      />

      {error && (
        <section style={{ padding: 12, marginBottom: 16, borderInlineStart: '4px solid var(--danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <strong>{lang === 'ar' ? 'خطأ' : 'Error'}:</strong> {error}
        </section>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginBottom: 16 }}>
        <Kpi label={lang === 'ar' ? 'تحقق مجدول' : 'Validations scheduled'} value={data?.validationsScheduled ?? 0} Icon={Clock} to="/ai-lifecycle/validations" />
        <Kpi label={lang === 'ar' ? 'تحقق قيد التنفيذ' : 'Validations in progress'} value={data?.validationsInProgress ?? 0} tone="info" Icon={Activity} to="/ai-lifecycle/validations" />
        <Kpi label={lang === 'ar' ? 'تحقق فاشل/مشروط' : 'Failed / conditional'} value={data?.validationsFailedOrConditions ?? 0} tone="warning" Icon={ShieldAlert} to="/ai-lifecycle/validations" />
        <Kpi label={lang === 'ar' ? 'إعادة تدريب قيد التنفيذ' : 'Retrainings in progress'} value={data?.retrainingsInProgress ?? 0} tone="info" Icon={RefreshCw} to="/ai-lifecycle/retrainings" />
        <Kpi label={lang === 'ar' ? 'منشورة هذا الربع' : 'Deployed this Q'} value={data?.retrainingsDeployedThisQuarter ?? 0} tone="success" Icon={ShieldCheck} to="/ai-lifecycle/retrainings" />
        <Kpi label={lang === 'ar' ? 'تراجعات' : 'Rollbacks'} value={data?.retrainingsRolledBack ?? 0} tone="danger" Icon={ShieldAlert} to="/ai-lifecycle/retrainings" />
        <Kpi label={lang === 'ar' ? 'إعادة اعتماد متأخرة' : 'Recerts overdue'} value={data?.recertificationsOverdue ?? 0} tone={data && data.recertificationsOverdue > 0 ? 'danger' : 'neutral'} Icon={ShieldAlert} to="/ai-lifecycle/recertifications" />
        <Kpi label={lang === 'ar' ? 'إعادة اعتماد ٩٠ يوماً' : 'Recerts due 90d'} value={data?.recertificationsDueNext90d ?? 0} tone="warning" Icon={Bot} to="/ai-lifecycle/recertifications" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 12, marginBottom: 16 }}>
        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'التحقق حسب الحالة' : 'Validations by status'}
          </h3>
          {data?.validationsByStatus && Object.entries(data.validationsByStatus).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.validationsByStatus).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={VALIDATION_STATUS_TONE[k as ValidationResponse['status']] ?? 'neutral'}>
                    {VALIDATION_STATUS_LABEL[k as ValidationResponse['status']]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>

        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'إعادة التدريب حسب الحالة' : 'Retrainings by status'}
          </h3>
          {data?.retrainingsByStatus && Object.entries(data.retrainingsByStatus).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.retrainingsByStatus).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={RETRAINING_STATUS_TONE[k as RetrainingResponse['status']] ?? 'neutral'}>
                    {RETRAINING_STATUS_LABEL[k as RetrainingResponse['status']]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>

        <section style={{ padding: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ marginTop: 0, fontSize: 14 }}>
            {lang === 'ar' ? 'إعادة الاعتماد حسب الحالة' : 'Recertifications by status'}
          </h3>
          {data?.recertificationsByStatus && Object.entries(data.recertificationsByStatus).length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Object.entries(data.recertificationsByStatus).map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                  <Badge tone={RECERT_STATUS_TONE[k as RecertificationResponse['status']] ?? 'neutral'}>
                    {RECERT_STATUS_LABEL[k as RecertificationResponse['status']]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}
                  </Badge>
                  <strong>{v}</strong>
                </div>
              ))}
            </div>
          ) : <InlineEmpty />}
        </section>
      </div>

      <section style={{ padding: 16, marginBottom: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>
          {lang === 'ar' ? 'أحدث عمليات التحقق' : 'Recent validations'}
        </h3>
        <DataTable
          columns={valCols}
          rows={data?.recentValidations ?? []}
          empty={<InlineEmpty />}
        />
      </section>

      <section style={{ padding: 16, marginBottom: 16, borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>
          {lang === 'ar' ? 'أحدث دورات إعادة التدريب' : 'Recent retrainings'}
        </h3>
        <DataTable
          columns={retCols}
          rows={data?.recentRetrainings ?? []}
          empty={<InlineEmpty />}
        />
      </section>

      {data?.overdueRecertifications && data.overdueRecertifications.length > 0 && (
        <section style={{ padding: 16, borderInlineStart: '4px solid var(--danger)', borderRadius: 12, background: 'var(--color-surface, #fff)', border: '1px solid rgba(0,0,0,0.08)' }}>
          <h3 style={{ margin: '0 0 12px 0', fontSize: 14 }}>
            {lang === 'ar' ? 'إعادة اعتماد متأخرة (AR-278)' : 'Overdue recertifications (AR-278)'}
          </h3>
          <DataTable columns={recertCols} rows={data.overdueRecertifications} />
        </section>
      )}
    </Shell>
  );
}
