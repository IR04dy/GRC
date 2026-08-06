/**
 * Avatar — circular initials with a caller-supplied colour.
 *
 * F2-38. This used to live in `UserPicker.tsx` beside a type-ahead people picker that had **zero
 * consumers**: the only `<UserPicker` occurrence in the repository was inside its own doc comment.
 * That picker read `lib/userDirectory` — 146 lines of invented users (`usr-001`, `ceo@org.sa`, E.164
 * mobiles that belong to nobody), and it was the obvious component to reuse for the F2-26 committee
 * seat picker, which would have bound governance seats to fabricated identities. Deleting it removes
 * the trap rather than documenting it.
 *
 * <p>The prop type is **structural on purpose**. It was `User` from `lib/userDirectory`, which meant
 * a presentational circle depended on the fabricated directory's shape; anything rendering a real
 * `iam.users` row had to fake that type first. Now it asks for the four fields it draws.
 */
export interface AvatarSubject {
  nameEn: string;
  initials: string;
  avatarColor: string;
  /**
   * Drawn as a green corner dot when `showOnCall` is set.
   *
   * ⚠ Optional and unset by every real caller today. `lib/userDirectory` documents this as *"whether
   * currently on call (for crisis routing)"*, which reads as though a crisis path consumes it —
   * measured, and it does not: nothing routes on this flag anywhere in the product. Treat it as
   * decoration until something real feeds it.
   */
  onCall?: boolean;
}

export function Avatar({ user, size = 28, showOnCall = true }: {
  user: AvatarSubject;
  size?: number;
  showOnCall?: boolean;
}) {
  return (
    <div style={{
      position: 'relative',
      width: size, height: size, borderRadius: '50%',
      background: user.avatarColor,
      color: 'white', fontWeight: 700,
      fontSize: Math.max(9, size * 0.42),
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }} title={user.nameEn}>
      {user.initials}
      {showOnCall && user.onCall && (
        <span style={{
          position: 'absolute', bottom: -1, insetInlineEnd: -1,
          width: Math.max(6, size * 0.3), height: Math.max(6, size * 0.3),
          borderRadius: '50%', background: 'var(--success)',
          border: '1.5px solid var(--surface-1)',
        }} />
      )}
    </div>
  );
}
