#!/usr/bin/env python3
"""从运行中的厂商后端抓只读端点样本，作为重写后的契约比对基线。

用法：
  kubectl proxy --port=8001 --address=127.0.0.1
  python3 tools/capture-vendor-samples.py

登录口令按需通过环境变量 GAOP_USER / GAOP_PASSWORD / GAOP_ROLE 提供（默认不写进仓库）。
样本落在 docs/findings/runtime-samples/，清单写入同目录 manifest.json。
"""
import hashlib
import json
import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / 'docs/findings/runtime-samples'
BASE = os.environ.get(
    'GAOP_BASE',
    'http://127.0.0.1:8001/api/v1/namespaces/gaop-namespace/services/gaop-be:8080/proxy')
USER = os.environ.get('GAOP_USER', 'admin')
PASSWORD = os.environ.get('GAOP_PASSWORD', '')
ROLE = os.environ.get('GAOP_ROLE', 'admin')
COOKIE = '/tmp/gaop-cookies.txt'


def curl(args, **kw):
    return subprocess.run(['curl', '--max-time', '25', '-s', *args], capture_output=True, text=True, **kw)


def login():
    if not PASSWORD:
        print('未提供 GAOP_PASSWORD，跳过登录（只能抓无需认证的端点）', file=sys.stderr)
        return
    body = json.dumps({'username': USER, 'password': PASSWORD, 'role': ROLE})
    r = curl(['-o', '/dev/null', '-c', COOKIE, '-X', 'POST', f'{BASE}/v1/pub/login',
              '-H', 'Content-Type: application/json', '-d', body])
    print(f'登录: rc={r.returncode} cookie={"gateway-token" in pathlib.Path(COOKIE).read_text() if pathlib.Path(COOKIE).exists() else False}')


def slug(path):
    return path.strip('/').replace('/', '_') or 'root'


def main():
    login()
    paths = [l.strip() for l in (ROOT / 'tools/vendor-sample-endpoints.txt').read_text(encoding='utf-8').splitlines()]
    paths = [p for p in paths if p and not p.startswith('#')]
    manifest = []
    for path in paths:
        out = OUT / f'{slug(path)}.json'
        res = curl(['-b', COOKIE, '-o', str(out), '-w', '%{http_code} %{size_download}', f'{BASE}{path}'])
        code, size = (res.stdout or '0 0').split()
        raw = out.read_bytes() if out.exists() else b''
        # 安全：绝不把令牌写进仓库
        if b'gateway-token' in raw:
            out.write_text('{"_redacted": "响应含令牌，已清空"}', encoding='utf-8')
            raw = b''
        manifest.append({'path': path, 'file': out.name, 'httpStatus': int(code),
                         'bytes': int(size), 'sha256': hashlib.sha256(raw).hexdigest()[:16]})
        print(f"{code:>4} {size:>8} B  {path}")
    (OUT / 'manifest.json').write_text(json.dumps({
        'capturedAt': subprocess.run(['date', '-u', '+%FT%TZ'], capture_output=True, text=True).stdout.strip(),
        'base': BASE, 'endpoints': manifest}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'\n清单写入 {OUT / "manifest.json"}（{len(manifest)} 条）')


if __name__ == '__main__':
    main()
