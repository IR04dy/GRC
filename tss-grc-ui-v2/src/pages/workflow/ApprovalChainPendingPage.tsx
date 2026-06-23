import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Inbox, ArrowRight, Clock, Users, UserCog, GitBranch,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import {
  SEED_INSTANCES, SEED_TEMPLATES, STEP_TYPE_LABEL,
  pendingForUser,
  type PendingForMeRow,
} from '../../lib/approvalChains';

/**
 * Sprint 13 — "My pending approvals" widget.
 *
 * <p>Mirrors the backend's GET /api/v1/workflow/chains/pending-for-me
 * endpoint shape: one row per step the logged-in user is the matched
 * approver for. The widget is the operator's landing list — every
 * morning the bank's CISO, DPO, and Compliance Officer should hit this
 * page first to clear the queue.
 *
 * <p>For demo purposes the role picker at the top of the page lets the
 * viewer swap personas (CISO / DPO / Compliance Officer / Risk Owner /
 * Department Head). In production these roles come from the JWT.
 */
export function ApprovalChainPendingPage() {
  const { t, lang } = useI18n();

  // Demo role picker — in prod the roles come from useAuth() / JWT.
  const personas = [
    { id: 1,  role: 'CISO',                nameEn: 'Ahmed Al-Qahtani — CISO',                  nameAr: 'أحمد القحطاني — رئيس أمن المعلومات' },
    { id: 7,  role: 'DPO',                 nameEn: 'Mona Al-Bishi — DPO',                       nameAr: 'منى البيشي — مسؤول حماية البيانات' },
    { id: 17, role: 'COMPLIANCE_OFFICER',  nameEn: 'Hassan Al-Saud — Compliance Officer',        nameAr: 'حسن السعود — مسؤول الالتزام' },
    { id: 42, role: 'RISK_OWNER',          nameEn: 'Salma Al-Otaibi — Risk Owner',                nameAr: 'سلمى العتيبي — مالك المخاطرة' },
    { id: 21, role: 'DEPARTMENT_HEAD',     nameEn: 'Ahmad Al-Shehri — Department Head',           nameAr: 'أحمد الشهري — رئيس الإدارة' },
    { id: 14, role: 'CHANGE_MANAGER',      nameEn: 'Tariq Al-Harbi — Change Manager',             nameAr: 'طارق الحربي — مدير التغيير' },
    { id: 99, role: 'LEGAL_COUNSEL',       nameEn: 'Saud Al-Dosari — Legal Counsel',              nameAr: 'سعود الدوسري — المستشار القانوني' },
    { id: 88, role: 'CAB_LEAD',            nameEn: 'Layla Al-Saud — CAB Lead',                    nameAr: 'ليلى السعود — قائد لجنة التغيير' },
    { id: 11, role: 'BOARD_CHAIR',         nameEn: 'Khaled Al-Rasheed — Board Chair',             nameAr: 'خالد الراشد — رئيس المجلس' },
  ];
  const [activeIdx, setActiveIdx] = useState(1); // default to DPO — has the most pending
  const active = personas[activeIdx];

  const rows: PendingForMeRow[] = useMemo(
    () => pendingForUser(active.id, new Set([active.role]), SEED_INSTANCES, SEED_TEMPLATES),
    [active],
  );

  // KPI rollups
  const totalPending = rows.length;
  const serialRows = rows.filter(r => r.stepType === 'SERIAL').length;
  const parallelRows = rows.filter(r => r.stepType === 'PARALLEL').length;
  // Naive "near SLA" — anything whose step has been current for more than
  // 70% of its sla_hours window.
  const nearSla = useMemo(() => {
    const now = Date.now();
    return rows.filter(r => {
      if (!r.slaHours) return false;
      const elapsedMs = now - new Date(r.stepBecameCurrentAt).getTime();
      const slaMs = r.slaHours * 3600 * 1000;
      return elapsedMs / slaMs >= 0.7;
    }).length;
  }, [rows]);

  return (
    <Shell title={t('approvalChainPendingTitle', 'My pending approvals')}
           sub={t('approvalChainPendingSub', 'Approval-chain rungs assigned to me')}>
      <PageHeader
        eyebrow={t('approvalChainEyebrow', 'Approval Chains · Sprint 13')}
        title={t('approvalChainPendingTitle', 'My pending approvals')}
        sub={t('approvalChainPendingPageSub', 'Every in-flight chain rung waiting on me. Sorted by SLA pressure — the topmost is closest to breaching the configured turnaround. Sourced from the GET /api/v1/workflow/chains/pending-for-me endpoint.')}
        actions={
          <Link to="/workflows/chains/templates">
            <span className="btn btn--secondary btn--sm" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
              <GitBranch size={14} />
              {t('approvalChainSeeTemplates', 'See templates')}
            </span>
          </Link>
        }
      />

      <Card title={t('approvalChainPersonaTitle', 'Viewing as')} Icon={UserCog}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {personas.map((p, idx) => (
            <button key={p.id}
              type="button"
              onClick={() => setActiveIdx(idx)}
              className={'btn ' + (idx === activeIdx ? 'btn--primary' : 'btn--secondary') + ' btn--sm'}>
              {lang === 'ar' ? p.nameAr : p.nameEn}
            </button>
          ))}
        </div>
      </Card>

      <div className="kpi-grid" style={{ marginTop: 16, marginBottom: 16 }}>
        <Kpi Icon={Inbox} label={t('approvalChainPendingKpiTotal',    'Waiting on me')}   value={String(totalPending)}  tone="primary" to="/workflows" />
        <Kpi Icon={Clock} label={t('approvalChainPendingKpiNearSla',  'Near SLA breach')} value={String(nearSla)}       tone="warning" to="/workflows" />
        <Kpi Icon={Users} label={t('approvalChainPendingKpiSerial',   'Serial rungs')}    value={String(serialRows)}    tone="info"    to="/workflows" />
        <Kpi Icon={Users} label={t('approvalChainPendingKpiParallel', 'Parallel rungs')}  value={String(parallelRows)}  tone="accent"  to="/workflows" />
      </div>

      <Card title={t('approvalChainPendingListTitle', 'Pending rungs')} Icon={Inbox}>
        {rows.length === 0
          ? <InlineEmpty>{t('approvalChainPendingEmpty', 'Nothing pending for this persona — every chain rung is clear.')}</InlineEmpty>
          : (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {rows.map(r => <PendingRow key={`${r.instanceId}-${r.stepId}`} row={r} lang={lang} t={t} />)}
            </div>
          )}
      </Card>
    </Shell>
  );
}

function PendingRow({
  row, lang, t,
}: {
  row: PendingForMeRow;
  lang: 'en' | 'ar';
  t: (key: string, fallback?: string) => string;
}) {
  const elapsedMs = Date.now() - new Date(row.stepBecameCurrentAt).getTime();
  const slaMs = (row.slaHours ?? 0) * 3600 * 1000;
  const ratio = slaMs > 0 ? elapsedMs / slaMs : 0;
  const tone: 'success' | 'warning' | 'danger' | 'info' =
    ratio >= 1   ? 'danger'
    : ratio >= 0.7 ? 'warning'
    : ratio >= 0.4 ? 'info'
    : 'success';

  return (
    <div style={{
      padding: '12px 0',
      borderBottom: '1px dashed var(--border)',
      display: 'grid',
      gridTemplateColumns: '1fr auto',
      gap: 12,
      alignItems: 'flex-start',
    }}>
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <span style={{ fontWeight: 700, fontSize: 14 }}>
            {lang === 'ar' ? row.stepTitleAr : row.stepTitleEn}
          </span>
          <Badge tone="primary">{row.templateCode}</Badge>
          <Badge tone={row.stepType === 'PARALLEL' ? 'accent' : 'neutral'}>
            {lang === 'ar' ? STEP_TYPE_LABEL[row.stepType].ar : STEP_TYPE_LABEL[row.stepType].en}
          </Badge>
          <Badge tone="info">{t('approvalChainStepLabel', 'Step')} {row.stepOrder}</Badge>
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
          {lang === 'ar' ? row.templateTitleAr : row.templateTitleEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <span>
            <b>{t('approvalChainOriginator', 'Originator')}:</b>{' '}
            {lang === 'ar' ? (row.originatorNameAr ?? '') : (row.originatorNameEn ?? '')}
            {(!row.originatorNameAr && !row.originatorNameEn) && (
              <span className="mono"> #{row.originatorUserId}</span>
            )}
          </span>
          {row.requiredRole && (
            <span>
              <b>{t('approvalChainStepRole', 'Role')}:</b>{' '}
              <span className="mono">{row.requiredRole}</span>
            </span>
          )}
          <span>
            <b>{t('approvalChainStarted', 'Started')}:</b>{' '}
            {new Date(row.startedAt).toLocaleString()}
          </span>
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
        {row.slaHours != null && (
          <Badge tone={tone}>
            <Clock size={11} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
            {row.slaHours}h SLA · {Math.round(ratio * 100)}%
          </Badge>
        )}
        <Link to={`/workflows/chains/${row.instanceId}`} style={{ fontSize: 11, color: 'var(--primary)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          {t('approvalChainOpenInstance', 'Open chain')} <ArrowRight size={11} />
        </Link>
      </div>
    </div>
  );
}
