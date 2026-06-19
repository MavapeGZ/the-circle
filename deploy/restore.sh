#!/usr/bin/env bash
#
# restore.sh — restaura un backup en la VM NUEVA.
# Ejecútalo desde la raíz del repo, ANTES del primer 'docker compose up':
#   ./scripts/restore.sh backup-YYYYMMDD-HHMMSS.tar.gz
#
# Restaurar antes del primer 'up' es importante: así Postgres ve el volumen ya
# poblado, NO re-ejecuta init.sql y no choca con el ddl-auto de Hibernate.
#
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup-YYYYMMDD-HHMMSS.tar.gz>"
  exit 1
fi

PROJECT="the-circle"
BK="$1"

cd "$(dirname "$0")/.."         # raíz del repo

echo "==> Checking that the volumes don't exist yet (clean restore)..."
for v in "${PROJECT}_postgres_data" "${PROJECT}_opensearch_data"; do
  if docker volume inspect "$v" >/dev/null 2>&1; then
    echo "ERROR: volume '$v' already exists. Delete it before restoring:"
    echo "       docker volume rm $v"
    exit 1
  fi
done

echo "==> Unpacking $BK ..."
tar xzf "$BK"
DIR="$(basename "${BK%.tar.gz}")"

echo "==> Restoring .env"
cp "$DIR/.env" .env

echo "==> Restoring KYC"
mkdir -p data
[ -f "$DIR/kyc.tar.gz" ] && tar xzf "$DIR/kyc.tar.gz" -C . || echo "   (no KYC in the backup)"

echo "==> Creating and filling the Postgres volume"
docker volume create "${PROJECT}_postgres_data" >/dev/null
docker run --rm \
  -v "${PROJECT}_postgres_data":/data \
  -v "$PWD/$DIR":/in \
  alpine sh -c "cd /data && tar xzf /in/postgres_data.tar.gz"

echo "==> Creating and filling the OpenSearch volume"
docker volume create "${PROJECT}_opensearch_data" >/dev/null
docker run --rm \
  -v "${PROJECT}_opensearch_data":/data \
  -v "$PWD/$DIR":/in \
  alpine sh -c "cd /data && tar xzf /in/opensearch_data.tar.gz"

rm -rf "$DIR"

echo ""
echo "OK. Data restored. Now bring up the stack:"
echo "  for s in api-gateway ms-users ms-catalog ms-contracts ms-gamification ms-notifications; do \\"
echo "    docker compose -f docker-compose.prod.yml build \"\$s\"; done"
echo "  docker compose -f docker-compose.prod.yml up -d"