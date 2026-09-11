#!/usr/bin/env bash
# Starts the prod stack in phases. Bringing all nine containers up at once on a
# 2 GB host makes every JVM allocate simultaneously and the kernel OOM-kills
# whichever loses the race, so the datastores go first and the gateway last.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f docker-compose.prod.yml"

[ -f .env ] || { echo "No .env found. Run ./deploy/gen-env.sh first." >&2; exit 1; }

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

log "Pulling images from GHCR"
$COMPOSE pull

log "Phase 1/3: datastores (waiting for healthchecks)"
$COMPOSE up -d --wait postgres opensearch

log "Phase 2/3: microservices"
$COMPOSE up -d ms-users ms-catalog ms-contracts ms-gamification ms-notifications
sleep 75

log "Phase 3/3: gateway, TLS and dynamic DNS"
$COMPOSE up -d api-gateway caddy duckdns
sleep 20

log "Container status"
$COMPOSE ps

log "Memory"
free -h

log "Per-container usage"
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'

cat <<'NEXT'

If a container is stuck Restarting, it was almost certainly OOM-killed:

  docker inspect <name> --format '{{.State.OOMKilled}}'
  docker compose -f docker-compose.prod.yml logs --tail=80 <name>

Caddy needs a minute to obtain the Let's Encrypt certificate. Then:

  curl -I https://api.the-circle.duckdns.org/actuator/health

NEXT
