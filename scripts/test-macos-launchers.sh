#!/usr/bin/env bash
# Exercise the packaged doctor, MCP ping, and desktop runtime on ONE data directory.
# Usage: test-macos-launchers.sh --ai-launcher PATH --launcher PATH --data-dir PATH
#        [--timeout-seconds 90] [--diagnostic-after-seconds 30]
# Caller supplies FLOWERFARM_HARNESS_TOKEN and its permission gates; none are enabled here.
# Requires Python 3 (PYTHON may select its executable). JAVA_HOME/bin/jcmd is used
# for a thread dump during slow startup, with its own ten-second deadline.
# Logs remain under target/macos-launcher-smoke.*. Existing smoke markers are refused.
# AUTO_SERVER and the application datasource settings are left intact.
set -euo pipefail

fail() { printf '%s\n' "macOS launcher smoke failed: $*" >&2; exit 1; }
ai_launcher=''
launcher=''
data_dir=''
timeout_seconds=90
diagnostic_after=30
while [[ $# -gt 0 ]]; do
  [[ $# -ge 2 ]] || fail "Missing value for $1."
  case "$1" in
    --ai-launcher) ai_launcher=$2 ;;
    --launcher) launcher=$2 ;;
    --data-dir) data_dir=$2 ;;
    --timeout-seconds) timeout_seconds=$2 ;;
    --diagnostic-after-seconds) diagnostic_after=$2 ;;
    *) fail 'Unknown option.' ;;
  esac
  shift 2
done
[[ "$timeout_seconds" =~ ^[1-9][0-9]{0,2}$ ]] || fail 'Timeout must be 1-999 seconds.'
[[ "$diagnostic_after" =~ ^[1-9][0-9]{0,2}$ ]] || fail 'Diagnostic delay must be 1-999 seconds.'
[[ $diagnostic_after -le $timeout_seconds ]] || fail 'Diagnostic delay must not exceed the phase timeout.'
[[ -n "$ai_launcher" && -n "$launcher" && -n "$data_dir" ]] || fail 'Both launcher paths and a data directory are required.'
for value in "$ai_launcher" "$launcher" "$data_dir"; do
  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail 'Paths must not contain newlines.'
done
[[ -f "$ai_launcher" && -x "$ai_launcher" ]] || fail 'The AI launcher must be an executable file.'
[[ -f "$launcher" && -x "$launcher" ]] || fail 'The desktop launcher must be an executable file.'
ai_launcher="$(cd "$(dirname "$ai_launcher")" && pwd -P)/$(basename "$ai_launcher")"
launcher="$(cd "$(dirname "$launcher")" && pwd -P)/$(basename "$launcher")"
python_executable=${PYTHON:-python3}
command -v "$python_executable" >/dev/null 2>&1 || fail 'Python 3 is required.'
"$python_executable" -c 'import sys; assert sys.version_info.major == 3' || fail 'Python 3 is required.'
harness_token=${FLOWERFARM_HARNESS_TOKEN:-}
[[ ${#harness_token} -ge 24 ]] || fail 'Caller must provide a harness token of at least 24 characters.'
mkdir -p "$data_dir"
data_dir=$(cd "$data_dir" && pwd -P)
export FLOWERFARM_DATA_DIR="$data_dir"
marker="$data_dir/release-smoke.ok"
[[ ! -e "$marker" && ! -L "$marker" ]] || fail 'An existing release-smoke.ok would be stale; it was preserved.'
project=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
mkdir -p "$project/target"
log_dir=$(mktemp -d "$project/target/macos-launcher-smoke.XXXXXX")
printf 'macOS launcher smoke logs: %s\n' "$log_dir"

active_pid=''
diagnostic_pid=''
diagnostic_start=0
diagnostic_file=''
diagnostic_phase=''

stop_owned_process() {
  local owned_pid=${1:-}
  [[ -n "$owned_pid" ]] || return 0
  if kill -0 "$owned_pid" 2>/dev/null; then
    kill -TERM "$owned_pid" 2>/dev/null || true
    local attempt
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
      kill -0 "$owned_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$owned_pid" 2>/dev/null; then kill -KILL "$owned_pid" 2>/dev/null || true; fi
  fi
  wait "$owned_pid" 2>/dev/null || true
}

cleanup() {
  local result=$?
  trap - EXIT INT TERM
  stop_owned_process "$active_pid"
  stop_owned_process "$diagnostic_pid"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

print_log() {
  "$python_executable" - "$1" <<'PY'
import os
import pathlib
import re
import sys
file = pathlib.Path(sys.argv[1])
if file.exists():
    raw = file.read_bytes()
    safe = re.sub(rb'(:[0-9]{1,5}/)[A-Za-z0-9_-]{16,}', rb'\1[redacted H2 access key]', raw)
    token_bytes = os.environ.get('FLOWERFARM_HARNESS_TOKEN', '').encode()
    if token_bytes:
        safe = safe.replace(token_bytes, b'[redacted harness token]')
    if safe != raw:
        file.write_bytes(safe)
    text = safe[:1024 * 1024].decode('utf-8', errors='replace')
    token = os.environ.get('FLOWERFARM_HARNESS_TOKEN', '')
    if token:
        text = text.replace(token, '[redacted harness token]')
    sys.stdout.write(text)
    if text and not text.endswith('\n'):
        print()
PY
}

redact_phase_token() {
  "$python_executable" - "$log_dir/$1.stdout" "$log_dir/$1.stderr" <<'PY'
import os
import pathlib
import re
import sys
token = os.environ['FLOWERFARM_HARNESS_TOKEN'].encode()
leaked = False
for name in sys.argv[1:]:
    file = pathlib.Path(name)
    raw = file.read_bytes()
    if token in raw:
        leaked = True
    safe = re.sub(rb'(:[0-9]{1,5}/)[A-Za-z0-9_-]{16,}', rb'\1[redacted H2 access key]', raw)
    safe = safe.replace(token, b'[redacted harness token]')
    if safe != raw:
        file.write_bytes(safe)
if leaked:
    raise SystemExit('Launcher exposed its harness token; captured output was redacted.')
PY
}

lock_status() {
  "$python_executable" - "$data_dir" "$1" <<'PY'
import datetime
import pathlib
import sys
lock = pathlib.Path(sys.argv[1]) / 'flowerfarm.lock.db'
label = sys.argv[2]
try:
    info = lock.stat()
    print('H2 lock at ' + label + ': present; bytes=' + str(info.st_size)
          + '; modified=' + datetime.datetime.fromtimestamp(info.st_mtime, datetime.timezone.utc).isoformat())
    if info.st_size <= 65536:
        for line in lock.read_text(errors='replace').splitlines():
            key, separator, value = line.partition('=')
            if separator and key.strip() in ('method', 'server', 'hostName'):
                print('  ' + key.strip() + '=' + value)
except FileNotFoundError:
    print('H2 lock at ' + label + ': absent')
except OSError:
    print('H2 lock at ' + label + ': metadata unavailable')
PY
}

start_diagnostic() {
  local phase=$1
  local jcmd=''
  if [[ -n ${JAVA_HOME:-} && -x "$JAVA_HOME/bin/jcmd" ]]; then
    jcmd="$JAVA_HOME/bin/jcmd"
  elif command -v jcmd >/dev/null 2>&1; then
    jcmd=$(command -v jcmd)
  fi
  printf 'Slow %s startup: collecting thread diagnostic for pid %s.\n' "$phase" "$active_pid"
  lock_status "$phase diagnostic"
  if [[ -z "$jcmd" ]]; then
    printf 'jcmd is unavailable; no thread dump collected.\n'
    return
  fi
  diagnostic_phase=$phase
  diagnostic_file="$log_dir/$phase.threads.txt"
  "$jcmd" "$active_pid" Thread.print -l >"$diagnostic_file" 2>&1 &
  diagnostic_pid=$!
  diagnostic_start=$SECONDS
}

check_diagnostic() {
  [[ -n "$diagnostic_pid" ]] || return 0
  if ! kill -0 "$diagnostic_pid" 2>/dev/null; then
    local result=0
    wait "$diagnostic_pid" || result=$?
    printf '%s thread diagnostic exit=%s.\n' "$diagnostic_phase" "$result"
    diagnostic_pid=''
    print_log "$diagnostic_file"
  elif [[ $((SECONDS - diagnostic_start)) -ge 10 ]]; then
    printf '%s thread diagnostic exceeded its 10-second deadline.\n' "$diagnostic_phase"
    stop_owned_process "$diagnostic_pid"
    diagnostic_pid=''
    print_log "$diagnostic_file"
  fi
}

run_phase() {
  local phase=$1
  local input=$2
  shift 2
  local started=$SECONDS
  local diagnosed=false
  local result=0
  lock_status "$phase before launch"
  "$@" <"$input" >"$log_dir/$phase.stdout" 2>"$log_dir/$phase.stderr" &
  active_pid=$!
  printf '%s started; pid=%s, deadline=%ss.\n' "$phase" "$active_pid" "$timeout_seconds"
  while kill -0 "$active_pid" 2>/dev/null; do
    if [[ "$diagnosed" == false && $((SECONDS - started)) -ge $diagnostic_after ]]; then
      start_diagnostic "$phase"
      diagnosed=true
    fi
    check_diagnostic
    if [[ $((SECONDS - started)) -ge $timeout_seconds ]]; then
      printf '%s exceeded its %s-second deadline.\n' "$phase" "$timeout_seconds" >&2
      stop_owned_process "$active_pid"
      active_pid=''
      result=124
      break
    fi
    sleep 1
  done
  if [[ -n "$active_pid" ]]; then
    wait "$active_pid" || result=$?
    active_pid=''
  fi
  while [[ -n "$diagnostic_pid" ]]; do check_diagnostic; [[ -z "$diagnostic_pid" ]] || sleep 0.2; done
  printf '%s finished; exit=%s, elapsed=%ss.\n' "$phase" "$result" "$((SECONDS - started))"
  local token_leaked=false
  redact_phase_token "$phase" || token_leaked=true
  print_log "$log_dir/$phase.stderr"
  lock_status "$phase after exit"
  [[ "$token_leaked" == false ]] || return 1
  return "$result"
}

validate_json() {
  "$python_executable" - "$log_dir/$1.stdout" "$1" <<'PY'
import json
import os
import pathlib
import sys
file = pathlib.Path(sys.argv[1])
phase = sys.argv[2]
raw = file.read_text()
token = os.environ['FLOWERFARM_HARNESS_TOKEN']
if token in raw:
    file.write_text(raw.replace(token, '[redacted harness token]'))
    raise SystemExit('MCP ' + phase + ' exposed its harness token; captured output was redacted.')
try:
    value = json.loads(raw)
except (ValueError, TypeError):
    raise SystemExit('MCP ' + phase + ' did not return one valid JSON document.')
if not isinstance(value, dict):
    raise SystemExit('MCP ' + phase + ' did not return a JSON object.')
if phase == 'doctor':
    if value.get('ready') is not True:
        raise SystemExit('MCP doctor did not report ready=true.')
    reported = value.get('dataDirectory')
    if not isinstance(reported, str) or pathlib.Path(reported).resolve() != pathlib.Path(os.environ['FLOWERFARM_DATA_DIR']).resolve():
        raise SystemExit('MCP doctor did not use the requested shared data directory.')
elif value.get('jsonrpc') != '2.0' or value.get('id') != 'release-smoke' or not isinstance(value.get('result'), dict) or 'error' in value:
    raise SystemExit('MCP ping did not return the expected successful response.')
PY
}

run_phase doctor /dev/null "$ai_launcher" --mcp-doctor
validate_json doctor
printf '%s\n' '{"jsonrpc":"2.0","id":"release-smoke","method":"ping","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}' >"$log_dir/ping.stdin"
run_phase ping "$log_dir/ping.stdin" "$ai_launcher"
validate_json ping
[[ ! -e "$marker" && ! -L "$marker" ]] || fail 'A smoke marker existed before the desktop launch; it was preserved.'
run_phase desktop /dev/null "$launcher" --release-smoke
[[ -f "$marker" && ! -L "$marker" && -s "$marker" ]] || fail 'Desktop exited without a new release-smoke.ok.'
grep -q 'Packaged runtime started successfully' "$marker" || fail 'Desktop smoke marker has unexpected contents.'
printf 'All three macOS launcher phases exited successfully using the same data directory.\n'
