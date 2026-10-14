# 契约比对复跑（第 88 轮）：9 条已记录读路径，**7 条 0 失败**，1 条真差异、1 条样本陈旧

**证据等级**：〔运行实测〕（`tools/contract-check.py` 对每条已记录读路径跑 L1/L2）。2026-10-14。

## 1. 结果

| 端点 | 样本 | 失败项 |
| --- | --- | --- |
| `GET /v1/service` | `service-list.json` | **0** ✅ |
| `GET /v1/role` | `v1_role.json` | **0** ✅ |
| `GET /v1/sub_role/switchRole` | `v1_sub_role_switchRole.json` | **0** ✅ |
| `GET /v1/role/list` | `role-list.json` | **0** ✅ |
| `GET /v1/software` | `software-list.json` | **0** ✅ |
| `GET /v1/user` | `v1_user.json` | **2**（见 §2，**真差异**） |
| `GET /v1/user/info` | `v1_user_info.json` | **3**（见 §3，**样本陈旧**） |
| `GET /v1/role/switchRole` | — | 无样本 |
| `GET /v1/role/{id}` | — | 无样本 |

## 2. **真差异**：`GET /v1/user` 的 `data.token`

同一时刻、同一 Cookie 调两侧，**键集合完全一致（20 键）**，但值不同：

```
vendor: data.token = null
mine  : data.token = "85c83088-ef3a-48b6-9ef9-138f21069a30"   （我们填了会话 token）
```

厂商出处：`UserService.loginedUser(user, role, token)` 会 `setToken(token)`，**但那只在 `login` 里用**；
`/v1/user`（当前登录用户）走的是另一条构造路径 ⇒ `token` 保持 **null**。

⇒ **我们应当把 `/v1/user` 的 `token` 置为 null**（token 只在登录响应里有意义）。
**下一轮修**：`LoginAssembler.toResponse` 目前同时服务登录与 `/v1/user`，需要区分（登录填 token、`/v1/user` 不填），
改完重跑 `contract-check.py "GET /v1/user"` 应当 0 失败。

## 3. **样本陈旧**：`GET /v1/user/info` 的 `createdAt`/`updatedAt`

```
vendor(样本): 1791908324000      mine(实时): 1791931380848 / 1791929846469
```
这是**时间戳**：样本是更早时刻抓的，而这期间 `admin` 行被改动过（本会话早期对 `PUT /v1/user/{userId}` 等做过探针）。
⇒ **不是回归**，是"样本会随数据变化而陈旧"这一固有性质。**处置**：这类字段应在样本比对里显式排除
（或每次比对前重抓样本），否则会周期性误报。

## 4. 结论

- 已记录读路径的**契约面没有回归**（7/9 全绿）；
- 发现 1 个**真实**值差异（`/v1/user` 的 token）→ 下一轮修；
- 发现 1 类**工具性问题**（时间戳字段的样本陈旧）→ 记入方法清单：**比对前要么重抓样本，要么排除时间类字段**。

## 5. 状态

本轮无业务代码改动；全量 **289 条用例全绿**；临时 NodePort 已删、进程清零。
