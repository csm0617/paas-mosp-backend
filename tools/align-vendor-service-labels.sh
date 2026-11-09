#!/bin/sh
# 把 gaop-namespace 的工作负载对齐到厂商「平台创建服务」的标签/注解约定。
#
# 为什么需要它（见 docs/findings/167 第十二节）：
#   厂商的 createService 会给 Deployment 写 5 个标签 + 1 个注解：
#     labels: app / name / system/appName / system/svcName / system/appType
#     annotations: gaop/serviceLabel
#   而我们的集群清单是手工 apply 的，只有 app 和 system/appName，于是：
#     · GET /v1/service/{name}  → 用 labelSelector `system/svcName=<name>` 查不到 → 400「服务不存在」
#     · GET /v1/service/gaop    → 读 pod 模板 annotations.get(...) → annotations 为 null → NPE
#
# 安全性：
#   · metadata.labels / metadata.annotations 的修改**不会**触发 Pod 重启；
#   · spec.template.metadata.annotations 的修改**会**触发滚动更新（用 --restart 才做）。
#
# 用法：
#   ./align-vendor-service-labels.sh                 # 只补 svcName（不重启，默认）
#   ./align-vendor-service-labels.sh --with-label    # 同时补 gaop/serviceLabel（会滚动重启）
#   ./align-vendor-service-labels.sh --dry-run       # 只看会改什么
set -eu

NS="${NS:-gaop-namespace}"
DRY=0
WITH_LABEL=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRY=1 ;;
    --with-label) WITH_LABEL=1 ;;
    *) echo "未知参数: $a"; exit 2 ;;
  esac
done

# 只处理平台自带服务；paas-web* 是我们自己的前端，不应对齐成厂商服务
SERVICES=$(kubectl get deploy -n "$NS" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
           | grep -E '^gaop-' || true)
[ -n "$SERVICES" ] || { echo "没找到 gaop-* Deployment"; exit 0; }

for d in $SERVICES; do
  cur=$(kubectl get deploy "$d" -n "$NS" -o jsonpath='{.metadata.labels.system/svcName}' 2>/dev/null || true)
  if [ "$cur" = "$d" ]; then
    echo "SKIP  $d  (system/svcName 已存在)"
  else
    echo "PATCH $d  metadata.labels.system/svcName=$d"
    [ "$DRY" = 1 ] || kubectl patch deploy "$d" -n "$NS" --type=merge \
      -p "{\"metadata\":{\"labels\":{\"system/svcName\":\"$d\"}}}" >/dev/null
  fi

  if [ "$WITH_LABEL" = 1 ]; then
    ann=$(kubectl get deploy "$d" -n "$NS" \
          -o jsonpath='{.spec.template.metadata.annotations.gaop/serviceLabel}' 2>/dev/null || true)
    if [ -n "$ann" ]; then
      echo "SKIP  $d  (gaop/serviceLabel 已存在)"
    else
      echo "PATCH $d  podTemplate.annotations.gaop/serviceLabel=$d  [会滚动重启]"
      [ "$DRY" = 1 ] || kubectl patch deploy "$d" -n "$NS" --type=strategic \
        -p "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"gaop/serviceLabel\":\"$d\"}}}}}" >/dev/null
      [ "$DRY" = 1 ] || kubectl rollout status "deploy/$d" -n "$NS" --timeout=180s | tail -1
    fi
  fi
done

echo "完成。校验（应全部有值）："
kubectl get deploy -n "$NS" -o jsonpath='{range .items[*]}{.metadata.name}{"  svcName="}{.metadata.labels.system/svcName}{"\n"}{end}' | grep '^gaop-'
