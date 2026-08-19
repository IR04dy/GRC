import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Scale, FileText, UserMinus, Eye, Plus, Trash2, Send, PlayCircle, StopCircle,
} from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { DataTable, type Column } from '../../../components/ui/DataTable';
import { EmptyState } from '../../../components/ui/EmptyState';
import { InlineEmpty } from '../../../components/ui/InlineEmpty';
import { Tabs } from '../../../components/ui/Tabs';
import { Progress } from '../../../components/ui/Progress';
import { Modal } from '../../../components/ui/Modal';
import { Input, Textarea } from '../../../components/ui/Input';
import { Select } from '../../../components/ui/Select';
import { useI18n } from '../../../lib/i18n';
import { useToast } from '../../../lib/toast';
import { ApiError } from '../../../lib/api';
import { isAuthenticated } from '../../../lib/auth';
import {
  coiApi,
  CAMPAIGN_STATUS_LABEL, CAMPAIGN_STATUS_TONE, CAMPAIGN_SCOPE_LABEL,
  ITEM_TYPE_LABEL,
  recusalSubjectLabel, recusalSubjectIsUnresolved,
  type CampaignResponse, type DeclarationSubmissionRequest,
  type DisclosedItemRequest, type ItemType, type RecusalResponse,
} from '../../../lib/coi';

/**
 * COI Disclosure Register — /ethics/coi.
 *
 * Three-tab page:
 *   1. Campaigns — annual declaration cycles. Row actions: Open / Close / View.
 *   2. My Declaration — submit a declaration in the currently open campaign.
 *   3. Recusal Log — historical recusals (filterable by committee, user, date).
 *
 * SAMA CG Art.18 + CMA CG Art.41–43 — annual conflict-of-interest
 * declarations. Saudi-native title: "تضارب المصالح" (operator-natural).
 */
export function CoiRegisterPage() {
  const { lang, t } = useI18n();
  const navigate = useNavigate();
  const demoMode = !isAuthenticated();

  type Tab = 'campaigns' | 'declaration' | 'recusals';
  const [tab, setTab] = useState<Tab>('campaigns');

  const [campaigns, setCampaigns] = useState<CampaignResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function loadCampaigns() {
    if (demoMode) { setCampaigns([]); return; }
    setLoading(true);
    setError(null);
    try {
      const r = await coiApi.listCampaigns();
      setCampaigns(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الدورات' : 'Failed to load campaigns'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { loadCampaigns(); /* eslint-disable-next-line */ }, []);

  const openCampaign = campaigns.find(c => c.status === 'OPEN') ?? null;

  async function doOpen(id: number) {
    setBusyId(id);
    try {
      await coiApi.openCampaign(id);
      await loadCampaigns();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح الدورة' : 'Failed to open campaign'));
    } finally {
      setBusyId(null);
    }
  }

  async function doClose(id: number) {
    setBusyId(id);
    try {
      await coiApi.closeCampaign(id);
      await loadCampaigns();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إغلاق الدورة' : 'Failed to close campaign'));
    } finally {
      setBusyId(null);
    }
  }

  // ─── Campaigns tab columns ──────────────────────────────────────
  const campaignCols: Column<CampaignResponse>[] = [
    {
      header: lang === 'ar' ? 'الرمز' : 'Code', width: '120px',
      cell: c => <code style={{ fontSize: 12, fontWeight: 500 }}>{c.code}</code>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: c => <span style={{ fontWeight: 500 }}>{lang === 'ar' ? c.titleAr : c.titleEn}</span>,
    },
    {
      header: lang === 'ar' ? 'السنة المالية' : 'Fiscal year', width: '110px',
      cell: c => <span style={{ fontSize: 12 }}>{c.fiscalYear}</span>,
    },
    {
      header: lang === 'ar' ? 'النطاق' : 'Scope', width: '160px',
      cell: c => <span style={{ fontSize: 12 }}>{CAMPAIGN_SCOPE_LABEL[c.scope][lang === 'ar' ? 'ar' : 'en']}</span>,
    },
    {
      header: lang === 'ar' ? 'الفتح / الإغلاق' : 'Opens / Closes', width: '180px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {new Date(c.opensOn).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
          {' → '}
          {new Date(c.closesOn).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '110px',
      cell: c => (
        <Badge tone={CAMPAIGN_STATUS_TONE[c.status]}>
          {CAMPAIGN_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'التقدّم' : 'Progress', width: '160px',
      cell: c => {
        const declared = c.declaredCount ?? 0;
        const target = c.targetUserCount ?? 0;
        const pct = target > 0 ? Math.round((declared / target) * 100) : 0;
        return (
          <div style={{ minWidth: 130 }}>
            <Progress
              label={`${declared} / ${target || '—'}`}
              value={pct}
              tone={pct >= 80 ? 'success' : pct >= 40 ? 'primary' : 'accent'}
            />
          </div>
        );
      },
    },
    {
      header: '', width: '260px',
      cell: c => (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {c.status === 'DRAFT' && (
            <Button size="sm" variant="secondary" Icon={PlayCircle}
              disabled={busyId === c.id}
              onClick={(e) => { e.stopPropagation(); doOpen(c.id); }}>
              {t('coi.openCampaign')}
            </Button>
          )}
          {c.status === 'OPEN' && (
            <Button size="sm" variant="secondary" Icon={StopCircle}
              disabled={busyId === c.id}
              onClick={(e) => { e.stopPropagation(); doClose(c.id); }}>
              {t('coi.closeCampaign')}
            </Button>
          )}
          <Button size="sm" variant="ghost" Icon={Eye}
            onClick={(e) => { e.stopPropagation(); navigate(`/ethics/coi/campaigns/${c.id}`); }}>
            {lang === 'ar' ? 'الإقرارات' : 'Declarations'}
          </Button>
        </div>
      ),
    },
  ];

  const declaredTotal = useMemo(
    () => campaigns.reduce((a, c) => a + (c.declaredCount ?? 0), 0), [campaigns]);
  const targetTotal = useMemo(
    () => campaigns.reduce((a, c) => a + (c.targetUserCount ?? 0), 0), [campaigns]);

  return (
    <Shell
      title={t('coi.title')}
      sub={lang === 'ar'
        ? 'سجل تضارب المصالح — الإقرارات السنوية والتنحّيات وفق المادة 18 لحوكمة الشركات (البنك المركزي) والمواد 41–43 (هيئة السوق المالية).'
        : 'Conflict of Interest Register — annual declarations and recusals per SAMA CG Art.18 + CMA CG Art.41–43.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الأخلاقيات والسلوك · المرحلة ١٣' : 'Ethics & Conduct · Sprint 13'}
        title={t('coi.title')}
        sub={lang === 'ar'
          ? `${declaredTotal} إقرار مُسلَّم من إجمالي ${targetTotal || '—'} مستهدف`
          : `${declaredTotal} declarations submitted of ${targetTotal || '—'} targeted`}
      />

      <div style={{ marginBottom: 16 }}>
        <Tabs<Tab>
          active={tab}
          onChange={setTab}
          items={[
            { id: 'campaigns',   label: lang === 'ar' ? 'الدورات' : 'Campaigns',         Icon: Scale,     count: campaigns.length },
            { id: 'declaration', label: t('coi.annualDeclaration'),                       Icon: FileText },
            { id: 'recusals',    label: lang === 'ar' ? 'سجلّ التنحّيات' : 'Recusal Log', Icon: UserMinus },
          ]}
        />
      </div>

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {tab === 'campaigns' && (
        <Card padded>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={campaignCols}
              rows={campaigns}
              empty={<EmptyState
                Icon={Scale}
                title={lang === 'ar' ? 'لا توجد دورات' : 'No campaigns'}
                sub={demoMode
                  ? (lang === 'ar' ? 'سجّل دخولك لعرض الدورات.' : 'Log in to view campaigns.')
                  : (lang === 'ar' ? 'لم تُفتَح أي دورة إقرار بعد.' : 'No declaration cycles created yet.')} />}
            />
          )}
        </Card>
      )}

      {tab === 'declaration' && (
        <DeclarationForm openCampaign={openCampaign} onSubmitted={() => { loadCampaigns(); setTab('campaigns'); }} />
      )}

      {tab === 'recusals' && (
        <RecusalLogPanel />
      )}
    </Shell>
  );
}

// ─── Declaration submission form ────────────────────────────────────

function DeclarationForm({
  openCampaign, onSubmitted,
}: {
  openCampaign: CampaignResponse | null;
  onSubmitted: () => void;
}) {
  const { lang, t } = useI18n();
  const [employeeNo, setEmployeeNo] = useState('');
  const [fullNameEn, setFullNameEn] = useState('');
  const [fullNameAr, setFullNameAr] = useState('');
  const [positionTitleEn, setPositionTitleEn] = useState('');
  const [positionTitleAr, setPositionTitleAr] = useState('');
  const [departmentEn, setDepartmentEn] = useState('');
  const [departmentAr, setDepartmentAr] = useState('');
  const [hasConflict, setHasConflict] = useState(false);
  const [hasPotentialConflict, setHasPotentialConflict] = useState(false);
  const [attestationText, setAttestationText] = useState(lang === 'ar'
    ? 'أُقرّ بأن المعلومات المُفصَح عنها أعلاه صحيحة وكاملة على حدّ علمي.'
    : 'I attest the information disclosed above is true and complete to the best of my knowledge.');
  const [items, setItems] = useState<DisclosedItemRequest[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function addItem() {
    setItems(prev => [...prev, {
      itemType: 'DIRECTORSHIP',
      counterpartyEn: '',
      counterpartyAr: '',
      isActive: true,
    }]);
  }
  function removeItem(i: number) {
    setItems(prev => prev.filter((_, idx) => idx !== i));
  }
  function updateItem(i: number, patch: Partial<DisclosedItemRequest>) {
    setItems(prev => prev.map((it, idx) => idx === i ? { ...it, ...patch } : it));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!openCampaign) return;
    setSubmitting(true);
    setError(null);
    try {
      const body: DeclarationSubmissionRequest = {
        campaignId: openCampaign.id,
        employeeNo,
        fullNameEn, fullNameAr,
        positionTitleEn, positionTitleAr,
        departmentEn, departmentAr,
        hasConflict, hasPotentialConflict,
        attestationText,
        items,
      };
      await coiApi.submitDeclaration(body);
      onSubmitted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تقديم الإقرار' : 'Failed to submit declaration'));
    } finally {
      setSubmitting(false);
    }
  }

  if (!openCampaign) {
    return (
      <Card padded>
        <EmptyState
          Icon={FileText}
          title={lang === 'ar' ? 'لا توجد دورة مفتوحة حاليًا' : 'No campaign is currently open'}
          sub={lang === 'ar'
            ? 'لا يمكن تقديم إقرار خارج دورة الإقرار السنوي.'
            : 'You can only submit a declaration during an open annual cycle.'} />
      </Card>
    );
  }

  const itemTypeOptions = (Object.keys(ITEM_TYPE_LABEL) as ItemType[]).map(it => ({
    value: it, label: ITEM_TYPE_LABEL[it][lang === 'ar' ? 'ar' : 'en'],
  }));

  return (
    <form onSubmit={submit}>
      <Card padded title={lang === 'ar' ? `الإقرار في الدورة: ${openCampaign.titleAr}` : `Declaration in: ${openCampaign.titleEn}`}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
          <Input label={lang === 'ar' ? 'رقم الموظف' : 'Employee number'}
            value={employeeNo} onChange={e => setEmployeeNo(e.target.value)} required />
          <Input label={lang === 'ar' ? 'الاسم (EN)' : 'Full name (EN)'}
            value={fullNameEn} onChange={e => setFullNameEn(e.target.value)} required />
          <Input label={lang === 'ar' ? 'الاسم (AR)' : 'Full name (AR)'}
            value={fullNameAr} onChange={e => setFullNameAr(e.target.value)} required dir="rtl" />
          <Input label={lang === 'ar' ? 'المسمى الوظيفي (EN)' : 'Position title (EN)'}
            value={positionTitleEn} onChange={e => setPositionTitleEn(e.target.value)} required />
          <Input label={lang === 'ar' ? 'المسمى الوظيفي (AR)' : 'Position title (AR)'}
            value={positionTitleAr} onChange={e => setPositionTitleAr(e.target.value)} required dir="rtl" />
          <Input label={lang === 'ar' ? 'الإدارة (EN)' : 'Department (EN)'}
            value={departmentEn} onChange={e => setDepartmentEn(e.target.value)} required />
          <Input label={lang === 'ar' ? 'الإدارة (AR)' : 'Department (AR)'}
            value={departmentAr} onChange={e => setDepartmentAr(e.target.value)} required dir="rtl" />
        </div>

        <div style={{ display: 'flex', gap: 24, marginTop: 16, flexWrap: 'wrap' }}>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <input type="checkbox" checked={hasConflict} onChange={e => setHasConflict(e.target.checked)} />
            {lang === 'ar' ? 'يوجد تضارب فعلي' : 'I have an actual conflict'}
          </label>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
            <input type="checkbox" checked={hasPotentialConflict} onChange={e => setHasPotentialConflict(e.target.checked)} />
            {lang === 'ar' ? 'يوجد تضارب محتمل' : 'I have a potential conflict'}
          </label>
        </div>

        <div style={{ marginTop: 16 }}>
          <Textarea label={lang === 'ar' ? 'نص الإقرار' : 'Attestation text'}
            value={attestationText} onChange={e => setAttestationText(e.target.value)}
            rows={3} required />
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card padded
          title={lang === 'ar' ? 'الإفصاحات' : 'Disclosed items'}
          action={<Button size="sm" variant="secondary" Icon={Plus} onClick={addItem}>
            {t('coi.addDisclosure')}
          </Button>}>
          {items.length === 0 ? (
            <InlineEmpty>
              {lang === 'ar'
                ? 'لا توجد إفصاحات. أضف صفًا لكل جهة ذات صلة.'
                : 'No disclosures yet. Add a row for each relationship to disclose.'}
            </InlineEmpty>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {items.map((it, i) => (
                <div key={i} className="coi-disclosure-row" style={{
                  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: 12,
                  background: 'var(--surface-1)',
                }}>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10 }}>
                    <Select label={lang === 'ar' ? 'النوع' : 'Type'}
                      value={it.itemType}
                      onChange={e => updateItem(i, { itemType: e.target.value as ItemType })}
                      options={itemTypeOptions} />
                    <Input label={lang === 'ar' ? `${t('coi.counterparty')} (EN)` : `${t('coi.counterparty')} (EN)`}
                      value={it.counterpartyEn} onChange={e => updateItem(i, { counterpartyEn: e.target.value })} required />
                    <Input label={lang === 'ar' ? `${t('coi.counterparty')} (AR)` : `${t('coi.counterparty')} (AR)`}
                      value={it.counterpartyAr} onChange={e => updateItem(i, { counterpartyAr: e.target.value })} required dir="rtl" />
                    <Input label={t('coi.financialValue')} type="number" step="0.01"
                      value={it.financialValueSar ?? ''}
                      onChange={e => updateItem(i, { financialValueSar: e.target.value ? Number(e.target.value) : undefined })} />
                    <Input label={lang === 'ar' ? 'تاريخ البدء' : 'Start date'} type="date"
                      value={it.startDate ?? ''} onChange={e => updateItem(i, { startDate: e.target.value || undefined })} />
                    <Input label={lang === 'ar' ? 'تاريخ الانتهاء' : 'End date'} type="date"
                      value={it.endDate ?? ''} onChange={e => updateItem(i, { endDate: e.target.value || undefined })} />
                  </div>
                  <div style={{ marginTop: 10, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                    <Textarea label={lang === 'ar' ? 'وصف العلاقة (EN)' : 'Relationship description (EN)'}
                      value={it.relationshipDescriptionEn ?? ''} rows={2}
                      onChange={e => updateItem(i, { relationshipDescriptionEn: e.target.value || undefined })} />
                    <Textarea label={lang === 'ar' ? 'وصف العلاقة (AR)' : 'Relationship description (AR)'}
                      value={it.relationshipDescriptionAr ?? ''} rows={2} dir="rtl"
                      onChange={e => updateItem(i, { relationshipDescriptionAr: e.target.value || undefined })} />
                    <Textarea label={`${t('coi.mitigationAction')} (EN)`}
                      value={it.mitigationActionEn ?? ''} rows={2}
                      onChange={e => updateItem(i, { mitigationActionEn: e.target.value || undefined })} />
                    <Textarea label={`${t('coi.mitigationAction')} (AR)`}
                      value={it.mitigationActionAr ?? ''} rows={2} dir="rtl"
                      onChange={e => updateItem(i, { mitigationActionAr: e.target.value || undefined })} />
                  </div>
                  <div style={{ marginTop: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                      <input type="checkbox" checked={it.isActive ?? false}
                        onChange={e => updateItem(i, { isActive: e.target.checked })} />
                      {lang === 'ar' ? 'علاقة قائمة حاليًا' : 'Currently active'}
                    </label>
                    <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => removeItem(i)}>
                      {lang === 'ar' ? 'إزالة' : 'Remove'}
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginTop: 12 }}>{error}</div>
      )}

      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="submit" variant="primary" Icon={Send} disabled={submitting}>
          {t('coi.submitDeclaration')}
        </Button>
      </div>
    </form>
  );
}

// ─── Recusal log panel ──────────────────────────────────────────────

function RecusalLogPanel() {
  const { lang, t } = useI18n();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [userId, setUserId] = useState('');
  const [committee, setCommittee] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [rows, setRows] = useState<RecusalResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<RecusalResponse | null>(null);

  // Record a recusal applied at a meeting (SAMA CG Art.18 / CMA CG Art.41–43).
  // listRecusalsByUser already backed this panel; this closes the write path.
  const [showRecord, setShowRecord] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rUser, setRUser] = useState('');
  const [rCommittee, setRCommittee] = useState('');
  const [rMeeting, setRMeeting] = useState('');
  const [rDate, setRDate] = useState('');
  const [rDecl, setRDecl] = useState('');
  const [rReasonEn, setRReasonEn] = useState('');
  const [rReasonAr, setRReasonAr] = useState('');
  const [rAgenda, setRAgenda] = useState('');

  function openRecord() {
    setRUser(userId || ''); setRCommittee(committee || '');
    setRMeeting(''); setRDate(''); setRDecl(''); setRReasonEn(''); setRReasonAr(''); setRAgenda('');
    setShowRecord(true);
  }

  async function submitRecord() {
    const uid = parseInt(rUser, 10);
    if (!Number.isFinite(uid) || !rCommittee.trim() || !rMeeting.trim() || !rDate
        || !rReasonEn.trim() || !rReasonAr.trim()) return;
    setSaving(true);
    try {
      await coiApi.recordRecusal({
        userId: uid,
        declarationId: rDecl ? parseInt(rDecl, 10) : undefined,
        committeeCode: rCommittee.trim(),
        meetingReference: rMeeting.trim(),
        recusalDate: `${rDate}T00:00:00`,
        recusalReasonEn: rReasonEn.trim(),
        recusalReasonAr: rReasonAr.trim(),
        agendaItems: rAgenda.trim() || undefined,
      });
      toast.success(lang === 'ar' ? 'سُجِّل التنحّي' : 'Recusal recorded');
      setShowRecord(false);
      setUserId(String(uid)); // pivot the filter to the recorded user
      const r = await coiApi.listRecusalsByUser(uid).catch(() => [] as RecusalResponse[]);
      setRows(r);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message
        : (lang === 'ar' ? 'تعذّر تسجيل التنحّي' : 'Failed to record recusal'));
    } finally { setSaving(false); }
  }

  async function loadByUser() {
    if (demoMode || !userId) { setRows([]); return; }
    const uid = parseInt(userId, 10);
    if (!Number.isFinite(uid)) {
      setError(lang === 'ar' ? 'مُعرّف مستخدم غير صالح' : 'Invalid user id');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const r = await coiApi.listRecusalsByUser(uid);
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل التنحّيات' : 'Failed to load recusals'));
    } finally {
      setLoading(false);
    }
  }

  const filtered = useMemo(() => rows.filter(r => {
    if (committee && !r.committeeCode.toLowerCase().includes(committee.toLowerCase())) return false;
    if (fromDate && r.recusalDate < fromDate) return false;
    if (toDate && r.recusalDate > toDate) return false;
    return true;
  }), [rows, committee, fromDate, toDate]);

  const cols: Column<RecusalResponse>[] = [
    // F2-244 — the recusal log had NO person column. Its whole subject is a director, committee
    // member or senior executive who withdrew from a decision (SAMA CG Art.18 / CMA CG Art.41–43),
    // and the only place a name was even attempted was the detail modal, behind a `userNameEn`
    // guard the wire never satisfied. An unresolvable id is DISCLOSED rather than blanked: 11 of
    // the 28 live COI rows in dev and prod name a user that does not exist, and a blank would make
    // exactly those rows the tidiest-looking ones (F2-197).
    {
      header: t('coi.employee'), width: '200px',
      cell: r => (
        <span
          role={recusalSubjectIsUnresolved(r) ? 'status' : undefined}
          title={recusalSubjectIsUnresolved(r)
            ? (lang === 'ar'
                ? 'سُجِّل هذا التنحّي مقابل مُعرّف مستخدم لا يُطابق أي مستخدم نشط في هذه المنشأة.'
                : 'This recusal was recorded against a user id that matches no active user in this organisation.')
            : undefined}
          style={{
            fontSize: 12,
            color: recusalSubjectIsUnresolved(r) ? 'var(--danger)' : 'var(--ink-1)',
          }}>
          {recusalSubjectLabel(r, lang)}
        </span>
      ),
    },
    {
      header: t('coi.committeeCode'), width: '160px',
      cell: r => <code style={{ fontSize: 12 }}>{r.committeeCode}</code>,
    },
    {
      header: t('coi.meetingMinutes'),
      cell: r => <span style={{ fontSize: 13 }}>{r.meetingReference}</span>,
    },
    {
      header: lang === 'ar' ? 'التاريخ' : 'Date', width: '120px',
      cell: r => <span style={{ fontSize: 12 }}>
        {new Date(r.recusalDate).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
      </span>,
    },
    {
      header: lang === 'ar' ? 'السبب' : 'Reason',
      cell: r => <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>
        {lang === 'ar' ? r.recusalReasonAr : r.recusalReasonEn}
      </span>,
    },
    {
      header: '', width: '90px',
      cell: r => (
        <Button size="sm" variant="ghost" Icon={Eye}
          onClick={() => setSelected(r)}>
          {lang === 'ar' ? 'عرض' : 'View'}
        </Button>
      ),
    },
  ];

  return (
    <>
      <Card padded title={lang === 'ar' ? 'فلاتر السجل' : 'Log filters'}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12, alignItems: 'end' }}>
          <Input label={`${t('coi.employee')} ID`} type="number"
            value={userId} onChange={e => setUserId(e.target.value)} />
          <Input label={t('coi.committeeCode')}
            value={committee} onChange={e => setCommittee(e.target.value)} />
          <Input label={lang === 'ar' ? 'من تاريخ' : 'From'} type="date"
            value={fromDate} onChange={e => setFromDate(e.target.value)} />
          <Input label={lang === 'ar' ? 'إلى تاريخ' : 'To'} type="date"
            value={toDate} onChange={e => setToDate(e.target.value)} />
          <Button size="md" variant="primary" onClick={loadByUser}>
            {lang === 'ar' ? 'بحث' : 'Search'}
          </Button>
          <Button size="md" variant="secondary" Icon={UserMinus} onClick={openRecord}>
            {lang === 'ar' ? 'تسجيل تنحٍّ' : 'Record recusal'}
          </Button>
        </div>
      </Card>

      <div style={{ marginTop: 14 }}>
        <Card padded>
          {error && <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 8 }}>{error}</div>}
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={cols}
              rows={filtered}
              empty={<EmptyState
                Icon={UserMinus}
                title={lang === 'ar' ? 'لا توجد تنحّيات' : 'No recusals'}
                sub={!userId
                  ? (lang === 'ar' ? 'أدخل مُعرّف المستخدم وابدأ البحث.' : 'Enter a user ID to load their recusals.')
                  : (lang === 'ar' ? 'لا توجد سجلات مطابقة للفلتر.' : 'No records match the current filter.')} />}
            />
          )}
        </Card>
      </div>

      <Modal
        open={showRecord}
        title={lang === 'ar' ? 'تسجيل تنحٍّ' : 'Record a recusal'}
        onClose={() => setShowRecord(false)}
        size="md"
      >
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
          <Input label={`${t('coi.employee')} ID *`} type="number" value={rUser} onChange={e => setRUser(e.target.value)} />
          <Input label={lang === 'ar' ? 'رمز اللجنة *' : 'Committee code *'} value={rCommittee} onChange={e => setRCommittee(e.target.value)} placeholder="BOARD-RISK" />
          <Input label={lang === 'ar' ? 'مرجع الاجتماع *' : 'Meeting reference *'} value={rMeeting} onChange={e => setRMeeting(e.target.value)} placeholder="BRC-2026-04" />
          <Input label={lang === 'ar' ? 'تاريخ التنحّي *' : 'Recusal date *'} type="date" value={rDate} onChange={e => setRDate(e.target.value)} />
          <Input label={lang === 'ar' ? 'مُعرّف الإقرار (اختياري)' : 'Declaration ID (optional)'} type="number" value={rDecl} onChange={e => setRDecl(e.target.value)} />
          <Input label={lang === 'ar' ? 'بنود جدول الأعمال (اختياري)' : 'Agenda items (optional)'} value={rAgenda} onChange={e => setRAgenda(e.target.value)} placeholder={lang === 'ar' ? 'البند 4: اعتماد تمويل' : 'Item 4: facility approval'} />
        </div>
        <div style={{ marginTop: 12, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
          <Textarea label={lang === 'ar' ? 'سبب التنحّي (إنجليزي) *' : 'Recusal reason (English) *'} rows={2} value={rReasonEn} onChange={e => setRReasonEn(e.target.value)} />
          <Textarea label={lang === 'ar' ? 'سبب التنحّي (عربي) *' : 'Recusal reason (Arabic) *'} rows={2} value={rReasonAr} onChange={e => setRReasonAr(e.target.value)} dir="rtl" />
        </div>
        <div style={{ marginTop: 14, display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="secondary" onClick={() => setShowRecord(false)}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</Button>
          <Button variant="primary" onClick={submitRecord}
            disabled={saving || !rUser || !rCommittee.trim() || !rMeeting.trim() || !rDate || !rReasonEn.trim() || !rReasonAr.trim()}>
            {saving ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…') : (lang === 'ar' ? 'تسجيل التنحّي' : 'Record recusal')}
          </Button>
        </div>
      </Modal>

      <Modal
        open={selected !== null}
        title={selected
          ? (lang === 'ar' ? `تنحٍّ — ${selected.committeeCode}` : `Recusal — ${selected.committeeCode}`)
          : ''}
        onClose={() => setSelected(null)}
        size="md"
      >
        {selected && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13 }}>
            <DetailRow label={t('coi.committeeCode')} value={selected.committeeCode} />
            <DetailRow label={t('coi.meetingMinutes')} value={selected.meetingReference} />
            <DetailRow label={lang === 'ar' ? 'تاريخ التنحّي' : 'Recusal date'}
              value={new Date(selected.recusalDate).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')} />
            <DetailRow label={lang === 'ar' ? 'السبب' : 'Reason'}
              value={lang === 'ar' ? selected.recusalReasonAr : selected.recusalReasonEn} />
            {selected.agendaItems && (
              <DetailRow label={lang === 'ar' ? 'بنود جدول الأعمال' : 'Agenda items'} value={selected.agendaItems} />
            )}
            {/* F2-244 — was `selected.userNameEn && …`, a guard the wire could never satisfy, so
                the panel silently omitted the one person the record is about. Always rendered now,
                with the three states the server distinguishes. */}
            <DetailRow label={t('coi.employee')} value={recusalSubjectLabel(selected, lang)} />
            <DetailRow label={lang === 'ar' ? 'مُعرّف المستخدم' : 'User ID'}
              value={String(selected.userId)} />
          </div>
        )}
      </Modal>
    </>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '160px 1fr', gap: 12 }}>
      <div style={{ color: 'var(--ink-3)', fontWeight: 500 }}>{label}</div>
      <div style={{ color: 'var(--ink-1)' }}>{value}</div>
    </div>
  );
}
