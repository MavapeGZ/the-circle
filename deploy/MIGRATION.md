# MIGRATION.md — Move the deployment to another sandbox / VM

Runbook for when the AWS sandbox credit runs out and the uni gives you another one
(or for moving the stack to any other Ubuntu VM).

The stack is **Docker on a VM**, so migrating is basically: provision a new VM
→ restore data → `docker compose up`. ~20-30 min.

---

## What travels by itself (you touch nothing)

| Piece | Why |
|---|---|
| **Frontend (Vercel)** | Still points to `api.the-circle.duckdns.org`, which doesn't change. Zero changes. |
| **DNS (DuckDNS)** | The compose `duckdns` container updates the IP by itself on startup. |
| **TLS certificate (Caddy)** | Let's Encrypt re-issues by itself on the new VM (same domain). |
| **Config** | `docker-compose.prod.yml`, `Dockerfile`s, `Caddyfile`, `init.sql` — all in git. |
| **CORS** | The Vercel URL doesn't change → the `CORS_ALLOWED_ORIGINS` value still holds. |

## What you have to carry over by hand

| Data | Where it lives | Critical |
|---|---|---|
| **`.env`** | Only on the VM (secrets, never in git) | Yes |
| **Postgres volume** | `the-circle_postgres_data` (users, contracts, gamification, notifications) | Yes |
| **OpenSearch volume** | `the-circle_opensearch_data` — **the catalog articles live ONLY here**, `ms-catalog` has no DB | Yes |
| **KYC uploads** | `data/kyc` (bind-mount) | Yes |
| The Caddy cert | `the-circle_caddy_data` | No — re-issued by itself |

> ⚠️ **The backup must be done BEFORE the old sandbox dies.** Once the VM is
> deleted, the Docker volumes are unrecoverable.

---

## 1. Backup (on the OLD VM, before it expires)

Use the `scripts/backup.sh` script (included in the repo). For a consistent copy
of Postgres it generates a logical dump with `pg_dumpall`; for OpenSearch it stops
the container for a few seconds and copies the volume.

```bash
cd /opt/the-circle
./scripts/backup.sh
# -> generates  backup-YYYYMMDD-HHMMSS.tar.gz  in /opt/the-circle
```

Copy it to your laptop (or wherever, outside the sandbox that's going to die):

```bash
# from your laptop:
scp ubuntu@<OLD-VM-IP>:/opt/the-circle/backup-*.tar.gz .
```

---

## 2. Provision the new VM

These steps are **machine-level** (not in git) and have to be repeated. They're the
same ones you did the first time:

1. **Launch an Ubuntu VM** (EC2 `t3.medium` or Lightsail 4 GB) in the new sandbox.
2. **Open inbound ports 80 and 443** in the **Security Group** (22 is already open).
3. **Install Docker**:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && newgrp docker
   ```
4. **`vm.max_map_count` for OpenSearch** (otherwise OpenSearch won't start):
   ```bash
   sudo sysctl -w vm.max_map_count=262144
   echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-opensearch.conf
   ```
5. **2 GB swap** (on a 4 GB box it prevents the kernel from killing a service via OOM
   when the 6 JVMs + OpenSearch start at the same time):
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
6. **Clone the repo** (NEW deploy key — the old one went with the old VM):
   ```bash
   ssh-keygen -t ed25519 -C "the-circle-ec2" -f ~/.ssh/the_circle -N ""
   cat ~/.ssh/the_circle.pub      # add it on GitHub: repo > Settings > Deploy keys
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

## 3. Restore + startup (on the new VM)

Upload the backup `.tar.gz` to the new VM and restore **BEFORE** the first `up`
(so Postgres sees the volume already populated, doesn't re-run `init.sql` and doesn't
clash with Hibernate):

```bash
# upload the backup to the new VM:
#   scp backup-*.tar.gz ubuntu@<NEW-VM-IP>:/opt/the-circle/
cd /opt/the-circle
./scripts/restore.sh backup-YYYYMMDD-HHMMSS.tar.gz
```

The script restores the `.env`, the `data/kyc` folder and fills the Postgres and
OpenSearch volumes. Then, bring up the stack:

```bash
# sequential build to avoid blowing up the RAM (the 1st time it compiles all 6 images)
for s in api-gateway ms-users ms-catalog ms-contracts ms-gamification ms-notifications; do
  docker compose -f docker-compose.prod.yml build "$s"
done
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

---

## 4. Post-migration (checklist)

- [ ] **DuckDNS** → the `duckdns` container updates the new IP by itself as soon as
  it starts. Verify: `docker compose -f docker-compose.prod.yml logs duckdns`
  (should say `successful`). The Vercel frontend and CORS are not touched.
- [ ] **Certificate** → Caddy re-issues the cert by itself. Verify:
  `curl -sI https://api.the-circle.duckdns.org/actuator/health` → `200`.
  (If DNS still points to the old IP, Caddy retries until DuckDNS updates.)
- [ ] **Start/stop scheduler** → the IAM credentials of the old account **don't
  work** on the new sandbox. Create a new IAM user
  (`gh-lightsail-scheduler` / or the EC2 equivalent) with permission
  `ec2:StartInstances`/`ec2:StopInstances`/`ec2:DescribeInstances` over the
  new instance, generate keys and update the **GitHub Secrets**:
  `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, and the new
  instance ID.
- [ ] **AWS Budgets** → reconfigure the alerts ($10/25/40) on the new account.
- [ ] **Smoke test** → registration → OTP in Gmail → KYC → login → create article
  (search) → sign contract (PDF + email). If it passes, migration is green.

---

## Notes

- **Pinned versions**: the volume-copy backup works because Postgres
  (`postgres:15-alpine`) and OpenSearch (`2.11.0`) are pinned in the compose. If
  one day you bump the **major** version of Postgres, do NOT use the volume copy:
  use the logical dump (there's a commented-out `pg_dumpall` in `backup.sh`).
- **Caddy / Let's Encrypt**: the cert is intentionally not backed up; it's re-issued.
  Be careful migrating many times in a row: Let's Encrypt limits to ~5 duplicate
  certificates per domain per week.
- **OpenSearch permissions**: the volume copy preserves UID/GID, so when restoring
  OpenSearch can read its data without touching permissions.
