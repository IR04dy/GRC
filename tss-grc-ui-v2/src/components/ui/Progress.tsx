type Tone = 'primary' | 'accent' | 'danger' | 'success';

const FILLS: Record<Tone, string> = {
  primary: 'linear-gradient(90deg, var(--primary-300), var(--primary))',
  accent:  'linear-gradient(90deg, var(--accent-300), var(--accent))',
  danger:  'linear-gradient(90deg, #E8A878, var(--danger))',
  success: 'linear-gradient(90deg, #7FC79A, var(--success-fg))',
};
const FGS: Record<Tone, string> = {
  primary: 'var(--primary-700)',
  accent:  'var(--accent-700)',
  danger:  'var(--danger-fg)',
  success: 'var(--success-fg)',
};

export function Progress({ label, value, tone = 'primary', onClick }: { label: string; value: number; tone?: Tone; onClick?: () => void }) {
  const safe = Math.max(0, Math.min(100, value));
  const clickable = !!onClick;
  return (
    <div
      className={`progress-row${clickable ? ' progress-row-clickable' : ''}`}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onClick={clickable ? onClick : undefined}
      onKeyDown={clickable ? (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick!(); }
      } : undefined}
      style={clickable ? { cursor: 'pointer' } : undefined}
    >
      <div className="progress-label">
        <span>{label}</span>
        <span style={{ color: FGS[tone], fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{safe}%</span>
      </div>
      <div className="progress-track">
        <div className="progress-fill" style={{ width: `${safe}%`, background: FILLS[tone] }} />
      </div>
    </div>
  );
}
