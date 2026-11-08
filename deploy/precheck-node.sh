#!/usr/bin/env bash
# 目标节点前置检查（在每台要跑容器的机器上执行，只读，不改任何东西）
#
# 检查项来源：
#   ① 厂商「!安装和使用注意事项.txt」的四条硬要求
#   ② 本项目在 172.16.209.25 上真实踩过的坑（时钟回拨导致全集群 401、swap 未关导致
#      kubelet 拒绝启动、磁盘不足导致镜像导入失败）
#
# 用法：
#   ./precheck-node.sh              # 检查本机
#   sudo ./precheck-node.sh         # 加上 root 才能查的项（root 密码状态、iptables 规则数）
#
# 退出码：0 全部通过或有提示；1 有失败项。
#
# 与 deploy.sh doctor 的分工：doctor 用 kubectl 从**集群侧**看；本脚本在**节点本机**看
# 那些 K8s API 里没有的东西（swap、路由、DNS、时钟源）。
set -uo pipefail

NFAIL=0; NWARN=0
pass() { printf '   \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '   \033[33m!\033[0m %s\n' "$*"; NWARN=$((NWARN+1)); }
fail() { printf '   \033[31m✗\033[0m %s\n' "$*"; NFAIL=$((NFAIL+1)); }
sec()  { printf '\n\033[36m==\033[0m %s\n' "$*"; }

sec "1/9 架构与内核"
ARCH="$(uname -m)"
case "$ARCH" in
  aarch64|arm64) pass "架构 $ARCH（与包内镜像一致）" ;;
  *)             fail "架构 $ARCH：本包镜像与 kubectl 都是 linux/arm64，跑不起来" ;;
esac
pass "内核 $(uname -r)"

sec "2/9 SWAP（必须关闭）"
SWAP_LINE="$(grep -E '^/' /proc/swaps 2>/dev/null | head -1 || true)"
if [ -z "$SWAP_LINE" ]; then
  pass "无 swap 分区"
else
  fail "swap 仍启用：$SWAP_LINE —— kubelet 会拒绝启动。执行 swapoff -a 并注释 /etc/fstab 里的 swap 行"
fi

sec "3/9 默认路由（必须恰好一条）"
NROUTE="$(ip route 2>/dev/null | grep -c '^default')"
case "$NROUTE" in
  1) pass "默认路由 1 条" ;;
  0) fail "没有默认路由：节点无法访问外部与 API server 的回程流量" ;;
  *) warn "默认路由 $NROUTE 条：多网关会让 Pod 出网方向不确定（厂商注意事项第 4 条）" ;;
esac

sec "4/9 DNS"
if grep -qE '^nameserver' /etc/resolv.conf 2>/dev/null; then
  NS1="$(grep -E '^nameserver' /etc/resolv.conf | head -1 | awk '{print $2}')"
  pass "resolv.conf 有 nameserver（$NS1）"
else
  fail "/etc/resolv.conf 里没有 nameserver：镜像拉取与集群 DNS 都会失败"
fi
if getent hosts dl.k8s.io >/dev/null 2>&1 || getent hosts github.com >/dev/null 2>&1; then
  pass "外网域名可解析"
else
  warn "外网域名解析失败（气隙环境正常；请确认内网 DNS 能解析镜像仓库/授时源）"
fi

sec "5/9 root 账号（厂商要求可用密码登录本机）"
if [ "$(id -u)" -eq 0 ]; then
  RSTATE="$(passwd -S root 2>/dev/null | awk '{print $2}')"
  case "$RSTATE" in
    P) pass "root 已设置密码" ;;
    L) fail "root 被锁定（passwd -S 显示 L）：Ansible 的 become 与本地调试都会受阻" ;;
    NP) fail "root 无密码" ;;
    *) warn "读不出 root 密码状态" ;;
  esac
else
  warn "非 root 执行，跳过 root 密码检查（sudo 再跑一次可查）"
fi

sec "6/9 时间同步（本项目踩过：时钟回拨 → 全集群 401）"
if command -v timedatectl >/dev/null 2>&1; then
  SYNC="$(timedatectl show -p NTPSynchronized --value 2>/dev/null || echo unknown)"
  case "$SYNC" in
    yes) pass "NTP 已同步" ;;
    no)  fail "NTP 未同步：K8s 的 SA token 有 iat/exp 硬校验，时钟回拨会让全集群 401" ;;
    *)   warn "读不出 NTP 同步状态" ;;
  esac
  NTP_EN="$(timedatectl show -p NTP --value 2>/dev/null || echo unknown)"
  [ "$NTP_EN" = "yes" ] && pass "NTP 服务已启用" || warn "NTP 未启用（气隙环境请配内网授时源）"
else
  warn "没有 timedatectl，跳过时钟检查"
fi

sec "7/9 磁盘"
TARGET=/var/lib/docker
[ -d "$TARGET" ] || TARGET=/var/lib/containerd
[ -d "$TARGET" ] || TARGET=/
AVAIL_G="$(df -BG "$TARGET" 2>/dev/null | awk 'NR==2 {gsub("G","",$4); print $4}')"
if [ -n "${AVAIL_G:-}" ] && [ "$AVAIL_G" -ge 20 ] 2>/dev/null; then
  pass "$TARGET 可用 ${AVAIL_G}G"
else
  warn "$TARGET 可用 ${AVAIL_G:-未知}G：本包镜像约 400MB，但容器层与日志还需要空间，建议 ≥ 20G"
fi

sec "8/9 资源"
MEM_G="$(awk '/MemTotal/ {printf "%d", $2/1024/1024}' /proc/meminfo 2>/dev/null || echo 0)"
CPU_N="$(nproc 2>/dev/null || echo 0)"
[ "$MEM_G" -ge 4 ] && pass "内存 ${MEM_G}G" || warn "内存 ${MEM_G}G：后端单副本 requests 256Mi/limit 1Gi，加中间件后偏紧"
[ "$CPU_N" -ge 2 ] && pass "CPU ${CPU_N} 核" || warn "CPU ${CPU_N} 核：清单刻意不声明 CPU，但 1 核会更慢"

sec "9/9 容器运行时与内核转发"
if command -v docker >/dev/null 2>&1; then
  pass "docker $(docker version --format '{{.Server.Version}}' 2>/dev/null || echo '未连通')"
elif command -v ctr >/dev/null 2>&1; then
  pass "containerd（ctr 可用，镜像用 ctr -n k8s.io images import 导入）"
else
  warn "既没有 docker 也没有 ctr：确认运行时是否在本机"
fi
IPF="$(sysctl -n net.ipv4.ip_forward 2>/dev/null || echo unknown)"
[ "$IPF" = "1" ] && pass "net.ipv4.ip_forward=1" || warn "ip_forward=$IPF（K8s 网络要求 1）"
if lsmod 2>/dev/null | grep -q '^br_netfilter'; then
  pass "br_netfilter 已加载"
else
  warn "br_netfilter 未加载（K8s 网络插件常需要；modprobe br_netfilter + 写 /etc/modules-load.d）"
fi
if command -v getenforce >/dev/null 2>&1; then
  SE="$(getenforce)"
  [ "$SE" = "Enforcing" ] && warn "SELinux=Enforcing：容器挂载与目录权限可能被拒" || pass "SELinux=$SE"
fi
if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet firewalld 2>/dev/null; then
  warn "firewalld 运行中：与 CNI 的 iptables 规则可能冲突"
fi

printf '\n'
if [ "$NFAIL" -eq 0 ] && [ "$NWARN" -eq 0 ]; then
  printf '\033[32m全部通过\033[0m\n'; exit 0
elif [ "$NFAIL" -eq 0 ]; then
  printf '\033[33m通过，%d 项提示\033[0m\n' "$NWARN"; exit 0
else
  printf '\033[31m%d 项失败 / %d 项提示\033[0m\n' "$NFAIL" "$NWARN"; exit 1
fi
