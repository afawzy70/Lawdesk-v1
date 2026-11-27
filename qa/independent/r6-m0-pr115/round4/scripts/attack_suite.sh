#!/bin/bash
# QA Round 4 attack suite. Run AFTER the author's probe files 05..53 were replayed on a DISPOSABLE PostgreSQL 18.6 (database "lawdesk").
# env: PGP=<port>  PY=<python with psycopg2>  E=<author's pg18-probe dir>   Synthetic data only. Never run against UAT.
R="$PY ${RUN:-run.py}"
echo "### A. operator roles (author file 80/80b), then X1 legal-credential direct complaint read"
$R postgres lawdesk $E/80-two-party-operator.sql.txt >/dev/null; $R lawdesk_owner lawdesk $E/80b-two-party-objects.sql.txt >/dev/null
$R lawdesk_legal lawdesk <(printf "select id,status from lawdesk.complaint\n;;\nselect * from lawdesk.complaint_case_link\n;;\nselect * from lawdesk.referral_draft\n;;\nselect user_id,authority,state from lawdesk.legal_authority_grant\n;;\nselect * from lawdesk.owner_policy\n")
echo "### B. X3 single operator login (no second person) activates policy, grants authority, records gate approval"
$R op_alice lawdesk <(printf "select lawdesk.r6_op_set_policy('P-DECLASS', true)\n;;\nselect lawdesk.r6_op_grant_authority('00000000-0000-0000-0000-0000000000a5','COMPLAINT_APPROVE') is not null\n;;\nselect lawdesk.r6_op_record_legal_approval('G-FINAL',1) is not null\n")
echo "### C. 009-a referral of a PRE-EXISTING case by forging created_at (legal credential, plain DML)"
$R lawdesk_app lawdesk <(printf "insert into lawdesk.legal_case (id, reference_no, title, case_type, opened_on, created_by, updated_by, created_at) values ('00000000-0000-0000-0000-0000000009a1','LD-2020-00001','PRE-EXISTING unrelated case','CIVIL', date '2020-01-01','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000a1', now() - interval '400 days')\n")
$PY hijack.py
echo "### D. 006-a runtime credential rewrites an approved R6 deadline by changing source_type first"
$R lawdesk_app lawdesk g2.sql
echo "### E. concurrency and lock order"
$R lawdesk_owner lawdesk <(printf "insert into lawdesk.complaint(id) values ('00000000-0000-0000-0000-00000000f401'),('00000000-0000-0000-0000-00000000f402')\n;;\ninsert into lawdesk.referral_draft values ('00000000-0000-0000-0000-00000000f401','{}',true),('00000000-0000-0000-0000-00000000f402','{}',true)\n")
$PY conc.py
