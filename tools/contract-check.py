#!/usr/bin/env python3
"""契约比对器：厂商样本 vs 重写版响应（单端点）。

判定标准 = docs/adr/0006-contract-semantic-equivalence.md（语义等价）：
  L1 形状      —— 信封键、data 键、列表项键集合；不绿=缺陷
  L2 读取路径  —— 前端**实际读**的字段路径必须存在且值相等（依据 docs/findings/read-paths.json）；不绿=缺陷
  L3 其余差异  —— 「已知偏离清单」，记录在案；**任何偏离一旦被证明前端可见即升级为缺陷**

用法：
  python3 tools/contract-check.py "GET /v1/service" [厂商样本.json] [重写版.json]
退出码：0 = L1+L2 全通过；1 = 有失败。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PATHS_FILE = ROOT / 'docs/findings/read-paths.json'
# 同一份清单在 Maven 测试里被离线复用，两份必须逐字节一致，否则"离线绿、在线红"。
MIRROR_FILE = ROOT / 'paas-core-trigger/src/test/resources/vendor/read-paths.json'


def check_mirror():
    """read-paths.json 的两份副本必须一致（离线门槛与在线门槛同源）。"""
    if not MIRROR_FILE.exists():
        print(f'❌ 离线副本缺失：{MIRROR_FILE.relative_to(ROOT)}')
        return 1
    if PATHS_FILE.read_bytes() != MIRROR_FILE.read_bytes():
        print('❌ read-paths.json 两份副本不一致——离线 U 会与在线判定分叉。'
              f'请同步：cp {PATHS_FILE.relative_to(ROOT)} {MIRROR_FILE.relative_to(ROOT)}')
        return 1
    return 0



def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def key_set(obj):
    """列表取首元素的键集合；字典取键集合。"""
    if isinstance(obj, list):
        return set().union(*[key_set(i) for i in obj]) if obj else set()
    if isinstance(obj, dict):
        return set(obj)
    return set()


def resolve(doc, path):
    """支持 `a.b[]` 与 `a.b[].c` 这种带通配的路径；返回 (found, values)。"""
    cur = [doc]
    for seg in path.split('.'):
        nxt = []
        wildcard = seg.endswith('[]')
        seg = seg[:-2] if wildcard else seg
        for node in cur:
            if not isinstance(node, dict) or seg not in node:
                return False, []
            val = node[seg]
            if wildcard:
                if not isinstance(val, list):
                    return False, []
                nxt.extend(val)
            else:
                nxt.append(val)
        cur = nxt
    return True, cur


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    if check_mirror() != 0:
        return 1
    endpoint = sys.argv[1]
    man = load(PATHS_FILE).get(endpoint)
    if not man:
        print(f'read-paths.json 里没有 {endpoint} 的读取路径清单，无法做 L2 判定')
        return 2

    slug = endpoint.split(' ', 1)[1].strip('/').replace('/', '_')
    vendor_path = sys.argv[2] if len(sys.argv) > 2 else ROOT / f'docs/findings/runtime-samples/{slug}.json'
    mine_path = sys.argv[3] if len(sys.argv) > 3 else '/tmp/mine2.json'
    vendor, mine = load(vendor_path), load(mine_path)

    fails, notes, deviations = [], [], []

    print(f'# 契约比对：{endpoint}')
    print(f'厂商样本 : {vendor_path}')
    print(f'重写版   : {mine_path}\n')
    print('## L1 形状')
    same = list(vendor) == list(mine)
    print(f"  {"✅" if same else "❌"} 信封键 vendor={list(vendor)} mine={list(mine)}")
    if not same:
        fails.append('信封键不一致')
    if isinstance(vendor.get('data'), dict) and isinstance(mine.get('data'), dict):
        same = set(vendor['data']) == set(mine['data'])
        print(f"  {"✅" if same else "❌"} data 键 vendor={sorted(vendor['data'])} mine={sorted(mine['data'])}")
        if not same:
            fails.append('data 键不一致')
        if 'services' in vendor['data'] and 'services' in mine['data']:
            vs, ms = key_set(vendor['data']['services']), key_set(mine['data']['services'])
            ok = vs == ms
            print(f"  {"✅" if ok else "❌"} 列表项键 {len(vs)}/{len(ms)} 缺失={sorted(vs - ms)} 多出={sorted(ms - vs)}")
            if not ok:
                fails.append('列表项键集合不一致')

    print('\n## L2 前端读取路径（门槛的核心）')
    for item in man['readPaths']:
        p = item['path']
        vf, vv = resolve(vendor, p)
        mf, mv = resolve(mine, p)
        mode = item.get('mode', 'value')
        if mode == 'container':
            # 容器型：只要求存在且类型一致（内容差异归 L3）
            ok = mf and (type(mv) is type(vv))
            detail = f'存在且类型一致（{type(mv).__name__}）' if ok else '缺失或类型不同'
        elif not vv and not mv:
            ok, detail = True, '两侧都没有该字段（厂商本就没有）'
        elif not mv:
            ok, detail = False, f'重写版缺失（厂商有 {str(vv)[:50]}）'
        elif not vv:
            ok, detail = True, f'厂商没有、重写版多出 {str(mv)[:40]}（不影响）'
        else:
            ok = vv == mv
            detail = '值一致' if ok else f'值不同 vendor={str(vv)[:60]} mine={str(mv)[:60]}'
        if not ok and item.get('allowDeviation'):
            print(f'  ⚠️  {p:52} 允许的偏离：{item["deviationReason"]}')
            deviations.append(p)
            continue
        print(f'  {"✅" if ok else "❌"} {p:52} {detail}')
        if not ok:
            fails.append(f'读取路径断裂: {p}')

    print('\n## L3 已知偏离（不影响门槛，记录在案）')
    for d in man.get('knownDeviations', []):
        disp = d.get('disposition', '记录在案')
        print(f"  • {d['path']}\n      理由：{d['reason']}\n      处置：{disp}")

    print('\n## 结论')
    if fails:
        print('  L1+L2 存在失败 ❌: ' + '; '.join(fails))
    elif deviations:
        print(f'  L1+L2 通过 ✅（另有 {len(deviations)} 条**已记录的允许偏离**，见 L2 的 ⚠️ 行）')
    else:
        print('  L1+L2 全部通过 ✅（门槛内等价，零偏离）')
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
