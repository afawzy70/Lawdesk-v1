# Phase 7 — Modern Chambers design system

## Approved source

Owner selected **Design D — Modern Chambers**. Original private Stitch project: [LawDesk — D — Modern Chambers](https://stitch.withgoogle.com/projects/2890116183553293246), project resource `projects/2890116183553293246`, design-system resource `assets/5663185915974364653`. The project metadata and dashboard screen `51490074816b4a16a0ebfbc19f35a506` were retrieved again through Stitch MCP on 2026-10-25. The prior design exploration at commit `c8dc460c1db709cd7d659117770aa24cd32476d1` contains verified screen screenshots and the full [concept specification](https://github.com/afawzy70/Lawdesk-v1/blob/design/stitch-lawdesk-exploration/design-exploration/concept-d/DESIGN.md). The Stitch screens are visual references only: their sample matters, metrics, retainer and billing details, and court integrations are fictional or unsupported in LawDesk. Runtime copy and figures always come from the implemented application and API.

## Atmosphere and tokens

A restrained, premium legal workspace: density 6/10, asymmetry 5/10, motion 1/10. Paper-like hierarchy is achieved by surfaces and rules, never textures, gradients or decorative motion. Tokens live in `frontend/src/modern-chambers.css` and override only presentation rules.

| Role | Token | Value |
| --- | --- | --- |
| Canvas | `--mc-canvas` | `#F3EFE6` |
| Reading surface | `--mc-paper` | `#FFFCF6` |
| Navigation | `--mc-charcoal` | `#272A29` |
| Primary ink | `--ld-fg` | `#252926` |
| Secondary ink | `--ld-muted` | `#625F57` |
| Single brand accent | `--mc-brass` | `#876B3C` |
| Primary control | `--ld-accent` | `#6C5326` |
| Structural border | `--ld-border` | `#D1C9BA` |
| Success | `--ld-ok` | `#326451` |
| Error / urgent | `--ld-bad` | `#963C3B` |
| Focus | `--ld-focus` | `#876B3C` |

Spacing uses 4, 8, 12, 16, 24, 32 and 48 px tokens. Radius is 4 px. Shadows are limited to the sign-in surface; the working UI uses rules and surface contrast.

## Typography and direction

English stack: DM Sans, Noto Sans, Segoe UI, sans-serif. Arabic stack: IBM Plex Sans Arabic, Cairo, Noto Sans Arabic, Tahoma, sans-serif. The named fonts are self-hosted from Google Fonts WOFF2 files with their SIL Open Font License texts in `frontend/src/assets/fonts/`; offline browsers retain installed fallbacks. Headings use 26–38 px via `clamp`, section headings 18–22 px, body 16 px, table data 14–15 px, metadata 12–13 px. Arabic gets more line-height and no tight tracking. Dates, file names and references retain their own direction with existing `<bdi>` or `[dir=ltr]`; logical CSS properties mirror the layout. Never reverse strings or Latin case numbers.

## Navigation and information architecture

Desktop has a persistent charcoal rail for permission-filtered top-level destinations. The application toolbar holds session state, language, profile and sign-out. On phones the existing keyboard accessible menu toggle reveals the same links. Escape closes it and returns focus. Report and administration subnavigation remain labelled tab strips. The case dossier groups identity, key facts, edit action, parties, judicial stages, operations and timeline in legal reading order. No unsupported Stitch navigation items are added.

## Components

- **Buttons:** one brass primary action per local form; paper secondary controls; 44 px minimum height; clear hover, disabled, active and focus states. Destructive confirmation retains its actual wording and action.
- **Forms:** persistent labels above fields, ivory inputs, visible validation and field-level errors, date controls in the browser's semantic type. Case, court, judicial, operations and admin forms share the same border, spacing and controls.
- **Tables:** paper registers with muted header band, thin horizontal rules, start-aligned text, tabular numbers and meaningful link styling. Wide data tables scroll in a labelled region on phones; the page itself must stay within the viewport.
- **Filters:** grouped muted-paper bar; apply/reset actions preserved; URL-driven case/report filtering remains intact. Search remains scoped to its current module.
- **Metrics and reports:** only backend values; typography and rules establish rank. No decorative chart or fabricated ratio. CSV, Excel and PDF links remain permission gated and include current report filter and locale.
- **Status and messages:** textual status accompanies color. Loading, empty, error, forbidden and expired-session copy stays in the DOM, with bordered paper treatment and unchanged retry or sign-in actions.
- **Documents:** file names and references isolate direction; download/upload actions, size and validation are untouched. No fake preview is introduced where the backend has no preview endpoint.

## Accessibility and responsive rules

Aim for WCAG 2.2 AA with 4.5:1 text contrast, 3:1 non-text contrast, semantic heading/table structure, keyboard focus, 44 px touch controls and reduced-motion support. Use the existing `role=alert`, `role=status`, form labels, table captions and keyboardable scroll regions. At 1120 px the rail narrows and dashboard grid simplifies; below 768 px the rail becomes a toggled menu and multi-column layouts collapse; below 410 px metrics stack. Validation is required at 320, 375, 768 and 1366 px in English and Arabic. No perpetual motion: the owner's instruction and legal workflow take precedence over the Stitch taste skill's generic motion suggestion.

## Banned patterns

No blue/purple SaaS gradients, marble or parchment textures, decorative charts, fake court-sync claims, invented metrics, generic hero artwork, unlabelled icons, dense text over low-contrast gold, clipped Arabic, page-level horizontal overflow or static replacement of live workflows. Do not imply that a Stitch mock action or piece of sample data is implemented.
