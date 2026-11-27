"""Shared helpers of the Round-5 probe (documentation evidence, not application code). Disposable servers on loopback only."""
import hashlib
import os
import uuid

import psycopg2

PORT = int(os.environ.get("PGP", "55611"))
PEOPLE = {k: "00000000-0000-0000-0000-0000000000%s" % k for k in ("a1", "a2", "a3", "a4", "a5", "a9")}


def connect(user, autocommit=True, db="lawdesk"):
    c = psycopg2.connect(host="127.0.0.1", port=PORT, user=user, dbname=db, connect_timeout=5,
                         options="-c lock_timeout=8000 -c statement_timeout=20000")
    c.autocommit = autocommit
    return c


def run(user, sql, *args):
    """One statement as `user` in its own transaction; returns the rows (or the row count)."""
    c = connect(user)
    try:
        cur = c.cursor()
        cur.execute(sql, args or None)
        return cur.fetchall() if cur.description else cur.rowcount
    finally:
        c.close()


def state(user, sql, *args):
    """SQLSTATE of a refused statement, or 'OK'."""
    try:
        run(user, sql, *args)
        return "OK"
    except psycopg2.Error as e:
        return e.pgcode or "CONNECT-REFUSED"


def one(sql, *args):
    return run("lawdesk_owner", sql, *args)[0][0]


def new_id():
    return str(uuid.uuid4())


def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()


def linked_judgment(linked=True):
    """A synthetic case + proceeding + judgment, written by the owner; `linked` adds the R6 procedural row."""
    case, proc, judg = new_id(), new_id(), new_id()
    a1 = PEOPLE["a1"]
    run("lawdesk_owner", "insert into lawdesk.legal_case (id, reference_no, title, case_type, opened_on, created_by, updated_by)"
        " values (%s, %s, 'synthetic', 'CIVIL', date '2026-01-01', %s, %s)", case, "LD-T-" + case[:13], a1, a1)
    run("lawdesk_owner", "insert into lawdesk.proceeding (id, case_id, stage, court_id, court_case_number, court_case_year,"
        " filing_date, created_by, updated_by) values (%s, %s, 'FIRST_INSTANCE', '00000000-0000-0000-0000-0000000000d1', %s,"
        " 2026, date '2026-01-10', %s, %s)", proc, case, proc[:12], a1, a1)
    run("lawdesk_owner", "insert into lawdesk.judgment (id, proceeding_id, judgment_date, outcome, created_by, updated_by)"
        " values (%s, %s, date '2026-06-01', 'AGAINST', %s, %s)", judg, proc, a1, a1)
    if linked:
        run("lawdesk_owner", "insert into lawdesk.judgment_procedural (judgment_id) values (%s)", judg)
    return case, proc, judg


def deadline(case, judg, pointer=True):
    """An approved JUDGMENT-sourced deadline written by the owner (what the writer function produces)."""
    d, a1, a2 = new_id(), PEOPLE["a1"], PEOPLE["a2"]
    run("lawdesk_owner", "insert into lawdesk.deadline (id, case_id, source_type, source_id, starts_on, period_days, due_on,"
        " action, calc_status, approved_by, approved_at, created_by, updated_by) values (%s, %s, 'JUDGMENT', %s,"
        " date '2026-06-01', 60, date '2026-08-01', 'appeal', 'APPROVED', %s, now(), %s, %s)", d, case, judg, a2, a1, a1)
    if pointer:
        run("lawdesk_owner", "update lawdesk.judgment set appeal_deadline_id = %s where id = %s", d, judg)
    return d


def row(table, key):
    return run("lawdesk_owner", "select to_jsonb(x) from lawdesk." + table + " x where id = %s", key)
