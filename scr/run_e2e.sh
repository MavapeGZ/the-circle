#!/usr/bin/env bash
# Linux/macOS counterpart of run_e2e.ps1: brings up the stack, runs the E2E
# suite, and always tears the stack down on exit.
set -euo pipefail

echo "Starting The Circle E2E run..."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

wait_for_ready() {
    local name="$1" uri="$2" timeout="${3:-180}"
    echo "Waiting for $name at $uri ..."
    local deadline=$(( $(date +%s) + timeout ))
    while (( $(date +%s) < deadline )); do
        if curl -sf -o /dev/null --max-time 5 "$uri"; then
            echo "$name is ready"
            return 0
        fi
        sleep 3
    done
    echo "$name did not become ready within ${timeout}s" >&2
    return 1
}

FRONTEND_PID=""
cleanup() {
    if [[ -n "$FRONTEND_PID" ]]; then
        kill "$FRONTEND_PID" 2>/dev/null || true
    fi
    ( cd "$REPO_ROOT" && docker compose down ) || true
}
trap cleanup EXIT

# Load .env from repo root if present
if [[ -f "$REPO_ROOT/.env" ]]; then
    set -a; . "$REPO_ROOT/.env"; set +a
fi

# Backend stack (docker) + frontend (Vite, background)
( cd "$REPO_ROOT" && docker compose up -d )
( cd "$REPO_ROOT/frontend" && npm run dev ) &
FRONTEND_PID=$!

wait_for_ready "Frontend"         "http://localhost:5173"
wait_for_ready "API Gateway"      "http://localhost:8080/actuator/health"
wait_for_ready "ms-users"         "http://localhost:8081/actuator/health"
wait_for_ready "ms-catalog"       "http://localhost:8082/actuator/health"
wait_for_ready "ms-contracts"     "http://localhost:8083/actuator/health"
wait_for_ready "ms-gamification"  "http://localhost:8084/actuator/health"
wait_for_ready "ms-notifications" "http://localhost:8085/actuator/health"

( cd "$REPO_ROOT/e2e" && mvn clean test )

echo "E2E run finished successfully"
