# V1.3.5 线上产物对账（2026-11-10）

**现场**：`http://172.16.209.170/gaop/`（gaop-web V1.3.5-20260805-1146-arm64）+ `/api/gaop`（gaop-be V1.3.5-20260803-1522-arm64）
**方法**：抓首页 + `asset-manifest.json`，下载 308 个资产 / 196 个 JS chunk（11 MB），正则抽取路由与接口字面量。
同目录三份快照即当时的原始抽取结果。

| 文件 | 内容 |
| --- | --- |
| `v135-asset-manifest.json` | 线上资源清单（308 条，含 196 个 JS） |
| `v135-routes.json` | 从产物抽出的 104 条 `path:"…"` 字面量（未去噪） |
| `v135-api-literals.json` | 从产物抽出的 238 条接口字面量（未去噪） |

## 结论一：路由 97 = 97，差集为空

归一化（去尾斜杠）+ 去噪（`/etc`、`/usr`、`/vscode-resource`、`socket.io`、静态扩展名）后：

- 线上 V1.3.5：**97** 个路由节点
- 本仓 `route-table.ts`：**97**（86 页面 + 11 菜单组）
- 双向差集：**均为空**
- v1.3.4 的 `recovered-web/routes.json` 树同样是 **97** 节点 ⇒ **V1.3.4 → V1.3.5 没有增删页面**

## 结论二：接口 214 条，后端覆盖 189（88%），缺口 25

归一化后按"互相为前缀"判命中（厂商大量用 `.concat` 拼路径，字面量只有半截）。

```
线上业务端点   214
后端映射       257（去噪后 244）
精确命中       189
缺口            25
```

### 缺口清单（已逐条用关键字在 Java 里复核，确认不是换了路径实现）

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

## 结论三：前端契约层缺 10 条

`/v1/lang/import`、`/v1/pub/import`、`/v1/pub/cicd_v2`、`/v1/pub/getNodeAlertSetting`、
`/v1/pub/setNodeAlertSetting`、`/v1/pub/node/terminal`、`/cloud/services`、`/cloud/native/Deployment`、
`/v1/prometheus/health`、`/v1/prometheus/repair`。

反向的 20 条"契约有、线上无字面量"多数不是缺口：K8s 代理在本仓写成 `/k8s/api/v1/...`（线上是
baseURL 拼接后的 `/api/v1/...`），`/settings`、`/auth/*` 走根基址。

## 已知局限

1. 抽取是**字面量级**：同义不同名（如 `getXxx` vs `/xxx/list`）只能靠人工复核，本次 25 条已逐条搜过关键字。
2. `/v3/api-docs` 在 V1.3.5 线上**不可达**（404/超时），因此无法用 OpenAPI 做第二来源交叉验证。
3. 统计的是"线上产物里有这个调用"，不等于"这个页面在用"——`prometheus/*` 就只出现在主包，故列为待核实（Q-21）。
