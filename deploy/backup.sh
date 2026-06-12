#!/usr/bin/env bash
#
# backup.sh — copia de seguridad del estado del despliegue (en la VM VIEJA).
# Ejecútalo desde la raíz del repo:  ./scripts/backup.sh
# Genera  backup-YYYYMMDD-HHMMSS.tar.gz  con: Postgres, OpenSearch, KYC y .env.
#
set -euo pipefail

COMPOSE="docker compose -f docker-compose.prod.yml"
PROJECT="the-circle"            # prefijo de los volúmenes (nombre del directorio)
STAMP=$(date +%Y%m%d-%H%M%S)
OUT="backup-${STAMP}"

cd "$(dirname "$0")/.."         # raíz del repo
mkdir -p "$OUT"

echo "==> Parando la app para una copia consistente..."
$COMPOSE stop

echo "==> Postgres (copia del volumen)"
docker run --rm \
  -v "${PROJECT}_postgres_data":/data \
  -v "$PWD/$OUT":/out \
  alpine tar czf /out/postgres_data.tar.gz -C /data .
# Alternativa lógica (úsala si vas a cambiar la versión MAYOR de Postgres):
#   $COMPOSE start postgres && sleep 5
#   docker exec thecircle-postgres pg_dumpall -U "${DB_USER:-thecircle}" > "$OUT/postgres_dumpall.sql"

echo "==> OpenSearch (copia del volumen)"
docker run --rm \
  -v "${PROJECT}_opensearch_data":/data \
  -v "$PWD/$OUT":/out \
  alpine tar czf /out/opensearch_data.tar.gz -C /data .

echo "==> KYC uploads"
if [ -d data/kyc ]; then
  tar czf "$OUT/kyc.tar.gz" -C . data/kyc
else
  echo "   (sin uploads de KYC todavía)"
fi

echo "==> .env (secretos)"
cp .env "$OUT/.env"

echo "==> Re-arrancando la app..."
$COMPOSE start

echo "==> Empaquetando..."
tar czf "${OUT}.tar.gz" -C . "$OUT"
rm -rf "$OUT"

echo ""
echo "OK -> ${OUT}.tar.gz"
echo "Cópialo FUERA de la sandbox antes de que caduque, p. ej.:"
echo "  scp ubuntu@<IP>:$(pwd)/${OUT}.tar.gz ."