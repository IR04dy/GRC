import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Send } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Input, Textarea } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  fraudApi,
  SAMA_CATEGORY_LABEL, SEVERITY_LABEL, SEVERITY_TONE, DETECTION_SOURCE_LABEL,
  type TypologyResponse, type CreateCaseRequest, type DetectionSource,
} from '../../lib/fraud';

/**
 * Fraud Case intake — /fraud/cases/new.
 *
 * Single-page intake form. Three sections rendered top-to-bottom:
 *   1. Typology — Select dropdown of the active catalog. On select,
 *      a "typology summary" card appears below showing the SAMA
 *      category, perpetrator origin, primary channel, typical
 *      severity (with tone), and the bilingual description. This
 *      lets the Fraud Officer confirm they picked the right
 *      typology before continuing.
 *   2. Case detail — subject EN+AR (required), narrative EN+AR
 *      (optional), detection source + reference, incident date,
 *      estimated loss SAR, affected customer count.
 *   3. Submit. On success, navigates to /fraud/cases/{id}.
 *
 * Inherited from the typology at open-time:
 *   * Severity (typicalSeverity) — Fraud Officer can re-rate
 *     during triage. Surfaced read-only here as a preview.
 *   * SAMA category + STR-required hint.
 *
 * Saudi-native AR titles:
 *   - Page : "فتح قضية احتيال" (Open a fraud case)
 *   - Sub  : "تسجيل قضية جديدة وفقاً لإطار مكافحة الاحتيال 2018 (ساما)"
 */
export function FraudCaseNewPage() {
  const { lang } = useI18n();
  const navigate = useNavigate();
  const demoMode = !isAuthenticated();

  // ─── Typology catalog ───────────────────────────────────────
  const [typologies, setTypologies] = useState<TypologyResponse[]>([]);
  const [loadingCatalog, setLoadingCatalog] = useState(true);
  const [catalogError, setCatalogError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) { setLoadingCatalog(false); return; }
    let cancelled = false;
    (async () => {
      try {
        const r = await fraudApi.listTypologies();
        if (!cancelled) { setTypologies(r); }
      } catch (err) {
        if (!cancelled) {
          setCatalogError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل قائمة الأنماط' : 'Failed to load typology catalog'));
        }
      } finally {
        if (!cancelled) { setLoadingCatalog(false); }
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ─── Form state ─────────────────────────────────────────────
  const [typologyId, setTypologyId] = useState<number | null>(null);
  const [subjectEn, setSubjectEn] = useState('');
  const [subjectAr, setSubjectAr] = useState('');
  const [narrativeEn, setNarrativeEn] = useState('');
  const [narrativeAr, setNarrativeAr] = useState('');
  const [detectionSource, setDetectionSource] = useState<DetectionSource>('INTERNAL_REPORT');
  const [detectionReference, setDetectionReference] = useState('');
  const [incidentDate, setIncidentDate] = useState('');
  const [estimatedLossSar, setEstimatedLossSar] = useState('');
  const [affectedCustomerCount, setAffectedCustomerCount] = useState('');

  const selectedTypology = useMemo(
    () => typologies.find(t => t.id === typologyId) ?? null,
    [typologies, typologyId],
  );

  // ─── Submit ─────────────────────────────────────────────────
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const valid = typologyId != null
    && subjectEn.trim().length > 0
    && subjectAr.trim().length > 0
    && detectionSource;

  async function submit() {
    if (!valid || submitting) { return; }
    setSubmitting(true);
    setSubmitError(null);
    const body: CreateCaseRequest = {
      typologyId: typologyId!,
      subjectEn: subjectEn.trim(),
      subjectAr: subjectAr.trim(),
      narrativeEn: narrativeEn.trim() || undefined,
      narrativeAr: narrativeAr.trim() || undefined,
      detectionSource,
      detectionReference: detectionReference.trim() || undefined,
      incidentDate: incidentDate || undefined,
      estimatedLossSar: estimatedLossSar ? Number(estimatedLossSar) : undefined,
      affectedCustomerCount: affectedCustomerCount ? Number(affectedCustomerCount) : 0,
    };
    try {
      const created = await fraudApi.createCase(body);
      navigate(`/fraud/cases/${created.id}`);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح القضية' : 'Failed to open case'));
      setSubmitting(false);
    }
  }

  // ─── Renderers ──────────────────────────────────────────────
  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'مخاطر الاحتيال · المرحلة ١٤' : 'Fraud Risk · Sprint 14'}
        title={lang === 'ar' ? 'فتح قضية احتيال' : 'Open a fraud case'}
        sub={lang === 'ar'
          ? 'تسجيل قضية جديدة وفقاً لإطار مكافحة الاحتيال 2018 (ساما)'
          : 'New case registered under SAMA Anti-Fraud Framework 2018'}
        actions={
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/fraud/cases')}>
            {lang === 'ar' ? 'العودة إلى القضايا' : 'Back to cases'}
          </Button>
        }
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 960 }}>

        {/* ─── 1. Typology ─── */}
        <Card>
          <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <h3 style={{ margin: 0, fontSize: 16 }}>
              {lang === 'ar' ? 'النمط' : 'Typology'}
            </h3>
            {catalogError ? (
              <InlineEmpty text={catalogError} />
            ) : (
              <Select
                label={lang === 'ar' ? 'اختر نمط الاحتيال' : 'Select fraud typology'}
                value={typologyId == null ? '' : String(typologyId)}
                onChange={(e) => {
                  const v = e.currentTarget.value;
                  setTypologyId(v === '' ? null : Number(v));
                }}
                disabled={loadingCatalog || demoMode}
                options={[
                  { value: '',
                    label: loadingCatalog
                      ? (lang === 'ar' ? 'جاري التحميل…' : 'Loading…')
                      : (lang === 'ar' ? '— اختر —' : '— Select —') },
                  ...typologies.map(t => ({
                    value: String(t.id),
                    label: `${t.code} · ${lang === 'ar' ? t.nameAr : t.nameEn}`,
                  })),
                ]}
              />
            )}

            {selectedTypology && (
              <Card>
                <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
                    <Badge tone="info">
                      {SAMA_CATEGORY_LABEL[selectedTypology.samaCategoryCode][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                    <Badge tone="neutral">
                      {selectedTypology.perpetratorOrigin === 'INTERNAL'
                        ? (lang === 'ar' ? 'مصدر داخلي' : 'Internal')
                        : (lang === 'ar' ? 'مصدر خارجي' : 'External')}
                    </Badge>
                    <Badge tone="neutral">{selectedTypology.primaryChannel}</Badge>
                    <Badge tone={SEVERITY_TONE[selectedTypology.typicalSeverity]}>
                      {lang === 'ar' ? 'شدة نموذجية: ' : 'Typical severity: '}
                      {SEVERITY_LABEL[selectedTypology.typicalSeverity][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                    {selectedTypology.requiresStrFiling && (
                      <Badge tone="warning">
                        {lang === 'ar' ? 'يستوجب بلاغ ساما (STR)' : 'STR filing usually required'}
                      </Badge>
                    )}
                  </div>
                  <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
                    {lang === 'ar' ? selectedTypology.descriptionAr : selectedTypology.descriptionEn}
                  </div>
                </div>
              </Card>
            )}
          </div>
        </Card>

        {/* ─── 2. Case detail ─── */}
        <Card>
          <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <h3 style={{ margin: 0, fontSize: 16 }}>
              {lang === 'ar' ? 'تفاصيل القضية' : 'Case details'}
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Input
                label={lang === 'ar' ? 'الموضوع (إنجليزي)' : 'Subject (English)'}
                value={subjectEn}
                onChange={(e) => setSubjectEn(e.currentTarget.value)}
                placeholder={lang === 'ar' ? 'مثال: محاولة تحويل برقي احتيالي' : 'e.g. Wire transfer fraud attempt'}
                required
                disabled={demoMode}
              />
              <Input
                label={lang === 'ar' ? 'الموضوع (عربي)' : 'Subject (Arabic)'}
                value={subjectAr}
                onChange={(e) => setSubjectAr(e.currentTarget.value)}
                placeholder={lang === 'ar' ? 'مثال: محاولة تحويل برقي احتيالي' : 'مثال: محاولة تحويل برقي احتيالي'}
                required
                disabled={demoMode}
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Textarea
                label={lang === 'ar' ? 'السرد (إنجليزي)' : 'Narrative (English)'}
                value={narrativeEn}
                onChange={(e) => setNarrativeEn(e.currentTarget.value)}
                rows={4}
                placeholder={lang === 'ar'
                  ? 'صف ما حدث، متى، وكيف اكتُشف…'
                  : 'Describe what happened, when, how it was detected…'}
                disabled={demoMode}
              />
              <Textarea
                label={lang === 'ar' ? 'السرد (عربي)' : 'Narrative (Arabic)'}
                value={narrativeAr}
                onChange={(e) => setNarrativeAr(e.currentTarget.value)}
                rows={4}
                placeholder={lang === 'ar'
                  ? 'صف ما حدث، متى، وكيف اكتُشف…'
                  : 'اوصف ما حدث، متى، وكيف اكتُشف…'}
                disabled={demoMode}
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Select
                label={lang === 'ar' ? 'مصدر الكشف' : 'Detection source'}
                value={detectionSource}
                onChange={(e) => setDetectionSource(e.currentTarget.value as DetectionSource)}
                disabled={demoMode}
                options={(Object.keys(DETECTION_SOURCE_LABEL) as DetectionSource[]).map(src => ({
                  value: src,
                  label: DETECTION_SOURCE_LABEL[src][lang === 'ar' ? 'ar' : 'en'],
                }))}
              />
              <Input
                label={lang === 'ar' ? 'مرجع الكشف (اختياري)' : 'Detection reference (optional)'}
                value={detectionReference}
                onChange={(e) => setDetectionReference(e.currentTarget.value)}
                placeholder={lang === 'ar' ? 'مثال: AP-CB-2026-0042' : 'e.g. AP-CB-2026-0042'}
                disabled={demoMode}
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
              <Input
                label={lang === 'ar' ? 'تاريخ الحادثة' : 'Incident date'}
                type="date"
                value={incidentDate}
                onChange={(e) => setIncidentDate(e.currentTarget.value)}
                disabled={demoMode}
              />
              <Input
                label={lang === 'ar' ? 'الخسارة المقدّرة (ريال)' : 'Estimated loss (SAR)'}
                type="number"
                min={0}
                value={estimatedLossSar}
                onChange={(e) => setEstimatedLossSar(e.currentTarget.value)}
                placeholder="0"
                disabled={demoMode}
              />
              <Input
                label={lang === 'ar' ? 'عدد العملاء المتأثّرين' : 'Affected customers'}
                type="number"
                min={0}
                value={affectedCustomerCount}
                onChange={(e) => setAffectedCustomerCount(e.currentTarget.value)}
                placeholder="0"
                disabled={demoMode}
              />
            </div>
          </div>
        </Card>

        {/* ─── 3. Submit ─── */}
        <Card>
          <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {submitError && (
              <InlineEmpty text={submitError} />
            )}
            {demoMode && (
              <div style={{
                padding: '8px 12px',
                background: 'var(--color-bg-muted)',
                borderRadius: 6,
                fontSize: 13,
                color: 'var(--color-text-muted)',
              }}>
                {lang === 'ar'
                  ? 'يجب تسجيل الدخول لفتح قضية احتيال.'
                  : 'Log in to open a fraud case.'}
              </div>
            )}
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button variant="ghost" onClick={() => navigate('/fraud/cases')} disabled={submitting}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button
                variant="primary"
                Icon={Send}
                onClick={submit}
                disabled={!valid || submitting || demoMode}
              >
                {submitting
                  ? (lang === 'ar' ? 'جارٍ الفتح…' : 'Opening…')
                  : (lang === 'ar' ? 'فتح القضية' : 'Open case')}
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </Shell>
  );
}
