#!/usr/bin/env bash
# 检查工作负载的 limits 是否「cpu + memory 成对」（findings/167 §二十）。
#
# 为什么必须成对：
#   gaop-be 的 DashboardsService.cockpit()（line 548）会这么取值：
#
#     Map limits = container.getResources().getLimits();
#     limits.get("cpu").getNumber().doubleValue() * 1000;
#     limits.get("memory").getNumber().doubleValue();
#
#   它只判了 `resources != null` 和 `limits != null`，**没有判具体 key**。
#   所以只要某个容器 limits 里缺 cpu（或 memory），整个驾驶舱就 500 NPE：
#     java.lang.NullPointerException at DashboardsService.cockpit(DashboardsService.java:548)
#
#   这类问题极易复发：手工 apply 的清单常常只写 memory（例如厂商自己的
#   gaop-mysql 清单就只有 memory: 1Gi），一上就崩。
#
# 用法：
#   tools/check-container-limits.sh [命名空间...]     # 默认检查 gaop-namespace
#
#   --json 以 JSON 输出，便于挂巡检
#
# 退出码：0 全部通过；1 存在缺项。
set -uo pipefail

JSON=0
NS_LIST=()
for arg in "$@"; do
  case "$arg" in
    --json) JSON=1 ;;
    -*) echo "未知参数: $arg" >&2; exit 2 ;;
    *) NS_LIST+=("$arg") ;;
  esac
done
[ ${#NS_LIST[@]} -eq 0 ] && NS_LIST=("gaop-namespace")

python3 - "$JSON" "${NS_LIST[@]}" <<'PY'
import json, subprocess, sys

as_json = sys.argv[1] == "1"
namespaces = sys.argv[2:]

KINDS = ["deployments", "statefulsets", "daemonsets"]
bad = []
checked = 0

for ns in namespaces:
    for kind in KINDS:
        try:
            out = subprocess.run(
                ["kubectl", "get", kind, "-n", ns, "-o", "json"],
                capture_output=True, text=True, timeout=60, check=True,
            ).stdout
        except Exception:
            continue
        for item in json.loads(out).get("items", []):
            name = item["metadata"]["name"]
            spec = (item.get("spec") or {}).get("template", {}).get("spec") or {}
            containers = (spec.get("containers") or []) + (spec.get("initContainers") or [])
            for c in containers:
                checked += 1
                lim = ((c.get("resources") or {}).get("limits") or {})
                missing = [r for r in ("cpu", "memory") if r not in lim]
                if missing:
                    bad.append({
                        "namespace": ns, "kind": kind[:-1], "workload": name,
                        "container": c.get("name"), "missing": missing,
                        "limits": lim,
                    })

if as_json:
    print(json.dumps({"checked": checked, "violations": bad}, ensure_ascii=False, indent=2))
else:
    print(f"检查容器数: {checked}")
    if not bad:
        print("OK  所有容器的 limits 都包含 cpu + memory")
    else:
        print(f"\n发现问题 {len(bad)} 处（会导致驾驶舱 cockpit NPE）：")
        for b in bad:
            print(f"  MISS {b['namespace']}/{b['kind']}/{b['workload']} "
                  f"container={b['container']} 缺={','.join(b['missing'])} limits={b['limits']}")
        print("\n修复示例：")
        print('  kubectl -n <ns> patch deploy <name> --type=strategic -p \''
              '{"spec":{"template":{"spec":{"containers":[{"name":"<c>",'
              '"resources":{"limits":{"cpu":"2"}}}]}}}}\'')
        print("\n注意：strategic merge 对 limits 是 map 合并，不会覆盖已有的 memory。")

sys.exit(1 if bad else 0)
PY
