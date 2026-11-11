# GridReserve — Design System

> Version 0.2 (draft, 2026-11-11). Changes from v0.1 (M1.13, D-45): design direction **A** chosen by the owner and built on every screen (controls with 10 px corners, cards 14 px, semibold buttons, 44 px inputs; dark selected day in the date strip; taken slots dashed and struck through instead of hatched; phones with a bottom tab bar and page headers with a back button); every dropdown is a searchable `Combobox` (the Radix `Select` was removed); axe checks approved (D-UI-4).
> Version 0.1 (draft, 2026-11-05). Owner decisions: **calm, precise SaaS** style; components built with **shadcn/ui on Radix primitives**, styled with Tailwind; **light and dark** themes.
> Applies to everything in `frontend/`. Requirement IDs (`FR-UI-03`, …) refer to REQUIREMENTS.md.
> No UI code exists yet. Tokens below are the source of truth; the implementation must use them, never raw colors.

## 1. Principles

1. **State is the product.** A booking UI is mostly about state: free, taken, held by me, confirmed, expired. Every state has one unmistakable look, and color is never the only signal (text, icons or patterns too).
2. **One accent, everything else neutral.** Neutral greys carry structure; the indigo accent marks *the* action or *my* selection. If everything is colored, nothing stands out.
3. **Calm density.** Admin tables and slot grids are dense; whitespace, alignment and a strict type scale keep them readable.
4. **Fast feedback, no drama.** Loading, success and conflict feedback appear within the interaction (skeletons, inline messages, toasts). Motion is short and functional.
5. **Accessible by default.** WCAG 2.2 AA: contrast-checked tokens, visible focus, full keyboard use, screen-reader announcements for holds and conflicts.
6. **Build once, reuse everywhere.** Screens are assembled from components in `components/ui` and `components/patterns`; no one-off styling in feature code.

## 2. References and what we take from them

Inspiration, not imitation: GridReserve has its own palette and components.

| Reference | What we take |
|---|---|
| Linear | Near-neutral surfaces with a single indigo-violet accent; themes derived from a few base values (base, accent, contrast) instead of dozens of hand-picked colors; crisp Inter typography. |
| Stripe (dashboard and docs) | Form clarity, precise alignment, helpful inline validation, calm data tables. |
| Cal.com / Calendly | The booking flow itself: date strip → time slots → confirm, with the selected slot clearly highlighted and a persistent summary. |
| Vercel (Geist) | Monochrome restraint, tight spacing scale, clean empty and error states. |
| shadcn/ui | Component anatomy and theming via CSS variables in light and dark. |
| Awwwards-featured product sites | Restraint and polish rather than spectacle: one strong hero idea on the landing/login page, subtle motion; no heavy animation inside working screens. |

## 3. Design tokens

Tokens are CSS variables defined once (`src/styles/globals.css`) for `:root` (light) and `.dark`, then mapped into Tailwind (Tailwind v4 `@theme inline`, verify the exact setup at scaffold). Names follow shadcn/ui conventions plus GridReserve-specific semantic tokens. Hex values are given for review; the implementation may express them in OKLCH (shadcn's default) with the same appearance.

### 3.1 Color — core

| Token | Light | Dark | Use |
|---|---|---|---|
| `--background` | `#FFFFFF` | `#0B0B0E` | Page background |
| `--canvas` | `#F7F7F8` | `#0B0B0E` | App background behind cards, admin areas |
| `--card` / `--popover` | `#FFFFFF` | `#141418` | Cards, menus, dialogs |
| `--foreground` | `#18181B` | `#F4F4F5` | Primary text |
| `--muted-foreground` | `#52525B` | `#A1A1AA` | Secondary text, labels |
| `--subtle-foreground` | `#71717A` | `#8B8B94` | Hints, timestamps |
| `--border` | `#E4E4E7` | `#27272A` | Dividers, card borders (decorative) |
| `--input` | `#8A8A93` | `#6B6B75` | Form control borders (meets 3:1) |
| `--ring` | `#4F46E5` | `#8B8CF8` | Focus ring |
| `--primary` | `#4F46E5` | `#8B8CF8` | Main action, selected slot, links |
| `--primary-hover` | `#4338CA` | `#A5A6FA` | Hover/pressed |
| `--primary-foreground` | `#FFFFFF` | `#14123A` | Text on primary |
| `--primary-soft` | `#EEF0FF` | `#1E1D3F` | Selected rows, active nav, highlights |
| `--primary-soft-foreground` | `#3730A3` | `#C7C8FD` | Text on primary-soft |

Implementation aliases (no new colors, so no new contrast pairs): shadcn's `--muted`, `--secondary` reuse `--slot-taken`'s neutral (`#F4F4F5` / `#1C1C21`), and `--accent` / `--accent-foreground` reuse `--primary-soft` / `--primary-soft-foreground`.

### 3.2 Color — status

| Token | Light | Dark | Use |
|---|---|---|---|
| `--success` | `#15803D` | `#4ADE80` | Icons/borders for free / confirmed |
| `--success-soft` / `-foreground` | `#ECFDF3` / `#166534` | `#0E2A1A` / `#86EFAC` | Confirmed badge, success alerts |
| `--warning` | `#B45309` | `#FBBF24` | Held / expiring |
| `--warning-soft` / `-foreground` | `#FFF7E6` / `#92400E` | `#2E2108` / `#FCD34D` | Hold banner, warning alerts |
| `--destructive` | `#DC2626` | `#F87171` | Destructive actions, errors |
| `--destructive-hover` | `#B91C1C` | `#FCA5A5` | Hover |
| `--destructive-foreground` | `#FFFFFF` | `#2A0A0A` | Text on destructive |
| `--destructive-soft` / `-foreground` | `#FEF2F2` / `#991B1B` | `#2E1010` / `#FCA5A5` | Error alerts, conflict notice |

### 3.3 Color — slot states (semantic, used only by `SlotGrid`)

| State | Background | Text | Border | Extra cue |
|---|---|---|---|---|
| `FREE` | `--card` | `--foreground` | `--border` | Hover: `--primary-soft` |
| `SELECTED` (my pick) | `--primary` | `--primary-foreground` | `--primary` | Check icon; `aria-pressed="true"` |
| `HELD_BY_ME` | `--warning-soft` | `--warning-soft-foreground` | `--warning` | Timer icon + countdown |
| `TAKEN` | `--slot-taken` (`#F4F4F5` / `#1C1C21`) | `--slot-taken-foreground` (`#626269` / `#8B8B94`) | none | Dashed border and struck-through time (design A, v0.2; was a diagonal hatch), "taken" in the accessible name, focusable but not selectable |
| `PAST` | `--card` at 50 % opacity | `--subtle-foreground` | none | Not interactive |

### 3.4 Contrast check

All text/background pairs above were checked with the WCAG 2.x formula (2026-11-05): every text pair ≥ 4.5:1; `--input`, `--ring`, `--success`, `--warning` against cards ≥ 3:1 (non-text). Results, light / dark:

| Pair | Light | Dark |
|---|---|---|
| foreground on background | 17.72 | 17.88 |
| muted-foreground on card | 7.73 | 7.17 |
| subtle-foreground on card | 4.83 | 5.44 |
| primary-foreground on primary | 6.29 | 6.12 |
| primary-soft-foreground on primary-soft | 8.77 | 10.06 |
| success-soft-foreground on success-soft | 6.76 | 10.96 |
| warning-soft-foreground on warning-soft | 6.65 | 10.89 |
| destructive-foreground on destructive | 4.83 | 6.62 |
| slot-taken-foreground on slot-taken | 5.51 | 5.03 |
| input border on card | 3.42 | 3.49 |

Any new color token must be added to this table with its measured ratio.

### 3.5 Typography

- **Font:** Inter Variable for all UI text (self-hosted, D-UI-3); system monospace stack for IDs and codes.
- **Numbers:** `font-variant-numeric: tabular-nums` for times, prices, counts and table numbers, so columns and countdowns don't jitter.

| Token | Size / line height | Weight | Use |
|---|---|---|---|
| `text-xs` | 12 / 16 | 500 | Badges, captions |
| `text-sm` | 14 / 20 | 400–500 | Default UI text, table cells, form labels |
| `text-base` | 16 / 24 | 400 | Body copy, inputs on mobile |
| `text-lg` | 18 / 28 | 600 | Card titles |
| `text-xl` | 20 / 28 | 600 | Section headings |
| `text-2xl` | 24 / 32 | 600 | Page titles |
| `text-3xl` | 30 / 36 | 650 | Login/landing headline |

Letter spacing `-0.01em` for 20 px and above. Never more than two weights on one screen besides headings.

### 3.6 Spacing, radius, elevation, motion

- **Spacing:** 4 px base (Tailwind default scale). Common: 8 between related items, 16 inside cards, 24 between sections, 32–48 page padding on desktop, 16 on mobile.
- **Radius:** `--radius-sm` 6 px (inputs, buttons, slots), `--radius-md` 10 px (cards), `--radius-lg` 14 px (dialogs, sheets), full for badges and avatars.
- **Elevation:** borders first. Two shadows only: `--shadow-sm` for cards on `--canvas`, `--shadow-lg` for popovers, dialogs and toasts. In dark mode, elevation comes from lighter surfaces, not shadows.
- **Motion:** 120 ms (hover, press), 180 ms (popovers, toasts), 240 ms (dialogs, sheets), `ease-out` for entering, `ease-in` for leaving. Respect `prefers-reduced-motion` (no movement, only fades). No motion on the slot grid beyond hover and selection.
- **Breakpoints:** Tailwind defaults (`sm` 640, `md` 768, `lg` 1024, `xl` 1280). Content max width 1200 px; forms max 560 px.
- **Touch targets:** at least 40 × 40 px for slots and icon buttons on touch layouts (WCAG 2.2 minimum is 24 px).

## 4. Components

### 4.1 Base components (`src/components/ui`, generated from shadcn/ui and owned by us)

| Component | Variants / sizes | States that must exist | Notes |
|---|---|---|---|
| `Button` | `primary`, `secondary`, `outline`, `ghost`, `destructive`, `link`; `sm`, `md`, `lg`, `icon` | default, hover, focus-visible, active, disabled, **loading** (spinner, keeps width, `aria-busy`) | One `primary` per view region |
| `Input`, `Textarea` | `md`, `sm` | default, focus, invalid (`aria-invalid` + message), disabled | Always paired with `Label` |
| `Select` | — | open, keyboard navigation, disabled | Radix Select |
| `Combobox` | — | searching, empty result, invalid, disabled | Every dropdown (v0.2): search box, words in any order, accents ignored, prefix matches first |
| `DatePicker` / `Calendar` | — | today, selected, disabled (beyond horizon) | Uses the venue time zone for "today" |
| `Dialog`, `AlertDialog`, `Sheet` | `sm`, `md`, `lg` | open/close, focus trap, Esc | AlertDialog for cancel/deactivate confirmations |
| `DropdownMenu`, `Popover`, `Tooltip` | — | — | Tooltips never hold essential info |
| `Tabs`, `Badge`, `Card`, `Separator`, `Skeleton`, `Alert`, `Toast` (sonner), `Pagination`, `Table` | — | — | `Badge` variants map to statuses (§4.3) |

### 4.2 Pattern components (`src/components/patterns`, GridReserve-specific)

| Component | Purpose | Key behavior |
|---|---|---|
| `AppShell` | Top bar (logo, category quick switch, theme toggle, user menu) + content; admin adds a left sidebar | Responsive: sidebar collapses to a sheet on mobile |
| `PageHeader` | Title, description, primary action | One per page |
| `CategorySelect` | Category dropdown (FR-UI-02) | Shows category icon + name |
| `VenueCard` | Venue name, address, local time, number of bookables | Whole card is one link |
| `BookableList` | Bookables grouped by type label ("Rooms", "Seats") | Label from bookable type; inactive hidden |
| `DateStrip` | Next 7 days (the booking horizon) as compact buttons | Closed weekdays shown as closed; days beyond the horizon not shown |
| `SlotGrid` / `SlotCell` | Slots for a day in venue time, states in §3.3 | Roving tabindex, arrow keys move, Enter/Space selects; multi-slot range selection for types with max > slot; shows zone name |
| `BookingSummary` | Sticky panel: bookable, date, time range, rules (cutoff), primary "Hold" button | Becomes `HoldPanel` after hold |
| `HoldPanel` / `HoldTimer` | Countdown from server `expiresAt`, Confirm and Release buttons | Turns warning color under 60 s; announces at 1 min and on expiry (polite live region) |
| `BookingCard` | One booking in "My bookings" | Status badge, local times, cancel button disabled after cutoff with reason |
| `StatusBadge` | Maps booking status → badge | §4.3 |
| `ConflictNotice` | Inline message for `409 SLOT_TAKEN` / `MEMBER_OVERLAP` | Explains and refreshes availability (FR-UI-05) |
| `EmptyState`, `ErrorState`, `LoadingState` | Standard states (FR-UI-03) | Error state shows API message + retry; 503/504 variant "temporarily unavailable" (FR-UI-10) |
| `DegradedBanner` | Admin list without member details (RES-06) | Neutral info style, not an error |
| `CountryZonePicker` | Country combobox → zone select (FR-UI-09) | Auto-fills single-zone countries |
| `DataTable` | Admin tables: sorting, filters, pagination, row actions | Sticky header; numbers right-aligned, tabular |
| `AuditTimeline` | Booking history in a sheet | Actor, from → to, time in venue zone |
| `ThemeToggle` | System / Light / Dark | Preference stored per browser |

### 4.3 Status mapping

| Booking status | Badge style | Icon |
|---|---|---|
| `HELD` | warning-soft | timer |
| `CONFIRMED` | success-soft | check |
| `CANCELLED` | neutral (outline) | x |
| `EXPIRED` | neutral (outline, muted text) | clock-off |
| `NO_SHOW` (P7) | destructive-soft | alert |

## 5. Screens and layout

| Screen | Layout |
|---|---|
| Login / Register | Two columns on desktop: left a calm product panel (one headline, a small slot-grid illustration), right the form card; single column on mobile. |
| Browse | `PageHeader` + `CategorySelect`; venue cards in a responsive grid (1 / 2 / 3 columns). |
| Venue | Venue header (name, local time, zone); `BookableList` grouped by type. |
| Book (bookable) | Left: `DateStrip` + `SlotGrid`. Right (sticky on desktop, bottom sheet on mobile): `BookingSummary` → `HoldPanel`. |
| My bookings | Tabs: Upcoming / Past / Cancelled & expired; `BookingCard` list; empty state with "Find a slot". |
| Admin | `AppShell` with sidebar (Venues, Bookables, Bookings); `DataTable`s; create/edit forms in `Sheet`s; confirmations in `AlertDialog`. |

**Time display:** `Tue 4 Nov · 18:00–19:00 (Asia/Kolkata)`; always the venue's zone, with the zone shown once per screen.

## 6. Content and voice

- Short, plain, specific. Say what happened and what to do next: "This slot was just taken. Pick another time." not "Error 409".
- Every API error code (REQUIREMENTS.md §15.3) has one user message in `src/api/errors.ts`.
- Buttons are verbs: "Hold slot", "Confirm booking", "Cancel booking", "Close bookable" (admin deactivate).
- No exclamation marks, no blame ("You entered…" → "Choose a date within the next 7 days").
- Never use the word "resource"; use the bookable type label.

## 7. Accessibility checklist

- Contrast per §3.4; focus ring 2 px `--ring` with 2 px offset on every interactive element.
- Full keyboard path for the main journey, including the slot grid (arrow keys) and dialogs (focus trap, Esc).
- Live regions: hold countdown (polite, at 1 minute and expiry), conflict notices (assertive), toasts.
- State never by color alone (labels, icons, dashed borders and strike-through).
- Forms: visible labels, error text linked with `aria-describedby`, errors summarized on submit.
- Respects `prefers-reduced-motion` and `prefers-color-scheme`.
- Tested with keyboard only and a screen reader before each phase closes; automated checks in tests (§9).

## 8. Implementation rules

- **Tokens only:** no hex/rgb values or arbitrary Tailwind colors (`bg-[#…]`) in components or features. Need a new color → add a token, check contrast, update §3.
- **Variants, not overrides:** new looks are new variants (class-variance-authority) on the component, not `className` patches in feature code.
- **Layering:** `features/*` use `components/patterns` and `components/ui`; `components/ui` never imports from `features`.
- **shadcn code is ours:** generated components live in the repo, are reviewed and may be changed; re-generating must not silently overwrite our edits.
- **Theme:** `.dark` class on `<html>`, set from the stored preference or `prefers-color-scheme`; the preference (not any token or auth data) is the only thing stored in `localStorage`.
- **Component gallery:** a dev-only route `/dev/components` renders every component in every state, in light and dark, so changes are visible in one place (no Storybook unless approved).

## 9. Testing the UI

- Vitest + RTL: each pattern component's states (e.g. `SlotCell` per state, `HoldTimer` warning and expiry, `StatusBadge` mapping, `ConflictNotice` refetch).
- Playwright: main journey, keyboard-only journey, light/dark screenshots of key screens with Playwright's built-in screenshot comparison (no extra library).
- Automated accessibility checks with axe in Playwright (D-UI-4, approved 2026-11-11): every screen at 360 and 1440 px, light and dark, WCAG 2.2 AA tags.

## 10. Decisions

| ID | Decision | Status |
|---|---|---|
| D-UI-1 | Calm, precise SaaS style; neutral + single indigo accent | Decided 2026-11-05 |
| D-UI-2 | shadcn/ui + Radix, Tailwind, light + dark tokens | Decided 2026-11-05 |
| D-UI-3 | Supporting UI packages: shadcn's standard helpers (`class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react` icons, `tw-animate-css`), `radix-ui` (the primitives), `sonner` (toasts), `@fontsource-variable/inter` (self-hosted font) | **Decided 2026-11-07** (M1.6, owner approved the exact list). `react-day-picker` and `@tanstack/react-table` **not approved** at M1.13 (native date inputs and a plain `Table` were enough) |
| D-UI-4 | `@axe-core/playwright` for automated accessibility checks | **Decided 2026-11-11** (M1.13, owner approved; 4.14.0 with `@playwright/test` 1.64.1) |
| D-UI-5 | Storybook vs dev-only component gallery route | Gallery route (no new dependency) unless Storybook is approved |
