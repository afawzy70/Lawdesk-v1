# Feature page contract (`content/features/<id>.json`)

One JSON file per feature, **Arabic for everything a reader sees**. Code identifiers, endpoint paths, class and file names stay in their original (LTR) form. Synthetic examples only.

```jsonc
{
  "id": "cases-register",                 // kebab-case, equals the file name
  "category": "cases",                    // one of categories.json
  "name_ar": "تسجيل قضية جديدة",
  "summary_ar": "...",                    // what it does (2-3 sentences, plain words)
  "purpose_ar": "...",                    // why it exists
  "who_ar": ["مدير الشؤون القانونية"],     // who can use it (from the real permission names in code)
  "who_perms": ["CASE_CREATE"],           // exact permission/role identifiers in code
  "access_ar": "القائمة > القضايا > قضية جديدة",   // how to reach it; frontend route in "route"
  "route": "/cases/new",
  "prerequisites_ar": ["..."],
  "fields": {
    "required": [{"name_ar":"رقم القضية","code":"caseNumber","rule_ar":"...", "evidence":[E]}],
    "optional": [ ... ]
  },
  "steps": [                               // the guided walkthrough, numbered in order
    {
      "n": 1,
      "actor_ar": "الموظف المخوّل",
      "action_ar": "...",
      "response_ar": "...",                // what the system does/shows
      "rules_ar": ["..."],                 // business rules that apply at this step
      "data_ar": "...",                    // data involved
      "next_ar": "...",
      "evidence": [E],                     // REQUIRED, >= 1
      "alt": [{"kind":"denied|validation|error|exceptional","title_ar":"...","detail_ar":"...","evidence":[E]}]
    }
  ],
  "rules": [{"text_ar":"...","evidence":[E]}],
  "after_submit_ar": "...",
  "notifications_ar": ["..."],            // [] if none exist (say so; do not invent)
  "approvals_ar": ["..."],                // [] if none
  "errors": [{"code":"HTTP 403 / stable error code","meaning_ar":"...","evidence":[E]}],
  "result_ar": "...",                     // what the user sees at the end
  "related": ["other-feature-id"],
  "endpoints": [{"method":"POST","path":"/api/v1/cases","evidence":E}],
  "tech": {                                // optional technical chain, ONLY links proven by evidence
    "chain": [{"layer":"screen|component|api_call|controller|service|authorization|repository|tables|event|result",
               "label":"CaseCreatePage","ref":E,"data_ar":"ما الذي ينتقل بين هذه الحلقة والتي بعدها"}]
  },
  "status": "IMPLEMENTED_ON_DEVELOP",     // IMPLEMENTED_ON_DEVELOP | VERIFIED_ON_UAT | IN_DEVELOPMENT | PROPOSED | BLOCKED | NOT_VERIFIED
  "status_note_ar": "...",
  "status_evidence": [E],
  "verification_ar": "..."                // what was and was NOT verified (tests found, UAT evidence, gaps)
}
```
`E` = `{"path":"backend/src/main/java/.../X.java","lines":"40-62","note":"short English or Arabic note"}`; the path must exist at the build SHA, the line range must lie inside the file, and for an endpoint the cited lines must contain its path fragment. A claim you cannot support with a file reference is **not written**; mark the gap in `verification_ar` and use `NOT_VERIFIED`.

Status rules: source code on develop is `IMPLEMENTED_ON_DEVELOP`. `VERIFIED_ON_UAT` only with cited independent UAT evidence for the same SHA. Unmerged PRs are `IN_DEVELOPMENT`; documentation-only requirements are `PROPOSED`; legally gated behaviour is `BLOCKED`.
