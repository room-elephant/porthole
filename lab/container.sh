#!/usr/bin/env bash
# Changes a lab container's state while `make lab` runs, to watch the dashboard follow.
#
#   lab/container.sh list
#   lab/container.sh <pause|play|stop|start|restart> <container> [lab-a|lab-b]
#
# play resumes a paused container or starts a stopped one. The host can be left out
# unless the name is on both hosts, like shared-name.
set -euo pipefail

# Prints the Docker API port of a lab host, or nothing for an unknown one.
port_of() {
  case "$1" in
    lab-a) printf '23751' ;;
    lab-b) printf '23752' ;;
  esac
}

usage() {
  sed -n '2,8p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

# Usage: on <host> <docker args...>
on() {
  local host="$1"
  shift
  docker -H "tcp://127.0.0.1:$(port_of "$host")" "$@"
}

require_lab() {
  local host
  for host in lab-a lab-b; do
    on "$host" version --format '{{.Server.Version}}' >/dev/null 2>&1 ||
      fail "The $host Docker host isn't reachable. Start the lab with make lab first."
  done
}

list() {
  local host
  for host in lab-a lab-b; do
    printf '%s\n' "$host"
    on "$host" ps --all --format '  {{printf "%-22s" .Names}} {{printf "%-10s" .State}} {{.Status}}'
  done
}

# Prints the hosts that have a container with this exact name.
hosts_with() {
  local name="$1" host
  for host in lab-a lab-b; do
    if [[ -n "$(on "$host" ps --all --quiet --filter "name=^${name}$")" ]]; then
      printf '%s\n' "$host"
    fi
  done
}

resolve_host() {
  local name="$1" requested="${2:-}"
  if [[ -n "$requested" ]]; then
    [[ -n "$(port_of "$requested")" ]] || fail "Unknown host $requested. Use lab-a or lab-b."
    [[ -n "$(hosts_with "$name" | grep -x "$requested" || true)" ]] || fail "$requested has no container named $name."
    printf '%s\n' "$requested"
    return
  fi
  local found
  found="$(hosts_with "$name")"
  case "$(printf '%s' "$found" | grep -c .)" in
    0) fail "No lab container is named $name. Run lab/container.sh list to see them." ;;
    1) printf '%s\n' "$found" ;;
    *) fail "$name is on both hosts. Add lab-a or lab-b." ;;
  esac
}

state_of() {
  on "$1" inspect --format '{{.State.Status}}' "$2"
}

play() {
  local host="$1" name="$2"
  case "$(state_of "$host" "$name")" in
    paused) on "$host" unpause "$name" >/dev/null ;;
    running | restarting)
      printf '%s on %s is already %s.\n' "$name" "$host" "$(state_of "$host" "$name")"
      exit 0
      ;;
    *) on "$host" start "$name" >/dev/null ;;
  esac
}

main() {
  [[ $# -ge 1 ]] || usage
  local action="$1"
  shift
  require_lab
  if [[ "$action" == list ]]; then
    [[ $# -eq 0 ]] || usage
    list
    return
  fi
  [[ $# -ge 1 && $# -le 2 ]] || usage
  local name="$1" host
  host="$(resolve_host "$name" "${2:-}")"
  case "$action" in
    pause) on "$host" pause "$name" >/dev/null ;;
    play) play "$host" "$name" ;;
    stop) on "$host" stop --timeout 1 "$name" >/dev/null ;;
    start) on "$host" start "$name" >/dev/null ;;
    restart) on "$host" restart --timeout 1 "$name" >/dev/null ;;
    *) usage ;;
  esac
  printf '%s on %s is now %s.\n' "$name" "$host" "$(state_of "$host" "$name")"
}

main "$@"
