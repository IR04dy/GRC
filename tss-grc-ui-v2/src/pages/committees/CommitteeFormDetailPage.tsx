import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { FileText, Download, Languages, Info } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { getForm, type FormTemplate } from '../../lib/committees';

/**
 * Form detail page — renders the bilingual markdown template, lets the
 * user toggle between EN / AR, and offers a download as .md file (the
 * raw template). The user can then paste into Word / Pages / Docs and
 * fill in the [BRACKETED] placeholders.
 *
 * <p>Route: `/committees/catalog/forms/:formCode`
 */
export function CommitteeFormDetailPage() {
  const { formCode } = useParams<{ formCode: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [form, setForm] = useState<FormTemplate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState<'en' | 'ar'>(isAr ? 'ar' : 'en');

  useEffect(() => {
    if (!formCode) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    getForm(formCode)
      .then(f => { if (!cancelled) setForm(f); })
      .catch(e => { if (!cancelled) setError((e as Error).message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [formCode]);

  function download() {
    if (!form) return;
    const content = view === 'ar' ? form.contentMdAr : form.contentMdEn;
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${form.formCode}-${view}.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  if (loading) {
    return (
      <Shell title={isAr ? 'مكتبة النماذج' : 'Forms Library'}>
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
          {isAr ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      </Shell>
    );
  }

  if (error || !form) {
    return (
      <Shell title={isAr ? 'مكتبة النماذج' : 'Forms Library'}>
        <DetailHeader
          breadcrumbs={[
            { label: isAr ? 'الكتالوج' : 'Catalog', to: '/committees/catalog' },
            { label: isAr ? 'النماذج' : 'Forms', to: '/committees/catalog' },
            { label: formCode ?? '?' },
          ]}
          title={isAr ? 'تعذّر التحميل' : 'Failed to load'}
          sub={error ?? (isAr ? 'النموذج غير موجود' : 'Form not found')}
          backTo="/committees/catalog"
        />
        <EmptyState Icon={FileText} title={isAr ? 'النموذج غير موجود' : 'Form not found'} />
      </Shell>
    );
  }

  const content = view === 'ar' ? form.contentMdAr : form.contentMdEn;
  const purpose = view === 'ar' ? form.purposeAr : form.purposeEn;
  const guide = view === 'ar' ? form.completionGuideAr : form.completionGuideEn;

  return (
    <Shell title={isAr ? 'مكتبة النماذج' : 'Forms Library'}>
      <DetailHeader
        breadcrumbs={[
          { label: isAr ? 'الكتالوج' : 'Catalog', to: '/committees/catalog' },
          { label: isAr ? 'النماذج' : 'Forms', to: '/committees/catalog' },
          { label: form.formCode },
        ]}
        eyebrow={form.formType}
        title={view === 'ar' ? form.titleAr : form.titleEn}
        sub={<>
          <Badge tone="info">{form.formType}</Badge>{' '}
          <span className="mono">v{form.version}</span>
          {form.regulatorAnchors && <>
            {' · '}
            <span style={{ color: 'var(--ink-3)' }}>{form.regulatorAnchors}</span>
          </>}
        </>}
        backTo="/committees/catalog"
        actions={<div style={{ display: 'flex', gap: 8 }}>
          <Button variant="ghost" size="sm" onClick={() => setView(view === 'en' ? 'ar' : 'en')}>
            <Languages size={14} />
            {view === 'en' ? 'العربية' : 'English'}
          </Button>
          <Button variant="primary" size="sm" onClick={download}>
            <Download size={14} />
            {isAr ? 'تنزيل .md' : 'Download .md'}
          </Button>
        </div>}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)', gap: 16, alignItems: 'start' }}>
        {/* Content */}
        <Card title={isAr ? 'محتوى النموذج' : 'Form Content'} Icon={FileText}>
          <pre style={{
            margin: 0,
            padding: 16,
            background: 'var(--surface-2)',
            borderRadius: 'var(--r-sm)',
            fontSize: 12,
            lineHeight: 1.6,
            fontFamily: 'var(--font-mono, "Courier New", monospace)',
            whiteSpace: 'pre-wrap',
            wordWrap: 'break-word',
            color: 'var(--ink-1)',
            direction: view === 'ar' ? 'rtl' : 'ltr',
            textAlign: view === 'ar' ? 'right' : 'left',
            maxHeight: '70vh',
            overflow: 'auto',
          }}>
            {content}
          </pre>
        </Card>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Purpose */}
          <Card title={isAr ? 'الغرض' : 'Purpose'} Icon={Info}>
            <p style={{ margin: 0, fontSize: 12, lineHeight: 1.7, color: 'var(--ink-2)' }}>
              {purpose}
            </p>
          </Card>

          {/* Completion guide */}
          {guide && (
            <Card title={isAr ? 'دليل الإكمال' : 'Completion Guide'} Icon={Info}>
              <p style={{ margin: 0, fontSize: 12, lineHeight: 1.7, color: 'var(--ink-2)' }}>
                {guide}
              </p>
            </Card>
          )}

          {/* Applicability */}
          <Card title={isAr ? 'ينطبق على' : 'Applicable To'} Icon={FileText}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {form.applicableTo === 'ALL' ? (
                <Badge tone="success">{isAr ? 'جميع اللجان' : 'All committees'}</Badge>
              ) : (
                form.applicableTo.split(',').map(t => (
                  <Badge key={t.trim()} tone="neutral">{t.trim()}</Badge>
                ))
              )}
            </div>
          </Card>
        </div>
      </div>
    </Shell>
  );
}
