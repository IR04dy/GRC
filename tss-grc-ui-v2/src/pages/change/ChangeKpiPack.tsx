/**
 * S8b / CE-12 — the ITIL/ISO KPI pack (G-12).
 *
 * **This component's job is to refuse to flatter the data.** §12 of the uplift plan asks for twenty
 * percentages; measured, seven have no source at all, one covers 2 rows out of 17, and one is complete
 * but rests entirely on values a human typed. So every tile renders three things — the figure, what it
 * was computed over, and whether it was measured or asserted — and a tile with no source renders the
 * *reason* rather than disappearing.
 *
 * Three rules, each the opposite of what a dashboard normally does:
 *
 *  1. **A missing metric stays on screen.** `NOT_MEASURABLE` tiles show what is missing
 *     (`blockedBy` → a translated sentence). Dropping them would leave a page with no visible gaps,
 *     which reads as "fully instrumented" — the F2-42 shape.
 *  2. **`null` never renders as 0.** "No change has closed yet" and "no closed change succeeded" are
 *     different facts and a zero merges them.
 *  3. **A partial or asserted figure is marked on the tile, not in a footnote.** F2-107 shipped a
 *     100% success rate for weeks because the denominator was invisible.
 */
import { Activity, AlertTriangle, HelpCircle, PenLine } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import type { ChangeAnalyticsDto, ChangeKpi } from '../../lib/api';
import { useI18n } from '../../lib/i18n';

/** Change-side metrics first, CAB second — the server sends them in this order and we keep it. */
export const CHANGE_KPI_KEYS = [
  'emergencyRatio', 'successRate', 'failedChangeRate', 'rollbackRate', 'unauthorisedChangeCount',
  'leadTimeSubmitToDecisionDays', 'leadTimeDecisionToImplementedDays', 'scheduleAdherenceRate',
  'pirCompletionRate', 'openConditionsOverdue',
];
export const CAB_KPI_KEYS = [
  'cabMeetingsHeld', 'cabMeetingsHeldRate', 'cabQuorumMetRate', 'cabItemsDecidedPerMeeting',
  'cabDeferralRate', 'cabTimeInQueueDays', 'cabMemberAttendanceRate', 'cabAgendaNoticeCompliance',
  'cabTermsExpiring',
];

/** Tone by basis. NOT_MEASURABLE is deliberately neutral: a gap is not an alarm, it is a gap. */
const BASIS_TONE: Record<string, 'success' | 'warning' | 'neutral' | 'danger'> = {
  COMPUTED: 'success',
  COMPUTED_PARTIAL: 'warning',
  NO_DATA: 'neutral',
  NOT_MEASURABLE: 'neutral',
};

export function formatKpi(k: ChangeKpi): string {
  if (k.value == null) return '—';
  switch (k.unit) {
    case 'PCT': return `${k.value}%`;
    case 'DAYS': return `${k.value}d`;
    case 'RATIO_PER_MEETING': return `${k.value}`;
    default: return `${k.value}`;
  }
}

export function KpiTile({ k }: { k: ChangeKpi }) {
  const { t } = useI18n();
  const notMeasurable = k.basis === 'NOT_MEASURABLE';
  const asserted = k.provenance === 'ASSERTED' || k.provenance === 'MIXED';

  return (
    <div className="changes-kpi-tile" data-basis={k.basis} data-provenance={k.provenance ?? ''}>
      <div className="changes-kpi-head">
        <span className="changes-kpi-label">{t('changeKpi_' + k.key)}</span>
        {/* ⚠ The asserted marker is on the tile, beside the number. A legend at the bottom of the
            page is a footnote, and F2-107 is what footnotes achieve. */}
        {asserted && (
          <span className="changes-kpi-asserted" title={t('changeKpiProvenance_' + k.provenance)}>
            <PenLine size={12} /> {t('changeKpiProvenance_' + k.provenance)}
          </span>
        )}
      </div>

      <div className="changes-kpi-value" data-empty={k.value == null ? '1' : '0'}>
        {formatKpi(k)}
      </div>

      {notMeasurable ? (
        <p className="changes-kpi-blocked">
          <HelpCircle size={12} /> {t('changeKpiBlocked_' + (k.blockedBy ?? 'UNKNOWN'))}
        </p>
      ) : (
        <div className="changes-kpi-meta">
          <Badge tone={BASIS_TONE[k.basis] ?? 'neutral'}>{t('changeKpiBasis_' + k.basis)}</Badge>
          {/* The denominator, always. For DAYS it is the sample size, which is the same warning in
              a different unit: a median over two changes is not a median. */}
          {k.denominator != null && k.denominator > 0 && (
            <span className="changes-kpi-over">
              {k.unit === 'DAYS'
                ? t('changeKpiSample').replace('{n}', String(k.denominator))
                : t('changeKpiOver')
                    .replace('{n}', String(k.numerator ?? 0))
                    .replace('{d}', String(k.denominator))}
            </span>
          )}
          {/* Only shown when it differs — repeating the denominator would be noise. */}
          {k.basis === 'COMPUTED_PARTIAL' && k.population != null && (
            <span className="changes-kpi-partial">
              <AlertTriangle size={12} />
              {t('changeKpiPartial').replace('{d}', String(k.denominator ?? 0))
                                    .replace('{p}', String(k.population))}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

export function ChangeKpiPack({ analytics }: { analytics: ChangeAnalyticsDto }) {
  const { t } = useI18n();
  const kpis = analytics.kpis;
  // A deployment without the KPI service sends no `kpis` key. Render nothing rather than an empty
  // shell — but this is the ONLY case where a tile may vanish.
  if (!kpis || Object.keys(kpis).length === 0) return null;

  const groups: Array<[string, string[]]> = [
    ['changeKpiGroupChange', CHANGE_KPI_KEYS],
    ['changeKpiGroupCab', CAB_KPI_KEYS],
  ];
  const measurable = Object.values(kpis).filter(k => k.basis !== 'NOT_MEASURABLE').length;
  const total = Object.keys(kpis).length;

  return (
    <Card
      title={t('changeKpiPackTitle')}
      Icon={Activity}
      action={
        <Badge tone={measurable === total ? 'success' : 'warning'} dot>
          {t('changeKpiCoverage').replace('{n}', String(measurable)).replace('{t}', String(total))}
        </Badge>
      }
    >
      <p className="changes-kpi-sub">{t('changeKpiPackSub')}</p>
      {groups.map(([label, keys]) => {
        const present = keys.map(key => kpis[key]).filter(Boolean);
        if (present.length === 0) return null;
        return (
          <div key={label} className="changes-kpi-group">
            <h4 className="changes-kpi-group-title">{t(label)}</h4>
            <div className="changes-kpi-grid">
              {present.map(k => <KpiTile key={k.key} k={k} />)}
            </div>
          </div>
        );
      })}
    </Card>
  );
}
