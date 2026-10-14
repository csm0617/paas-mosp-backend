# `GET /v1/service/serviceAuth` 完整算法摘录（下一轮直接实现，无需再读源码）

**证据等级**：〔代码事实〕（逐行读全）+〔运行实测〕（可观测，9 条）。2026-10-14。
本轮**没有写业务代码**：把算法读到"可直接翻译"的程度并留档，避免下一轮边读边写（上一轮 `gaop`
就是因为**读到倒数第二行就下结论**而连错两轮）。

## 1. 签名与校验（`ServiceController:3478-3493`）

```
GET /v1/service/serviceAuth?page(1)&size(20)&keyword&authType&serviceType
  authType：逗号分隔的整数列表 —— 0未授权 1已授权 2已过期 3授权中 4授权失败；不传=查全部
  keyword 长度 > 45 → 400 service.invalid.service.name.length
  page/size 走 SqlUtils 归一化（我们的 PageUtils 同款）
```

## 2. 第一段过滤（`:3496-3504`）

对 `tenxCloud.allPageService(null)` 的每个 `ServicesItem`：

```
若 e / deployment / metadata / labels 任一为 null → 丢弃
appName = labels["system/appName"]
若 appName == "gis-server" → 丢弃
若 appName == "gaop"       → 丢弃
否则保留
```
本环境 `system/appName` 标签缺失 ⇒ `appName == null` ⇒ **9 条全保留**（与实测 `total=9` 一致）。

⚠️ **注意与 `gaop` 端点的区别**：`gaop` 要求 `appName == "gaop"`（`findings/58`），
`serviceAuth` 要求 `appName != "gaop" && != "gis-server"`——两个端点**正好互补**，别写反。

## 3. keyword 两段式（`:3505-3519`）

与 `gaop` 完全同款（同一份实现 `filterServiceName`）：
先按 deployment 名 `contains(keyword)`（大小写敏感）；**若结果为空**才用兜底
`filterServiceName`（匹配注解 `gaop/serviceLabel`，大小写敏感）。见 `findings/56`。

## 4. authType 过滤（`:3520-3556`，**本轮刚读全**）

仅当 `authType` 非空（`check == false`）时执行。对每个服务：

```
serviceTypeName = 注解 gaop/serviceType（可能为 null）
authInfo = g_service_auth 里按服务名查
若查到一行：        → 保留当且仅当 typeList.contains(auth.getAuth())
若没查到（走下面三条，按顺序）：
  1) typeList 同时含 0 和 1 且 size > 1        → 保留
  2) typeList 含 0 且 size == 1                → 保留当且仅当
        serviceTypeName == null || !"前台服务".equals(serviceTypeName)
  3) 否则                                       → 保留当且仅当
        typeList.contains(1) && serviceTypeName != null && "前台服务".equals(serviceTypeName)
```
（第 2 条里 `length != 1` 恒为 false，已按等价式化简。）

## 5. 分页与装配（`:3557+`）

1. `pagination(collect, page, size)`；
2. 取本页服务名 → `listNamespacedPod` 取 Pod 列表（失败只记日志）；
3. `authService.queryServiceList(本页服务名)` 取授权信息；
4. 逐条富化（重启次数等）——**与 `GET /v1/service` 是同一套装配**。

⇒ **形状复用已对拉的 `ServiceListResponseDTO`**，装配器也复用 `ServiceListAssembler`。

## 6. 下一轮实现清单（按顺序，全部为小改动）

1. `ServiceRuntimeEntity` 加 `serviceType`（注解 `gaop/serviceType`，端口里用已有的 `serviceLabelOf` 同款取法扩展）；
2. `IServiceAuthRepository` 已有 `queryAll()` —— 加一个按服务名批量查（或复用全量再内存匹配，注意表通常很小）；
3. 域服务加 `queryServiceAuthList(namespace, page, size, keyword, authType, serviceType)`，
   按 §2→§3→§4→§5 顺序实现；
4. 控制器加 `GET /v1/service/serviceAuth`（`keyword>45` → 400）；
5. 对拉：**不带 authType（应为 9 条，与厂商一致）**、带 `authType=0`、带 `keyword`、`keyword>45`。
   —— 不带 authType 的那条就能拿到**非空对拉**，这是本轮之后最容易验证的端点。

## 7. 状态

全量 **34 类 229 条全绿**；本轮零业务代码改动、无集群写入、无残留。
