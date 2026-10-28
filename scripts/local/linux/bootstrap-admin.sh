#!/usr/bin/env bash
#
# 平台账号引导（Linux + Docker MySQL）
#
# 做三件事：
#   1. 通过网关注册引导管理员与演示 HR 账号（已存在则跳过）
#   2. 用 SQL 把角色改成 ADMIN / RECRUITER —— 自助注册一律是 USER，
#      这是刻意的：角色只能由管理端授予，不能自己声明
#   3. 打印登录信息
#
# 幂等：重复执行不会报错，也不会改密码。
#
# 用法：
#   ./scripts/local/linux/bootstrap-admin.sh
#   MYSQL_CONTAINER=my-mysql MYSQL_ROOT_PASSWORD=xxx ./scripts/local/linux/bootstrap-admin.sh
#
set -euo pipefail

GW="${GW:-http://127.0.0.1:8080}"
ADMIN_EMAIL="${CP_ADMIN_EMAIL:-admin@demo.local}"
ADMIN_PASSWORD="${CP_ADMIN_PASSWORD:-Admin1234}"
HR_EMAIL="${CP_HR_EMAIL:-hr@demo.local}"
HR_PASSWORD="${CP_HR_PASSWORD:-Abcd1234}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-careerpilot-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-careerpilot}"

register() {
  local email="$1" password="$2" nickname="$3"
  curl -s -m 10 -X POST "$GW/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$password\",\"nickname\":\"$nickname\"}" \
    | grep -q '"code":0' \
    && echo "  注册成功：$email" \
    || echo "  已存在或注册失败（继续）：$email"
}

echo "== 1/2 注册账号"
register "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "平台管理员"
register "$HR_EMAIL" "$HR_PASSWORD" "HR 演示"

echo "== 2/2 写入角色"
docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "
  UPDATE t_user SET role = 'ADMIN'     WHERE email = '$ADMIN_EMAIL';
  UPDATE t_user SET role = 'RECRUITER' WHERE email = '$HR_EMAIL';
  SELECT email, role FROM t_user WHERE email IN ('$ADMIN_EMAIL', '$HR_EMAIL');
" 2>/dev/null

echo
echo "完成。注意：角色改动后需要重新登录，旧令牌里的 role 仍然是 USER。"
echo "  管理端：http://<host>:3100    $ADMIN_EMAIL / $ADMIN_PASSWORD"
echo "  HR 端：  http://<host>:3000    $HR_EMAIL / $HR_PASSWORD"
