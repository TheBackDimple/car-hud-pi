#!/bin/bash
# Run FastAPI from the project root so `import backend` works.
# Usage:  ./scripts/dev-backend.sh
# Optional:  ./scripts/dev-backend.sh --reload
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

if [ -f "$PROJECT_DIR/venv/bin/activate" ]; then
  # shellcheck source=/dev/null
  . "$PROJECT_DIR/venv/bin/activate"
fi

exec python3 -m uvicorn backend.main:app --host 0.0.0.0 --port 8000 "$@"
