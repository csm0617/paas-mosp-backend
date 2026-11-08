#!/usr/bin/env bash
# paas-core 部署包 —— 目标机编排
#
# 设计立场：**替换式部署**。自研前后端与厂商对象并存，切流只改 Ingress 的 backend，
# 回退改回去即可（ADR-0005 / paas-web deploy/CUTOVER.md）。本脚本不删任何厂商对象、
# 不重建已有数据库、不改厂商 Deployment。
#
#   deploy.sh preflight                  检查 kubectl / 命名空间 / 节点架构 / 依赖 Service / 镜像
#   deploy.sh install [选项]             部署 paas-core + paas-web 并等待就绪
#   deploy.sh schema                     只灌数据库结构与账号（空库首次用）
#   deploy.sh seed                       只播种最小可登录数据
#   deploy.sh status                     状态总览 + 当前入口指向
#   deploy.sh verify                     冒烟：健康、前端静态资源、后端接口
#   deploy.sh switch paas|vendor [范围]  切流（范围：all|web|api，默认 all）
#   deploy.sh down                       删除本包对象（会先要求回到 vendor）
#
# install 选项：
#   --with-schema   顺带建库+灌 87 个迁移（空库才需要）
#   --with-seed     顺带播种 admin 账号
#   --with-redis    顺带起一个 paas-redis（现场没有 Redis 时）
#   --with-ingress  顺带创建本包入口（现场没有厂商 ingress 时；否则用 switch）
#   --skip-wait     不等 rollout（只看 apply 结果）
#
# 关键环境变量（都有默认值，按现场覆盖）：
#   NS=gaop-namespace            命名空间
#   BACKEND_IMAGE=paas-core:1.0.0-arm64
#   FRONTEND_IMAGE=paas-web:0.1.10-arm64
#   MYSQL_HOST=gaop-mysql        MySQL Service 名或 ExternalName
#   MYSQL_PORT=3306
#   DB_NAME=gaop  APP_USER=gaop
#   MYSQL_PASSWORD=（不传则自动生成并写进 Secret paas-db；已存在则复用）
#   MYSQL_ROOT_PASSWORD=（可选；给了才会执行「建库+建用户」，否则假定已存在）
#   REDIS_HOST=gaop-redis  REDIS_PORT=6379
#   GATEWAY_HOST=gaop-gateway-auth  GATEWAY_PORT=5000
#   GIS_URL=gis-server:8080
#   INGRESS_CLASS=higress
#   MYSQL_IMAGE=mysql:8.0.32
#   RBAC=scoped|admin            scoped=受限 ClusterRole（默认），admin=cluster-admin（等价厂商）
#   GW=172.16.209.170            verify 时测的网关地址（可空，为空跳过网关检查）
#
# 退出码：0 成功；1 失败；2 参数错误。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S="$HERE/k8s"
SQLDIR="$HERE/sql"

# 离线包根目录下的 package.env 记录本包镜像的真实 tag（由 build-package.sh 生成）。
# 用 `: "${VAR:=...}"` 写法，已显式设置的环境变量优先，不会被包内默认值覆盖。
if [ -f "$HERE/package.env" ]; then
  # shellcheck disable=SC1091
  . "$HERE/package.env"
fi

NS="${NS:-gaop-namespace}"
BACKEND_IMAGE="${BACKEND_IMAGE:-paas-core:1.0.0-arm64}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-paas-web:0.1.12-arm64}"
MYSQL_HOST="${MYSQL_HOST:-gaop-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
DB_NAME="${DB_NAME:-gaop}"
APP_USER="${APP_USER:-gaop}"
REDIS_HOST="${REDIS_HOST:-gaop-redis}"
REDIS_PORT="${REDIS_PORT:-6379}"
GATEWAY_HOST="${GATEWAY_HOST:-gaop-gateway-auth}"
GATEWAY_PORT="${GATEWAY_PORT:-5000}"
GIS_URL="${GIS_URL:-gis-server:8080}"
INGRESS_CLASS="${INGRESS_CLASS:-higress}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0.32}"
RBAC="${RBAC:-scoped}"
GW="${GW:-}"

case "$RBAC" in
  scoped) RBAC_ROLE="paas-core-manager" ;;
  admin)  RBAC_ROLE="cluster-admin" ;;
  *) echo "RBAC 只支持 scoped|admin" >&2; exit 2 ;;
esac

WORK="$(mktemp -d /tmp/paas-deploy.XXXXXX)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

# ── 输出 ────────────────────────────────────────────────────────────────────
log()  { printf '\033[36m==\033[0m %s\n' "$*"; }
ok()   { printf '   \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '   \033[33m!\033[0m %s\n' "$*"; }
die()  { printf '\033[31m!!\033[0m %s\n' "$*" >&2; exit 1; }

# ── 清单渲染 ────────────────────────────────────────────────────────────────
render() {
  local src="$1" dst="$2"
  sed -e "s|__NS__|$NS|g" \
      -e "s|__RBAC_ROLE__|$RBAC_ROLE|g" \
      -e "s|__BACKEND_IMAGE__|$BACKEND_IMAGE|g" \
      -e "s|__FRONTEND_IMAGE__|$FRONTEND_IMAGE|g" \
      -e "s|__MYSQL_IMAGE__|$MYSQL_IMAGE|g" \
      -e "s|__MYSQL_HOST__|$MYSQL_HOST|g" \
      -e "s|__MYSQL_PORT__|$MYSQL_PORT|g" \
      -e "s|__DB_NAME__|$DB_NAME|g" \
      -e "s|__APP_USER__|$APP_USER|g" \
      -e "s|__REDIS_HOST__|$REDIS_HOST|g" \
      -e "s|__REDIS_PORT__|$REDIS_PORT|g" \
      -e "s|__GATEWAY_HOST__|$GATEWAY_HOST|g" \
      -e "s|__GATEWAY_PORT__|$GATEWAY_PORT|g" \
      -e "s|__GIS_URL__|$GIS_URL|g" \
      -e "s|__INGRESS_CLASS__|$INGRESS_CLASS|g" \
      "$src" > "$dst"
  # 渲染后不允许残留占位符：漏一个就是 apply 出一个字面量 __XXX__ 的对象
  if grep -nE '__[A-Z0-9_]+__' "$dst" >/dev/null; then
    echo "!! $src 渲染后仍有占位符：" >&2
    grep -nE '__[A-Z0-9_]+__' "$dst" >&2
    exit 1
  fi
}

render_all() {
  for f in "$K8S"/*.yaml; do
    render "$f" "$WORK/$(basename "$f")"
  done
}

k() { kubectl -n "$NS" "$@"; }

ensure_ns_or_die() {
  kubectl get ns "$NS" >/dev/null 2>&1 || die "命名空间 $NS 不存在；先跑 'deploy.sh install'"
}

# ── Secret ──────────────────────────────────────────────────────────────────
ensure_db_secret() {
  if k get secret paas-db >/dev/null 2>&1; then
    ok "复用已有 Secret paas-db（不覆盖口令）"
    return
  fi
  local pw="${MYSQL_PASSWORD:-}"
  if [ -z "$pw" ]; then
    pw="$(head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | head -c 24)"
    warn "未提供 MYSQL_PASSWORD，已生成随机口令并写入 Secret paas-db"
  fi
  local args=(--from-literal=MYSQL_PASSWORD="$pw")
  # root 口令只在提供时写入；不提供则 Job 跳过建库步骤
  if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
    args+=(--from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD")
  fi
  k create secret generic paas-db "${args[@]}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  ok "Secret paas-db 已创建"
  warn "应用账号口令${MYSQL_PASSWORD:+由 MYSQL_PASSWORD 指定}${MYSQL_PASSWORD:-为随机生成}；请自行同步到 MySQL 侧（或用 --with-schema 让 Job 建）"
}

# ── 前置检查 ────────────────────────────────────────────────────────────────
preflight() {
  log "1/5 工具与集群"
  command -v kubectl >/dev/null || die "没有 kubectl"
  kubectl cluster-info >/dev/null 2>&1 || die "kubectl 连不上集群（检查 KUBECONFIG）"
  ok "kubectl 可用：$(kubectl config current-context)"

  log "2/5 命名空间与节点架构"
  if kubectl get ns "$NS" >/dev/null 2>&1; then
    ok "命名空间 $NS 存在"
  else
    warn "命名空间 $NS 不存在（install 会创建；若现场用别的名字请设 NS=）"
  fi
  local arch
  arch="$(kubectl get node -o jsonpath='{.items[0].status.nodeInfo.architecture}' 2>/dev/null || true)"
  if [ "$arch" = "arm64" ]; then
    ok "节点架构 arm64"
  else
    warn "节点架构是 '${arch:-未知}'，而本包镜像是 linux/arm64 —— 会 exec format error"
  fi

  log "3/5 依赖 Service 可达性"
  for host in "$MYSQL_HOST" "$REDIS_HOST"; do
    if k get svc "$host" >/dev/null 2>&1; then
      ok "$host 是本命名空间内的 Service"
    else
      warn "$host 在 $NS 里找不到同名 Service（外部地址可以忽略这条）"
    fi
  done

  log "4/5 镜像是否已导入"
  if command -v docker >/dev/null 2>&1; then
    for img in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
      if docker image inspect "$img" >/dev/null 2>&1; then ok "$img 已在本机"; else warn "$img 本机没有（先 docker load）"; fi
    done
  else
    warn "目标机没有 docker，跳过镜像检查；请确认容器运行时里已有这两个 tag"
  fi

  log "5/5 现有入口"
  for ing in gaop-web gaop-api-be; do
    if k get ingress "$ing" >/dev/null 2>&1; then
      ok "Ingress $ing → $(ingress_backend "$ing")"
    else
      warn "Ingress $ing 不存在（空环境请 install --with-ingress）"
    fi
  done
}

# ── Ingress 指向 ────────────────────────────────────────────────────────────
ingress_backend() {
  local name="$1" idx
  idx="$(k get ingress "$name" -o jsonpath='{range .spec.rules[0].http.paths[*]}{.backend.service.name}{"\n"}{end}' 2>/dev/null | head -1)"
  echo "${idx:-<无>}"
}

switch_one() {
  local ing="$1" svc="$2" pattern="$3"
  if ! k get ingress "$ing" >/dev/null 2>&1; then
    die "Ingress $ing 不存在；空环境请先 install --with-ingress"
  fi
  # 按 path 文本定位索引，不假设一定在 paths[0]
  local line idx
  line="$(k get ingress "$ing" -o jsonpath='{range .spec.rules[0].http.paths[*]}{.path}{"\n"}{end}' | grep -nE "$pattern" | head -1 || true)"
  [ -n "$line" ] || die "在 Ingress $ing 里找不到匹配 $pattern 的 path"
  idx=$(( $(echo "$line" | cut -d: -f1) - 1 ))
  local cur
  cur="$(k get ingress "$ing" -o jsonpath="{.spec.rules[0].http.paths[$idx].backend.service.name}")"
  if [ "$cur" = "$svc" ]; then
    ok "$ing 已经指向 ${svc}（无需改动）"
    return
  fi
  k patch ingress "$ing" --type=json \
    -p="[{\"op\":\"replace\",\"path\":\"/spec/rules/0/http/paths/$idx/backend/service/name\",\"value\":\"$svc\"}]" >/dev/null
  ok "${ing}：$cur → $svc"
}

# 切流：目标服务名与要改的 ingress 分开列，避免把 pattern 当成 service 名
switch_flow() {
  local target="$1" scope="$2"
  local web api
  case "$target" in
    paas)   web="paas-web";  api="paas-core" ;;
    vendor) web="gaop-web";  api="gaop-be" ;;
    *) die "用法：deploy.sh switch paas|vendor [all|web|api]" ;;
  esac
  log "切流 → ${target}（范围 ${scope}）"
  case "$scope" in
    all)
      switch_one gaop-web "$web" '^/gaop'
      switch_one gaop-api-be "$api" '^/api/gaop'
      ;;
    web) switch_one gaop-web "$web" '^/gaop' ;;
    api) switch_one gaop-api-be "$api" '^/api/gaop' ;;
    *) die "范围只支持 all|web|api" ;;
  esac
  log "当前指向"
  for ing in gaop-web gaop-api-be; do
    k get ingress "$ing" >/dev/null 2>&1 && printf '   %-14s → %s\n' "$ing" "$(ingress_backend "$ing")"
  done
}

# ── 数据库 ──────────────────────────────────────────────────────────────────
run_schema() {
  log "建库 / 建用户 / 灌 schema"
  k create configmap paas-schema-sql --from-file="$SQLDIR/migration" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  ok "ConfigMap paas-schema-sql（$(ls "$SQLDIR/migration"/*.sql | wc -l | tr -d ' ') 个迁移）"
  k delete job paas-schema --ignore-not-found >/dev/null
  kubectl apply -f "$WORK/15-schema-job.yaml" >/dev/null
  if k wait --for=condition=complete job/paas-schema --timeout=900s >/dev/null 2>&1; then
    k logs job/paas-schema --tail=6
    ok "schema 完成"
  else
    k logs job/paas-schema --tail=60 || true
    die "schema Job 未完成（日志见上）"
  fi
}

run_seed() {
  log "播种最小可登录数据"
  k create configmap paas-seed-sql --from-file="seed.sql=$SQLDIR/seed.sql" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  k delete job paas-seed --ignore-not-found >/dev/null
  kubectl apply -f "$WORK/16-seed-job.yaml" >/dev/null
  if k wait --for=condition=complete job/paas-seed --timeout=300s >/dev/null 2>&1; then
    k logs job/paas-seed --tail=8
    ok "seed 完成（admin / admin123，投产前务必改）"
  else
    k logs job/paas-seed --tail=40 || true
    die "seed Job 未完成（日志见上）"
  fi
}

# ── install ─────────────────────────────────────────────────────────────────
cmd_install() {
  local with_schema=0 with_seed=0 with_redis=0 with_ingress=0 skip_wait=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --with-schema)  with_schema=1; shift;;
      --with-seed)    with_seed=1; shift;;
      --with-redis)   with_redis=1; shift;;
      --with-ingress) with_ingress=1; shift;;
      --skip-wait)    skip_wait=1; shift;;
      *) die "未知参数: $1" ;;
    esac
  done

  render_all
  preflight

  if ! kubectl get ns "$NS" >/dev/null 2>&1; then
    log "创建命名空间 $NS"
  fi
  kubectl apply -f "$WORK/10-foundation.yaml" >/dev/null
  ok "SA paas-core + ClusterRole($RBAC_ROLE) 就绪"

  ensure_db_secret
  [ "$with_schema" = 1 ] && run_schema
  [ "$with_seed" = 1 ] && run_seed
  if [ "$with_redis" = 1 ]; then
    kubectl apply -f "$WORK/11-redis-optional.yaml" >/dev/null
    k rollout status deploy/paas-redis --timeout=120s >/dev/null && ok "paas-redis 就绪"
  fi

  log "应用层"
  kubectl apply -f "$WORK/20-backend.yaml" >/dev/null
  kubectl apply -f "$WORK/30-frontend.yaml" >/dev/null
  ok "paas-core / paas-web 已 apply"

  if [ "$skip_wait" = 0 ]; then
    log "等待就绪"
    if ! k rollout status deploy/paas-core --timeout=600s; then
      k describe pod -l app=paas-core | tail -30 || true
      k logs deploy/paas-core --tail=60 || true
      die "paas-core 未就绪"
    fi
    k rollout status deploy/paas-web --timeout=180s
  fi

  if [ "$with_ingress" = 1 ]; then
    kubectl apply -f "$WORK/40-ingress.yaml" >/dev/null
    ok "本包入口已创建（paas-web / paas-api / paas-ws）"
  else
    warn "未创建入口：用 'deploy.sh switch paas' 把现有 ingress 指过来"
  fi

  cmd_status
}

# ── status ──────────────────────────────────────────────────────────────────
cmd_status() {
  log "部署状态（${NS}）"
  k get deploy -l app.kubernetes.io/part-of=msop -o custom-columns=\
'NAME:.metadata.name,READY:.status.readyReplicas,DESIRED:.spec.replicas,IMAGE:.spec.template.spec.containers[0].image' 2>/dev/null || true
  echo
  k get pods -l app.kubernetes.io/part-of=msop -o wide --no-headers 2>/dev/null || true
  echo
  log "入口指向"
  for ing in gaop-web gaop-api-be paas-web paas-api; do
    if k get ingress "$ing" >/dev/null 2>&1; then
      printf '   %-14s → %s\n' "$ing" "$(ingress_backend "$ing")"
    fi
  done
  echo
  log "健康"
  if k get deploy paas-core >/dev/null 2>&1; then
    printf '   paas-core /actuator/health：'
    k exec deploy/paas-core -- curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health 2>/dev/null || echo "(取不到)"
  fi
}

# ── verify ──────────────────────────────────────────────────────────────────
cmd_verify() {
  log "冒烟检查"
  local failn=0
  check() { # 名称 期望 实际
    if [ "$2" = "$3" ]; then ok "$1：$3"; else warn "$1：期望 $2，实际 $3"; failn=$((failn+1)); fi
  }

  local h
  h="$(k exec deploy/paas-core -- curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health 2>/dev/null || echo "NA")"
  case "$h" in *'"status":"UP"'*) ok "后端健康：UP" ;; *) warn "后端健康：$h"; failn=$((failn+1)) ;; esac

  local web
  web="$(k exec deploy/paas-core -- curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://paas-web/index.html 2>/dev/null || echo 000)"
  check "前端 /index.html" 200 "$web"

  local api
  # /v1/role?username=admin 是登录页下拉依赖的**公开**端点（未登录即 200），
  # 拿它当探针比 /v1/app 可靠：后者在自研实现里没有对应路由，会稳定 404
  api="$(k exec deploy/paas-core -- curl -s -o /dev/null -w '%{http_code}' --max-time 8 'http://127.0.0.1:8080/v1/role?username=admin' 2>/dev/null || echo 000)"
  check "后端 /v1/role?username=admin" 200 "$api"

  if [ -n "$GW" ]; then
    local gw
    gw="$(k exec deploy/paas-core -- curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://$GW/gaop/" 2>/dev/null || echo 000)"
    check "网关 /gaop/" 200 "$gw"
    local gapi
    gapi="$(k exec deploy/paas-core -- curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://$GW/api/gaop/v1/role?username=admin" 2>/dev/null || echo 000)"
    check "网关 /api/gaop/v1/role" 200 "$gapi"
  else
    warn "未设 GW，跳过网关检查"
  fi

  [ "$failn" -eq 0 ] && { log "全部通过"; } || die "$failn 项未通过"
}

# ── down ────────────────────────────────────────────────────────────────────
cmd_down() {
  local force=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --force) force=1; shift;;
      *) die "未知参数: $1" ;;
    esac
  done
  render_all

  # 安全闸：入口还指着 paas-* 就删服务 = 白屏。必须先把流量切回厂商。
  local cur
  cur="$(ingress_backend gaop-web 2>/dev/null || echo '')"
  if [ "$cur" = "paas-web" ] && [ "$force" = 0 ]; then
    die "Ingress gaop-web 仍指向 paas-web，先执行：deploy.sh switch vendor"
  fi

  log "删除本包对象（保留命名空间、数据库、厂商对象）"
  kubectl delete -f "$WORK/40-ingress.yaml" --ignore-not-found >/dev/null 2>&1 || true
  k delete deploy paas-web paas-core --ignore-not-found >/dev/null
  k delete svc paas-web paas-core --ignore-not-found >/dev/null
  k delete job paas-schema paas-seed --ignore-not-found >/dev/null
  k delete configmap paas-schema-sql paas-seed-sql --ignore-not-found >/dev/null
  k delete deploy paas-redis --ignore-not-found >/dev/null 2>&1 || true
  k delete svc paas-redis --ignore-not-found >/dev/null 2>&1 || true
  warn "Secret paas-db 与数据库数据未删除（可能仍被厂商后端使用）；确需清理请手动删"
  ok "完成"
}

# ── render（给打包自检用：只渲染清单，不碰集群）──────────────────────────────
cmd_render() {
  local out="${1:-}"
  [ -n "$out" ] || die "用法：deploy.sh render <输出目录>"
  mkdir -p "$out"
  for f in "$K8S"/*.yaml; do
    render "$f" "$out/$(basename "$f")"
  done
  echo "$out"
}

# ── 入口 ────────────────────────────────────────────────────────────────────
cmd="${1:-}"; shift || true
case "$cmd" in
  preflight) render_all; preflight ;;
  install)   cmd_install "$@" ;;
  schema)    render_all; ensure_ns_or_die; ensure_db_secret; run_schema ;;
  seed)      render_all; ensure_ns_or_die; ensure_db_secret; run_seed ;;
  status)    cmd_status ;;
  verify)    cmd_verify ;;
  switch)    switch_flow "${1:-}" "${2:-all}" ;;
  down)      cmd_down "$@" ;;
  render)    cmd_render "${1:-}" ;;
  ""|-h|--help|help) sed -n '2,40p' "${BASH_SOURCE[0]}" ;;
  *) echo "未知命令: $cmd" >&2; sed -n '2,40p' "${BASH_SOURCE[0]}" >&2; exit 2 ;;
esac
