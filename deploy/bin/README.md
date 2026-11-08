# bin/ —— 包内自带的运维二进制

目标机不预装 kubectl 也能完成部署。包里只放**目标机架构**的二进制：
本阶段只有 `linux/arm64`（与镜像架构一致）。

| 文件 | 来源 | 校验 |
| --- | --- | --- |
| `kubectl` | `https://dl.k8s.io/release/v1.31.9/bin/linux/arm64/kubectl` | `kubectl.sha256`（官方发布的 SHA-256，64 字节无换行） |
| `kubectl.sha256` | 同版本官方校验文件 | — |

版本选 1.31.9 的理由：与目标集群（v1.31.9）一致，避免 kubectl 与服务端跨版本
导致的字段兼容问题。集群升级后同步换这里。

## 为什么不用系统 kubectl

`deploy.sh` 会把本目录前置到 `PATH`：包内的优先于目标机的。两个原因：

1. **拿到就能装**：现场经常只有一台跳板机能连 API，或者节点上根本没装 kubectl；
2. **版本可控**：避免目标机那个 1.18 的老 kubectl 把 `jsonpath` 和 `--dry-run=server`
   解释成别的意思（本包大量依赖 jsonpath 定位 ingress path）。

## 二进制不进 git

53MB 的可执行文件不适合进版本库，已在 `.gitignore` 排除。获取方式：

```bash
cd paas-core/deploy/bin
curl -fsSLO https://dl.k8s.io/release/v1.31.9/bin/linux/arm64/kubectl
curl -fsSLO https://dl.k8s.io/release/v1.31.9/bin/linux/arm64/kubectl.sha256
python3 - <<'EOF'
import hashlib, pathlib
d = pathlib.Path('kubectl.sha256').read_text().strip()
a = hashlib.sha256(pathlib.Path('kubectl').read_bytes()).hexdigest()
assert d == a, f'校验失败: {d} != {a}'
print('校验通过')
EOF
```

`build-package.sh` 在缺文件时会自动下载并校验；它也会在打包自检里核对
ELF 的 `e_machine` 必须是 AArch64（0xB7），防止误把 amd64 版本打进去。
