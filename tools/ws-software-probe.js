/**
 * `/topic/software` 推送验证探针（第 142 轮新增）。
 *
 * 用途：确认 `PUT /v1/software/softwareRelateRole` 真的把"删除关联"推到了订阅端
 * （厂商 `updateSoftwareRelateRole:1546-1548` 的 `convertAndSend("/topic/software", softwareStatus)`），
 * 而不是只改了库。
 *
 * 做法：用 `ws` 连 SockJS 的裸 WS 端点 `/ws/websocket`，手工发 STOMP `CONNECT`/`SUBSCRIBE`，
 * 再触发一次 softwareRelateRole，断言收到 `destination:/topic/software` 的 `MESSAGE`。
 *
 * 运行（NODE_PATH 指向装了 ws 的 node_modules，例如 /tmp/e2e/node_modules）：
 *   NODE_PATH=/tmp/e2e/node_modules node tools/ws-software-probe.js <softwareId> '<roleKeyJson>' [http://127.0.0.1:8080]
 * 例：NODE_PATH=/tmp/e2e/node_modules node tools/ws-software-probe.js sw-rr '["user"]'
 *
 * 注意：会**写** `g_role_software`，跑完请还原（本会话用 DELETE 探针行还原）。
 */
const WebSocket = require('ws');
const http = require('http');
const SOFTWARE_ID = process.argv[2];
const ROLE_KEYS = process.argv[3] || '[]';
const BASE = process.argv[4] || 'http://127.0.0.1:8080';
const sock = new WebSocket(BASE.replace('http', 'ws') + '/ws/websocket');
const NUL = '\u0000';
let got = null;

sock.on('open', () => {
  sock.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n' + NUL);
});
sock.on('message', (data) => {
  const text = data.toString();
  if (text.startsWith('CONNECTED')) {
    sock.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/software\n\n' + NUL);
    setTimeout(trigger, 300);
  } else if (text.startsWith('MESSAGE')) {
    got = text;
    console.log('收到 MESSAGE:\n' + text.replace(/\u0000/g, '\\0'));
    process.exit(0);
  }
});

function trigger() {
  const url = new URL(BASE + '/v1/software/softwareRelateRole?softwareId=' + encodeURIComponent(SOFTWARE_ID));
  const req = http.request({
    hostname: url.hostname, port: url.port, path: url.pathname + url.search, method: 'PUT',
    headers: {'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(ROLE_KEYS)}
  }, (res) => {
    let body = '';
    res.on('data', (c) => body += c);
    res.on('end', () => console.log('接口返回 http=' + res.statusCode + ' body=' + body));
  });
  req.end(ROLE_KEYS);
}

setTimeout(() => {
  console.log(got ? '超时前收到过消息' : '**超时：没有收到 /topic/software 的 MESSAGE**');
  process.exit(got ? 0 : 1);
}, 8000);
