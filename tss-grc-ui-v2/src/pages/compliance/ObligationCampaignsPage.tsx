import { useEffect, useState } from 'react';
import {
  Briefcase, Plus, Play, Mail, AlertTriangle, CheckCircle2, FileText, Send,
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
  sendCampaignReminders,
  escalateCampaign,
  closeCampaign,
  type ObligationCampaign,
  type Regulator,
  CAMPAIGN_STATUS_LABELS,
  REGULATORS,
  REGULATOR_LABELS,
  campaignStatusTone,
  campaignActions,
} from '../../lib/obligations';

/**
 * Sprint 11 — Obligation attestation campaigns register.
 *
 * <p>Saudi-native bilingual: "حملات الإقرار الدوري للالتزامات".
 * Mirrors the BCP-attestation campaigns page (Slice 10-D) — same
 * action surface, progress bar, status badges.
 */
export function ObligationCampaignsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();
  const navigate = useNavigate();

  const [items, setItems] = useState<ObligationCampaign[]>([]);
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

  async function onCreate() {
    const titleEn = await dialog.prompt({
      title: isAr ? 'عنوان الحملة (إنجليزي)' : 'Campaign title (English)',
      placeholder: 'e.g. Q2 2026 SAMA obligation attestation cycle',
      required: true,
    });
    if (titleEn === null) return;
    const titleAr = await dialog.prompt({
      title: 'عنوان الحملة (عربي)',
      placeholder: 'مثال: دورة الإقرارات الربع سنوية لالتزامات مؤسسة النقد — الربع الثاني 2026',
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
    const quarter = await dialog.prompt({
      title: isAr ? 'الربع (Q1/Q2/Q3/Q4) أو فارغ للسنة الكاملة' : 'Quarter (Q1/Q2/Q3/Q4) or empty for annual',
      initial: '',
      validate: v => v.trim() === '' || ['Q1', 'Q2', 'Q3', 'Q4'].includes(v.trim())
        ? null : (isAr ? 'Q1 / Q2 / Q3 / Q4 أو فارغ' : 'Q1 / Q2 / Q3 / Q4 or empty'),
    });
    if (quarter === null) return;
    const regulator = await dialog.prompt({
      title: isAr ? 'الجهة الرقابية (اتركها فارغة لكل الجهات)' : 'Regulator filter (empty = all)',
      initial: '',
      validate: v => v.trim() === '' || REGULATORS.includes(v.trim() as Regulator)
        ? null : (isAr ? 'قيمة غير معروفة' : 'Unknown regulator'),
    });
    if (regulator === null) return;
    const closesAt = await dialog.prompt({
      title: isAr ? 'تاريخ إغلاق الحملة (YYYY-MM-DDTHH:mm)' : 'Closes at (YYYY-MM-DDTHH:mm)',
      initial: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16),
      required: true,
    });
    if (closesAt === null) return;
    try {
      await createCampaign({
        titleEn,
        titleAr,
        cycleYear: Number(yearStr),
        cycleQuarter: quarter.trim() || undefined,
        regulatorFilter: regulator.trim() as Regulator | '',
        closesAt: closesAt.includes(':') && closesAt.length === 16 ? `${closesAt}:00` : closesAt,
      });
      toast.success(isAr ? 'تم إنشاء الحملة' : 'Campaign created');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onOpen(c: ObligationCampaign) {
    const ok = await dialog.confirm({
      title: isAr ? 'فتح الحملة وإنشاء سجلات الإقرار؟' : 'Open campaign and materialise attestations?',
      body: isAr
        ? 'سيتم إنشاء سجل إقرار لكل التزام نشط ضمن نطاق الحملة.'
        : 'One attestation row will be created per active obligation matching the campaign scope.',
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

  async function onRemind(c: ObligationCampaign) {
    const ok = await dialog.confirm({
      title: isAr ? 'إرسال تذكير للمسؤولين؟' : 'Send reminder to owners?',
      body: isAr
        ? 'سيُسجَّل التذكير في سجل التدقيق ويُطلق حدث F8 لتوجيه الإشعارات.'
        : 'The reminder is logged to the audit trail and emits an F8 event that routes the notifications.',
    });
    if (!ok) return;
    try {
      await sendCampaignReminders(c.id);
      toast.success(isAr ? 'تم إرسال التذكير' : 'Reminders sent');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onEscalate(c: ObligationCampaign) {
    const ok = await dialog.confirm({
      title: isAr ? 'تصعيد المتأخرات إلى لجنة المراجعة؟' : 'Escalate overdue to Audit Committee?',
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

  async function onClose(c: ObligationCampaign) {
    const summaryEn = await dialog.prompt({
      title: 'Closing summary (English)',
      type: 'textarea',
      placeholder: 'Regulator-facing summary — completion rate, exceptions, follow-ups.',
    });
    if (summaryEn === null) return;
    const summaryAr = await dialog.prompt({
      title: 'ملخّص الإغلاق (عربي)',
      type: 'textarea',
      placeholder: 'ملخص يصلح لتقديمه للجهة التنظيمية — نسبة الإنجاز، الاستثناءات، إجراءات المتابعة.',
    });
    if (summaryAr === null) return;
    try {
      await closeCampaign(c.id, { closingSummaryEn: summaryEn, closingSummaryAr: summaryAr });
      toast.success(isAr ? 'تم إغلاق الحملة' : 'Campaign closed');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ─── KPIs ─────────────────────────────────────────────────────────
  const activeCount = items.filter(c => !['CLOSED', 'CANCELLED', 'DRAFT'].includes(c.status)).length;
  const overdueCount = items.reduce((s, c) => s + (c.totalOverdue ?? 0), 0);
  const closedCount = items.filter(c => c.status === 'CLOSED').length;

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'الالتزام والامتثال' : 'Compliance'}
        title={isAr ? 'حملات الإقرار الدوري للالتزامات' : 'Obligation Attestation Campaigns'}
        sub={isAr
          ? 'إطلاق دورة ربع سنوية أو سنوية لجمع الإقرارات من المسؤولين عن الالتزامات الرقابية — وفق إطار الالتزام لمؤسسة النقد §3.2.'
          : 'Launch a quarterly or annual cycle to collect attestations from obligation owners — per SAMA Compliance Framework §3.2.'}
        actions={
          <Button Icon={Plus} onClick={onCreate}>
            {isAr ? 'حملة جديدة' : 'New campaign'}
          </Button>
        }
      />

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--space-md)' }}>
        <Kpi Icon={Briefcase}
          label={isAr ? 'إجمالي الحملات' : 'Total campaigns'}
          value={String(items.length)} />
        <Kpi Icon={Send}
          label={isAr ? 'حملات نشطة' : 'Active'}
          value={String(activeCount)} tone="info" />
        <Kpi Icon={AlertTriangle}
          label={isAr ? 'متأخرات مفتوحة' : 'Open overdue'}
          value={String(overdueCount)}
          tone={overdueCount > 0 ? 'danger' : 'neutral'} />
        <Kpi Icon={CheckCircle2}
          label={isAr ? 'دورات مُغلَقة' : 'Closed cycles'}
          value={String(closedCount)} tone="success" />
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
                  <th>{isAr ? 'الرمز' : 'Code'}</th>
                  <th>{isAr ? 'العنوان' : 'Title'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th>{isAr ? 'النطاق' : 'Scope'}</th>
                  <th>{isAr ? 'نسبة الإنجاز' : 'Completion'}</th>
                  <th>{isAr ? 'تاريخ الإغلاق' : 'Closes'}</th>
                  <th style={{ width: 280 }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(c => {
                  const actions = campaignActions(c.status);
                  const completed = (c.totalAttested ?? 0) + (c.totalAttestedWithExcs ?? 0);
                  const pct = c.totalInvited > 0
                    ? Math.round((completed * 100) / c.totalInvited) : 0;
                  const closes = c.closesAt
                    ? new Date(c.closesAt).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB') : '—';
                  return (
                    <tr key={c.id}>
                      <td><code style={{ fontSize: 12 }}>{c.campaignCode}</code></td>
                      <td>
                        <Link to={`/compliance/obligation-campaigns/${c.id}`} className="link">
                          {isAr ? c.titleAr : c.titleEn}
                        </Link>
                      </td>
                      <td>
                        <Badge tone={campaignStatusTone(c.status)}>
                          {tlabel(CAMPAIGN_STATUS_LABELS, c.status, isAr)}
                        </Badge>
                      </td>
                      <td style={{ fontSize: 13 }}>
                        {c.regulatorFilter
                          ? (tlabel(REGULATOR_LABELS, c.regulatorFilter, isAr))
                          : (isAr ? 'كل الجهات' : 'All regulators')}
                        {c.cycleQuarter ? ` · ${c.cycleQuarter}` : ''} · {c.cycleYear}
                      </td>
                      <td>
                        <ProgressBar percent={pct}
                          label={`${completed}/${c.totalInvited}`} />
                      </td>
                      <td style={{ fontSize: 13 }}>{closes}</td>
                      <td>
                        <div className="cluster" style={{ gap: 6 }}>
                          <Button size="sm" variant="secondary" Icon={FileText}
                            onClick={() => navigate(`/compliance/obligation-campaigns/${c.id}`)}>
                            {isAr ? 'فتح' : 'Open'}
                          </Button>
                          {actions.canOpen && (
                            <Button size="sm" Icon={Play} onClick={() => onOpen(c)}>
                              {isAr ? 'بدء' : 'Launch'}
                            </Button>
                          )}
                          {actions.canRemind && (
                            <Button size="sm" variant="secondary" Icon={Mail} onClick={() => onRemind(c)}>
                              {isAr ? 'تذكير' : 'Remind'}
                            </Button>
                          )}
                          {actions.canEscalate && (
                            <Button size="sm" variant="danger" Icon={AlertTriangle} onClick={() => onEscalate(c)}>
                              {isAr ? 'تصعيد' : 'Escalate'}
                            </Button>
                          )}
                          {actions.canClose && (
                            <Button size="sm" variant="secondary" Icon={CheckCircle2} onClick={() => onClose(c)}>
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
