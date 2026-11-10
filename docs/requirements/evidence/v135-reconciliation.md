# V1.3.5 线上对账（2026-11-10）

**现场**：`http://172.16.209.170/gaop/`（gaop-web `V1.3.5-20260805-1146-arm64`）+ `/api/gaop`（gaop-be `V1.3.5-20260803-1522-arm64`）

| 文件 | 内容 |
| --- | --- |
| `v135-api-docs.json` | 线上 OpenAPI 原文档（781 KB，`GET /api/gaop/v3/api-docs`，冷启动约 6.2 s） |
| `v135-api-docs-summary.json` | 上者的精简版：504 条路径 + 各自方法 |
| `v135-asset-manifest.json` | 前端资源清单（308 资产 / 196 JS） |
| `v135-routes.json` | 从产物抽出的 104 条 `path:"…"` 字面量（未去噪） |
| `v135-api-literals.json` | 从产物抽出的 238 条接口字面量（未去噪） |

## 一、两个来源都必须用

- **OpenAPI**：504 paths / 546 operations / 945 schemas（v1.3.4 是 500 / 542 / 930）。权威，但**不是全集**。
- **前端产物**：196 个 chunk 里的字面量。能补到 OpenAPI 照不到的东西，也会混入噪音。

**已证实的反例**：`/v1/pub/file_project` 线上 200 可用，却**不在** 504 条路径里（被 springdoc 排除）；
反过来产物里的 `/v1/pub/file/list`、`/v1/pub/file/storage` 是拼路径的中间态，线上 404。
⇒ 只信任何一边都会得出错误结论。

## 二、路由：97 = 97，差集为空

- 线上 V1.3.5：97（去噪 + 去尾斜杠）
- 本仓 `route-table.ts`：97（86 页面 + 11 菜单组）
- 双向差集：**空**
- v1.3.4 的 `routes.json` 树：**97** ⇒ 两个版本之间**没有增删页面**

## 三、接口：路径缺 25 条，方法缺 4 条

```
OpenAPI 路径            504
本仓路径                255（283 个操作）
与 OpenAPI 精确同路径    242
前端调用的字面量         214
路径级覆盖              189 / 214 = 88%
整路径缺口               25
同路径但缺方法            4
```

### 整路径缺口（前端在调、后端没有）

```
/audit
/audit/dictionaryList
/cloud/alert
/cloud/alert/enable
/cloud/alert/exist
/cloud/alert/record
/cloud/alert/rule
/cloud/label-summary
/cloud/native/Deployment
/cloud/nodes
/cloud/nodes/resource-consumption
/cloud/nodes/sorted_pods
/cloud/nodesummary
/cloud/services
/cloud/summary
/cloud/volumes
/loki/logs/deleteLogs
/loki/logs/getDeleteStatus
/loki/logs/getLogsAssertInfo
/loki/logs/setLogMaxMemory
/loki/logs/setLokiLogRetainMaxDay
/register/ip/harbor
/v1/prometheus/health
/v1/prometheus/repair
/v1/pub/node/terminal
```

### 方法缺口（路径在、方法不全）—— 比整路径缺口更要紧

| 路径 | 厂商 | 本仓 | 业务含义 |
| --- | --- | --- | --- |
| `/v1/service` | GET / POST / DELETE | GET | **创建服务 / 删除服务未实现** |
| `/v1/role/{roleId}/software` | GET / POST / PUT | GET | 席位↔软件关联/解除未实现 |
| `/v1/software/{id}/relate_service` | GET / POST / DELETE | DELETE | 软件↔服务关联未实现 |
| `/v1/cs_software/upload` | POST / GET | POST | 影响小 |

## 四、版本差异：V1.3.5 只新增 4 个端点

| 端点 | 前端是否调用 | 本仓 |
| --- | --- | --- |
| `GET /v1/prometheus/health` | 否（仅主包定义） | 未实现 |
| `POST /v1/prometheus/repair` | 否 | 未实现 |
| `POST /v1/pub/node/terminal` | 是（终端登录） | 未实现 |
| `POST /v1/software/{id}/restart` | 否 | 未实现 |

## 五、抽取脚本的三个坑（下次别重犯）

1. **超时误判**：`/v3/api-docs` 冷启动 6.2 s，用 `-m 15` 偶发超时会被当成"没有文档"。用 ≥60 s。
2. **全限定注解名**：本仓控制器写 `@org.springframework.web.bind.annotation.PostMapping("/x")`，
   正则必须允许 `@(?:[\w.]+\.)?` 前缀；另有裸 `@GetMapping`（路径在类级），要回填 base。
3. **`consumes` 被当路径**：`@PostMapping(consumes = "multipart/form-data")` 若直接取"第一个引号串"，
   会把 `multipart/form-data` 当路径，凭空造出假缺口。必须只认 `value=` / `path=` 或首字符是引号。

## 六、局限

- 路径级"互相为前缀"匹配会漏掉同义不同名的端点，本次 25 条已用 OpenAPI 交叉确认 + Java 关键字复核。
- 方法级只在**精确同路径**上比，参数形状不同的路径会被算作"路径缺失"，需人工判断。
- "线上有调用字面量"不等于"该页面在用"：`prometheus/*` 就只出现在主包。
