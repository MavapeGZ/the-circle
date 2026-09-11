#!/usr/bin/env bash
# Prepares a fresh Amazon Linux 2023 (arm64) host to run the prod stack.
# Run once, as ec2-user, then log out and back in so the docker group applies.
#
#   curl -fsSL https://raw.githubusercontent.com/MavapeGZ/the-circle/main/deploy/ec2-bootstrap.sh | bash
set -euo pipefail

REPO_URL="https://github.com/MavapeGZ/the-circle.git"
APP_DIR="$HOME/the-circle"
SWAP_SIZE="4G"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

if [ "$(id -u)" -eq 0 ]; then
  echo "Run as ec2-user, not root." >&2
  exit 1
fi

log "Installing Docker and git"
sudo dnf update -y -q
sudo dnf install -y -q docker git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

log "Installing the Docker Compose plugin (arm64)"
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL \
  https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 2 GB of RAM is not enough headroom for six JVMs plus OpenSearch during
# startup, when every service allocates at once. Swap absorbs that spike.
if ! swapon --show | grep -q /swapfile; then
  log "Creating a ${SWAP_SIZE} swapfile"
  sudo fallocate -l "$SWAP_SIZE" /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
else
  log "Swapfile already present, skipping"
fi

# Keep the kernel off swap until it genuinely has to; swapping a JVM heap on
# EBS is slow enough to trip healthchecks.
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf >/dev/null

log "Raising vm.max_map_count for OpenSearch"
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-opensearch.conf >/dev/null
sudo sysctl --system >/dev/null

log "Cloning the repository"
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" pull --ff-only
else
  git clone --depth 1 "$REPO_URL" "$APP_DIR"
fi
mkdir -p "$APP_DIR/data/kyc" "$APP_DIR/data/avatars"

log "Done"
cat <<'NEXT'

Bootstrap finished. Now:

  1. Log out and back in    -> exit, then ssh in again (applies the docker group)
  2. cd ~/the-circle
  3. ./deploy/gen-env.sh    -> generates .env with fresh secrets
  4. ./deploy/up.sh         -> starts the stack in phases

NEXT
