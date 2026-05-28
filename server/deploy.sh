#!/usr/bin/env bash
# fkwk-push 服务端一键部署脚本
# 用法：在服务器上 git clone/上传本目录后，cd server && ./deploy.sh
set -euo pipefail

cd "$(dirname "$0")"

# 1. 检查 .env
if [[ ! -f .env ]]; then
  echo "❌ 未找到 .env，请先执行： cp .env.example .env  然后填写真实配置"
  exit 1
fi
# shellcheck disable=SC1091
source .env

# 2. 检查 docker
if ! command -v docker >/dev/null 2>&1; then
  echo "❌ 未安装 docker。Ubuntu 可执行： curl -fsSL https://get.docker.com | sh"
  exit 1
fi

echo "🚀 构建并启动 caddy + ntfy ..."
docker compose up -d --build

echo "⏳ 等待 ntfy 就绪 ..."
sleep 5

# 3. 创建管理员账号（发布端用）
echo "👤 创建 ntfy 用户：${NTFY_ADMIN_USER}"
docker compose exec -T ntfy sh -c "NTFY_PASSWORD='${NTFY_ADMIN_PASS}' ntfy user add --role=admin '${NTFY_ADMIN_USER}'" \
  || echo "ℹ️ 用户可能已存在，跳过"

echo "🎫 生成访问 token（Android 发布端填这个，比明文密码安全）..."
TOKEN=$(docker compose exec -T ntfy ntfy token add --label "android-publisher" "${NTFY_ADMIN_USER}" | grep -oE 'tk_[A-Za-z0-9]+' || true)

echo ""
echo "================ 部署完成 ================"
echo "订阅地址（iOS / Android ntfy app 填这个 Server URL）："
echo "  https://${NTFY_DOMAIN}:${PUBLIC_HTTPS_PORT}"
echo ""
echo "四个 topic（业务分流 + 心跳）："
echo "  - ${NTFY_DOMAIN%%.*}_urgent   紧急"
echo "  - ${NTFY_DOMAIN%%.*}_normal   普通"
echo "  - ${NTFY_DOMAIN%%.*}_low      低优先级"
echo "  - ${NTFY_DOMAIN%%.*}_heartbeat 心跳"
echo ""
echo "发布端 Token： ${TOKEN:-（生成失败，请手动执行 ntfy token add）}"
echo "用户名/密码： ${NTFY_ADMIN_USER} / （见 .env）"
echo "=========================================="
echo "提示：服务器防火墙或安全组需放行入站 TCP ${PUBLIC_HTTPS_PORT}"
