#!/usr/bin/env bash
# 让 Loki 的配置源符合厂商后端的读取契约。
#
# 背景（findings/167 §十六）：
#   gaop-be 的 LokiApiService.getLogsAssertInfo() 会去读
#     Secret  loki/loki  的键 loki.yaml
#   读不到就抛 k8s.call.api.failed，被 catch 后把 lokiPodStatus 置为 false，
#   前端「资源概览」的日志存储容量/存储时长两个环就永久转圈（逻辑是
#   `!lokiPodStatus || deleteStatus ? spinning=true : false`）。
#
#   而手工 apply 的 Loki 清单用的是 ConfigMap loki-config/config.yml，
#   既没有 Secret，键名也不对 ⇒ 必然转圈。
#
# 本脚本做的事（幂等）：
#   1. 用 ConfigMap loki-config 的内容创建 Secret loki/loki，键名 loki.yaml
#   2. 把 StatefulSet 的 config 卷从 configMap 切到该 Secret，
#      并保持容器内文件名仍为 config.yml（不改启动参数 -config.file）
#
# 用法：
#   tools/align-loki-config-source.sh [--dry-run] [命名空间]
#
# 注意：
#   - 改 StatefulSet 会滚动重启 loki-0（约 30 秒），期间日志查询不可用；
#   - ConfigMap 仍保留作备份，但从此不再是生效配置，改它不会生效。
set -euo pipefail

DRY_RUN=0
NS="loki"
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -*) echo "未知参数: $arg" >&2; exit 2 ;;
    *) NS="$arg" ;;
  esac
done

CM_NAME="loki-config"
CM_KEY="config.yml"
SECRET_NAME="loki"
SECRET_KEY="loki.yaml"

say() { printf '%s\n' "$*"; }
run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    say "  [dry-run] $*"
  else
    say "  + $*"
    eval "$@"
  fi
}

say "== 目标命名空间: $NS"

# ---------- 1. Secret ----------
if kubectl get secret "$SECRET_NAME" -n "$NS" >/dev/null 2>&1; then
  if kubectl get secret "$SECRET_NAME" -n "$NS" -o jsonpath='{.data.loki\.yaml}' 2>/dev/null | grep -q .; then
    say "Secret $NS/$SECRET_NAME 已存在且含 ${SECRET_KEY}，跳过创建"
  else
    say "Secret $NS/$SECRET_NAME 存在但缺少键 $SECRET_KEY —— 需要人工确认，已中止" >&2
    exit 1
  fi
else
  if ! kubectl get cm "$CM_NAME" -n "$NS" >/dev/null 2>&1; then
    say "找不到 ConfigMap $NS/${CM_NAME}，无法生成 Secret —— 请确认 Loki 已部署" >&2
    exit 1
  fi
  TMP="$(mktemp -t loki-yaml-XXXXXX)"
  trap 'rm -f "$TMP"' EXIT
  kubectl get cm "$CM_NAME" -n "$NS" -o "jsonpath={.data.${CM_KEY//./\\.}}" > "$TMP"
  if ! grep -q '^limits_config:' "$TMP"; then
    say "取到的配置里没有 limits_config，后端解析保留天数会失败 —— 已中止" >&2
    exit 1
  fi
  say "准备创建 Secret $NS/${SECRET_NAME}（键 ${SECRET_KEY}，$(wc -c < "$TMP" | tr -d ' ') 字节）"
  if [ "$DRY_RUN" -eq 1 ]; then
    say "  [dry-run] kubectl create secret generic $SECRET_NAME -n $NS --from-file=$SECRET_KEY=<cm:$CM_NAME/$CM_KEY>"
  else
    kubectl create secret generic "$SECRET_NAME" -n "$NS" --from-file="$SECRET_KEY=$TMP" >/dev/null
    say "  + secret/$SECRET_NAME created"
  fi
fi

# ---------- 2. StatefulSet 挂载 ----------
CUR="$(kubectl get sts loki -n "$NS" -o jsonpath='{.spec.template.spec.volumes[?(@.name=="config")]}' 2>/dev/null || true)"
say "当前 config 卷: ${CUR:-<未找到>}"

if printf '%s' "$CUR" | grep -q "\"secretName\":\"$SECRET_NAME\""; then
  say "StatefulSet 已挂载 Secret ${SECRET_NAME}，无需改动"
else
  say "将 config 卷切到 Secret ${SECRET_NAME}（容器内仍为 config.yml）"
  run "kubectl patch sts loki -n $NS --type=strategic -p '{\"spec\":{\"template\":{\"spec\":{\"volumes\":[{\"name\":\"config\",\"configMap\":null,\"secret\":{\"secretName\":\"$SECRET_NAME\",\"items\":[{\"key\":\"$SECRET_KEY\",\"path\":\"$CM_KEY\"}]}}]}}}}' >/dev/null"
  if [ "$DRY_RUN" -eq 0 ]; then
    say "等待 loki 重建..."
    kubectl rollout status sts/loki -n "$NS" --timeout=180s | tail -1
  fi
fi

# ---------- 3. 复核 ----------
if [ "$DRY_RUN" -eq 0 ]; then
  say "== 复核"
  kubectl get secret "$SECRET_NAME" -n "$NS" -o jsonpath='  secret keys: {.data}' | head -c 120; echo
  kubectl get sts loki -n "$NS" -o jsonpath='  volume: {.spec.template.spec.volumes[?(@.name=="config")]}{"\n"}'
  say "  下一步：确认后端接口 GET /loki/logs/getLogsAssertInfo 返回 lokiPodStatus=true"
fi
