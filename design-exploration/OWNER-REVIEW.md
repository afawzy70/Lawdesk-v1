# LawDesk Stitch owner review guide

## Open the four directions

Sign into Google Stitch with the account that has access to these **private** projects:

1. [A — Legal Executive](https://stitch.withgoogle.com/projects/17616592170830450346)
2. [B — Modern Workspace](https://stitch.withgoogle.com/projects/2159660728911017526)
3. [C — Case Intelligence](https://stitch.withgoogle.com/projects/2809941713949337888)
4. [D — Modern Chambers](https://stitch.withgoogle.com/projects/2890116183553293246)

The project IDs and titles were verified through Stitch MCP. The links follow Stitch's project URL format; opening a private project requires the owner's authenticated Stitch session. The API did not provide a separate shareable URL, and anonymous browser access could not be verified.

## Compare the same tasks

For each concept, review the six desktop topics (Dashboard, Cases List, Case Details, Clients, Hearings, Documents), the three mobile topics (Dashboard, Cases List, Case Details), and the Arabic RTL Cases List. Use fictional case `LD-2026-00421` as the common anchor. Compare: finding a case, reading its parties and first-instance proceeding, seeing the next hearing, and locating a deadline. The other IDs in the concepts are also fictional.

Start with [COMPARISON.md](COMPARISON.md). Its twelve local exports show a dashboard, case details and RTL list for every direction. Full IDs and screenshot file resources are in [STITCH-EVIDENCE.json](STITCH-EVIDENCE.json).

## Canvas and evidence limitation

At the final MCP check, the project Canvas listed **A: 9 screens, B: 6, C: 6, D: 1**. Stitch `get_screen` independently confirmed **9 core screens plus one Arabic variant for each project**, and one additional A RTL revision. The remaining verified resources may not appear on the Canvas in the web app. Do not count a missing Canvas thumbnail as a failed generation, and do not assume that an API-confirmed screen is already placed on the Canvas. The twelve local screenshots make the four directions comparable while this Stitch linking issue remains.

## Review prompts for the owner

- Which home view fits the users' daily decisions: A's partner portfolio, B's task queue, C's operations grid, or D's diary?
- Can a lawyer find `LD-2026-00421`, its client and opponent, status, proceeding and next hearing quickly?
- Is the desktop case details view efficient without scrolling past a large hero area?
- Are the Arabic labels, court names, dates, case references and navigation naturally right-to-left without clipping?
- Is a planned module visibly a concept, especially Clients Management, global Hearings and Documents?
- Which table density and mobile record shape best match the firm's case volume?

Mark invented factual/legal text as placeholder. The generated screens are not a tested workflow and do not prove accessibility, permissions or backend behavior. The current `develop` application has a sample dashboard and ready case/per-case proceeding areas; global hearings and documents are roadmap items.

## Suggested decision

Choose **B as the base**, then specify which of C's data-density controls, A's summary and D's file organization should be included. Before implementation, commission a corrected desktop Case Details layout, a content pass on every fictional record, and hands-on Arabic/accessibility checks.
