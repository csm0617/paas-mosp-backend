#!/bin/sh
# 检测 gaop-namespace 里是否存在「会打崩厂商前端配置页」的 env 形态。
#
# 背景（docs/findings/167 第十三节）：
#   前端 ServiceConfig 把容器 env 转成表单时只认两种形态：
#     value（普通变量）/ valueFrom.fieldRef（Pod 字段引用）
#   代码：value: e.value ? e.value : (e.valueFrom ? re[e.valueFrom.fieldRef.fieldPath] : e.value)
#   如果 env 只有 valueFrom.secretKeyRef / configMapKeyRef / resourceFieldRef，
#   fieldRef 为 undefined ⇒ TypeError: reading 'fieldPath' ⇒ React 树崩 ⇒ 点「配置」整页白屏。
#
# 本脚本只做**只读检测**，不修改任何对象。
# 修法见 findings/167：把这类 env 换成字面 value（注意明文风险），或改前端兼容。
#
# 用法：./check-vendor-env-shape.sh [namespace]
set -eu
NS="${1:-gaop-namespace}"

echo "检查 $NS 的 Deployment env 形态 …"
kubectl get deploy -n "$NS" -o json | python3 -c '
import json, sys
items = json.load(sys.stdin)["items"]
bad_total = 0
for it in items:
    name = it["metadata"]["name"]
    for c in it["spec"]["template"]["spec"]["containers"]:
        cname = c["name"]
        for e in (c.get("env") or []):
            vf = e.get("valueFrom") or {}
            if vf and not vf.get("fieldRef"):
                bad_total += 1
                kinds = ",".join(vf.keys())
                print("  RISK  %s/%s  env=%s  valueFrom=%s" % (name, cname, e["name"], kinds))
if bad_total == 0:
    print("  未发现风险：所有 valueFrom 都是 fieldRef")
else:
    print(f"\n共 {bad_total} 项会让厂商前端「服务配置」页白屏（React: reading \x27fieldPath\x27）")
    sys.exit(1)
'
