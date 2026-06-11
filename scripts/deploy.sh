#!/usr/bin/env bash
set -euo pipefail

MYSQL_PASSWORD="${MYSQL_PASSWORD}"
SYNC_CONFIG="${SYNC_CONFIG:-0}"
INIT_DB="${INIT_DB:-0}"

APP_NAME="${APP_NAME:-cook-history-service-1.0.0}"
ECS_IP="${ECS_IP:-47.94.9.240}"
IDENTITY="${IDENTITY:-$HOME/.ssh/aliyun_secret.pem}"
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_HOME="${REMOTE_HOME:-~}"
REMOTE_HOST="${REMOTE_USER}@${ECS_IP}"
IMAGE_TAR="${APP_NAME}.tar"
IMAGE_TAR_GZ="${IMAGE_TAR}.gz"

log() {
  printf '[deploy] %s\n' "$*"
}

scp_to_remote() {
  local source="$1"
  local target="$2"
  scp -i "$IDENTITY" "$source" "${REMOTE_HOST}:${target}"
}

ssh_remote() {
  ssh -i "$IDENTITY" "$REMOTE_HOST" "$1"
}

sync_config() {
  if [ "$SYNC_CONFIG" != "1" ]; then
    log "Skipping docker-compose.yml and .env sync. Set SYNC_CONFIG=1 to enable."
    return
  fi

  if [ ! -f ".env" ]; then
    log ".env not found; cannot sync config."
    exit 1
  fi

  log "Uploading docker-compose.yml"
  scp_to_remote "docker-compose.yml" "${REMOTE_HOME}/docker-compose.yml"

  log "Uploading .env"
  scp_to_remote ".env" "${REMOTE_HOME}/.env"
}

build_and_upload_image() {
  log "Building application package"
  mvn clean package -DskipTests

  log "Building Docker image: $APP_NAME"
  docker build -t "$APP_NAME" .

  log "Saving Docker image archive"
  docker save "$APP_NAME" -o "$IMAGE_TAR"
  gzip -f "$IMAGE_TAR"

  log "Uploading Docker image archive"
  scp_to_remote "$IMAGE_TAR_GZ" "${REMOTE_HOME}/${IMAGE_TAR_GZ}"
}

load_remote_image() {
  log "Loading image on remote host"
  ssh_remote "cd ${REMOTE_HOME} && gunzip -f ${IMAGE_TAR_GZ} && docker load -i ${IMAGE_TAR} && rm -f ${IMAGE_TAR}"
}

restart_remote_compose() {
  log "Restarting docker compose services"
  ssh_remote "cd ${REMOTE_HOME} && docker compose down && docker compose up -d"
}

init_db() {
  if [ "$INIT_DB" != "1" ]; then
    log "Skipping database initialization. Set INIT_DB=1 to enable."
    return
  fi

  log "Uploading schema.sql"
  scp_to_remote "src/main/resources/db/schema.sql" "${REMOTE_HOME}/schema.sql"

  log "Initializing database schema"
  ssh_remote "cd ${REMOTE_HOME} && mysql -h 127.0.0.1 -P 3306 -u root -p${MYSQL_PASSWORD} < schema.sql"
}

cleanup_local_artifacts() {
  log "Cleaning local image archive"
  rm -f "$IMAGE_TAR_GZ"
}

main() {
  sync_config
  build_and_upload_image
  load_remote_image
  restart_remote_compose
  cleanup_local_artifacts
  init_db
  log "Deployment finished."
}

main "$@"
