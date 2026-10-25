# Modern Chambers: visual review against approved Stitch

Approved source: [Google Stitch project 2890116183553293246](https://stitch.withgoogle.com/projects/2890116183553293246). Stitch MCP returned one saved screen, **LawDesk - Modern Chambers Dashboard**, ID `51490074816b4a16a0ebfbc19f35a506`, desktop 2560 × 2048. [Saved source screenshot](reference/stitch-dashboard.png). No approved report, mobile, or other route screens were saved in this project. The review below compares the actual render, not its prompt.

Browser renders use fictional, contract-shaped local responses; they do not prove UAT integration or independent QA. Compare [English dashboard](screenshots/after/dashboard-stitch-en-1366.png), [Arabic dashboard at 375 px](screenshots/after/dashboard-stitch-ar-375.png), [English case inventory report](screenshots/after/inventory-current-en-1366.png), and [Arabic report at 320 px](screenshots/after/inventory-current-ar-320.png). The original source is much wider than the 1366 px target and includes features absent from LawDesk v1.

| Stitch element | Current implementation | Visual result / remaining gap |
| --- | --- | --- |
| Charcoal rail, brass legal mark and active state | Shared shell on all signed-in routes; responsive menu on phone | Close in palette and hierarchy. The app rail has more real modules, so labels are smaller and the rail is proportionally busier. |
| Ivory canvas, paper panels, fine rules and restrained brass | Shared design tokens and components | Consistent on dashboard, cases, operations, administration and reports. The live app uses simpler typography and less editorial contrast than the source. |
| Search in top bar | Permission-gated search of case reference/title to existing case list | Works with real API and URL state. The broader party/circuit search shown by Stitch has no corresponding v1 contract. |
| Horizontal operational signals | Four values from the real dashboard report | Placement and rhythm match. Values and labels reflect existing backend fields; no fabricated pending submissions or trust accounts. |
| 5:7 Court Diary / Active Dossier first row | Live scheduled hearings and active case preview, permission-scoped | Main composition now follows the source. With one fixture record the panels are shorter and less detailed than the Stitch example. |
| Rich dossier facts and evidence bundle | Link to the full real case workspace and its actual documents/parties | Full information remains inside case details. The dashboard does not duplicate unsupported client, counsel, claim value, POA, bundle or milestone data. |
| Arabic legal language | Localized chrome and a fictional Arabic case title in the new sample | RTL order, reference isolation and text wrapping work at 320/375 px. The source mixes Arabic/English text in every panel; live records show the language stored by users. |
| Reports | No report screen exists in the approved Stitch project | The five real reports inherit paper, charcoal, brass, typography, tabs, form and table language. This is a Modern Chambers interpretation, not a direct screen match. At 320 px the inventory's ten filters create a long first section; the data table is locally scrollable. |
| Mobile | No approved mobile source screen exists | Dashboard stacks diary and case register, signals stack at 320 px, menu and global search remain operable. This is a responsive interpretation. |

The source also depicts billing/trust accounting, print roll and a session folio. Those controls are absent from the implemented Phase 2–6 product and were not introduced as visual-only actions. The current report and case data remain tied to their existing APIs, permissions and workflows.

Automated checks on the current dashboard and case inventory at 320, 375, 768 and 1366 px in both languages reported no document-level horizontal overflow and no axe WCAG 2/2.1 A/AA violations. See [dashboard matrix](screenshots/after/dashboard-current-matrix.json) and [inventory matrix](screenshots/after/inventory-current-matrix.json). These checks do not certify all screen-reader, keyboard, browser or UAT behavior.
