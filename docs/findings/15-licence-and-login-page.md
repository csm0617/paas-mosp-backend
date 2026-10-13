# 授权端点 + 登录页打通（2026-10-14 03:35）

## 本轮实现并核对

| 端点 | 实现 | 与厂商样本 |
| --- | --- | --- |
| `GET /v1/licence` | 机器码（配置项，厂商由本机文件推导）+ `existGis=false` | 键一致；`code` 是环境相关值（我方为占位） |
| `GET /v1/licence/info` | 未激活分支：读 `g_setting['system.active.trial_period']`，缺失则落库 now+30 天 | 键一致，**`trialPeriod` 与厂商完全相同**（同一份 `g_setting`） |
| `GET /ws/info` | SockJS 握手信息（websocket/cookie_needed/origins/entropy） | 厂商无样本，按 SockJS 协议给出 |

依据：厂商 `LicenceService.getLicenceInfo()`（`:937` 起）与 `getActiveCode()`（`:390`：优先授权文件，其次 `g_setting['system.active.code']`）。
激活码校验（RSA、机器码派生）属 `gaop-auth` 域，本实现只覆盖"未激活 → 试用期"这条可观测路径，并在代码注释里写明。

## 效果：登录页从"不可交互"变成"可交互"

补齐 `/v1/licence/info` 后重跑登录页：

| 观测 | 变化 |
| --- | --- |
| 账号下拉 | **1 个选项（admin）**——数据来自我们刚实现的 `/v1/user/get` |
| 门户下拉 | **2 个选项（运维管理员门户 / 席位操作员）**——数据来自 `/v1/role` |
| 选中账号后前端的行为 | 前端自己发起了 `GET /v1/role?username=admin`、`GET /v1/user/get?…&keyword=admin` —— 说明**它的表单逻辑真的跑起来了** |
| 页面文本 | 从"正在获取授权信息…"变成可操作的表单 |

## 仍然没做完的一步（工具问题，不是产品问题）

用 puppeteer 自动**填口令**这一步反复失败：点 `#basic_password` 时，文本会串到仍持有焦点的账号下拉里
（前端日志里出现 `keyword=adminadmin123` 即证据）；改用坐标点击/`ElementHandle.click + type`/React 原生 setter 兜底
都没稳定成功，且失败时脚本会因为 `#basic_password` 解析异常而中断。

**已确认的不是**：不是页面白屏、不是端点缺数据、不是认证问题——表单本身可用（上面的下拉与它自发起的请求都是证据）。
**待解的是**：如何在这个 antd 表单上稳定地把口令写进去（下一步试：先 `blur` 掉 Select、或直接对 `form` 派发 React 事件、或改用 CDP 的 `Input.insertText`）。

## 其它仍未实现的（不阻塞登录页，但会刷错误日志）

- `GET /v1/licence/checkAndActiveGis` → 404（前端会调）
- SockJS 的真实传输端点 `/ws/<server>/<session>/{eventsource,jsonp,htmlfile}` → 404（前端会轮询重试；`/ws/info` 只解决握手）
- 前端还会尝试连 `ws://localhost:9999/`（前端自身硬编码的 socket 地址，与我们无关）
