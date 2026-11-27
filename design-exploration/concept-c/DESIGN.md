# LawDesk — Case Intelligence Design System

## 1. Design philosophy

An operations cockpit built for rapid scanning and comparison across many matters. Density 9/10, asymmetry 3/10, motion 1/10. Deadline and stage visibility outrank decoration. This is a concept for the LawDesk product roadmap; planned modules must be marked as such in review material.

## 2. Semantic colors

- **Slate canvas** `#E9EEF2` — application background.
- **Work surface** `#FFFFFF` — tables and panes.
- **Steel header** `#24394D` — persistent chrome.
- **Primary ink** `#1D2C39` — body text.
- **Muted ink** `#536677` — metadata.
- **Steel accent** `#376B88` — single accent for selection and action.
- **Border** `#B9C7D2` — grid and separators.
- **Critical** `#A22D35` — overdue and risk.
- **Success** `#24624F` — completed.

Use status colors only for meaning and pair every status with a text label. One decorative accent; functional danger and success are exceptions. Avoid pure black.

## 3. Typography hierarchy and 4. Language fonts

English: IBM Plex Sans for labels and body; JetBrains Mono for all numerical values, references, timestamps and counts. Arabic: IBM Plex Sans Arabic with Noto Sans Arabic fallback. Title 24/31; section 17/24; table body 14/22; metadata 12/19; Arabic rows 15/25. Use 65ch maximum for paragraphs, tabular numerals where relevant, and enough Arabic line height for dots and diacritics.

## 5. Navigation principles

A narrow 210px labelled left sidebar plus global command/search bar. Primary domains: Queue, Cases, Judicial, People, Operations, Reports. Case list keeps a persistent filter column; details open in a 65:35 split pane. Permission gating hides unavailable actions. A screen title, breadcrumb and visible current state must answer where the user is.

## 6. Spacing and layout

Desktop 1600px target with 210px navigation, 228px filter pane, flexible table, 360px inspector. 4px spacing scale, 16px content padding, 2–4px corners. Density remains readable through alignment and strong headers. Dashboard uses one wide priority queue plus compact deadline and stage rails. No content overlap.

## 7. Buttons, forms, tables and cards

Flat steel primary button and outlined compact secondary. Inputs 40px with labels and visible focus. Data table 40px rows with sticky headers, sortable columns, row selection and text badges; minimum 44px click targets despite row density. No card wallpaper. Disable actions the viewer lacks permission for and explain why when appropriate. Validation appears beside the relevant field without echoing sensitive values.

## 8. Search and filtering

Pinned global search and column filters. Filters can combine type, stage, status, court and date; show query count and removable active filters. Inspector retains selected row context and keyboard escape route. Preserve query and filters when returning from a case. Distinguish no results from loading and network errors.

## 9. Accessibility requirements

Target WCAG 2.2 AA: text contrast ≥4.5:1, non-text contrast ≥3:1, visible 2px focus, semantic headings and table headers, keyboard navigation, predictable tab order, labelled icons, error summary plus inline errors, and 44×44px touch targets. Color is never the only signal. Do not animate essential information.

## 10. RTL/LTR behavior

Mirror panes and column order intentionally; do not reverse chronology or numbers. Align Arabic prose to inline start and isolate reference/date strings. Arabic table rows get increased line height and expandable title width. Use logical alignment and directional isolation, not string reversal. Localize stage, case type, party roles and court terminology.

## 11. Responsive behavior

Under 1024px inspector becomes full-height drawer. Under 768px filters become a full-screen sheet and rows become compact labelled cards; priority and deadline still appear before secondary metadata. No horizontal page overflow. At 200% zoom, core actions stay usable and text does not clip.

## 12. Loading, error and empty states

Skeletons match the table/record shape. An error names the affected operation and offers retry without exposing private data. Empty state distinguishes a new workspace from filtered no-results and gives a relevant action. Permission denial uses a dedicated message; planned features are labelled Planned rather than pretending to work.

## 13. Motion guidelines

90–140ms opacity/transform only for inspector and filter drawer; no animated dashboard counters. Respect reduced motion. State changes must also be announced in text or accessible status, as needed.

## 14. Prohibited anti-patterns

No charts without labels, color-only status, tiny touch controls, truncated court names, stacked KPI cards, neon, emojis, heavy shadows, or ambiguous icon-only controls. Never use real client names, documents, source code, secrets, fabricated performance figures, or unsupported product claims in concept screens.
