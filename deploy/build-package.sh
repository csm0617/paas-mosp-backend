#!/usr/bin/env bash
# paas-core 离线部署包 —— 打包
#
# 产物（一个自包含 tar.gz，目标机解包即可部署，全程不需要 registry / 不需要联网）：
#   paas-deploy-<ver>-arm64/
#   ├── README.md            目标机操作手册
#   ├── MANIFEST.txt         版本 / git / 镜像 sha256 / SQL 指纹 / 清单
#   ├── deploy.sh            编排入口（preflight/install/switch/verify/down）
#   ├── k8s/                 参数化清单（占位符 __NS__ 等）
#   ├── sql/                 87 个迁移 + seed（灌库用）
#   └── images/
#       ├── paas-core-arm64-<ver>.tar.gz
#       ├── paas-web-arm64-<webver>.tar.gz
#       └── mysql-8.0.32-arm64.tar.gz     （灌 schema 的客户端，--no-db-image 可省）
#
# 用法：
#   deploy/build-package.sh                    # 构建后端镜像 + 复用前端镜像 + 打包 + 自检
#   deploy/build-package.sh --no-build         # 不重新构建，用现有 tar
#   deploy/build-package.sh --no-db-image      # 不带 mysql 镜像（现场自带时）
#   deploy/build-package.sh --skip-verify      # 跳过解包自检
#   deploy/build-package.sh --out /path/x.tar.gz
#
# 环境变量：
#   WEB_REPO=../paas-web        前端仓路径
#   BACKEND_TAR=...             指定后端镜像 tar（跳过构建）
#   FRONTEND_TAR=...            指定前端镜像 tar（跳过构建）
#   MYSQL_IMAGE=mysql:8.0.32
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY="$REPO/deploy"
# 不用 ${VAR:-$(...)} 的嵌套写法：macOS 自带 bash 3.2 在 set -u 下会解析错误
if [ -z "${WEB_REPO:-}" ]; then
  WEB_REPO="$(cd "$REPO/../paas-web" 2>/dev/null && pwd || true)"
fi

IMG_VER="$(grep -A4 '<artifactId>paas-core</artifactId>' "$REPO/pom.xml" \
  | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' | head -1)"
IMG_VER="${IMG_VER%-SNAPSHOT}"
WEB_VER="$(node -p "require('$WEB_REPO/package.json').version" 2>/dev/null || echo 0.0.0)"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0.32}"

PKG="paas-deploy-${IMG_VER}-arm64"
OUT="${OUT:-$REPO/artifacts/${PKG}.tar.gz}"
BACKEND_TAR="${BACKEND_TAR:-$REPO/artifacts/paas-core-arm64-${IMG_VER}.tar.gz}"
# 同上：避免 ${A:-${B:+...}} 的嵌套展开（bash 3.2 会报 unbound variable）
if [ -z "${FRONTEND_TAR:-}" ] && [ -n "$WEB_REPO" ]; then
  FRONTEND_TAR="$WEB_REPO/artifacts/paas-web-arm64-${WEB_VER}.tar.gz"
fi

NO_BUILD=0; WITH_DB_IMAGE=1; VERIFY=1
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build)     NO_BUILD=1; shift;;
    --no-db-image)  WITH_DB_IMAGE=0; shift;;
    --skip-verify)  VERIFY=0; shift;;
    --out)          OUT="$2"; shift 2;;
    -h|--help)      sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0;;
    *) echo "未知参数: $1" >&2; exit 2;;
  esac
done

WORK="$(mktemp -d /tmp/paas-pkg.XXXXXX)"
ROOT="$WORK/$PKG"
mkdir -p "$ROOT/images" "$ROOT/k8s" "$ROOT/sql"
trap 'rm -rf "$WORK"' EXIT

log() { printf '\033[36m==\033[0m %s\n' "$*"; }

# ── 1/5 镜像 ────────────────────────────────────────────────────────────────
log "1/5 收集镜像"
if [ "$NO_BUILD" = 0 ]; then
  log "构建后端镜像"
  "$DEPLOY/build-image.sh"
fi
[ -f "$BACKEND_TAR" ] || { echo "缺后端镜像包：${BACKEND_TAR}（先跑 deploy/build-image.sh）" >&2; exit 1; }
cp "$BACKEND_TAR" "$ROOT/images/"
echo "   后端：$(basename "$BACKEND_TAR") $(du -h "$BACKEND_TAR" | cut -f1)"

if [ ! -f "$FRONTEND_TAR" ]; then
  if [ "$NO_BUILD" = 0 ] && [ -n "$WEB_REPO" ]; then
    log "构建前端镜像（${WEB_REPO}）"
    ( cd "$WEB_REPO" && TAG="paas-web:${WEB_VER}" OUT="$FRONTEND_TAR" ./deploy/build-image.sh )
  fi
fi
[ -f "$FRONTEND_TAR" ] || { echo "缺前端镜像包：${FRONTEND_TAR}（设 FRONTEND_TAR= 指定）" >&2; exit 1; }
cp "$FRONTEND_TAR" "$ROOT/images/"
echo "   前端：$(basename "$FRONTEND_TAR") $(du -h "$FRONTEND_TAR" | cut -f1)"

if [ "$WITH_DB_IMAGE" = 1 ]; then
  if ! docker image inspect "$MYSQL_IMAGE" >/dev/null 2>&1; then
    echo "   本机没有 ${MYSQL_IMAGE}，尝试拉取（气隙环境请先在别处导出后拷入）"
    docker pull "$MYSQL_IMAGE"
  fi
  docker save "$MYSQL_IMAGE" | gzip -1 > "$ROOT/images/$(echo "$MYSQL_IMAGE" | tr '/:' '--')-arm64.tar.gz"
  echo "   数据库客户端：$(du -h "$ROOT/images/"*mysql* | cut -f1)"
else
  echo "   跳过 mysql 镜像（--no-db-image）：现场需自备 $MYSQL_IMAGE"
fi

# ── 2/5 脚本与清单 ──────────────────────────────────────────────────────────
log "2/5 收集脚本、清单与 SQL"
cp "$DEPLOY/deploy.sh" "$DEPLOY/Dockerfile" "$ROOT/"
cp "$DEPLOY"/k8s/*.yaml "$ROOT/k8s/"
cp -r "$DEPLOY/sql/." "$ROOT/sql/"
chmod +x "$ROOT/deploy.sh"
echo "   k8s 清单：$(ls "$ROOT/k8s" | wc -l | tr -d ' ') 个；SQL：$(ls "$ROOT/sql/migration"/*.sql | wc -l | tr -d ' ') 个迁移"

# ── 3/5 说明与清单 ──────────────────────────────────────────────────────────
log "3/5 生成 README 与 MANIFEST"
cp "$DEPLOY/README-OFFLINE.md" "$ROOT/README.md" 2>/dev/null || true

# 镜像 tag 在这里固化：deploy.sh 会 source 本文件，避免「包内镜像版本」与「清单默认值」漂移。
# 用 := 形式，现场显式设置的 BACKEND_IMAGE/FRONTEND_IMAGE 仍然优先。
cat > "$ROOT/package.env" <<EOF
# 由 build-package.sh 生成，勿手改
: "\${BACKEND_IMAGE:=paas-core:${IMG_VER}-arm64}"
: "\${FRONTEND_IMAGE:=paas-web:${WEB_VER}-arm64}"
EOF
echo "   package.env：后端 paas-core:${IMG_VER}-arm64 / 前端 paas-web:${WEB_VER}-arm64"

SQL_FP="$(cd "$ROOT/sql/migration" && shasum -a 256 *.sql | shasum -a 256 | cut -d' ' -f1)"
{
  echo "# paas-deploy 离线包清单"
  echo "package:      $PKG"
  echo "arch:         linux/arm64（含后端/前端/数据库客户端三个镜像）"
  echo "built_at:     $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "built_on:     $(uname -sm) · docker $(docker version --format '{{.Server.Version}}' 2>/dev/null || echo n/a)"
  echo "backend:      paas-core:${IMG_VER}-arm64   git $(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)  dirty=$(git -C "$REPO" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
  echo "frontend:     paas-web:${WEB_VER}-arm64    git $(git -C "$WEB_REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "mysql-client: ${MYSQL_IMAGE}"
  echo "sql:          87 个迁移，目录指纹 $SQL_FP"
  echo "seed:         admin / admin123（测试口令，投产前必须改）"
  echo
  echo "## 镜像包 sha256"
  ( cd "$ROOT/images" && shasum -a 256 *.tar.gz | sed 's/^/  /' )
  echo
  echo "## k8s 清单"
  ls -1 "$ROOT/k8s" | sed 's/^/  /'
} > "$ROOT/MANIFEST.txt"

# ── 4/5 打包 ────────────────────────────────────────────────────────────────
log "4/5 打包"
( cd "$ROOT" && shasum -a 256 images/*.tar.gz > SHA256SUMS )
mkdir -p "$(dirname "$OUT")"
tar czf "$OUT" -C "$WORK" "$PKG"
echo "   产物：$OUT ($(du -h "$OUT" | cut -f1))"
( cd "$(dirname "$OUT")" && shasum -a 256 "$(basename "$OUT")" | tee "$(basename "$OUT").sha256" )

# ── 5/5 自检 ────────────────────────────────────────────────────────────────
if [ "$VERIFY" = 1 ]; then
  log "5/5 自检（解包 → 脚本语法 → 清单渲染 → 镜像包完整性）"
  CHK="$WORK/verify"; mkdir -p "$CHK"
  tar xzf "$OUT" -C "$CHK"
  R="$CHK/$PKG"

  bash -n "$R/deploy.sh" && echo "   deploy.sh 语法 OK"

  # 用示例值渲染一遍：任何 __XXX__ 残留都会让 render 以非 0 退出
  NS=verify-ns BACKEND_IMAGE="paas-core:${IMG_VER}-arm64" FRONTEND_IMAGE="paas-web:${WEB_VER}-arm64" \
    bash "$R/deploy.sh" render "$CHK/rendered" >/dev/null
  echo "   清单渲染 OK（$(ls "$CHK/rendered" | wc -l | tr -d ' ') 个文件，无占位符残留）"

  for t in "$R"/images/*.tar.gz; do
    tar tzf "$t" >/dev/null || { echo "镜像包损坏：$t" >&2; exit 1; }
  done
  echo "   镜像包可解：$(ls "$R/images" | wc -l | tr -d ' ') 个"

  ( cd "$R" && shasum -a 256 -c SHA256SUMS >/dev/null ) && echo "   SHA256SUMS 校验通过"
  echo "   README 行数：$(wc -l < "$R/README.md" 2>/dev/null || echo 0)"
fi

log "完成"
