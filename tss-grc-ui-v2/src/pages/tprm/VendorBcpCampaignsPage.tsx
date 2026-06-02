import { useEffect, useState } from 'react';
import {
  ClipboardCheck, Plus, Play, Send, AlertTriangle, CheckCircle2,
  Mail, FileText, Briefcase,
} from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
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
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  listCampaigns,
  createCampaign,
  openCampaign,
  sendReminders,
  escalateCampaign,
  closeCampaign,
  type VendorBcpCampaign,
  type Threshold,
  CAMPAIGN_STATUS_LABELS,
  THRESHOLD_LABELS,
  campaignStatusTone,
  campaignActions,
} from '../../lib/vendorBcp';

/**
 * Slice 10-D — Vendor BCP attestation campaigns register.
 *
 * <p>TPRM Lead's landing page. Shows every campaign with status,
 * response-rate progress bar, and quick-action buttons (Open / Remind
 * R1 / Remind R2 / Escalate / Close) gated by current state.
 *
 * <p>Saudi-native bilingual: "حملة شهادات استمرارية الموردين" rather
 * than the literal "حملة BCP للموردين". Title chosen to read naturally
 * to a Saudi-bank TPRM Lead.
 */
export function VendorBcpCampaignsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();
  const navigate = useNavigate();

  const [items, setItems] = useState<VendorBcpCampaign[]>([]);
  const [loading, setLoading] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      const list = await listCampaigns();
      setItems(Array.isArray(list) ? list : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل الحملات' : 'Failed to load campaigns'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Actions ──────────────────────────────────────────────────────

  async function onCreate() {
    const titleEn = await dialog.prompt({
      title: isAr ? 'عنوان الحملة (إنجليزي)' : 'Campaign title (English)',
      placeholder: 'e.g. Annual critical-third-party BCP attestation — 2026',
      required: true,
    });
    if (titleEn === null) return;
    const titleAr = await dialog.prompt({
      title: 'عنوان الحملة (عربي)',
      placeholder: 'مثال: الشهادة السنوية لاستمرارية الأعمال للموردين الجوهريين — 2026',
      required: true,
    });
    if (titleAr === null) return;
    const yearStr = await dialog.prompt({
      title: isAr ? 'سنة الدورة' : 'Cycle year',
      type: 'number',
      initial: String(new Date().getFullYear()),
      required: true,
    });
    if (yearStr === null) return;
    const threshold = await dialog.prompt({
      title: isAr ? 'نطاق الحملة (ALL / CRITICAL_ONLY / HIGH_AND_ABOVE)'
        : 'Scope (ALL / CRITICAL_ONLY / HIGH_AND_ABOVE)',
      initial: 'CRITICAL_ONLY',
      required: true,
      validate: v => ['ALL', 'CRITICAL_ONLY', 'HIGH_AND_ABOVE'].includes(v.trim())
        ? null
        : (isAr ? 'قيمة غير مقبولة' : 'Must be ALL, CRITICAL_ONLY, or HIGH_AND_ABOVE'),
    });
    if (threshold === null) return;
    try {
      await createCampaign({
        titleEn,
        titleAr,
        cycleYear: Number(yearStr),
        mandatoryThreshold: threshold.trim() as Threshold,
        regulatorReference: 'SAMA BCF §3.4; NCA ECC-2 4-2-3',
      });
      toast.success(isAr ? 'تم إنشاء الحملة' : 'Campaign created');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onOpen(c: VendorBcpCampaign) {
    const ok = await dialog.confirm({
      title: isAr ? 'فتح الحملة وإرسال الدعوات؟' : 'Open campaign and send invitations?',
      body: isAr
        ? `سيتم إنشاء سجلات دعوة لكل مورّد ضمن نطاق "${tlabel(THRESHOLD_LABELS, c.mandatoryThreshold, true)}".`
        : `Invitations will be materialised for every vendor in scope "${tlabel(THRESHOLD_LABELS, c.mandatoryThreshold, false)}".`,
    });
    if (!ok) return;
    try {
      await openCampaign(c.id);
      toast.success(isAr ? 'الحملة مفتوحة' : 'Campaign opened');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onRemind(c: VendorBcpCampaign, round: 1 | 2) {
    const ok = await dialog.confirm({
      title: isAr ? `إرسال التذكير — الجولة ${round}؟` : `Send reminders — round ${round}?`,
      body: isAr
        ? 'سيتم إرسال بريد تذكير لكل مورّد لم يستجب حتى الآن ضمن نافذة فترة التذكير.'
        : 'A reminder email will go to every vendor still outstanding past the cadence window.',
    });
    if (!ok) return;
    try {
      await sendReminders(c.id, round);
      toast.success(isAr ? 'تم إرسال التذكير' : 'Reminders sent');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onEscalate(c: VendorBcpCampaign) {
    const ok = await dialog.confirm({
      title: isAr ? 'تصعيد المتأخرات إلى لجنة المراجعة؟' : 'Escalate overdue to Audit Committee?',
      body: isAr
        ? 'هذا الإجراء يُسجَّل في سجل التدقيق ويُرسل إشعاراً للجنة المراجعة.'
        : 'This action is logged in the audit trail and notifies the Audit Committee.',
      destructive: true,
    });
    if (!ok) return;
    try {
      await escalateCampaign(c.id);
      toast.success(isAr ? 'تم التصعيد' : 'Escalated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onClose(c: VendorBcpCampaign) {
    const summaryEn = await dialog.prompt({
      title: 'Closing summary (English)',
      type: 'textarea',
      placeholder: 'Regulator-facing summary — response rate, key findings, follow-ups.',
    });
    if (summaryEn === null) return;
    const summaryAr = await dialog.prompt({
      title: 'ملخّص الإغلاق (عربي)',
      type: 'textarea',
      placeholder: 'ملخص يصلح لتقديمه للجهة التنظيمية — نسبة الاستجابة، النتائج الرئيسية، إجراءات المتابعة.',
    });
    if (summaryAr === null) return;
    try {
      await closeCampaign(c.id, {
        closingSummaryEn: summaryEn,
        closingSummaryAr: summaryAr,
      });
      toast.success(isAr ? 'تم إغلاق الحملة' : 'Campaign closed');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ─── KPIs ─────────────────────────────────────────────────────────
  const activeCount = items.filter(c =>
    !['CLOSED', 'CANCELLED', 'DRAFT'].includes(c.status)).length;
  const overdueCount = items.reduce((s, c) => s + (c.totalOverdue ?? 0), 0);
  const closedCount = items.filter(c => c.status === 'CLOSED').length;

  // ─── Render ───────────────────────────────────────────────────────
  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'إدارة مخاطر الأطراف الخارجية' : 'Third-Party Risk Management'}
        title={isAr
          ? 'حملة شهادات استمرارية الموردين'
          : 'Vendor BCP Attestation Campaigns'}
        sub={isAr
          ? 'إطلاق دورة سنوية أو ربعية لجمع شهادات استمرارية الأعمال من الموردين الجوهريين، وفق إطار استمرارية الأعمال لمؤسسة النقد وضوابط الهيئة الوطنية للأمن السيبراني.'
          : 'Launch an annual or quarterly cycle to collect BCP attestations from critical third parties, per SAMA BCF and NCA ECC-2 Annex H.'}
        actions={
          <Button Icon={Plus} onClick={onCreate}>
            {isAr ? 'حملة جديدة' : 'New campaign'}
          </Button>
        }
      />

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--sp-6)' }}>
        <Kpi
          Icon={ClipboardCheck}
          label={isAr ? 'إجمالي الحملات' : 'Total campaigns'}
          value={String(items.length)}
        />
        <Kpi
          Icon={Send}
          label={isAr ? 'حملات نشطة' : 'Active campaigns'}
          value={String(activeCount)}
          tone="info"
        />
        <Kpi
          Icon={AlertTriangle}
          label={isAr ? 'متأخرات مفتوحة' : 'Open overdue'}
          value={String(overdueCount)}
          tone={overdueCount > 0 ? 'danger' : 'neutral'}
        />
        <Kpi
          Icon={CheckCircle2}
          label={isAr ? 'حملات مُغلَقة' : 'Closed cycles'}
          value={String(closedCount)}
          tone="success"
        />
      </div>

      <Card title={isAr ? 'سجل الحملات' : 'Campaign register'} Icon={Briefcase}>
        {loading && items.length === 0 ? (
          <InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
        ) : items.length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد حملات. أنشئ الأولى للبدء.' : 'No campaigns yet. Create one to start.'} />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{isAr ? 'رقم الحملة' : 'Code'}</th>
                  <th>{isAr ? 'العنوان' : 'Title'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th>{isAr ? 'النطاق' : 'Scope'}</th>
                  <th>{isAr ? 'نسبة الاستجابة' : 'Response rate'}</th>
                  <th>{isAr ? 'تاريخ الإغلاق' : 'Closes'}</th>
                  <th style={{ width: 280 }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(c => {
                  const actions = campaignActions(c.status);
                  const responseRate = c.totalInvited > 0
                    ? Math.round((c.totalSubmitted * 100) / c.totalInvited)
                    : 0;
                  const closes = c.closesAt
                    ? new Date(c.closesAt).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB')
                    : '—';
                  return (
                    <tr key={c.id}>
                      <td><code style={{ fontSize: 12 }}>{c.campaignCode}</code></td>
                      <td>
                        <Link to={`/tprm/bcp-campaigns/${c.id}`} className="link">
                          {isAr ? c.titleAr : c.titleEn}
                        </Link>
                      </td>
                      <td>
                        <Badge tone={campaignStatusTone(c.status)}>
                          {tlabel(CAMPAIGN_STATUS_LABELS, c.status, isAr)}
                        </Badge>
                      </td>
                      <td style={{ fontSize: 13 }}>
                        {tlabel(THRESHOLD_LABELS, c.mandatoryThreshold, isAr)}
                      </td>
                      <td>
                        <ProgressBar percent={responseRate}
                          label={`${c.totalSubmitted}/${c.totalInvited}`} />
                      </td>
                      <td style={{ fontSize: 13 }}>{closes}</td>
                      <td>
                        <div className="cluster" style={{ gap: 6 }}>
                          <Button size="sm" variant="secondary" Icon={FileText}
                            onClick={() => navigate(`/tprm/bcp-campaigns/${c.id}`)}>
                            {isAr ? 'فتح' : 'Open'}
                          </Button>
                          {actions.canOpen && (
                            <Button size="sm" Icon={Play} onClick={() => onOpen(c)}>
                              {isAr ? 'بدء' : 'Launch'}
                            </Button>
                          )}
                          {actions.canSendR1 && (
                            <Button size="sm" variant="secondary" Icon={Mail}
                              onClick={() => onRemind(c, 1)}>
                              {isAr ? 'تذكير 1' : 'Remind 1'}
                            </Button>
                          )}
                          {actions.canSendR2 && (
                            <Button size="sm" variant="secondary" Icon={Mail}
                              onClick={() => onRemind(c, 2)}>
                              {isAr ? 'تذكير 2' : 'Remind 2'}
                            </Button>
                          )}
                          {actions.canEscalate && (
                            <Button size="sm" variant="danger" Icon={AlertTriangle}
                              onClick={() => onEscalate(c)}>
                              {isAr ? 'تصعيد' : 'Escalate'}
                            </Button>
                          )}
                          {actions.canClose && (
                            <Button size="sm" variant="secondary" Icon={CheckCircle2}
                              onClick={() => onClose(c)}>
                              {isAr ? 'إغلاق' : 'Close'}
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Shell>
  );
}

// ─── Inline progress bar ───────────────────────────────────────────────

function ProgressBar({ percent, label }: { percent: number; label: string }) {
  const color = percent >= 80 ? 'var(--success)'
    : percent >= 50 ? 'var(--info)'
    : percent > 0 ? 'var(--warning)'
    : 'var(--ink-4)';
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{
        flex: 1, height: 8, borderRadius: 4,
        background: 'var(--surface-3)', overflow: 'hidden', minWidth: 80,
      }}>
        <div style={{
          width: `${Math.max(0, Math.min(100, percent))}%`,
          height: '100%', background: color, transition: 'width 200ms',
        }} />
      </div>
      <span style={{ fontSize: 12, color: 'var(--ink-3)', minWidth: 56 }}>
        {label}
      </span>
    </div>
  );
}
