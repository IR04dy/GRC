import { useCallback, useEffect, useState } from 'react';
import {
  Inbox, ClipboardCheck, Scale, Siren, BarChart3, Plus, RefreshCw, Download,
  CheckCircle2, XCircle, ShieldAlert,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  governanceAssuranceApi, ApiError,
  type GovAccessRequestDto, type GovCampaignView, type GovReviewItemDto,
  type GovSodRuleDto, type GovSodViolationDto, type GovBreakGlassDto, type GovAnalyticsDto,
  type GovRoleDto, type AdminUserDto,
} from '../../lib/api';

/**
 * Access-Governance assurance tabs (RBAC plan §6.8 — the P1–P3 screens over the
 * already-live APIs): access requests (maker-checker + SoD override), review
 * campaigns, SoD rules/violations/dispositions, break-glass, and analytics with
 * the SAMA/NCA evidence-ZIP export.
 */

const INPUT: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 140, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};
const EMPTY: React.CSSProperties = { padding: 14, color: 'var(--ink-3)', fontSize: 13 };

function statusTone(s: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (s) {
    case 'PROVISIONED': case 'APPROVED': case 'CERTIFY': case 'REVIEWED': case 'REMEDIATED': case 'CLOSED': return 'success';
    case 'PENDING': case 'OPEN': case 'PENDING_REVIEW': case 'ACTIVE': return 'warning';
    case 'REJECTED': case 'REVOKE': case 'EXPIRED': return 'danger';
    case 'ACCEPTED_RISK': case 'MODIFY': return 'info';
    default: return 'neutral';
  }
}

// ─── Access Requests (maker-checker, §6.2) ───────────────────────────────────

export function RequestsTab({ roles, users }: { roles: GovRoleDto[]; users: AdminUserDto[] }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [rows, setRows] = useState<GovAccessRequestDto[]>([]);
  const [status, setStatus] = useState('PENDING');
  const [form, setForm] = useState({ beneficiaryUserId: '', roleId: '', justification: '' });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setRows(await governanceAssuranceApi.requests(status || undefined)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load requests'); }
  }, [status]);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  const userName = (id: number) => users.find(u => u.id === id)?.username ?? `#${id}`;
  const roleName = (id: number) => roles.find(r => r.id === id)?.roleName ?? `#${id}`;

  async function create() {
    if (!form.beneficiaryUserId || !form.roleId || !form.justification) {
      toast.danger(isAr ? 'كل الحقول مطلوبة' : 'All fields are required'); return;
    }
    setBusy(true);
    try {
      await governanceAssuranceApi.createRequest({
        beneficiaryUserId: Number(form.beneficiaryUserId), roleId: Number(form.roleId),
        justification: form.justification,
      });
      toast.success(isAr ? 'أُرسل الطلب' : 'Request submitted');
      setForm({ beneficiaryUserId: '', roleId: '', justification: '' });
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
    finally { setBusy(false); }
  }

  async function approve(r: GovAccessRequestDto) {
    try {
      await governanceAssuranceApi.approveRequest(r.id);
      toast.success(isAr ? 'اعتُمد وزُوِّد' : 'Approved + provisioned');
      await load();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : 'Approve failed';
      // Preventive SoD block — offer the GRC_ADMINISTRATOR override path.
      if (msg.includes('SoD conflict')) {
        const justification = prompt((isAr
          ? 'تعارض فصل مهام. أدخل مبرر التجاوز (GRC_ADMINISTRATOR فقط):\n'
          : 'SoD conflict. Enter an override justification (GRC_ADMINISTRATOR only):\n') + msg);
        if (justification) {
          try {
            await governanceAssuranceApi.approveRequest(r.id, { override: true, overrideJustification: justification });
            toast.success(isAr ? 'اعتُمد بتجاوز موثق' : 'Approved with documented override');
            await load();
            return;
          } catch (e2) { toast.danger(e2 instanceof ApiError ? e2.message : 'Override failed'); return; }
        }
      }
      toast.danger(msg);
    }
  }

  async function reject(r: GovAccessRequestDto) {
    const reason = prompt(isAr ? 'سبب الرفض:' : 'Rejection reason:');
    if (!reason) return;
    try {
      await governanceAssuranceApi.rejectRequest(r.id, reason);
      toast.success(isAr ? 'رُفض الطلب' : 'Request rejected');
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Reject failed'); }
  }

  return (
    <Card title={isAr ? 'طلبات الوصول (أربع عيون)' : 'Access requests (4-eyes)'} Icon={Inbox}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <select value={form.beneficiaryUserId} onChange={e => setForm(f => ({ ...f, beneficiaryUserId: e.target.value }))} style={INPUT}>
            <option value="">{isAr ? 'المستفيد…' : 'Beneficiary…'}</option>
            {users.map(u => <option key={u.id} value={u.id}>{u.username}</option>)}
          </select>
          <select value={form.roleId} onChange={e => setForm(f => ({ ...f, roleId: e.target.value }))} style={INPUT}>
            <option value="">{isAr ? 'الدور…' : 'Role…'}</option>
            {roles.map(r => <option key={r.id} value={r.id}>{r.roleName}</option>)}
          </select>
          <input value={form.justification} onChange={e => setForm(f => ({ ...f, justification: e.target.value }))}
            placeholder={isAr ? 'المبرر' : 'Justification'} style={{ ...INPUT, minWidth: 200 }} />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void create()} disabled={busy}>
            {isAr ? 'طلب' : 'Request'}
          </Button>
          <select value={status} onChange={e => setStatus(e.target.value)} style={INPUT}>
            {['PENDING', 'PROVISIONED', 'REJECTED', ''].map(s =>
              <option key={s} value={s}>{s || (isAr ? 'الكل' : 'All')}</option>)}
          </select>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
        </div>
      }>
      {rows.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا توجد طلبات.' : 'No requests.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>#</th>
              <th>{isAr ? 'المستفيد' : 'Beneficiary'}</th>
              <th>{isAr ? 'الدور' : 'Role'}</th>
              <th>{isAr ? 'المبرر' : 'Justification'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th>{isAr ? 'محرك السير' : 'Engine'}</th>
              <th></th>
            </tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td className="mono text-2xs">{r.id}</td>
                  <td className="text-2xs">{userName(r.beneficiaryUserId)}</td>
                  <td className="mono text-2xs">{roleName(r.roleId)}</td>
                  <td className="text-2xs" style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.justification}</td>
                  <td><Badge tone={statusTone(r.status)} dot>{r.status}</Badge></td>
                  <td className="mono text-2xs">{r.engineRequestId ? `WF#${r.engineRequestId}` : '—'}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {r.status === 'PENDING' && <>
                      <Button size="sm" variant="ghost" Icon={CheckCircle2} title={isAr ? 'اعتماد' : 'Approve'}
                        onClick={() => void approve(r)} />
                      <Button size="sm" variant="ghost" Icon={XCircle} title={isAr ? 'رفض' : 'Reject'}
                        onClick={() => void reject(r)} />
                    </>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
    </Card>
  );
}

// ─── Review campaigns (§6.5) ─────────────────────────────────────────────────

export function CampaignsTab({ users }: { users: AdminUserDto[] }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [rows, setRows] = useState<GovCampaignView[]>([]);
  const [open, setOpen] = useState<number | null>(null);
  const [items, setItems] = useState<GovReviewItemDto[]>([]);
  const [form, setForm] = useState({ titleEn: '', scope: 'PRIVILEGED', windowEnd: '' });

  const load = useCallback(async () => {
    try { setRows(await governanceAssuranceApi.campaigns()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load campaigns'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function openItems(id: number) {
    setOpen(id);
    try { setItems(await governanceAssuranceApi.campaignItems(id)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load items'); }
  }

  async function create() {
    if (!form.titleEn || !form.windowEnd) {
      toast.danger(isAr ? 'العنوان وتاريخ النهاية مطلوبان' : 'Title and window end are required'); return;
    }
    try {
      await governanceAssuranceApi.createCampaign({
        titleEn: form.titleEn, scope: form.scope,
        windowStart: new Date().toISOString().slice(0, 10), windowEnd: form.windowEnd,
      });
      toast.success(isAr ? 'فُتحت الحملة' : 'Campaign opened');
      setForm({ titleEn: '', scope: 'PRIVILEGED', windowEnd: '' });
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  async function decide(item: GovReviewItemDto, decision: string) {
    const note = decision === 'MODIFY'
      ? prompt(isAr ? 'وصف التعديل المطلوب:' : 'Describe the required modification:') ?? undefined
      : undefined;
    if (decision === 'MODIFY' && !note) return;
    try {
      await governanceAssuranceApi.decideItem(item.campaignId, item.id, decision, note);
      toast.success(decision);
      if (open) await openItems(open);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Decision failed'); }
  }

  async function close(c: GovCampaignView) {
    let note: string | undefined;
    if (c.pending > 0) {
      note = prompt(isAr
        ? `${c.pending} عنصر معلق — الإغلاق الاستثنائي يتطلب مبرراً (GRC_ADMINISTRATOR):`
        : `${c.pending} item(s) pending — exception closure needs a note (GRC_ADMINISTRATOR):`) ?? undefined;
      if (!note) return;
    }
    try {
      await governanceAssuranceApi.closeCampaign(c.campaign.id, note);
      toast.success(isAr ? 'أُغلقت الحملة' : 'Campaign closed');
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Close failed'); }
  }

  const userName = (id?: number | null) => id == null ? '—' : (users.find(u => u.id === id)?.username ?? `#${id}`);

  return (
    <Card title={isAr ? 'حملات إعادة التصديق' : 'Recertification campaigns'} Icon={ClipboardCheck}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.titleEn} onChange={e => setForm(f => ({ ...f, titleEn: e.target.value }))}
            placeholder={isAr ? 'عنوان الحملة' : 'Campaign title'} style={{ ...INPUT, minWidth: 180 }} />
          <select value={form.scope} onChange={e => setForm(f => ({ ...f, scope: e.target.value }))} style={INPUT}>
            {['PRIVILEGED', 'ALL'].map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <input type="date" value={form.windowEnd} onChange={e => setForm(f => ({ ...f, windowEnd: e.target.value }))} style={INPUT} />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void create()}>{isAr ? 'فتح' : 'Open'}</Button>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
        </div>
      }>
      {rows.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا توجد حملات.' : 'No campaigns.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الحملة' : 'Campaign'}</th>
              <th>{isAr ? 'النطاق' : 'Scope'}</th>
              <th>{isAr ? 'النافذة' : 'Window'}</th>
              <th>{isAr ? 'هجري' : 'Hijri'}</th>
              <th>{isAr ? 'التقدم' : 'Progress'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th></th>
            </tr></thead>
            <tbody>
              {rows.map(c => (
                <tr key={c.campaign.id} style={open === c.campaign.id ? { background: 'var(--surface-2)' } : undefined}>
                  <td className="text-2xs">{c.campaign.titleEn}</td>
                  <td><Badge tone="info">{c.campaign.scope}</Badge></td>
                  <td className="text-2xs">{c.campaign.windowStart} → {c.campaign.windowEnd}</td>
                  <td className="text-2xs" dir="rtl">{c.campaign.windowHijri ?? '—'}</td>
                  <td className="text-2xs">{c.total - c.pending}/{c.total}</td>
                  <td><Badge tone={statusTone(c.campaign.status)} dot>{c.campaign.status}</Badge></td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <Button size="sm" variant="ghost" onClick={() => void openItems(c.campaign.id)}>
                      {isAr ? 'العناصر' : 'Items'}
                    </Button>
                    {c.campaign.status === 'OPEN' &&
                      <Button size="sm" variant="ghost" onClick={() => void close(c)}>{isAr ? 'إغلاق' : 'Close'}</Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
      {open != null && items.length > 0 && (
        <div style={{ marginTop: 12 }}>
          <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'المستخدم' : 'User'}</th>
              <th>{isAr ? 'الدور' : 'Role'}</th>
              <th>{isAr ? 'المصدر' : 'Source'}</th>
              <th>{isAr ? 'القرار' : 'Decision'}</th>
              <th>{isAr ? 'قرّر بواسطة' : 'Decided by'}</th>
              <th></th>
            </tr></thead>
            <tbody>
              {items.map(i => (
                <tr key={i.id}>
                  <td className="text-2xs">{i.username}</td>
                  <td className="mono text-2xs">{i.roleName}</td>
                  <td className="text-2xs">{i.assignmentSource ?? '—'}</td>
                  <td><Badge tone={statusTone(i.decision)} dot>{i.decision}</Badge></td>
                  <td className="text-2xs">{userName(i.decidedBy)}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {i.decision === 'PENDING' && ['CERTIFY', 'REVOKE', 'MODIFY'].map(d =>
                      <Button key={d} size="sm" variant="ghost" onClick={() => void decide(i, d)}>{d}</Button>)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

// ─── SoD (§6.4) ──────────────────────────────────────────────────────────────

export function SodTab() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [rules, setRules] = useState<GovSodRuleDto[]>([]);
  const [violations, setViolations] = useState<GovSodViolationDto[]>([]);
  const [status, setStatus] = useState('OPEN');

  const load = useCallback(async () => {
    try {
      setRules(await governanceAssuranceApi.sodRules());
      setViolations(await governanceAssuranceApi.sodViolations(status || undefined));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load SoD data'); }
  }, [status]);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function scan() {
    try {
      const r = await governanceAssuranceApi.sodScan();
      toast.success(isAr ? `أُثيرت ${r.violationsRaised} مخالفة` : `${r.violationsRaised} violation(s) raised`);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Scan failed'); }
  }

  async function disposition(v: GovSodViolationDto, st: 'ACCEPTED_RISK' | 'REMEDIATED') {
    const note = prompt(isAr ? 'ملاحظة التسوية:' : 'Disposition note:');
    if (!note) return;
    let acceptedUntil: string | undefined;
    if (st === 'ACCEPTED_RISK') {
      acceptedUntil = prompt(isAr ? 'قبول المخاطر حتى (YYYY-MM-DD):' : 'Accepted until (YYYY-MM-DD):') ?? undefined;
      if (!acceptedUntil) return;
    }
    try {
      await governanceAssuranceApi.disposition(v.id, { status: st, note, acceptedUntil });
      toast.success(st);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Disposition failed'); }
  }

  return (
    <>
      <Card title={isAr ? 'مخالفات فصل المهام' : 'SoD violations'} Icon={Scale}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <select value={status} onChange={e => setStatus(e.target.value)} style={INPUT}>
              {['OPEN', 'ACCEPTED_RISK', 'REMEDIATED', ''].map(s =>
                <option key={s} value={s}>{s || (isAr ? 'الكل' : 'All')}</option>)}
            </select>
            <Button size="sm" variant="primary" Icon={ShieldAlert} onClick={() => void scan()}>
              {isAr ? 'فحص الآن' : 'Scan now'}
            </Button>
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
          </div>
        }>
        {violations.length === 0
          ? <div style={EMPTY}>{isAr ? 'لا مخالفات بهذه الحالة.' : 'No violations with this status.'}</div>
          : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'القاعدة' : 'Rule'}</th>
                <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                <th>{isAr ? 'المستخدم' : 'User'}</th>
                <th>{isAr ? 'التعارض' : 'Conflict'}</th>
                <th>{isAr ? 'المصدر' : 'Source'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th></th>
              </tr></thead>
              <tbody>
                {violations.map(v => (
                  <tr key={v.id}>
                    <td className="mono text-2xs">{v.ruleCode}</td>
                    <td><Badge tone={v.severity === 'CRITICAL' ? 'danger' : v.severity === 'HIGH' ? 'warning' : 'neutral'} dot>{v.severity}</Badge></td>
                    <td className="text-2xs">{v.username}</td>
                    <td className="text-2xs">{v.detailEn ?? '—'}</td>
                    <td><Badge tone={v.detectionSource === 'OVERRIDE' ? 'info' : 'neutral'}>{v.detectionSource}</Badge></td>
                    <td><Badge tone={statusTone(v.status)} dot>{v.status}</Badge></td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {v.status === 'OPEN' && <>
                        <Button size="sm" variant="ghost" onClick={() => void disposition(v, 'ACCEPTED_RISK')}>
                          {isAr ? 'قبول مؤقت' : 'Accept risk'}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => void disposition(v, 'REMEDIATED')}>
                          {isAr ? 'عولجت' : 'Remediated'}
                        </Button>
                      </>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>}
      </Card>
      <div style={{ height: 16 }} />
      <Card title={isAr ? 'حزمة قواعد فصل المهام' : 'SoD rule pack'} Icon={Scale}>
        <table className="grc-table">
          <thead><tr>
            <th>{isAr ? 'الرمز' : 'Code'}</th>
            <th>{isAr ? 'النوع' : 'Type'}</th>
            <th>{isAr ? 'التعارض' : 'Conflict'}</th>
            <th>{isAr ? 'الخطورة' : 'Severity'}</th>
            <th>{isAr ? 'الأساس' : 'Rationale'}</th>
          </tr></thead>
          <tbody>
            {rules.map(r => (
              <tr key={r.id}>
                <td className="mono text-2xs">{r.ruleCode}</td>
                <td><Badge tone="neutral">{r.ruleType}</Badge></td>
                <td className="mono text-2xs">
                  {r.ruleType === 'PAIR'
                    ? `${r.moduleA}/${r.actionA} × ${r.moduleB}/${r.actionB}`
                    : `${r.structuralKey} [${r.rolePatternCsv}]`}
                </td>
                <td><Badge tone={r.severity === 'CRITICAL' ? 'danger' : r.severity === 'HIGH' ? 'warning' : 'neutral'} dot>{r.severity}</Badge></td>
                <td className="text-2xs">{isAr && r.rationaleAr ? r.rationaleAr : r.rationaleEn}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  );
}

// ─── Break-glass (§6.6) ──────────────────────────────────────────────────────

export function BreakGlassTab() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [rows, setRows] = useState<GovBreakGlassDto[]>([]);
  const [form, setForm] = useState({ incidentRef: '', reason: '', validityHours: '24' });

  const load = useCallback(async () => {
    try { setRows(await governanceAssuranceApi.breakGlass()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load break-glass ledger'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function activate() {
    if (!form.incidentRef) { toast.danger(isAr ? 'مرجع الحادثة مطلوب' : 'Incident reference is required'); return; }
    if (!confirm(isAr
      ? 'تفعيل وصول الطوارئ يمنحك EMERGENCY_RESPONDER ويُسجَّل كحدث حرج مع مراجعة لاحقة إلزامية. متابعة؟'
      : 'Break-glass grants you EMERGENCY_RESPONDER, fires a CRITICAL audit event, and requires a mandatory post-hoc review. Continue?')) return;
    try {
      await governanceAssuranceApi.activateBreakGlass({
        incidentRef: form.incidentRef, reason: form.reason || undefined,
        validityHours: Number(form.validityHours) || 24,
      });
      toast.success(isAr ? 'فُعِّل وصول الطوارئ' : 'Break-glass activated');
      setForm({ incidentRef: '', reason: '', validityHours: '24' });
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Activation failed'); }
  }

  async function review(r: GovBreakGlassDto) {
    const note = prompt(isAr ? 'خلاصة المراجعة اللاحقة:' : 'Post-hoc review note:');
    if (!note) return;
    try {
      await governanceAssuranceApi.reviewBreakGlass(r.id, note);
      toast.success(isAr ? 'اكتملت المراجعة' : 'Review completed');
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Review failed'); }
  }

  return (
    <Card title={isAr ? 'وصول الطوارئ (كسر الزجاج)' : 'Break-glass emergency access'} Icon={Siren}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.incidentRef} onChange={e => setForm(f => ({ ...f, incidentRef: e.target.value }))}
            placeholder={isAr ? 'مرجع الحادثة (إلزامي)' : 'Incident ref (required)'} style={INPUT} />
          <input value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))}
            placeholder={isAr ? 'السبب' : 'Reason'} style={INPUT} />
          <input type="number" min={1} max={24} value={form.validityHours}
            onChange={e => setForm(f => ({ ...f, validityHours: e.target.value }))}
            style={{ ...INPUT, minWidth: 70 }} title={isAr ? 'ساعات (≤24)' : 'Hours (≤24)'} />
          <Button size="sm" variant="primary" Icon={Siren} onClick={() => void activate()}>
            {isAr ? 'تفعيل' : 'Activate'}
          </Button>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
        </div>
      }>
      {rows.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا سجلات.' : 'No events.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'المُفعِّل' : 'Activated by'}</th>
              <th>{isAr ? 'الحادثة' : 'Incident'}</th>
              <th>{isAr ? 'ينتهي' : 'Expires'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th>{isAr ? 'المراجعة' : 'Review'}</th>
              <th></th>
            </tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td className="text-2xs">{r.activatedByUsername}</td>
                  <td className="mono text-2xs">{r.incidentRef}</td>
                  <td className="text-2xs">{r.expiresAt?.replace('T', ' ').slice(0, 16)}</td>
                  <td><Badge tone={statusTone(r.status)} dot>{r.status}</Badge></td>
                  <td><Badge tone={statusTone(r.reviewStatus)} dot>{r.reviewStatus}</Badge></td>
                  <td>
                    {r.reviewStatus === 'PENDING_REVIEW' &&
                      <Button size="sm" variant="ghost" onClick={() => void review(r)}>
                        {isAr ? 'مراجعة' : 'Review'}
                      </Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
    </Card>
  );
}

// ─── Analytics (§6.7) ────────────────────────────────────────────────────────

export function AnalyticsTab() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [data, setData] = useState<GovAnalyticsDto | null>(null);

  const load = useCallback(async () => {
    try { setData(await governanceAssuranceApi.analytics()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load analytics'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function exportPack() {
    try {
      await governanceAssuranceApi.evidencePackage();
      toast.success(isAr ? 'نُزِّلت حزمة الأدلة' : 'Evidence package downloaded');
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Export failed (needs IAM/EXPORT)'); }
  }

  const KPIS = data ? [
    { label: isAr ? 'حاملو صلاحيات مميزة' : 'Privileged holders', value: data.privilegedHolders },
    { label: isAr ? 'صلاحيات خاملة (≥90ي)' : 'Dormant grants (≥90d)', value: data.dormantGrants },
    { label: isAr ? 'تنتهي خلال 30ي' : 'Expiring ≤30d', value: data.grantsExpiring30d },
    { label: isAr ? 'مخالفات فصل مهام مفتوحة' : 'Open SoD violations', value: data.openSodViolations },
    { label: isAr ? 'وصول طوارئ نشط' : 'Break-glass active', value: data.breakGlassActive },
    { label: isAr ? 'بانتظار مراجعة لاحقة' : 'Awaiting post-hoc review', value: data.breakGlassAwaitingReview },
    { label: isAr ? 'أنشطة مدير المنصّة (30ي)' : 'PLATFORM_ADMIN actions (30d)', value: data.platformAdminActions30d },
  ] : [];

  return (
    <Card title={isAr ? 'تحليلات الوصول' : 'Access analytics'} Icon={BarChart3}
      action={
        <div style={{ display: 'inline-flex', gap: 8 }}>
          <Button size="sm" variant="primary" Icon={Download} onClick={() => void exportPack()}>
            {isAr ? 'حزمة أدلة SAMA/NCA' : 'SAMA/NCA evidence ZIP'}
          </Button>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
        </div>
      }>
      {!data
        ? <div style={EMPTY}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
        : <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10, marginBottom: 16 }}>
              {KPIS.map(k => (
                <div key={k.label} style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: '10px 12px' }}>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{k.label}</div>
                  <div style={{ fontSize: 22, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{k.value}</div>
                </div>
              ))}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>{isAr ? 'اكتمال الحملات' : 'Campaign completion'}</div>
                <table className="grc-table">
                  <thead><tr><th>{isAr ? 'الحملة' : 'Campaign'}</th><th>%</th><th>{isAr ? 'الحالة' : 'Status'}</th></tr></thead>
                  <tbody>
                    {data.campaignCompletion.map(c => (
                      <tr key={c.id}>
                        <td className="text-2xs">{c.titleEn}</td>
                        <td className="mono text-2xs">{c.completionPct}%</td>
                        <td><Badge tone={statusTone(c.status)} dot>{c.status}</Badge></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>{isAr ? 'الأكثر صلاحياتٍ' : 'Top grant holders'}</div>
                <table className="grc-table">
                  <thead><tr><th>{isAr ? 'المستخدم' : 'User'}</th><th>{isAr ? 'الصلاحيات' : 'Grants'}</th></tr></thead>
                  <tbody>
                    {data.topGrantHolders.map(t => (
                      <tr key={t.username}>
                        <td className="text-2xs">{t.username}</td>
                        <td className="mono text-2xs">{t.grants}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>}
    </Card>
  );
}
