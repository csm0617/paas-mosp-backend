#!/usr/bin/env python3
"""厂商前端接口连通性探针：按 api-catalogue 重放全部 GET，输出通过率与失败原因。

为什么需要它：`coverage-audit.py` 只回答"有没有实现"，回答不了"现在通不通"。
本文具补的就是运行侧——厂商前端会在这些端点上真正发请求，环境缺件/地址错/厂商 NPE
都只会在这里暴露（判定口径见 docs/findings/167）。

用法：
  # 1) 先建通道（本机 HTTP_PROXY 会拦 localhost，curl/python 都要绕过代理）
  kubectl proxy --port=18001 --address=127.0.0.1 &
  # 2) 跑
  python3 tools/probe-vendor-api.py --proxy http://127.0.0.1:18001
  python3 tools/probe-vendor-api.py --base http://172.16.209.170/api/gaop   # 走网关时用这个

退出码：0 = 全部 2xx；1 = 有失败（方便挂进 CI/巡检）。

注意三点（都是实测踩过的）：
  · 缺 @RequestParam 时厂商回 500 而非 400，必须按 catalogue 的 query 补齐才算公平判定；
  · Date 参数要 ISO8601 带时区，/audit 的 start/end 却是 Long 毫秒；
  · 镜像名必须 `project/name`，否则一律「镜像名错误」。
"""
import argparse
import collections
import json
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOGUE = pathlib.Path("/Users/csm/Desktop/Project/msop/recovered-web/api-catalogue.json")
TIMEOUT = 20
ISO_S, ISO_E = "2026-11-09T20:00:00.000+08:00", "2026-11-09T21:00:00.000+08:00"

VALUES = {
    "page": "1", "size": "10", "keyword": "", "order": "", "sort": "",
    "alertType": "0", "userId": "22222222-2222-2222-2222-222222222222",
    "nodeName": "k8s-master", "serviceName": "gaop-be", "name": "gaop/gaop-be",
    "baseServiceName": "gaop-be", "namespace": "gaop-namespace",
    "start": ISO_S, "end": ISO_E, "queryTimeType": "true", "timeType": "ONE_HOURS",
    "ip": "127.0.0.1", "url": "http://127.0.0.1:8080", "lang": "zh_CN",
    "version": "V1.3.4", "module": "", "storage": "local",
    "imageName": "gaop/gaop-be", "tag": "latest", "databaseName": "gaop",
    "key": "x", "jobName": "x", "strategyName": "x", "type": "1", "port": "30000",
    "etcdNodeIp": "172.16.209.25", "authType": "", "serviceType": "",
    "parentId": "", "moduleName": "", "storageType": "local", "query": "1",
    "userName": "admin", "roleName": "",
}
# 探针值按本地集群取名（nodeName/podName/etcdNodeIp/userId 换成自己环境的）
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def login(base):
    body = b'{"username":"admin","password":"admin123","role":"admin"}'
    req = urllib.request.Request(base + "/v1/pub/login", data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    with opener.open(req, timeout=15) as r:
        return json.loads(r.read())["data"]["token"]


def probe(args_tuple):
    base, token, url, params = args_tuple
    full = url + ("?" + urllib.parse.urlencode(params) if params else "")
    req = urllib.request.Request(base + full, headers={
        "Accept": "application/json", "Cookie": "gateway-token=" + token})
    t0 = time.time()
    try:
        with opener.open(req, timeout=TIMEOUT) as r:
            return {"url": full, "status": r.status, "ms": int((time.time() - t0) * 1000),
                    "body": r.read().decode("utf-8", "replace")[:400]}
    except urllib.error.HTTPError as e:
        return {"url": full, "status": e.code, "ms": int((time.time() - t0) * 1000),
                "body": e.read().decode("utf-8", "replace")[:400]}
    except Exception as e:
        return {"url": full, "status": 0, "ms": int((time.time() - t0) * 1000),
                "body": f"__EXC__ {type(e).__name__}: {e}"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--proxy", default="http://127.0.0.1:18001",
                    help="kubectl proxy 地址；与 --base 二选一")
    ap.add_argument("--base", default=None, help="直接给网关/服务地址（如 http://ip/api/gaop）")
    ap.add_argument("--token", default=None, help="已有 gateway-token；不给则自动登录")
    ap.add_argument("--workers", type=int, default=2, help="并发数，默认 2（厂商 pod 只有 1Gi）")
    ap.add_argument("--json", default=None, help="把明细写到文件")
    args = ap.parse_args()

    if args.base:
        base = args.base.rstrip("/")
    else:
        base = (args.proxy.rstrip("/") + "/api/v1/namespaces/gaop-namespace"
                "/services/gaop-be:8080/proxy")
    token = args.token or login(base)

    catalogue = json.loads(CATALOGUE.read_text())
    seen = collections.OrderedDict()
    for x in catalogue:
        if x["method"].upper() == "GET":
            seen.setdefault(x["url"], x.get("query") or [])
    now_ms = int(time.time() * 1000)
    tasks = []
    for u, qs in seen.items():
        params = {q["name"]: VALUES.get(q["name"], "1") for q in qs}
        if u == "/audit":  # 审计的 start/end 是 Long 毫秒，与 dashboards 的 Date 不同
            params["start"], params["end"] = str(now_ms - 3600000), str(now_ms)
        tasks.append((base, token, u, params))

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        results = list(ex.map(probe, tasks))
    if args.json:
        pathlib.Path(args.json).write_text(json.dumps(results, ensure_ascii=False, indent=1))

    ok = [r for r in results if 200 <= r["status"] < 300]
    bad = [r for r in results if not 200 <= r["status"] < 300]
    print(f"通过 {len(ok)} / 失败 {len(bad)} / 共 {len(results)}"
          f"  ({len(ok) / len(results) * 100:.0f}%)")
    for r in sorted(bad, key=lambda x: x["url"]):
        print(f"{r['status']:>3} {r['ms']:>6}ms {r['url'][:110]}")
        print(f"     {r['body'].replace(chr(10), ' ')[:220]}")
    return 0 if not bad else 1


if __name__ == "__main__":
    sys.exit(main())
