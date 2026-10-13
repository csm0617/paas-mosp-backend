#!/usr/bin/env node
/**
 * 用 CDP 驱动本机 Chrome 打开**原样的厂商前端**，核对：
 *   ① 页面能不能渲染（不是白屏）；② 它实际调了哪些 /api/gaop 接口、状态码如何；③ 控制台有没有报错。
 *
 * 用法：
 *   cd /tmp/e2e && npm i puppeteer-core         # 只装驱动，用本机 Chrome，不下载 Chromium
 *   GAOP_TOKEN=<会话值> node tools/frontend-e2e-check.js <url> <输出前缀>
 */
const fs = require('fs');
const puppeteer = require('puppeteer-core');

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const url = process.argv[2];
const outPrefix = process.argv[3] || '/tmp/e2e-page';
const token = process.env.GAOP_TOKEN || '';

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1600, height: 1000 });
  if (token) {
    await page.setCookie({ name: 'gateway-token', value: token, domain: '127.0.0.1', path: '/' });
  }

  const api = [];
  const consoleMsgs = [];
  page.on('response', (r) => {
    const u = r.url();
    if (u.includes('/api/')) api.push({ status: r.status(), method: r.request().method(), url: u.replace(/^https?:\/\/[^/]+/, '') });
  });
  page.on('console', (m) => consoleMsgs.push(`${m.type()}: ${m.text().slice(0, 300)}`));
  page.on('pageerror', (e) => consoleMsgs.push(`pageerror: ${String(e).slice(0, 300)}`));

  try {
    await page.goto(url, { waitUntil: 'networkidle2', timeout: 60000 });
  } catch (e) {
    console.log('goto 警告（继续）:', String(e).split('\n')[0]);
  }
  await new Promise((r) => setTimeout(r, 12000)); // 让异步数据落地

  const info = await page.evaluate(() => {
    const root = document.querySelector('#root');
    return {
      title: document.title,
      rootChildren: root ? root.childElementCount : -1,
      visibleText: (document.body.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 600),
      tableRows: document.querySelectorAll('.ant-table-tbody tr.ant-table-row').length,
      tables: document.querySelectorAll('table').length,
      url: location.href,
    };
  });

  fs.writeFileSync(`${outPrefix}.html`, await page.content(), 'utf8');
  fs.writeFileSync(`${outPrefix}.api.json`, JSON.stringify(api, null, 2), 'utf8');
  fs.writeFileSync(`${outPrefix}.console.txt`, consoleMsgs.join('\n'), 'utf8');

  console.log('--- 页面 ---');
  console.log('  url        :', info.url);
  console.log('  title      :', info.title);
  console.log('  #root 子节点:', info.rootChildren, '| table:', info.tables, '| 数据行:', info.tableRows);
  console.log('  可见文本    :', info.visibleText.slice(0, 220));
  console.log('--- /api 调用 ---');
  const seen = new Map();
  for (const a of api) seen.set(`${a.method} ${a.url}`, a.status);
  for (const [k, v] of [...seen.entries()].sort()) console.log(`  ${v}  ${k}`);
  console.log('--- 控制台（前 10 条）---');
  consoleMsgs.slice(0, 10).forEach((m) => console.log('  ' + m));
  await browser.close();
})();
