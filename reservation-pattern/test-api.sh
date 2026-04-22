#!/usr/bin/env bash
set -euo pipefail

BASE="http://localhost:8080/api/user-accounts"
PASS=0; FAIL=0; TOTAL=0

ID=$(date +%s)
ALICE="alice-${ID}"
BOB="bob-${ID}"
CAROL="carol-${ID}"
EVE="eve-${ID}"

ASYNC_WAIT="${ASYNC_WAIT:-1}"

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
echo "  Reservation Pattern API Tests (async)"
echo "  Users: ${ALICE}, ${BOB}, ${CAROL}, ${EVE}"
echo "  Async wait: ${ASYNC_WAIT}s (override via ASYNC_WAIT=...)"
echo "══════════════════════════════════════════"

req "Register ${ALICE} → 202 (accepted async)" 202 POST "$BASE/register" \
  -d "{\"username\":\"${ALICE}\",\"email\":\"${ALICE}@example.com\"}"

sleep "$ASYNC_WAIT"
req "GET ${ALICE} → 200, Registered" 200 GET "$BASE/${ALICE}"

req "Register ${BOB} with ${ALICE}'s email → 202 (accepted, denied async)" 202 POST "$BASE/register" \
  -d "{\"username\":\"${BOB}\",\"email\":\"${ALICE}@example.com\"}"

sleep "$ASYNC_WAIT"
req "GET ${BOB} → 200, NotRegistered (email was taken)" 200 GET "$BASE/${BOB}"

req "Register ${ALICE} again → 409 (username taken, sync)" 409 POST "$BASE/register" \
  -d "{\"username\":\"${ALICE}\",\"email\":\"other-${ID}@example.com\"}"

req "Change ${ALICE}'s email → 202 (accepted async)" 202 POST "$BASE/${ALICE}/change-email" \
  -d "{\"newEmail\":\"${ALICE}-new@example.com\"}"

sleep "$ASYNC_WAIT"
req "GET ${ALICE} → 200, Registered with new email" 200 GET "$BASE/${ALICE}"

req "Change ${ALICE}'s email to the same one → 400 (SameEmail, sync)" 400 POST "$BASE/${ALICE}/change-email" \
  -d "{\"newEmail\":\"${ALICE}-new@example.com\"}"

req "Change ${BOB}'s email (NotRegistered) → 409 (AccountDisabled, sync)" 409 POST "$BASE/${BOB}/change-email" \
  -d "{\"newEmail\":\"bob-new-${ID}@example.com\"}"

req "Register ${CAROL} → 202 (accepted async)" 202 POST "$BASE/register" \
  -d "{\"username\":\"${CAROL}\",\"email\":\"${CAROL}@example.com\"}"

sleep "$ASYNC_WAIT"
req "GET ${CAROL} → 200, Registered" 200 GET "$BASE/${CAROL}"

req "Change ${CAROL}'s email to ${ALICE}'s new email → 202 (accepted, reverted async)" 202 POST "$BASE/${CAROL}/change-email" \
  -d "{\"newEmail\":\"${ALICE}-new@example.com\"}"

sleep "$ASYNC_WAIT"
req "GET ${CAROL} → 200, Registered with original email (reverted)" 200 GET "$BASE/${CAROL}"

req "Register ${EVE} with ${ALICE}'s old email → 202 (old email now available)" 202 POST "$BASE/register" \
  -d "{\"username\":\"${EVE}\",\"email\":\"${ALICE}@example.com\"}"

sleep "$ASYNC_WAIT"
req "GET ${EVE} → 200, Registered" 200 GET "$BASE/${EVE}"

req "GET non-existent user → 404" 404 GET "$BASE/nonexistent-user-${ID}"

req "Change non-existent user's email → 404 (NotFound, sync)" 404 POST "$BASE/nonexistent-${ID}/change-email" \
  -d "{\"newEmail\":\"foo-${ID}@example.com\"}"

echo -e "\n══════════════════════════════════════════"
echo "  Results: ${PASS}/${TOTAL} passed, ${FAIL} failed"
echo "══════════════════════════════════════════"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
