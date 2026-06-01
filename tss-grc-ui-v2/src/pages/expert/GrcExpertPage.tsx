// GRC Expert — unified SME surface.
// Browse every framework's controls → sub-controls → recommended evidence
// → live collection status → raise an evidence request in one click.

import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, FileCheck2, AlertCircle, Clock, X, type LucideIcon } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Modal } from '../../components/ui/Modal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError, adminApi, departmentsApi, type AdminUserDto, type DepartmentDto } from '../../lib/api';
import {
  listExpertCatalog,
  raiseEvidenceRequest,
  FAMILY_LABELS,
  COVERAGE_TONE,
  COVERAGE_LABEL,
  type ExpertFramework,
  type ExpertControl,
  type ExpertSubControl,
  type ExpertFamily,
  type RaiseEvidenceRequestInput,
} from '../../lib/grcExpert';

// Families shown as tabs (in order); OTHER appears only when data has it.
const PRIMARY_FAMILIES: ExpertFamily[] = ['NCA', 'NDI', 'PDPL', 'BCM', 'ERM', 'ESG'];

// ─── Page ────────────────────────────────────────────────────────────────────

export function GrcExpertPage() {
  const { lang, t } = useI18n();
  const toast = useToast();

  const [frameworks, setFrameworks] = useState<ExpertFramework[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Selected state
  const [activeFamily, setActiveFamily] = useState<ExpertFamily>('NCA');
  const [selectedFwId, setSelectedFwId] = useState<number | null>(null);
  const [expandedControls, setExpandedControls] = useState<Set<number>>(new Set());

  // Collect-evidence dialog
  const [collectTarget, setCollectTarget] = useState<ExpertSubControl | null>(null);
  const [collectTitle, setCollectTitle] = useState('');
  const [collectDesc, setCollectDesc] = useState('');
  const [collectPriority, setCollectPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM');
  const [collectAssignee, setCollectAssignee] = useState<number | ''>('');
  const [collectDept, setCollectDept] = useState<number | ''>('');
  const [collectDue, setCollectDue] = useState('');
  const [collecting, setCollecting] = useState(false);

  // Directory for the "request from" pickers — a request must name an accountable
  // owner (person and/or department), not be a fire-and-forget button.
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const [departments, setDepartments] = useState<DepartmentDto[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listExpertCatalog();
      setFrameworks(data);
      // Auto-select first framework in the default family
      const first = data.find(f => f.family === 'NCA');
      if (first) setSelectedFwId(first.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الأطر' : 'Failed to load frameworks'));
    } finally {
      setLoading(false);
    }
  }, [lang]);

  useEffect(() => { void load(); }, [load]);

  // Load the assignee + department directories once for the request pickers.
  useEffect(() => {
    adminApi.users(0, 200).then(p => setUsers(p.content.filter(u => u.isActive))).catch(() => setUsers([]));
    departmentsApi.list().then(setDepartments).catch(() => setDepartments([]));
  }, []);

  const userLabel = (u: AdminUserDto) =>
    (lang === 'ar' ? (u.fullNameAr || u.fullNameEn) : (u.fullNameEn || u.fullNameAr)) || u.username;
  const deptLabel = (d: DepartmentDto) => (lang === 'ar' ? (d.nameAr || d.nameEn) : d.nameEn);

  // When family changes, auto-select the first framework in that family
  useEffect(() => {
    const first = frameworks.find(f => f.family === activeFamily);
    setSelectedFwId(first?.id ?? null);
    setExpandedControls(new Set());
  }, [activeFamily, frameworks]);

  // ── KPI totals across selected family ──────────────────────────────────────
  const familyFrameworks = frameworks.filter(f => f.family === activeFamily);
  const totalSubControls = familyFrameworks.reduce((s, f) => s + f.subControlCount, 0);
  const totalCovered = familyFrameworks.reduce((s, f) => s + f.coveredCount, 0);
  const totalPartial = familyFrameworks.reduce((s, f) => s + f.partialCount, 0);
  const totalGap = familyFrameworks.reduce((s, f) => s + f.gapCount, 0);
  const totalFrameworks = frameworks.length;

  // ── Visible families (always show primaries + OTHER if present) ─────────────
  const hasOther = frameworks.some(f => f.family === 'OTHER');
  const visibleFamilies: ExpertFamily[] = hasOther
    ? [...PRIMARY_FAMILIES, 'OTHER']
    : PRIMARY_FAMILIES;

  // ── Selected framework ──────────────────────────────────────────────────────
  const selectedFw = frameworks.find(f => f.id === selectedFwId) ?? null;

  function toggleControl(controlId: number) {
    setExpandedControls(prev => {
      const next = new Set(prev);
      if (next.has(controlId)) next.delete(controlId);
      else next.add(controlId);
      return next;
    });
  }

  function openCollect(sc: ExpertSubControl) {
    setCollectTarget(sc);
    setCollectTitle(`Evidence: ${sc.code} ${sc.nameEn}`);
    setCollectDesc(sc.evidenceGuidance ?? '');
    setCollectPriority('MEDIUM');
    setCollectAssignee('');
    setCollectDept('');
    const due = new Date(); due.setDate(due.getDate() + 14);   // default 2-week turnaround
    setCollectDue(due.toISOString().slice(0, 10));
  }

  function closeCollect() {
    setCollectTarget(null);
    setCollectTitle('');
    setCollectDesc('');
    setCollectPriority('MEDIUM');
    setCollectAssignee('');
    setCollectDept('');
    setCollectDue('');
  }

  // A request must be routed to a person and/or a department — never anonymous.
  const canSubmitCollect = !!collectTitle.trim() && (collectAssignee !== '' || collectDept !== '');

  async function submitCollect() {
    if (!collectTarget || !canSubmitCollect) return;
    setCollecting(true);
    try {
      const input: RaiseEvidenceRequestInput = {
        titleEn: collectTitle.trim() || `Evidence: ${collectTarget.code}`,
        descriptionEn: collectDesc.trim() || undefined,
        subControlId: collectTarget.id,
        priority: collectPriority,
        assignedToId: collectAssignee === '' ? undefined : collectAssignee,
        departmentId: collectDept === '' ? undefined : collectDept,
        dueDate: collectDue || undefined,
      };
      await raiseEvidenceRequest(input);
      toast.success(
        lang === 'ar'
          ? 'تم رفع طلب الدليل بنجاح'
          : 'Evidence request raised successfully'
      );
      closeCollect();
      // Best-effort refresh to update coverage counts
      void load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر رفع الطلب' : 'Failed to raise request'));
    } finally {
      setCollecting(false);
    }
  }

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'الإطار المرجعي للحوكمة والمخاطر والامتثال' : 'GRC Reference Catalog'}
        title={lang === 'ar' ? 'خبير الحوكمة والمخاطر والامتثال' : t('expert.title')}
        sub={lang === 'ar'
          ? 'تصفّح الضوابط والضوابط الفرعية لكل إطار تنظيمي — اطّلع على الدليل الموصى به والحالة الفعلية لجمع الأدلة'
          : t('expert.subtitle')}
      />

      {/* ── KPI strip ── */}
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 20 }}>
        <KpiTile
          label={lang === 'ar' ? 'إجمالي الأطر' : t('expert.kpi.frameworks')}
          value={totalFrameworks}
          tone="primary"
        />
        <KpiTile
          label={lang === 'ar' ? 'الضوابط الفرعية' : t('expert.kpi.subControls')}
          value={totalSubControls}
          tone="neutral"
        />
        <KpiTile
          label={lang === 'ar' ? 'مغطّاة' : t('expert.kpi.covered')}
          value={totalCovered}
          tone="success"
        />
        <KpiTile
          label={lang === 'ar' ? 'جزئية' : t('expert.kpi.partial')}
          value={totalPartial}
          tone="warning"
        />
        <KpiTile
          label={lang === 'ar' ? 'فجوات' : t('expert.kpi.gaps')}
          value={totalGap}
          tone="danger"
        />
      </div>

      {/* ── Family tabs ── */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 20, flexWrap: 'wrap' }}>
        {visibleFamilies.map(fam => (
          <button
            key={fam}
            type="button"
            onClick={() => setActiveFamily(fam)}
            style={{
              padding: '6px 18px',
              borderRadius: 20,
              fontSize: 13,
              fontWeight: 600,
              border: '1.5px solid',
              borderColor: activeFamily === fam ? 'var(--primary)' : 'var(--card-border)',
              background: activeFamily === fam ? 'var(--primary)' : 'var(--surface-1)',
              color: activeFamily === fam ? '#fff' : 'var(--ink-2)',
              cursor: 'pointer',
              transition: 'all 0.15s',
            }}
          >
            {FAMILY_LABELS[fam][lang === 'ar' ? 'ar' : 'en']}
          </button>
        ))}
      </div>

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          <AlertCircle size={14} style={{ marginInlineEnd: 6, verticalAlign: 'middle' }} />
          {error}
        </div>
      )}

      {loading && (
        <p style={{ fontSize: 13, opacity: 0.6 }}>
          {lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}
        </p>
      )}

      {!loading && !error && (
        <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16, alignItems: 'start' }}>

          {/* ── Left: framework list ── */}
          <Card padded={false} title={lang === 'ar' ? 'الأطر' : t('expert.frameworks')}>
            <div style={{ maxHeight: '70vh', overflowY: 'auto' }}>
              {familyFrameworks.length === 0 && (
                <div style={{ padding: 16, fontSize: 13, opacity: 0.6 }}>
                  {lang === 'ar' ? 'لا توجد أطر في هذا المجال' : 'No frameworks in this family'}
                </div>
              )}
              {familyFrameworks.map(fw => (
                <button
                  key={fw.id}
                  type="button"
                  onClick={() => { setSelectedFwId(fw.id); setExpandedControls(new Set()); }}
                  style={{
                    width: '100%',
                    textAlign: lang === 'ar' ? 'right' : 'left',
                    padding: '10px 14px',
                    border: 'none',
                    borderBottom: '1px solid var(--card-border)',
                    background: selectedFwId === fw.id ? 'var(--primary-wash, rgba(var(--primary-rgb,37,99,235),0.08))' : 'transparent',
                    cursor: 'pointer',
                    transition: 'background 0.12s',
                    display: 'block',
                  }}
                >
                  <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4, color: selectedFwId === fw.id ? 'var(--primary)' : 'var(--ink-1)' }}>
                    <span style={{ opacity: 0.7, marginInlineEnd: 4 }}>{fw.code}</span>
                    {lang === 'ar' ? fw.nameAr : fw.nameEn}
                  </div>
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                    <MiniChip count={fw.subControlCount} tone="neutral"
                      label={lang === 'ar' ? 'فرعية' : 'sub-ctrls'} />
                    {fw.coveredCount > 0 && (
                      <MiniChip count={fw.coveredCount} tone="success"
                        label={lang === 'ar' ? 'مغطّى' : 'covered'} />
                    )}
                    {fw.partialCount > 0 && (
                      <MiniChip count={fw.partialCount} tone="warning"
                        label={lang === 'ar' ? 'جزئي' : 'partial'} />
                    )}
                    {fw.gapCount > 0 && (
                      <MiniChip count={fw.gapCount} tone="danger"
                        label={lang === 'ar' ? 'فجوة' : 'gap'} />
                    )}
                  </div>
                </button>
              ))}
            </div>
          </Card>

          {/* ── Right: controls + sub-controls ── */}
          <div>
            {!selectedFw && (
              <Card padded>
                <p style={{ fontSize: 13, opacity: 0.6, textAlign: 'center', padding: '24px 0' }}>
                  {lang === 'ar' ? 'اختر إطاراً من القائمة' : 'Select a framework from the list'}
                </p>
              </Card>
            )}

            {selectedFw && (
              <>
                {/* Framework header */}
                <div style={{ marginBottom: 12 }}>
                <Card padded>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.8, opacity: 0.5, marginBottom: 2 }}>
                        {FAMILY_LABELS[selectedFw.family][lang === 'ar' ? 'ar' : 'en']}
                        {selectedFw.version && ` · ${selectedFw.version}`}
                      </div>
                      <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 4 }}>
                        {lang === 'ar' ? selectedFw.nameAr : selectedFw.nameEn}
                      </div>
                      {selectedFw.descriptionEn && lang !== 'ar' && (
                        <div style={{ fontSize: 12, opacity: 0.7 }}>{selectedFw.descriptionEn}</div>
                      )}
                    </div>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <Badge tone="neutral">{selectedFw.controlCount} {lang === 'ar' ? 'ضابط' : 'controls'}</Badge>
                      <Badge tone="neutral">{selectedFw.subControlCount} {lang === 'ar' ? 'فرعي' : 'sub-ctrls'}</Badge>
                      <Badge tone="success">{selectedFw.coveredCount} {lang === 'ar' ? 'مغطّى' : 'covered'}</Badge>
                      {selectedFw.partialCount > 0 && (
                        <Badge tone="warning">{selectedFw.partialCount} {lang === 'ar' ? 'جزئي' : 'partial'}</Badge>
                      )}
                      {selectedFw.gapCount > 0 && (
                        <Badge tone="danger">{selectedFw.gapCount} {lang === 'ar' ? 'فجوة' : 'gap'}</Badge>
                      )}
                    </div>
                  </div>
                </Card>
                </div>

                {/* Controls accordion */}
                {selectedFw.controls.map(ctrl => (
                  <ControlAccordion
                    key={ctrl.id}
                    control={ctrl}
                    expanded={expandedControls.has(ctrl.id)}
                    onToggle={() => toggleControl(ctrl.id)}
                    lang={lang}
                    onCollect={openCollect}
                    t={t}
                  />
                ))}

                {selectedFw.controls.length === 0 && (
                  <Card padded>
                    <p style={{ fontSize: 13, opacity: 0.6, textAlign: 'center', padding: '24px 0' }}>
                      {lang === 'ar' ? 'لا توجد ضوابط في هذا الإطار بعد' : 'No controls loaded for this framework yet'}
                    </p>
                  </Card>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* ── Collect evidence dialog ── */}
      {collectTarget && (
        <Modal
          open
          title={lang === 'ar' ? 'رفع طلب دليل' : t('expert.collect.title')}
          onClose={closeCollect}
          size="md"
          footer={
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button variant="ghost" onClick={closeCollect}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button
                variant="primary"
                onClick={() => { void submitCollect(); }}
                disabled={collecting || !canSubmitCollect}
                Icon={FileCheck2}
              >
                {collecting
                  ? (lang === 'ar' ? 'جارٍ الرفع…' : 'Raising…')
                  : (lang === 'ar' ? 'رفع الطلب' : t('expert.collect.submit'))}
              </Button>
            </div>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {/* Sub-control reference */}
            <div style={{
              padding: '8px 12px',
              background: 'var(--surface-2)',
              borderRadius: 6,
              fontSize: 12,
            }}>
              <span style={{ fontWeight: 700 }}>{collectTarget.code}</span>
              {' · '}
              <span style={{ opacity: 0.8 }}>
                {lang === 'ar' ? collectTarget.nameAr : collectTarget.nameEn}
              </span>
            </div>

            <FieldRow label={lang === 'ar' ? 'عنوان الطلب *' : t('expert.collect.titleLabel')}>
              <input
                value={collectTitle}
                onChange={e => setCollectTitle(e.target.value)}
                style={inputStyle}
                maxLength={256}
              />
            </FieldRow>

            <FieldRow label={lang === 'ar' ? 'الوصف / الدليل الموصى به' : t('expert.collect.descLabel')}>
              <textarea
                value={collectDesc}
                onChange={e => setCollectDesc(e.target.value)}
                rows={3}
                style={{ ...inputStyle, resize: 'vertical' }}
              />
            </FieldRow>

            {/* Who is this requested from — a person and/or a department (required). */}
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', marginTop: 4 }}>
              {lang === 'ar' ? 'الطلب موجَّه إلى' : 'Request from'}
              <span style={{ color: 'var(--danger-fg, #c0392b)' }}> *</span>
            </div>

            <FieldRow label={lang === 'ar' ? 'المسؤول (شخص)' : 'Assignee (person)'}>
              <select
                value={collectAssignee}
                onChange={e => setCollectAssignee(e.target.value === '' ? '' : Number(e.target.value))}
                style={inputStyle}
              >
                <option value="">{lang === 'ar' ? '— اختر شخصًا —' : '— Select a person —'}</option>
                {users.map(u => <option key={u.id} value={u.id}>{userLabel(u)}</option>)}
              </select>
            </FieldRow>

            <FieldRow label={lang === 'ar' ? 'الإدارة / القسم' : 'Department'}>
              <select
                value={collectDept}
                onChange={e => setCollectDept(e.target.value === '' ? '' : Number(e.target.value))}
                style={inputStyle}
              >
                <option value="">{lang === 'ar' ? '— اختر إدارة —' : '— Select a department —'}</option>
                {departments.map(d => <option key={d.id} value={d.id}>{deptLabel(d)}</option>)}
              </select>
            </FieldRow>

            <div style={{ display: 'flex', gap: 12 }}>
              <div style={{ flex: 1 }}>
                <FieldRow label={lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due date'}>
                  <input
                    type="date"
                    value={collectDue}
                    onChange={e => setCollectDue(e.target.value)}
                    style={inputStyle}
                  />
                </FieldRow>
              </div>
              <div style={{ flex: 1 }}>
                <FieldRow label={lang === 'ar' ? 'الأولوية' : t('expert.collect.priority')}>
                  <select
                    value={collectPriority}
                    onChange={e => setCollectPriority(e.target.value as 'LOW' | 'MEDIUM' | 'HIGH')}
                    style={inputStyle}
                  >
                    <option value="LOW">{lang === 'ar' ? 'منخفضة' : 'Low'}</option>
                    <option value="MEDIUM">{lang === 'ar' ? 'متوسطة' : 'Medium'}</option>
                    <option value="HIGH">{lang === 'ar' ? 'عالية' : 'High'}</option>
                  </select>
                </FieldRow>
              </div>
            </div>

            {!canSubmitCollect && (
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {lang === 'ar'
                  ? 'يجب إسناد الطلب إلى شخص أو إدارة قبل الرفع.'
                  : 'Assign the request to a person or a department before submitting.'}
              </div>
            )}
          </div>
        </Modal>
      )}
    </Shell>
  );
}

// ─── Control accordion ────────────────────────────────────────────────────────

function ControlAccordion({
  control, expanded, onToggle, lang, onCollect, t,
}: {
  control: ExpertControl;
  expanded: boolean;
  onToggle: () => void;
  lang: string;
  onCollect: (sc: ExpertSubControl) => void;
  t: (key: string, fallback?: string) => string;
}) {
  return (
    <div style={{ marginBottom: 8 }}>
      <button
        type="button"
        onClick={onToggle}
        style={{
          width: '100%',
          textAlign: lang === 'ar' ? 'right' : 'left',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '10px 14px',
          background: 'var(--surface-1)',
          border: '1px solid var(--card-border)',
          borderRadius: expanded ? '8px 8px 0 0' : 8,
          cursor: 'pointer',
          transition: 'background 0.12s',
        }}
      >
        {expanded
          ? <ChevronDown size={16} style={{ flexShrink: 0, color: 'var(--primary)' }} />
          : <ChevronRight size={16} style={{ flexShrink: 0, opacity: 0.5 }} />
        }
        <span style={{ fontWeight: 600, fontSize: 13, flex: 1 }}>
          <span style={{ fontFamily: 'var(--mono)', fontSize: 11, opacity: 0.6, marginInlineEnd: 6 }}>
            {control.code}
          </span>
          {lang === 'ar' ? control.nameAr : control.nameEn}
        </span>
        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
          {control.coveredCount > 0 && <MiniChip count={control.coveredCount} tone="success" />}
          {control.partialCount > 0 && <MiniChip count={control.partialCount} tone="warning" />}
          {control.gapCount > 0 && <MiniChip count={control.gapCount} tone="danger" />}
        </div>
      </button>

      {expanded && (
        <div style={{
          border: '1px solid var(--card-border)',
          borderTop: 'none',
          borderRadius: '0 0 8px 8px',
          overflow: 'hidden',
        }}>
          {control.subControls.length === 0 && (
            <div style={{ padding: '12px 16px', fontSize: 12, opacity: 0.6 }}>
              {lang === 'ar' ? 'لا توجد ضوابط فرعية' : 'No sub-controls'}
            </div>
          )}
          {control.subControls.map((sc, idx) => (
            <SubControlRow
              key={sc.id}
              sc={sc}
              lang={lang}
              onCollect={onCollect}
              isLast={idx === control.subControls.length - 1}
              t={t}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Sub-control row ──────────────────────────────────────────────────────────

function SubControlRow({
  sc, lang, onCollect, isLast, t,
}: {
  sc: ExpertSubControl;
  lang: string;
  onCollect: (sc: ExpertSubControl) => void;
  isLast: boolean;
  t: (key: string, fallback?: string) => string;
}) {
  return (
    <div style={{
      padding: '12px 16px',
      borderBottom: isLast ? 'none' : '1px solid var(--card-border)',
      background: 'var(--surface-0, var(--bg))',
      display: 'flex',
      gap: 12,
      alignItems: 'flex-start',
    }}>
      {/* Left: code + name + guidance */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4, flexWrap: 'wrap' }}>
          <span style={{ fontFamily: 'var(--mono)', fontSize: 11, opacity: 0.6 }}>{sc.code}</span>
          <span style={{ fontWeight: 600, fontSize: 13 }}>
            {lang === 'ar' ? sc.nameAr : sc.nameEn}
          </span>
          <Badge tone={COVERAGE_TONE[sc.coverage]}>
            {COVERAGE_LABEL[sc.coverage][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        </div>

        {sc.evidenceGuidance && (
          <div style={{
            fontSize: 12,
            color: 'var(--ink-2)',
            marginBottom: 6,
            padding: '6px 10px',
            background: 'var(--surface-2)',
            borderRadius: 4,
            borderInlineStart: '3px solid var(--primary)',
          }}>
            <span style={{ fontWeight: 700, opacity: 0.6, marginInlineEnd: 4 }}>
              {lang === 'ar' ? 'الدليل الموصى به:' : t('expert.recommendedEvidence', 'Recommended evidence:')}
            </span>
            {lang === 'ar' && sc.evidenceGuidanceAr ? sc.evidenceGuidanceAr : sc.evidenceGuidance}
          </div>
        )}

        {/* Status chips */}
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {sc.approved > 0 && (
            <StatusChip count={sc.approved} tone="success"
              label={lang === 'ar' ? 'معتمد' : 'approved'} />
          )}
          {sc.pendingReview > 0 && (
            <StatusChip count={sc.pendingReview} tone="warning"
              label={lang === 'ar' ? 'قيد المراجعة' : 'pending'} />
          )}
          {sc.draft > 0 && (
            <StatusChip count={sc.draft} tone="neutral"
              label={lang === 'ar' ? 'مسوّدة' : 'draft'} />
          )}
          {sc.expiringSoon > 0 && (
            <StatusChip count={sc.expiringSoon} tone="warning"
              label={lang === 'ar' ? 'ينتهي قريباً' : 'expiring'} Icon={Clock} />
          )}
          {sc.expired > 0 && (
            <StatusChip count={sc.expired} tone="danger"
              label={lang === 'ar' ? 'منتهي' : 'expired'} Icon={X} />
          )}
          {sc.total === 0 && (
            <span style={{ fontSize: 11, opacity: 0.45 }}>
              {lang === 'ar' ? 'لا أدلة مرتبطة بعد' : 'no evidence yet'}
            </span>
          )}
        </div>
      </div>

      {/* Right: collect button */}
      <div style={{ flexShrink: 0 }}>
        <Button
          variant="secondary"
          size="sm"
          Icon={FileCheck2}
          onClick={() => onCollect(sc)}
        >
          {lang === 'ar' ? 'جمع دليل' : t('expert.collectBtn', 'Collect evidence')}
        </Button>
      </div>
    </div>
  );
}

// ─── Helper components ────────────────────────────────────────────────────────

function KpiTile({ label, value, tone }: {
  label: string;
  value: number;
  tone: 'primary' | 'neutral' | 'success' | 'warning' | 'danger';
}) {
  const colors: Record<string, string> = {
    primary: 'var(--primary)',
    neutral: 'var(--ink-2)',
    success: 'var(--success)',
    warning: 'var(--warning)',
    danger: 'var(--danger)',
  };
  return (
    <div style={{
      padding: '12px 20px',
      background: 'var(--surface-1)',
      border: '1px solid var(--card-border)',
      borderRadius: 8,
      minWidth: 100,
      textAlign: 'center',
    }}>
      <div style={{ fontSize: 24, fontWeight: 700, color: colors[tone] ?? 'var(--ink-1)' }}>
        {value.toLocaleString()}
      </div>
      <div style={{ fontSize: 11, opacity: 0.6, marginTop: 2 }}>{label}</div>
    </div>
  );
}

function MiniChip({ count, tone, label }: {
  count: number;
  tone: 'neutral' | 'success' | 'warning' | 'danger';
  label?: string;
}) {
  return (
    <Badge tone={tone}>
      {count}{label ? ` ${label}` : ''}
    </Badge>
  );
}

function StatusChip({ count, tone, label, Icon: IconProp }: {
  count: number;
  tone: 'neutral' | 'success' | 'warning' | 'danger';
  label: string;
  Icon?: LucideIcon;
}) {
  return (
    <Badge tone={tone}>
      {IconProp && <IconProp size={10} />}{' '}
      {count} {label}
    </Badge>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 8px',
  border: '1px solid var(--card-border)',
  borderRadius: 4,
  fontSize: 13,
  width: '100%',
  fontFamily: 'inherit',
  background: 'var(--surface-0, var(--bg))',
  color: 'var(--ink-1)',
};

function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>{label}</span>
      {children}
    </label>
  );
}
