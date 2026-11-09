#!/usr/bin/env bash
# 让「镜像上传」链路在当前集群可用（findings/167 §十七）。
#
# 上传链路与两个容易漏的配置点：
#
#   前端 POST /v1/image/upload
#     → Higress ingress「gaop-api-image-upload」(rewrite → /v1/image/upload)
#       → gaop-image-uploader:18080   （不是 gaop-be！gaop-be 没有 /upload）
#         → 宿主 docker（/run/docker.sock）docker load + tag + push
#           → 仓库地址由 env HARBOR_HOST 决定（默认值 192.168.1.171:1080，厂商旧环境）
#             且 docker 必须以 INSECURE 方式访问该仓库（sealos registry 是纯 HTTP）
#
# 本脚本确保两件事：
#   1) gaop-image-uploader 的 HARBOR_HOST 指向本集群 registry
#   2) 节点 /etc/docker/daemon.json 的 insecure-registries 含该地址，
#      并通过 SIGHUP 让 dockerd 重载（**不重启 docker**，避免杀掉所有容器）
#
# 用法：
#   tools/align-image-registry.sh [--dry-run] [registry地址]
#   默认 registry 地址：172.16.209.25:30180
#
# 注意：
#   - 第 2 步会起一个短暂的特权 Pod 挂宿主 /etc/docker，用完即删；
#     需要节点 docker 支持 SIGHUP 重载 insecure-registries（实测 Docker 26 可用）。
#   - 会先备份 /etc/docker/daemon.json 为 daemon.json.bak.<时间戳>。
set -euo pipefail

DRY_RUN=0
REG="172.16.209.25:30180"
NS="gaop-namespace"
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -*) echo "未知参数: $arg" >&2; exit 2 ;;
    *) REG="$arg" ;;
  esac
done

say() { printf '%s\n' "$*"; }

say "== 目标仓库: $REG"

# ---------- 1. 上传服务的 HARBOR_HOST ----------
CUR="$(kubectl get deploy gaop-image-uploader -n "$NS" \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="HARBOR_HOST")].value}' 2>/dev/null || true)"
say "当前 HARBOR_HOST: ${CUR:-<未设置，走镜像默认 192.168.1.171:1080>}"
if [ "$CUR" = "$REG" ]; then
  say "已指向目标仓库，跳过"
else
  if [ "$DRY_RUN" -eq 1 ]; then
    say "  [dry-run] patch deploy gaop-image-uploader HARBOR_HOST=$REG"
  else
    kubectl patch deploy gaop-image-uploader -n "$NS" --type=strategic -p \
      "{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"gaop-image-uploader\",\"env\":[{\"name\":\"HARBOR_HOST\",\"value\":\"$REG\"}]}]}}}}" >/dev/null
    kubectl rollout status deploy/gaop-image-uploader -n "$NS" --timeout=180s | tail -1
  fi
fi

# ---------- 2. 节点 docker 信任该仓库 ----------
say "== 检查节点 /etc/docker/daemon.json 的 insecure-registries"
if [ "$DRY_RUN" -eq 1 ]; then
  say "  [dry-run] 起特权 Pod 检查并在缺失时追加 \"$REG\" + SIGHUP dockerd"
  exit 0
fi

POD="docker-tune"
kubectl delete pod "$POD" -n "$NS" --force --grace-period=0 --wait=false >/dev/null 2>&1 || true
cat <<EOF | kubectl apply -f - >/dev/null
apiVersion: v1
kind: Pod
metadata:
  name: $POD
  namespace: $NS
spec:
  restartPolicy: Never
  hostPID: true
  containers:
    - name: tune
      image: docker.1ms.run/curlimages/curl:latest
      imagePullPolicy: IfNotPresent
      command: ["sleep", "1800"]
      securityContext:
        runAsUser: 0
        privileged: true
      volumeMounts:
        - name: dockercfg
          mountPath: /host-docker
        - name: sock
          mountPath: /run/docker.sock
  volumes:
    - name: dockercfg
      hostPath:
        path: /etc/docker
    - name: sock
      hostPath:
        path: /run/docker.sock
EOF
kubectl wait --for=condition=Ready pod/"$POD" -n "$NS" --timeout=120s >/dev/null
trap 'kubectl delete pod "$POD" -n "$NS" --force --grace-period=0 --wait=false >/dev/null 2>&1 || true' EXIT

if kubectl exec -n "$NS" "$POD" -- grep -q "\"$REG\"" /host-docker/daemon.json 2>/dev/null; then
  say "daemon.json 已包含 $REG，无需改动"
else
  say "追加 $REG 到 insecure-registries 并重载 dockerd"
  kubectl exec -n "$NS" "$POD" -- sh -c "
    set -e
    TS=\$(date +%Y%m%d-%H%M%S)
    cp /host-docker/daemon.json /host-docker/daemon.json.bak.\$TS
    sed -i 's|\"insecure-registries\": \\[|\"insecure-registries\": [\n    \"$REG\",|' /host-docker/daemon.json
    sed -i 's|\"insecure-registries\": \\[\n    \"$REG\",\\]|\"insecure-registries\": [\n    \"$REG\"\n  ]|' /host-docker/daemon.json
    echo '--- 结果 ---'; sed -n '/insecure-registries/,/]/p' /host-docker/daemon.json
    PID=''
    for p in /proc/[0-9]*; do [ \"\$(cat \$p/comm 2>/dev/null)\" = dockerd ] && PID=\${p#/proc/} && break; done
    [ -n \"\$PID\" ] && kill -HUP \$PID && echo \"SIGHUP -> dockerd(\$PID)\"
  "
  sleep 3
fi

say "== 复核（/info 里应能看到该 registry 的 IndexConfig）"
kubectl exec -n "$NS" "$POD" -- sh -c \
  "curl -s --unix-socket /run/docker.sock http://localhost/info | tr ',' '\n' | grep -c '$REG' || true"
say "  下一步：上传一个真实镜像验证（假 tar 会在 docker load 阶段失败，属正常）"
