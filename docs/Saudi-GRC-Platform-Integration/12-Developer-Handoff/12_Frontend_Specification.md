# 12 — Frontend Specification

## Stack

| Layer | Choice |
|---|---|
| Language | TypeScript (strict mode) |
| Framework | React 18 |
| Build | Vite |
| Router | React Router v6 |
| State | local component state + React Query (where needed) |
| Styling | utility classes + CSS modules + Bootstrap RTL |
| Charts | Chart.js 4 + lightweight custom 3D bars |
| Icons | lucide-react |
| Date | dayjs + custom Hijri renderer |
| i18n | `useI18n()` hook + `translations/{en,ar}.ts` |
| HTTP | `fetch`-based `api` helper in `lib/api.ts` |
| Tests | `npx tsc --noEmit` must be clean; Vitest for the few unit-tested helpers |

## Folder layout

```
tss-grc-ui-v2/src/
├── App.tsx                     route table
├── main.tsx                    Vite entry
├── components/
│   ├── layout/                 Shell, Sidebar, Topbar, PageHeader, Breadcrumb
│   ├── ui/                     Card, Tabs, Badge, Button, DataTable, Modal, FormModal, …
│   ├── viz/                    BarChart3D, CoverageMatrix, RegulatorPostureCard, …
│   ├── regulatory/             RegulatoryAlignmentTab (F5)
│   ├── workflows/              ActiveWorkflowsPanel
│   ├── comms/                  CommsPanel
│   └── rtl/                    DirectionalArrow
├── pages/                      one folder per route
├── lib/                        i18n, api, toast, dialog, auth, regulatoryAuthority, controlLibrary, …
├── translations/               en.ts, ar.ts
└── styles/                     global CSS, RTL overrides
```

## Page anatomy

```jsx
<Shell title="…" sub="…">
  <PageHeader eyebrow="…" title="…" />
  <Tabs items={tabs} active={tab} onChange={setTab} />
  {tab === 'overview'  && <OverviewTab />}
  {tab === 'register'  && <RegisterTab />}
  {tab === 'alignment' && <RegulatoryAlignmentTab domain="…" families={[…]} />}
  <FormModal open={…} onClose={…} onSubmit={…} fields={FIELDS} />
  <ConfirmDialog open={…} onConfirm={…} />
</Shell>
```

## Routing

`App.tsx` has the full route table. The sidebar (`Sidebar.tsx`)
reads the JWT `enabled_modules` claim (F9) and hides unlicensed
entries. NavLink active state drives the highlight.

## API client

```ts
import { api } from './lib/api';

const docs = await api.get<AuthorityDocument[]>('/api/v1/regulatory/authority-documents');
const c    = await api.post<Control>('/api/v1/controls', body);
```

The helper auto-attaches the Bearer token, refreshes on 401, and
unwraps `ProblemDetail` errors into thrown `ApiError`s.

## i18n

```ts
const { t, lang } = useI18n();
return <span>{lang === 'ar' ? 'مرحباً' : t('hello')}</span>;
```

Every user-visible string lives in `translations/{en,ar}.ts`. New
strings must be added to both files in the same commit; `tsc`
catches missing keys.

## RTL

The root `<html dir>` flips at runtime. Bootstrap RTL stylesheet is
loaded conditionally. Layout flips automatically; direction-
sensitive icons use `DirectionalArrow` to render mirrored variants.

## Forms

`FormModal<T>` (in `components/ui/FormModal.tsx`) takes a `fields`
array describing each input + validation. Built-in field types:
text, textarea, number, select, date, hijri-date, file, multi-select.

## Toast / dialog

```ts
const toast = useToast();
const dialog = useDialog();
toast.success('saved');
toast.danger(error.message);
const ok = await dialog.confirm({ title: '…', body: '…' });
const text = await dialog.prompt({ label: '…', type: 'textarea' });
```

## Empty states

Use `<EmptyState Icon title sub />` for whole-page empty,
`<InlineEmpty>children</InlineEmpty>` for in-card empty.

## Data tables

```tsx
<DataTable<Row>
  columns={[
    { header: 'ID', cell: r => r.id, mono: true, width: '80px' },
    { header: 'Title', cell: r => <strong>{r.title}</strong> },
    { header: 'Status', cell: r => <Badge tone={…}>{r.status}</Badge> },
  ]}
  rows={rows}
  empty={<EmptyState … />} />
```

## Charts

`BarChart3D`, `CoverageMatrix`, plain Chart.js for line/donut. Chart
labels are bilingual.

## Auth flow

1. `LoginPage` → POST `/api/v1/auth/login` → access token (1h) +
   HttpOnly refresh cookie (7d).
2. `api` helper auto-refreshes on 401 via cookie.
3. Logout clears `sessionStorage` + posts to `/auth/logout`.

## Build & deploy

```bash
npm run build        # → dist/
```

Static `dist/` served behind the backend or via a CDN. CSP allows
the backend origin; for dev, `vite.config.ts` proxies `/api/*`.

## Testing

`tsc --noEmit` is mandatory before commit. Run as a pre-commit
hook ideally. Visual regression suites are an open task.

---

**See also:** `12_Backend_Specification.md`, `Sidebar.tsx`, `App.tsx`.
