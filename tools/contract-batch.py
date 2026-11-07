#!/usr/bin/env python3
"""契约比对（批量）：把 `runtime-samples/manifest.json` 里**所有厂商 200 样本**逐条对我们的重写版重放。

为什么需要它：`contract-check.py` 是**单端点**工具，而 `read-paths.json` 只登记了 12 条路径，
剩下 200+ 条已实现端点没有契约门槛。厂商后端已经 Metaspace 死掉、无法再抓新样本，
所以「能做的最大一步」就是把**已有的 37 条厂商真实抓包**全部用起来。

判定（对齐 docs/adr/0006 的 L1）：
  - 信封键集合（msg/code/data）
  - `code` 必须与厂商样本一致（样本 200 ⇒ 我们也要 code=0）
  - `data` 为对象时：顶层键集合
  - `data` 里任一族为**非空数组**时：首元素的键集合（我们这边为空则跳过并标注）

用法：
  python3 tools/contract-batch.py [--base http://127.0.0.1:8080] [--all]
  （默认只跑厂商 httpStatus=200 的样本；--all 连厂商自己 500/401 的也跑，用于观察）
"""
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "docs/findings/runtime-samples/manifest.json"
BASE = "http://127.0.0.1:8080"


def fetch(path: str, timeout: int = 15):
    req = urllib.request.Request(BASE + path, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8", "replace"))
        except Exception:
            return e.code, None
    except Exception as e:  # 超时/连接失败
        return 0, {"__error__": str(e)}


def keys_of(obj):
    return set(obj.keys()) if isinstance(obj, dict) else set()


def compare(vendor, ours):
    """返回 (状态, 说明列表)。状态 ∈ PASS / DIFF / SKIP。"""
    notes = []
    if not isinstance(ours, dict):
        return "SKIP", ["重写版没有 JSON 响应（超时或非 JSON）"]
    v_top, o_top = keys_of(vendor), keys_of(ours)
    if v_top != o_top:
        notes.append(f"信封键：厂商{sort_str(v_top)} vs 我们{sort_str(o_top)}")
    if vendor.get("code") is not None and vendor.get("code") != ours.get("code"):
        notes.append(f"code：厂商 {vendor.get('code')} vs 我们 {ours.get('code')}")

    vd, od = vendor.get("data"), ours.get("data")
    if isinstance(vd, dict):
        if not isinstance(od, dict):
            notes.append(f"data 类型：厂商 object vs 我们 {type(od).__name__}")
        else:
            vk, ok = keys_of(vd), keys_of(od)
            if vk != ok:
                miss = sorted(vk - ok)
                extra = sorted(ok - vk)
                if miss:
                    notes.append(f"data 缺键：{miss}")
                if extra:
                    notes.append(f"data 多键：{extra}")
            # 非空数组族：比首元素键集合
            for k, vv in vd.items():
                if isinstance(vv, list) and vv and isinstance(vv[0], dict):
                    ov = od.get(k)
                    if isinstance(ov, list) and ov and isinstance(ov[0], dict):
                        ik, ok2 = keys_of(vv[0]), keys_of(ov[0])
                        if ik != ok2:
                            miss = sorted(ik - ok2)
                            extra = sorted(ok2 - ik)
                            if miss:
                                notes.append(f"{k}[0] 缺键：{miss}")
                            if extra:
                                notes.append(f"{k}[0] 多键：{extra}")
                    else:
                        notes.append(f"{k}[0]：厂商非空数组，我们为空/非数组（数据量差异，仅标注）")
    elif isinstance(vd, list):
        if not isinstance(od, list):
            notes.append(f"data 类型：厂商 array vs 我们 {type(od).__name__}")
        elif vd and od and isinstance(vd[0], dict) and isinstance(od[0], dict):
            ik, ok2 = keys_of(vd[0]), keys_of(od[0])
            if ik != ok2:
                notes.append(f"data[0] 缺键：{sorted(ik - ok2)} / 多键：{sorted(ok2 - ik)}")
    return ("PASS" if not notes else "DIFF"), notes


def sort_str(s):
    return "{" + ",".join(sorted(s)) + "}"


def main():
    global BASE
    if "--base" in sys.argv:
        BASE = sys.argv[sys.argv.index("--base") + 1]
    include_all = "--all" in sys.argv

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = [e for e in manifest["endpoints"]
               if e["path"].startswith("/v1") and (include_all or e.get("httpStatus") == 200)]

    passed = diff = skip = 0
    print(f"契约批量比对：{len(entries)} 条样本（厂商 httpStatus=200）→ {BASE}\n")
    for e in entries:
        vpath = ROOT / "docs/findings/runtime-samples" / e["file"]
        vendor = json.loads(vpath.read_text(encoding="utf-8"))
        status, ours = fetch(e["path"])
        state, notes = compare(vendor, ours)
        if state == "PASS":
            passed += 1
        elif state == "SKIP":
            skip += 1
        else:
            diff += 1
        mark = {"PASS": "✅", "DIFF": "❌", "SKIP": "⚠️"}[state]
        print(f"{mark} {e['path']}")
        for n in notes:
            print(f"     - {n}")

    print(f"\n汇总：PASS {passed} / DIFF {diff} / SKIP {skip}")
    return 0 if diff == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
