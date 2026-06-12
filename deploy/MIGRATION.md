# MIGRATION.md — Mover el despliegue a otra sandbox / VM

Runbook para cuando se acabe el crédito de la sandbox de AWS y la uni te dé otra
(o para mover el stack a cualquier otra VM Ubuntu).

El stack es **Docker sobre una VM**, así que migrar es básicamente: provisionar VM
nueva → restaurar datos → `docker compose up`. ~20-30 min.

---

## Qué viaja solo (no tocas nada)

| Pieza | Por qué |
|---|---|
| **Frontend (Vercel)** | Sigue apuntando a `api.the-circle.duckdns.org`, que no cambia. Cero cambios. |
| **DNS (DuckDNS)** | El contenedor `duckdns` del compose actualiza la IP solo al arrancar. |
| **Certificado TLS (Caddy)** | Let's Encrypt se re-emite solo en la VM nueva (mismo dominio). |
| **Config** | `docker-compose.prod.yml`, `Dockerfile`s, `Caddyfile`, `init.sql` — todo en git. |
| **CORS** | La URL de Vercel no cambia → el valor de `CORS_ALLOWED_ORIGINS` sigue valiendo. |

## Qué hay que llevarse a mano

| Dato | Dónde vive | Crítico |
|---|---|---|
| **`.env`** | Solo en la VM (secretos, nunca en git) | Sí |
| **Volumen Postgres** | `the-circle_postgres_data` (usuarios, contratos, gamificación, notificaciones) | Sí |
| **Volumen OpenSearch** | `the-circle_opensearch_data` — **los artículos del catálogo viven SOLO aquí**, `ms-catalog` no tiene BD | Sí |
| **Subidas KYC** | `data/kyc` (bind-mount) | Sí |
| El cert de Caddy | `the-circle_caddy_data` | No — se re-emite solo |

> ⚠️ **El backup hay que hacerlo ANTES de que muera la sandbox vieja.** Una vez
> borrada la VM, los volúmenes Docker son irrecuperables.

---

## 1. Backup (en la VM VIEJA, antes de que caduque)

Usa el script `scripts/backup.sh` (incluido en el repo). Para una copia consistente
para Postgres genera un volcado lógico con `pg_dumpall`; para OpenSearch para el
contenedor unos segundos y copia el volumen.

```bash
cd /opt/the-circle
./scripts/backup.sh
# -> genera  backup-YYYYMMDD-HHMMSS.tar.gz  en /opt/the-circle
```

Cópiatelo a tu portátil (o a donde sea, fuera de la sandbox que va a morir):

```bash
# desde tu portátil:
scp ubuntu@<IP-VM-VIEJA>:/opt/the-circle/backup-*.tar.gz .
```

---

## 2. Provisionar la VM nueva

Estos pasos son de **máquina** (no están en git) y hay que repetirlos. Son los
mismos que hiciste la primera vez:

1. **Lanza una VM Ubuntu** (EC2 `t3.medium` o Lightsail 4 GB) en la sandbox nueva.
2. **Abre los puertos 80 y 443** de entrada en el **Security Group** (el 22 ya viene abierto).
3. **Instala Docker**:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && newgrp docker
   ```
4. **`vm.max_map_count` para OpenSearch** (si no, OpenSearch no arranca):
   ```bash
   sudo sysctl -w vm.max_map_count=262144
   echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-opensearch.conf
   ```
5. **Swap de 2 GB** (en un box de 4 GB evita que el kernel mate un servicio por OOM
   cuando arrancan los 6 JVMs + OpenSearch a la vez):
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
6. **Clona el repo** (deploy key NUEVA — la vieja se fue con la VM vieja):
   ```bash
   ssh-keygen -t ed25519 -C "the-circle-ec2" -f ~/.ssh/the_circle -N ""
   cat ~/.ssh/the_circle.pub      # añádela en GitHub: repo > Settings > Deploy keys
   cat >> ~/.ssh/config <<'EOF'
   Host github.com
       HostName github.com
       User git
       IdentityFile ~/.ssh/the_circle
       IdentitiesOnly yes
   EOF
   chmod 600 ~/.ssh/config
   sudo mkdir -p /opt/the-circle && sudo chown $USER:$USER /opt/the-circle
   git clone git@github.com:MavapeGZ/the-circle.git /opt/the-circle
   cd /opt/the-circle
   ```

---

## 3. Restore + arranque (en la VM nueva)

Sube el `.tar.gz` del backup a la VM nueva y restaura **ANTES** del primer `up`
(así Postgres ve el volumen ya poblado, no re-ejecuta `init.sql` y no choca con
Hibernate):

```bash
# sube el backup a la VM nueva:
#   scp backup-*.tar.gz ubuntu@<IP-VM-NUEVA>:/opt/the-circle/
cd /opt/the-circle
./scripts/restore.sh backup-YYYYMMDD-HHMMSS.tar.gz
```

El script restaura el `.env`, la carpeta `data/kyc` y rellena los volúmenes de
Postgres y OpenSearch. Después, levanta el stack:

```bash
# build secuencial para no reventar la RAM (la 1ª vez compila las 6 imágenes)
for s in api-gateway ms-users ms-catalog ms-contracts ms-gamification ms-notifications; do
  docker compose -f docker-compose.prod.yml build "$s"
done
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

---

## 4. Post-migración (checklist)

- [ ] **DuckDNS** → el contenedor `duckdns` actualiza la IP nueva solo en cuanto
  arranca. Verifica: `docker compose -f docker-compose.prod.yml logs duckdns`
  (debe decir `successful`). El frontend de Vercel y el CORS no se tocan.
- [ ] **Certificado** → Caddy re-emite el cert solo. Verifica:
  `curl -sI https://api.the-circle.duckdns.org/actuator/health` → `200`.
  (Si el DNS aún apunta a la IP vieja, Caddy reintenta hasta que DuckDNS actualiza.)
- [ ] **Scheduler de start/stop** → las credenciales IAM de la cuenta vieja **no
  sirven** en la sandbox nueva. Crea un usuario IAM nuevo
  (`gh-lightsail-scheduler` / o el equivalente EC2) con permiso
  `ec2:StartInstances`/`ec2:StopInstances`/`ec2:DescribeInstances` sobre la
  instancia nueva, genera claves y actualiza los **Secrets de GitHub**:
  `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, y el ID de la
  instancia nueva.
- [ ] **AWS Budgets** → vuelve a configurar las alertas (10/25/40 $) en la cuenta nueva.
- [ ] **Smoke test** → registro → OTP en Gmail → KYC → login → crear artículo
  (búsqueda) → firmar contrato (PDF + email). Si pasa, migración verde.

---

## Notas

- **Versiones fijadas**: el backup por copia de volumen funciona porque Postgres
  (`postgres:15-alpine`) y OpenSearch (`2.11.0`) están pineados en el compose. Si
  algún día subes la versión **mayor** de Postgres, NO uses la copia de volumen:
  usa el volcado lógico (hay un `pg_dumpall` comentado en `backup.sh`).
- **Caddy / Let's Encrypt**: no se backupea el cert a propósito; se re-emite. Ojo
  con migrar muchas veces seguidas: Let's Encrypt limita a ~5 certificados
  duplicados por dominio y semana.
- **OpenSearch permisos**: la copia de volumen preserva UID/GID, así que al
  restaurar OpenSearch puede leer sus datos sin tocar permisos.