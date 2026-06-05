#!/usr/bin/env bash
set -euo pipefail

INSTALL_JAVA="${INSTALL_JAVA:-1}"
INSTALL_MAVEN="${INSTALL_MAVEN:-1}"
INSTALL_MYSQL="${INSTALL_MYSQL:-1}"
INSTALL_REDIS="${INSTALL_REDIS:-1}"
SET_TIMEZONE="${SET_TIMEZONE:-1}"
TIMEZONE="${TIMEZONE:-Asia/Shanghai}"

log() {
  printf '[install-deps] %s\n' "$*"
}

need_root() {
  if [ "$(id -u)" -ne 0 ]; then
    log "Please run as root, for example: sudo bash scripts/install_production_dependencies.sh"
    exit 1
  fi
}

detect_os() {
  if [ -r /etc/os-release ]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    OS_ID="${ID:-}"
    OS_VERSION_ID="${VERSION_ID:-}"
  else
    OS_ID=""
    OS_VERSION_ID=""
  fi
}

has_command() {
  command -v "$1" >/dev/null 2>&1
}

install_apt() {
  export DEBIAN_FRONTEND=noninteractive
  log "Updating apt package index"
  apt-get update

  packages=(ca-certificates curl tzdata)
  if [ "$INSTALL_JAVA" = "1" ]; then
    packages+=(openjdk-17-jdk)
  fi
  if [ "$INSTALL_MAVEN" = "1" ]; then
    packages+=(maven)
  fi
  if [ "$INSTALL_MYSQL" = "1" ]; then
    packages+=(mysql-server)
  fi
  if [ "$INSTALL_REDIS" = "1" ]; then
    packages+=(redis-server)
  fi

  log "Installing packages: ${packages[*]}"
  apt-get install -y "${packages[@]}"
}

enable_service_if_present() {
  local service="$1"
  if has_command systemctl && systemctl list-unit-files "$service" >/dev/null 2>&1; then
    log "Enabling and starting $service"
    systemctl enable --now "$service"
  else
    log "Service $service not found; skipping systemctl enable"
  fi
}

enable_services() {
  if [ "$INSTALL_MYSQL" = "1" ]; then
    enable_service_if_present mysql.service
    enable_service_if_present mysqld.service
  fi

  if [ "$INSTALL_REDIS" = "1" ]; then
    enable_service_if_present redis-server.service
    enable_service_if_present redis.service
  fi
}

configure_timezone() {
  if [ "$SET_TIMEZONE" != "1" ]; then
    return
  fi

  if has_command timedatectl; then
    log "Setting timezone to $TIMEZONE"
    timedatectl set-timezone "$TIMEZONE" || log "Failed to set timezone; continue"
  elif [ -f "/usr/share/zoneinfo/$TIMEZONE" ]; then
    log "Setting timezone to $TIMEZONE without timedatectl"
    ln -snf "/usr/share/zoneinfo/$TIMEZONE" /etc/localtime
    printf '%s\n' "$TIMEZONE" >/etc/timezone || true
  else
    log "Timezone data for $TIMEZONE not found; skipping"
  fi
}

print_versions() {
  log "Installed versions:"
  if has_command java; then
    java -version 2>&1 | sed 's/^/[java] /'
  fi
  if has_command mvn; then
    mvn -version | sed 's/^/[maven] /'
  fi
  if has_command mysql; then
    mysql --version | sed 's/^/[mysql] /'
  fi
  if has_command redis-server; then
    redis-server --version | sed 's/^/[redis] /'
  fi
}

main() {
  need_root
  detect_os

  if [ "$OS_ID" != "ubuntu" ] || [ "$OS_VERSION_ID" != "24.04" ]; then
    log "Unsupported OS: ${OS_ID:-unknown} ${OS_VERSION_ID:-unknown}. This script targets Ubuntu 24.04 LTS."
    exit 1
  fi
  if ! has_command apt-get; then
    log "apt-get not found. This script targets Ubuntu 24.04 LTS."
    exit 1
  fi

  install_apt
  configure_timezone
  enable_services
  print_versions

  log "Dependency installation finished."
  log "Next steps: initialize MySQL schema, export application environment variables, build the jar, then start the service."
}

main "$@"
