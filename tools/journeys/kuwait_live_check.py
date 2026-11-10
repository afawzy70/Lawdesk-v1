"""LIVE check of the server's clock and the Kuwait day (LD-DOC r3). Unlike the pinned-clock tests, this observes what the
deployed server answers NOW; it can only observe the 21:00:00Z-23:59:59Z boundary window if it is run inside that window.

What it does: signs in as the QA manager (credentials come from LAWDESK_QA_DIR and are never printed), reads the HTTP Date
header (the server's clock), the dashboard month and the workload export file name (both built from the server's Kuwait
"today"), creates ONE synthetic case marked SYNTH-DOC (the only write; no existing case is read or edited) and records how its
creation instant is serialized. Writes the result as JSON to the path given as the first argument.
"""

import email.utils
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

from uat import BASE, MARK, Session

KUWAIT = timezone(timedelta(hours=3))
out_path = sys.argv[1] if len(sys.argv) > 1 else "kuwait_live_check.json"
result = {"base": BASE, "marker": MARK}

started = datetime.now(timezone.utc)
result["client_utc_now"] = started.strftime("%Y-%m-%dT%H:%M:%SZ")
window = (started.hour >= 21)
result["inside_boundary_window_21_00Z_to_23_59Z"] = window

session = Session("manager")
with session.opener.open(BASE + "/api/v1/auth/csrf") as response:
    date_header = response.headers.get("Date")
server_now = email.utils.parsedate_to_datetime(date_header)
result["server_date_header"] = date_header
result["server_utc_now"] = server_now.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
result["clock_skew_seconds_vs_client"] = round((server_now - started).total_seconds(), 1)
expected_kuwait_day = server_now.astimezone(KUWAIT).strftime("%Y-%m-%d")
result["expected_kuwait_day_from_server_clock"] = expected_kuwait_day
result["utc_day_from_server_clock"] = server_now.astimezone(timezone.utc).strftime("%Y-%m-%d")

session.login("manager")

status, dashboard = session.call("GET", "/api/v1/reports/dashboard")
result["dashboard_status"] = status
if status == 200:
    result["dashboard_month"] = dashboard.get("month")
    result["dashboard_month_matches_kuwait_month"] = dashboard.get("month") == expected_kuwait_day[:7]

# The export file name carries the server's Kuwait "today" (ReportCalendar).
request_path = "/api/v1/reports/workload/export?format=csv&lang=en"
try:
    request = urllib.request.Request(BASE + request_path, headers={"Accept": "text/csv"})
    with session.opener.open(request, timeout=60) as response:
        disposition = response.headers.get("Content-Disposition")
except urllib.error.HTTPError as error:
    disposition = "HTTP %d" % error.code
result["workload_export_content_disposition"] = disposition
if disposition and "workload-" in disposition:
    result["export_day_matches_kuwait_day"] = ("workload-%s.csv" % expected_kuwait_day) in disposition

# The one write: a synthetic case dated with the Kuwait day (openedOn is @PastOrPresent on the Kuwait clock).
title = "%s Kuwait live clock check %s" % (MARK, int(time.time()))
status, case = session.call("POST", "/api/v1/cases", {"title": title, "caseType": "CIVIL", "openedOn": expected_kuwait_day})
result["synthetic_case_create_status"] = status
if status == 201 and isinstance(case, dict):
    result["synthetic_case_openedOn"] = case.get("openedOn")
    result["synthetic_case_createdAt_as_serialized"] = case.get("createdAt")
    result["synthetic_case_updatedAt_as_serialized"] = case.get("updatedAt")
    created = case.get("createdAt")
    if created:
        parsed = datetime.fromisoformat(created.replace("Z", "+00:00"))
        result["createdAt_ends_with_Z_or_offset_zero"] = created.endswith("Z") or created.endswith("+00:00")
        result["createdAt_as_kuwait_wall_clock"] = parsed.astimezone(KUWAIT).strftime("%Y-%m-%d %H:%M:%S")
        result["createdAt_within_120s_of_server_clock"] = abs((parsed - server_now).total_seconds()) < 120
else:
    result["synthetic_case_create_body_keys"] = sorted(case) if isinstance(case, dict) else None

with open(out_path, "w", encoding="utf-8") as out:
    json.dump(result, out, indent=2, ensure_ascii=False)
print(json.dumps(result, indent=2, ensure_ascii=False))
