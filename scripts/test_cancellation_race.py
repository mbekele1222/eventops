"""Exercise cancellation/signup contention against local EventOps and PostgreSQL."""

import json
import os
import subprocess
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from http.cookiejar import CookieJar
from pathlib import Path
from threading import Barrier
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, ProxyHandler, Request, build_opener

BASE_URL = "http://localhost:8080"
EVENT_TITLE = "Cancellation Race Test"
PSQL = "/opt/homebrew/opt/postgresql@17/bin/psql"


class TestFailure(Exception):
    pass


def check(condition, message):
    if not condition:
        raise TestFailure(message)


def psql_args(event_id):
    return [PSQL, "-X", "-qAt", "-h", "localhost", "-p", "5432",
            "-d", "eventops", "-v", "ON_ERROR_STOP=1",
            "-v", "event_id=" + str(event_id)]


def sql(event_id, query):
    result = subprocess.run(
        psql_args(event_id), input=query, text=True, capture_output=True,
        timeout=10, env=dict(os.environ, PGCONNECT_TIMEOUT="5")
    )
    check(result.returncode == 0, "PostgreSQL check failed: " + result.stderr.strip())
    return result.stdout.strip()


@contextmanager
def hold_event_lock(event_id):
    process = subprocess.Popen(
        psql_args(event_id), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True,
        env=dict(os.environ, PGCONNECT_TIMEOUT="5")
    )
    released = False

    def release():
        nonlocal released
        if released:
            return
        released = True
        try:
            process.communicate("ROLLBACK;\n", timeout=5)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            process.kill()
            process.communicate()

    try:
        process.stdin.write(r"""
SET lock_timeout = '5s';
SET statement_timeout = '10s';
SET idle_in_transaction_session_timeout = '30s';
BEGIN;
SELECT id FROM events WHERE id = :event_id FOR UPDATE;
\echo EVENTOPS_LOCK_READY
""")
        process.stdin.flush()
        while True:
            line = process.stdout.readline()
            check(line != "", "Could not acquire the test event lock.")
            if line.strip() == "EVENTOPS_LOCK_READY":
                break
            check("ERROR:" not in line, "Could not acquire the test event lock: " + line.strip())
        yield release
    finally:
        release()


def api(path, payload=None, gate=None):
    client = build_opener(ProxyHandler({}), HTTPCookieProcessor(CookieJar()))
    if payload is None:
        with client.open(BASE_URL + path, timeout=15) as response:
            return response.status, json.load(response)

    with client.open(BASE_URL + "/api/csrf", timeout=15) as response:
        csrf = json.load(response)
    request = Request(
        BASE_URL + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", csrf["headerName"]: csrf["token"]},
        method="POST"
    )
    if gate is not None:
        gate.wait(timeout=15)
    try:
        with client.open(request, timeout=20) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        status = error.code
        error.close()
        return status, {}


def main():
    check(Path(PSQL).is_file(), "PostgreSQL 17's psql executable was not found.")
    _, events = api("/api/events")
    matches = [event for event in events if event["title"] == EVENT_TITLE]
    check(matches, "Create a new Cancellation Race Test event with capacity 1 first.")
    event = max(matches, key=lambda item: int(item["id"]))
    event_id = int(event["id"])
    check(event["capacity"] == 1, "The test event must have capacity 1.")

    database_event = sql(event_id, """
SELECT json_build_object('title', title, 'capacity', capacity)
FROM events WHERE id = :event_id;
""")
    check(database_event, "The API event was not found in the local eventops database.")
    check(json.loads(database_event) == {"title": EVENT_TITLE, "capacity": 1},
          "The API and psql must use the same local eventops database.")
    count = sql(event_id, "SELECT count(*) FROM registrations WHERE event_id = :event_id;")
    check(count == "0", "Use a new, empty Cancellation Race Test event for each run.")

    print("Testing event", event_id, "with capacity 1")
    run_id = uuid.uuid4().hex[:12]
    receipts = []
    names = ["Race Owner", "First Waiter", "Second Waiter"]
    for index, name in enumerate(names):
        code, receipt = api("/api/events/" + str(event_id) + "/registrations", {
            "fullName": name,
            "email": "cancel-race-" + run_id + "-" + str(index) + "@example.com"
        })
        expected = "CONFIRMED" if index == 0 else "WAITLISTED"
        check(code == 201 and receipt.get("status") == expected,
              "Could not prepare the initial confirmed attendee and two waiters.")
        receipts.append(receipt)

    gate = Barrier(4)
    cancel_payload = {"cancellationToken": receipts[0]["cancellationToken"]}
    with ThreadPoolExecutor(max_workers=3) as executor:
        with hold_event_lock(event_id) as release:
            futures = [
                executor.submit(api, "/api/registrations/cancel", cancel_payload, gate),
                executor.submit(api, "/api/registrations/cancel", cancel_payload, gate),
                executor.submit(api, "/api/events/" + str(event_id) + "/registrations", {
                    "fullName": "New Arrival",
                    "email": "cancel-race-" + run_id + "-new@example.com"
                }, gate)
            ]
            try:
                gate.wait(timeout=15)
                deadline = time.monotonic() + 8
                waiting = 0
                while time.monotonic() < deadline:
                    waiting = int(sql(event_id, """
SELECT count(*) FROM pg_stat_activity
WHERE datname = current_database()
  AND wait_event_type = 'Lock'
  AND query ILIKE '%FROM events%FOR UPDATE%';
"""))
                    if waiting >= 3:
                        break
                    time.sleep(0.05)
            finally:
                release()
            results = [future.result(timeout=25) for future in futures]

    check(waiting >= 3, "Expected all three requests to wait on the event lock; observed " + str(waiting) + ".")
    print("Observed all three requests waiting on the event lock.")
    for status, data in results[:2]:
        check(status == 200 and data.get("status") == "CANCELLED",
              "Both cancellation attempts must return HTTP 200 and CANCELLED.")
    new_status, new_receipt = results[2]
    check(new_status == 201 and new_receipt.get("status") == "WAITLISTED",
          "The new arrival must join the waitlist behind existing waiters.")
    receipts.append(new_receipt)
    names.append("New Arrival")
    expected = ["CANCELLED", "CONFIRMED", "WAITLISTED", "WAITLISTED"]

    for name, receipt, expected_status in zip(names, receipts, expected):
        status, view = api("/api/registrations/status", {
            "cancellationToken": receipt["cancellationToken"]
        })
        check(status == 200 and view.get("status") == expected_status,
              name + " had an unexpected status after the competing requests.")

    database_rows = sql(event_id, """
SELECT json_build_object('id', id, 'name', full_name, 'status', status)
FROM registrations WHERE event_id = :event_id ORDER BY id;
""")
    rows = [json.loads(line) for line in database_rows.splitlines()]
    check([row["id"] for row in rows] == [receipt["registrationId"] for receipt in receipts],
          "Expected exactly four database registrations in signup order.")
    check([row["status"] for row in rows] == expected,
          "The database statuses did not match the expected cancellation/promotion result.")
    for row in rows:
        print(row["name"] + ": " + row["status"])
    print("PASS: one cancelled, one confirmed, two waitlisted; no double promotion or queue jumping.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        raise SystemExit("FAIL: " + (str(error) or type(error).__name__))
