# LawDesk — Modern Chambers Design System

## 1. Design philosophy

A distinctive boutique legal workspace with editorial rhythm and excellent case-file organization. Density 6/10, asymmetry 7/10, motion 2/10. The case file feels curated, but remains an efficient software tool. This is a concept for the LawDesk product roadmap; planned modules must be marked as such in review material.

## 2. Semantic colors

- **Parchment canvas** `#F3EFE6` — application background.
- **Ivory paper** `#FFFCF6` — reading surface.
- **Charcoal chrome** `#272A29` — navigation and mastheads.
- **Primary ink** `#252926` — body text.
- **Muted ink** `#625F57` — metadata.
- **Brass accent** `#876B3C` — single accent for selection and primary action.
- **Border** `#D1C9BA` — separators.
- **Danger** `#963C3B` — urgent status.
- **Success** `#326451` — completed.

Use status colors only for meaning and pair every status with a text label. One decorative accent; functional danger and success are exceptions. Avoid pure black.

## 3. Typography hierarchy and 4. Language fonts

English: DM Sans for headings and body, JetBrains Mono for references. Arabic: Cairo headings and IBM Plex Sans Arabic body. Display 30/38, section 20/28, body 15/25, metadata 13/21; Arabic body 16/29. No serif inside software screens. Use 65ch maximum for paragraphs, tabular numerals where relevant, and enough Arabic line height for dots and diacritics.

## 5. Navigation principles

A 104px charcoal rail with wordmark and labelled destinations, plus a horizontal case-file subnav. Primary domains: Chambers, Matters, People, Diary, Library. Context for a case appears in a paper tab strip, not an additional generic sidebar. A screen title, breadcrumb and visible current state must answer where the user is.

## 6. Spacing and layout

Desktop 1440px: 104px rail, 56px margin, editorial 5:7 division. Dashboard leads with diary and featured matters; details use a 5:7 narrative timeline and document/party file index. 8px rhythm, 24px/32px spacing, 4px corners, restrained brass rules. No content overlap.

## 7. Buttons, forms, tables and cards

Charcoal primary button with brass active underline; ivory bordered secondary. Forms resemble labelled dossier fields with 46px height. Tables have strong typographic hierarchy and subtle horizontal rules, no vertical grid. Document rows show type, case, date, owner and file status; no fake upload claim. Disable actions the viewer lacks permission for and explain why when appropriate. Validation appears beside the relevant field without echoing sensitive values.

## 8. Search and filtering

Library and matters have scoped search with an explicit scope selector, filter bar and saved-view affordance labelled Concept only. Search result snippets expose case reference and role context. Preserve query and filters when returning from a case. Distinguish no results from loading and network errors.

## 9. Accessibility requirements

Target WCAG 2.2 AA: text contrast ≥4.5:1, non-text contrast ≥3:1, visible 2px focus, semantic headings and table headers, keyboard navigation, predictable tab order, labelled icons, error summary plus inline errors, and 44×44px touch targets. Color is never the only signal. Do not animate essential information.

## 10. RTL/LTR behavior

Charcoal rail and file index mirror; editorial reading order remains logical for Arabic. Use bdi for reference and dates, preserve punctuation and avoid mirrored court numbers. Longer Arabic titles expand without clipping. Use logical alignment and directional isolation, not string reversal. Localize stage, case type, party roles and court terminology.

## 11. Responsive behavior

At mobile width, charcoal top bar and bottom labelled navigation replace rail. Case file sections become accordion tabs; document index becomes single-column records. Diary remains scrollable by day with a visible date label. At 200% zoom, core actions stay usable and text does not clip.

## 12. Loading, error and empty states

Skeletons match the table/record shape. An error names the affected operation and offers retry without exposing private data. Empty state distinguishes a new workspace from filtered no-results and gives a relevant action. Permission denial uses a dedicated message; planned features are labelled Planned rather than pretending to work.

## 13. Motion guidelines

160ms page-section fade and 120ms press translation using opacity/transform; reduced motion has immediate state changes. No decorative perpetual motion. State changes must also be announced in text or accessible status, as needed.

## 14. Prohibited anti-patterns

No parchment textures that reduce contrast, serif dashboard type, fake document thumbnails, gold gradients, unlabelled abstract icons, emojis, hero artwork in work screens, or generic equal-card grids. Never use real client names, documents, source code, secrets, fabricated performance figures, or unsupported product claims in concept screens.
