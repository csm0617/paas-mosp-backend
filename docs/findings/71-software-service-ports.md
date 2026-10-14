# `GET /v1/software/{softwareId}/service/{serviceName}`：端口映射列表，三种情形对拉全等

**证据等级**：〔运行实测〕（探针行 + 对拉）+〔代码事实〕。2026-10-14。

## 1. 实测（3 行 `g_service_port` 探针）

| 请求 | vendor | mine | 结果 |
| --- | --- | --- | --- |
| `.../probeport/service/svc-a`（两条） | 2 条 8 键 | 同 | **完全相等** ✅ |
| `.../probeport/service/svc-b`（一条） | 1 条 | 同 | **完全相等** ✅ |
| `.../probeport/service/nope`（不存在） | `[]` | `[]` | **完全相等** ✅ |

条目形状 8 键：`{id, softwareId, serviceName, port, pathName, webEntry, allow, updatedAt}`。

## 2. 厂商规则（`SoftwareService.servicePortList:2304-2306`）

```java
select where software_id = ? and service_name = ?
```
- 只有**两个等值条件**（不像 `/v1/software` 列表那样还有 name/type/排序）；
- **不排序**（厂商没写 `orderBy`）——照抄，不"顺手加个稳定排序"；
- 查不到 → **空数组**，不是 404/400（这点与 `GET /v1/software/{id}` 的 400 形成对照）。

## 3. 一处与"软件域其它端点"的对照（容易套错）

| 端点 | 查不到时 |
| --- | --- |
| `GET /v1/software/{id}`（详情） | 400 `software.software.id.not.found` |
| `GET /v1/software/{id}`（删除用） | 400 `software.not.exist.by.id` |
| **本端点** | **空数组** |
| `GET /v1/software/{id}/users` 类（战位下用户） | 空列表 |

**同一个 `/v1/software/...` 前缀下，"找不到"有四种不同表现**——这类差异只能靠逐个端点核对，
不能靠"同一模块应该一致"来推断。

## 4. 状态

K2 跨域端点 **+1**；测试 **41 类 248 条全绿**（上一轮 246）。
探针行已删（库内 0 残留）、临时 NodePort 已删、进程清零、工作树干净。
