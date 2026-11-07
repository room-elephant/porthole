#!/usr/bin/env bash
# Starts the fake Docker hosts and containers, then the app. Ctrl-C stops everything.
set -euo pipefail
set -m # Each background job gets its own process group, so it can be stopped as a whole.

LAB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$LAB")"
URL="http://localhost:5173"
READY_TIMEOUT_SECONDS=300
STOP_TIMEOUT_SECONDS=25

server_pid=""
client_pid=""

log() { printf '[lab] %s\n' "$*"; }
fail() {
  printf '[lab] ERROR: %s\n' "$*" >&2
  exit 1
}

daemons() { docker compose -f "$LAB/compose.yml" "$@"; }

# Usage: scenario <lab-a|lab-b> <daemon port> <compose args...>
scenario() {
  local name="$1" port="$2"
  shift 2
  DOCKER_HOST="tcp://127.0.0.1:$port" docker compose -f "$LAB/scenarios/$name.yml" "$@"
}

# The manual profile makes `down` include the container that's only ever created.
remove_containers() {
  scenario lab-a 23751 --profile manual down --remove-orphans --timeout 0 >/dev/null 2>&1 || true
  scenario lab-b 23752 down --remove-orphans --timeout 0 >/dev/null 2>&1 || true
}

port_in_use() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

require_free() {
  local port
  for port in "$@"; do
    port_in_use "$port" && fail "port $port is already in use. Stop whatever is using it and run make lab again."
  done
  return 0
}

# Usage: start_job <log prefix> <command...>. Sets job_pid.
start_job() {
  local prefix="$1"
  shift
  # Input comes from /dev/null because a background job that reads the terminal gets suspended.
  ("$@" </dev/null 2>&1 | awk -v prefix="[$prefix] " '{ print prefix $0; fflush() }') &
  job_pid=$!
  disown "$job_pid" # Stops bash printing "Terminated" when the job is stopped.
}

is_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

stop_job() {
  local pid="$1" waited=0
  is_running "$pid" || return 0
  kill -TERM -- "-$pid" 2>/dev/null || true
  while is_running "$pid" && ((waited < STOP_TIMEOUT_SECONDS)); do
    sleep 1
    waited=$((waited + 1))
  done
  kill -KILL -- "-$pid" 2>/dev/null || true
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  log "Stopping the app and removing the fake containers..."
  stop_job "$server_pid"
  stop_job "$client_pid"
  remove_containers
  daemons down --timeout 5 >/dev/null 2>&1 || true
  log "Stopped."
  # Ctrl-C is the normal way to stop, so make shouldn't report an error.
  ((status == 130)) && exit 0
  exit "$status"
}

wait_for_url() {
  local url="$1" pid="$2" name="$3" waited=0
  until curl -fsS -o /dev/null --max-time 15 "$url" 2>/dev/null; do
    is_running "$pid" || fail "the $name stopped during startup. See the log above."
    ((waited < READY_TIMEOUT_SECONDS)) || fail "the $name wasn't ready after ${READY_TIMEOUT_SECONDS}s."
    sleep 1
    waited=$((waited + 1))
  done
}

docker info >/dev/null 2>&1 || fail "Docker isn't running. Start it and run make lab again."

# Checked before the cleanup below, so a second make lab can't take down one that's already running.
require_free 9753 5173

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

log "Starting the fake Docker hosts..."
daemons down --timeout 5 >/dev/null 2>&1 || true
require_free 23751 23752 23759 18100-18119
daemons up -d --wait

log "Creating the fake containers (the first run downloads images)..."
remove_containers # Leftovers from a run that didn't get to clean up.
scenario lab-a 23751 up -d --quiet-pull
scenario lab-a 23751 --profile manual create created
docker -H tcp://127.0.0.1:23751 pause paused >/dev/null
scenario lab-b 23752 up -d --quiet-pull

if [[ ! "$ROOT/client/node_modules/.package-lock.json" -nt "$ROOT/client/package-lock.json" ]]; then
  log "Installing client dependencies..."
  make -C "$ROOT/client" install
fi

log "Starting the back and front ends..."
export SPRING_CONFIG_ADDITIONALLOCATION="file:$LAB/application.yml"
export PORTHOLE_PREFERENCES_PATH="$LAB/preferences.json"
start_job server make -C "$ROOT/server" dev
server_pid=$job_pid
start_job client npm --prefix "$ROOT/client" run dev -- --strictPort
client_pid=$job_pid

wait_for_url "http://localhost:9753/api/containers" "$server_pid" "back end"
wait_for_url "$URL" "$client_pid" "front end"

printf '\n[lab] Ready: %s\n[lab] Press Ctrl-C to stop. Preferences save to lab/preferences.json, links to lab/links.json.\n\n' "$URL"

while is_running "$server_pid" && is_running "$client_pid"; do
  sleep 1
done
fail "the back or front end stopped unexpectedly. See the log above."
