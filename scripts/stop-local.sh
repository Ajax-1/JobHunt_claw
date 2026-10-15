#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="$ROOT_DIR/.jobclaw-dev"

stop_process_tree() {
  local pid="$1"
  local child_pid

  # spring-boot:run starts a separate Java process; stop descendants first.
  for child_pid in $(pgrep -P "$pid" 2>/dev/null || true); do
    stop_process_tree "$child_pid"
  done

  kill "$pid" 2>/dev/null || true
  for _ in {1..10}; do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  kill -9 "$pid" 2>/dev/null || true
}

stop_process() {
  local label="$1"
  local pid_file="$2"
  [[ -f "$pid_file" ]] || return 0

  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    echo "正在停止 ${label}（PID ${pid}）……"
    stop_process_tree "$pid"
  fi
  rm -f "$pid_file"
}

stop_process "前端" "$STATE_DIR/frontend.pid"
stop_process "后端" "$STATE_DIR/backend.pid"
echo "本地服务已停止。"
