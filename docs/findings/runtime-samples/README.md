# 厂商运行时契约样本

2026-10-14 02:32 从**运行中的厂商后端**（`gaop-namespace/gaop-be`，端口 8080）抓取，用于「厂商前端一行不改」这条验收门槛的 diff 基线。

## 采集方式

```bash
kubectl proxy --port=8001 --address=127.0.0.1
# 服务的 API server 代理前缀
P=http://127.0.0.1:8001/api/v1/namespaces/gaop-namespace/services/gaop-be:8080/proxy
# 登录拿 cookie（gateway-token）
curl -s -c /tmp/gaop-cookies.txt -X POST "$P/v1/pub/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"<用户名>","password":"<口令>","role":"<战位>"}'
```

**注意**：本节点的 kubelet 缺 `socat`，`kubectl port-forward` 直接报
`unable to do port forwarding: socat not found`——只能用 `kubectl proxy` 的 service 代理路径。
抓取产物里的 `gateway-token` 已确认未落盘（抓取时校验过）。**本文件不记录任何口令**：集成验证用的种子口令见
`/Users/csm/Desktop/Project/msop/artifacts/k8s/seed/msop-seed.sql`。

## 端点对照（实测）

| 方法 | 路径 | 状态 | 体积 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/v1/pub/login` | 200 | — | 入参 `{username,password,role}`；下发 `Set-Cookie: gateway-token=<token>; Path=/` |
| POST | `/v1/software/overView` | 200 | 162 B | `data.{software,service,pod}` 三组 `{running,stop,pending}` |
| GET | `/v1/software` | 200 | 58 B | `data.{softwareList:[],total:0}`（当前无软件数据） |
| GET | `/v1/software/overView` | 400 | 6.9 KB | **证明 overView 只接受 POST**：GET 落到 `detailSoftWare({id})`，"id指定软件不存在" |
| GET | `/v1/service` | 200 | 67 KB | `data.{runningDeployment,total:9,count:9,services:[9 项]}` |
| GET | `/v1/namespaceService` | 404 | 116 B | 该端点不存在（Spring 默认错误体，非契约信封） |
| GET | `/v1/app` | 200 | 31 B | `data: []`——恒空，`netWorkExport` 空桩的直接后果 |
| GET | `/k8s/api/v1/namespaces/gaop-namespace/services` | 200 | 14 KB | 信封内 `data` 是**原始 K8s `ServiceList`**（10 项） |
| GET | `/k8s/api/v1/namespaces/gaop-namespace/deployments` | 400 | 6 KB | 该代理路径不认 apps/v1 资源（`k8s.invalid.api.parameter`） |
| GET | `/v1/software/relationService/{uuid}` | 400 | 7.1 KB | 软件不存在时也是 400（不是 5xx） |
| GET | `/v1/role` | 200 | 1 KB | 战位列表 |

## 契约事实（可直接当断言用）

1. **统一信封**：`{"msg": "...", "code": 0, "data": ...}`；`code=0` 为成功。失败时 `data` 里带 `trace`（**Java 堆栈原文**）——兼容门槛内不能"美化"掉 trace 字段。
2. `/v1/service` 的 `data.services[]` 每项键固定为：
   `nameFr, nameUs, service, volumeTypeList, deployment, serviceAuth, terminated, hostIP, nodePortService, imageInfo, mappingInfo, softwareNameList, restartCount, podInfoList, belongNodeList, cpuOrder, memoryOrder, gpuOrder, fixedName`，
   其中 `service` / `deployment` 内嵌**完整 K8s 对象**（带大量 `null` 字段），不是引用。
3. **数字被序列化成双精度**：K8s 代理路径返回 `"port":19526.0`（而非 `19526`）。→ 契约比对必须按**数值**而非字节做。
4. `/v1/app` 恒返回空数组（`AppService.queryAppEndPoints` 依赖空桩 `netWorkExport`）。

## 集群现状（与之前结论的差异，重要）

采集时实测：**两个白屏触发条件在实集群里已不存在**——

- 所有 Deployment 的探针都是数字端口（`tcpSocket.port = 8080/19526/55555/9527/5000/18080/9889`），没有具名端口；
- `gaop-mysql`（ExternalName）**已有 `spec.ports`**（`targetPort: 3306`）；
- 结果：`GET /v1/service` 返回 200 / 67 KB（此前会 NPE）。

即：白屏根因在**代码层**依然成立（见 `../named-port-fabric8-7.9.0.md`），但当前这个集群已经被修好了，别再把它当成"复现现场"。

## 语料库（2026-10-14 02:47 扩采）

`tools/capture-vendor-samples.py` 按 `tools/vendor-endpoints.txt` 采了 **26 个只读端点**，清单与状态在 `manifest.json`：

- **200 且可直接用作基线**：`/audit`、`/audit/dictionaryList`、`/settings`、`/v1/user`、`/v1/role`、`/v1/role/list`、`/v1/lang/current`、`/v1/lang/version`、`/v1/licence`、`/v1/licence/info`、`/v1/mode/current`、`/v1/mode/list`、`/v1/backup/list`、`/v1/config_map`、`/v1/image/projectList`、`/v1/service`、`/v1/service/baseComponent`、`/v1/service/labels`、`/v1/software`
- **500，因为缺必需参数**（不是环境坏）：`/v1/lang/detail`（缺 `lang`）、`/v1/database/users`（缺 `databaseName`）、`/v1/image/repositories`（缺 `storage`）、`/v1/config_map/relationService`
  → 顺带得到一条契约事实：**缺参数时厂商返回 HTTP 500 + `code:-1` + `msg:"系统错误"` + data.trace 堆栈**，不是 400。
- **未采到（curl 25s 超时）**：`/v1/backup/backupSetting`、`/v1/database`

## 相关文件

- `cluster-deployments.yaml` / `cluster-services.yaml`：`gaop-namespace` 的 Deployment/Service 快照（无 Secret）
- `software-*.json`、`service-list.json`、`k8s-proxy-*.json`、`app-list.json`、`role-list.json`：原始响应体
