#!/usr/bin/env bash
# 让「镜像上传 / 文件上传」在当前集群真正可用（findings/167 §十七、§十八）。
#
# 涉及的四个配置点，缺任何一个都会表现为「接口不通」：
#
#   ① 前端直连端口：vendor 前端把地址硬编码成 http://<当前hostname>:19528（上传）
#      和 :19527（fdfs），不走 /api/gaop 网关。用户从 VIP 访问时，VIP 上必须能通这两个端口
#      ⇒ 用 externalIPs 把 VIP:19528 / VIP:19527 映射到两个 Service
#         （IPVS 模式下 externalIPs 映射 Service 的 port，不是 nodePort）
#   ② 上传服务地址：gaop-image-uploader 的 HARBOR_HOST 必须指向本集群 registry
#      （镜像默认值是厂商旧环境 192.168.1.171:1080）
#   ③ 后端地址：gaop-be 的 HARBOR_HOST 同理，否则镜像列表/详情调 registry 也失败
#   ④ 节点信任：registry 是纯 HTTP，dockerd 默认用 HTTPS ⇒ 必须列入 insecure-registries
#      并 SIGHUP 热重载（**不能 restart docker**：live-restore=false 会停掉节点所有容器）
#
# 用法：
#   tools/align-image-registry.sh [--dry-run]
#
# 注意：
#   - registry 地址与端口**动态探测**（节点 InternalIP + docker-registry 的 nodePort），
#     不写死，避免 sealos 重建 Service 换了 nodePort 后失效；
#   - 第 ④ 步会起一个临时特权 Pod 挂宿主 /etc/docker，改完即删，并先备份 daemon.json。
set -euo pipefail

DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -*) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

NS="gaop-namespace"
say() { printf '%s\n' "$*"; }

# ---------- 探测 ----------
NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')"
REG_PORT="$(kubectl get svc docker-registry -n kube-system -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || true)"
VIP="$(kubectl get svc higress-gateway -n higress-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
REG="${NODE_IP}:${REG_PORT}"

say "节点 InternalIP : ${NODE_IP:-<未知>}"
say "仓库 nodePort   : ${REG_PORT:-<未找到 docker-registry>}"
say "网关 VIP        : ${VIP:-<无>}"
say "推导出的 registry: $REG"
[ -n "$REG_PORT" ] || { echo "找不到 kube-system/docker-registry 的 nodePort，已中止" >&2; exit 1; }

# ---------- ① 前端直连端口 ----------
say ""
say "== ① VIP 直连端口 19528 / 19527"
MANIFEST="$(dirname "$0")/../deploy/cluster/frontend-direct-ports.yaml"
if [ -n "$VIP" ] && [ "$VIP" != "172.16.209.170" ]; then
  say "  注意：清单里 externalIPs 写的是 172.16.209.170，实际 VIP 是 $VIP，将按实际值应用"
  TMP="$(mktemp -t direct-ports-XXXXXX.yaml)"; trap 'rm -f "$TMP"' EXIT
  sed "s/172\.16\.209\.170/$VIP/g" "$MANIFEST" > "$TMP"; MANIFEST="$TMP"
fi
if [ "$DRY_RUN" -eq 1 ]; then
  say "  [dry-run] kubectl apply -f $MANIFEST"
else
  kubectl apply -f "$MANIFEST" | sed 's/^/  /'
fi

# ---------- ②③ HARBOR_HOST ----------
for pair in "gaop-image-uploader:gaop-image-uploader" "gaop-be:gaop-be"; do
  deploy="${pair%%:*}"; cname="${pair##*:}"
  CUR="$(kubectl get deploy "$deploy" -n "$NS" \
    -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="HARBOR_HOST")].value}' 2>/dev/null || true)"
  say ""
  say "== ②③ $deploy HARBOR_HOST（当前: ${CUR:-<未设置>}）"
  if [ "$CUR" = "$REG" ]; then
    say "  已是目标值，跳过"
  elif [ "$DRY_RUN" -eq 1 ]; then
    say "  [dry-run] patch $deploy HARBOR_HOST=$REG"
  else
    kubectl patch deploy "$deploy" -n "$NS" --type=strategic -p \
      "{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"$cname\",\"env\":[{\"name\":\"HARBOR_HOST\",\"value\":\"$REG\"}]}]}}}}" >/dev/null
    kubectl rollout status "deploy/$deploy" -n "$NS" --timeout=300s | tail -1 | sed 's/^/  /'
  fi
done

# ---------- ④ 节点 docker 信任 ----------
say ""
say "== ④ 节点 insecure-registries 是否包含 $REG"
if [ "$DRY_RUN" -eq 1 ]; then
  say "  [dry-run] 起特权 Pod 检查 daemon.json，缺失则追加并 SIGHUP dockerd"
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
      command: ["sleep", "900"]
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
  say "  已包含，跳过"
else
  say "  追加并重载 dockerd"
  kubectl exec -n "$NS" "$POD" -- sh -c "
    set -e
    cp /host-docker/daemon.json /host-docker/daemon.json.bak.\$(date +%Y%m%d-%H%M%S)
    grep -q '\"$REG\"' /host-docker/daemon.json || \
      sed -i 's|\"insecure-registries\": \[|\"insecure-registries\": [\n    \"$REG\",|' /host-docker/daemon.json
    sed -n '/insecure-registries/,/]/p' /host-docker/daemon.json
    PID=''
    for p in /proc/[0-9]*; do [ \"\$(cat \$p/comm 2>/dev/null)\" = dockerd ] && PID=\${p#/proc/} && break; done
    [ -n \"\$PID\" ] && kill -HUP \$PID && echo \"  SIGHUP -> dockerd(\$PID)\"
  " | sed 's/^/  /'
  sleep 4
fi

say ""
say "== 复核"
kubectl exec -n "$NS" "$POD" -- sh -c "curl -s -m 6 -o /dev/null -w '  registry /v2/ from node = %{http_code}\n' http://$REG/v2/" || true
if [ -n "$VIP" ]; then
  curl -s --noproxy '*' -m 8 -o /dev/null -w "  VIP:19528 = %{http_code}（404 正常，说明连到上传服务）\n" "http://$VIP:19528/" || true
  curl -s --noproxy '*' -m 8 -o /dev/null -w "  VIP:19527 = %{http_code}（404 正常，说明连到 fdfs）\n" "http://$VIP:19527/" || true
fi
say "  下一步：界面上传一个真实镜像（docker save 的 tar），结果看 GET /api/gaop/v1/image/imageLog"
