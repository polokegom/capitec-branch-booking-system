#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/bank-appointment-backend"

if ! command -v docker >/dev/null 2>&1; then
  echo "Required command 'docker' was not found on PATH." >&2
  exit 1
fi

cd "$BACKEND_DIR"

echo
echo "==> Building images and starting the local stack"
docker compose up -d --build

echo
docker compose ps

echo
echo "Local stack is running."
echo "Frontend:   http://localhost:4200"
echo "Backend:    http://localhost:8080/q/health"
echo "FusionAuth: http://localhost:9011"
echo "MailHog:    http://localhost:8025"
