import { Badge } from '../ui/Badge';
import type { RegulatoryTemplate } from '../../lib/api';

/**
 * F2-281 — discloses whether an incident has been classified as affecting
 * personal data, on the PDPL Article 20 notification card.
 *
 * `applicabilityHint` is prose the reader has to judge, and that is **right**
 * for SAMA (*"for SAMA-licensed financial institutions"*) and CITC: the platform
 * cannot know whether its tenant is a bank or a telco. It is **wrong** for
 * Article 20, which is exactly the question `cyber.incidents.affects_personal_data`
 * answers — a column the platform had stored on the row all along and no wire
 * carried until F2-281.
 *
 * Two rules this component exists to keep:
 *
 *  1. `NOT_CLASSIFIED` renders **neutral**, never `success`. It means nobody has
 *     assessed the incident yet, and a green all-clear on an unassessed statutory
 *     clock is the worse error of the two (F2-197) — an invented figure invites a
 *     question, an invented all-clear closes one.
 *  2. It renders on the Article 20 card only. Extending it to SAMA/CITC/HRSD
 *     would present a classification the platform holds as though it settled an
 *     applicability question the platform cannot answer.
 *
 * The template is still offered and still shows its deadline in both cases: the
 * classification is disclosed, never used to suppress a statutory notification
 * (F2-196 — under-scoping a statutory obligation is what an examiner writes up).
 */
export function PdplApplicabilityBadge({ tmpl, isAr }: {
  tmpl: RegulatoryTemplate;
  isAr: boolean;
}) {
  if (tmpl.code !== 'DATA_OFFICE') return null;
  const confirmed = tmpl.piiClassification === 'CONFIRMED';
  return (
    <div style={{ marginTop: 6 }}>
      <Badge tone={confirmed ? 'danger' : 'neutral'} dot>
        {confirmed
          ? (isAr ? 'بيانات شخصية: مؤكَّد' : 'Personal data: confirmed')
          : (isAr ? 'بيانات شخصية: لم يُقيَّم بعد' : 'Personal data: not yet assessed')}
      </Badge>
    </div>
  );
}
