import { useEffect, useMemo, useState } from 'react';
import {
  Copy, Eye, Clock, BookOpen, Trash2, Search,
  Filter, X,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  POLICY_TEMPLATES, POLICY_CATEGORIES,
  loadCloned, clonePolicy, deleteCloned, subscribePolicyTemplates,
  renderSection,
  type PolicyTemplate, type PolicyCategory,
} from '../../lib/policyTemplates';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';

/**
 * PolicyTemplatesPage — Track D2 (content moat).
 *
 * Library of bilingual policy templates calibrated for Saudi regulatory
 * context. Tenants browse by category, preview the full bilingual body
 * (with [ORGANISATION] placeholders auto-substituted from white-label),
 * and clone-and-brand into their own policy register.
 */
export function PolicyTemplatesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();

  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<PolicyCategory | 'all'>('all');
  const [previewing, setPreviewing] = useState<PolicyTemplate | null>(null);

  const [cloned, setCloned] = useState(() => loadCloned());
  useEffect(() => subscribePolicyTemplates(() => setCloned(loadCloned())), []);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  const filtered = useMemo(() => {
    return POLICY_TEMPLATES.filter(p => {
      if (category !== 'all' && p.category !== category) return false;
      if (search) {
        const q = search.toLowerCase().trim();
        const haystack = [p.nameEn, p.nameAr, p.blurbEn, p.blurbAr, p.code]
          .join(' ').toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [search, category]);

  function clone(template: PolicyTemplate) {
    const c = clonePolicy(template, brand.organisationNameEn, brand.organisationNameAr);
    toast.success(`${t('ptClonedToast')} — ${c.cloneCode}`);
  }

  async function deleteClone(cloneCode: string) {
    // CH-8: themed confirm dialog instead of the browser's native confirm().
    const ok = await dialog.confirm({
      title: t('ptDeleteCloneTitle'), body: t('ptDeleteCloneConfirm'),
      destructive: true, submitLabel: t('deleteBtn'),
    });
    if (!ok) return;
    deleteCloned(cloneCode);
    toast.info(t('ptCloneDeletedToast'));
  }

  return (
    <Shell title={t('ptTitle')} sub={t('ptSub')}>
      <PageHeader
        eyebrow={t('ptEyebrow')}
        title={t('ptTitle')}
        sub={t('ptPageSub')}
      />

      {/* KPIs */}
      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          <Stat label={t('ptKpiTemplates')} value={String(POLICY_TEMPLATES.length)} />
          <Stat label={t('ptKpiCategories')} value={String(POLICY_CATEGORIES.length)} />
          <Stat label={t('ptKpiCloned')} value={String(cloned.length)} />
          <Stat label={t('ptKpiBrand')} value={lang === 'ar' ? brand.organisationNameAr : brand.organisationNameEn} />
        </div>
      </Card>

      {/* Filters */}
      <div style={{ marginTop: 16 }}>
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <div style={{ position: 'relative', flex: 1, minWidth: 240 }}>
              <Search size={14} style={{ position: 'absolute', insetInlineStart: 8, top: 11, color: 'var(--ink-3)' }} />
              <input value={search} onChange={e => setSearch(e.target.value)}
                placeholder={t('ptSearchPlaceholder')}
                style={{ ...inputStyle, paddingInlineStart: 28, width: '100%' }} />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--ink-3)' }}>
              <Filter size={12} />
              <span>{t('ptCategoryFilter')}:</span>
            </div>
            <button onClick={() => setCategory('all')}
              style={{
                padding: '6px 10px', borderRadius: 'var(--r-sm)', fontSize: 11,
                border: `1px solid ${category === 'all' ? 'var(--primary)' : 'var(--border)'}`,
                background: category === 'all' ? 'var(--primary-50)' : 'var(--surface-1)',
                cursor: 'pointer', fontWeight: 600,
              }}>
              {t('ptCategoryAll')}
            </button>
            {POLICY_CATEGORIES.map(c => (
              <button key={c.code} onClick={() => setCategory(c.code)}
                style={{
                  padding: '6px 10px', borderRadius: 'var(--r-sm)', fontSize: 11,
                  border: `1px solid ${category === c.code ? 'var(--primary)' : 'var(--border)'}`,
                  background: category === c.code ? 'var(--primary-50)' : 'var(--surface-1)',
                  cursor: 'pointer', fontWeight: 600,
                }}>
                {lang === 'ar' ? c.nameAr : c.nameEn}
              </button>
            ))}
          </div>
        </Card>
      </div>

      {/* Template grid */}
      <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 12 }}>
        {filtered.map(p => (
          <TemplateCard key={p.code} template={p}
            onPreview={() => setPreviewing(p)}
            onClone={() => clone(p)}
            cloneCount={cloned.filter(c => c.sourceCode === p.code).length} />
        ))}
        {filtered.length === 0 && (
          <Card>
            <div style={{ padding: 20, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
              {t('ptNoMatches')}
            </div>
          </Card>
        )}
      </div>

      {/* Cloned register */}
      {cloned.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <Card title={t('ptClonedTitle')} Icon={BookOpen}>
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
              {t('ptClonedSub')}
            </div>
            <table className="tbl" style={{ width: '100%' }}>
              <thead>
                <tr>
                  <th>{t('ptCloneCode')}</th>
                  <th>{t('ptCloneSource')}</th>
                  <th>{t('ptCloneOrg')}</th>
                  <th>{t('ptCloneAt')}</th>
                  <th style={{ width: 60 }}></th>
                </tr>
              </thead>
              <tbody>
                {cloned.map(c => {
                  const src = POLICY_TEMPLATES.find(t => t.code === c.sourceCode);
                  return (
                    <tr key={c.cloneCode}>
                      <td className="mono" style={{ fontSize: 11 }}>{c.cloneCode}</td>
                      <td>{src ? (lang === 'ar' ? src.nameAr : src.nameEn) : c.sourceCode}</td>
                      <td>{lang === 'ar' ? c.organisationNameAr : c.organisationNameEn}</td>
                      <td className="mono" style={{ fontSize: 11 }}>
                        {new Date(c.clonedAt).toLocaleDateString()}
                      </td>
                      <td>
                        <Button variant="ghost" Icon={Trash2} onClick={() => deleteClone(c.cloneCode)}>{''}</Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </Card>
        </div>
      )}

      {/* Preview modal */}
      {previewing && (
        <PreviewModal template={previewing}
          orgEn={brand.organisationNameEn}
          orgAr={brand.organisationNameAr}
          onClose={() => setPreviewing(null)}
          onClone={() => { clone(previewing); setPreviewing(null); }} />
      )}
    </Shell>
  );
}

// ─── Template card ───────────────────────────────────────────────────────────

function TemplateCard({ template, onPreview, onClone, cloneCount }: {
  template: PolicyTemplate;
  onPreview: () => void;
  onClone: () => void;
  cloneCount: number;
}) {
  const { t, lang } = useI18n();
  return (
    <Card>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 4 }}>
            {lang === 'ar' ? template.nameAr : template.nameEn}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
            <span className="mono">{template.code}</span>
            <span>· v{template.version}</span>
            <span>· <Clock size={10} style={{ display: 'inline-block', verticalAlign: 'middle' }} /> {template.readMinutes} {t('ptMin')}</span>
          </div>
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6 }}>
          {lang === 'ar' ? template.blurbAr : template.blurbEn}
        </div>
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {template.frameworkAnchors.map(a => (
            <Badge key={a} tone="info">{a}</Badge>
          ))}
          {cloneCount > 0 && <Badge tone="success">{cloneCount}× {t('ptCloned')}</Badge>}
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
          <Button variant="secondary" Icon={Eye} onClick={onPreview}>{t('ptPreview')}</Button>
          <Button variant="primary" Icon={Copy} onClick={onClone}>{t('ptClone')}</Button>
        </div>
      </div>
    </Card>
  );
}

// ─── Preview modal ───────────────────────────────────────────────────────────

function PreviewModal({ template, orgEn, orgAr, onClose, onClone }: {
  template: PolicyTemplate;
  orgEn: string;
  orgAr: string;
  onClose: () => void;
  onClone: () => void;
}) {
  const { t, lang } = useI18n();
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
    }} onClick={onClose}>
      <div onClick={e => e.stopPropagation()} style={{
        background: 'var(--surface-1)', borderRadius: 'var(--r-md)',
        maxWidth: 880, width: '100%', maxHeight: '90vh', overflow: 'auto',
        boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
      }}>
        {/* Header */}
        <div style={{
          padding: 20, borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
          gap: 12, position: 'sticky', top: 0, background: 'var(--surface-1)', zIndex: 1,
        }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {t('ptPreviewEyebrow')}
            </div>
            <div style={{ fontSize: 18, fontWeight: 700, marginTop: 4 }}>
              {lang === 'ar' ? template.nameAr : template.nameEn}
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <span className="mono">{template.code}</span>
              <span>· v{template.version}</span>
              <span>· {t('ptLastReviewed')} {template.lastReviewed}</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            <Button variant="primary" Icon={Copy} onClick={onClone}>{t('ptClone')}</Button>
            <Button variant="ghost" Icon={X} onClick={onClose}>{''}</Button>
          </div>
        </div>

        {/* Body */}
        <div style={{ padding: 20 }}>
          {template.sections.map(s => (
            <section key={s.code} style={{ marginBottom: 18 }}>
              <h3 style={{
                fontSize: 13, fontWeight: 700, color: 'var(--ink-1)',
                marginBottom: 6, display: 'flex', alignItems: 'baseline', gap: 8,
              }}>
                <span className="mono" style={{
                  fontSize: 11, color: 'var(--primary)',
                  padding: '2px 6px', background: 'var(--primary-50)',
                  borderRadius: 4,
                }}>{s.code}</span>
                <span>{lang === 'ar' ? s.titleAr : s.titleEn}</span>
              </h3>
              <p style={{
                fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7,
                margin: 0,
              }}>
                {renderSection(s, orgEn, orgAr, lang)}
              </p>
            </section>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 4,
      padding: 12, borderRadius: 'var(--r-md)',
      background: 'var(--surface-2)', border: '1px solid var(--border)',
    }}>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </div>
      <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {value}
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
