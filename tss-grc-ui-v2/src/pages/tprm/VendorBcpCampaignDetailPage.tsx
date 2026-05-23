import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ChevronLeft, Download, CheckCircle2, XCircle, Slash, Mail,
  AlertTriangle, ClipboardCheck, Calendar,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  getCampaign, getCampaignStats, listAttestations,
  acceptAttestation, rejectAttestation, markAttestationOutOfScope,
  attestationsToCsv,
  type VendorBcpCampaign, type CampaignStats,
  type VendorBcpAttestation, type AttestationStatus,
  CAMPAIGN_STATUS_LABELS, ATTESTATION_STATUS_LABELS,
  campaignStatusTone, attestationStatusTone,
} from '../../lib/vendorBcp';

/**
 * Slice 10-D — Campaign detail with per-status drill-down.
 *
 * <p>Header: campaign code, status, response rate, days to close.
 * Body: tab strip over the eight attestation statuses; clicking a row
 * opens a drawer with the submitted fields + accept/reject/NA buttons.
 *
 * <p>"Export evidence pack" downloads a CSV that's the regulator-facing
 * summary (one row per vendor with every relevant column).
 */
export function VendorBcpCampaignDetailPage() {
  const { id } = useParams<{ id: string }>();
  const numId = Number(id);
  const navigate = useNavigate();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [campaign, setCampaign] = useState<VendorBcpCampaign | null>(null);
  const [stats, setStats] = useState<CampaignStats | null>(null);
  const [items, setItems] = useState<VendorBcpAttestation[]>([]);
  const [activeTab, setActiveTab] = useState<AttestationStatus | 'ALL'>('ALL');
  const [drawer, setDrawer] = useState<VendorBcpAttestation | null>(null);

  async function reload() {
    if (!Number.isFinite(numId)) return;
    try {
      const [c, s, a] = await Promise.all([
        getCampaign(numId),
        getCampaignStats(numId),
        listAttestations(numId),
      ]);
      setCampaign(c);
      setStats(s);
      setItems(Array.isArray(a) ? a : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل تفاصيل الحملة' : 'Failed to load campaign'));
    }
  }
  useEffect(() => { void reload(); }, [numId]); // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    if (activeTab === 'ALL') return items;
    return items.filter(a => a.status === activeTab);
  }, [items, activeTab]);

  // ─── Tab counts ────────────────────────────────────────────────────
  const counts = useMemo(() => {
    const byStatus: Record<string, number> = { ALL: items.length };
    for (const a of items) byStatus[a.status] = (byStatus[a.status] ?? 0) + 1;
    return byStatus;
  }, [items]);

  // ─── Actions ──────────────────────────────────────────────────────
  async function review(att: VendorBcpAttestation, action: 'accept' | 'reject' | 'na') {
    const commentEn = await dialog.prompt({
      title: action === 'accept'
        ? (isAr ? 'تعليق القبول (إنجليزي)' : 'Acceptance note (English)')
        : action === 'reject'
        ? (isAr ? 'سبب الرفض (إنجليزي)' : 'Rejection reason (English)')
        : (isAr ? 'سبب الإخراج من النطاق (إنجليزي)' : 'Out-of-scope reason (English)'),
      type: 'textarea',
      required: action !== 'accept',
    });
    if (commentEn === null) return;
    const commentAr = await dialog.prompt({
      title: action === 'accept'
        ? 'تعليق القبول (عربي)'
        : action === 'reject'
        ? 'سبب الرفض (عربي)'
        : 'سبب الإخراج من النطاق (عربي)',
      type: 'textarea',
      required: action !== 'accept',
    });
    if (commentAr === null) return;
    const who = await dialog.prompt({
      title: isAr ? 'اسم المراجع' : 'Reviewer name',
      placeholder: 'e.g. Salman Al-Otaibi (TPRM Lead)',
      required: true,
    });
    if (who === null) return;
    try {
      const body = {
        reviewerCommentEn: commentEn,
        reviewerCommentAr: commentAr,
        reviewedByName: who,
      };
      if (action === 'accept') await acceptAttestation(att.id, body);
      else if (action === 'reject') await rejectAttestation(att.id, body);
      else await markAttestationOutOfScope(att.id, body);
      toast.success(isAr ? 'تم تحديث الحالة' : 'Status updated');
      setDrawer(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  function exportCsv() {
    if (!campaign) return;
    const csv = attestationsToCsv(items, campaign);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${campaign.campaignCode}-evidence-pack.csv`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success(isAr ? 'تم تنزيل الحزمة' : 'Evidence pack downloaded');
  }

  if (!campaign) {
    return (
      <Shell>
        <PageHeader title={isAr ? 'تفاصيل الحملة' : 'Campaign'} />
        <Card><InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} /></Card>
      </Shell>
    );
  }

  const STATUS_TABS: (AttestationStatus | 'ALL')[] = [
    'ALL', 'PENDING_INVITE', 'INVITED', 'REMINDED',
    'OVERDUE', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'NA_OUT_OF_SCOPE',
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={`${campaign.campaignCode} · ${isAr ? 'دورة' : 'Cycle'} ${campaign.cycleYear}${campaign.cycleQuarter ? ' ' + campaign.cycleQuarter : ''}`}
        title={isAr ? campaign.titleAr : campaign.titleEn}
        sub={campaign.regulatorReference || undefined}
        actions={
          <div className="cluster" style={{ gap: 8 }}>
            <Button variant="secondary" Icon={ChevronLeft}
              onClick={() => navigate('/tprm/bcp-campaigns')}>
              {isAr ? 'الرجوع' : 'Back'}
            </Button>
            <Button Icon={Download} onClick={exportCsv}>
              {isAr ? 'تنزيل حزمة الأدلة' : 'Export evidence pack'}
            </Button>
          </div>
        }
      />

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--space-md)' }}>
        <Kpi
          Icon={ClipboardCheck}
          label={isAr ? 'الحالة' : 'Status'}
          value={isAr ? CAMPAIGN_STATUS_LABELS[campaign.status].ar : CAMPAIGN_STATUS_LABELS[campaign.status].en}
          tone={campaignStatusTone(campaign.status) === 'danger' ? 'danger' : 'primary'}
        />
        <Kpi
          Icon={Mail}
          label={isAr ? 'نسبة الاستجابة' : 'Response rate'}
          value={`${stats?.responseRatePercent ?? 0}%`}
          tone={(stats?.responseRatePercent ?? 0) >= 80 ? 'success' : 'info'}
        />
        <Kpi
          Icon={Calendar}
          label={isAr ? 'أيام متبقية' : 'Days remaining'}
          value={String(stats?.daysUntilClose ?? 0)}
          tone={(stats?.daysUntilClose ?? 0) < 7 ? 'warning' : 'neutral'}
        />
        <Kpi
          Icon={AlertTriangle}
          label={isAr ? 'متأخرات' : 'Overdue'}
          value={String(stats?.overdue ?? 0)}
          tone={(stats?.overdue ?? 0) > 0 ? 'danger' : 'neutral'}
        />
      </div>

      <Card title={isAr ? 'سجلات الموردين' : 'Vendor responses'} Icon={ClipboardCheck}>
        <div className="tabs" style={{ marginBottom: 12, flexWrap: 'wrap' }}>
          {STATUS_TABS.map(t => {
            const count = counts[t] ?? 0;
            const label = t === 'ALL'
              ? (isAr ? 'الكل' : 'All')
              : (isAr ? ATTESTATION_STATUS_LABELS[t].ar : ATTESTATION_STATUS_LABELS[t].en);
            return (
              <button key={t}
                className={`tab ${activeTab === t ? 'tab-active' : ''}`}
                onClick={() => setActiveTab(t)}
                style={{ padding: '6px 12px', cursor: 'pointer' }}>
                {label} <span style={{ marginLeft: 6, color: 'var(--ink-4)', fontSize: 11 }}>({count})</span>
              </button>
            );
          })}
        </div>

        {filtered.length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد سجلات في هذه الحالة.' : 'No rows in this status.'} />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{isAr ? 'المورّد' : 'Vendor'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th>{isAr ? 'تاريخ الدعوة' : 'Invited'}</th>
                  <th>{isAr ? 'تاريخ التقديم' : 'Submitted'}</th>
                  <th>{isAr ? 'RTO المُعلن' : 'Attested RTO'}</th>
                  <th>{isAr ? 'MTPD المُعلن' : 'Attested MTPD'}</th>
                  <th>{isAr ? 'تنبيه' : 'Flags'}</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(a => (
                  <tr key={a.id}
                      onClick={() => setDrawer(a)}
                      style={{ cursor: 'pointer' }}>
                    <td>
                      <strong>{(isAr ? a.vendorNameAr : a.vendorName) || `#${a.vendorId}`}</strong>
                    </td>
                    <td>
                      <Badge tone={attestationStatusTone(a.status)}>
                        {isAr ? ATTESTATION_STATUS_LABELS[a.status].ar : ATTESTATION_STATUS_LABELS[a.status].en}
                      </Badge>
                    </td>
                    <td style={{ fontSize: 13 }}>{fmtDate(a.invitedAt, isAr)}</td>
                    <td style={{ fontSize: 13 }}>{fmtDate(a.submittedAt, isAr)}</td>
                    <td style={{ fontSize: 13 }}>
                      {a.rtoHoursAttested != null ? `${a.rtoHoursAttested}h` : '—'}
                    </td>
                    <td style={{ fontSize: 13 }}>
                      {a.mtpdHoursAttested != null ? `${a.mtpdHoursAttested}h` : '—'}
                    </td>
                    <td>
                      {a.rtoBreach && <Badge tone="danger" className="badge-sm">RTO</Badge>}
                      {a.mtpdBreach && <Badge tone="danger" className="badge-sm">MTPD</Badge>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {drawer && (
        <AttestationDrawer
          item={drawer}
          isAr={isAr}
          onClose={() => setDrawer(null)}
          onAccept={() => review(drawer, 'accept')}
          onReject={() => review(drawer, 'reject')}
          onNa={() => review(drawer, 'na')}
        />
      )}
    </Shell>
  );
}

// ─── Drawer ───────────────────────────────────────────────────────────

function AttestationDrawer({
  item, isAr, onClose, onAccept, onReject, onNa,
}: {
  item: VendorBcpAttestation;
  isAr: boolean;
  onClose: () => void;
  onAccept: () => void;
  onReject: () => void;
  onNa: () => void;
}) {
  const canReview = item.status === 'SUBMITTED';
  const canMarkNa = ['INVITED', 'REMINDED', 'OVERDUE'].includes(item.status);

  return (
    <div className="drawer-overlay" onClick={onClose}
         style={{
           position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
           zIndex: 50, display: 'flex', justifyContent: isAr ? 'flex-start' : 'flex-end',
         }}>
      <div onClick={e => e.stopPropagation()}
           style={{
             width: 480, maxWidth: '90vw', height: '100%', overflow: 'auto',
             background: 'var(--surface-1)', padding: 24,
           }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
          <div>
            <div className="eyebrow">{isAr ? 'سجل شهادة المورّد' : 'Vendor attestation'}</div>
            <h3 style={{ margin: '4px 0', fontSize: 18 }}>
              {(isAr ? item.vendorNameAr : item.vendorName) || `#${item.vendorId}`}
            </h3>
            <Badge tone={attestationStatusTone(item.status)}>
              {isAr ? ATTESTATION_STATUS_LABELS[item.status].ar : ATTESTATION_STATUS_LABELS[item.status].en}
            </Badge>
          </div>
          <Button variant="ghost" onClick={onClose}>
            {isAr ? 'إغلاق' : 'Close'}
          </Button>
        </div>

        <Field label={isAr ? 'تاريخ الدعوة' : 'Invited'} value={fmtDate(item.invitedAt, isAr)} />
        <Field label={isAr ? 'تاريخ التقديم' : 'Submitted'} value={fmtDate(item.submittedAt, isAr)} />
        <Field label={isAr ? 'جهة الاتصال المُقدِّمة' : 'Submitted by'}
               value={item.submittedByContactName || item.submittedByContactEmail || '—'} />

        <hr style={{ margin: '12px 0', border: 0, borderTop: '1px solid var(--surface-3)' }} />
        <h4 style={{ fontSize: 13, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          {isAr ? 'الجاهزية المُعلنة' : 'Declared readiness'}
        </h4>
        <Field
          label={isAr ? 'RTO (ساعات)' : 'RTO (hours)'}
          value={item.rtoHoursAttested != null ? `${item.rtoHoursAttested}h` : '—'}
          alert={item.rtoBreach}
          alertLabel={isAr ? 'تجاوز الحد التعاقدي' : 'Exceeds contractual SLA'}
        />
        <Field
          label={isAr ? 'MTPD (ساعات)' : 'MTPD (hours)'}
          value={item.mtpdHoursAttested != null ? `${item.mtpdHoursAttested}h` : '—'}
          alert={item.mtpdBreach}
          alertLabel={isAr ? 'تجاوز الحد التعاقدي' : 'Exceeds contractual SLA'}
        />
        <Field
          label={isAr ? 'هل خطة الاستمرارية مُختبَرة؟' : 'BCP tested?'}
          value={item.hasTestedBcp == null ? '—' : (item.hasTestedBcp ? (isAr ? 'نعم' : 'Yes') : (isAr ? 'لا' : 'No'))}
        />
        <Field label={isAr ? 'آخر اختبار للخطة' : 'Last BCP test'} value={item.lastBcpTestDate ?? '—'} />
        <Field label={isAr ? 'آخر اختبار للتعافي' : 'Last DR test'} value={item.lastDrTestDate ?? '—'} />
        {item.subprocessorDependencies && (
          <Field
            label={isAr ? 'المعالجون الفرعيون' : 'Sub-processors'}
            value={<pre style={{ fontSize: 12, background: 'var(--surface-2)', padding: 8, margin: 0, borderRadius: 4, whiteSpace: 'pre-wrap' }}>{item.subprocessorDependencies}</pre>}
          />
        )}
        {item.evidenceFileName && (
          <Field label={isAr ? 'ملف الدليل' : 'Evidence file'} value={item.evidenceFileName} />
        )}

        {item.reviewerCommentEn && (
          <>
            <hr style={{ margin: '12px 0', border: 0, borderTop: '1px solid var(--surface-3)' }} />
            <h4 style={{ fontSize: 13, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              {isAr ? 'مراجعة TPRM' : 'TPRM review'}
            </h4>
            <Field label={isAr ? 'المراجع' : 'Reviewer'} value={item.reviewedByName ?? '—'} />
            <Field label={isAr ? 'تاريخ المراجعة' : 'Reviewed at'} value={fmtDate(item.reviewedAt, isAr)} />
            <Field label={isAr ? 'التعليق (إنجليزي)' : 'Comment (EN)'} value={item.reviewerCommentEn} />
            <Field label={isAr ? 'التعليق (عربي)' : 'Comment (AR)'} value={item.reviewerCommentAr ?? '—'} />
          </>
        )}

        <div className="cluster" style={{ gap: 8, marginTop: 16 }}>
          {canReview && (
            <>
              <Button Icon={CheckCircle2} onClick={onAccept}>
                {isAr ? 'قبول' : 'Accept'}
              </Button>
              <Button variant="danger" Icon={XCircle} onClick={onReject}>
                {isAr ? 'رفض' : 'Reject'}
              </Button>
            </>
          )}
          {(canReview || canMarkNa) && (
            <Button variant="secondary" Icon={Slash} onClick={onNa}>
              {isAr ? 'خارج النطاق' : 'Out of scope'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function Field({
  label, value, alert, alertLabel,
}: {
  label: string;
  value: React.ReactNode;
  alert?: boolean;
  alertLabel?: string;
}) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ fontSize: 11, color: 'var(--ink-4)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {label}
      </div>
      <div style={{ fontSize: 14, color: 'var(--ink-1)', display: 'flex', alignItems: 'center', gap: 8 }}>
        {value}
        {alert && (
          <Badge tone="danger" className="badge-sm" title={alertLabel}>!</Badge>
        )}
      </div>
    </div>
  );
}

function fmtDate(d?: string | null, isAr = false): string {
  if (!d) return '—';
  try {
    return new Date(d).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB');
  } catch {
    return d;
  }
}
