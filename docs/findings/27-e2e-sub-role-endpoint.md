# 切片 3c：子角色列表 `GET /v1/sub_role/switchRole`（用户管理页最后一个 404 清除）

**证据等级**：〔运行实测〕。2026-10-14，接续 `docs/findings/26-e2e-user-page.md`。

## 1. 结果

`/gaop/usermanage/account`（用户管理）现在**零 404**：

```
200 GET /api/gaop/settings
200 GET /api/gaop/v1/image/projectList?page=1&size=999
200 GET /api/gaop/v1/lang/current
200 GET /api/gaop/v1/licence/info
200 GET /api/gaop/v1/mode/current
200 GET /api/gaop/v1/pub/quickStart
200 GET /api/gaop/v1/role/software
200 GET /api/gaop/v1/sub_role/switchRole?page=1&size=9999   ← 本切片新增
200 GET /api/gaop/v1/user
200 GET /api/gaop/v1/user/info?page=1&size=10&keyword=
200 GET /api/gaop/ws/info
```

控制台无 `404`/`500`，有 `socket 连接成功`；表格仍为 1 行真实数据。
A/B 对照（同一份前端、两个后端）：**标题 / 表头 / 数据行 / 左侧导航全部一致**；
`/api` 集合的唯一差异是 **SockJS 传输形态**（某一轮我们走了 `xhr_streaming`+`xhr_send`、厂商走了 WS 升级），
两侧都是 200/204，属传输层竞态而非契约差异——**这条差异必须记下来，否则下次会被误判成缺口**。

## 2. 契约形状（实测抓取，含一个"为抓样本而临时写库"的过程）

本环境 `g_sub_role` 为空，直接抓只能得到空列表。为了拿到**非空**形状，向**厂商**后端做了一次可回收的探针：

```bash
B=http://127.0.0.1:18001/api/v1/namespaces/gaop-namespace/services/gaop-be:8080/proxy
# 1) 建探针子角色（厂商建的行，保证形状是厂商产出）
curl -s -X POST "$B/v1/sub_role" -H 'Content-Type: application/json' \
     -d '{"key":"probe-k1-20261014","name":"探针子角色","nameUs":"probe sub role","nameFr":"probe"}'
# 2) 抓样本
curl -s "$B/v1/sub_role/switchRole?page=1&size=20" \
     -o docs/findings/runtime-samples/v1_sub_role_switchRole_nonempty.json
# 3) **删除探针**（已确认回到空列表）
curl -s -X DELETE "$B/v1/sub_role/delete" -H 'Content-Type: application/json' \
     -d '{"subRoleIdList":["<上一步返回的 id>"]}'
```

**列表项 10 个键**（实测）：
`id, key, name, nameFr, nameUs, updatedAt, createdAt, desc, userList, roleList`

三条必须照抄的细节：

1. **列表接口的 `userList`/`roleList` 恒为数组**（无关联是 `[]`，不是 null）；
   而**新增接口** `POST /v1/sub_role` 返回的同名字段是 **`null`**——同一个模型两种形状，不能共用装配逻辑。
2. **`nameFr`/`nameUs` 在列表里恒为 `null`**（`switchRoleList` 只覆盖 `name`，不填这两个）；
   而 `GET /v1/role/{id}` 会填 `nameUs`——同一套 i18n 工具，不同端点行为不同。
3. **两条分支语义不同**：关键字为空 → `PageHelper` 分页、`total` 取分页总数；
   关键字非空 → `name LIKE`、**没有 `startPage`**（厂商漏了）、`total = list.size()`（搜到多少返回多少）。

## 3. 前端读路径（逐条有据）

`p__user__user-setting.8925a570.async.js` 里子角色下拉的原文：

```js
s.map(function (e) { return M.createElement(se, { value: e.id, key: e.id }, e.name); })   // 选项
s.filter(function (e) { return o.includes(e.name); }).map(function (e) { return e.id; })   // 回填已关联
```

⇒ L2 读路径 = `data.list`（容器）、`data.total`、`list[].id`、`list[].name`（4 条，已入 `read-paths.json`）。
`key/nameFr/nameUs/desc/updatedAt/createdAt/userList/roleList` **该页不读**，已记入 L3 账。

## 4. 一个必须记住的操作坑（与 26 号同源）

**`max-login=1`**：给两侧 harness 与浏览器**只能用同一个 token**。本轮所有 A/B 都是"只登录一次、token 三处复用"。

## 5. 复现

同 `docs/findings/26-e2e-user-page.md` 第 2 节；把页面换成 `/gaop/usermanage/account` 即可，
预期输出：**零 404**、`200 GET /api/gaop/v1/sub_role/switchRole`、表格 1 行。

**清理**（本轮已执行）：`pkill` 两个 harness 与后端、删 `gaop-redis-tmp`、停 `kubectl proxy`、端口实测不可达。
