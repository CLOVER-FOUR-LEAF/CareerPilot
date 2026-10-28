#!/usr/bin/env bash
#
# CareerPilot Linux/Docker 一键启动
#
# 与 scripts/local/*.ps1 等价，但基础设施用 Docker、环境是 Linux。
#
# 用法：
#   cp scripts/local/linux/env.example scripts/local/.env
#   ./scripts/local/linux/start-all.sh infra     # 起 MySQL / Redis 并初始化库
#   ./scripts/local/linux/start-all.sh all       # 全部
#   ./scripts/local/linux/start-all.sh status
#   ./scripts/local/linux/start-all.sh stop-all
#
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$SCRIPT_DIR/../../.." && pwd)
RUNTIME="$REPO/.runtime"
LOGS="$RUNTIME/logs"
PIDS="$RUNTIME/pids"

ENV_FILE="${ENV_FILE:-$REPO/scripts/local/.env}"
if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  set -a; source "$ENV_FILE"; set +a
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-careerpilot-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-careerpilot}"
MYSQL_PORT="${MYSQL_PORT:-3308}"
REDIS_CONTAINER="${REDIS_CONTAINER:-careerpilot-redis}"
REDIS_PORT="${REDIS_PORT:-6381}"
REDIS_PASSWORD="${REDIS_PASSWORD:-redis123456}"

export MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
export MYSQL_PORT
export MYSQL_USER="${MYSQL_USER:-careerpilot}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-cp123456}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT
export REDIS_PASSWORD
export JWT_SECRET="${JWT_SECRET:-careerpilot-default-secret-key-must-be-at-least-256-bits-long-for-hs256}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:3000,http://127.0.0.1:3000,http://localhost:3100,http://127.0.0.1:3100}"
export AI_ORCHESTRATOR_URL="${AI_ORCHESTRATOR_URL:-http://localhost:8001}"
export AI_SCHEDULER_URL="${AI_SCHEDULER_URL:-http://localhost:8085}"
export RESUME_SERVICE_URL="${RESUME_SERVICE_URL:-http://localhost:8082}"
export JD_SERVICE_URL="${JD_SERVICE_URL:-http://localhost:8083}"
export STORAGE_ROOT="${STORAGE_ROOT:-$RUNTIME/uploads}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-}"

VENV="${VENV:-$RUNTIME/venv}"
JAVA="${JAVA:-$(command -v java)}"

# user-center 的 8081 在本机经常被别的服务占用，统一退避到 18081
USER_CENTER_PORT="${USER_CENTER_PORT:-18081}"
export USER_CENTER_PORT
export USER_CENTER_HOST="localhost:$USER_CENTER_PORT"

mkdir -p "$LOGS" "$PIDS" "$RUNTIME/uploads"

AGENTS=(
  "agent-orchestrator:8001"
  "jd-parser-agent:8002"
  "resume-analyzer-agent:8003"
  "match-agent:8004"
  "resume-optimizer-agent:8005"
  "interview-agent:8006"
  "cover-letter-agent:8007"
  "career-advisor-agent:8008"
)

SERVICES=(
  "user-center:$USER_CENTER_PORT"
  "resume-service:8082"
  "jd-service:8083"
  "match-service:8084"
  "ai-task-scheduler:8085"
  "admin-service:8086"
  "gateway-service:8080"
)

port_busy() { ss -lnt "sport = :$1" 2>/dev/null | grep -q LISTEN; }

wait_port() { # port name timeout
  local p=$1 n=$2 t=${3:-90} i=0
  while [ $i -lt $t ]; do
    port_busy "$p" && { echo "  OK   $n :$p"; return 0; }
    sleep 1; i=$((i+1))
  done
  echo "  FAIL $n :$p (${t}s 内未监听)"; return 1
}

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
    --default-character-set=utf8mb4 "$MYSQL_DATABASE" 2>/dev/null
}

mysql_scalar() {
  docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N \
    -e "$1" 2>/dev/null
}

start_infra() {
  echo "== 基础设施（Docker）"
  if ! docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
    echo "  创建 $MYSQL_CONTAINER"
    docker run -d --name "$MYSQL_CONTAINER" --restart unless-stopped \
      -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
      -e MYSQL_DATABASE="$MYSQL_DATABASE" \
      -e MYSQL_USER="$MYSQL_USER" -e MYSQL_PASSWORD="$MYSQL_PASSWORD" \
      -p "127.0.0.1:$MYSQL_PORT:3306" \
      -v careerpilot-mysql-data:/var/lib/mysql \
      mysql:8.0.40 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
  else
    docker start "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
  fi

  if ! docker inspect "$REDIS_CONTAINER" >/dev/null 2>&1; then
    echo "  创建 $REDIS_CONTAINER"
    docker run -d --name "$REDIS_CONTAINER" --restart unless-stopped \
      -p "127.0.0.1:$REDIS_PORT:6379" valkey/valkey:8 \
      --requirepass "$REDIS_PASSWORD" >/dev/null
  else
    docker start "$REDIS_CONTAINER" >/dev/null 2>&1 || true
  fi

  echo "  等待 MySQL 就绪..."
  until docker exec "$MYSQL_CONTAINER" mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent 2>/dev/null; do
    sleep 2
  done
  echo "  OK   MySQL :$MYSQL_PORT"
  echo "  OK   Redis :$REDIS_PORT"

  # 首次初始化：V1 + V2；老库补 V3（角色 / 平台配置）
  local tables role_col
  tables=$(mysql_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MYSQL_DATABASE' AND table_name='t_user';")
  if [ "${tables:-0}" = "0" ]; then
    echo "  导入 V1 / V2"
    mysql_exec < "$REPO/db/migrations/V1__init_schema.sql"
    mysql_exec < "$REPO/db/migrations/V2__hr_insight.sql"
  fi
  role_col=$(mysql_scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$MYSQL_DATABASE' AND table_name='t_user' AND column_name='role';")
  if [ "${role_col:-0}" = "0" ]; then
    echo "  应用 V3（平台角色）"
    mysql_exec < "$REPO/db/migrations/V3__platform_roles.sql"
  fi
  # V4 是幂等的：补 Agent / 向量 / TTS 配置默认值与行为视图索引
  echo "  应用 V4（Agent 配置中心）"
  mysql_exec < "$REPO/db/migrations/V4__agent_configs_activity.sql"
  echo "  OK   数据库已就绪"
}

ensure_venv() {
  if [ ! -x "$VENV/bin/python" ]; then
    echo "== 创建 Python venv: $VENV"
    python3 -m venv "$VENV"
    cat "$REPO"/agents/*/requirements.txt | sort -u > "$RUNTIME/requirements.txt"
    "$VENV/bin/pip" install -q --upgrade pip
    "$VENV/bin/pip" install -q -r "$RUNTIME/requirements.txt"
  fi
}

start_agents() {
  echo "== Python Agent 层"
  ensure_venv
  for item in "${AGENTS[@]}"; do
    local name=${item%%:*} port=${item##*:}
    local pidf="$PIDS/agent-$name.pid"
    if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then
      echo "  SKIP agent-$name 已在运行"; continue
    fi
    if port_busy "$port"; then echo "  SKIP $name :$port 已被占用"; continue; fi
    ( cd "$REPO/agents/$name" && PYTHONPATH="$REPO/agents" nohup "$VENV/bin/python" \
        -m uvicorn app.main:app --host 0.0.0.0 --port "$port" \
        > "$LOGS/agent-$name.log" 2>&1 & echo $! > "$pidf" )
    wait_port "$port" "agent-$name" 60
  done
}

start_services() {
  echo "== Java 微服务"
  for item in "${SERVICES[@]}"; do
    local name=${item%%:*} port=${item##*:}
    local pidf="$PIDS/service-$name.pid"
    if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then
      echo "  SKIP service-$name 已在运行"; continue
    fi
    if port_busy "$port"; then echo "  SKIP $name :$port 已被占用"; continue; fi
    local jar
    jar=$(ls "$REPO/services/$name/target/"*-SNAPSHOT.jar 2>/dev/null | head -1)
    if [ -z "$jar" ]; then
      echo "  FAIL $name 缺少 jar（先跑 cd services && mvn -DskipTests package）"; continue
    fi
    ( cd "$REPO/services/$name" && nohup "$JAVA" -jar "$jar" --server.port="$port" \
        > "$LOGS/service-$name.log" 2>&1 & echo $! > "$pidf" )
    wait_port "$port" "service-$name" 120
  done
}

start_vite() { # name dir port
  local name=$1 dir=$2 port=$3 pidf="$PIDS/$1.pid"
  if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then
    echo "  SKIP $name 已在运行"; return 0
  fi
  if port_busy "$port"; then echo "  SKIP $name :$port 已被占用"; return 0; fi
  [ -d "$dir/node_modules" ] || ( cd "$dir" && npm install >/dev/null 2>&1 )
  ( cd "$dir" && nohup npm run dev -- --host 0.0.0.0 --port "$port" \
      > "$LOGS/$name.log" 2>&1 & echo $! > "$pidf" )
  wait_port "$port" "$name" 60
}

start_frontend() {
  echo "== 前端"
  start_vite frontend "$REPO/web" 3000
  start_vite admin-web "$REPO/admin-web" 3100
}

status() {
  echo "== 端口状态"
  for item in "${AGENTS[@]}" "${SERVICES[@]}" "frontend:3000" "admin-web:3100"; do
    local name=${item%%:*} port=${item##*:}
    if port_busy "$port"; then echo "  UP   $name :$port"; else echo "  DOWN $name :$port"; fi
  done
  echo "== 容器"
  docker ps --filter name=careerpilot --format '  {{.Names}}  {{.Status}}  {{.Ports}}'
}

stop_all() {
  echo "== 停止应用进程（容器保留）"
  for f in "$PIDS"/*.pid; do
    [ -e "$f" ] || continue
    local pid; pid=$(cat "$f")
    if kill -0 "$pid" 2>/dev/null; then
      pkill -P "$pid" 2>/dev/null
      kill "$pid" 2>/dev/null
      echo "  killed $(basename "$f")"
    fi
    rm -f "$f"
  done
}

case "${1:-all}" in
  infra)    start_infra ;;
  agents)   start_agents ;;
  services) start_services ;;
  frontend) start_frontend ;;
  all)      start_infra; start_agents; start_services; start_frontend; status ;;
  status)   status ;;
  stop-all) stop_all ;;
  bootstrap) "$SCRIPT_DIR/bootstrap-admin.sh" ;;
  *) echo "用法: $0 {infra|agents|services|frontend|all|status|stop-all|bootstrap}"; exit 1 ;;
esac
