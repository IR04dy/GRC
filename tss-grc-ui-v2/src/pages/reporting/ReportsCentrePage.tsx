import { useState } from 'react';
import {
  FileDown, FileText, FileType2, Table, Presentation, Globe,
  ShieldAlert, ClipboardList, Activity, BookMarked, Languages, Library,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { reportingApi, ApiError } from '../../lib/api';

/**
 * Reports Centre — on-demand document-export hub.
 *
 * The backend ExportController (com.tss.grc.module.reporting) renders every
 * board-grade report through the real document engines — OpenPDF (PDF, Arabic
 * RTL), Apache POI XWPFDocument (Word) + XSSFWorkbook (Excel), and the
 * board-pack PPTX engine. Those endpoints were only ever reached piecemeal
 * from a handful of register pages, and the Word (.docx) variants weren't
 * wired to any UI at all.
 *
 * This hub surfaces the complete matrix in one place: every standard report ×
 * every format it supports, with a document-language switch (the reports carry
 * their own Arabic RTL rendering independent of the UI locale). One click
 * streams the file straight from the engine.
 */

type Fmt = 'PDF' | 'DOCX' | 'XLSX' | 'PPTX';

interface ReportDef {
  id: string;
  Icon: typeof FileText;
  /** category accent token 1..8 */
  cat: number;
  nameEn: string;
  nameAr: string;
  descEn: string;
  descAr: string;
  regEn: string;
  regAr: string;
  /** download fn per supported format; lang passed where the engine honours it */
  run: Partial<Record<Fmt, (lang: string) => Promise<void>>>;
}

// Standard report catalog — mirrors ExportController's @GetMapping surface.
const REPORTS: ReportDef[] = [
  {
    id: 'risk-register',
    Icon: ShieldAlert,
    cat: 1,
    nameEn: 'Enterprise risk register',
    nameAr: 'سجل المخاطر المؤسسي',
    descEn: 'Full risk register snapshot — inherent and residual scoring, treatment status and owners, ranked by residual exposure.',
    descAr: 'لقطة كاملة من سجل المخاطر — التقييم الكامن والمتبقي وحالة المعالجة والمُلّاك، مرتّبة حسب التعرّض المتبقي.',
    regEn: 'SAMA ERM · ISO 31000 board reporting',
    regAr: 'إدارة المخاطر — ساما · إيزو 31000 لتقارير المجلس',
    run: {
      PDF:  (lang) => reportingApi.riskRegisterPdf(lang),
      DOCX: (lang) => reportingApi.riskRegisterDocx(lang),
      XLSX: () => reportingApi.riskRegisterXlsx(),
    },
  },
  {
    id: 'findings-aging',
    Icon: ClipboardList,
    cat: 3,
    nameEn: 'Findings aging',
    nameAr: 'تقادُم الملاحظات',
    descEn: 'Open audit and compliance findings bucketed by age, with severity, source and remediation due dates for the closure pipeline.',
    descAr: 'الملاحظات المفتوحة من التدقيق والامتثال مصنّفة حسب العمر، مع الخطورة والمصدر وتواريخ استحقاق المعالجة لمسار الإغلاق.',
    regEn: 'Internal Audit · SAMA audit committee pack',
    regAr: 'التدقيق الداخلي · حزمة لجنة المراجعة لساما',
    run: {
      PDF:  (lang) => reportingApi.findingsPdf(lang),
      DOCX: (lang) => reportingApi.findingsDocx(lang),
      XLSX: () => reportingApi.findingsXlsx(),
    },
  },
  {
    id: 'kri-dashboard',
    Icon: Activity,
    cat: 5,
    nameEn: 'KRI dashboard',
    nameAr: 'لوحة مؤشرات المخاطر الرئيسية',
    descEn: 'Key risk indicators against thresholds and appetite, with breach events and trend direction for the risk committee.',
    descAr: 'مؤشرات المخاطر الرئيسية مقابل الحدود والشهية، مع أحداث التجاوز واتجاه المؤشر للجنة المخاطر.',
    regEn: 'Board risk appetite · KRI breach monitoring',
    regAr: 'شهية المخاطر للمجلس · رصد تجاوز مؤشرات المخاطر',
    run: {
      PDF:  (lang) => reportingApi.kriPdf(lang),
      DOCX: (lang) => reportingApi.kriDocx(lang),
      XLSX: () => reportingApi.kriXlsx(),
    },
  },
  {
    id: 'board-pack',
    Icon: BookMarked,
    cat: 7,
    nameEn: 'Executive board pack',
    nameAr: 'حزمة المجلس التنفيذية',
    descEn: 'Multi-section executive pack — posture summary, top risks, compliance heat, incidents and KRIs assembled into a single board document.',
    descAr: 'حزمة تنفيذية متعدّدة الأقسام — ملخّص الوضع وأبرز المخاطر وحرارة الامتثال والحوادث والمؤشرات في مستند مجلس واحد.',
    regEn: 'Quarterly board governance · SAMA governance pack',
    regAr: 'حوكمة المجلس الربعية · حزمة الحوكمة لساما',
    run: {
      PDF:  (lang) => reportingApi.boardPackPdf(lang),
      PPTX: (lang) => reportingApi.boardPackPptx(lang),
    },
  },
];

const FMT_META: Record<Fmt, { Icon: typeof FileText; labelEn: string; labelAr: string; tone: 'danger' | 'info' | 'success' | 'warning' }> = {
  PDF:  { Icon: FileText,    labelEn: 'PDF',        labelAr: 'PDF',        tone: 'danger' },
  DOCX: { Icon: FileType2,   labelEn: 'Word',       labelAr: 'Word',       tone: 'info' },
  XLSX: { Icon: Table,       labelEn: 'Excel',      labelAr: 'Excel',      tone: 'success' },
  PPTX: { Icon: Presentation, labelEn: 'PowerPoint', labelAr: 'PowerPoint', tone: 'warning' },
};

const FMT_ORDER: Fmt[] = ['PDF', 'DOCX', 'XLSX', 'PPTX'];

export function ReportsCentrePage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  // Document language is independent of the UI locale — an Arabic-first
  // operator may still need an English pack for an overseas auditor. Defaults
  // to the active UI locale.
  const [docLang, setDocLang] = useState<'en' | 'ar'>(isAr ? 'ar' : 'en');
  const [busy, setBusy] = useState<string | null>(null);

  const formatCount = new Set(REPORTS.flatMap(r => Object.keys(r.run))).size;

  async function download(report: ReportDef, fmt: Fmt) {
    const fn = report.run[fmt];
    if (!fn) return;
    const key = `${report.id}:${fmt}`;
    setBusy(key);
    try {
      await fn(docLang);
      toast.success(t('exportStarted'));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('exportFailed'));
    } finally {
      setBusy(null);
    }
  }

  return (
    <Shell title={t('reportsCentreTitle')} sub={t('reportsCentreSub')}>
      <PageHeader
        eyebrow={t('reportsCentreEyebrow')}
        title={t('reportsCentreTitle')}
        sub={t('reportsCentreSub')}
        actions={
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Languages size={15} style={{ color: 'var(--ink-3)' }} />
            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('rcLangLabel')}</span>
            <div role="group" aria-label={t('rcLangLabel')} style={{
              display: 'inline-flex', border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)', overflow: 'hidden',
            }}>
              {(['en', 'ar'] as const).map(l => (
                <button
                  key={l}
                  onClick={() => setDocLang(l)}
                  aria-pressed={docLang === l}
                  style={{
                    padding: '6px 14px', fontSize: 12, fontWeight: 600, cursor: 'pointer',
                    border: 'none',
                    background: docLang === l ? 'var(--primary)' : 'var(--surface-2)',
                    color: docLang === l ? '#fff' : 'var(--ink-2)',
                  }}>
                  {l === 'en' ? 'English' : 'العربية'}
                </button>
              ))}
            </div>
          </div>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Library}  label={t('rcKpiReports')}   value={REPORTS.length} tone="primary" to="/reports/centre" />
        <Kpi Icon={FileDown} label={t('rcKpiFormats')}   value={formatCount}    tone="info"    to="/reports/centre" />
        <Kpi Icon={Globe}    label={t('rcKpiLanguages')} value={2}              tone="success" to="/reports/centre" />
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(380px, 1fr))',
        gap: 16,
      }}>
        {REPORTS.map(report => {
          const accent = `var(--cat-${report.cat})`;
          return (
            <Card key={report.id} padded>
              <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
                <div style={{
                  width: 44, height: 44, flexShrink: 0, borderRadius: 'var(--r-md)',
                  background: `color-mix(in srgb, ${accent} 14%, transparent)`,
                  color: accent,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <report.Icon size={22} strokeWidth={2} />
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: 'var(--ink-0)' }}>
                    {isAr ? report.nameAr : report.nameEn}
                  </h3>
                  <p style={{ margin: '6px 0 0', fontSize: 12.5, lineHeight: 1.6, color: 'var(--ink-3)' }}>
                    {isAr ? report.descAr : report.descEn}
                  </p>
                </div>
              </div>

              <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px dashed var(--border)' }}>
                <div style={{
                  fontSize: 10.5, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase',
                  color: 'var(--ink-4)', marginBottom: 6,
                }}>
                  {t('rcRegulatoryHook')}
                </div>
                <Badge tone="neutral">
                  <BookMarked size={10} style={{ marginInlineEnd: 4 }} />
                  {isAr ? report.regAr : report.regEn}
                </Badge>
              </div>

              <div style={{ marginTop: 14 }}>
                <div style={{
                  fontSize: 10.5, fontWeight: 600, letterSpacing: 0.4, textTransform: 'uppercase',
                  color: 'var(--ink-4)', marginBottom: 8,
                }}>
                  {t('rcAvailableFormats')}
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {FMT_ORDER.filter(f => report.run[f]).map(fmt => {
                    const meta = FMT_META[fmt];
                    const key = `${report.id}:${fmt}`;
                    return (
                      <Button
                        key={fmt}
                        variant="secondary"
                        size="sm"
                        Icon={meta.Icon}
                        disabled={busy === key}
                        onClick={() => void download(report, fmt)}
                      >
                        {isAr ? meta.labelAr : meta.labelEn}
                      </Button>
                    );
                  })}
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </Shell>
  );
}
