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
hit /api/briefing
hit /api/closeout
hit "/api/inventory/low-stock?threshold=10"
hit /api/harvest/week
hit /api/orders/week
hit /api/connectors
hit "/api/connectors/history?limit=10"
hit "/api/connectors/history/report?limit=20"
hit "/api/irrigation/advice?live=false"
hit /api/market-day
hit "/api/harvest/beds?week=true"
hit "/api/harvest/log?week=true"
curl -sS "${AUTH[@]}" -o bed-production-demo.pdf "${BASE}/api/harvest/beds/report.pdf?week=true" && echo "Wrote bed-production-demo.pdf"
curl -sS "${AUTH[@]}" -o harvest-log-demo.pdf "${BASE}/api/harvest/log/report.pdf?week=true" && echo "Wrote harvest-log-demo.pdf"
curl -sS "${AUTH[@]}" -o audit-history-demo.pdf "${BASE}/api/connectors/history/report.pdf?limit=50" \
  && echo "Wrote audit-history-demo.pdf"
curl -sS "${AUTH[@]}" -o market-pack-demo.pdf "${BASE}/api/market-day/packing.pdf" && echo "Wrote market-pack-demo.pdf"
curl -sS "${AUTH[@]}" -o weekly-demo.pdf "${BASE}/api/reports/weekly.pdf" && echo "Wrote weekly-demo.pdf"
curl -sS "${AUTH[@]}" -o morning-briefing-demo.pdf "${BASE}/api/briefing/report.pdf" && echo "Wrote morning-briefing-demo.pdf"
curl -sS "${AUTH[@]}" -o day-closeout-demo.pdf "${BASE}/api/closeout/report.pdf" && echo "Wrote day-closeout-demo.pdf"
curl -sS "${AUTH[@]}" -o low-stock-reorder-demo.pdf "${BASE}/api/inventory/low-stock/report.pdf?threshold=10" \
  && echo "Wrote low-stock-reorder-demo.pdf"
# First order id when present
OID=$(curl -sS "${AUTH[@]}" "${BASE}/api/orders" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n1)
if [[ -n "${OID}" ]]; then
  curl -sS "${AUTH[@]}" -o "invoice-order-${OID}-demo.pdf" "${BASE}/api/orders/${OID}/invoice.pdf" \
    && echo "Wrote invoice-order-${OID}-demo.pdf"
else
  echo "(no orders — skip invoice PDF; use --spring.profiles.active=demo)"
fi
CID=$(curl -sS "${AUTH[@]}" "${BASE}/api/customers" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n1)
if [[ -n "${CID}" ]]; then
  curl -sS "${AUTH[@]}" -o "statement-customer-${CID}-demo.pdf" "${BASE}/api/customers/${CID}/statement.pdf" \
    && echo "Wrote statement-customer-${CID}-demo.pdf"
else
  echo "(no customers — skip statement PDF; use --spring.profiles.active=demo)"
fi
echo "Done."
