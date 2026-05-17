// Preventive-Action triggers — DD-43.
//
// A "trigger" is a current state in the platform that *should*
// give rise to a Preventive Action (PA) before the underlying
// risk turns into an actual incident. Sources we sweep:
//
//   1. KRI breach: any KRI whose latest reading is in AMBER or
//      RED, where no open PA already addresses that KRI.
//   2. Risk trend up: a residual risk whose recent re-assessments
//      show an upward score trend (here approximated by the
//      review-date age + status — a real implementation would
//      use historic residual-score samples).
//   3. CAPA recurrence pattern: when a CAPA's root-cause category
//      already accounts for >= 30% of all closed CAPAs, file a
//      systemic PA against that category.
//
// Standards anchor:
//   ISO 9001:2015 §10.2.1 — Action to address risks of recurrence
//                            (PA is the canonical preventive lane)
//   ISO 27001:2022 §10.1   — Continual improvement, including
//                              proactive identification
//   COSO ERM Component 4   — Risk Assessment + Response: Risk
//                              treatment includes preventive action
//   IIA IPPF 2010.A2       — Risk-based plans must consider
//                              risks not yet realized
//
// industry-standard GRC tooling equivalent: the `risk_signal` table that feeds
// the "Suggested PA" widget on the CAPA workspace.

import type { Capa } from './capa';
import { CAPAS } from './capa';
import { KRIS, KRI_TRENDS_BY_ID, MOCK_RISKS } from '../pages/risk/mockRisks';
import type { KriDefinitionDto } from './api';

export type PaTriggerKind = 'KRI_BREACH' | 'RISK_TREND' | 'RECURRENCE_PATTERN';

export interface PaTrigger {
  id: string;
  kind: PaTriggerKind;
  /** What the user sees in the trigger row title. */
  titleEn: string;
  titleAr: string;
  /** Why this trigger fired — fed into the new PA's description. */
  rationaleEn: string;
  rationaleAr: string;
  /** Suggested severity for the resulting PA. */
  suggestedSeverity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  /** Suggested owner — fed into the new PA. */
  suggestedOwner: string;
  /** Source record key (KRI:1006 / RISK:R-0040 / RC:PROCESS). */
  sourceRecordKey: string;
  sourceRecordTitle: string;
  /** Relevant authority/framework references. */
  frameworkRefs: string[];
  /** Already covered by an existing open PA? (controls the badge.) */
  alreadyCovered: boolean;
}

// ── KRI breach detection ──────────────────────────────────────────

/** RAG band of a single reading against a KRI's thresholds. */
function ragForKri(latest: number, k: KriDefinitionDto): 'GREEN' | 'AMBER' | 'RED' {
  const greenMax = k.greenMax ?? Number.POSITIVE_INFINITY;
  const amberMax = k.amberMax ?? Number.POSITIVE_INFINITY;
  if (k.direction === 'HIGHER_IS_WORSE') {
    if (latest <= greenMax) return 'GREEN';
    if (latest <= amberMax) return 'AMBER';
    return 'RED';
  }
  // LOWER_IS_WORSE: greenMax is the FLOOR, amberMax is below that
  if (latest >= greenMax) return 'GREEN';
  if (latest >= amberMax) return 'AMBER';
  return 'RED';
}

function severityForRag(rag: 'GREEN' | 'AMBER' | 'RED'): PaTrigger['suggestedSeverity'] {
  return rag === 'RED' ? 'HIGH' : 'MEDIUM';
}

function alreadyCoveredByPa(items: Capa[], sourceRecordKey: string): boolean {
  return items.some(c =>
    c.type === 'PREVENTIVE' &&
    c.sourceRecordKey === sourceRecordKey &&
    c.status !== 'CLOSED' && c.status !== 'CANCELLED',
  );
}

function frameworkRefForKri(k: KriDefinitionDto): string[] {
  const refs: string[] = [];
  if (k.framework === 'NCA_ECC') refs.push('NCA ECC');
  if (k.framework === 'PDPL') refs.push('PDPL');
  if (k.framework === 'SAMA_AML') refs.push('SAMA AML');
  if (k.framework === 'ISO_22301') refs.push('ISO 22301');
  if (k.framework === 'ISO_27001') refs.push('ISO 27001');
  refs.push('ISO 9001 §10.2.1');
  return refs;
}

function kriBreachTriggers(items: Capa[]): PaTrigger[] {
  const out: PaTrigger[] = [];
  for (const k of KRIS) {
    if (!k.isActive) continue;
    const trend = KRI_TRENDS_BY_ID[k.id];
    if (!trend || trend.length === 0) continue;
    const latest = trend[trend.length - 1];
    const rag = ragForKri(latest, k);
    if (rag === 'GREEN') continue;
    const sourceRecordKey = `KRI:${k.id}`;
    const unit = k.unit ?? '';
    out.push({
      id: `pa-trig-kri-${k.id}`,
      kind: 'KRI_BREACH',
      titleEn:    `KRI ${rag === 'RED' ? 'red' : 'amber'} — ${k.nameEn} (${latest} ${unit})`,
      titleAr:    `مؤشر ${rag === 'RED' ? 'أحمر' : 'كهرماني'} — ${k.nameAr ?? k.nameEn} (${latest} ${unit})`,
      rationaleEn:
        `${k.code} latest reading ${latest} ${unit} crosses the ${rag === 'RED' ? 'red (>' + (k.amberMax ?? '?') + ')' : 'amber (>' + (k.greenMax ?? '?') + ')'} threshold. Open a preventive action before it manifests as an incident or audit finding.`,
      rationaleAr:
        `${k.code} آخر قراءة ${latest} ${unit} تجاوزت العتبة ${rag === 'RED' ? 'الحمراء (>' + (k.amberMax ?? '?') + ')' : 'الكهرمانية (>' + (k.greenMax ?? '?') + ')'}. افتح إجراءً وقائياً قبل أن يظهر كحادثة أو ملاحظة مراجعة.`,
      suggestedSeverity: severityForRag(rag),
      suggestedOwner: k.owner ?? 'risk.officer@tss.sa',
      sourceRecordKey,
      sourceRecordTitle: `${k.code} — ${k.nameEn}`,
      frameworkRefs: frameworkRefForKri(k),
      alreadyCovered: alreadyCoveredByPa(items, sourceRecordKey),
    });
  }
  return out;
}

// ── Risk-trend detection ──────────────────────────────────────────
// We don't have historic residual-score samples in the demo data,
// so approximate "trending up" with the heuristic: residualScore is
// > 60% of inherentScore AND status is OPEN or UNDER_ASSESSMENT
// (i.e. treatment has not yet meaningfully reduced exposure). For
// an open risk with no residual yet, treat the inherent score as
// the residual (i.e. fully exposed).

function riskTrendTriggers(items: Capa[]): PaTrigger[] {
  const out: PaTrigger[] = [];
  for (const r of MOCK_RISKS) {
    if (r.status === 'CLOSED' || r.status === 'ACCEPTED') continue;
    const residual = r.residualScore ?? r.inherentScore;
    const ratio = r.inherentScore ? residual / r.inherentScore : 1;
    if (ratio < 0.6) continue;            // treatment is meaningfully working
    if (r.riskLevel === 'LOW') continue;  // low residual not worth a PA
    const sourceRecordKey = `RISK:${r.id}`;
    out.push({
      id: `pa-trig-risk-${r.id}`,
      kind: 'RISK_TREND',
      titleEn: `Residual still ≈ inherent — ${r.titleEn}`,
      titleAr: `الخطر المتبقي ≈ المتأصل — ${r.titleAr ?? r.titleEn}`,
      rationaleEn:
        `${r.riskCode}: residual score ${residual} of inherent ${r.inherentScore} (${Math.round(ratio * 100)}%). Treatment plan has not yet meaningfully reduced exposure; preventive action recommended to lower likelihood of materialising.`,
      rationaleAr:
        `${r.riskCode}: درجة الخطر المتبقية ${residual} من المتأصلة ${r.inherentScore} (${Math.round(ratio * 100)}%). خطة المعالجة لم تخفض الخطر بعد؛ يُوصى بإجراء وقائي.`,
      suggestedSeverity: r.riskLevel === 'CRITICAL' ? 'HIGH' : r.riskLevel === 'HIGH' ? 'HIGH' : 'MEDIUM',
      suggestedOwner: r.ownerUsername ?? 'risk.officer@tss.sa',
      sourceRecordKey,
      sourceRecordTitle: `${r.riskCode} — ${r.titleEn}`,
      frameworkRefs: ['ISO 31000 §6.5', 'ISO 27001 §10.1', 'COSO ERM Component 4'],
      alreadyCovered: alreadyCoveredByPa(items, sourceRecordKey),
    });
  }
  return out;
}

// ── Recurrence pattern detection ─────────────────────────────────

function recurrencePatternTriggers(items: Capa[]): PaTrigger[] {
  const out: PaTrigger[] = [];
  // count categories across all CAPAs
  const total = items.length || 1;
  const byCat: Record<string, number> = {};
  for (const c of items) {
    if (!c.rootCauseCategory) continue;
    byCat[c.rootCauseCategory] = (byCat[c.rootCauseCategory] ?? 0) + 1;
  }
  for (const [cat, count] of Object.entries(byCat)) {
    const pct = count / total;
    if (pct < 0.3) continue;
    const sourceRecordKey = `RC:${cat}`;
    if (alreadyCoveredByPa(items, sourceRecordKey)) continue;
    out.push({
      id: `pa-trig-rc-${cat}`,
      kind: 'RECURRENCE_PATTERN',
      titleEn: `Recurring root cause — ${cat} (${count} of ${total})`,
      titleAr: `سبب جذري متكرر — ${cat} (${count} من ${total})`,
      rationaleEn:
        `${count} CAPAs (${Math.round(pct * 100)}%) trace back to category ${cat}. Open a systemic preventive action against this category to break the recurrence pattern.`,
      rationaleAr:
        `${count} إجراءاً (${Math.round(pct * 100)}%) ترجع إلى فئة ${cat}. افتح إجراءً وقائياً منهجياً ضد هذه الفئة لكسر نمط التكرار.`,
      suggestedSeverity: 'MEDIUM',
      suggestedOwner: 'risk.officer@tss.sa',
      sourceRecordKey,
      sourceRecordTitle: `Root-cause pattern: ${cat}`,
      frameworkRefs: ['ISO 9001 §10.2.1', 'IIA IPPF 2500.A1'],
      alreadyCovered: false,
    });
  }
  return out;
}

// ── Public scanner ────────────────────────────────────────────────

/** Scan every signal source and return active PA triggers. */
export function scanPaTriggers(currentCapas: Capa[]): PaTrigger[] {
  // Use static seed CAPAS combined with the live overlay items so
  // alreadyCovered() sees PA records the user just created.
  const merged = [...CAPAS, ...currentCapas.filter(c => !CAPAS.some(s => s.id === c.id))];
  return [
    ...kriBreachTriggers(merged),
    ...riskTrendTriggers(merged),
    ...recurrencePatternTriggers(merged),
  ];
}
