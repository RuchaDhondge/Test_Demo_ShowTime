# Ticket Booking LAN Demo

Minimal Spring Boot and React demonstration for testing a ticket-booking flow over an office local network.

See [docs/local-network-hosting.md](docs/local-network-hosting.md) for setup, LAN access, API examples, operations, and scale-up guidance.

## GitHub Actions image delivery

Pushes to `main` test the frontend and backend, build both Docker images, and publish commit-SHA tags plus `latest` tags to GHCR:

- `ghcr.io/<owner>/<repo>/backend:<git-sha>`
- `ghcr.io/<owner>/<repo>/web:<git-sha>`

To run published images locally:

```bash
GHCR_BACKEND_IMAGE=ghcr.io/<owner>/<repo>/backend:<git-sha> \
GHCR_WEB_IMAGE=ghcr.io/<owner>/<repo>/web:<git-sha> \
docker compose -f compose.yml -f compose.ghcr.yml pull backend web

GHCR_BACKEND_IMAGE=ghcr.io/<owner>/<repo>/backend:<git-sha> \
GHCR_WEB_IMAGE=ghcr.io/<owner>/<repo>/web:<git-sha> \
docker compose -f compose.yml -f compose.ghcr.yml up -d --no-build
```

The optional `demo-showtime` deployment job uses a self-hosted GitHub Actions runner. Register the runner on the shared dev server with the labels `self-hosted,demo-showtime`, create a `demo-showtime` GitHub environment, and make the repository package permissions available to the runner. Hosted runners cannot directly update Docker Compose on another machine.
