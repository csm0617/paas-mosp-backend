#!/usr/bin/env python3
"""对比厂商 GET /v1/service 与 paas-core 重写版的响应。

用法：python3 tools/diff-service-list.py [重写版响应.json]
默认读取 /tmp/mine-service-list.json。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
vendor = json.loads((ROOT / 'docs/findings/runtime-samples/service-list.json').read_text(encoding='utf-8'))
mine = json.loads(Path(sys.argv[1] if len(sys.argv) > 1 else '/tmp/mine-service-list.json').read_text(encoding='utf-8'))

out = []
def line(s=''):
    out.append(s)
    print(s)

def name_of(item):
    return item.get('fixedName') or (item.get('service') or {}).get('metadata', {}).get('name')

line('# 厂商 vs 重写版：GET /v1/service 契约对比')
line()
line(f"信封键      : vendor={list(vendor)} | mine={list(mine)} | {'一致' if list(vendor) == list(mine) else '不一致'}")
vd, md = vendor['data'], mine['data']
line(f"data 键     : vendor={sorted(vd)} | mine={sorted(md)} | {'一致' if sorted(vd) == sorted(md) else '不一致'}")
for k in ('runningDeployment', 'total', 'count'):
    tag = '一致' if vd.get(k) == md.get(k) else '**差异**'
    line(f"  {k:<18}: vendor={vd.get(k)} | mine={md.get(k)} | {tag}")
line()

vs = {name_of(i) for i in vd['services']}
ms = {name_of(i) for i in md['services']}
line(f"服务数      : vendor={len(vd['services'])} | mine={len(md['services'])}")
line(f"仅厂商有    : {sorted(vs - ms) or '（无）'}")
line(f"仅重写版有  : {sorted(ms - vs) or '（无）'}")
line()

vk = set(vd['services'][0].keys())
mk = set(md['services'][0].keys())
line(f"单项键数    : vendor={len(vk)} | mine={len(mk)}")
line(f"  重写版缺失: {sorted(vk - mk) or '（无）'}")
line(f"  重写版多出: {sorted(mk - vk) or '（无）'}")
line()

def ports(item):
    return ((item.get('service') or {}).get('spec') or {}).get('ports') or []

def port_repr(p):
    return {k: f"{p.get(k)!r}({type(p.get(k)).__name__})" for k in ('name', 'port', 'targetPort', 'protocol') if k in p}

by_mine = {name_of(i): i for i in md['services']}
by_vendor = {name_of(i): i for i in vd['services']}
line('## 同名服务的 spec.ports 对比（前 6 个）')
line()
line('| 服务 | 厂商 | 重写版 | 判定 |')
line('| --- | --- | --- | --- |')
for name in sorted(vs & ms)[:6]:
    vp, mp = ports(by_vendor[name]), ports(by_mine[name])
    vr = '; '.join(json.dumps(port_repr(p), ensure_ascii=False) for p in vp) or '（无）'
    mr = '; '.join(json.dumps(port_repr(p), ensure_ascii=False) for p in mp) or '（无）'
    if vp == mp:
        verdict = '一致'
    else:
        vkeys = sorted(set().union(*[set(p) for p in vp]) if vp else set())
        mkeys = sorted(set().union(*[set(p) for p in mp]) if mp else set())
        verdict = f"**差异**（厂商独有键 {sorted(set(vkeys) - set(mkeys))}／重写版独有键 {sorted(set(mkeys) - set(vkeys))}）"
    line(f"| {name} | {vr[:120]} | {mr[:120]} | {verdict} |")
line()

line('## K8s Service 对象的键集合差异（第一个同名服务）')
line()
if vs & ms:
    n = sorted(vs & ms)[0]
    v_obj, m_obj = by_vendor[n]['service'], by_mine[n]['service']
    line(f"服务 {n}")
    line(f"  顶层键 : vendor={sorted(v_obj)} | mine={sorted(m_obj)}")
    line(f"  metadata 缺失 : {sorted(set(v_obj.get('metadata', {})) - set(m_obj.get('metadata', {})))}")
    line(f"  metadata 多出 : {sorted(set(m_obj.get('metadata', {})) - set(v_obj.get('metadata', {})))}")
    line(f"  spec 缺失     : {sorted(set(v_obj.get('spec', {})) - set(m_obj.get('spec', {})))}")
    def null_keys(obj):
        return sorted(k for k, v in (obj or {}).items() if v is None)

    line(f"  厂商 metadata 显式 null 键: {null_keys(v_obj.get('metadata'))}")
    line(f"  厂商 spec 显式 null 键    : {null_keys(v_obj.get('spec'))}")
    line(f"  重写版 metadata null 键   : {null_keys(m_obj.get('metadata'))}")
    line(f"  重写版 spec null 键       : {null_keys(m_obj.get('spec'))}")

(ROOT / 'docs/findings/06-prototype-diff.txt')
