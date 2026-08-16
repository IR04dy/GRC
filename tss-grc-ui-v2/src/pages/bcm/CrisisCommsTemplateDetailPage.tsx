import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft, Save, CheckCircle2, RotateCcw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  getTemplate, createTemplate, updateTemplate, transitionTemplate,
  AUDIENCES, SCENARIO_CATEGORIES, SEVERITY_BANDS,
  AUDIENCE_LABELS, SCENARIO_LABELS, SEVERITY_LABELS, APPROVAL_LABELS,
  parsePlaceholders, allowedTransitions,
  severityTone, approvalTone,
  type CrisisCommsTemplate, type Audience, type ScenarioCategory,
  type SeverityBand, type ApprovalStatus, type TemplateRequest,
} from '../../lib/crisisComms';

/**
 * Slice 9-E — Crisis-Comms template editor + approval state-machine UI.
 *
 * <p>Bilingual side-by-side editor: EN | AR for title / subject / body /
 * SMS body. The approval state machine renders as a horizontal stepper
 * with action buttons enabled only for transitions the backend will
 * accept (state-machine guard mirrors {@link allowedTransitions}).
 *
 * <p>Routes:
 *  - /bcm/comms/templates/new   — create mode
 *  - /bcm/comms/templates/:id   — edit mode (id is the external UUID)
 */
export function CrisisCommsTemplateDetailPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isNew = id === 'new' || !id;

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [tpl, setTpl] = useState<CrisisCommsTemplate | null>(null);

  // form state
  const [form, setForm] = useState<TemplateRequest>({
    titleEn: '', titleAr: '',
    audience: 'BOARD', scenarioCategory: 'GENERIC_MAJOR', severityBand: 'HIGH',
    subjectEn: '', subjectAr: '',
    bodyEn: '', bodyAr: '',
    smsBodyEn: '', smsBodyAr: '',
    placeholdersJson: '', regulatoryAnchor: '',
  });

  useEffect(() => {
    if (isNew) return;
    setLoading(true);
    getTemplate(id!)
      .then(t => {
        setTpl(t);
        setForm({
          templateCode: t.templateCode,
          titleEn: t.titleEn, titleAr: t.titleAr,
          audience: t.audience,
          scenarioCategory: t.scenarioCategory,
          severityBand: t.severityBand,
          subjectEn: t.subjectEn, subjectAr: t.subjectAr,
          bodyEn: t.bodyEn, bodyAr: t.bodyAr,
          smsBodyEn: t.smsBodyEn ?? '', smsBodyAr: t.smsBodyAr ?? '',
          placeholdersJson: t.placeholdersJson ?? '',
          regulatoryAnchor: t.regulatoryAnchor ?? '',
        });
      })
      .catch(e => toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التحميل' : 'Failed to load')))
      .finally(() => setLoading(false));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const placeholders = useMemo(
    () => parsePlaceholders(form.placeholdersJson),
    [form.placeholdersJson],
  );

  const canEdit = isNew || (tpl &&
    tpl.approvalStatus !== 'APPROVED_BY_CISO' &&
    tpl.approvalStatus !== 'RETIRED');

  async function save() {
    if (!form.titleEn.trim() || !form.titleAr.trim()) {
      toast.danger(isAr ? 'العنوان مطلوب باللغتين' : 'Title required in both languages');
      return;
    }
    setSaving(true);
    try {
      const saved = isNew
        ? await createTemplate(form)
        : await updateTemplate(id!, form);
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
      if (isNew) {
        navigate(`/bcm/comms/templates/${saved.externalId}`, { replace: true });
      } else {
        setTpl(saved);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed'));
    } finally {
      setSaving(false);
    }
  }

  async function transition(to: ApprovalStatus) {
    if (!tpl) return;
    // F2-223 — CrisisCommsTemplateService stamps approvedByLegalName / approvedByCisoName from
    // the authenticated principal. This is the sign-off CrisisCommsDispatchService refuses to
    // send a crisis cascade without, so its holder may not be a name somebody typed.
    try {
      const updated = await transitionTemplate(tpl.externalId, {
        approvalStatus: to,
      });
      setTpl(updated);
      toast.success(isAr ? 'تمت العملية' : 'Transition applied');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحديث' : 'Failed'));
    }
  }

  const transitions = tpl ? allowedTransitions(tpl.approvalStatus) : [];

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال — الاتصالات' : 'BCM — Crisis Communications'}
        title={isNew
          ? (isAr ? 'قالب جديد' : 'New template')
          : (isAr ? form.titleAr || 'قالب' : form.titleEn || 'Template')}
        sub={tpl?.templateCode}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="ghost" onClick={() => navigate('/bcm/comms/templates')}>
              <ChevronLeft size={16} style={{ marginInlineEnd: 6 }} />
              {isAr ? 'رجوع' : 'Back'}
            </Button>
            {canEdit && (
              <Button variant="primary" onClick={save} disabled={saving}>
                <Save size={16} style={{ marginInlineEnd: 6 }} />
                {isAr ? 'حفظ' : 'Save'}
              </Button>
            )}
          </div>
        }
      />

      {loading && <div style={{ padding: 24 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}

      {!loading && (
        <>
          {/* Approval status row */}
          {tpl && (
            <Card title={isAr ? 'حالة الاعتماد' : 'Approval status'}>
              <div style={{
                display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
              }}>
                <Badge tone={approvalTone(tpl.approvalStatus)}>
                  {APPROVAL_LABELS[tpl.approvalStatus][isAr ? 'ar' : 'en']}
                </Badge>
                {tpl.approvedByLegalName && (
                  <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                    {isAr ? 'الاعتماد القانوني: ' : 'Legal: '}{tpl.approvedByLegalName}
                  </span>
                )}
                {tpl.approvedByCisoName && (
                  <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                    {isAr ? 'اعتماد CISO: ' : 'CISO: '}{tpl.approvedByCisoName}
                  </span>
                )}
                <div style={{ display: 'flex', gap: 8, marginInlineStart: 'auto' }}>
                  {transitions.map(t => (
                    <Button key={t} variant="secondary" onClick={() => transition(t)}>
                      {t === 'IN_REVIEW' || t === 'DRAFT'
                        ? <RotateCcw size={14} style={{ marginInlineEnd: 6 }} />
                        : <CheckCircle2 size={14} style={{ marginInlineEnd: 6 }} />}
                      {APPROVAL_LABELS[t][isAr ? 'ar' : 'en']}
                    </Button>
                  ))}
                </div>
              </div>
            </Card>
          )}

          {/* Metadata */}
          <div style={{ marginTop: 16 }}>
            <Card title={isAr ? 'البيانات الأساسية' : 'Metadata'}>
              <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12,
              }}>
                <Field label={isAr ? 'الجمهور' : 'Audience'}>
                  <select
                    value={form.audience}
                    disabled={!canEdit}
                    onChange={e => setForm({ ...form, audience: e.target.value as Audience })}
                    style={inputStyle}
                  >
                    {AUDIENCES.map(a => (
                      <option key={a} value={a}>{AUDIENCE_LABELS[a][isAr ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </Field>
                <Field label={isAr ? 'فئة السيناريو' : 'Scenario'}>
                  <select
                    value={form.scenarioCategory}
                    disabled={!canEdit}
                    onChange={e => setForm({ ...form, scenarioCategory: e.target.value as ScenarioCategory })}
                    style={inputStyle}
                  >
                    {SCENARIO_CATEGORIES.map(s => (
                      <option key={s} value={s}>{SCENARIO_LABELS[s][isAr ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </Field>
                <Field label={isAr ? 'مستوى الخطورة' : 'Severity'}>
                  <select
                    value={form.severityBand}
                    disabled={!canEdit}
                    onChange={e => setForm({ ...form, severityBand: e.target.value as SeverityBand })}
                    style={inputStyle}
                  >
                    {SEVERITY_BANDS.map(s => (
                      <option key={s} value={s}>{SEVERITY_LABELS[s][isAr ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </Field>
                {tpl && (
                  <Field label={isAr ? 'الخطورة (شارة)' : 'Severity (tag)'}>
                    <Badge tone={severityTone(form.severityBand)}>
                      {SEVERITY_LABELS[form.severityBand][isAr ? 'ar' : 'en']}
                    </Badge>
                  </Field>
                )}
                <Field label={isAr ? 'المرجع التنظيمي' : 'Regulatory anchor'}>
                  <input
                    value={form.regulatoryAnchor ?? ''}
                    disabled={!canEdit}
                    onChange={e => setForm({ ...form, regulatoryAnchor: e.target.value })}
                    placeholder="e.g. PDPL Art.32"
                    style={inputStyle}
                  />
                </Field>
              </div>
            </Card>
          </div>

          {/* Bilingual side-by-side editor */}
          <div style={{ marginTop: 16 }}>
            <Card title={isAr ? 'العنوان والمحتوى' : 'Title & content'}>
              <div style={{
                display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16,
              }}>
                <div>
                  <Field label="Title (English)">
                    <input
                      value={form.titleEn} disabled={!canEdit}
                      onChange={e => setForm({ ...form, titleEn: e.target.value })}
                      style={inputStyle}
                    />
                  </Field>
                  <Field label="Subject (English)">
                    <input
                      value={form.subjectEn} disabled={!canEdit}
                      onChange={e => setForm({ ...form, subjectEn: e.target.value })}
                      style={inputStyle}
                    />
                  </Field>
                  <Field label="Body (English)">
                    <textarea
                      value={form.bodyEn} disabled={!canEdit}
                      onChange={e => setForm({ ...form, bodyEn: e.target.value })}
                      rows={10}
                      style={{ ...inputStyle, fontFamily: 'var(--font-mono)', fontSize: 13 }}
                    />
                  </Field>
                  <Field label="SMS body (English, ≤160 chars)">
                    <textarea
                      value={form.smsBodyEn ?? ''} disabled={!canEdit}
                      onChange={e => setForm({ ...form, smsBodyEn: e.target.value })}
                      rows={3} maxLength={500}
                      style={{ ...inputStyle, fontFamily: 'var(--font-mono)', fontSize: 13 }}
                    />
                  </Field>
                </div>
                <div dir="rtl">
                  <Field label="العنوان (عربي)">
                    <input
                      value={form.titleAr} disabled={!canEdit}
                      onChange={e => setForm({ ...form, titleAr: e.target.value })}
                      style={inputStyle}
                    />
                  </Field>
                  <Field label="الموضوع (عربي)">
                    <input
                      value={form.subjectAr} disabled={!canEdit}
                      onChange={e => setForm({ ...form, subjectAr: e.target.value })}
                      style={inputStyle}
                    />
                  </Field>
                  <Field label="نص الرسالة (عربي)">
                    <textarea
                      value={form.bodyAr} disabled={!canEdit}
                      onChange={e => setForm({ ...form, bodyAr: e.target.value })}
                      rows={10}
                      style={{ ...inputStyle, fontFamily: 'var(--font-mono)', fontSize: 13 }}
                    />
                  </Field>
                  <Field label="نص SMS (عربي، ≤160 حرف)">
                    <textarea
                      value={form.smsBodyAr ?? ''} disabled={!canEdit}
                      onChange={e => setForm({ ...form, smsBodyAr: e.target.value })}
                      rows={3} maxLength={500}
                      style={{ ...inputStyle, fontFamily: 'var(--font-mono)', fontSize: 13 }}
                    />
                  </Field>
                </div>
              </div>
            </Card>
          </div>

          {/* Placeholders panel */}
          {placeholders.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Card title={isAr ? 'المتغيرات المطلوبة' : 'Required placeholders'}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {placeholders.map(p => (
                    <div key={p.key} style={{
                      padding: '6px 10px',
                      border: '1px solid var(--border)',
                      borderRadius: 6,
                      background: 'var(--surface-2)',
                      fontFamily: 'var(--font-mono)',
                      fontSize: 12,
                    }}>
                      {`{{${p.key}}}`}
                      {p.type && (
                        <span style={{ color: 'var(--ink-3)', marginInlineStart: 6 }}>
                          ({p.type})
                        </span>
                      )}
                      {p.required && (
                        <span style={{ color: 'var(--danger)', marginInlineStart: 4 }}>*</span>
                      )}
                    </div>
                  ))}
                </div>
              </Card>
            </div>
          )}
        </>
      )}
    </Shell>
  );
}

const inputStyle: CSSProperties = {
  width: '100%', padding: 8,
  border: '1px solid var(--border)', borderRadius: 6,
  background: 'var(--surface)', color: 'var(--ink-1)',
};

function Field(props: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <label style={{
        display: 'block', fontSize: 12, color: 'var(--ink-3)', marginBottom: 4,
      }}>
        {props.label}
      </label>
      {props.children}
    </div>
  );
}
