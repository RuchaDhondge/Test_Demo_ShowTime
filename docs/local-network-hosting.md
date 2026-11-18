# Local Network Hosting Runbook

This runbook creates and operates a minimal ticket-booking vertical slice on one office machine and makes the web application available to colleagues on the same local network.

The demo proves:

- React static UI served by Nginx
- Same-origin `/api` proxying from Nginx to Spring Boot
- PostgreSQL as the authoritative booking database
- Atomic multi-seat holds with database row locks
- Redis as an expiring acceleration layer, not the source of booking correctness
- Official AWS Emulator with an emulated S3 event-image workflow
- Flyway-managed schema and sample data
- Spring Boot Actuator and Prometheus metrics
- Grafana with a provisioned Prometheus data source
- Docker Compose deployment
- Access from another office machine without public internet exposure

It intentionally does not include full authentication, payment processing, ticket issuance, email/SMS, or public internet hosting.

## 1. Architecture

```text
Colleague browser
    |
    | http://HOST_LAN_IP:8080
    v
Nginx + React
    |
    | /api/*
    v
Spring Boot
    |-----------------|-----------------|
    v                 v                 v
PostgreSQL          Redis          AWS Emulator
authoritative       acceleration   emulated S3
    |
    v
Actuator metrics <-- Prometheus <-- Grafana
```

Only Nginx port `8080` is exposed to the local network. PostgreSQL, Redis, and the Spring Boot container have no host ports. Prometheus and Grafana are bound to `127.0.0.1` and are therefore visible only on the host machine by default.

## 2. Host and network requirements

### Minimum host

- macOS, Linux, or Windows with Docker Desktop or Docker Engine
- 4 CPU cores recommended
- 8 GB RAM minimum; 12 GB recommended when AWS Emulator and monitoring run together
- Allocate at least 6 GB to Docker when running the complete stack
- 5 GB free disk space for images, build layers, and data
- Office Ethernet or Wi-Fi connection
- Permission to accept inbound TCP traffic on port `8080`

This project was prepared on macOS with:

- Java 17
- Node.js 26 and npm 11 for host-side development checks
- Docker 29 and Docker Compose 5

Java, Node.js, and npm are not required on a colleague's machine. The colleague only needs a current web browser or an HTTP client.

### Required installations

| Installation | Required on host | Required on colleague machine | Purpose |
|---|---:|---:|---|
| Docker Desktop or Docker Engine with Compose | Yes | No | Builds and runs the full stack |
| Git | Recommended | No | Version control and team distribution |
| Java 17+ | Optional | No | Run backend tests outside Docker |
| Node.js 24+ and npm | Optional | No | Lint and build frontend outside Docker |
| Browser | Recommended | Yes | Use the application |
| `curl` or Postman | Optional | Optional | Query and test APIs |
| AWS account | No | No | AWS Emulator runs without an AWS account |
| AWS CLI | Optional | No | Directly inspect emulated AWS APIs |

No paid subscription is required for this LAN demonstration.

## 3. Project layout

```text
DemoBooking/
├── backend/                 Spring Boot API, Flyway, and unit tests
├── frontend/                React application and Nginx configuration
├── aws-emulator/            Official emulator image and internal TLS proxy
├── monitoring/              Prometheus and Grafana provisioning
├── docs/                    This runbook
├── compose.yml              Complete local runtime
└── .env.example             Local configuration template
```

## 4. First-time setup

Open a terminal on the host:

```bash
cd /Users/rucha.dhondge/DemoBooking
cp .env.example .env
```

Edit `.env` and replace the sample PostgreSQL and Grafana passwords. The file is ignored by Git.

Start Docker Desktop and wait until its engine reports that it is running:

```bash
docker info
```

Build and start the stack:

```bash
docker compose up --build -d
```

Inspect status:

```bash
docker compose ps
```

All services should eventually show `running` or `healthy`.

Follow logs if a service does not become healthy:

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 web
docker compose logs --tail=100 aws-emulator
```

## 5. Verify on the host

Open:

- Application: <http://localhost:8080>
- Nginx health: <http://localhost:8080/healthz>
- Prometheus: <http://localhost:9090>
- Grafana: <http://localhost:3000>

Grafana credentials come from `.env`. Its Prometheus data source is provisioned automatically.

Check the API:

```bash
curl -fsS 'http://localhost:8080/api/events?city=Pune'
curl -fsS 'http://localhost:8080/api/events/1/seats'
```

Create a two-minute seat hold:

```bash
curl -i \
  -H 'Content-Type: application/json' \
  -d '{"userId":"host-test@example.com","seatIds":[1,2]}' \
  http://localhost:8080/api/events/1/holds
```

Expected result: HTTP `201 Created`, a hold UUID, and an expiry timestamp.

## 6. AWS Emulator setup

The emulator image follows the official AWS instructions:

<https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html>

Its Dockerfile uses:

- `public.ecr.aws/amazonlinux/amazonlinux:2023-minimal`
- The official `aws-emulator-x86_64.rpm`
- Non-root user ID `1000`
- Port `9297`
- `aws-emulator -b 0.0.0.0` as the entrypoint

The host directory required by the official guide is:

```bash
mkdir -p ~/.aws/emulator
```

Compose mounts that directory at the same absolute path and publishes the emulator only on host loopback:

```text
127.0.0.1:9297
```

Verify it using the generated certificate:

```bash
curl --cacert ~/.aws/emulator/9297/ca.pem \
  https://127.0.0.1:9297/__admin/health
```

Expected response:

```text
ok
```

### Demo-specific runtime settings

The official image build is unchanged. Compose adds:

- A 2 GB emulator memory limit
- Telemetry disabled
- Request-signature checks disabled
- An internal Nginx proxy from HTTP to the emulator's self-signed HTTPS endpoint
- Localhost-only host port publishing

Authentication is disabled only because the emulator and proxy are isolated inside the private Compose network and the host port is restricted to `127.0.0.1`. Enable emulator authentication and mount its generated credentials into the backend before using this pattern in a less controlled environment.

The proxy avoids replacing the Java runtime's normal trust store. Replacing it with the emulator trust store would prevent the same JVM from trusting normal HTTPS services unless the trust stores were merged.

### Emulated S3 workflow

At backend startup, the application creates an emulated S3 bucket named `booking-demo-assets`.

Upload an event image:

```bash
curl -F 'file=@event.png;type=image/png' \
  http://localhost:8080/api/events/1/image
```

Download it:

```bash
curl -o downloaded-event.png \
  http://localhost:8080/api/events/1/image
```

The UI also provides an image upload control for the selected event.

AWS Emulator keeps emulated resources in memory. Images and buckets are lost when the emulator stops or restarts. This is expected for local tests; PostgreSQL data remains in its Docker volume.

### AWS Emulator limitations

- It is for development and testing, not production.
- It does not evaluate IAM policies like real AWS.
- Service and operation fidelity varies.
- It represents one logical account and region per emulator.
- It does not test VPCs, security groups, public DNS, real availability, performance, quotas, or AWS cost.
- Validate the final S3 workflow once against a disposable real AWS environment before release.

## 7. Make the application reachable on the office LAN

Find the host's current LAN address on macOS:

```bash
ipconfig getifaddr en0 || ipconfig getifaddr en1
```

At preparation time the detected address was `172.30.3.102`. DHCP can change it, so verify it before every demonstration.

The colleague should open:

```text
http://HOST_LAN_IP:8080
```

Example:

```text
http://172.30.3.102:8080
```

From a colleague's terminal:

```bash
curl -i http://HOST_LAN_IP:8080/healthz
curl -fsS 'http://HOST_LAN_IP:8080/api/events?city=Pune'
```

### macOS firewall

If local access works but the colleague cannot connect:

1. Open **System Settings > Network > Firewall**.
2. Ensure Docker Desktop is allowed to accept incoming connections.
3. Keep the firewall enabled; do not disable it as a workaround.
4. Ask office IT whether the Wi-Fi uses client isolation or blocks peer-to-peer traffic.
5. Confirm both machines are on the same routable office network or VLAN.

Test the port from the colleague machine:

```bash
nc -vz HOST_LAN_IP 8080
```

Do not configure router port forwarding. This demo is intended only for the trusted office LAN.

## 8. Demonstrate booking concurrency

1. Open the application from two machines.
2. Select the same event and seat in both browsers.
3. Submit the hold at approximately the same time.
4. One request should receive `201 Created`.
5. The other request should receive `409 Conflict`.
6. Wait two minutes and refresh. The seat becomes available after expiry processing.

The backend locks requested seat rows in a consistent ID order inside one PostgreSQL transaction. Every selected seat is acquired together or the complete request fails. Redis failure does not permit double booking because PostgreSQL remains authoritative.

## 9. Development checks

Backend tests:

```bash
cd backend
./mvnw test
```

Frontend lint and build:

```bash
cd frontend
npm ci
npm run lint
npm run build
```

Validate Compose:

```bash
docker compose config --quiet
```

## 10. Operations

Start:

```bash
docker compose up -d
```

Stop while preserving data:

```bash
docker compose down
```

Rebuild after code changes:

```bash
docker compose up --build -d
```

View all recent logs:

```bash
docker compose logs --tail=100
```

Remove the environment and all local data only when a complete reset is intended:

```bash
docker compose down --volumes
```

### Database backup

```bash
docker compose exec -T postgres \
  pg_dump -U booking -d booking -Fc > booking-backup.dump
```

Use the values from `.env` if the database name or user was changed.

### Database restore

Restore into a clean database and verify the application before depending on a backup:

```bash
cat booking-backup.dump | docker compose exec -T postgres \
  pg_restore -U booking -d booking --clean --if-exists
```

For a real shared environment, automate daily backups, encrypt them, store them outside the host, and test restoration regularly.

## 11. Security boundaries

- This demo has no authentication. Anyone who can reach port `8080` can query it and create holds.
- Use only synthetic event and user data.
- Keep PostgreSQL, Redis, Actuator, Prometheus, and Grafana off the LAN unless a specific protected access requirement is approved.
- Keep the AWS Emulator endpoint on `127.0.0.1`; colleagues should access emulated S3 only through the application API.
- Do not expose this configuration directly to the internet.
- Do not commit `.env`, credentials, personal information, or payment data.
- Use HTTPS, authentication, rate limiting, CSRF controls, and managed secret storage before broader deployment.
- Restrict host sleep during a demonstration, but restore normal power settings afterward.

## 12. Troubleshooting

### Docker daemon is unavailable

Symptom:

```text
Cannot connect to the Docker daemon
```

Start Docker Desktop, wait for it to finish initialization, and run `docker info`.

### Port 8080 is already in use

Change `WEB_PORT` in `.env`, for example:

```text
WEB_PORT=8088
```

Restart Compose and use the new port in the colleague URL.

### Host works but colleague cannot connect

- Recheck the current host LAN address.
- Verify `docker compose ps` shows a published port such as `0.0.0.0:8080->80/tcp`.
- Check the macOS firewall rule for Docker.
- Check office Wi-Fi client isolation and VLAN routing with IT.
- Test `nc -vz HOST_LAN_IP 8080` from the colleague machine.

### Events do not load

```bash
docker compose ps
docker compose logs --tail=100 backend postgres
curl -i http://localhost:8080/api/events?city=Pune
```

### Grafana has no data

Open Prometheus, query `up`, and confirm `booking-backend` equals `1`. Then check:

```bash
docker compose logs --tail=100 prometheus grafana
```

### AWS Emulator is unhealthy

Check:

```bash
docker compose logs --tail=100 aws-emulator
ls -la ~/.aws/emulator
```

The emulator container runs as user ID `1000` and must be able to write under `~/.aws/emulator`. Use the host bind mount shown in the official Docker guide rather than a root-owned Docker volume.

Confirm health:

```bash
curl --cacert ~/.aws/emulator/9297/ca.pem \
  https://127.0.0.1:9297/__admin/health
```

### Event-image upload fails

```bash
docker compose logs --tail=100 backend aws-emulator aws-emulator-proxy
curl -i http://localhost:8080/api/events/1/image
```

Uploads accept JPEG, PNG, or WebP files up to 2 MB.

## 13. Scale-up path for the actual backend

Keep the modular monolith and add features in controlled stages:

1. **Identity:** Spring Security, verified email/mobile ownership, password reset, session revocation, authorization, and audit events.
2. **Catalog:** cities, categories, artists, movies, venues, shows, pricing tiers, and PostgreSQL full-text/trigram search.
3. **Booking:** idempotency keys, durable booking records, hold ownership checks, cancellation, booking confirmation, and ticket generation.
4. **Payment:** provider abstraction, Razorpay test mode, signed and idempotent webhooks, retries, reconciliation, and an internal failure simulator.
5. **Telemetry:** low-cardinality technical metrics in Prometheus; durable business events in PostgreSQL/outbox or a product analytics system.
6. **Notifications:** transactional outbox, retryable email/SMS workers, and delivery status.
7. **Testing:** Testcontainers PostgreSQL/Redis, REST Assured, Playwright, k6 concurrency tests, dependency scanning, secret scanning, and container scanning.
8. **Delivery:** GitHub Actions or Woodpecker CI, immutable images in GHCR/ECR, manual environment approval, image-digest deployment, smoke tests, and rollback.
9. **Hosting:** first move the same Compose stack to a controlled VM, then separate managed PostgreSQL, backups, TLS, and monitoring when reliability requirements justify it.

Before implementation at project scale, write measurable requirements for expected concurrent users, shows, seats per show, hold duration, booking throughput, recovery time, recovery point, retention, and availability.

## 14. Production differences

This Compose environment is appropriate for learning, development, and an internal demonstration. It is not a production architecture because it uses one host, local Docker volumes, one database instance, no authentication, no HTTPS, and no automated off-host backup.

For production:

- Build images in CI and pull immutable image digests from a registry.
- Use TLS and a controlled DNS name.
- Use managed or independently backed-up PostgreSQL.
- Use a real secret manager.
- Run database migrations as a controlled release step.
- Add authenticated observability access and centralized logs.
- Define health, alerting, backup, restore, rollback, capacity, and incident runbooks.
- Perform security review and load/concurrency testing before release.
