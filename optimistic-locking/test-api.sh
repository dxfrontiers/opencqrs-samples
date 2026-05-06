#!/usr/bin/env bash
set -euo pipefail

BASE="http://localhost:8080/api/books"
PASS=0; FAIL=0; TOTAL=0

req() {
  local label="$1" expect="$2" method="$3" url="$4"; shift 4
  TOTAL=$((TOTAL + 1))

  echo -e "\n━━━ Test ${TOTAL}: ${label} ━━━"
  echo -e "  ${method} ${url}"

  local tmpfile; tmpfile=$(mktemp)
  local http_code
  http_code=$(curl -s -o "$tmpfile" -w "%{http_code}" -X "$method" "$url" \
    -H "Content-Type: application/json" "$@")

  local body; body=$(cat "$tmpfile"); rm -f "$tmpfile"
  LAST_BODY="$body"

  if [ -n "$body" ]; then
    echo "$body" | jq . 2>/dev/null || echo "$body"
  fi

  if [ "$http_code" = "$expect" ]; then
    echo -e "  ✓ HTTP ${http_code} (expected ${expect})"
    PASS=$((PASS + 1))
  else
    echo -e "  ✗ HTTP ${http_code} (expected ${expect})"
    FAIL=$((FAIL + 1))
  fi
}

echo "══════════════════════════════════════════"
echo "  Optimistic Locking Demo – API Tests"
echo "══════════════════════════════════════════"

req "Purchase a book" 201 POST "$BASE" \
  -d '{"isbn":"978-0","title":"Lord of the Rings","authors":["J.R.R. Tolkien"]}'

sleep 1

req "GET from projection" 200 GET "$BASE/978-0/projected"
V1=$(echo "$LAST_BODY" | jq -r '.version')
echo "  → version: ${V1}"

req "Update with correct version" 204 PUT "$BASE/978-0" \
  -d "{\"title\":\"The Lord of the Rings\",\"authors\":[\"J.R.R. Tolkien\"],\"version\":\"${V1}\"}"

sleep 1

req "GET from projection after update" 200 GET "$BASE/978-0/projected"
V2=$(echo "$LAST_BODY" | jq -r '.version')
echo "  → version: ${V2}"

req "Update with stale version (V1) → 412" 412 PUT "$BASE/978-0" \
  -d "{\"title\":\"Stale attempt\",\"authors\":[\"Nobody\"],\"version\":\"${V1}\"}"

req "Update with current version (V2)" 204 PUT "$BASE/978-0" \
  -d "{\"title\":\"LOTR Revised\",\"authors\":[\"Tolkien\"],\"version\":\"${V2}\"}"

req "PUT nonexistent book → 404" 404 PUT "$BASE/nonexistent" \
  -d '{"title":"Ghost","authors":["Nobody"],"version":"fake"}'

req "POST duplicate isbn → 409" 409 POST "$BASE" \
  -d '{"isbn":"978-0","title":"Duplicate","authors":["X"]}'

echo -e "\n══════════════════════════════════════════"
echo "  Results: ${PASS}/${TOTAL} passed, ${FAIL} failed"
echo "══════════════════════════════════════════"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
