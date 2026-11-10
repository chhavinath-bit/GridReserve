#!/usr/bin/env python3
"""Smoke test of a running GridReserve through its public address (M1.10).

    scripts/smoke-test.py https://203-0-113-5.sslip.io      # the deploy host (run by deploy.yml)
    scripts/smoke-test.py http://localhost:8000             # compose --profile full on a laptop

Checks, in the order a visitor meets them:
  1. the front door serves the app, and the gateway's health is UP;
  2. login, then refresh with the cookie alone (what a page reload does): proves the cookie and Origin settings;
  3. hold -> confirm -> cancel on a free slot at least 3 hours ahead (so cancelling is inside the policy);
  4. what must stay private is not reachable: /internal/**, service actuator paths, and (with --host) the
     platform ports from the outside.
Uses its own account (SMOKE_EMAIL / SMOKE_PASSWORD; registered on the first run), never a demo account, whose
booking limit any visitor could fill. Exits 1 on the first failure. No token or password is printed.
"""
import argparse
import datetime as dt
import http.cookiejar
import json
import os
import socket
import ssl
import sys
import urllib.error
import urllib.request

PRIVATE_PORTS = [5432, 8080, 8081, 8082, 8761, 8888]


class Client:
    def __init__(self, base, ca_file=None):
        self.base = base.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        tls = urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=ca_file))
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies), tls)
        self.token = None

    def call(self, method, path, body=None, auth=True, headers=None):
        request = urllib.request.Request(self.base + path, method=method,
                                         data=None if body is None else json.dumps(body).encode())
        request.add_header("Accept", "application/json")
        if body is not None:
            request.add_header("Content-Type", "application/json")
        if auth and self.token:
            request.add_header("Authorization", "Bearer " + self.token)
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        try:
            with self.opener.open(request, timeout=20) as response:
                raw = response.read()
                return response.status, (json.loads(raw) if raw and raw[:1] in b"{[" else raw)
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                return error.code, json.loads(raw)
            except ValueError:
                return error.code, raw


def check(ok, message):
    print(("ok   " if ok else "FAIL ") + message)
    if not ok:
        sys.exit(1)


def auth_headers(client):
    # What the browser sends to refresh and logout (FR-AUTH-12): its own Origin and the client header.
    origin = client.base
    return {"Origin": origin, "X-GridReserve-Client": "web"}


def sign_in(client, email, password):
    status, body = client.call("POST", "/api/v1/auth/login", {"email": email, "password": password}, auth=False,
                               headers=auth_headers(client))
    if status == 401:
        status, body = client.call("POST", "/api/v1/auth/register",
                                   {"email": email, "password": password, "displayName": "Smoke Test"},
                                   auth=False, headers=auth_headers(client))
    check(status in (200, 201) and "accessToken" in body, f"sign in as the smoke-test account ({status})")
    client.token = body["accessToken"]


def find_slot(client, earliest):
    """First free slot that starts after `earliest`, on any active bookable, in the next three days (UTC dates; the server answers in the venue's own days)."""
    status, categories = client.call("GET", "/api/v1/categories")
    check(status == 200 and categories, f"categories listed ({status}, {len(categories or [])})")
    for category in categories:
        _, venues = client.call("GET", f"/api/v1/venues?categoryId={category['id']}&size=100")
        for venue in venues.get("items", []):
            _, bookables = client.call("GET", f"/api/v1/venues/{venue['id']}/bookables?size=100")
            for bookable in bookables.get("items", []):
                for days in (0, 1, 2):
                    date = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=days)).date().isoformat()
                    status, availability = client.call(
                        "GET", f"/api/v1/bookables/{bookable['id']}/availability?date={date}")
                    if status != 200 or availability.get("closed"):
                        continue
                    for slot in availability["slots"]:
                        start = dt.datetime.fromisoformat(slot["start"].replace("Z", "+00:00"))
                        if slot["status"] == "FREE" and start >= earliest:
                            end = start + dt.timedelta(minutes=availability["minDurationMinutes"])
                            return bookable["id"], slot["start"], end.isoformat().replace("+00:00", "Z")
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("base_url")
    parser.add_argument("--host", help="also check from here that the platform ports of this host are closed")
    parser.add_argument("--ca-file", help="trust this CA too (a local rehearsal with Caddy's own test certificate)")
    args = parser.parse_args()
    email = os.environ.get("SMOKE_EMAIL", "smoke-test@example.test")
    password = os.environ.get("SMOKE_PASSWORD", "smoke-test-pass")

    client = Client(args.base_url, args.ca_file)
    status, page = client.call("GET", "/", auth=False, headers={"Accept": "text/html"})
    check(status == 200 and b'<div id="root">' in page, f"front page served ({status})")
    status, page = client.call("GET", "/bookings", auth=False, headers={"Accept": "text/html"})
    check(status == 200 and b'<div id="root">' in page, f"a client-side route survives a reload ({status})")
    status, health = client.call("GET", "/actuator/health", auth=False)
    check(status == 200 and health.get("status") == "UP", f"gateway health UP ({status})")

    sign_in(client, email, password)
    client.token = None  # forget the access token, as a reload does; only the cookie is left
    status, body = client.call("POST", "/api/v1/auth/refresh", auth=False, headers=auth_headers(client))
    check(status == 200 and "accessToken" in body, f"refresh with the cookie alone ({status})")
    client.token = body["accessToken"]
    status, _ = client.call("POST", "/api/v1/auth/refresh", auth=False,
                            headers={"Origin": "https://evil.example", "X-GridReserve-Client": "web"})
    check(status == 403, f"refresh from another Origin refused ({status})")

    slot = find_slot(client, dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=3))
    check(slot is not None, "found a free slot at least 3 hours ahead")
    bookable_id, start, end = slot
    status, hold = client.call("POST", "/api/v1/bookings", {"bookableId": bookable_id, "start": start, "end": end})
    check(status == 201 and hold.get("status") == "HELD", f"hold bookable {bookable_id} at {start} ({status})")
    status, confirmed = client.call("POST", f"/api/v1/bookings/{hold['id']}/confirm")
    check(status == 200 and confirmed.get("status") == "CONFIRMED", f"confirm ({status})")
    status, cancelled = client.call("POST", f"/api/v1/bookings/{hold['id']}/cancel", {"reason": "smoke test"})
    check(status == 200 and cancelled.get("status") == "CANCELLED", f"cancel ({status})")

    for path in ("/internal/users?ids=1", "/api/v1/internal/users?ids=1", "/actuator/env", "/actuator/refresh",
                 "/actuator/health/readiness"):
        status, _ = client.call("GET", path)
        check(status in (401, 403, 404), f"{path} not reachable ({status})")

    if args.host:
        for port in PRIVATE_PORTS:
            try:
                socket.create_connection((args.host, port), timeout=3).close()
                is_open = True
            except OSError:
                is_open = False
            check(not is_open, f"port {port} closed from outside")
    print("Smoke test passed")


if __name__ == "__main__":
    main()
