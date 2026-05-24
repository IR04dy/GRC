# Brand assets

Closes Open Issue **#010** — defines the file-naming convention, sizing requirements, and color palette for TSS GRC's logo + favicon assets. The three SVGs in this folder are **placeholders** — final TSS-supplied assets will replace them prior to go-live (Operations follow-up, not blocking).

---

## File inventory

| File | Status | Purpose | Spec |
|---|---|---|---|
| `logo-placeholder-light.svg` | Placeholder | App header / login on light backgrounds | viewBox 0 0 240 64 |
| `logo-placeholder-dark.svg`  | Placeholder | App header / login on dark backgrounds | viewBox 0 0 240 64 |
| `favicon-placeholder.svg`    | Placeholder | Browser favicon (modern browsers) | viewBox 0 0 32 32 |

Once final assets arrive, the convention is:

| Final filename | Purpose |
|---|---|
| `logo-light.svg` | Primary logo for light themes — wordmark in `var(--primary)` |
| `logo-dark.svg`  | Primary logo for dark themes — wordmark in white (`var(--ink-0)`) |
| `logo-mark.svg`  | Square mark only (no wordmark) — for compact spaces (sidebar collapsed, push notifications) — viewBox 0 0 64 64 |
| `favicon.svg`    | Browser tab favicon (SVG, modern browsers) — viewBox 0 0 32 32 |
| `favicon-32.png` | Browser tab favicon (PNG fallback for older browsers) — 32×32 |
| `favicon-16.png` | Browser tab favicon (PNG fallback, small) — 16×16 |
| `apple-touch-icon.png` | iOS home-screen icon — 180×180 |
| `og-image.png` | Open Graph card for link previews — 1200×630 |

---

## Sizing requirements

| Asset | viewBox | Display sizes |
|---|---|---|
| Wordmark logos | `0 0 240 64` (3.75:1 aspect ratio) | Render at 120×32 (compact header), 240×64 (login), 480×128 (loading splash) |
| Square mark | `0 0 64 64` (1:1) | Render at 24, 32, 48, 64, 96 |
| Favicon | `0 0 32 32` (1:1) | Browser-driven (16, 32, 48) |

All logos **must** be SVG with text outlined to paths (no system-font fallbacks — RTL renderers misbehave with embedded fonts). The placeholder SVGs in this folder use `<text>` elements for editability, but the final assets should ship as outlined paths.

---

## Color palette references

Use these design tokens (defined in `tss-grc-ui-v2/src/styles/tokens.css`):

| Token | Hex | Used for |
|---|---|---|
| `--primary`      | `#0E5E5E` | Wordmark on light backgrounds |
| `--primary-600`  | `#0B5050` | Hover / pressed states (if logo is a link) |
| `--ink-0`        | `#FFFFFF` | Wordmark on dark backgrounds |
| `--ink-1`        | `#15201D` | Logo background mask (if applied) |
| `--accent`       | `#E8B547` (approx — see tokens.css) | Reserved — for the optional mark glyph stroke |

Final assets **must** use these hex values verbatim (not approximations) so the brand reads consistently against the rest of the UI.

### Dark theme rendering

The platform supports a dark theme (toggled via `data-theme="dark"` on `<html>`). The header logo swaps between `logo-light.svg` and `logo-dark.svg` based on the active theme:

```css
[data-theme="light"] .app-logo { content: url('/brand/logo-light.svg'); }
[data-theme="dark"]  .app-logo { content: url('/brand/logo-dark.svg'); }
```

(The actual swap mechanism today lives in the React shell — see `src/components/layout/Header.tsx`.)

---

## Arabic / bilingual handling

The wordmark text "TSS GRC" is treated as an English-only acronym (it stands for Trusted Security Solutions — Governance, Risk & Compliance) and is **not** transliterated to Arabic. In RTL mode, the logo position flips to the right side of the header but the wordmark itself remains LTR — same approach used by Saudi banking apps like Al Rajhi and STC pay (English acronyms stay LTR even in RTL layouts).

If a future Arabic-first sub-brand is added (e.g. a Saudi-localised marketing variant), the file naming convention extends as `logo-light-ar.svg` etc., with `tashkeel` left out for legibility at small sizes.

---

## License

All final assets are **TSS-internal**: copyright Trusted Security Solutions Co., all rights reserved. Do not redistribute or use in third-party material without written permission. The placeholders in this folder may be freely modified — they exist only to keep the build green until final assets ship.

---

## Wiring (where these files are loaded)

| Reference | Loaded by |
|---|---|
| `<img src="/brand/logo-light.svg">` | `tss-grc-ui-v2/src/components/layout/Header.tsx` |
| `<link rel="icon" href="/brand/favicon.svg">` | `tss-grc-ui-v2/index.html` |
| `<link rel="apple-touch-icon" href="/brand/apple-touch-icon.png">` | `tss-grc-ui-v2/index.html` |
| OG meta tags (`og:image`) | `tss-grc-ui-v2/index.html` |

Until the final assets land, those tags can either point at `*-placeholder.svg` or use the existing favicon shipped via Vite's `public/` default. Either way, **no go-live blocker** — this issue is intentionally LOW priority.

---

## Final-asset checklist (Operations follow-up)

Before swapping placeholders for final assets, confirm:

- [ ] All SVGs have text converted to outlined paths
- [ ] Wordmark renders correctly at 120×32 (compact header) — letters not pixelated at <24px height
- [ ] PNG fallbacks are crushed (`pngquant --quality=80-95` or equivalent) — < 5 KB each
- [ ] Dark-theme variant has sufficient contrast against `--bg-dark` (`#101816`) — minimum WCAG AA ratio 4.5:1 for normal text equivalent
- [ ] Favicon renders correctly in Safari, Chrome, Firefox, Edge — tab title bar legible at the 16×16 pixel density
- [ ] iOS home-screen icon does not have transparent background (iOS will add an off-white plate behind it otherwise)
- [ ] All filenames lowercased, no spaces — `logo-light.svg` not `Logo Light.svg`
