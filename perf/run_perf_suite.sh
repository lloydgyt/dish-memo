#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-http://localhost:8000}"
API_PREFIX="${LOCUST_API_PREFIX:-/api/v1}"
USER_ID="${LOCUST_USER_ID:-perf_user_001}"
STAGE="${1:-${STAGE:-smoke}}"
RESULT_DIR="${RESULT_DIR:-perf/results/$(date +%Y%m%d_%H%M%S)}"
DATASET_COUNT="${DATASET_COUNT:-1000}"

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

log() {
  mkdir -p "$RESULT_DIR"
  printf '[perf-suite] %s\n' "$*" | tee -a "$RESULT_DIR/suite.log"
}

usage() {
  cat <<EOF
Usage:
  HOST=http://localhost:8000 bash perf/run_perf_suite.sh <stage>

Stages:
  reachability   Call each endpoint once and print only the final reachability result.
  smoke          Run fixed-order Locust smoke tests.
  baseline       Run fixed-order Locust baseline tests.
  target         Run fixed-order Locust target/performance tests.
  all            Run reachability, smoke, baseline, and target.

Main options:
  HOST=http://localhost:8000
  LOCUST_USER_ID=perf_user_001
  LOCUST_API_PREFIX=/api/v1
  RESULT_DIR=perf/results/<timestamp>
  DATASET_COUNT=1000

Stage options:
  SMOKE_USERS=1 SMOKE_SPAWN_RATE=1 SMOKE_RUN_TIME=30s
  BASELINE_USERS=5 BASELINE_SPAWN_RATE=1 BASELINE_RUN_TIME=2m
  TARGET_USERS=20 TARGET_SPAWN_RATE=5 TARGET_RUN_TIME=5m
EOF
}

if [ "$STAGE" = "-h" ] || [ "$STAGE" = "--help" ]; then
  usage
  exit 0
fi

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

run_reachability() {
  python3 perf/reachability_check.py \
    --host "$HOST" \
    --api-prefix "$API_PREFIX" \
    --user-id "$USER_ID"
}

locust_runs=(
  "post_dish:perf/locust_post_dish.py"
  "get_dishes_list:perf/locust_get_dishes_list.py"
  "get_dish_detail:perf/locust_get_dish_detail.py"
  "get_today_meals:perf/locust_get_today_meals.py"
  "put_dish:perf/locust_put_dish.py"
  "delete_dish:perf/locust_delete_dish.py"
  "mixed:perf/locust_mixed_dish_behaviors.py"
)

run_locust_file() {
  local stage="$1"
  local run_name="$2"
  local locust_file="$3"
  local users="$4"
  local spawn_rate="$5"
  local run_time="$6"
  local dataset_file="$7"
  local created_ids_file="$8"

  local run_dir="$RESULT_DIR/$stage/$run_name"
  mkdir -p "$run_dir"

  log "Running $stage/$run_name"
  LOCUST_USER_ID="$USER_ID" \
  LOCUST_API_PREFIX="$API_PREFIX" \
  LOCUST_DISH_PAYLOAD_FILE="$dataset_file" \
  LOCUST_CREATED_DISH_ID_FILE="$created_ids_file" \
  LOCUST_DISH_ID_FILE="$created_ids_file" \
  LOCUST_WAIT_MIN="$WAIT_MIN" \
  LOCUST_WAIT_MAX="$WAIT_MAX" \
    uvx locust \
      -f "$locust_file" \
      --host "$HOST" \
      --headless \
      --users "$users" \
      --spawn-rate "$spawn_rate" \
      --run-time "$run_time" \
      --csv "$run_dir/stats" \
      --html "$run_dir/report.html" \
      --logfile "$run_dir/locust.log" \
      --loglevel INFO \
      >"$run_dir/console.log" 2>&1
}

run_locust_stage() {
  local stage="$1"
  local users spawn_rate run_time
  read -r users spawn_rate run_time <<<"$(stage_config "$stage")"

  local stage_dir="$RESULT_DIR/$stage"
  local dataset_file="$stage_dir/dish_payloads.jsonl"
  local created_ids_file="$stage_dir/created_dish_ids.txt"
  mkdir -p "$stage_dir"

  log "Preparing deterministic dataset for $stage"
  python3 perf/generate_dataset.py \
    --output "$dataset_file" \
    --created-ids-output "$created_ids_file" \
    --count "$DATASET_COUNT" \
    --user-id "$USER_ID" \
    >"$stage_dir/dataset.log"

  log "Running $stage: users=$users spawn_rate=$spawn_rate run_time=$run_time"
  for entry in "${locust_runs[@]}"; do
    run_locust_file \
      "$stage" \
      "${entry%%:*}" \
      "${entry#*:}" \
      "$users" \
      "$spawn_rate" \
      "$run_time" \
      "$dataset_file" \
      "$created_ids_file"
  done

  python3 perf/summarize_locust.py \
    --stage-dir "$stage_dir" \
    --output "$stage_dir/summary.csv" \
    | tee "$stage_dir/summary.txt"
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

if [ "$STAGE" != "reachability" ]; then
  log "Finished. Results are under $RESULT_DIR"
fi
