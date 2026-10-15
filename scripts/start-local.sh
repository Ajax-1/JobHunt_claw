#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="$ROOT_DIR/.jobclaw-dev"
BACKEND_LOG="$STATE_DIR/backend.log"
FRONTEND_LOG="$STATE_DIR/frontend.log"
BACKEND_PID_FILE="$STATE_DIR/backend.pid"
FRONTEND_PID_FILE="$STATE_DIR/frontend.pid"

cd "$ROOT_DIR"

fail() {
  echo "启动检查未通过：$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "找不到 $1，请先安装并确保它在 PATH 中。"
}

port_owner() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true
}

check_port() {
  local port="$1"
  local owner
  owner="$(port_owner "$port")"
  if [[ -n "$owner" ]]; then
    local command_line
    command_line="$(ps -p "$owner" -o command= 2>/dev/null || true)"
    fail "端口 $port 已被进程 $owner 占用${command_line:+：$command_line}。请先停止它，或执行 ./scripts/stop-local.sh。"
  fi
}

wait_for_url() {
  local url="$1"
  local timeout_seconds="$2"
  local pid="${3:-}"
  local elapsed=0
  local status

  while (( elapsed < timeout_seconds )); do
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
      return 1
    fi
    status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 "$url" 2>/dev/null || true)"
    if [[ "$status" =~ ^2[0-9][0-9]$|^3[0-9][0-9]$ ]]; then
      return 0
    fi
    sleep 1
    ((elapsed += 1))
  done
  return 1
}

stop_process_tree() {
  local pid="$1"
  local child_pid

  for child_pid in $(pgrep -P "$pid" 2>/dev/null || true); do
    stop_process_tree "$child_pid"
  done

  kill "$pid" 2>/dev/null || true
  sleep 1
  kill -9 "$pid" 2>/dev/null || true
}

stop_process_from_file() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] || return 0
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 0
  if kill -0 "$pid" 2>/dev/null; then
    stop_process_tree "$pid"
  fi
  rm -f "$pid_file"
}

require_command java
require_command node
require_command pnpm
require_command curl
require_command lsof
[[ -x "$ROOT_DIR/mvnw" ]] || fail "mvnw 不可执行，请运行 chmod +x mvnw。"

maven_args=()
maven_global_repo="${HOME:-}/.m2/repository"
maven_write_probe="$maven_global_repo/.jobclaw-write-probe.$$"
if [[ -d "$maven_global_repo" ]] && touch "$maven_write_probe" 2>/dev/null; then
  rm -f "$maven_write_probe"
else
  mkdir -p "$ROOT_DIR/.m2/repository"
  maven_args+=("-Dmaven.repo.local=$ROOT_DIR/.m2/repository")
  echo "检测到全局 Maven 仓库不可写，将使用仓库内的 .m2/repository。"
fi

run_maven() {
  if (( ${#maven_args[@]} > 0 )); then
    ./mvnw "${maven_args[@]}" "$@"
  else
    ./mvnw "$@"
  fi
}

java_major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
[[ "$java_major" =~ ^[0-9]+$ ]] || fail "无法识别 Java 版本，请安装 Java 21 或更高版本。"
(( java_major >= 21 )) || fail "当前 Java 版本为 $java_major，项目需要 Java 21 或更高版本。"

node_version="$(node --version | sed 's/^v//')"
node_major="${node_version%%.*}"
node_minor="${node_version#*.}"
node_minor="${node_minor%%.*}"
[[ "$node_major" =~ ^[0-9]+$ && "$node_minor" =~ ^[0-9]+$ ]] || fail "无法识别 Node.js 版本，请安装 Node.js 18.19 或更高版本。"
(( node_major > 18 || (node_major == 18 && node_minor >= 19) )) || fail "当前 Node.js 版本为 $node_version，项目需要 Node.js 18.19 或更高版本。"

mkdir -p "$STATE_DIR" "$ROOT_DIR/workspace/datas" "$ROOT_DIR/workspace/users" "$ROOT_DIR/workspace/conversations"

if [[ ! -f "$ROOT_DIR/.env" ]]; then
  cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
  echo "已根据 .env.example 创建 .env；模型和第三方渠道仍保持关闭或未配置。"
fi

frontend_modules="$ROOT_DIR/ui-react/node_modules"
frontend_next="$frontend_modules/.bin/next"
if [[ ! -f "$frontend_modules/.modules.yaml" || ! -x "$frontend_next" ]]; then
  echo "前端依赖尚未完整安装，已停止继续启动后端。"
  (cd "$ROOT_DIR/ui-react" && node scripts/frontend-doctor.mjs) || true
  echo
  echo "请在网络可用时执行：cd ui-react && pnpm install --frozen-lockfile"
  echo "如果本机已有完整缓存但没有网络，可尝试：cd ui-react && pnpm install --offline --frozen-lockfile"
  echo "依赖安装完成后重新执行：./scripts/start-local.sh"
  exit 1
fi

check_port 8087
check_port 3000

echo "正在准备后端依赖，Maven 首次运行需要访问 Maven Central；日志：$BACKEND_LOG"
if ! run_maven -DskipTests install >"$STATE_DIR/maven-install.log" 2>&1; then
  echo "后端依赖或编译准备失败，最后的日志如下：" >&2
  tail -n 80 "$STATE_DIR/maven-install.log" >&2 || true
  echo >&2
  echo "如果日志包含 DNS、代理或下载错误，请恢复 Maven Central 网络后重试。" >&2
  echo "脚本会在全局 Maven 仓库不可写时改用仓库内的 .m2/repository。" >&2
  exit 1
fi

rm -f "$BACKEND_PID_FILE" "$FRONTEND_PID_FILE"

(
  cd "$ROOT_DIR"
  if (( ${#maven_args[@]} > 0 )); then
    exec ./mvnw "${maven_args[@]}" -pl app spring-boot:run
  else
    exec ./mvnw -pl app spring-boot:run
  fi
) >"$BACKEND_LOG" 2>&1 &
backend_pid=$!
echo "$backend_pid" >"$BACKEND_PID_FILE"

echo "正在等待后端健康检查（最多 180 秒）……"
if ! wait_for_url "http://127.0.0.1:8087/actuator/health" 180 "$backend_pid"; then
  echo "后端没有在预期时间内启动，最后的日志如下：" >&2
  tail -n 100 "$BACKEND_LOG" >&2 || true
  stop_process_from_file "$BACKEND_PID_FILE"
  exit 1
fi

(
  cd "$ROOT_DIR/ui-react"
  exec node scripts/start-dev.mjs --hostname 127.0.0.1
) >"$FRONTEND_LOG" 2>&1 &
frontend_pid=$!
echo "$frontend_pid" >"$FRONTEND_PID_FILE"

echo "正在等待前端（最多 60 秒）……"
if ! wait_for_url "http://127.0.0.1:3000" 60 "$frontend_pid"; then
  echo "前端没有在预期时间内启动，最后的日志如下：" >&2
  tail -n 100 "$FRONTEND_LOG" >&2 || true
  stop_process_from_file "$FRONTEND_PID_FILE"
  stop_process_from_file "$BACKEND_PID_FILE"
  exit 1
fi

echo
echo "JobClaw 已启动："
echo "- 前端：http://localhost:3000"
echo "- 后端健康检查：http://localhost:8087/actuator/health"
echo "- 后端日志：$BACKEND_LOG"
echo "- 前端日志：$FRONTEND_LOG"
echo "- 停止服务：./scripts/stop-local.sh"
