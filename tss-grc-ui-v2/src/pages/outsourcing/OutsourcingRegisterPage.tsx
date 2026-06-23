import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Briefcase, Filter, AlertTriangle, CalendarClock, Building2, ShieldCheck } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listArrangements,
  getDashboard,
  type OutsourcingArrangement,
  type Dashboard,
  type LifecycleStage,
  type CriticalFunction,
  LIFECYCLE_LABELS,
  LIFECYCLE_STAGES,
  SERVICE_TYPE_LABELS,
  CRITICAL_FUNCTION_LABELS,
  lifecycleTone,
  fmtSar,
} from '../../lib/outsourcing';

/**
 * Sprint 13 — Outsourcing arrangement register (سجل ترتيبات الإسناد).
 *
 * <p>Compliance Officer + TPRM Lead landing page. Shows every
 * outsourcing arrangement (material + non-material) with materiality
 * badge, lifecycle stage, SAMA non-objection status, and quick filter
 * by stage / materiality / critical-function.
 */
export function OutsourcingRegisterPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [items, setItems] = useState<OutsourcingArrangement[]>([]);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [materialFilter, setMaterialFilter] = useState<'all' | 'material' | 'nonMaterial'>('all');
  const [stageFilter, setStageFilter] = useState<LifecycleStage | ''>('');
  const [criticalFnFilter, setCriticalFnFilter] = useState<CriticalFunction | ''>('');

  async function reload() {
    setLoading(true);
    try {
      const [list, dash] = await Promise.all([
        listArrangements(),
        getDashboard().catch(() => null),
      ]);
      setItems(Array.isArray(list) ? list : []);
      setDashboard(dash);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل سجل ترتيبات الإسناد' : 'Failed to load outsourcing register'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    return items.filter(it => {
      if (materialFilter === 'material' && !it.isMaterial) return false;
      if (materialFilter === 'nonMaterial' && it.isMaterial) return false;
      if (stageFilter && it.lifecycleStage !== stageFilter) return false;
      if (criticalFnFilter && it.criticalFunction !== criticalFnFilter) return false;
      return true;
    });
  }, [items, materialFilter, stageFilter, criticalFnFilter]);

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'إدارة مخاطر الأطراف الخارجية' : 'Third-Party Risk Management'}
        title={isAr ? 'سجل ترتيبات الإسناد' : 'Outsourcing Arrangement Register'}
        sub={isAr
          ? 'قواعد الإسناد لمؤسسة النقد (2019/2023) — جميع ترتيبات الإسناد جوهرية وغير جوهرية'
          : 'SAMA Rules Governing Outsourcing (2019/2023) — all material + non-material arrangements'}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="secondary" onClick={() => navigate('/tprm/outsourcing/admin/sama-pending')}>
              {isAr ? 'بانتظار ساما' : 'SAMA pending'}
            </Button>
          </div>
        }
      />

      {/* KPI tiles */}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 mb-4">
        <Kpi
          Icon={Briefcase}
          label={isAr ? 'إجمالي ترتيبات الإسناد' : 'Total arrangements'}
          value={dashboard?.totalArrangements ?? items.length}
          onClick={() => { setMaterialFilter('all'); setStageFilter(''); setCriticalFnFilter(''); }}
          active={materialFilter === 'all' && !stageFilter && !criticalFnFilter}
        />
        <Kpi
          Icon={ShieldCheck}
          label={isAr ? 'إسناد جوهري نشط' : 'Active material'}
          value={dashboard?.activeMaterial ?? 0}
          tone="info"
          onClick={() => setMaterialFilter('material')}
          active={materialFilter === 'material'}
        />
        <Kpi
          Icon={AlertTriangle}
          label={isAr ? 'بانتظار عدم ممانعة ساما' : 'Awaiting SAMA'}
          value={dashboard?.awaitingSama ?? 0}
          tone={(dashboard?.awaitingSama ?? 0) > 0 ? 'warning' : 'neutral'}
          to="/tprm/outsourcing/admin/sama-pending"
        />
        <Kpi
          Icon={CalendarClock}
          label={isAr ? 'مراجعات متأخرة' : 'Overdue reviews'}
          value={dashboard?.overdueReviews ?? 0}
          tone={(dashboard?.overdueReviews ?? 0) > 0 ? 'warning' : 'neutral'}
          to="/tprm/outsourcing"
        />
      </div>

      {/* Filters */}
      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex items-center gap-1 text-sm text-muted-foreground">
            <Filter size={14} />
            <span>{isAr ? 'تصفية:' : 'Filter:'}</span>
          </div>

          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            <span>{isAr ? 'الجوهرية' : 'Materiality'}</span>
            <select
              className="rounded-md border border-input bg-background px-3 py-1.5 text-sm"
              value={materialFilter}
              onChange={e => setMaterialFilter(e.target.value as 'all' | 'material' | 'nonMaterial')}
            >
              <option value="all">{isAr ? 'الكل' : 'All'}</option>
              <option value="material">{isAr ? 'إسناد جوهري' : 'Material'}</option>
              <option value="nonMaterial">{isAr ? 'غير جوهري' : 'Non-material'}</option>
            </select>
          </label>

          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            <span>{isAr ? 'دورة الحياة' : 'Lifecycle'}</span>
            <select
              className="rounded-md border border-input bg-background px-3 py-1.5 text-sm"
              value={stageFilter}
              onChange={e => setStageFilter(e.target.value as LifecycleStage | '')}
            >
              <option value="">{isAr ? 'الكل' : 'All'}</option>
              {LIFECYCLE_STAGES.map(s => (
                <option key={s} value={s}>{tlabel(LIFECYCLE_LABELS, s, isAr)}</option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-1 text-xs text-muted-foreground">
            <span>{isAr ? 'الوظيفة الحرجة' : 'Critical function'}</span>
            <select
              className="rounded-md border border-input bg-background px-3 py-1.5 text-sm"
              value={criticalFnFilter}
              onChange={e => setCriticalFnFilter(e.target.value as CriticalFunction | '')}
            >
              <option value="">{isAr ? 'الكل' : 'All'}</option>
              {Object.entries(CRITICAL_FUNCTION_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{isAr ? v.ar : v.en}</option>
              ))}
            </select>
          </label>

          {(materialFilter !== 'all' || stageFilter || criticalFnFilter) && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setMaterialFilter('all');
                setStageFilter('');
                setCriticalFnFilter('');
              }}
            >
              {isAr ? 'مسح التصفية' : 'Clear filters'}
            </Button>
          )}
        </div>
      </Card>

      {/* Table */}
      <Card>
        {loading ? (
          <div className="p-6 text-center text-sm text-muted-foreground">
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        ) : filtered.length === 0 ? (
          <InlineEmpty
            text={isAr
              ? 'لا توجد ترتيبات إسناد ضمن التصفية الحالية'
              : 'No outsourcing arrangements registered under the current filter'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/30 text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 text-start">{isAr ? 'الرمز' : 'Code'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'العنوان' : 'Title'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'النوع' : 'Service type'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'الجوهرية' : 'Material'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'دورة الحياة' : 'Lifecycle'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'عدم ممانعة ساما' : 'SAMA non-objection'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'الوظيفة الحرجة' : 'Critical function'}</th>
                  <th className="px-3 py-2 text-end">{isAr ? 'القيمة السنوية' : 'Annual SAR'}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(a => (
                  <tr
                    key={a.id}
                    className="cursor-pointer border-b hover:bg-muted/20"
                    onClick={() => navigate(`/tprm/outsourcing/${a.id}`)}
                  >
                    <td className="px-3 py-2 font-mono text-xs">{a.arrangementCode}</td>
                    <td className="px-3 py-2">
                      <div className="font-medium">{isAr ? a.titleAr : a.titleEn}</div>
                      <div className="text-xs text-muted-foreground" dir={isAr ? 'ltr' : 'rtl'}>
                        {isAr ? a.titleEn : a.titleAr}
                      </div>
                    </td>
                    <td className="px-3 py-2 text-xs">
                      {tlabel(SERVICE_TYPE_LABELS, a.serviceType, isAr)}
                    </td>
                    <td className="px-3 py-2">
                      {a.isMaterial
                        ? <Badge tone="warning">{isAr ? 'جوهري' : 'Material'}</Badge>
                        : <Badge tone="neutral">{isAr ? 'غير جوهري' : 'Non-material'}</Badge>}
                    </td>
                    <td className="px-3 py-2">
                      <Badge tone={lifecycleTone(a.lifecycleStage)}>
                        {tlabel(LIFECYCLE_LABELS, a.lifecycleStage, isAr)}
                      </Badge>
                    </td>
                    <td className="px-3 py-2 text-xs">
                      {a.samaNonObjectionRequired
                        ? (a.samaNonObjectionObtained
                          ? <Badge tone="success">{a.samaNonObjectionRef ?? (isAr ? 'مُحصَّل' : 'Obtained')}</Badge>
                          : <Badge tone="danger">{isAr ? 'مطلوب' : 'Required'}</Badge>)
                        : <span className="text-muted-foreground">{isAr ? 'غير مطلوب' : 'Not required'}</span>}
                    </td>
                    <td className="px-3 py-2 text-xs">
                      {a.criticalFunction === 'NA'
                        ? <span className="text-muted-foreground">—</span>
                        : (tlabel(CRITICAL_FUNCTION_LABELS, a.criticalFunction, isAr))}
                    </td>
                    <td className="px-3 py-2 text-end font-mono text-xs">
                      {fmtSar(a.contractValueSarAnnual)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <Building2 size={12} />
        <span>
          {isAr
            ? `يُعرَض ${filtered.length} من ${items.length} ترتيباً.`
            : `Showing ${filtered.length} of ${items.length} arrangements.`}
        </span>
      </div>
    </Shell>
  );
}
