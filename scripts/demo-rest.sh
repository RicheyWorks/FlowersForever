#!/usr/bin/env bash
# FlowersForever REST smoke (app on :8080). Optional: USER=farm PASS=kitsap ./scripts/demo-rest.sh
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080}"
AUTH=()
if [[ -n "${USER:-}" && -n "${PASS:-}" ]]; then
  AUTH=(-u "${USER}:${PASS}")
  echo "Using Basic auth as ${USER}"
fi

hit() {
  echo ""
  echo "=== GET $1 ==="
  curl -sS "${AUTH[@]}" "${BASE}$1" | head -c 2000 || true
  echo ""
}

echo "FlowersForever demo REST smoke → ${BASE}"
hit /actuator/health
hit /api/auth/me
hit /api/dashboard
hit /api/harvest/week
hit /api/orders/week
hit /api/connectors
hit "/api/connectors/history?limit=10"
hit "/api/irrigation/advice?live=false"
curl -sS "${AUTH[@]}" -o weekly-demo.pdf "${BASE}/api/reports/weekly.pdf" && echo "Wrote weekly-demo.pdf"
echo "Done."
