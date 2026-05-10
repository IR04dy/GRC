# TSS-Theme — Design Language Reference

**Frozen snapshot of the TSS GRC Platform v2 design language**, taken at the point the pilot was signed off (Risk Register module). Use this folder as the source of truth for every visual decision in `tss-grc-ui-v2` going forward — palette, typography, spacing, components, RTL behavior, theme customization model.

This is **not** a live project. Do not build new modules here. Build them in `tss-grc-ui-v2/` and refer back to this folder when in doubt.

## When to consult this folder

- A new module's visual treatment doesn't feel right — diff it against the Risk module here.
- The dev team wants to change a token (palette, radius, spacing). Discuss the change against the snapshot first; if it overrides the design system, it must be approved before propagating.
- A future redesign needs a starting point — fork from this folder, not from `tss-grc-ui-v2/` after it has accumulated module work.

## What's signed off

| Layer | Reference file |
|---|---|
| Design tokens (palette, type, spacing, radius, shadow, motion) | [src/styles/tokens.css](src/styles/tokens.css) — copied verbatim from the design system's `colors_and_type.css` |
| Shell layout (sidebar + topbar + page chrome) | [src/styles/shell.css](src/styles/shell.css) |
| Component styles (buttons, badges, cards, KPIs, tables, heatmap, 3D bars, modal, toast, login) | [src/styles/components.css](src/styles/components.css) |
| Type stack (Plus Jakarta Sans · IBM Plex Sans Arabic · JetBrains Mono) | [src/styles/tokens.css](src/styles/tokens.css) — Google Fonts import |
| Bilingual + RTL flip logic | [src/lib/i18n.tsx](src/lib/i18n.tsx) + grid template in `shell.css` |
| Client-side theming (custom tokens, presets, persistence) | [src/lib/theme.tsx](src/lib/theme.tsx) + [src/pages/SettingsPage.tsx](src/pages/SettingsPage.tsx) |
| Pilot module pattern (KPIs · tabs · table · heatmap · modal) | [src/pages/risk/RiskRegisterPage.tsx](src/pages/risk/RiskRegisterPage.tsx) |
| Login chrome (split-hero + framework strip) | [src/pages/LoginPage.tsx](src/pages/LoginPage.tsx) |

## Palette at a glance

| Role | Token | Value |
|---|---|---|
| Canvas | `--bg` | `#F2EEE6` (warm off-white) |
| Cards | `--surface` | `#FAF8F4` |
| Primary | `--primary` | `#0E5E5E` (deep teal-petrol) |
| Accent | `--accent` | `#C8932A` (warm sand-amber) |
| Sidebar bg | `--sb-bg` | `#0B1F22` |
| Sidebar highlight | `--sb-accent` | `#E5B547` |
| Ink (text) | `--ink-1` | `#15201D` (warm near-black) |

Risk severity, semantic, and 8-step categorical scales are all in [src/styles/tokens.css](src/styles/tokens.css). Three shadow steps; warm-petrol hero gradient; aurora wash for hero/login.

## How the pilot was reviewed

1. **Login page** at `/login` — split hero with framework pill strip + form panel.
2. **Risk Register** at `/risk` — KPI strip · Register tab (table with score-coloured pills + status badges) · Heat Map tab (5×5 likelihood × impact) · KRI / Appetite placeholders · New Risk modal.
3. **Settings** at `/settings` — 5 theme presets (Default, Sand, Petrol, Mono, Rose) + per-token color pickers + sidebar/header/radius sliders. All overrides persist to `localStorage` under `grc.theme`.
4. **Bilingual toggle** — top-right `العربية / EN` button. RTL flips: sidebar to the right, toast stack to the bottom-left, score-pill heatmap axis labels, sidebar active-item indicator border to the right edge.

## To run this reference (read-only review)

```bash
cd TSS-Theme
npm install
npm run dev
# UI: http://localhost:5173
```

The Vite dev server proxies `/api/*` → `http://localhost:8084` so it can talk to the v1 Spring Boot backend if running. Without the backend, click *Continue without backend (demo mode)* on the login page.

## Things the theme deliberately does **not** include

- A dark mode. The eye-comfort palette is intentionally a warm, low-contrast light theme. Dark mode would require a parallel token set and is a separate design exercise.
- Auto-tint regeneration: when a user picks a custom `--primary`, the `--primary-50…900` ramp does not auto-recompute. Five named presets cover that case. If full ramp regeneration becomes a hard requirement, that's a follow-up PR.
- Animation libraries (framer-motion, react-spring). The eight CSS keyframes in `components.css` cover the entrance/hover language. Stay there until a real performance need pushes us off it.

## Frozen as of

`2026-04-30` — sign-off on the Risk Register pilot before extending the design to other modules.
