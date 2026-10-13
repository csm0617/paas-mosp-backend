# 切片 3b 端到端实测：厂商原样前端渲染出我们的「席位管理」页

**证据等级**：〔运行实测〕。2026-10-14，本机 `paas-core` + 集群 `172.16.209.25` 真实 MySQL/Redis/K8s。

## 1. 结果

厂商前端一行不改，`/gaop/usermanage/role`（席位管理）用**我们的后端**渲染出 9 列表头 + 1 行真实数据：

| 列 | 值（我方后端） | 来自哪个字段 |
| --- | --- | --- |
| 席位名称 | 运维管理员门户（终端显示为双重编码的字节，与厂商一致） | `list[].name` |
| 唯一标识 | `admin` | `list[].key` |
| 关联应用 | 「运维管理员门户」芯片 | `list[].softwareList`（**admin 的硬编码伪条目**） |
| 关联子角色 | 「+ 添加子角色」 | `list[].subRoleList`（空数组） |
| 更新时间 | 2026-10-14 00:18:44 | `list[].updatedAt` |
| **已登录用户** | **1** | `list[].onLineUserList`（**来自 Redis 扫描**） |
| 席位描述 | 平台运维管理员门户（…） | `list[].desc` |
| 操作 | 配置 / 删除 | `list[].key` 客户端比较 |

截图：[我们](artifacts/2026-10-14-role-page.png) / [厂商](artifacts/2026-10-14-role-page-vendor.png)。

**两个"最容易做错"的点都拿到了运行证据**：
① 「关联应用」显示的是**伪门户条目**（`g_role_software` 在本环境为空，值完全是后端硬编码出来的）；
② 「已登录用户 = 1」证明 `onLineUserList` 真的来自 **Redis `login-list*` 扫描**并被页面消费。

## 2. 五个端点与厂商**逐字节相等**

同一时刻、同一库、同一会话下逐端点 JSON 全等：

```
✅ equal=True  /v1/role/switchRole?page=1&size=20&type=1
✅ equal=True  /v1/role/switchRole?page=1&size=20&type=2
✅ equal=True  /v1/role/list?page=1&size=20
✅ equal=True  /v1/role?page=1&size=20
✅ equal=True  /v1/role/11111111-1111-1111-1111-111111111111
```

**A/B 对照**（同一份前端、两个后端）：标题 `席位管理` / 9 个表头 / 数据行 / 左侧导航**全部一致**。

## 3. 四个读端点的集合填充差异（本轮真正的难点）

四个端点返回**同一个 16 键模型**，差别只在哪些集合被填 —— 这是最容易被"顺手统一"破坏的地方：

| 端点 | `onLineUserList` | `softwareList` | `userList` | `subRoleList` | `nameFr`/`nameUs` |
| --- | --- | --- | --- | --- | --- |
| `/v1/role` | null | ✅ | null | null | null |
| `/v1/role/list` | null | ✅ | ✅ | null | null |
| `/v1/role/switchRole` | ✅（数组，无人为 `[]`） | ✅ | ✅ | ✅ | null |
| `/v1/role/{id}` | null | **null** | null | null | **兜底为 `name`** |

两个反直觉处：`/v1/role/{id}` 的 `softwareList` **是 null**（不做伪门户覆盖，与列表端点相反）；
`/v1/role/switchRole` 的 `onLineUserList` **恒为数组**（`[]`），而 `/v1/role` 是 **null**。

## 4. 遗留缺口（明确记录，不掩盖）

`/usermanage/role` 页会调 `GET /api/gaop/v1/software`，**我们 404、厂商 200**。
它属于 **K2 软件目录**（`/v1/software` 列表），不在切片 3 范围内 —— 即**战位页的"关联应用"对话框需要 K2**。
其余 `/api` 差异只有 SockJS 传输形态（`xhr_streaming` vs WS 升级，两侧都 200/204）。

## 5. 一处未定论

厂商 `getSubRoleListByRoleId` 用 **`HashSet`** 去重，**多元素时顺序不确定**（`SubRoleServiceImpl.java:391-396`）。
重写版按关联行顺序返回（更确定），因此**子角色多于一个时无法与厂商逐字节对齐**。
本环境该集合为空，暂无法实测；已记入 `read-paths.json` 的 L3 账。

## 6. 复现

同 `docs/findings/26-e2e-user-page.md` 第 2 节，页面换成 `/gaop/usermanage/role`。
清理已执行：两个 harness、后端、`kubectl proxy` 全部停止，`gaop-redis-tmp` 已删除，端口实测不可达。
