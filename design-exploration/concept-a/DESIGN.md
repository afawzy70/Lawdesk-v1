# LawDesk — Legal Executive Design System

## 1. Design philosophy

An authoritative partner view: calm, formal, and selective. Density 6/10, asymmetry 5/10, motion 2/10. The first glance answers exposure, upcoming proceedings, and who owns the work. This is a concept for the LawDesk product roadmap; planned modules must be marked as such in review material.

## 2. Semantic colors

- **Canvas navy** `#101B2B` — application shell.
- **Panel navy** `#18283B` — navigation and emphasis.
- **Paper** `#F6F4EF` — working surface.
- **Raised paper** `#FFFFFF` — dialogs and focused detail.
- **Primary ink** `#172337` — body text.
- **Muted ink** `#526173` — metadata.
- **Brass accent** `#8A6A2F` — single brand accent and selected state.
- **Border** `#CBD1D4` — structure.
- **Danger** `#9A3535` — urgent status.
- **Success** `#286552` — completed status.

Use status colors only for meaning and pair every status with a text label. One decorative accent; functional danger and success are exceptions. Avoid pure black.

## 3. Typography hierarchy and 4. Language fonts

English: Geist for headings and body; Geist Mono for case references and dates. Arabic: IBM Plex Sans Arabic, with Noto Sans Arabic fallback. Page title 28/36 semibold; section title 19/28; body 15/24; label 13/20; Arabic body 16/28. Never serif in the software UI. Use 65ch maximum for paragraphs, tabular numerals where relevant, and enough Arabic line height for dots and diacritics.

## 5. Navigation principles

A 248px deep navy, full-height formal sidebar with named groups: Overview, Matters, Judicial, Operations, Administration. Current item uses a brass rail and text; permissions remove inaccessible destinations; planned destinations carry an explicit Planned tag. A screen title, breadcrumb and visible current state must answer where the user is.

## 6. Spacing and layout

Desktop 1440px: 248px sidebar, 48px outer margins, 12-column content grid. Dashboard 7:5 split between portfolio and agenda. Case details: fixed summary masthead above a 7:5 file and proceedings split. 8px rhythm, 24px panel padding, 1px rules; square to 6px corners. No content overlap.

## 7. Buttons, forms, tables and cards

Primary button navy with paper text; brass reserved for selected states and one featured decision. Secondary outline. Inputs 44px, label above, error below. Tables 48px rows with right-aligned numbers, sticky header, quiet striped hover. Cards only for portfolio summary; dense records use rules. Disable actions the viewer lacks permission for and explain why when appropriate. Validation appears beside the relevant field without echoing sensitive values.

## 8. Search and filtering

One global case search plus local filters for case type, status and date. Search results show reference, title, status; applied filters remain visible as removable chips. No hidden filter drawer on desktop. Preserve query and filters when returning from a case. Distinguish no results from loading and network errors.

## 9. Accessibility requirements

Target WCAG 2.2 AA: text contrast ≥4.5:1, non-text contrast ≥3:1, visible 2px focus, semantic headings and table headers, keyboard navigation, predictable tab order, labelled icons, error summary plus inline errors, and 44×44px touch targets. Color is never the only signal. Do not animate essential information.

## 10. RTL/LTR behavior

Arabic flips sidebar, breadcrumb, row actions and split panels to logical inline start/end. Keep case reference, ISO date, court number and year in isolated LTR spans; Arabic court names remain natural RTL. Label lengths may wrap, never truncate legal terms. Use logical alignment and directional isolation, not string reversal. Localize stage, case type, party roles and court terminology.

## 11. Responsive behavior

At 768px sidebar becomes a labelled drawer; dashboard stacks agenda after exposure. Mobile bottom navigation has Dashboard, Cases, Agenda, More. Case details uses summary then tabbed Proceedings, Parties, Activity; no horizontal page scroll. At 200% zoom, core actions stay usable and text does not clip.

## 12. Loading, error and empty states

Skeletons match the table/record shape. An error names the affected operation and offers retry without exposing private data. Empty state distinguishes a new workspace from filtered no-results and gives a relevant action. Permission denial uses a dedicated message; planned features are labelled Planned rather than pretending to work.

## 13. Motion guidelines

120–180ms opacity and transform for hover, drawer and tab changes. Respect reduced motion. No perpetual animation on legal records. State changes must also be announced in text or accessible status, as needed.

## 14. Prohibited anti-patterns

No decorative scales of justice, faux leather texture, serif dashboard typography, gold gradients, neon, emoji, three equal KPI cards, or invented data presented as live. Never use real client names, documents, source code, secrets, fabricated performance figures, or unsupported product claims in concept screens.
