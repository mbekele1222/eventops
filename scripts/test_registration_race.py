import json
import uuid
from concurrent.futures import ThreadPoolExecutor
from http.cookiejar import CookieJar
from threading import Barrier
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen

BASE_URL = "http://localhost:8080"
EVENT_TITLE = "Last Seat Race Test"

def main():
    with urlopen(BASE_URL + "/api/events", timeout=15) as response:
        events = json.load(response)

    matches = [event for event in events if event["title"] == EVENT_TITLE]
    if not matches:
        raise SystemExit("Create the Last Seat Race Test event first.")

    event = max(matches, key=lambda item: item["id"])
    if event["capacity"] != 1:
        raise SystemExit("The test event must have capacity 1.")

    event_id = event["id"]
    print(f"Testing event {event_id}: {EVENT_TITLE} (capacity 1)")

    def signup(email, gate=None):
        client = build_opener(HTTPCookieProcessor(CookieJar()))

        with client.open(BASE_URL + "/api/csrf", timeout=15) as response:
            csrf = json.load(response)

        request = Request(
            BASE_URL + f"/api/events/{event_id}/registrations",
            data=json.dumps({
                "fullName": "Race Test",
                "email": email
            }).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                csrf["headerName"]: csrf["token"]
            },
            method="POST"
        )

        if gate is not None:
            gate.wait(timeout=15)

        try:
            with client.open(request, timeout=15) as response:
                receipt = json.load(response)
                return email, response.status, receipt.get("status")
        except HTTPError as error:
            status_code = error.code
            error.close()
            return email, status_code, "ERROR"

    run_id = uuid.uuid4().hex[:12]
    emails = [
        f"race-{run_id}-1@example.com",
        f"race-{run_id}-2@example.com"
    ]
    gate = Barrier(2)

    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(signup, email, gate) for email in emails]
        results = [future.result() for future in futures]

    for email, http_status, status in results:
        print(f"{email}: HTTP {http_status}, {status}")

    if any(result[1] != 201 for result in results):
        raise SystemExit("FAIL: Both signup requests should return HTTP 201.")

    statuses = sorted(result[2] for result in results)
    if statuses != ["CONFIRMED", "WAITLISTED"]:
        raise SystemExit(
            "FAIL: Expected one CONFIRMED and one WAITLISTED. "
            "Use a new, empty test event for each run."
        )

    duplicate = signup(emails[0].upper())
    print(f"Mixed-case duplicate: HTTP {duplicate[1]}")

    if duplicate[1] != 409:
        raise SystemExit("FAIL: The duplicate signup should return HTTP 409.")

    print("PASS: One confirmed, one waitlisted, duplicate rejected.")

if __name__ == "__main__":
    main()
