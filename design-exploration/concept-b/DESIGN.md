# LawDesk — Modern Workspace Design System

## 1. Design philosophy

A productive daily workspace with gentle wayfinding, readable forms, and minimal cognitive load. Density 5/10, asymmetry 4/10, motion 3/10. Tasks and next actions take precedence over executive totals. This is a concept for the LawDesk product roadmap; planned modules must be marked as such in review material.

## 2. Semantic colors

- **Off-white canvas** `#F7F8F4` — application background.
- **White surface** `#FFFFFF` — work panels.
- **Soft green surface** `#EEF4EE` — selected and contextual zones.
- **Primary ink** `#1E2B27` — body text.
- **Secondary ink** `#56645D` — metadata.
- **Forest accent** `#246B50` — single accent for actions and focus.
- **Border** `#D9E2DA` — structure.
- **Danger** `#A13238` — urgent status.
- **Success** `#236449` — completed status.

Use status colors only for meaning and pair every status with a text label. One decorative accent; functional danger and success are exceptions. Avoid pure black.

## 3. Typography hierarchy and 4. Language fonts

English: Sora for headings and body, JetBrains Mono for references. Arabic: Cairo for headings, Noto Sans Arabic for body. Page title 27/35 medium; section 20/28; body 15/24; label 13/20. Arabic body 16/28. Use 65ch maximum for paragraphs, tabular numerals where relevant, and enough Arabic line height for dots and diacritics.

## 5. Navigation principles

A compact 72px icon rail plus contextual page menu on desktop; icons always have text in the secondary menu and accessible names. Daily workspace tabs: Today, Cases, Clients, Calendar, Documents. Access controlled; planned labels visible only as clearly marked concepts in this exploration. A screen title, breadcrumb and visible current state must answer where the user is.

## 6. Spacing and layout

Desktop maximum 1360px, 72px rail, 32px margins, 8px rhythm. Dashboard: 8:4 work queue and schedule. Cases list spans full width with filter toolbar. Case details uses a 2:1 workstream and context sidebar. Moderate 10px radius, open whitespace. No content overlap.

## 7. Buttons, forms, tables and cards

Forest primary button, white secondary button with border, text tertiary. 48px inputs with persistent labels and examples. 52px table rows with generous title cell and visible row actions. Cards only for a current task or calendar day. Disable actions the viewer lacks permission for and explain why when appropriate. Validation appears beside the relevant field without echoing sensitive values.

## 8. Search and filtering

Search bar is always visible above lists; filter presets for My cases, Open, This week, then advanced fields. Empty results offer clear filters. Never conflate client and party without a role label. Preserve query and filters when returning from a case. Distinguish no results from loading and network errors.

## 9. Accessibility requirements

Target WCAG 2.2 AA: text contrast ≥4.5:1, non-text contrast ≥3:1, visible 2px focus, semantic headings and table headers, keyboard navigation, predictable tab order, labelled icons, error summary plus inline errors, and 44×44px touch targets. Color is never the only signal. Do not animate essential information.

## 10. RTL/LTR behavior

Mirror navigation rail and context sidebar; RTL uses logical spacing and Arabic numerals only according to locale setting. Preserve mixed-direction references in bdi. Test Arabic form hints, search and validation after mirroring. Use logical alignment and directional isolation, not string reversal. Localize stage, case type, party roles and court terminology.

## 11. Responsive behavior

Under 768px, bottom tabs Today, Cases, Calendar, More and a full-screen search/filter sheet. Tables become labelled record rows. Case workstream uses Overview, Proceedings, Parties, Activity tabs. 44px minimum targets. At 200% zoom, core actions stay usable and text does not clip.

## 12. Loading, error and empty states

Skeletons match the table/record shape. An error names the affected operation and offers retry without exposing private data. Empty state distinguishes a new workspace from filtered no-results and gives a relevant action. Permission denial uses a dedicated message; planned features are labelled Planned rather than pretending to work.

## 13. Motion guidelines

160–200ms easing with opacity/transform on panel opening and completion feedback. Reduced motion disables transitions; no attention-grabbing loops. State changes must also be announced in text or accessible status, as needed.

## 14. Prohibited anti-patterns

No neon, gradients, generic stock photos, tiny pale text, icon-only unlabeled navigation, floating labels, hidden row actions, emoji, or three identical statistic cards. Never use real client names, documents, source code, secrets, fabricated performance figures, or unsupported product claims in concept screens.
