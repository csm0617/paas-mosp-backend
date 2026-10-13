#!/usr/bin/env python3
"""本地 e2e harness：静态托管复刻出来的厂商前端，并把 `/api/gaop/**` 反代到重写后端。

用法：
  GAOP_TOKEN=<会话 cookie 值> DIST=<前端产物目录> python3 tools/frontend-e2e-harness.py [端口]

设计要点：
- 浏览器访问的是**原样的厂商前端**（一行未改），只是把它背后的 API 指到了 paas-core；
- 每个代理请求都打日志（方法/路径/状态/耗时），用来核对"页面实际发了哪些接口、是否都成功"；
- 非 `/api/gaop/**` 的其它后端前缀（`/api/v2/**`、`/k8s/**`、`/cloud/**` 等）**不代理**，直接 404 并记日志——
  这样能一眼看出还有哪些前缀属于别的服务（本切片的边界之外）。
"""
import http.server
import json
import os
import socketserver
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
DIST = Path(os.environ.get('DIST', '/Users/csm/Desktop/Project/msop/build/gaop-web-ctx/html'))
BACKEND = os.environ.get('BACKEND', 'http://127.0.0.1:8080')
TOKEN = os.environ.get('GAOP_TOKEN', '')
API_PREFIX = '/api/gaop'
# 厂商前端以 routerBase=/gaop/ 发布（index.html 里的资源都是 /gaop/xxx 绝对路径）
WEB_PREFIX = '/gaop'
LOG: list[dict] = []


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(DIST), **kw)

    def log_message(self, *a):  # 静音默认日志（我们用 LOG 自己记）
        pass

    def _proxy(self, method):
        rest = self.path[len(API_PREFIX):] or '/'
        url = BACKEND + rest
        length = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(length) if length else None
        req = urllib.request.Request(url, data=body, method=method)
        for h in ('Content-Type', 'Accept'):
            if self.headers.get(h):
                req.add_header(h, self.headers[h])
        if TOKEN:
            req.add_header('Cookie', f'gateway-token={TOKEN}')
        started = time.time()
        resp_headers = None
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                status, payload = resp.status, resp.read()
                ctype = resp.headers.get('Content-Type', 'application/json')
                resp_headers = resp.headers
        except urllib.error.HTTPError as e:
            status, payload, ctype = e.code, e.read(), 'application/json'
            resp_headers = e.headers
        except Exception as e:  # noqa: BLE001
            status, payload, ctype = 599, json.dumps({'error': str(e)}).encode(), 'application/json'
        entry = {'method': method, 'path': rest, 'status': status,
                 'ms': int((time.time() - started) * 1000)}
        LOG.append(entry)
        with open('/tmp/harness-log.jsonl', 'a', encoding='utf-8') as fh:   # 流式，便于随时查看
            fh.write(json.dumps(entry, ensure_ascii=False) + '\n')
        self.send_response(status)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(payload)))
        # 把后端的 Set-Cookie 原样透给浏览器（登录流程必需）
        for hname in ('Set-Cookie',):
            for hv in resp_headers.get_all(hname, []) if resp_headers else []:
                self.send_header(hname, hv)
        self.end_headers()
        if method != 'HEAD':
            self.wfile.write(payload)

    def do_GET(self):
        if self.path.startswith(API_PREFIX):
            return self._proxy('GET')
        if self.path.startswith(('/api/', '/k8s/', '/cloud/', '/tenx/')):
            entry = {'method': 'GET', 'path': self.path, 'status': 404, 'ms': 0,
                     'note': '非本切片的服务前缀，未代理'}
            LOG.append(entry)
            with open('/tmp/harness-log.jsonl', 'a', encoding='utf-8') as fh:
                fh.write(json.dumps(entry, ensure_ascii=False) + '\n')
            self.send_error(404)
            return
        # 静态资源挂在 /gaop/ 下（厂商前端的 routerBase）
        raw = self.path.split('?')[0]
        if raw.startswith(WEB_PREFIX):
            raw = raw[len(WEB_PREFIX):] or '/'
        elif raw != '/':
            self.send_error(404)
            return
        target = DIST / raw.lstrip('/')
        self.path = raw if target.exists() and target.is_file() else '/index.html'
        return super().do_GET()

    def do_POST(self):
        if self.path.startswith(API_PREFIX):
            return self._proxy('POST')
        self.send_error(404)

    def do_PUT(self):
        if self.path.startswith(API_PREFIX):
            return self._proxy('PUT')
        self.send_error(404)

    def do_DELETE(self):
        if self.path.startswith(API_PREFIX):
            return self._proxy('DELETE')
        self.send_error(404)

    def do_OPTIONS(self):
        self.send_response(204)
        self.end_headers()


if __name__ == '__main__':
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.ThreadingTCPServer(('127.0.0.1', PORT), Handler) as httpd:
        print(f'harness 就绪: http://127.0.0.1:{PORT}  dist={DIST}  backend={BACKEND}  token={TOKEN[:8]}…')
        print('按 Ctrl-C 结束后会把调用日志写入 /tmp/harness-log.json')
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            Path('/tmp/harness-log.json').write_text(json.dumps(LOG, ensure_ascii=False, indent=2), encoding='utf-8')
            print('已写出 /tmp/harness-log.json')
