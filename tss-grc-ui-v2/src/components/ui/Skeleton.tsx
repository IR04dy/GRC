import { cn } from '../../lib/cn';

/**
 * Single shimmering bar — drop into a cell while data is loading.
 *
 * Defaults to a block-level line; pass {@link width} to constrain it inside
 * an inline context like a table cell, or set {@link inline} for an inline
 * dash that pairs with surrounding text.
 *
 * @example
 *   {isLoading ? <Skeleton width="60%" /> : <span>{user.fullName}</span>}
 */
export function Skeleton({
  width, height, className, inline, pill,
}: {
  /** CSS width (e.g. '60%', '120px'). Defaults to 100% block. */
  width?: string | number;
  /** CSS height. Defaults to 12px (matches text-xs ascender height). */
  height?: string | number;
  className?: string;
  /** Render inline-block instead of block so it sits next to text. */
  inline?: boolean;
  /** Pill-shaped (for badge placeholders). */
  pill?: boolean;
}) {
  const style: React.CSSProperties = {};
  if (width !== undefined) style.width = typeof width === 'number' ? `${width}px` : width;
  if (height !== undefined) style.height = typeof height === 'number' ? `${height}px` : height;
  return (
    <span
      aria-busy="true"
      aria-live="polite"
      className={cn(
        'skeleton',
        !inline && !pill && 'skeleton-line',
        pill && 'skeleton-pill',
        className,
      )}
      style={style}
    />
  );
}

/**
 * A canned skeleton row for tables — renders N cells with shimmering bars
 * of slightly varied width so the row doesn't look like a barcode.
 *
 * @example
 *   <tbody>
 *     {isLoading
 *       ? Array.from({ length: 5 }, (_, i) => <SkeletonRow key={i} cols={6} />)
 *       : rows.map(r => <DataRow key={r.id} row={r} />)}
 *   </tbody>
 */
export function SkeletonRow({ cols }: { cols: number }) {
  // Pseudo-random but stable widths so successive rows aren't identical.
  // Cycles through 6 widths — gives a natural-looking column-content variance.
  const widths = ['78%', '52%', '90%', '46%', '68%', '60%'];
  return (
    <tr aria-busy="true">
      {Array.from({ length: cols }, (_, i) => (
        <td key={i}>
          <Skeleton width={widths[i % widths.length]} />
        </td>
      ))}
    </tr>
  );
}
