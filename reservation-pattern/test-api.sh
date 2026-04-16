#!/usr/bin/env bash
set -euo pipefail

BASE="http://localhost:8080/api/user-accounts"
PASS=0; FAIL=0; TOTAL=0

ID=$(date +%s)
ALICE="alice-${ID}"
BOB="bob-${ID}"
CAROL="carol-${ID}"
EVE="eve-${ID}"

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
echo "  Reservation Pattern API Tests"
echo "  Users: ${ALICE}, ${BOB}, ${CAROL}, ${EVE}"
echo "══════════════════════════════════════════"

req "Register ${ALICE}" 201 POST "$BASE/register" \
  -d "{\"username\":\"${ALICE}\",\"email\":\"${ALICE}@example.com\"}"

req "GET ${ALICE} → Registered" 200 GET "$BASE/${ALICE}"

req "Register ${BOB} with ${ALICE}'s email → 422 (denied)" 422 POST "$BASE/register" \
  -d "{\"username\":\"${BOB}\",\"email\":\"${ALICE}@example.com\"}"

req "GET ${BOB} → NotRegistered (email taken)" 200 GET "$BASE/${BOB}"

req "Change ${ALICE}'s email → 204" 204 POST "$BASE/${ALICE}/change-email" \
  -d "{\"newEmail\":\"${ALICE}-new@example.com\"}"

req "GET ${ALICE} → new email, Registered" 200 GET "$BASE/${ALICE}"

req "Register ${CAROL}" 201 POST "$BASE/register" \
  -d "{\"username\":\"${CAROL}\",\"email\":\"${CAROL}@example.com\"}"

req "GET ${CAROL} → Registered" 200 GET "$BASE/${CAROL}"

req "Change ${CAROL}'s email to ${ALICE}'s email → 422 (denied, reverted)" 422 POST "$BASE/${CAROL}/change-email" \
  -d "{\"newEmail\":\"${ALICE}-new@example.com\"}"

req "GET ${CAROL} → Registered, original email (reverted)" 200 GET "$BASE/${CAROL}"

req "Register ${EVE} with ${ALICE}'s old email → 201 (old email now available)" 201 POST "$BASE/register" \
  -d "{\"username\":\"${EVE}\",\"email\":\"${ALICE}@example.com\"}"

req "GET ${EVE} → Registered (old email now available)" 200 GET "$BASE/${EVE}"

req "GET non-existent user → 404" 404 GET "$BASE/nonexistent-user-${ID}"

echo -e "\n══════════════════════════════════════════"
echo "  Results: ${PASS}/${TOTAL} passed, ${FAIL} failed"
echo "══════════════════════════════════════════"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
