import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, UserX, RefreshCw, ShieldAlert } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { getRosterCoverage, type RosterCoverageReport, type RosterCoverageRow } from '../../lib/committees';

/**
 * F2-178 — tenant-wide roster-linkage coverage.
 *
 * **Why this panel exists when the per-committee report already did.** F2-26 built
 * `SeatLinkageReport` and wired it to the member-registry screen, so a gap has been visible one
 * committee at a time ever since. That is enough to fix a roster you are already looking at and
 * useless for learning that you have a problem: production has **0 of 31 seats linked** and **11 of
 * 17 committees with no roster at all**, and finding that out required opening seventeen registry
 * screens in turn. Nobody did.
 *
 * **Why it matters more than it did in F2-26.** Back then an unlinked seat meant a vote had to be
 * recorded on the member's behalf — an audit-trail blemish. F2-177 made a seat an authorisation
 * subject (`hasPermission(type,'COMMITTEE','SEAT')`), so an unlinked roster is now the reason six
 * assurance views are closed to the committee they were built for. `currentLinked === 0` is that
 * statement, per committee.
 *
 * **The panel self-hides for personas without `ADMIN:READ`**, matching `ChangeAuthorityPanel`:
 * leaving a permanent error card on a screen someone is otherwise entitled to use trains them to
 * ignore the card.
 */
export function RosterCoveragePanel() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [rep, setRep] = useState<RosterCoverageReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [expanded, setExpanded] = useState(false);

  async function load() {
    setLoading(true);
    try {
      setRep(await getRosterCoverage());
      setDenied(false);
    } catch {
      setDenied(true);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  if (denied || (loading && !rep)) return null;
  if (!rep || rep.totalCommittees === 0) return null;

  // Everything green: one line, collapsed. A report that occupies the same space whether or not
  // there is anything to do is a report that gets scrolled past.
  const clean = rep.committeesAdmittingNobody === 0 && rep.totalUnlinked === 0;

  /** The three states a row can be in, worst first. Empty roster is NOT 0% coverage: a committee
   *  with no seats can never be quorate, which is a different defect with a different remedy. */
  function rowTone(r: RosterCoverageRow): 'danger' | 'warning' | 'success' {
    if (!r.hasRoster) return 'danger';
    if (r.currentLinked === 0) return 'danger';
    return r.unlinked > 0 ? 'warning' : 'success';
  }
  function rowLabel(r: RosterCoverageRow): string {
    if (!r.hasRoster) return isAr ? 'لا يوجد أعضاء' : 'No roster';
    if (r.currentLinked === 0) return isAr ? 'لا يُخوِّل أحداً' : 'Admits nobody';
    return r.unlinked > 0
      ? (isAr ? `${r.unlinked} غير مرتبط` : `${r.unlinked} unlinked`)
      : (isAr ? 'مكتمل' : 'Complete');
  }

  const shown = expanded ? rep.committees : rep.committees.filter(r => rowTone(r) !== 'success');

  return (
    <Card
      title={isAr ? 'تغطية ربط مقاعد اللجان' : 'Committee roster linkage coverage'}
      Icon={clean ? Users : ShieldAlert}
      action={
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()}>
            {isAr ? 'تحديث' : 'Refresh'}
          </Button>
          {rep.committees.some(r => rowTone(r) === 'success') && (
            <Button size="sm" variant="ghost" onClick={() => setExpanded(v => !v)}>
              {expanded
                ? (isAr ? 'إظهار الفجوات فقط' : 'Show gaps only')
                : (isAr ? 'إظهار كل اللجان' : 'Show all committees')}
            </Button>
          )}
        </div>
      }>

      <p style={{ margin: '0 0 12px', fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.6 }}>
        {isAr
          ? `${rep.totalLinked} من ${rep.totalSeats} مقعد نشط مرتبط بمستخدم في المنصة، و${rep.totalDeclaredExternal} مُصرَّح بأنه خارجي، و${rep.totalUnlinked} لا هذا ولا ذاك. `
            + `${rep.committeesAdmittingNobody} من ${rep.totalCommittees} لجنة لا تُخوِّل أي مستخدم اليوم — فالمقعد صار أساساً للتخويل، لذا الشاشات الرقابية المخصَّصة لأعضائها مُغلقة أمامهم.`
          : `${rep.totalLinked} of ${rep.totalSeats} active seats name a platform user, ${rep.totalDeclaredExternal} are declared external, and ${rep.totalUnlinked} are neither. `
            + `${rep.committeesAdmittingNobody} of ${rep.totalCommittees} committees admit nobody today — a seat is now an authorisation subject, so the assurance screens built for these members are closed to them.`}
        {rep.totalCurrentLinked < rep.totalLinked && (
          <>
            {' '}
            <strong style={{ color: 'var(--danger)' }}>
              {isAr
                ? `${rep.totalLinked - rep.totalCurrentLinked} مقعد مرتبط انتهت مدته وما زال مُسجَّلاً «نشطاً» — لا يمنح أي صلاحية.`
                : `${rep.totalLinked - rep.totalCurrentLinked} linked seats have lapsed while still reading ACTIVE — they confer nothing.`}
            </strong>
          </>
        )}
        {rep.dissolvedExcluded > 0 && (
          <>
            {' '}
            {isAr
              ? `(استُثنيت ${rep.dissolvedExcluded} لجنة مُنحلَّة: اللجنة المنحلَّة لا تمنح مقعداً.)`
              : `(${rep.dissolvedExcluded} dissolved committee${rep.dissolvedExcluded === 1 ? '' : 's'} excluded — a dissolved body confers no seat.)`}
          </>
        )}
      </p>

      {shown.length === 0 ? (
        <p style={{ margin: 0, fontSize: 13, color: 'var(--success)' }}>
          {isAr
            ? 'كل لجنة نشطة لديها مقعد واحد على الأقل مرتبط بمستخدم في المنصة.'
            : 'Every live committee has at least one seat linked to a platform user.'}
        </p>
      ) : (
        <table className="grc-table">
          <thead>
            <tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th>
              <th>{isAr ? 'اللجنة' : 'Committee'}</th>
              <th>{isAr ? 'المقاعد' : 'Seats'}</th>
              <th>{isAr ? 'مرتبط' : 'Linked'}</th>
              <th>{isAr ? 'خارجي' : 'External'}</th>
              <th title={isAr ? 'المقاعد المصوّتة المرتبطة' : 'Voting seats that can self-vote'}>
                {isAr ? 'تصويت' : 'Voting'}
              </th>
              <th title={isAr
                ? 'المقاعد التي يقبلها حَرَس المقعد اليوم — سارية المدة ومرتبطة'
                : 'Seats the seat guard admits today — in-window and linked'}>
                {isAr ? 'يُخوِّل' : 'Admits'}
              </th>
              <th>{isAr ? 'الحالة' : 'State'}</th>
              <th style={{ textAlign: 'right' }}>{isAr ? 'إجراء' : 'Action'}</th>
            </tr>
          </thead>
          <tbody>
            {shown.map(r => (
              <tr key={r.committeeId}>
                <td className="mono">{r.code}</td>
                <td>{isAr && r.nameAr ? r.nameAr : r.nameEn}</td>
                <td className="mono">{r.seats}</td>
                <td className="mono">{r.linked}</td>
                <td className="mono">{r.declaredExternal || '—'}</td>
                <td className="mono">{r.votingSeats > 0 ? `${r.votingLinked}/${r.votingSeats}` : '—'}</td>
                <td className="mono" style={{ color: r.currentLinked === 0 ? 'var(--danger)' : undefined }}>
                  {r.currentSeats > 0 ? `${r.currentLinked}/${r.currentSeats}` : '—'}
                </td>
                <td><Badge tone={rowTone(r)}>{rowLabel(r)}</Badge></td>
                <td style={{ textAlign: 'right' }}>
                  <Link to={`/committees/${r.committeeId}/members`}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 4,
                                 fontSize: 12, color: 'var(--primary)' }}>
                    {r.hasRoster ? <Users size={12} /> : <UserX size={12} />}
                    {r.hasRoster
                      ? (isAr ? 'ربط المقاعد' : 'Link seats')
                      : (isAr ? 'تعيين أعضاء' : 'Appoint members')}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
