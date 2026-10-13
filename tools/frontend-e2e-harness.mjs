#!/usr/bin/env node
/**
 * e2e harness（Node 版，零依赖）：静态托管复刻前端 + HTTP 反代 `/api/gaop/**` + **支持 WebSocket 升级**。
 *
 * 为什么需要它：前端的实时通道是 `SockJS("/ws")`，Python 版 harness 无法处理 Upgrade，
 * 浏览器只能降级到 xhr-polling；这会让"A/B 对照"混入传输差异。本版把 Upgrade 也透传。
 *
 * 用法：
 *   GAOP_TOKEN=<会话值> DIST=<前端目录> BACKEND=http://127.0.0.1:8080 node tools/frontend-e2e-harness.mjs 8099
 */
import http from 'node:http';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import { URL } from 'node:url';

const PORT = Number(process.argv[2] || 8099);
const DIST = process.env.DIST || '/Users/csm/Desktop/Project/msop/build/gaop-web-ctx/html';
const BACKEND = new URL(process.env.BACKEND || 'http://127.0.0.1:8080');
const TOKEN = process.env.GAOP_TOKEN || '';
const WEB_PREFIX = '/gaop';
const API_PREFIX = '/api/gaop';
const LOG_FILE = '/tmp/harness-node-log.jsonl';

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.ico': 'image/x-icon',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.map': 'application/json',
};

const log = (entry) => {
  try { fs.appendFileSync(LOG_FILE, JSON.stringify(entry) + '\n'); } catch { /* ignore */ }
};

function writeStatic(res, filePath) {
  const ext = path.extname(filePath).toLowerCase();
  res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
  fs.createReadStream(filePath).pipe(res);
}

const BACKEND_PREFIX = BACKEND.pathname.replace(/\/$/, '');

function proxyHttp(req, res) {
  const rest = BACKEND_PREFIX + (req.url.slice(API_PREFIX.length) || '/');
  const started = Date.now();
  const options = {
    hostname: BACKEND.hostname, port: BACKEND.port, path: rest, method: req.method,
    headers: { ...req.headers, cookie: TOKEN ? `gateway-token=${TOKEN}` : (req.headers.cookie || '') },
  };
  const upstream = http.request(options, (up) => {
    log({ status: up.statusCode, method: req.method, path: rest.split('?')[0], ms: Date.now() - started });
    res.writeHead(up.statusCode, up.headers);
    up.pipe(res);
  });
  upstream.on('error', (e) => {
    log({ status: 599, method: req.method, path: rest.split('?')[0], error: String(e).slice(0, 80) });
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: String(e) }));
  });
  req.pipe(upstream);
}

const server = http.createServer((req, res) => {
  const raw = req.url.split('?')[0];
  if (raw.startsWith(API_PREFIX)) return proxyHttp(req, res);
  if (raw.startsWith('/api/') || raw.startsWith('/k8s/') || raw.startsWith('/cloud/') || raw.startsWith('/tenx/')) {
    log({ status: 404, method: req.method, path: raw, note: '非本切片的服务前缀，未代理' });
    res.writeHead(404); res.end('not proxied');
    return;
  }
  let rel = raw;
  if (raw.startsWith(WEB_PREFIX)) rel = raw.slice(WEB_PREFIX.length) || '/';
  else if (raw !== '/') { res.writeHead(404); res.end('not found'); return; }
  const file = path.join(DIST, rel.replace(/^\/+/, ''));
  if (fs.existsSync(file) && fs.statSync(file).isFile()) return writeStatic(res, file);
  return writeStatic(res, path.join(DIST, 'index.html'));
});

/** 关键能力：把 WebSocket 升级原样透传到后端（含 cookie 注入）。 */
server.on('upgrade', (req, socket, head) => {
  const rest = BACKEND_PREFIX + (req.url.startsWith(API_PREFIX) ? req.url.slice(API_PREFIX.length) : req.url);
  log({ status: 101, method: 'WS', path: rest.split('?')[0], note: 'upgrade 透传' });
  const upstream = net.connect(Number(BACKEND.port), BACKEND.hostname, () => {
    const headers = { ...req.headers, cookie: TOKEN ? `gateway-token=${TOKEN}` : (req.headers.cookie || '') };
    const lines = Object.entries(headers).map(([k, v]) => `${k}: ${v}`).join('\r\n');
    upstream.write(`${req.method} ${rest} HTTP/1.1\r\n${lines}\r\n\r\n`);
    if (head && head.length) upstream.write(head);
    upstream.pipe(socket);
    socket.pipe(upstream);
  });
  upstream.on('error', () => socket.destroy());
  socket.on('error', () => upstream.destroy());
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`node harness 就绪 http://127.0.0.1:${PORT}  dist=${DIST}  backend=${BACKEND.origin}  token=${TOKEN.slice(0, 8)}…  WS=on`);
});
