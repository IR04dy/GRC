import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, ArrowLeft } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listSamaPending,
  type OutsourcingArrangement,
  SERVICE_TYPE_LABELS,
  LIFECYCLE_LABELS,
  lifecycleTone,
  fmtSar,
} from '../../lib/outsourcing';

/**
 * Sprint 13 — Material outsourcing arrangements awaiting SAMA
 * non-objection (المادة §4.2).
 *
 * <p>Compliance Officer's "what's blocking go-live" view. Every row
 * here represents an arrangement that has been logged as material +
 * SAMA non-objection required, but the bank has not yet recorded the
 * non-objection reference. Until that's recorded, the arrangement
 * cannot transition to APPROVED.
 */
export function OutsourcingSamaPendingPage() {
  const navigate = useNavigate();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [items, setItems] = useState<OutsourcingArrangement[]>([]);
  const [loading, setLoading] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      setItems(await listSamaPending());
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل القائمة' : 'Failed to load list'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'إدارة مخاطر الأطراف الخارجية / سجل ترتيبات الإسناد' : 'TPRM / Outsourcing Register'}
        title={isAr ? 'بانتظار عدم ممانعة ساما' : 'Awaiting SAMA non-objection'}
        sub={isAr
          ? 'إسناد جوهري لا يمكن اعتماده دون الحصول على عدم ممانعة من مؤسسة النقد وفق §4.2'
          : 'Material outsourcing arrangements that cannot be approved until SAMA non-objection is received (§4.2)'}
        actions={
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/tprm/outsourcing')}>
            {isAr ? 'الرجوع للسجل' : 'Back to register'}
          </Button>
        }
      />

      <Card>
        {loading ? (
          <div className="p-6 text-center text-sm text-muted-foreground">
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        ) : items.length === 0 ? (
          <InlineEmpty
            text={isAr
              ? 'لا توجد ترتيبات إسناد جوهرية بانتظار عدم ممانعة ساما — كل شيء على ما يرام'
              : 'No material arrangements pending SAMA non-objection — all clear'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b bg-muted/30 text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 text-start">{isAr ? 'الرمز' : 'Code'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'العنوان' : 'Title'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'النوع' : 'Service type'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'دورة الحياة' : 'Lifecycle'}</th>
                  <th className="px-3 py-2 text-start">{isAr ? 'بداية العقد' : 'Contract start'}</th>
                  <th className="px-3 py-2 text-end">{isAr ? 'القيمة السنوية' : 'Annual SAR'}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(a => (
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
                      {isAr ? SERVICE_TYPE_LABELS[a.serviceType].ar : SERVICE_TYPE_LABELS[a.serviceType].en}
                    </td>
                    <td className="px-3 py-2">
                      <Badge tone={lifecycleTone(a.lifecycleStage)}>
                        {isAr ? LIFECYCLE_LABELS[a.lifecycleStage].ar : LIFECYCLE_LABELS[a.lifecycleStage].en}
                      </Badge>
                    </td>
                    <td className="px-3 py-2 text-xs">{a.contractStartDate ?? '—'}</td>
                    <td className="px-3 py-2 text-end font-mono text-xs">{fmtSar(a.contractValueSarAnnual)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <AlertTriangle size={12} />
        <span>
          {isAr
            ? 'ساما — قاعدة §4.2: لا يجوز تفعيل أي إسناد جوهري قبل الحصول على عدم ممانعة كتابية من مؤسسة النقد.'
            : 'SAMA §4.2: a material outsourcing arrangement may not go live before written non-objection is obtained.'}
        </span>
      </div>
    </Shell>
  );
}
