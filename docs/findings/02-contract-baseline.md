# 02 契约基线：gaop-be 三方交叉对账

> 工单：`02-contract-baseline`（research）。产物：本文件 + `02-contract-baseline.json`。
> 对象：厂商运行态 OpenAPI dump、前端复刻 service 层清单、反编译 controller 源码三方的**机器可比对**基线。
> 纪律：只描述字面证据；判不了的一律写「未定论」，不猜。所有结论标注出处 `〔源：…〕`。

---

## 0. 摘要（先看这 8 条）

| # | 结论 | 计数 | 出处 |
|---|---|---|---|
| 1 | **运行态 api-docs 完全被反编译源码覆盖** | 542 / 542 个 operation 都能找到 controller handler | JSON `counts.endpointRecordsInApiDocs` |
| 2 | **反编译源码多出 63 个 handler 不在 api-docs** | 63 | §4.2 |
| 3 | 其中 **57 个是死代码**：`@ConditionalOnExpression(value="false")` 导致 bean 不装配 | 57 | §4.2 |
| 4 | 其余 6 个是 `@Hidden`：5 个 K8s 代理 + 1 个测试端点 | 5 + 1 | §5、§4.2 |
| 5 | **`api-catalogue.json` 不是前端接口全集**：它只有 196 个函数，前端 service 层实际有 **259** 个 | −63 | §1.4、§4.2 |
| 6 | 前端调用 279 / 542 个 api-docs 端点；**263 个运行态端点无任何前端调用者** | 279 / 263 | §4.2 |
| 7 | `/api/gaop` 是**网关前缀**，后端无 `context-path`；同一前缀下还挂着 gaop-fdfs / gaop-image-uploader | — | §1.3、§6 |
| 8 | 严格意义「前端在调 gaop-be、而 gaop-be 没有」的端点：**0 条** | 0 | §4.1 |

---

## 1. 证据与方法

### 1.1 三份一手证据

| id | 路径 | 是什么 | 规模 |
|---|---|---|---|
| `api-docs` | `recovered-web/regenerated/openapi-gaop-be.json` | 厂商**运行态** `/v3/api-docs` dump，OpenAPI 3.0.1，springdoc 1.5.2 | 500 paths / **542 operations** / 930 schemas 〔源：同文件 `paths`、`components.schemas`〕 |
| `service-layer` | `recovered-web/decompiled/umi.d08aa716.js`（webpack module **22239**） | 前端 service 层全量：259 个构造请求的函数 | 210 字面量 URL + 49 拼路径 URL 〔源：`umi.d08aa716.js` 模块 22239 函数体〕 |
| `controllers` | `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/**` | CFR 0.152 反编译源码，逐注解解析 | 51 个类 / **605 handler** / 655 个映射注解 〔源：同目录，见附录 B〕 |

辅助证据：

- `recovered-web/api-catalogue.json` —— 前端 service 层的**子集清单**，196 个函数（见 §1.4 的重要修正）。
- `recovered-web/request-layer.json` —— axios 实例与超时白名单，**只收录了部分数组**；完整表在 `recovered-web/decompiled/umi.d08aa716.js:27657-27722`〔源：bundle 同上〕。
- `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/config/ReverseProxyConfig.java` —— charon 反向代理注册〔源：`:71-73,122-133`〕。

### 1.2 655 个映射注解自校验

解析规则：类级 `@RequestMapping` 取 `public class` 声明之前的行；方法级取其后缩进 4 空格、且后续签名为方法的方法。结果：

| 项 | 数 | 说明 |
|---|---|---|
| 类级 `@RequestMapping`（缩进 0） | 50 | 51 个类中 `cloud/TenxController.java` 无类级前缀〔源：`controller/cloud/TenxController.java:104-105`〕 |
| 方法级 `@{Get,Post,Put,Delete,Patch}Mapping`（缩进 4） | 604 | 对应 604 个 handler |
| 方法级 `@RequestMapping`（缩进 4） | 1 | `cloud/TenxController.java:456` |
| **合计注解** | **655** | 与工单给的 655 一致 |
| **handler** | **605** | 604 + 1 |

> 注：`TenxController` 与 `TenxApiV2Controller` 的部分方法**没有 `public` 修饰符**（包级私有，如 `TenxResponse<TenxImages> getWorkLoadNum(...)`）。只按 `public` 匹配会漏 10 个 handler〔源：`controller/cloud/TenxController.java:346`、`.../cloud/TenxApiV2Controller.java:490`〕。

### 1.3 上下文路径：后端没有，`/api/gaop` 是网关前缀

**结论**：gaop-be 的 servlet 路径是裸的（`/v1/**`、`/k8s/**`、`/cloud/**`…），`/api/gaop` 不由后端声明。

证据：

- `recovered/gaop-be/src/main/resources/application.yml`、`application-dev.yml`、`application-k8s.yml`、`application-prod.properties` **均无** `server.servlet.context-path`〔源：四个文件全文〕。
- `application-k8s.yml:26` 只有 `server.port: 8080`〔源：同文件〕。
- 前端 axios 实例 `a().create({ baseURL: "/api/gaop", timeout: 6e4, withCredentials: !0 })`〔源：`recovered-web/decompiled/umi.d08aa716.js:27723`〕。
- 后端自带页面也按这个前缀写：`ws://…/api/gaop/ws/userInfo`〔源：`recovered/gaop-be/src/main/resources/ws.html:68`〕。
- 运行态实测入口：`GET /api/gaop/v3/api-docs → 785 KB`〔源：`docs/findings/03-平台与运行时.md:309`〕。

对重写的含义：**网关/ingress 负责剥掉 `/api/gaop`**，重写后端只需对外暴露 `/v1/**` 等裸路径；`/api/gaop` 不能写进后端路由表，否则双前缀。

### 1.4 ⚠️ 重要修正：`api-catalogue.json` 只覆盖 196 / 259

工单与既有产物都按「前端 196 个 service 函数」表述。实测前端 service 层（`umi.d08aa716.js` module 22239）有 **259** 个构造请求的函数，其中 **49 个用 `.concat(...)` 或 `+` 拼接路径参数**。

> 自校验（可复现）：module 22239 的导出表 `n.d(t, {...})` 只有 **1 个**，条目 **259** 个，局部函数声明 **259** 个，两者 1:1；模块体内 `url:` / `method:` / 请求包装调用 `.W)(` 各出现 **259** 次。因此 259 是硬计数，不是估算。
> 〔源：`recovered-web/decompiled/umi.d08aa716.js` module 22239〕

`api-catalogue.json` 只收录了 **URL 为纯字符串字面量** 的函数，因此**漏掉 63 个**（49 个拼路径 + 14 个字面量但当时未收录）。典型漏项：

```
fn l  → GET  /v1/software/{id}                        （源码：url: "/v1/software/".concat(e.id)）
fn s  → GET  /v1/software/{softwareId}/service/{serviceName}
fn W  → PUT  /settings/{settingKey}
fn o  → PUT  /v1/user/{userId}
fn Qt → GET  /v1/service/serviceAuth                  （导出名 PKV，字面量漏项）
```
〔源：`recovered-web/decompiled/umi.d08aa716.js` module 22239；导出名 `N_7/W$5/PKV` 等见 JSON `frontendEvidence.catalogueGap.missingFns`〕

那 14 个字面量漏项里有 11 个其实是 gaop-be 端点，例如 `DELETE /v1/white_list`、`POST /v3/software/backup`、`GET /v1/mode/current`、`GET v1/pub/versionInfo`、`GET /v1/service/otherPlugin`、`GET /v1/service/serviceAuth`；另 3 个（`GET /v1/pub/file/storage`、`GET /v1/pub/file/list`、`DELETE /v1/pub/file/delete`）走 gaop-fdfs〔源：同上，JSON `catalogueGap.missingFns`；api-docs 存在性逐条核对〕。

> 因此既有结论「196/196 命中」**在该受限集合内成立，但不代表前端只调用 196 个端点**。真正的交集是 255（见 §4.2）。这一点会直接影响兼容性验收的覆盖判定。

### 1.5 前端调用者（函数名 + 页面 chunk）如何定位

沿用既有产物 `docs/findings/02-应用与功能地图.md` 附录 B 的方法，并补齐一级：

1. 从 module 22239 的导出表取 `短名 → 内部函数名`：`Nqq: function () { return o; }` 形式，共 259 对〔源：`umi.d08aa716.js` module 22239 导出表〕。
2. 把 259 个函数重建为 `method + 路径模板`（处理 `"a".concat(e.id)`、`"a" + e`、`"a".concat(e.x, "/b").concat(e.y)`）〔源：同上函数体〕。
3. 在每个 chunk 里匹配 `.<短名>\b`；`p__*` / `layouts__*` 按 umi 命名约定还原为 `src/pages/**`、`src/layouts/**`〔源：`tools/recover-frontend.mjs:181-186`〕。

例：`GET /v1/software/{id}` 的调用者是 `fn l`（导出名 `N_7`），出现在页面 chunk `p__software.3edb99c9.async.js` → `src/pages/software`〔源：JSON `endpoints` 中该条 `frontendCallers`〕。

---

## 2. 三方对账总表

| 关系 | 计数 | 说明 |
|---|---|---|
| api-docs operations | 542 | 500 paths |
| controller handlers | 605 | 含 63 个不在 api-docs 的 |
| api-docs ⊂ controllers | **542 / 542** | 无一条 api-docs 端点找不到源码 |
| controllers − api-docs | **63** | 57 死代码 + 5 K8s 代理 + 1 hidden |
| 前端 service 函数 | 259 | api-catalogue 只记 196（§1.4） |
| 前端函数 → gaop-be api-docs | **255** | 命中（1:1，严格占位符匹配，0 未命中 / 0 歧义）；其余 4 个走 gaop-fdfs（§6） |
| 前端 URL 字面量（含直接调用） | 229 | 其中 **40** 个在页面/共享 chunk 里有直连调用点，**35** 个完全不出现在主包 |
| api-docs 端点被前端调用 | **279** | 255（service 层）+ 24（仅字面量/直连） |
| api-docs 端点无人调用 | **263** | §4.2 |
| 端点记录总数（JSON `endpoints`） | **605** | = 全量 controller handler，每条都标了是否在 api-docs |

> 独立复核（见附录 C）：上表「端点被前端调用 / 无人调用」两行与 JSON `counts` 已按**端点记录**重算，满足 `279 + 263 = 542`、`255 + 24 = 279`。第 4 行「前端 URL 字面量 229 / 40 / 35」是字面量级统计，本次复核**未重算**，保留原值并标 `未定论`。

---

## 3. 端点基线怎么读（JSON）

`02-contract-baseline.json` 顶层：

```
meta / counts / endpoints[605] / nonBusinessRoutes / crossServiceRoutes
     / frontendEvidence / discrepancies{frontendOnly, backendUncalled, nearMissPaths}
```

每条端点记录（要求字段已全部满足）：

```jsonc
{
  "method": "GET",
  "path": "/v1/software/{id}",
  "source": { "apiDocs": "/v1/software/{id}",      // api-docs 的 path（不在则 null）
              "controller": "SoftwareController.java:422" },  // 文件:行号（映射注解行）
  "tags": ["软件"],                                  // api-docs tags，缺则用类级 @Tag
  "operationId": "detailSoftWare",
  "summary": "查询软件详情",
  "request": {
    "parameters": [ { "name":"id","in":"path","type":"string","required":true,
                      "ref":null,"source":"api-docs" } ],
    "body": null,
    "controllerSignature": [ { "name":"id","kind":"path","type":"String","required":true } ],
    "consumes": []
  },
  "response": { "codes": {"200":"ResponseSoftwareInfo","400":"ResponseObject",...},
                "controllerReturn": "Response<SoftwareInfo>" },
  "frontendCallers": [ { "fn":"l","exportKey":"N_7","url":"/v1/software/{id}","method":"GET",
                         "baseURLOverride":null,
                         "pageChunks":[{"chunk":"p__software.3edb99c9.async.js","source":"src/pages/software"}],
                         "sharedChunks":[], "recordedInApiCatalogue":false,
                         "evidence":"...umi.d08aa716.js (module 22239, function l)" } ],
  "frontendDirectCallSites": [],
  "runtime": { "inApiDocs": true, "active": true, "excludedBy": null },
  "businessContract": true
}
```

- `response.codes` 里的名字就是 **api-docs 的 schema 名**（`#/components/schemas/<名>`），可直接机器比对。
- 不在 api-docs 的 63 条：`runtime.inApiDocs=false`，且 `runtime.excludedBy` 给出原因；`source.apiDocs=null`。
- 请求形状优先取 api-docs（运行态事实），`request.controllerSignature` 同时保留源码签名，两者不一致时可对差。

---

## 4. 差异三桶

### 4.1 桶一：仅前端在用 —— **38 条，其中 0 条是真正的 gaop-be 缺口**

38 条全部是「前端字面量在 gaop-be api-docs 中找不到同名端点」。逐条判定后的分布：

| 分类 | 计数 | 判定 | 例子 |
|---|---|---|---|
| `prefix-of-endpoint` | 12 | **同一端点的前缀/拼接基串**，非独立端点 | `/v1/software/order/` → `/v1/software/order/{orderNum}` |
| `substring-of-endpoint` | 3 | 前端拿它做 `.includes()` 子串匹配 | `/nodes` 命中 `/cloud/nodes` |
| `full-url-with-base` | 1 | 带 baseURL 的完整 URL | `/api/gaop/v3/software/backup/download?taskId=` |
| `base-url-constant` | 1 | axios baseURL 常量，非端点 | `/api/gaop` |
| `cross-service` | **12** | 归属**其他服务**（同 `/api/gaop` 前缀，见 §6） | `/v1/pub/file/{fileId}` → gaop-fdfs |
| `cross-system-external` | 5 | 第三方系统绝对地址，不走 `/api/gaop` | `http://<hostname>/zk-103-jcsjkgl-software/.../contacts/ips` |
| `k8s-proxy` | 3 | K8s 代理路径（§5） | `/api/v1/pods` |
| `charon-reverse-proxy` | 1 | charon 反代 `/tenx/.*`（§5） | `/tenx/api/v2/clusters` |

**判定结果**：`resolved` 16 + `cross-service` 12 + `not-this-backend` 5 + `non-business` 4 + `not-an-endpoint` 1 = 38，**`未定论` 0 条**。

> **严格意义的桶一为空（0 条）**：没有「前端在调 gaop-be、而 gaop-be 既无 handler 也不在 api-docs」的路径。
> 12 条 `cross-service` 不是缺口，但对**只替换 gaop-be** 的重写是硬约束——它们必须继续由原服务承接，网关路由不能改。

〔源：JSON `discrepancies.frontendOnly.items`；分类依据见各项 `reason`/`verdict`〕

### 4.2 桶二：后端有但无人调用 —— **321 条**

| 子桶 | 计数 | 判定 | 依据 |
|---|---|---|---|
| `api-docs-live-uncalled` | **263** | 运行态存在（api-docs 有），前端 259 个 service 函数与全部 chunk URL 字面量中**均无调用点** | JSON `discrepancies.backendUncalled.items` |
| `controller-only-not-ready` | **58** | 源码有，但运行态没有或不出现在 api-docs | 见下 |
| ↳ 死代码 | 57 | 类级 `@ConditionalOnExpression(value="false")` → bean 不装配，api-docs 与前端都没有 | `controller/cloud/TenxController.java:104` |
| ↳ hidden 测试端点 | 1 | `@Operation(..., hidden=true)` | `controller/InstancesController.java:75-76` `PUT /ui/test/scale/{deployName}` |

无人调用最多的 tag（`api-docs-live-uncalled`）：

| tag | 条数 |
|---|---|
| 公共API | 54 |
| cloud.js使用到的接口 | 33 |
| 同步 | 25 |
| 公共API-文件 | 16 |
| 软件 | 15 |
| 服务 | 14 |

〔源：JSON 同桶 `items[].tags`〕

> ⚠️ `TenxController` 的 57 个 handler **不是**「保留能力」：它们连 bean 都不会创建。重写时**不要**把它们当契约实现。
> 但同目录的 `TenxApiV2Controller` 是**活的**（无该注解，其 `/cloud/**` 端点出现在 api-docs，且大量被前端调用，如 `GET /cloud/nodes`←`fn ci`、`GET /cloud/nodesummary`←`fn di`）。
> 注意区分到具体端点：`PUT /cloud/services/batch-start|stop` 本身是活的但**无前端调用者**（属 263 条 `api-docs-live-uncalled`）。
> 〔源：`.../cloud/TenxController.java:104`、`.../cloud/TenxApiV2Controller.java:353,374`；调用者见 JSON `endpoints[].frontendCallers`〕

### 4.3 桶三：路径近似但不同 —— **21 条已判定 + 68 组自动扫描（未逐条判定）**

**已判定 21 条**（JSON `discrepancies.nearMissPaths.items`），关键几条：

#### (a) 工单点名的例子：`/v1/config_map/relationService` vs `/v1/software/relationService`

**判定：不同的合法端点，非重复、非笔误，不可合并。**

| | `/v1/config_map/relationService` | `/v1/software/relationService/{id}` |
|---|---|---|
| 源码 | `ConfigMapController.java:364` `configMapRelationService` | `SoftwareController.java:431` `softwareRelationServiceList` |
| 入参 | 无 | `@PathVariable id` |
| 返回 | `Response<List<ConfigMapInfo>>` | `Response<List<ServicesItem>>` |

仅末段名词相同：前者问「配置组关联了哪些服务」，后者问「某软件的关联服务列表」。
〔源：`recovered/gaop-be/.../controller/ConfigMapController.java:364`、`.../SoftwareController.java:431`〕

#### (b) 真正高危的近似：同方法名、同返回类型、只差 `/pub` 前缀

| a | b | 证据 |
|---|---|---|
| `GET /v1/lang/current`（`LanguageController.currentLanguage`） | `GET /v1/pub/lang/current`（`pub/PubLanguageController.currentLanguage`） | 方法名与 `Response<CurrentLanguage>` **完全相同**〔源：`LanguageController.java:45`、`pub/PubLanguageController.java:32`〕 |
| `GET /v1/mode/current`（`ModeController.currentMode`） | `GET /v1/pub/mode/current`（`pub/PublicController.currentMode`） | 方法名与 `Response<Mode>` **完全相同**〔源：`ModeController.java:31`、`pub/PublicController.java:3720`〕 |

**判定：两者都在运行态 api-docs 里，都是契约，必须都实现。** 这是厂商历史双注册，不是遗留可删项。

#### (c) 同业务名、不同层

`PUT /v1/service/batch-stop`（`ServiceController.serviceStop`，`Response<Boolean>`）vs
`PUT /cloud/services/batch-stop`（`cloud/TenxApiV2Controller.stopDeploy`，`TenxResponse<String>`）vs
`PUT /services/batch-stop`（`cloud/TenxController.stopService`，**死代码**）
〔源：`ServiceController.java:3649`、`cloud/TenxApiV2Controller.java:374`、`cloud/TenxController.java:270`〕

#### (d) api-docs 文本 ≠ 源码文本（必然出现，需归一化）

`PUT /settings/{settingKey}`（api-docs）vs `PUT /settings/{settingKey:.+}`（源码）
—— 源码是**正则路径变量**，springdoc 1.5.2 去掉正则后生成 api-docs。
**判定：同一端点**；机器比对必须先做 `\{[^}]*\}` → `{}` 归一化。
〔源：`GlobalSettingController.java:44`、api-docs `#/paths/~1settings~1{settingKey}`〕

#### (e) 前端源码本身的不一致

`GET v1/pub/versionInfo` —— `umi.d08aa716.js` module 22239 的 `function Yi` 写成 `url:"v1/pub/versionInfo"`，**少一个前导 `/`**。axios 会拼成 `/api/gaop/v1/pub/versionInfo`，实际仍命中后端 `GET /v1/pub/versionInfo`。
**判定：同一端点**；但重写侧若做严格字符串路径匹配会漏掉这条。
〔源：`recovered-web/decompiled/umi.d08aa716.js` module 22239 `function Yi`〕

**自动扫描 68 组（未逐条判定）**：`nearMissPaths.autoScan` 列出所有「同一末段名词出现在 ≥2 个不同前缀下」的组（如 `backup` 8 条、`download` 8 条、`agentType` 8 条、`exist` 5 条、`current` 4 条、`config_map` 4 条）。这些**只做了机器枚举，未逐条判定业务是否重复**，保持 `未定论`。

---

## 5. 非业务契约（单独列出）

### 5.1 K8s 代理：`/api/gaop/k8s/**`

| 项 | 内容 |
|---|---|
| 源码 | `controller/K8sProxyController.java:48` `@RequestMapping("/k8s")`；`:49` `@Hidden`；`:50` 类声明 |
| handler | `GET :60`、`POST :155`、`PUT :160`、`DELETE :165`、`PATCH :170`，全部 `value={"/**"}` |
| 为何不在 api-docs | 类级 `@Hidden`〔源：同上 `:49`〕 |
| 谁在调 | 前端 **chunk `3900.97fc5443.async.js`**，独立 axios 实例 `create({baseURL:"/api/gaop/k8s"})`，内含 K8s 原生路径 `/api/v1/pods`、`/api/v1/persistentvolumes`、`/api/v1/namespaces/`〔源：`recovered-web/decompiled/3900.97fc5443.async.js`〕 |

**判定：非业务契约。** 它把请求原样反代到 K8s API，路径空间是 K8s 的、不是 gaop-be 的。重写后端可原样保留实现，但**不要**把它纳入业务契约的比对集合。

### 5.2 charon 反向代理：`/tenx/**`、`/harbor/**`、`/tenx-ui/**`

charon 在 gaop-be 里注册了 3 条反代路由（`RequestMappingConfigurer`），**它们不是 Spring MVC handler，因此永远不会出现在 api-docs**：

| 前缀 | 上游 | 源码 |
|---|---|---|
| `/{basePath}/.*`，`basePath="tenx"` → `/tenx/**` | `server.host.tenx`（时速云） | `config/ReverseProxyConfig.java:71,122-133` |
| `/harbor/**` | `server.host.harbor` | `:72,133` |
| `/tenx-ui/**` | `server.host.tenx` | `:73,133` |

**这解释了为什么前端 request-layer 白名单里的 `GET /tenx/api/v2/clusters` 在 api-docs 与 controller 里都找不到**——它是反代路由，走 charon〔源：`config/ReverseProxyConfig.java:71,122-133`；`umi.d08aa716.js:27657,27722`〕。

〔源：`recovered/gaop-be/.../config/ReverseProxyConfig.java`〕

---

## 6. 同一 `/api/gaop` 前缀下的其他服务（重写 gaop-be 的硬边界）

`/api/gaop` 网关前缀下不只有 gaop-be。前端调用的这些路径**不属于 gaop-be 契约**：

| 前缀 | 归属 | 证据 |
|---|---|---|
| `/api/gaop/v1/image/clean|upload|download|live` | **gaop-image-uploader** | `recovered/gaop-image-uploader/.../controller/ImageController.java:57,101,161,362,435`；gaop-be api-docs 的 `/v1/image` 系列**没有**这 4 个 |
| `/api/gaop/v1/pub/file/**` | **gaop-fdfs** | `recovered/gaop-fdfs/.../controller/PublicMinioController.java:50,59,73,82,88,95,103,110,117,124,130` |
| `/api/gaop/v1/pub/fdfs/**` | gaop-fdfs（与 gaop-be 的 `/v1/pub/fdfs/**` **同名并存**） | 两侧都有映射；归属需按网关路由判定 → **未定论** |

前端对 gaop-fdfs 的调用不是靠网关分流，而是**在请求上覆盖 baseURL**：

```js
// umi.d08aa716.js, module 22239
function Tn(e) { var t = { url: "/v1/pub/file/storage", baseURL: r.$8, method: "get", ... }; ... }
```
其中 `r.$8` = 模块 68843 导出的 `le`，`le = "http://".concat(window.location.hostname, ":19527")`
〔源：`recovered-web/decompiled/umi.d08aa716.js:1009454`（`function Tn`）、同文件模块 68843 的 `le = "http://".concat(window.location.hostname, ":19527")`〕

> **未定论**：19527 这个 host 端口 ↔ 具体服务的对照，在 `recovered/`、`extracted/`、`artifacts/` 中**没有证据**（gaop-fdfs 默认 9527、gaop-image-uploader 默认 18080）。仅 `ws.html:27` 的注释旁证 19528 与镜像上传相关。**不猜**。

---

## 7. 未定论清单（明确不猜）

| # | 事项 | 缺什么 |
|---|---|---|
| 1 | 前端 host 端口 `:19527` / `:19528` 分别对应哪个服务 | 仓库内无端口↔服务映射证据（§6） |
| 2 | `/v1/pub/fdfs/**` 同名端点究竟由 gaop-be 还是 gaop-fdfs 承接 | 需要网关/ingress 路由表，仓库内无 |
| 3 | `nearMissPaths.autoScan` 的 68 组末段碰撞，哪些是真重复 | 只做机器枚举，未逐组判定业务语义（§4.3） |
| 4 | 263 个「无前端调用者」端点是否有非本前端的调用方（运维脚本、其他系统） | 只有本仓库证据；无法证明「全系统无人调用」 |
| 5 | `TenxController` 死代码是否在别的部署 profile 下被启用 | `@ConditionalOnExpression(value="false")` 是硬编码字面量，但未见其他 profile 覆盖 |

---

## 8. 对重写后端的直接含义（兼容边界 checklist）

1. **必须实现**：api-docs 的 542 个 operation，形状以 `response.codes` 的 schema 名为准。
2. **不要实现**：63 个不在 api-docs 的 handler 里有 57 个 `TenxController` 死代码 + 1 个 hidden 测试端点；剩 5 个 `/k8s/**` 若保留，应作为独立代理组件而非业务 controller。
3. **命名/归一化**：路由匹配前先做 `{xxx:.+}` → `{xxx}` 归一化，否则 `/settings/{settingKey}` 对不上〔§4.3(d)〕。
4. **双注册必须都留**：`/v1/lang/current` 与 `/v1/pub/lang/current`、`/v1/mode/current` 与 `/v1/pub/mode/current`〔§4.3(b)〕。
5. **前端字面量不可作为契约全集**：`api-catalogue.json` 缺 63 个函数，其中 11 个是 gaop-be 端点；验收用例应从 259 个 service 函数（或其重建结果）反推，而不是 196〔§1.4〕。
6. **网关前缀 `/api/gaop` 不进后端路由**〔§1.3〕。
7. **跨服务路径不动**：`/v1/image/{clean,upload,download,live}`、`/v1/pub/file/**` 继续由原服务承接〔§6〕。
8. **反代与 K8s 代理单列**：`/tenx/**`、`/harbor/**`、`/tenx-ui/**`、`/k8s/**` 不参与业务契约比对〔§5〕。

---

## 附录 A：可复现推导

> 本工单的推导脚本全部以 `python3 - <<'PY' … PY` 内联执行，未在工作区落任何脚本文件（约束：只写两个产物）。以下为等价步骤。

```python
# 1) 反编译 controller → 605 handler
#    类级 @RequestMapping 取 public class 声明之前；方法级取其后缩进 4 空格、后续为方法签名者。
#    签名判定必须放宽到包级私有（不能只匹配 public/protected），否则 TenxController/TenxApiV2Controller 漏 10 个。
#    @Operation / @Hidden 要按「注解块」处理，因为 @Operation 出现在 @XxxMapping 之前。
#    输入：recovered/gaop-be/src/main/java/com/glriverside/gaop/be/controller/**/*.java

# 2) api-docs → 542 operation
#    json.loads(...)['paths'][path][method] → {tags, operationId, parameters, requestBody, responses}
#    responses[code].content[*].schema.$ref → schema 名

# 3) 前端 service 层 → 259 函数（method + 路径模板）
#    定位 webpack module 22239：在 bundle 里找 "/v1/software/update_screenshot" 后向前找最近的 "n.d(t, {"
#    导出表： r'([\w$]+): function \(\) \{\s*return ([\w$]+);'  →  短名 ↔ 内部函数名
#    函数体： r'\bfunction ([\w$]+)\(([\w$]*)\) \{' 后 2500 字符窗口内取 url:/method:/baseURL:
#    路径重建：把 "a".concat(x, "/b").concat(y) 与 "a" + x 统一展开成 字面量 + {参数名}

# 4) 页面 chunk 归属
#    每个 chunk 索引 r'\.([\w$]+)\b' 的属性访问集合；与导出短名求交
#    p__*/layouts__* → src/pages/**、src/layouts/**（umi 命名约定）

# 5) 三方 join： key = (method, norm(path))
#    norm = 去 query + re.sub(r'\{[^}]*\}','{}') + 去尾斜杠
```

## 附录 B：关键行号索引

| 事项 | 位置 |
|---|---|
| `/api/gaop` 前缀（axios） | `recovered-web/decompiled/umi.d08aa716.js:27723` |
| 前端超时/免跳转白名单全表 | `recovered-web/decompiled/umi.d08aa716.js:27657-27722` |
| K8s 代理 axios 实例 | `recovered-web/decompiled/3900.97fc5443.async.js`（`baseURL:"/api/gaop/k8s"`） |
| 前端 service 层 url `"v1/pub/versionInfo"`（缺 `/`） | `umi.d08aa716.js` module 22239 `function Yi` |
| gaop-fdfs baseURL 覆盖 | `umi.d08aa716.js:1009454`（`function Tn`，`baseURL: r.$8`） |
| charon 反代注册 | `recovered/gaop-be/.../config/ReverseProxyConfig.java:71-73,122-133` |
| K8s 代理 controller | `recovered/gaop-be/.../controller/K8sProxyController.java:48-50,60,155,160,165,170` |
| 死代码 controller | `recovered/gaop-be/.../controller/cloud/TenxController.java:104`（`@ConditionalOnExpression(value="false")`） |
| hidden 测试端点 | `recovered/gaop-be/.../controller/InstancesController.java:75-76` |
| 正则路径变量 | `recovered/gaop-be/.../controller/GlobalSettingController.java:44` |
| 工单点名的近似对 | `ConfigMapController.java:364`、`SoftwareController.java:431` |

---

## 附录 C：独立复核（第二次交叉，2026-10-14）

本附录记录一次**独立重跑**：不复用 §1–§8 的任何中间量，直接从三份一手证据重新解析，再与本文件/JSON 对账。凡结论一致的不再重复；下表只列**发现的偏差**与**无法定论项**。

### C.1 复核确认（独立重算与原文一致）

| 项 | 独立重算值 | 方法 |
|---|---|---|
| api-docs paths / operations / schemas | 500 / 542 / 930 | `json.load` 直接计数〔源：`recovered-web/regenerated/openapi-gaop-be.json`〕 |
| controller 方法级 `@XxxMapping` | 604 | `grep -rE "@(Get\|Post\|Put\|Delete\|Patch)Mapping"` 全目录计数〔源：`recovered/gaop-be/.../controller/**`〕 |
| 方法级 `@RequestMapping`（TenxController:456） | 1 | 同上 |
| 类级 `@RequestMapping` | 50 | 同上（`cloud/TenxController.java` 无类级前缀） |
| 合计注解 / handler | 655 / 605 | 50 + 604 + 1 / 604 + 1 |
| api-docs ⊂ controller | 542 / 542 | 归一化 `{xxx:.+}`→`{xxx}` 后按 `(method, path)` 精确匹配，逐条可追 `文件:行号` |
| controller − api-docs | 63 | 其中 TenxController 57（56 个 verb 映射 + 1 个方法级 `@RequestMapping`）、K8sProxyController 5、InstancesController 1 |
| `/settings/{settingKey:.+}` ↔ `/settings/{settingKey}` | 同一端点 | 源码正则路径变量，springdoc 去正则后输出 |
| `/v1/config_map/relationService` 与 `/v1/software/relationService/{id}` | 两个不同端点 | `ConfigMapController.java:364` / `SoftwareController.java:431`；前端只调前者（`fn qn`），后者无调用者 |

### C.2 复核发现并已修正的偏差

| # | 原值 | 修正值 | 一手证据 |
|---|---|---|---|
| 1 | 前端 service 函数 **258** | **259** | module 22239：导出表 `n.d(t,{...})` 仅 1 个、条目 259、局部函数声明 259（1:1）；模块体内 `url:` / `method:` / `.W)(` 各 259 次〔源：`recovered-web/decompiled/umi.d08aa716.js`〕 |
| 2 | 字面量 209 + 拼接 49 | **210 + 49 = 259** | 同上，逐个函数按 url 表达式是否含 `.concat(`/`+` 分类 |
| 3 | 漏出 api-catalogue **62** | **63** | 259 − 196；其中 49 拼接 + 14 字面量（原按 209 算成 13） |
| 4 | 字面量漏项中 gaop-be 端点 **10** | **11** | 逐条查 api-docs：14 条里 3 条走 gaop-fdfs（`/v1/pub/file/{storage,list,delete}`），其余 11 条在 api-docs |
| 5 | `apiDocsOpsCalledByServiceLayer` **254** | **255** | 255 个面向 gaop-be 的函数（259 − 4 个 baseURL 覆盖）与 api-docs **1:1**：严格占位符匹配（两侧占位符位置须一致）得 0 未命中、0 多义 |
| 6 | 被调 **283** / 无人调 **259** | **279 / 263** | 原值与 JSON `endpoints` 记录不符（记录算出 278/264，另有 5 条未进桶）；现全部按**端点记录**重算，满足 `279+263=542`、`255+24=279` |
| 7 | 桶二 **317** | **321** | 263 live-uncalled + 58 controller-only（5 个 `/k8s/**` 归 `nonBusinessRoutes`，不计入） |
| 8 | `meta.sources` 描述 255/209/46、漏 46 | 259/210/49、漏 63 | 同 #1–#3，原描述与自身 `counts` 互相矛盾 |

> 第 5 条新增的那个函数是 `fn Qt`（导出名 `PKV`）→ `GET /v1/service/serviceAuth`，调用页面 chunk `p__ServiceAuth.504b1a3e.async.js` → `src/pages/ServiceAuth`〔源：`umi.d08aa716.js` module 22239 `function Qt`；`chunks.json` 的 `p__ServiceAuth.*` → `src/pages/ServiceAuth`〕。JSON 中该端点的 `frontendCallers` 已补上。

### C.3 复核后仍为「未定论」

| # | 事项 | 为什么不定论 |
|---|---|---|
| 1 | 桶二 263 条是否含「非本前端调用方」（运维脚本 / 其他系统） | 只有本仓库证据，不能证明全系统无人调用 |
| 2 | §2 表「前端 URL 字面量 229 / 40 / 35」 | 字面量级统计，本次复核用的是另一套字面量口径（只对 service 模块外做全 chunk 字符串扫描），两者不可直接换算 → 保留原值，标未定论 |
| 3 | `/v1/pub/fdfs/**` 同名端点由 gaop-be 还是 gaop-fdfs 承接 | 需网关/ingress 路由表，仓库内无 |
| 4 | 端口 `:19527` / `:19528` ↔ 服务对照 | 仓库内无映射证据 |
| 5 | `nearMissPaths.autoScan` 的 68 组末段碰撞哪些是真重复 | 只做机器枚举，未逐组判定业务语义 |

### C.4 复核用到的可复现命令

```bash
# 前端 service 层硬计数（本附录 #1 的直接证据）
python3 - <<'PY'
import re
t=open('recovered-web/decompiled/umi.d08aa716.js',encoding='utf-8',errors='replace').read()
F=re.compile(r"\n\s*(\d+):\s*function\s*\(([^)]*)\)\s*\{")
m=[(int(x.group(1)),x.start()) for x in F.finditer(t)]; m.sort(key=lambda z:z[1])
for i,(mid,s) in enumerate(m):
    if mid==22239: b=t[s:(m[i+1][1] if i+1<len(m) else len(t))]
print(len(re.findall(r'\burl:',b)), len(re.findall(r'\bmethod:\s*"',b)),
      len(re.findall(r'\n\s+function\s+[\w$]+\s*\(',b)), len(re.findall(r'\.W\)\(',b)))
PY
# 期望输出: 259 259 259 259
```

---

*本工单只写入 `docs/findings/02-contract-baseline.{md,json}` 两个文件；`recovered/` 与 `recovered-web/` 全程只读，未 SSH 集群、未安装依赖、未落任何脚本文件。*
