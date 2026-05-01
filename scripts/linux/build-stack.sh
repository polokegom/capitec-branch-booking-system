#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/bank-appointment"
BACKEND_DIR="$ROOT_DIR/bank-appointment-backend"

INSTALL=false
SKIP_TESTS=false
BUILD_DOCKER=false
START_STACK=false

usage() {
  cat <<'EOF'
Usage: ./build-stack.sh [options]

Options:
  --install      Run npm ci before building the frontend.
  --skip-tests   Skip frontend Jest tests and backend Maven tests.
  --docker       Build local Docker images after app builds.
  --start        Build and start the full local Docker stack.
  -h, --help     Show this help text.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install)
      INSTALL=true
      shift
      ;;
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --docker)
      BUILD_DOCKER=true
      shift
      ;;
    --start)
      START_STACK=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

run_step() {
  local name="$1"
  shift
  echo
  echo "==> $name"
  "$@"
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command '$command_name' was not found on PATH." >&2
    exit 1
  fi
}

require_command npm
require_command mvn

if [[ "$BUILD_DOCKER" == true || "$START_STACK" == true ]]; then
  require_command docker
fi

if [[ "$INSTALL" == true ]]; then
  run_step "Install frontend dependencies" bash -lc "cd '$FRONTEND_DIR' && npm ci"
fi

if [[ "$SKIP_TESTS" == false ]]; then
  run_step "Run frontend Jest tests" bash -lc "cd '$FRONTEND_DIR' && npm test"
fi

run_step "Build frontend" bash -lc "cd '$FRONTEND_DIR' && npm run build"

if [[ "$SKIP_TESTS" == true ]]; then
  run_step "Build backend" bash -lc "cd '$BACKEND_DIR' && mvn -B clean package -DskipTests"
else
  run_step "Build backend" bash -lc "cd '$BACKEND_DIR' && mvn -B clean verify"
fi

if [[ "$START_STACK" == true ]]; then
  run_step "Build and start Docker stack" bash -lc "cd '$BACKEND_DIR' && docker compose up -d --build"
elif [[ "$BUILD_DOCKER" == true ]]; then
  run_step "Build Docker images" bash -lc "cd '$BACKEND_DIR' && docker compose build"
fi

echo
echo "Stack build completed successfully."
