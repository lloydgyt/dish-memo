#!/usr/bin/env bash
set -euo pipefail

HOST_IP='47.94.9.240'
HOST="${HOST:-http://${HOST_IP}:8080}"
API_PREFIX="${LOCUST_API_PREFIX:-/api/v1}"
USER_ID="${LOCUST_USER_ID:-perf_user_001}"
USER_ID_PREFIX="${LOCUST_USER_ID_PREFIX:-perf_user}"
PHASE="${1:-${PHASE:-smoke}}"
TEST="${2:-${TEST:-suite}}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d_%H%M%S)}"
RESULT_DIR="${RESULT_DIR:-perf/results/$RUN_ID}"
COMMIT="${COMMIT:-$(git rev-parse --short HEAD 2>/dev/null || printf unknown)}"

WAIT_MIN="${LOCUST_WAIT_MIN:-1}"
WAIT_MAX="${LOCUST_WAIT_MAX:-1}"
LOCUST_EXIT_CODE_ON_ERROR="${LOCUST_EXIT_CODE_ON_ERROR:-0}"

ECS_USER="${ECS_USER:-root}"
ECS_HOST="${ECS_HOST:-}"
ECS_SSH_TARGET="${ECS_SSH_TARGET:-root@${HOST_IP}}"
ECS_SSH_PORT="${ECS_SSH_PORT:-22}"
ECS_REMOTE_DIR="${ECS_REMOTE_DIR:-/tmp/dish_memo_perf/$RUN_ID}"

# this is for in ECS's perspective, since we use ssh to let ECS execute command
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-dish_memo}"

# test phase settings
SMOKE_USERS="${SMOKE_USERS:-10}"
SMOKE_SPAWN_RATE="${SMOKE_SPAWN_RATE:-1}"
SMOKE_RUN_TIME="${SMOKE_RUN_TIME:-1m}"
SMOKE_ROW_PER_USER="${SMOKE_ROW_PER_USER:-50}"
SMOKE_PAYLOAD_COUNT="${SMOKE_PAYLOAD_COUNT:-300}"

BASELINE_USERS="${BASELINE_USERS:-100}"
BASELINE_SPAWN_RATE="${BASELINE_SPAWN_RATE:-10}"
BASELINE_RUN_TIME="${BASELINE_RUN_TIME:-10m}"
BASELINE_ROW_PER_USER="${BASELINE_ROW_PER_USER:-100}"
BASELINE_PAYLOAD_COUNT="${BASELINE_PAYLOAD_COUNT:-1000}"

TARGET_USERS="${TARGET_USERS:-1000}"
TARGET_SPAWN_RATE="${TARGET_SPAWN_RATE:-100}"
TARGET_RUN_TIME="${TARGET_RUN_TIME:-10m}"
TARGET_ROW_PER_USER="${TARGET_ROW_PER_USER:-200}"
TARGET_PAYLOAD_COUNT="${TARGET_PAYLOAD_COUNT:-5000}"


declare -a SUMMARY_FILES=()
PREPARED_REMOTE=0

log() {
  mkdir -p "$RESULT_DIR"
  printf '[perf-suite] %s\n' "$*" | tee -a "$RESULT_DIR/suite.log"
}

usage() {
  cat <<EOF
Usage:
  HOST=http://47.94.9.240:8080 ECS_HOST=47.94.9.240 MYSQL_PASSWORD=... bash perf/run_perf_suite.sh <phase> <test>

Phases:
  smoke      users=$SMOKE_USERS spawn_rate=$SMOKE_SPAWN_RATE run_time=$SMOKE_RUN_TIME row_per_user=$SMOKE_ROW_PER_USER payloads=$SMOKE_PAYLOAD_COUNT
  baseline   users=$BASELINE_USERS spawn_rate=$BASELINE_SPAWN_RATE run_time=$BASELINE_RUN_TIME row_per_user=$BASELINE_ROW_PER_USER payloads=$BASELINE_PAYLOAD_COUNT
  target     users=$TARGET_USERS spawn_rate=$TARGET_SPAWN_RATE run_time=$TARGET_RUN_TIME row_per_user=$TARGET_ROW_PER_USER payloads=$TARGET_PAYLOAD_COUNT

Tests:
  post_dish
  get_dishes_list
  get_dish_detail
  get_today_meals
  put_dish
  delete_dish
  mixed
  suite      Run all Locust files in fixed order.
  reachability

Remote MySQL options:
  ECS_HOST=47.94.9.240 or ECS_SSH_TARGET=user@host
  ECS_USER=root
  ECS_SSH_PORT=22
  MYSQL_HOST=127.0.0.1
  MYSQL_PORT=3306
  MYSQL_USER=root
  MYSQL_PASSWORD=...
  MYSQL_DATABASE=dish_memo

Data options:
  LOCUST_USER_ID_PREFIX=perf_user
  SMOKE_ROW_PER_USER=50 SMOKE_PAYLOAD_COUNT=300
  BASELINE_ROW_PER_USER=100 BASELINE_PAYLOAD_COUNT=1000
  TARGET_ROW_PER_USER=200 TARGET_PAYLOAD_COUNT=5000
  ROW_PER_USER=<override phase rows per user>
  PAYLOAD_COUNT=<override phase payloads>
  RESULT_DIR=perf/results/<run-id>
EOF
}

phase_config() {
  case "$1" in
    smoke)
      printf '%s %s %s %s %s' "$SMOKE_USERS" "$SMOKE_SPAWN_RATE" "$SMOKE_RUN_TIME" "${ROW_PER_USER:-$SMOKE_ROW_PER_USER}" "${PAYLOAD_COUNT:-$SMOKE_PAYLOAD_COUNT}"
      ;;
    baseline)
      printf '%s %s %s %s %s' "$BASELINE_USERS" "$BASELINE_SPAWN_RATE" "$BASELINE_RUN_TIME" "${ROW_PER_USER:-$BASELINE_ROW_PER_USER}" "${PAYLOAD_COUNT:-$BASELINE_PAYLOAD_COUNT}"
      ;;
    target)
      printf '%s %s %s %s %s' "$TARGET_USERS" "$TARGET_SPAWN_RATE" "$TARGET_RUN_TIME" "${ROW_PER_USER:-$TARGET_ROW_PER_USER}" "${PAYLOAD_COUNT:-$TARGET_PAYLOAD_COUNT}"
      ;;
    *)
      log "Unknown phase: $1"
      usage
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

locust_file_for() {
  case "$1" in
    post_dish) printf '%s' "perf/locust_post_dish.py" ;;
    get_dishes_list) printf '%s' "perf/locust_get_dishes_list.py" ;;
    get_dish_detail) printf '%s' "perf/locust_get_dish_detail.py" ;;
    get_today_meals) printf '%s' "perf/locust_get_today_meals.py" ;;
    put_dish) printf '%s' "perf/locust_put_dish.py" ;;
    delete_dish) printf '%s' "perf/locust_delete_dish.py" ;;
    mixed) printf '%s' "perf/locust_mixed_dish_behaviors.py" ;;
    *)
      log "Unknown test: $1"
      usage
      exit 1
      ;;
  esac
}

selected_tests() {
  case "$TEST" in
    suite|all)
      printf '%s\n' post_dish get_dishes_list get_dish_detail get_today_meals put_dish delete_dish mixed
      ;;
    post_dish|get_dishes_list|get_dish_detail|get_today_meals|put_dish|delete_dish|mixed)
      printf '%s\n' "$TEST"
      ;;
    reachability)
      printf '%s\n' reachability
      ;;
    *)
      log "Unknown test: $TEST"
      usage
      exit 1
      ;;
  esac
}

ssh_target() {
  if [ -n "$ECS_SSH_TARGET" ]; then
    printf '%s' "$ECS_SSH_TARGET"
    return
  fi
  if [ -z "$ECS_HOST" ]; then
    log "ECS_HOST or ECS_SSH_TARGET is required for tests that prepare remote data"
    usage
    exit 1
  fi
  printf '%s@%s' "$ECS_USER" "$ECS_HOST"
}

mysql_remote_command() {
  local sql_file="$1"
  if [ -n "$MYSQL_PASSWORD" ]; then
    printf 'mysql -h %q -P %q -u %q %q %q < %q' \
      "$MYSQL_HOST" "$MYSQL_PORT" "$MYSQL_USER" "-p$MYSQL_PASSWORD" "$MYSQL_DATABASE" "$sql_file"
    return
  fi
  printf 'mysql -h %q -P %q -u %q %q < %q' \
    "$MYSQL_HOST" "$MYSQL_PORT" "$MYSQL_USER" "$MYSQL_DATABASE" "$sql_file"
}

remote_exec() {
  # hardcoded pem
  ssh -p "$ECS_SSH_PORT" -i ~/.ssh/aliyun_secret.pem "$(ssh_target)" "$@"
}

remote_copy() {
  # hardcoded pem
  scp -P "$ECS_SSH_PORT" -i ~/.ssh/aliyun_secret.pem "$1" "$(ssh_target):$2"
}

generate_prepare_files() {
  local users="$1"
  local row_per_user="$2"
  local payload_count="$3"
  local data_dir="$RESULT_DIR/$PHASE/data"
  local targets
  targets="$(selected_tests | paste -sd, -)"
  log "Generating prepare SQL: phase=$PHASE test=$TEST targets=$targets users=$users row_per_user=$row_per_user payloads=$payload_count"
  python3 perf/generate_prepare_sql.py \
    --output-dir "$data_dir" \
    --run-id "$RUN_ID" \
    --user-id-prefix "$USER_ID_PREFIX" \
    --user-count "$users" \
    --targets "$targets" \
    --row-per-user "$row_per_user" \
    --payload-count "$payload_count" \
    >"$data_dir.generate.log"
}

prepare_remote_data() {
  local data_dir="$RESULT_DIR/$PHASE/data"
  log "Uploading prepare SQL to ECS and importing into MySQL"
  remote_exec "mkdir -p '$ECS_REMOTE_DIR'"
  remote_copy "$data_dir/prepare.sql" "$ECS_REMOTE_DIR/prepare.sql"
  remote_copy "$data_dir/cleanup.sql" "$ECS_REMOTE_DIR/cleanup.sql"
  PREPARED_REMOTE=1
  remote_exec "$(mysql_remote_command "$ECS_REMOTE_DIR/prepare.sql")"
}

cleanup_remote_data() {
  if [ "$PREPARED_REMOTE" != "1" ]; then
    return
  fi
  log "Cleaning prepared data on ECS"
  remote_exec "$(mysql_remote_command "$ECS_REMOTE_DIR/cleanup.sql")" || true
  remote_exec "rm -rf '$ECS_REMOTE_DIR'" || true
}

cleanup_local_data() {
  local data_dir="$RESULT_DIR/$PHASE/data"
  if [ -d "$data_dir" ]; then
    log "Removing local prepare data under $data_dir"
    rm -rf "$data_dir"
  fi
}

cleanup() {
  cleanup_remote_data
  cleanup_local_data
}

run_locust_file() {
  local run_name="$1"
  local locust_file="$2"
  local users="$3"
  local spawn_rate="$4"
  local run_time="$5"

  local stage_dir="$RESULT_DIR/$PHASE"
  local data_dir="$stage_dir/data"
  local run_dir="$stage_dir/$run_name"
  mkdir -p "$run_dir"

  log "Running $PHASE/$run_name"
  LOCUST_USER_ID="$USER_ID" \
  LOCUST_USER_ID_FILE="$data_dir/user_ids.txt" \
  LOCUST_API_PREFIX="$API_PREFIX" \
  LOCUST_DISH_PAYLOAD_FILE="$data_dir/dish_payloads.jsonl" \
  LOCUST_DISH_IDS="" \
  LOCUST_DISH_ID_FILE="$data_dir/dish_ids.txt" \
  LOCUST_CREATED_DISH_ID_FILE="$run_dir/created_dish_ids.txt" \
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
      --logfile "$run_dir/locust.log" \
      --loglevel INFO \
      --exit-code-on-error "$LOCUST_EXIT_CODE_ON_ERROR" \

  python3 perf/summarize_locust.py \
    --run-dir "$run_dir" \
    --stage "$PHASE" \
    --run "$run_name" \
    --output "$run_dir/summary.csv" \
    | tee "$run_dir/summarize.log"
  SUMMARY_FILES+=("$run_dir/summary.csv")
}

render_summary_report() {
  local users="$1"
  local spawn_rate="$2"
  local run_time="$3"
  if [ "${#SUMMARY_FILES[@]}" -eq 0 ]; then
    return
  fi

  local summary_args=()
  local summary_file
  for summary_file in "${SUMMARY_FILES[@]}"; do
    summary_args+=(--summary "$summary_file")
  done
  python3 perf/render_report.py \
    "${summary_args[@]}" \
    --output "$RESULT_DIR/$PHASE/summary.md" \
    --title "$PHASE $TEST Test Summary" \
    --host "$HOST" \
    --users "$users" \
    --spawn-rate "$spawn_rate" \
    --run-time "$run_time" \
    --stage "$PHASE" \
    --commit "$COMMIT" \
    | tee "$RESULT_DIR/$PHASE/summary.log"
}

run_phase_tests() {
  local users spawn_rate run_time row_per_user payload_count
  read -r users spawn_rate run_time row_per_user payload_count <<<"$(phase_config "$PHASE")"
  mkdir -p "$RESULT_DIR/$PHASE/data"
  trap cleanup EXIT

  generate_prepare_files "$users" "$row_per_user" "$payload_count"
  prepare_remote_data

  local run_name
  while IFS= read -r run_name; do
    run_locust_file "$run_name" "$(locust_file_for "$run_name")" "$users" "$spawn_rate" "$run_time"
  done < <(selected_tests)

  render_summary_report "$users" "$spawn_rate" "$run_time"
  log "Finished. Results are under $RESULT_DIR"
}

main() {
  if [ "$PHASE" = "-h" ] || [ "$PHASE" = "--help" ]; then
    usage
    exit 0
  fi

  if [ "$PHASE" = "reachability" ] || [ "$TEST" = "reachability" ]; then
    run_reachability
    exit 0
  fi

  run_phase_tests
}

main "$@"
