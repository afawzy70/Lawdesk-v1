# LD-DOC-13 — إدارة القوائم المنسدلة (صفحة الأدمن) / Administrator dropdown lists

Stream A of the r5 screens round. Page: **/admin/lists** (tab «القوائم المنسدلة»), right `LIST_ADMIN`, held by `ADMIN` only
(seeded by V860; no other role). Owner decision: «كل القوائم دون استثناء». What that means in practice is below, without
overstating it.

## Design

* One catalogue (`ListCatalog`) names every list. Options live in `lookup_value` (extended with `sort_order` and `system`),
  in `managed_list_value` (settlement, directive, template kinds: existing service and permissions) or in the court register.
* Existing values were seeded with their **current codes** and labels from the screens' own messages, so no stored code changes.
* A value that system logic depends on is `system`: code immutable, labels and order editable, **never deactivated** (422
  `SYSTEM_VALUE_FIXED`). A list whose values drive logic is `label-only`: no new option (422 `LIST_LABEL_ONLY`), because a code
  the code does not know cannot work (Java enums, status machines, scopes).
* Nothing is deleted. Every create/edit/activate/deactivate keeps a history row (`lookup_value_history`) and an audit event.
* Optimistic version on every edit (409 on a stale one). Duplicate code: 409.
* The three original lists (`JUDGMENT_TYPE`, `JUDGMENT_NATURE`, `DOCUMENT_CATEGORY`) stay manageable by `LOOKUP_MANAGE` on the
  old tab; every other lookup list requires `LIST_ADMIN` on the old endpoints too (403 otherwise).
* Screens read option labels from `GET /api/v1/lookups/{list}` through `useListOptions`; if it is unavailable or empty they keep
  their built-in i18n labels.

## Inventory

| List | Where stored | Editable | Why / what a new option does |
|---|---|---|---|
| CASE_IMPORTANCE (أهمية القضية) | `lookup_value`; column `legal_case.importance` | **add + edit + deactivate** | The CHECK was replaced by a format check (V860) and a check against the active options in `CaseService`; a case keeps an option deactivated later. Screens: case registration/edit (dropdown) and case details (label). |
| JUDGMENT_TYPE, JUDGMENT_NATURE, DOCUMENT_CATEGORY | `lookup_value` | add + edit + deactivate | Already managed lists (LD-051); now also on this page. |
| SETTLEMENT_KIND, DIRECTIVE_KIND, TEMPLATE_KIND | `managed_list_value` | add + edit + deactivate | Existing LD-057 service and its permissions; this page calls the same endpoints. |
| COURTS | `court` | add / edit / deactivate on the courts screen | Linked from this page; uses the existing court service and `COURT_MANAGE`. The court **level** is a fixed list (COURT_LEVEL below). |
| CASE_TYPE | `lookup_value`; `legal_case.case_type` (CHECK, Java enum, access scope `CASE_TYPE`, reports) | **label + order only** | Scopes and reports key on the code. Case registration reads label and order from the catalogue. |
| CASE_STATUS | `legal_case.status` (CHECK, status machine, closing/archiving) | label only | Workflow logic. Not consumed by screens yet. |
| AUTHORITY_POSITION | `legal_case.authority_position` (CHECK) | label only | Plaintiff/defendant drives sides of parties. Not consumed by screens yet. |
| COURT_LEVEL | `court.level` (CHECK) | label only | Stage logic. Not consumed by screens yet. |
| HEARING_PURPOSE | `hearing.purpose` (CHECK, Java enum) | label only | Not consumed by screens yet. |
| HEARING_STATUS | `hearing.status` (CHECK, state machine) | label only | Not consumed by screens yet. |

## Not done (be explicit)

* About 70 further enum/CHECK-backed columns (task, expert, enforcement, directive, library, document… statuses and kinds) are
  **not catalogued**. Each needs its Java enum and CHECK replaced and its screens re-wired; none was converted without that
  work. Listing them needs an owner decision on which are really user-facing choices.
* The label-only lists above are editable in the catalogue, but only `CASE_IMPORTANCE` and `CASE_TYPE` (registration form) read
  their labels from it today; status, position, level and hearing screens still use the built-in labels.
* Courts are managed on their own screen (linked), not inline.
* The new page is not tested in a browser on UAT; nothing is merged or deployed.
