# 顺着 e2e 线索查到的**真实缺口**：`GET /v1/image/projectList` 我们返回空、厂商返回 2 个项目

**证据等级**：〔运行实测〕（A/B 对拉 + 前端 HTML 对比）+〔代码事实〕。2026-10-14。

## 1. 线索是怎么来的

第 68 轮 e2e 复验发现两页 HTML 大小不同（55KB vs 101KB）。第 69 轮把它查清：

- 两页**可见文本都是 64 行且完全相同**（我第 68 轮写的"厂商多若干导航条目"是**猜测，已被否掉**）；
- 真正差异是 **`data:image` 内联图片 5 vs 15**，多的 10 个在**左侧导航项的图标**上；
- 于是去比对"前端调用的端点集合"：**两侧完全相同**（我们只多出 SockJS 回退的 `/ws/.../xhr_*`）。

⇒ 差异不在"少调了哪个端点"，而在**同一个端点的响应内容**。逐个 A/B 共享端点后定位到它：

| 端点 | vendor | mine |
| --- | --- | --- |
| `/v1/pub/quickStart` | 相等 | 相等 |
| `/v1/mode/current` | 相等 | 相等 |
| `/v1/lang/current` | 相等 | 相等 |
| `/settings` | 相等（3930B） | 相等 |
| `/v1/sub_role/switchRole` | 相等 | 相等 |
| **`/v1/image/projectList`** | **2 个项目**（551B） | **`{list:[],total:0}`（58B）** ❌ |

## 2. 厂商返回的真实数据（Harbor 项目列表）

```json
{"list":[
 {"creation_time":"+58753-04-29T03:17:20+08:00","update_time":null,"togglable":null,"current_user_role_id":null,
  "metadata":null,"deleted":null,"owner_name":null,"project_id":3,"owner_id":null,"name":"library","repo_count":null},
 {"creation_time":"+58753-04-29T03:17:00+08:00",...,"project_id":2,...,"name":"gaop",...}
],"total":2}
```

条目 11 键：`creation_time/update_time/togglable/current_user_role_id/metadata/deleted/owner_name/project_id/owner_id/name/repo_count`。
`creation_time` 是 `+58753-04-29…`（公元 58753 年）——**明显是畸形值**，但那是 Harbor 里真实存着的东西，
不是厂商编的。

## 3. 实现路径（厂商代码事实）

`ImageController.projectList:244-248` → `imageServiceInstance.projectList(page,size,keyword)` →
`K8sImageServiceImpl.projectList:107-119`：

```
harbor.allPageProjects(keyword)                       // Harbor API
  → 过滤掉 system_containers / system_store
  → 按 creation_time 倒序
  → skip((page-1)*size).limit(size)
```

**我们的实现是个 stub**（早期为让前端不报错而写的空列表），所以这是**一个真实缺口**，
不是"环境缺设施"。

## 4. 一条要更正的重要判断：**Harbor 其实是通的**

`findings/52` 把 `imageStorage` 归为"依赖缺失的 Harbor 设施"。但本轮证据表明
**Harbor 在本环境是可访问的**——厂商正是从它读出了 `library`/`gaop` 两个项目
（`imageStorage` 返回的 `{free:0,total:0}` 也就更可能是 Harbor 的**真实回答**，而不是"没接上"）。

⇒ **影像域（Harbor）应当从 C 类（无法对拉）挪出来，变成一个可实现的域**：
有真实对端、能对拉、能在前端页面上看到效果。这是本轮最有价值的结论。

## 5. 下一轮

1. 找到 Harbor 的地址/凭据来自哪里（配置项？`g_setting`？K8s Service？）；
2. 按 `K8sImageServiceImpl.projectList` 的三步实现 `/v1/image/projectList` 并与厂商对拉（应当能逐字节相同）；
3. 顺带把 `imageStorage` 一起做掉（同一个 Harbor 客户端）。

## 6. 状态

本轮无业务代码改动（探查 + 更正结论）；全量用例仍 **41 类 262 条全绿**；
临时 NodePort 已删、进程清零、工作树干净。
