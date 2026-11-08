#!/usr/bin/env bash
# paas-core —— linux/arm64 镜像构建与导出
#
# 用法：
#   deploy/build-image.sh                 # mvn package + docker build + docker save
#   deploy/build-image.sh --skip-build    # 复用 target 里已有的 jar（气隙机常用）
#   deploy/build-image.sh --context-only  # 只组装构建上下文，不构建
#   deploy/build-image.sh --with-tests    # 打包时跑测试（默认跳过）
#   deploy/build-image.sh --push          # 构建后推 registry（需先 docker login）
#
# 环境变量：
#   TAG    镜像 tag（默认 paas-core:0.1.0）
#   OUT    导出路径（默认 artifacts/paas-core-arm64-<ver>.tar.gz）
#   CTX    构建上下文目录（默认 /tmp/paas-core-arm64-ctx）
#   MVN    maven 可执行文件（默认 mvn；本机是 /Users/csm/DevTools/apache-maven-3.9.9/bin/mvn）
#   OFFLINE=0  允许联网解析依赖（默认 1，走 mvn -o）
#
# 退出码：0 成功；非 0 失败。
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY="$REPO/deploy"

# maven 版本（含 -SNAPSHOT，用于定位 jar）与镜像版本（去掉 -SNAPSHOT，用于 tag）
# 注意：不能直接取 pom 里第一个 <version>——那是 parent（spring-boot 3.3.5）
JAR_VERSION="$(grep -A4 '<artifactId>paas-core</artifactId>' "$REPO/pom.xml" \
  | sed -n 's:.*<version>\(.*\)</version>.*:\1:p' | head -1)"
[ -n "$JAR_VERSION" ] || { echo "无法从 pom.xml 读出 paas-core 版本" >&2; exit 1; }
IMG_VERSION="${IMG_VERSION:-${JAR_VERSION%-SNAPSHOT}}"

TAG="${TAG:-paas-core:${IMG_VERSION}}"
TAG_ARM="${TAG}-arm64"
CTX="${CTX:-/tmp/paas-core-arm64-ctx}"
OUT="${OUT:-$REPO/artifacts/paas-core-arm64-${IMG_VERSION}.tar.gz}"
JAR="$REPO/paas-core-app/target/paas-core-app-${JAR_VERSION}.jar"
MVN="${MVN:-mvn}"
OFFLINE="${OFFLINE:-1}"

SKIP_BUILD=0; CONTEXT_ONLY=0; PUSH=0; WITH_TESTS=0
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build)   SKIP_BUILD=1; shift;;
    --context-only) CONTEXT_ONLY=1; shift;;
    --push)         PUSH=1; shift;;
    --with-tests)   WITH_TESTS=1; shift;;
    -h|--help)      sed -n '2,18p' "${BASH_SOURCE[0]}"; exit 0;;
    *) echo "未知参数: $1" >&2; exit 2;;
  esac
done

# 兼容 target 里被 rename 过的产物（spring-boot 会产出 .jar 与 .jar.original）
if [ ! -f "$JAR" ]; then
  CAND="$(ls "$REPO"/paas-core-app/target/paas-core-app-*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
  [ -n "$CAND" ] && JAR="$CAND"
fi

# ---- 1/4 打包 ----
if [ "$SKIP_BUILD" = 1 ]; then
  echo "== 1/4 跳过构建，使用现有 jar =="
  [ -f "$JAR" ] || { echo "找不到 jar：${JAR}，先不带 --skip-build 跑一次" >&2; exit 1; }
else
  echo "== 1/4 mvn package（offline=${OFFLINE}）=="
  MVN_FLAGS=(-q)
  [ "$OFFLINE" = 1 ] && MVN_FLAGS+=(-o)
  [ "$WITH_TESTS" = 1 ] || MVN_FLAGS+=(-DskipTests)
  ( cd "$REPO" && "$MVN" "${MVN_FLAGS[@]}" package )
fi
echo "   jar: $(basename "$JAR") $(du -h "$JAR" | cut -f1)"

# ---- 2/4 组装上下文 ----
echo "== 2/4 组装构建上下文 =="
rm -rf "$CTX"; mkdir -p "$CTX"
# Dockerfile 里 COPY 的固定名字，与构建产物解耦
cp "$JAR" "$CTX/paas-core-app.jar"
cp "$DEPLOY/Dockerfile" "$CTX/Dockerfile"
echo "   上下文: $CTX ($(du -sh "$CTX" | cut -f1))"

if [ "$CONTEXT_ONLY" = 1 ]; then
  echo "== 仅生成上下文，结束 =="; exit 0
fi

# ---- 3/4 docker build ----
echo "== 3/4 docker build =="
BUILD_ARGS=(
  -t "$TAG" -t "$TAG_ARM"
  --build-arg "APP_VERSION=$IMG_VERSION"
  --build-arg "VCS_REF=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)"
)
case "$(uname -m)" in
  arm64|aarch64) : ;;
  *) BUILD_ARGS+=(--platform linux/arm64) ;;   # 非 arm 宿主：QEMU 模拟，仅验证用
esac
docker build "${BUILD_ARGS[@]}" "$CTX"
docker image inspect "$TAG_ARM" --format '   架构: arch={{.Architecture}} os={{.Os}} size={{.Size}}'

# ---- 4/4 导出 ----
echo "== 4/4 导出 =="
mkdir -p "$(dirname "$OUT")"
docker save "$TAG" "$TAG_ARM" | gzip -1 > "$OUT"
echo "   产物: $OUT ($(du -h "$OUT" | cut -f1))"
( cd "$(dirname "$OUT")" && shasum -a 256 "$(basename "$OUT")" | tee "$(basename "$OUT").sha256" )

if [ "$PUSH" = 1 ]; then
  echo "== 推送 ${TAG_ARM} =="
  docker push "$TAG_ARM"
fi

echo "== 完成 =="
