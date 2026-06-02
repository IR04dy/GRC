/**
 * Shared gauge geometry — one source of truth for ring sizing.
 *
 * The arc primitives (`RadialGauge`, `CountdownRing`) take numeric SVG
 * `size` / `thickness` props, so the "token" here is a TS constant rather
 * than a CSS var. Before this, the same ring rendered at 96 / 104 / 108 /
 * 168 / 172px with strokes that differed by 1px at the same size — a 50%
 * framework gauge in Cyber sat at 96/9 while its BCM sibling sat at 108/10.
 *
 *  - `GAUGE_MD`   — in-grid dashboard rings (framework / programme / SLA).
 *  - `GAUGE_HERO` — the single focal metric on a page (posture, war-room).
 *
 * `DEFAULT_GAUGE_STROKE` keeps RadialGauge and CountdownRing on the same
 * stroke at their shared ~168px default size, so sibling rings match.
 *
 * Spread at the call site: `<RadialGauge value={v} {...GAUGE_MD} />`.
 */
export const GAUGE_MD = { size: 108, thickness: 10 } as const;
export const GAUGE_HERO = { size: 172, thickness: 15 } as const;

/** Shared default stroke for the ~168px base size (aligns the two primitives). */
export const DEFAULT_GAUGE_STROKE = 14;
