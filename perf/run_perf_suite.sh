#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-http://localhost:8000}"
API_PREFIX="${LOCUST_API_PREFIX:-/api/v1}"
USER_ID="${LOCUST_USER_ID:-perf_user_001}"
STAGE="${STAGE:-all}"
RESULT_DIR="${RESULT_DIR:-perf/results/$(date +%Y%m%d_%H%M%S)}"
SEED_COUNT="${SEED_COUNT:-20}"
DELETE_SEED_COUNT="${DELETE_SEED_COUNT:-1000}"

SMOKE_USERS="${SMOKE_USERS:-1}"
SMOKE_SPAWN_RATE="${SMOKE_SPAWN_RATE:-1}"
SMOKE_RUN_TIME="${SMOKE_RUN_TIME:-30s}"

BASELINE_USERS="${BASELINE_USERS:-5}"
BASELINE_SPAWN_RATE="${BASELINE_SPAWN_RATE:-1}"
BASELINE_RUN_TIME="${BASELINE_RUN_TIME:-2m}"

TARGET_USERS="${TARGET_USERS:-20}"
TARGET_SPAWN_RATE="${TARGET_SPAWN_RATE:-5}"
TARGET_RUN_TIME="${TARGET_RUN_TIME:-5m}"

WAIT_MIN="${LOCUST_WAIT_MIN:-0.1}"
WAIT_MAX="${LOCUST_WAIT_MAX:-1}"

mkdir -p "$RESULT_DIR"

log() {
  printf '[perf-suite] %s\n' "$*" | tee -a "$RESULT_DIR/suite.log"
}

usage() {
  cat <<EOF
Usage:
  bash perf/run_perf_suite.sh [stage]

Stages:
  all            Run reachability, smoke, baseline, and target.
  reachability   Curl each endpoint once, including one mixed workflow.
  smoke          Run all single-endpoint Locust tests and mixed Locust test.
  baseline       Run all single-endpoint Locust tests and mixed Locust test.
  target         Run all single-endpoint Locust tests and mixed Locust test.

Environment:
  HOST=http://localhost:8000
  LOCUST_USER_ID=perf_user_001
  RESULT_DIR=perf/results/<timestamp>
  SEED_COUNT=20
  DELETE_SEED_COUNT=50
  SMOKE_USERS=1 SMOKE_SPAWN_RATE=1 SMOKE_RUN_TIME=30s
  BASELINE_USERS=5 BASELINE_SPAWN_RATE=1 BASELINE_RUN_TIME=2m
  TARGET_USERS=20 TARGET_SPAWN_RATE=5 TARGET_RUN_TIME=5m
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if [ "${1:-}" != "" ]; then
  STAGE="$1"
fi

request_id() {
  printf 'perf-%s-%s' "$(date +%s)" "$RANDOM"
}

curl_json() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local output="$4"
  local status_file="$output.status"

  if [ -n "$body" ]; then
    curl -sS -X "$method" "$HOST$API_PREFIX$path" \
      -H 'Content-Type: application/json' \
      -H "X-WX-OPENID: $USER_ID" \
      -H "X-Request-Id: $(request_id)" \
      -d "$body" \
      -w '%{http_code}' \
      -o "$output" >"$status_file"
  else
    curl -sS -X "$method" "$HOST$API_PREFIX$path" \
      -H "X-WX-OPENID: $USER_ID" \
      -H "X-Request-Id: $(request_id)" \
      -w '%{http_code}' \
      -o "$output" >"$status_file"
  fi
}

payload() {
  local suffix="$1"
  local meal_type="${2:-dinner}"
  cat <<EOF
{
  "name": "压测菜品-$suffix",
  "file_id": "production/dish/$USER_ID/perf_$suffix.jpg",
  "note": "perf suite seed $suffix",
  "date": "2026-06-05",
  "meal_type": "$meal_type"
}
EOF
}

extract_json_field() {
  local file="$1"
  local field_path="$2"
  python3 - "$file" "$field_path" <<'PY'
import json
import sys

path = sys.argv[2].split(".")
with open(sys.argv[1], encoding="utf-8") as handle:
    data = json.load(handle)
for key in path:
    data = data[key]
print(data)
PY
}

assert_status_2xx() {
  local label="$1"
  local status_file="$2"
  local status
  status="$(cat "$status_file")"
  if [[ "$status" != 2* ]]; then
    log "$label failed with HTTP $status. Response: $(cat "${status_file%.status}")"
    return 1
  fi
  log "$label reachable with HTTP $status"
}

create_dish() {
  local suffix="$1"
  local output="$2"
  curl_json POST "/dishes" "$(payload "$suffix")" "$output"
  assert_status_2xx "POST /dishes seed" "$output.status" >/dev/null
  extract_json_field "$output" "data.id"
}

seed_ids() {
  local count="$1"
  local file="$2"
  : >"$file"
  log "Creating $count seed dishes into $file"
  for i in $(seq 1 "$count"); do
    create_dish "seed_${i}_$RANDOM" "$RESULT_DIR/seed_$i.json" >>"$file"
  done
}

run_reachability() {
  local dir="$RESULT_DIR/reachability"
  mkdir -p "$dir"
  log "Running reachability checks"

  local dish_id
  dish_id="$(create_dish "reachability_$RANDOM" "$dir/post_dishes.json")"
  assert_status_2xx "POST /dishes" "$dir/post_dishes.json.status"

  curl_json GET "/dishes?page_no=1&page_size=20" "" "$dir/get_dishes_list.json"
  assert_status_2xx "GET /dishes" "$dir/get_dishes_list.json.status"

  curl_json GET "/dishes/$dish_id" "" "$dir/get_dish_detail.json"
  assert_status_2xx "GET /dishes/{dish_id}" "$dir/get_dish_detail.json.status"

  curl_json PUT "/dishes/$dish_id" "$(payload "reachability_update_$RANDOM" lunch)" "$dir/put_dish.json"
  assert_status_2xx "PUT /dishes/{dish_id}" "$dir/put_dish.json.status"

  curl_json GET "/recommendations/today-meals?meal_type=lunch&size=3&refresh_token=reachability_$RANDOM" "" "$dir/get_today_meals.json"
  assert_status_2xx "GET /recommendations/today-meals" "$dir/get_today_meals.json.status"

  curl_json DELETE "/dishes/$dish_id" "" "$dir/delete_dish.json"
  assert_status_2xx "DELETE /dishes/{dish_id}" "$dir/delete_dish.json.status"

  local mixed_id
  mixed_id="$(create_dish "mixed_$RANDOM" "$dir/mixed_post.json")"
  curl_json GET "/dishes?page_no=1&page_size=20" "" "$dir/mixed_list.json"
  curl_json GET "/dishes/$mixed_id" "" "$dir/mixed_detail.json"
  curl_json PUT "/dishes/$mixed_id" "$(payload "mixed_update_$RANDOM" breakfast)" "$dir/mixed_put.json"
  curl_json GET "/recommendations/today-meals?meal_type=breakfast&size=3&refresh_token=mixed_$RANDOM" "" "$dir/mixed_recommendation.json"
  curl_json DELETE "/dishes/$mixed_id" "" "$dir/mixed_delete.json"
  assert_status_2xx "mixed POST /dishes" "$dir/mixed_post.json.status"
  assert_status_2xx "mixed GET /dishes" "$dir/mixed_list.json.status"
  assert_status_2xx "mixed GET /dishes/{dish_id}" "$dir/mixed_detail.json.status"
  assert_status_2xx "mixed PUT /dishes/{dish_id}" "$dir/mixed_put.json.status"
  assert_status_2xx "mixed GET /recommendations/today-meals" "$dir/mixed_recommendation.json.status"
  assert_status_2xx "mixed DELETE /dishes/{dish_id}" "$dir/mixed_delete.json.status"
}

locust_files=(
  "post_dish:perf/locust_post_dish.py"
  "get_dishes_list:perf/locust_get_dishes_list.py"
  "get_dish_detail:perf/locust_get_dish_detail.py"
  "put_dish:perf/locust_put_dish.py"
  "delete_dish:perf/locust_delete_dish.py"
  "get_today_meals:perf/locust_get_today_meals.py"
  "mixed:perf/locust_mixed_dish_behaviors.py"
)

stage_config() {
  case "$1" in
    smoke)
      printf '%s %s %s' "$SMOKE_USERS" "$SMOKE_SPAWN_RATE" "$SMOKE_RUN_TIME"
      ;;
    baseline)
      printf '%s %s %s' "$BASELINE_USERS" "$BASELINE_SPAWN_RATE" "$BASELINE_RUN_TIME"
      ;;
    target)
      printf '%s %s %s' "$TARGET_USERS" "$TARGET_SPAWN_RATE" "$TARGET_RUN_TIME"
      ;;
    *)
      log "Unknown Locust stage: $1"
      exit 1
      ;;
  esac
}

run_locust_stage() {
  local stage="$1"
  local users spawn_rate run_time
  read -r users spawn_rate run_time <<<"$(stage_config "$stage")"

  local stage_dir="$RESULT_DIR/$stage"
  mkdir -p "$stage_dir"

  local shared_ids="$stage_dir/shared_dish_ids.txt"
  seed_ids "$SEED_COUNT" "$shared_ids"

  log "Running $stage Locust tests: users=$users spawn_rate=$spawn_rate run_time=$run_time"
  for entry in "${locust_files[@]}"; do
    local name="${entry%%:*}"
    local file="${entry#*:}"
    local run_dir="$stage_dir/$name"
    mkdir -p "$run_dir"

    local id_file="$shared_ids"
    if [ "$name" = "delete_dish" ]; then
      id_file="$run_dir/delete_dish_ids.txt"
      seed_ids "$DELETE_SEED_COUNT" "$id_file"
    fi

    log "Running $stage/$name with $file"
    LOCUST_USER_ID="$USER_ID" \
    LOCUST_API_PREFIX="$API_PREFIX" \
    LOCUST_DISH_ID_FILE="$id_file" \
    LOCUST_WAIT_MIN="$WAIT_MIN" \
    LOCUST_WAIT_MAX="$WAIT_MAX" \
      uvx locust \
        -f "$file" \
        --host "$HOST" \
        --headless \
        --users "$users" \
        --spawn-rate "$spawn_rate" \
        --run-time "$run_time" \
        --csv "$run_dir/stats" \
        --html "$run_dir/report.html" \
        --logfile "$run_dir/locust.log" \
        --loglevel INFO \
        2>&1 | tee "$run_dir/console.log"
  done
}

case "$STAGE" in
  all)
    run_reachability
    run_locust_stage smoke
    run_locust_stage baseline
    run_locust_stage target
    ;;
  reachability)
    run_reachability
    ;;
  smoke|baseline|target)
    run_locust_stage "$STAGE"
    ;;
  *)
    usage
    exit 1
    ;;
esac

log "Finished. Results are under $RESULT_DIR"
