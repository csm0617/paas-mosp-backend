# 根因：`GET /v1/user` 返回的是 `UserLogined`（20 键，含 role）——我又把形状记错了

## 根因链（逐行可查）

1. 前端 `getInitialState`：`const t = await (0,E.umG)({}); return {currentUser: t.data, settings: v}`；
2. `umG` → `function J(){ var t={url:"/v1/user", method:"get", ...} }`，即 **`GET /v1/user`**（`umi.d08aa716.js`）；
3. 路由访问控制：`function $T(e){ var r = e.currentUser; return { canAdmin: r && "admin" === r.role?.key, canDev: ... } }`；
4. `/software` 路由带 `access: "canAdmin"` → **缺 `role` 就判 false → 路由守卫把 `/software/workloads` 弹回登录页**。

## 我错在哪

第 6 轮我从运行时样本里读出"`/v1/user` 是 14 个键"，据此把 DTO 定成 14 键——**那是我的打印语句截断造成的假象**。
厂商实际返回的是 `UserLogined`（**20 键**：id…avatar + `token`、`role`、`roles`、`subRoles`、`settingList`、`style`），
**与登录响应同一形状**（`UserController.info()` 的返回类型就是 `Response<UserLogined>`）。
本轮把 `/v1/user` 改成复用 `LoginAssembler`（即登录那套 20 键装配），并给 `read-paths.json` 补上 `data.role.key`。

**这是本会话第三次"凭截断/示意数据建模"翻车**（前两次：登录内嵌 role 的 14 vs 16 键、中文乱码归因）。
教训已经很清楚：**形状类断言必须用"整个键集合相等"，不能靠眼睛数**。仓库里的两个回放测试正是这么写的。

## 效果（2026-10-14 03:5x 实测）

`/v1/user` 修好后，真实前端打开 `/gaop/software/workloads` **不再被弹回登录页**：
URL 停在目标页，页面渲染出完整外壳（左侧菜单、训练模式、admin、授权提示），并弹出"快速入门"引导（因为
`/v1/pub/quickStart` 返回 false，语义正确）。这一轮同时补了 `/v1/pub/quickStart` 与 `/v1/licence/checkAndActiveGis`（都为 200）。

**这就是门槛里"厂商前端一行不改"那条的实证**：前端未改一行，登录、取数、路由守卫全部走通。

## 页面随后暴露出的新缺口（下一轮）

进工作负载页后，前端又调了三个我们还没实现的端点（当前 404）：

| 端点 | 语料库 |
| --- | --- |
| `GET /v1/image/projectList?page=1&size=999` | ✅ 已录样本（`runtime-samples/v1_image_projectList.json`） |
| `GET /v1/role/software` | ✅ 已录样本（`{list:[],total:0}`） |
| `GET /v1/pub/etcd_label/check` | ❌ 未录，需从厂商抓 |

此时表格行数仍为 0——多半是引导弹窗还没关（"知道了/不再提示"）以及依赖上面这些端点；
下一轮先把弹窗关掉并补齐这三个端点，再看工作负载表是否出行。
