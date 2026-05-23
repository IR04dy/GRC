import { useEffect, useMemo, useState } from 'react';
import { BookOpen, Landmark, AlertTriangle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listAuthorityDocuments, listAuthorityDocumentCitations,
  rollupAuthorityDocumentsByFamily,
  citationsToTree,
  authorityStatusTone, translationTone, translationLabel,
  type AuthorityDocument, type AuthorityDocumentCitation,
  type CitationNode,
} from '../../lib/regulatoryAuthority';

/**
 * Regulatory Library — Framework Intersection plan slice F1.
 *
 * Read-only view of platform.authority_documents +
 * authority_document_citations. The plan is explicit: regulators are
 * data, not modules. This page is the catalog browser; downstream
 * domain pages (Risk, Cyber, BCM, …) will overlay control coverage
 * against these citations in slice F5.
 *
 * Every seeded document is is_placeholder=1 until the official text
 * is loaded — the banner is intentional, mirroring the NDI Rule-0
 * guardrail so the platform never overclaims regulatory completeness.
 */
export function RegulatoryLibraryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [documents, setDocuments] = useState<AuthorityDocument[]>([]);
  const [rollup, setRollup] = useState<Record<string, number>>({});
  const [familyFilter, setFamilyFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  const [selected, setSelected] = useState<AuthorityDocument | null>(null);
  const [citations, setCitations] = useState<AuthorityDocumentCitation[]>([]);
  const [citationsLoading, setCitationsLoading] = useState(false);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — show the document list even if the rollup endpoint
    // fails (e.g. missing aggregation view in dev DB), and vice versa.
    const [docs, agg] = await Promise.all([
      listAuthorityDocuments(familyFilter || undefined).catch(() => [] as AuthorityDocument[]),
      rollupAuthorityDocumentsByFamily().catch(() => ({} as Record<string, number>)),
    ]);
    setDocuments(docs);
    setRollup(agg);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [familyFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  async function selectDoc(d: AuthorityDocument) {
    setSelected(d);
    setCitationsLoading(true);
    try { setCitations(await listAuthorityDocumentCitations(d.id)); }
    catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل العناصر' : 'Failed to load citations'));
      setCitations([]);
    }
    finally { setCitationsLoading(false); }
  }

  const families = useMemo(() => Object.keys(rollup).sort(), [rollup]);

  const placeholderCount = useMemo(() =>
    documents.filter(d => d.isPlaceholder).length, [documents]);
  const arOfficialCount = useMemo(() =>
    documents.filter(d => d.translationStatus === 'AR_OFFICIAL').length, [documents]);

  const citationTree = useMemo(() => citationsToTree(citations), [citations]);

  return (
    <Shell
      title={isAr ? 'مكتبة المرجعيات التنظيمية' : 'Regulatory library'}
      sub={isAr ? 'الجهات والأنظمة والمعايير التي تتبعها المنصة' : 'Authority documents and citations the platform tracks'}>
      <PageHeader
        eyebrow={isAr ? 'الإطار التنظيمي' : 'Regulatory framework'}
        title={isAr ? 'مكتبة المرجعيات' : 'Authority documents catalog'} />

      {/* Placeholder guardrail banner (NDI Rule-0 pattern) */}
      {placeholderCount > 0 && (
        <div style={{
          marginBottom: 14, padding: '10px 14px', borderRadius: 'var(--r-md)',
          background: 'var(--warning-soft, #fff8e1)', border: '1px solid var(--warning, #f6c149)',
          display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <AlertTriangle size={18} style={{ flexShrink: 0, color: 'var(--warning, #c47e00)' }} />
          <div style={{ fontSize: 13 }}>
            <div style={{ fontWeight: 600, marginBottom: 2 }}>
              {isAr ? 'تنبيه: عناصر مؤقتة' : 'Placeholder data in use'}
            </div>
            <div style={{ color: 'var(--ink-2)' }}>
              {isAr
                ? `${placeholderCount} وثيقة مرجعية بحاجة لاستيراد النص الرسمي من الجهة المُصدرة قبل اعتمادها للامتثال.`
                : `${placeholderCount} authority document(s) require the official text to be imported before they can be used for compliance attestation.`}
            </div>
          </div>
        </div>
      )}

      {/* KPI strip */}
      <div className="kpi-strip-4">
        <Kpi Icon={BookOpen} label={isAr ? 'عدد الوثائق' : 'Documents'} value={documents.length} />
        <Kpi Icon={Landmark} label={isAr ? 'العائلات' : 'Families'} value={families.length} />
        <Kpi Icon={AlertTriangle} label={isAr ? 'عناصر مؤقتة' : 'Placeholders'} value={placeholderCount}
             tone={placeholderCount > 0 ? 'warning' : 'success'} />
        <Kpi Icon={BookOpen} label={isAr ? 'نص عربي رسمي' : 'AR official'} value={arOfficialCount} />
      </div>

      {/* Family filter */}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
        <button
          onClick={() => setFamilyFilter('')}
          style={chipStyle(familyFilter === '')}>
          {isAr ? 'الكل' : 'All'}
          <span className="mono text-2xs" style={{ marginInlineStart: 6, opacity: 0.7 }}>
            {Object.values(rollup).reduce((a, b) => a + b, 0)}
          </span>
        </button>
        {families.map(f => (
          <button key={f} onClick={() => setFamilyFilter(f)}
            style={chipStyle(familyFilter === f)}>
            {f}
            <span className="mono text-2xs" style={{ marginInlineStart: 6, opacity: 0.7 }}>{rollup[f]}</span>
          </button>
        ))}
      </div>

      <div className="dash-grid" style={{ gridTemplateColumns: 'minmax(420px, 1fr) minmax(0, 1.4fr)' }}>
        {/* Document list */}
        <Card title={isAr ? 'الوثائق المرجعية' : 'Authority documents'}
          action={<Badge tone="primary">{documents.length}</Badge>}>
          {loading
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                {isAr ? 'جاري التحميل…' : 'Loading…'}
              </div>
            : documents.length === 0
              ? <InlineEmpty>{isAr ? 'لا توجد مرجعيات' : 'No authority documents'}</InlineEmpty>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'العائلة' : 'Family'}</th>
                    <th>{isAr ? 'الإصدار' : 'Version'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'اللغة' : 'Translation'}</th>
                  </tr></thead>
                  <tbody>
                    {documents.map(d => (
                      <tr key={d.id} style={{
                        cursor: 'pointer',
                        background: selected?.id === d.id ? 'var(--surface-2)' : undefined,
                      }} onClick={() => selectDoc(d)}>
                        <td className="mono text-2xs">{d.code}</td>
                        <td>
                          <div style={{ fontWeight: 600 }}>
                            {isAr && d.titleAr ? d.titleAr : d.titleEn}
                          </div>
                          <div className="text-2xs text-muted">{d.issuer} · {d.jurisdiction}</div>
                          {d.isPlaceholder && (
                            <Badge tone="warning">{isAr ? 'مؤقت' : 'placeholder'}</Badge>
                          )}
                        </td>
                        <td className="text-xs">{d.family}</td>
                        <td className="mono text-2xs">v{d.version}</td>
                        <td><Badge tone={authorityStatusTone(d.status)}>{statusLabel(d.status, lang)}</Badge></td>
                        <td>
                          <Badge tone={translationTone(d.translationStatus)}>
                            {translationLabel(d.translationStatus, isAr ? 'ar' : 'en')}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
        </Card>

        {/* Citations panel */}
        {selected ? (
          <Card title={`${selected.code} v${selected.version} — ${isAr ? 'العناصر' : 'Citations'}`}
            action={
              <div style={{ display: 'flex', gap: 4 }}>
                <Badge tone="primary">{citations.length}</Badge>
                {selected.isPlaceholder && <Badge tone="warning">{isAr ? 'مؤقت' : 'placeholder'}</Badge>}
              </div>
            }>
            {selected.summaryEn && (
              <div className="text-xs text-muted" style={{ padding: '0 4px 12px' }}>
                {isAr && selected.summaryAr ? selected.summaryAr : selected.summaryEn}
              </div>
            )}
            {citationsLoading
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                  {isAr ? 'جاري التحميل…' : 'Loading…'}
                </div>
              : citations.length === 0
                ? <InlineEmpty>
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>
                      {isAr ? 'لا توجد عناصر بعد' : 'No citations yet'}
                    </div>
                    <div className="text-2xs text-muted">
                      {isAr ? 'يلزم استيراد البنود من النص الرسمي' : 'Import citations from the official document'}
                    </div>
                  </InlineEmpty>
                : <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {citationTree.map(n => <CitationRow key={n.citation.id} node={n} isAr={isAr} depth={0} />)}
                  </div>}
          </Card>
        ) : (
          <Card title={isAr ? 'العناصر' : 'Citations'}>
            <InlineEmpty>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                {isAr ? 'اختر وثيقة لعرض عناصرها' : 'Pick a document to view its citations'}
              </div>
              <div className="text-2xs text-muted">
                {isAr ? 'يمكنك تصفية حسب العائلة من أعلى' : 'Filter by family using the chips above'}
              </div>
            </InlineEmpty>
          </Card>
        )}
      </div>
    </Shell>
  );
}

function CitationRow({ node, depth, isAr }: { node: CitationNode; depth: number; isAr: boolean }) {
  const c = node.citation;
  const title = isAr && c.titleAr ? c.titleAr : c.titleEn;
  const isLeaf = node.children.length === 0;
  return (
    <>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'auto 1fr auto',
        gap: 8,
        alignItems: 'center',
        padding: '6px 8px',
        marginInlineStart: depth * 18,
        borderInlineStart: depth > 0 ? '2px solid var(--border)' : undefined,
        background: depth === 0 ? 'var(--surface-2)' : undefined,
        borderRadius: 'var(--r-sm)',
      }}>
        <span className="mono text-2xs" style={{ minWidth: 56, color: 'var(--ink-3)' }}>{c.citationCode}</span>
        <span style={{ fontSize: 12, fontWeight: depth === 0 ? 600 : 400 }}>{title}</span>
        <span style={{ display: 'inline-flex', gap: 4 }}>
          {c.mandatory && <Badge tone="info">{isAr ? 'إلزامي' : 'mandatory'}</Badge>}
          {c.isPlaceholder && isLeaf && <Badge tone="warning">{isAr ? 'مؤقت' : 'placeholder'}</Badge>}
        </span>
      </div>
      {node.children.map(child =>
        <CitationRow key={child.citation.id} node={child} depth={depth + 1} isAr={isAr} />)}
    </>
  );
}

function chipStyle(active: boolean): React.CSSProperties {
  return {
    padding: '6px 12px', fontSize: 12,
    border: '1px solid var(--border)',
    borderRadius: 999,
    background: active ? 'var(--primary)' : 'var(--surface-1)',
    color: active ? '#fff' : 'var(--ink-1)',
    cursor: 'pointer',
    display: 'inline-flex', alignItems: 'center', gap: 4,
  };
}
