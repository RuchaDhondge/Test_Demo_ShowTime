# Colleague Testing Guide

Use this guide after GitHub Actions publishes and deploys a new Demo Showtime version to the shared office server.

## Prerequisites

- Connect to the same office network as the host.
- Obtain the current shared URL from the host owner.
- Use synthetic names and images only.
- Do not share database credentials or use them outside the office network.

Current shared URL:

```text
http://172.30.3.102:8080
```

The host address can change if DHCP assigns a new address. Ask the host owner if the URL is unreachable.

## 1. Availability test

Open the shared URL in a browser. The Ticket Booking page should load.

Command-line check:

```bash
curl -i http://172.30.3.102:8080/healthz
```

Expected result:

```text
HTTP/1.1 200 OK

ok
```

## 2. Event catalogue test

1. Select **Pune**.
2. Confirm that sports, movie, and live events appear.
3. Select **Mumbai**.
4. Confirm that the event list changes.
5. Return to Pune and select each event.

API checks:

```bash
curl 'http://172.30.3.102:8080/api/events?city=Pune'
curl 'http://172.30.3.102:8080/api/events?city=Mumbai'
```

Expected result: JSON arrays containing events for the requested city.

## 3. Seat availability test

1. Select an event.
2. Confirm that its seat map appears.
3. Select and deselect several available seats.
4. Select **Refresh**.

API check:

```bash
curl 'http://172.30.3.102:8080/api/events/1/seats'
```

Expected result: each seat has an ID, label, and `AVAILABLE`, `HELD`, or `BOOKED` status.

## 4. Seat-hold test

1. Enter a synthetic demo-user value.
2. Select one or more available seats.
3. Select **Hold seats**.
4. Confirm that the page displays the hold expiry time.
5. Refresh the seat map.

Expected result: the selected seats are unavailable for other users for approximately two minutes.

API example:

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"userId":"colleague-test@example.com","seatIds":[1]}' \
  http://172.30.3.102:8080/api/events/1/holds
```

Expected result: HTTP `201 Created` with a hold ID and expiry timestamp.

Seat IDs can change state during shared testing. Query the seat endpoint and select an available ID before running the request.

## 5. Concurrent booking test

Use two colleague machines or two browser windows:

1. Open the same event on both clients.
2. Select the same available seat.
3. Submit both holds at approximately the same time.

Expected result:

- One request succeeds.
- The other receives a seat-unavailable message.
- The unsuccessful request must not partially hold any other requested seat.

The equivalent API failure is HTTP `409 Conflict`.

## 6. Hold-expiry test

1. Hold a seat.
2. Record the displayed expiry time.
3. Wait slightly longer than two minutes.
4. Refresh the seat map.

Expected result: the expired seat returns to `AVAILABLE`.

## 7. Event-image test

1. Select an event.
2. Upload a JPEG, PNG, or WebP image smaller than 2 MB.
3. Confirm that the uploaded image appears.
4. Refresh the page and retrieve the event image again.

Expected result: the backend stores and retrieves the image through emulated S3.

Unsupported formats and files larger than 2 MB should be rejected.

## 8. Invalid-request tests

Verify that the application handles invalid input:

- Submit a hold without choosing a seat.
- Use an empty demo-user value.
- Attempt to hold an already-held seat.
- Upload an unsupported image format.
- Query an event that does not exist.

Expected result: the application returns a clear error and remains usable.

## 9. Read-only database verification

Request the current read-only password from the host owner through an approved private channel.

Connection details:

```text
Host: 172.30.3.102
Port: 5432
Database: booking
Username: booking_reader
SSL: disabled for this office-LAN demo
```

Connect:

```bash
psql \
  -h 172.30.3.102 \
  -p 5432 \
  -U booking_reader \
  -d booking
```

Read tests:

```sql
SELECT id, name, category, city, venue, starts_at
FROM events
ORDER BY id;

SELECT event_id, seat_label, status, hold_expires_at
FROM show_seats
ORDER BY event_id, seat_label;
```

Permission test:

```sql
UPDATE events SET name = 'Must fail' WHERE id = 1;
```

Expected result: reads succeed and the update is rejected because the account is read-only.

## 10. Deployment persistence test

Before a planned deployment:

1. Record the event count in PostgreSQL.
2. Create a temporary seat hold.
3. Ask the host owner to deploy the next GHCR image version.
4. Reload the application after the deployment.
5. Query the event count again.

Expected result:

- The application becomes healthy after containers restart.
- PostgreSQL event data remains available because it uses a persistent volume.
- A non-expired hold remains authoritative in PostgreSQL.
- Application code changes without resetting the database.

## 11. Browser and device coverage

Where available, test:

- Chrome
- Edge
- Firefox
- Safari
- Desktop and mobile-width browser windows

Report layout issues with browser name, version, screenshot, selected event, and exact steps.

## 12. Defect report format

Send:

```text
Test:
Time:
Shared URL:
Browser or client:
Steps:
Expected result:
Actual result:
HTTP status, if known:
Screenshot or response:
```

Do not include database passwords, authentication tokens, personal information, or confidential images in defect reports.
