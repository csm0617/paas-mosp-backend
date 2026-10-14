/**
 * `/topic/userInfo` 推送验证探针（第 114 轮新增）。
 *
 * 用途：确认 `POST /v1/user/update_avatar/{userId}` 真的**推到了订阅端**——而不是只打了日志。
 * 做法：用 `ws` 连 SockJS 的裸 WS 端点 `/ws/websocket`，手工发 STOMP `CONNECT`/`SUBSCRIBE`，
 * 再触发一次 update_avatar，断言收到 `destination:/topic/userInfo` 的 `MESSAGE`。
 *
 * 运行（NODE_PATH 指向装了 ws 的 node_modules，例如 /tmp/e2e/node_modules）：
 *   NODE_PATH=/tmp/e2e/node_modules node tools/ws-userinfo-probe.js <userId> [http://127.0.0.1:8080]
 *
 * 注意：会**写** `g_user.avatar`，跑完请恢复原值（本会话用 `UPDATE g_user SET avatar=NULL ...` 还原）。
 */
const WebSocket = require('ws');
const http = require('http');
const USERID = process.argv[2];
const sock = new WebSocket('ws://127.0.0.1:8080/ws/websocket');
const NUL = '\u0000';
let got = null;
sock.on('open', () => {
  sock.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n' + NUL);
});
sock.on('message', (data) => {
  const text = data.toString();
  if (text.startsWith('CONNECTED')) {
    sock.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/userInfo\n\n' + NUL);
    setTimeout(trigger, 300);
  } else if (text.startsWith('MESSAGE')) {
    got = text;
    console.log('收到 MESSAGE:\n' + text.replace(/\u0000/g, '\\0'));
    process.exit(0);
  }
});
function trigger() {
  const boundary = '----probe' + Date.now();
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="a.png"\r\nContent-Type: image/png\r\n\r\n`),
    Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]),
    Buffer.from('\r\n'),
    Buffer.from(`--${boundary}--\r\n`)
  ]);
  const req = http.request({ host: '127.0.0.1', port: 8080, path: `/v1/user/update_avatar/${USERID}`, method: 'POST',
    headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}`, 'Content-Length': body.length } },
    (res) => { console.log('update_avatar HTTP ' + res.statusCode); res.resume(); });
  req.end(body);
}
setTimeout(() => { console.log(got ? '超时前收到过消息' : '**超时：没有收到 /topic/userInfo 的 MESSAGE**'); process.exit(got ? 0 : 1); }, 6000);
