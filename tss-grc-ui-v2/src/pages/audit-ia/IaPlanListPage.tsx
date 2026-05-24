import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CalendarRange, ClipboardList, Plus, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  iaApi, planStatusLabel,
  type IaPlan, type PlanStatus,
} from '../../lib/iaPlan';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const STATUS_TONE: Record<PlanStatus, BadgeTone> = {
  DRAFT:        'neutral',
  BOARD_REVIEW: 'info',
  APPROVED:     'success',
  EXECUTING:    'warning',
  COMPLETED:    'success',
  ARCHIVED:     'neutral',
};

/**
 * Sprint 13 — Internal Audit Annual Plan list.
 *
 * <p>Lists every annual plan for the tenant, with a quick-create
 * button for the Chief Audit Executive. The detail link routes to
 * {@code /audit/ia/plans/:id} which renders the KPI tiles + engagement list.
 *
 * <p>Saudi-native Arabic labels used here:
 *   "خطة المراجعة الداخلية السنوية" — Annual Plan
 *   "الجَردة الشاملة للعمليات"   — Audit Universe
 *   "رئيس وحدة المراجعة الداخلية" — Chief Audit Executive
 */
export function IaPlanListPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();

  const [state, setState] = useState<LoadState>('idle');
  const [plans, setPlans] = useState<IaPlan[]>([]);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      setPlans(await iaApi.listPlans());
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); }, []);

  return (
    <Shell
      title={isAr ? 'خطة المراجعة الداخلية السنوية' : 'Internal Audit Annual Plans'}
      sub={isAr
        ? 'خطط سنوية قائمة على المخاطر، وفق إطار ساما للمراجعة الداخلية (٢٠١٨) ومعيار IIA 2010'
        : 'Risk-based annual plans aligned with SAMA Internal Audit framework (2018) and IIA 2010'}
    >
      <PageHeader
        eyebrow={isAr ? 'المراجعة الداخلية' : 'Internal Audit'}
        title={isAr ? 'خطة المراجعة الداخلية السنوية' : 'Annual Audit Plans'}
        sub={isAr
          ? 'يضع رئيس وحدة المراجعة الداخلية الخطة السنوية ويعرضها على لجنة المراجعة للاعتماد قبل تنفيذها'
          : 'The Chief Audit Executive prepares the annual plan, submits it to the Audit Committee for approval, then executes it'}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/audit/ia/universe">
              <Button variant="ghost" size="sm">
                <ClipboardList size={14} /> {isAr ? 'الجَردة الشاملة' : 'Audit universe'}
              </Button>
            </Link>
          </>
        }
      />

      {state === 'loading' && (
        <Card>
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        </Card>
      )}

      {state === 'error' && (
        <Card>
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>
            {err ?? (isAr ? 'تعذّر تحميل البيانات' : 'Could not load data')}
          </div>
        </Card>
      )}

      {state === 'ready' && plans.length === 0 && (
        <Card>
          <div style={{ padding: 48, textAlign: 'center' }}>
            <CalendarRange size={32} style={{ opacity: 0.4, marginBottom: 12 }} />
            <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 16 }}>
              {isAr ? 'لا توجد خطط مراجعة داخلية بعد' : 'No annual plans yet'}
            </div>
            <Button variant="primary" size="sm">
              <Plus size={14} /> {isAr ? 'إنشاء خطة' : 'Create plan'}
            </Button>
          </div>
        </Card>
      )}

      {state === 'ready' && plans.length > 0 && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
          gap: 12,
        }}>
          {plans.map(p => (
            <button
              key={p.id}
              type="button"
              onClick={() => navigate(`/audit/ia/plans/${p.id}`)}
              style={{
                textAlign: 'start',
                padding: 16,
                borderRadius: 'var(--r-md)',
                border: '1px solid var(--border)',
                background: 'var(--surface-1)',
                cursor: 'pointer',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 14 }}>{p.planCode}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {isAr ? 'دورة' : 'Cycle'} {p.cycleYear}
                  </div>
                </div>
                <Badge tone={STATUS_TONE[p.status]}>{planStatusLabel(p.status, isAr)}</Badge>
              </div>
              <div style={{ fontSize: 12, marginBottom: 8 }}>
                {isAr ? p.titleAr : p.titleEn}
              </div>
              <div style={{ display: 'flex', gap: 16, fontSize: 11, color: 'var(--ink-3)' }}>
                <span>{isAr ? 'الجَردة:' : 'Universe:'} <strong>{p.auditUniverseCount}</strong></span>
                <span>{isAr ? 'مهام مُجدوَلة:' : 'Engagements:'} <strong>{p.totalEngagementsPlanned}</strong></span>
              </div>
            </button>
          ))}
        </div>
      )}
    </Shell>
  );
}
