import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Save, Send, Archive, FileEdit, Trash2, ChevronLeft, Eye } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { hasAnyRole } from '../../lib/auth';
import {
  getArticle, createArticle, updateArticle, transitionArticle, deleteArticle,
  type KbArticle, type KbArticleDraft, type KbStatus, type KbTransition,
} from '../../lib/kb';

/**
 * KB editor — Sprint 4 §2.9 (write-side completion).
 *
 * Same component handles both:
 *   /knowledge-base/new            → create-mode (no slug param)
 *   /knowledge-base/:slug/edit     → edit-mode (loads existing draft)
 *
 * State machine wiring (POST /api/kb/articles/{slug}/transition):
 *   DRAFT ─PUBLISH─► PUBLISHED ─ARCHIVE─► ARCHIVED ─REDRAFT─► DRAFT
 *
 * Buttons surfaced based on current status; transitions block until the latest
 * edits are saved (we always Save+Transition in one click to keep authoring
 * atomic — partial writes between two HTTP calls are a footgun).
 */

const EMPTY_DRAFT: KbArticleDraft = {
  slug: '',
  categoryCode: '',
  titleEn: '',
  titleAr: '',
  summaryEn: '',
  summaryAr: '',
  bodyEnMd: '',
  bodyArMd: '',
  tags: '',
  status: 'DRAFT',
  visibility: 'INTERNAL',
};

export function KbEditorPage() {
  const { slug: routeSlug } = useParams<{ slug?: string }>();
  const navigate = useNavigate();
  const { lang } = useI18n();
  const toast = useToast();
  const isCreate = !routeSlug;

  const canWrite = hasAnyRole('PLATFORM_ADMIN', 'KB_WRITE');
  const canDelete = hasAnyRole('PLATFORM_ADMIN');

  const [draft, setDraft] = useState<KbArticleDraft>(EMPTY_DRAFT);
  const [original, setOriginal] = useState<KbArticle | null>(null);
  const [loading, setLoading] = useState(!isCreate);
  const [saving, setSaving] = useState(false);
  const [confirmDel, setConfirmDel] = useState(false);

  // ─── Load existing article in edit mode ────────────────────────────
  useEffect(() => {
    if (isCreate) return;
    let cancelled = false;
    getArticle(routeSlug!).then(a => {
      if (cancelled) return;
      if (!a) {
        toast.danger(lang === 'ar' ? 'المقال غير موجود' : 'Article not found');
        navigate('/knowledge-base');
        return;
      }
      setOriginal(a);
      setDraft({
        slug: a.slug,
        categoryCode: a.categoryCode ?? '',
        titleEn: a.titleEn,
        titleAr: a.titleAr,
        summaryEn: a.summaryEn ?? '',
        summaryAr: a.summaryAr ?? '',
        bodyEnMd: a.bodyEnMd ?? '',
        bodyArMd: a.bodyArMd ?? '',
        tags: a.tags ?? '',
        status: a.status,
        visibility: a.visibility,
      });
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [routeSlug, isCreate, lang, navigate, toast]);

  // ─── Validation ─────────────────────────────────────────────────────
  const errors = useMemo(() => {
    const e: Record<string, string> = {};
    if (!draft.titleEn.trim()) e.titleEn = lang === 'ar' ? 'العنوان الإنجليزي مطلوب' : 'English title is required';
    if (!draft.titleAr.trim()) e.titleAr = lang === 'ar' ? 'العنوان العربي مطلوب' : 'Arabic title is required';
    if (isCreate && !draft.slug?.trim()) e.slug = lang === 'ar' ? 'الرمز (slug) مطلوب' : 'Slug is required';
    if (draft.slug && !/^[a-z0-9-]+$/.test(draft.slug)) {
      e.slug = lang === 'ar' ? 'استخدم أحرف صغيرة وأرقامًا وشرطات فقط' : 'Lowercase letters, numbers, and hyphens only';
    }
    return e;
  }, [draft, isCreate, lang]);

  const hasErrors = Object.keys(errors).length > 0;

  function update<K extends keyof KbArticleDraft>(key: K, value: KbArticleDraft[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  // ─── Save (create OR update) ────────────────────────────────────────
  async function save(): Promise<KbArticle | null> {
    if (hasErrors) {
      toast.danger(lang === 'ar' ? 'يرجى تصحيح الأخطاء قبل الحفظ' : 'Please fix the errors before saving');
      return null;
    }
    setSaving(true);
    try {
      const result = isCreate
        ? await createArticle(draft)
        : await updateArticle(routeSlug!, draft);
      setOriginal(result);
      toast.success(lang === 'ar' ? 'تم الحفظ' : 'Saved');
      // After a create, hop into edit mode so the URL becomes shareable.
      if (isCreate) navigate(`/knowledge-base/${result.slug}/edit`, { replace: true });
      return result;
    } catch (err) {
      toast.danger((err as Error).message ?? (lang === 'ar' ? 'فشل الحفظ' : 'Save failed'));
      return null;
    } finally {
      setSaving(false);
    }
  }

  // ─── Transition (publish / archive / redraft) ───────────────────────
  // Always Save first, then transition. Two HTTP calls but one user action.
  async function saveAndTransition(action: KbTransition) {
    const saved = await save();
    if (!saved) return;
    try {
      const result = await transitionArticle(saved.slug, action);
      setOriginal(result);
      setDraft(d => ({ ...d, status: result.status }));
      const labels: Record<KbTransition, { en: string; ar: string }> = {
        PUBLISH:  { en: 'Published',  ar: 'تم النشر' },
        ARCHIVE:  { en: 'Archived',   ar: 'تمت الأرشفة' },
        REDRAFT:  { en: 'Reverted to draft', ar: 'تمت إعادة التسوّيد' },
      };
      toast.success(labels[action][lang]);
    } catch (err) {
      toast.danger((err as Error).message ?? (lang === 'ar' ? 'تعذّرت العملية' : 'Transition failed'));
    }
  }

  async function executeDelete() {
    if (!routeSlug) return;
    try {
      await deleteArticle(routeSlug);
      toast.success(lang === 'ar' ? 'تم الحذف' : 'Deleted');
      navigate('/knowledge-base');
    } catch (err) {
      toast.danger((err as Error).message ?? (lang === 'ar' ? 'فشل الحذف' : 'Delete failed'));
    } finally {
      setConfirmDel(false);
    }
  }

  // ─── Permission gate ───────────────────────────────────────────────
  if (!canWrite) {
    return (
      <Shell title={lang === 'ar' ? 'محرر قاعدة المعرفة' : 'Knowledge Base Editor'} sub="">
        <Card>
          <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
            <p>{lang === 'ar' ? 'لا تملك صلاحية تحرير المقالات' : 'You do not have permission to edit articles.'}</p>
            <Link to="/knowledge-base" style={{ color: 'var(--primary)', marginTop: 12, display: 'inline-block' }}>
              {lang === 'ar' ? '← رجوع لقاعدة المعرفة' : '← Back to Knowledge Base'}
            </Link>
          </div>
        </Card>
      </Shell>
    );
  }

  if (loading) {
    return (
      <Shell title={lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'} sub="">
        <div style={{ padding: 48, textAlign: 'center' }}>…</div>
      </Shell>
    );
  }

  const status: KbStatus = draft.status;
  const statusTone: Record<KbStatus, 'neutral' | 'success' | 'warning'> = {
    DRAFT:     'neutral',
    PUBLISHED: 'success',
    ARCHIVED:  'warning',
  };

  const heading = isCreate
    ? (lang === 'ar' ? 'مقال جديد' : 'New article')
    : (lang === 'ar' ? draft.titleAr || 'تحرير مقال' : draft.titleEn || 'Edit article');

  return (
    <Shell title={heading} sub={lang === 'ar' ? 'محرر قاعدة المعرفة' : 'Knowledge Base editor'}>
      <PageHeader
        title={heading}
        sub={isCreate
          ? (lang === 'ar' ? 'أنشئ مسودة جديدة قابلة للنشر للموظفين' : 'Compose a new draft to publish to staff')
          : (lang === 'ar' ? 'حدّث المحتوى وحرّك الحالة عند الجاهزية' : 'Update content, advance status when ready')}
        actions={
          <>
            <Badge tone={statusTone[status]}>{status}</Badge>
            <Link to="/knowledge-base"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--ink-2)', fontSize: 13, textDecoration: 'none' }}>
              <ChevronLeft size={14} />
              {lang === 'ar' ? 'كل المقالات' : 'All articles'}
            </Link>
          </>
        }
      />

      <Card>
        {/* ── Identity row ───────────────────────────────────────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, marginBottom: 12 }}>
          <Field label={lang === 'ar' ? 'الرمز (slug)' : 'Slug'} hint={lang === 'ar' ? 'حروف صغيرة وأرقام وشرطات' : 'lowercase, numbers, hyphens'} error={errors.slug}>
            <input
              value={draft.slug ?? ''}
              onChange={e => update('slug', e.target.value.toLowerCase().replace(/\s+/g, '-'))}
              disabled={!isCreate}
              placeholder="incident-runbook"
              style={inputStyle}
            />
          </Field>
          <Field label={lang === 'ar' ? 'التصنيف' : 'Category'}>
            <input value={draft.categoryCode ?? ''} onChange={e => update('categoryCode', e.target.value)}
                   placeholder="OPERATIONS" style={inputStyle} />
          </Field>
        </div>

        {/* ── Title row (bilingual side-by-side) ────────────────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, marginBottom: 12 }}>
          <Field label={lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'} required error={errors.titleEn}>
            <input value={draft.titleEn} onChange={e => update('titleEn', e.target.value)} style={inputStyle} dir="ltr" />
          </Field>
          <Field label={lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'} required error={errors.titleAr}>
            <input value={draft.titleAr} onChange={e => update('titleAr', e.target.value)} style={inputStyle} dir="rtl" />
          </Field>
        </div>

        {/* ── Summary row (bilingual) ───────────────────────────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, marginBottom: 12 }}>
          <Field label={lang === 'ar' ? 'ملخص (إنجليزي)' : 'Summary (English)'}>
            <textarea value={draft.summaryEn ?? ''} onChange={e => update('summaryEn', e.target.value)} rows={2} style={textareaStyle} dir="ltr" />
          </Field>
          <Field label={lang === 'ar' ? 'ملخص (عربي)' : 'Summary (Arabic)'}>
            <textarea value={draft.summaryAr ?? ''} onChange={e => update('summaryAr', e.target.value)} rows={2} style={textareaStyle} dir="rtl" />
          </Field>
        </div>

        {/* ── Body row (bilingual markdown) ─────────────────────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12, marginBottom: 12 }}>
          <Field label={lang === 'ar' ? 'المحتوى (إنجليزي · ماركداون)' : 'Body (English · Markdown)'}
                 hint={lang === 'ar' ? 'يدعم العناوين والقوائم والروابط' : 'Headings, lists, links supported'}>
            <textarea value={draft.bodyEnMd ?? ''} onChange={e => update('bodyEnMd', e.target.value)}
                      rows={14} style={{ ...textareaStyle, fontFamily: 'var(--font-mono, monospace)' }} dir="ltr" />
          </Field>
          <Field label={lang === 'ar' ? 'المحتوى (عربي · ماركداون)' : 'Body (Arabic · Markdown)'}
                 hint={lang === 'ar' ? 'يدعم العناوين والقوائم والروابط' : 'Headings, lists, links supported'}>
            <textarea value={draft.bodyArMd ?? ''} onChange={e => update('bodyArMd', e.target.value)}
                      rows={14} style={{ ...textareaStyle, fontFamily: 'var(--font-mono, monospace)' }} dir="rtl" />
          </Field>
        </div>

        {/* ── Metadata row ──────────────────────────────────────────── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 12 }}>
          <Field label={lang === 'ar' ? 'الوسوم' : 'Tags'} hint={lang === 'ar' ? 'مفصولة بفواصل' : 'comma-separated'}>
            <input value={draft.tags ?? ''} onChange={e => update('tags', e.target.value)} style={inputStyle} />
          </Field>
          <Field label={lang === 'ar' ? 'مستوى الوصول' : 'Visibility'}>
            <select value={draft.visibility} onChange={e => update('visibility', e.target.value as KbArticleDraft['visibility'])}
                    style={inputStyle}>
              <option value="INTERNAL">INTERNAL</option>
              <option value="PUBLIC">PUBLIC</option>
              <option value="RESTRICTED">RESTRICTED</option>
            </select>
          </Field>
          <div /> {/* spacer */}
        </div>
      </Card>

      {/* ── Action bar ─────────────────────────────────────────────── */}
      <Card>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <Button variant="primary" Icon={Save} onClick={save} disabled={saving || hasErrors}>
            {lang === 'ar' ? 'حفظ' : 'Save'}
          </Button>

          {!isCreate && status === 'DRAFT' && (
            <Button variant="primary" Icon={Send} onClick={() => saveAndTransition('PUBLISH')} disabled={saving || hasErrors}>
              {lang === 'ar' ? 'نشر' : 'Publish'}
            </Button>
          )}

          {!isCreate && status === 'PUBLISHED' && (
            <Button variant="secondary" Icon={Archive} onClick={() => saveAndTransition('ARCHIVE')} disabled={saving}>
              {lang === 'ar' ? 'أرشفة' : 'Archive'}
            </Button>
          )}

          {!isCreate && status === 'ARCHIVED' && (
            <Button variant="secondary" Icon={FileEdit} onClick={() => saveAndTransition('REDRAFT')} disabled={saving}>
              {lang === 'ar' ? 'إعادة للمسوّدات' : 'Move to draft'}
            </Button>
          )}

          {!isCreate && original && (
            <Button variant="ghost" Icon={Eye} onClick={() => navigate(`/knowledge-base/${original.slug}`)}>
              {lang === 'ar' ? 'معاينة' : 'Preview'}
            </Button>
          )}

          <div style={{ marginInlineStart: 'auto' }} />

          {!isCreate && canDelete && (
            <Button variant="ghost" Icon={Trash2} onClick={() => setConfirmDel(true)} disabled={saving}>
              {lang === 'ar' ? 'حذف' : 'Delete'}
            </Button>
          )}
        </div>
      </Card>

      <ConfirmDialog
        open={confirmDel}
        destructive
        title={lang === 'ar' ? 'حذف هذا المقال؟' : 'Delete this article?'}
        message={lang === 'ar'
          ? `سيتم حذف "${draft.titleAr || draft.titleEn}". لن يكون قابلاً للاسترداد من هذه الواجهة.`
          : `"${draft.titleEn || draft.titleAr}" will be removed. It cannot be recovered from this UI.`}
        confirmLabel={lang === 'ar' ? 'حذف' : 'Delete'}
        onCancel={() => setConfirmDel(false)}
        onConfirm={executeDelete}
      />
    </Shell>
  );
}

// ─── Form field primitives ────────────────────────────────────────────
function Field({ label, hint, required, error, children }: {
  label: string; hint?: string; required?: boolean; error?: string; children: React.ReactNode;
}) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--ink-2)', marginBottom: 4 }}>
        {label}
        {required && <span style={{ color: 'var(--danger-fg)', marginInlineStart: 4 }}>*</span>}
      </label>
      {children}
      {hint && !error && <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>{hint}</div>}
      {error && <div style={{ fontSize: 11, color: 'var(--danger-fg)', marginTop: 4 }}>{error}</div>}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '8px 10px', border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
  fontSize: 14, outline: 'none',
};

const textareaStyle: React.CSSProperties = {
  ...inputStyle,
  resize: 'vertical',
  fontSize: 13,
  lineHeight: 1.5,
};
