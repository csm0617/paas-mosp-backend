#!/usr/bin/env python3
"""前端调用面 / 厂商后端 覆盖率审计（口径见 docs/findings/141、155）。

三条口径：
  FE     = 厂商原样前端 bundle 里出现的 `/v1/*` 路径（归一化去重）
  VENDOR = 厂商后端 recovered/gaop-be 的 controller 映射（类级前缀 + 方法映射，归一化去重）
  OURS   = paas-core-trigger 的 controller 映射（同上）

归一化（两侧一致）：去 query/fragment、去尾斜杠、`{x}`/`${x}`/`:x` → `X`、连续 `X` 折叠。

用法：
  python3 tools/coverage-audit.py              # 三条口径总账
  python3 tools/coverage-audit.py --group      # 未实现按前缀分组
  python3 tools/coverage-audit.py --list       # 逐条列出未实现
  python3 tools/coverage-audit.py --extra      # 后端有、前端未见
"""
import re
import sys
import pathlib
import collections

ROOT = pathlib.Path(__file__).resolve().parents[1]
FRONTEND = pathlib.Path("/Users/csm/Desktop/Project/msop/build/gaop-web-ctx/html")
VENDOR = pathlib.Path(
    "/Users/csm/Desktop/Project/msop/recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller"
)
TRIGGER = ROOT / "paas-core-trigger/src/main/java"

FE_PATH_RE = re.compile(r"/v1/[A-Za-z0-9_./{}$:?=&,\-]+")
# 注意：本仓库两种写法都有 —— 简写 @GetMapping 与全限定 @org.springframework...GetMapping
ANNO_RE = re.compile(
    r"@(?:[A-Za-z0-9_.]+\.)?(Get|Post|Put|Delete|Patch|Request)Mapping"
    r"\s*(?:\((.*?)\))?\s*(?:\n|\r|$)",
    re.S,
)


def normalize(p: str) -> str:
    p = p.split("?")[0].split("#")[0]
    p = re.sub(r"/{2,}", "/", p)
    p = p.rstrip("/") or p
    p = re.sub(r"\$\{[^}]*\}", "X", p)
    p = re.sub(r"\{[^}]*\}", "X", p)
    p = re.sub(r"/:[A-Za-z_][A-Za-z0-9_]*", "/X", p)
    # 注意：**不要**折叠连续的 `/X`。`/v1/role/{roleId}/{userId}` 与 `/v1/role/{id}` 是
    # 两个不同的端点（前者 DELETE 删用户关联、后者 GET 取详情），折叠会把前者的缺口吃掉。
    return p.rstrip("/") or p


# 前端还有 `"/v1/x/".concat(id,"/y")` 这种拼串写法，纯 grep `/v1/` 会漏掉后半段
CONCAT_RE = re.compile(r'"(/v1/[^"]*)"((?:\s*\.concat\([^()]*\))+)', re.S)
CONCAT_ARG_RE = re.compile(r'\.concat\(([^()]*)\)')


def frontend_paths():
    found = set()
    for f in FRONTEND.rglob("*.js"):
        try:
            text = f.read_text(errors="ignore")
        except OSError:
            continue
        for m in FE_PATH_RE.findall(text):
            found.add(normalize(m))
        for m in CONCAT_RE.finditer(text):
            path = m.group(1)
            for arg in CONCAT_ARG_RE.findall(m.group(2)):
                pos = 0
                for lit in re.finditer(r'"([^"]*)"', arg):
                    if re.search(r"[A-Za-z_$]", arg[pos:lit.start()]):
                        path += "X"
                    path += lit.group(1)
                    pos = lit.end()
                if re.search(r"[A-Za-z_$]", arg[pos:]):
                    path += "X"
            found.add(normalize(path))
    return found


def _values(body):
    """取注解里的路径值。只有完全没有 `=` 时才把整个 body 当裸字符串。"""
    if body is None:
        return [""]
    if "=" not in body:
        return re.findall(r'"([^"]*)"', body) or [""]
    for key in ("value", "path"):
        m = re.search(r"\b" + key + r"\s*=\s*(\{[^}]*\}|\"[^\"]*\")", body, re.S)
        if m:
            return re.findall(r'"([^"]*)"', m.group(1)) or [""]
    return [""]


def _parse_file(path: pathlib.Path):
    """返回该文件里所有 (归一化路径, 是否类级) —— 类级前缀与每条方法映射。"""
    src = path.read_text(errors="ignore")
    annos = list(ANNO_RE.finditer(src))
    m_cls = re.search(r"\bclass\s+\w+", src)
    class_pos = m_cls.start() if m_cls else len(src)
    class_anno = None
    for m in annos:
        if m.start() < class_pos and m.group(1) == "Request":
            class_anno = m
    prefix = ""
    if class_anno:
        v = _values(class_anno.group(2))
        prefix = v[0].rstrip("/") if v else ""
    paths = []
    for m in annos:
        kind = m.group(1)
        if class_anno is not None and m.start() == class_anno.start():
            continue
        if m.start() < class_pos and kind == "Request":
            continue
        for v in _values(m.group(2)):
            # Spring 语义：类级前缀与方法值总是拼接
            full = (prefix + "/" + v.lstrip("/")) if v else prefix
            if full:
                paths.append(normalize(full))
    return paths


def collect(directory: pathlib.Path):
    out = {}
    for f in directory.rglob("*Controller.java"):
        for p in _parse_file(f):
            out.setdefault(p, set()).add(f.name)
    return out


def report(name_a, a, name_b, b):
    missing = sorted(set(a) - set(b))
    extra = sorted(set(b) - set(a))
    print(f"{name_a:<28}: {len(a)}")
    print(f"{name_b:<28}: {len(b)}")
    print(f"未实现（{name_a} 有、{name_b} 无）: {len(missing)}")
    print(f"{name_b} 多出（{name_a} 未见）    : {len(extra)}")
    return missing, extra


def main():
    fe = frontend_paths()
    ours = collect(TRIGGER)
    vendor = collect(VENDOR)

    print("=== 口径 A：前端调用面 ===")
    missing_fe, extra_fe = report("前端 /v1/*（归一化）", fe, "paas-core 已实现", ours)
    print()
    print("=== 口径 B：厂商后端 API 面（真正的重构总量） ===")
    missing_v, extra_v = report("厂商后端映射（归一化）", vendor, "paas-core 已实现", ours)

    missing = missing_v if "--vendor" in sys.argv else missing_fe
    label = "厂商后端" if "--vendor" in sys.argv else "前端"

    if "--group" in sys.argv or "--list" in sys.argv:
        groups = collections.Counter()
        for p in missing:
            seg = p.split("/")
            groups["/".join(seg[:3]) if len(seg) > 2 else p] += 1
        print(f"\n## 未实现 · 按前缀分组（口径：{label}）")
        for k, v in groups.most_common():
            print(f"{v:>4}  {k}")

    if "--list" in sys.argv:
        print(f"\n## 未实现 · 逐条（口径：{label}）")
        for p in missing:
            print(p)

    if "--extra" in sys.argv:
        print("\n## paas-core 多出（前端未见）")
        for p in extra_fe:
            print(p)


if __name__ == "__main__":
    main()
