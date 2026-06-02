import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BookOpen, Search, Clock, Sparkles, TrendingUp, Users,
  ShieldAlert, Filter,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listScenarios,
  popularScenarios,
  type ScenarioSummary,
  type ScenarioType,
  type Complexity,
  SCENARIO_TYPES,
  COMPLEXITIES,
  FRAMEWORKS,
  SCENARIO_TYPE_LABELS,
  COMPLEXITY_LABELS,
  complexityTone,
  scenarioTypeTone,
  fmtDuration,
  parseFrameworks,
  frameworkLabel,
} from '../../lib/scenarioLibrary';

/**
 * Slice 10-A — Exercise Scenario Library landing page.
 *
 * <p>Card grid surfacing the SAMA-BCF-Annex-C-aligned scenario catalog.
 * The BCM Manager picks a card, opens detail, and instantiates a runnable
 * exercise in ~15 minutes — replacing the 8-12 hours of writing scenario,
 * objectives, injects, and observers'' guide from scratch.
 *
 * <p>Saudi-native phrasing throughout. The hero title reads "مكتبة
 * سيناريوهات تمارين الاستمرارية" (not literal "Library" → "مكتبة" alone)
 * to convey it is the continuity-exercise catalog the regulator expects
 * the BCM function to maintain.
 *
 * <p>Filter sidebar (left): scenario type, complexity, framework. A free-
 * text search runs against title + scenario code in both languages.
 * "Popular this quarter" pin sits above the main grid.
 */
export function ExerciseScenarioLibraryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [items, setItems] = useState<ScenarioSummary[]>([]);
  const [popular, setPopular] = useState<ScenarioSummary[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  // Filters
  const [typeFilter, setTypeFilter] = useState<ScenarioType | ''>('');
  const [cmplxFilter, setCmplxFilter] = useState<Complexity | ''>('');
  const [fwFilter, setFwFilter] = useState<string>('');
  const [q, setQ] = useState<string>('');

  // ─── Load ──────────────────────────────────────────────────────────
  async function reload() {
    setLoading(true);
    try {
      const [page, top] = await Promise.all([
        listScenarios({
          type: typeFilter || undefined,
          complexity: cmplxFilter || undefined,
          framework: fwFilter || undefined,
          q: q.trim() || undefined,
          page: 0, size: 60,
        }),
        popularScenarios(5),
      ]);
      setItems(page.content);
      setTotalElements(page.totalElements);
      setPopular(Array.isArray(top) ? top : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل مكتبة السيناريوهات'
                : 'Failed to load scenario library'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [typeFilter, cmplxFilter, fwFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  // Debounced search
  useEffect(() => {
    const id = setTimeout(() => { void reload(); }, 250);
    return () => clearTimeout(id);
  }, [q]); // eslint-disable-line react-hooks/exhaustive-deps

  function clearFilters() {
    setTypeFilter('');
    setCmplxFilter('');
    setFwFilter('');
    setQ('');
  }

  const activeFilterCount =
    (typeFilter ? 1 : 0) + (cmplxFilter ? 1 : 0) + (fwFilter ? 1 : 0) + (q.trim() ? 1 : 0);

  const popularIds = useMemo(() => new Set(popular.map(p => p.id)), [popular]);

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'مكتبة سيناريوهات تمارين الاستمرارية'
                   : 'Continuity Exercise Scenario Library'}
        sub={isAr
          ? 'سيناريوهات جاهزة للتنفيذ تغطي تهديدات حقيقية للسوق السعودي — يختار مدير الاستمرارية سيناريو ويُنشئ تمريناً في ١٥ دقيقة بدلاً من ١٢ ساعة كتابة من الصفر.'
          : 'Ready-to-run scenarios mapped to real Saudi-market threats — the BCM Manager picks one and instantiates an exercise in 15 minutes instead of 12 hours of writing from scratch.'}
      />

      <div style={{
        display: 'grid',
        gridTemplateColumns: '260px 1fr',
        gap: 'var(--space-lg)',
        alignItems: 'start',
      }}>
        {/* ─── Filter sidebar ─────────────────────────────────────── */}
        <aside className="stack" style={{ gap: 'var(--space-md)', position: 'sticky', top: 0 }}>
          <Card title={isAr ? 'تصفية' : 'Filters'} Icon={Filter}>
            <div className="stack" style={{ gap: 'var(--space-sm)' }}>
              {/* Free-text */}
              <div>
                <label style={{
                  display: 'block', fontSize: 12, fontWeight: 600,
                  marginBottom: 4, color: 'var(--ink-3)',
                }}>
                  {isAr ? 'بحث' : 'Search'}
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="text"
                    className="input"
                    style={{ width: '100%', paddingInlineStart: 30 }}
                    placeholder={isAr ? 'العنوان أو الرمز…' : 'Title or code…'}
                    value={q}
                    onChange={e => setQ(e.target.value)}
                  />
                  <Search
                    size={14}
                    style={{
                      position: 'absolute',
                      [isAr ? 'right' : 'left']: 9,
                      top: '50%',
                      transform: 'translateY(-50%)',
                      color: 'var(--ink-3)',
                    } as React.CSSProperties}
                  />
                </div>
              </div>

              {/* Scenario type */}
              <div>
                <label style={{
                  display: 'block', fontSize: 12, fontWeight: 600,
                  marginBottom: 4, color: 'var(--ink-3)',
                }}>
                  {isAr ? 'نوع التمرين' : 'Exercise type'}
                </label>
                <select
                  className="input"
                  style={{ width: '100%' }}
                  value={typeFilter}
                  onChange={e => setTypeFilter(e.target.value as ScenarioType | '')}
                >
                  <option value="">{isAr ? 'الكل' : 'All'}</option>
                  {SCENARIO_TYPES.map(t => (
                    <option key={t} value={t}>
                      {tlabel(SCENARIO_TYPE_LABELS, t, isAr)}
                    </option>
                  ))}
                </select>
              </div>

              {/* Complexity */}
              <div>
                <label style={{
                  display: 'block', fontSize: 12, fontWeight: 600,
                  marginBottom: 4, color: 'var(--ink-3)',
                }}>
                  {isAr ? 'مستوى التعقيد' : 'Complexity'}
                </label>
                <select
                  className="input"
                  style={{ width: '100%' }}
                  value={cmplxFilter}
                  onChange={e => setCmplxFilter(e.target.value as Complexity | '')}
                >
                  <option value="">{isAr ? 'الكل' : 'All'}</option>
                  {COMPLEXITIES.map(c => (
                    <option key={c} value={c}>
                      {tlabel(COMPLEXITY_LABELS, c, isAr)}
                    </option>
                  ))}
                </select>
              </div>

              {/* Framework */}
              <div>
                <label style={{
                  display: 'block', fontSize: 12, fontWeight: 600,
                  marginBottom: 4, color: 'var(--ink-3)',
                }}>
                  {isAr ? 'الإطار التنظيمي' : 'Framework'}
                </label>
                <select
                  className="input"
                  style={{ width: '100%' }}
                  value={fwFilter}
                  onChange={e => setFwFilter(e.target.value)}
                >
                  <option value="">{isAr ? 'الكل' : 'All'}</option>
                  {FRAMEWORKS.map(f => (
                    <option key={f.code} value={f.code}>
                      {isAr ? f.ar : f.en}
                    </option>
                  ))}
                </select>
              </div>

              {activeFilterCount > 0 && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ width: '100%', marginTop: 'var(--space-xs)' }}
                  onClick={clearFilters}
                >
                  {isAr ? `إزالة التصفية (${activeFilterCount})` : `Clear filters (${activeFilterCount})`}
                </button>
              )}
            </div>
          </Card>

          {/* Help / regulatory context */}
          <Card title={isAr ? 'متطلبات SAMA BCF' : 'SAMA BCF requirement'} Icon={ShieldAlert}>
            <p style={{ fontSize: 13, color: 'var(--ink-3)', margin: 0 }}>
              {isAr
                ? 'يفرض الملحق ج من إطار استمرارية الأعمال لساما إجراء تمرينين كاملي النطاق وأربعة تمارين حوارية على الأقل سنوياً. تساعد المكتبة على تحقيق هذا الالتزام بسرعة.'
                : 'SAMA BCF Annex C requires 2 full-scale + 4 tabletop exercises per year. The library accelerates that obligation.'}
            </p>
          </Card>
        </aside>

        {/* ─── Main grid ──────────────────────────────────────────── */}
        <div className="stack" style={{ gap: 'var(--space-md)' }}>
          {/* "Popular this quarter" pinned section */}
          {popular.length > 0 && activeFilterCount === 0 && (
            <Card
              title={isAr ? 'الأكثر استخداماً هذا الربع' : 'Popular this quarter'}
              Icon={Sparkles}
            >
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                gap: 'var(--space-sm)',
              }}>
                {popular.map(s => (
                  <ScenarioCard
                    key={s.id} s={s} isAr={isAr} pinned
                    onClick={() => navigate(`/bcm/scenarios/${s.id}`)}
                  />
                ))}
              </div>
            </Card>
          )}

          {/* Main grid */}
          <Card
            title={isAr
              ? `جميع السيناريوهات (${totalElements})`
              : `All scenarios (${totalElements})`}
            Icon={BookOpen}
          >
            {loading ? (
              <InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
            ) : items.length === 0 ? (
              <InlineEmpty text={isAr
                ? 'لا توجد سيناريوهات تطابق التصفية — جرّب إزالة بعض المرشّحات.'
                : 'No scenarios match the current filters — try clearing some.'} />
            ) : (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
                gap: 'var(--space-sm)',
              }}>
                {items.map(s => (
                  <ScenarioCard
                    key={s.id} s={s} isAr={isAr}
                    pinned={popularIds.has(s.id) && activeFilterCount === 0}
                    onClick={() => navigate(`/bcm/scenarios/${s.id}`)}
                  />
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </Shell>
  );
}

// ─── Card component ────────────────────────────────────────────────────

function ScenarioCard({
  s, isAr, pinned, onClick,
}: {
  s: ScenarioSummary;
  isAr: boolean;
  pinned?: boolean;
  onClick: () => void;
}) {
  const frameworks = parseFrameworks(s.frameworkAlignment);
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } }}
      style={{
        padding: 'var(--space-sm)',
        border: '1px solid var(--border)',
        borderRadius: 8,
        background: 'var(--surface)',
        cursor: 'pointer',
        display: 'flex', flexDirection: 'column', gap: 8,
        transition: 'box-shadow 0.15s ease, transform 0.15s ease',
      }}
      onMouseEnter={e => {
        e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.08)';
        e.currentTarget.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={e => {
        e.currentTarget.style.boxShadow = '';
        e.currentTarget.style.transform = '';
      }}
    >
      {/* Title row */}
      <div style={{ display: 'flex', alignItems: 'start', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontSize: 14, fontWeight: 600,
            overflow: 'hidden', textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}>
            {isAr ? (s.titleAr || s.titleEn) : s.titleEn}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
            {s.scenarioCode}
          </div>
        </div>
        {pinned && (
          <Badge tone="warning" title={isAr ? 'الأكثر استخداماً' : 'Popular'}>
            <Sparkles size={11} style={{ verticalAlign: 'text-top' }} />
          </Badge>
        )}
      </div>

      {/* Tag row */}
      <div className="cluster" style={{ gap: 4, flexWrap: 'wrap' }}>
        {s.scenarioType && (
          <Badge tone={scenarioTypeTone(s.scenarioType)}>
            {tlabel(SCENARIO_TYPE_LABELS, s.scenarioType, isAr)}
          </Badge>
        )}
        {s.complexity && (
          <Badge tone={complexityTone(s.complexity)}>
            {tlabel(COMPLEXITY_LABELS, s.complexity, isAr)}
          </Badge>
        )}
      </div>

      {/* Framework chips */}
      {frameworks.length > 0 && (
        <div className="cluster" style={{ gap: 4, flexWrap: 'wrap' }}>
          {frameworks.slice(0, 4).map(code => (
            <span
              key={code}
              style={{
                fontSize: 10, padding: '2px 6px',
                background: 'var(--surface-2, #f3f4f6)',
                borderRadius: 3,
                color: 'var(--ink-3)',
                whiteSpace: 'nowrap',
              }}
            >
              {frameworkLabel(code, isAr)}
            </span>
          ))}
          {frameworks.length > 4 && (
            <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>
              {isAr ? `+${frameworks.length - 4}` : `+${frameworks.length - 4} more`}
            </span>
          )}
        </div>
      )}

      {/* Footer stats */}
      <div className="cluster" style={{
        gap: 12, fontSize: 11, color: 'var(--ink-3)',
        marginTop: 'auto',
      }}>
        <span title={isAr ? 'المدة المُوصى بها' : 'Recommended duration'}>
          <Clock size={11} style={{ verticalAlign: 'text-top' }} />
          {' '}
          {fmtDuration(s.recommendedDurationMinutes ?? s.durationMinutes, isAr)}
        </span>
        <span title={isAr ? 'عدد مرات الاستخدام' : 'Times instantiated'}>
          <TrendingUp size={11} style={{ verticalAlign: 'text-top' }} />
          {' '}
          {s.usageCount ?? 0}
        </span>
        {s.targetAudience && (
          <span title={s.targetAudience}>
            <Users size={11} style={{ verticalAlign: 'text-top' }} />
            {' '}
            {(s.targetAudience.split(',').length)} {isAr ? 'أدوار' : 'roles'}
          </span>
        )}
      </div>
    </div>
  );
}
