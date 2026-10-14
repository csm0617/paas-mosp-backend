# `GET /v1/config_map`（配置组列表）规格 + 实现 + 一处旧结论更正（第 149 轮）

**证据等级**：〔代码事实〕（厂商 `ConfigMapController.listConfigMap:185-243` 逐行读）
+〔运行实测〕（真集群 ConfigMap，含标签选择器与"total 不随关键字变"的反直觉行为）。2026-10-14。

## 1. 端点

```java
@GetMapping                                  // 类级 @RequestMapping("/v1/config_map")
public Response<ConfigMapList> listConfigMap(@RequestParam(required=false) Integer page,
                                             @RequestParam(required=false) Integer size,
                                             @RequestParam(required=false) String keyword)
```
响应 `ConfigMapList`（厂商 `extends IoK8sApiCoreV1ConfigMapList` + `total`）⇒ JSON 键：
**`apiVersion` / `items` / `kind` / `metadata` / `total`**。

## 2. 流程

```
page = normalizePage(page)；size = normalizeSize(size)
if (keyword 非空白) keyword = keyword.replaceAll(" +","")        // **只去空格**
list = 列 ConfigMap，**标签选择器 `!ingress-lb`**（= 该标签不存在）
filtered = list.items 里**再排除两个硬编码名字**：`ingress-nginx-controller`、`kube-root-ca.crt`
if (keyword 空白):  items = filtered.skip((page-1)*size).limit(size)；total = filtered.size()
else:               matched = filtered.filter(name.matches("(.*)<keyword>(.*)"))
                    if (matched 为空) items = **null**
                    else items = matched.skip(...).limit(size)
                    total = filtered.size()          ← **仍是过滤前的总数**
任何异常 ⇒ 500 `configmap.get.list.failed`
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`total` 恒为"排除两个名字之后、关键字过滤之前"的数量** | 带关键字时 `total` **不反映命中数**（实测③：`keyword=alpha` ⇒ items 1 条、**total 仍 3**） |
| 2 | **匹配为空 ⇒ `items` 为 `null`** | 不是空数组（实测④：响应里 `"items": null`） |
| 3 | 关键字**只去空格**、不转小写、用**正则**匹配 | `" cm "` ⇒ `cm`（实测⑤命中 3 条）；正则未转义 ⇒ 关键字里的正则元字符会被当模式 |
| 4 | 两个硬编码排除名 | `ingress-nginx-controller`、`kube-root-ca.crt`（实测①：`kube-root-ca.crt` 被排除） |

## 4. ⚠️ 一处旧结论的更正（`findings/134` §3 第 3 点）

早前我在 `exportConfigmap` 的规格里写过"厂商的 `!ingress-lb` **selector 非法** ⇒ 那条分支恒返回空列表"。
**这是错的**，本轮更正：

- 厂商把它传在 `listNamespacedConfigMap(...)` 的**第 6 个参数**，即 **`labelSelector`**；
- `!key` 是**合法的标签选择器**语法（"该键不存在"）——实测 `kubectl -n gaop-namespace get cm -l '!ingress-lb'`
  **正常返回** `kube-root-ca.crt`；
- 我早前是用 `kubectl --field-selector='!ingress-lb'` 验证的，那是**字段选择器**，才报
  `invalid selector: '!ingress-lb'`。**参数位置看错一位，结论就反了。**

结论修正为：`exportConfigmap` 的"不带 key"分支**是死代码（`||` bug）**；**若可达**，它会列出"不带
`ingress-lb` 标签"的全部 ConfigMap（**不是空**）。我们的处理（没有 key ⇒ 空列表，B22）不变，只是**理由**改了。

> 判据（供以后复用）：厂商多处调用 `listNamespaced*(ns, null, null, null, **fieldSelector**, **labelSelector**, …)`；
> `AppService.resetService` 里传的 `"system/appName!=gaop"` 是**标签**语法且位于第 6 位 ⇒ 第 6 位就是 `labelSelector`。

## 5. 实现与验证

**实现**：`IServiceQueryService.queryConfigMapList` + 新建 `ConfigMapController`（`GET /`）+ `ConfigMapListDTO`/
`ConfigMapListEntity`；新增端口 `listConfigMapsWithoutIngressLb`（fabric8 `withoutLabel("ingress-lb")`，
返回**整份列表快照**以带上 `apiVersion`/`kind`/`metadata`）。

### 5.1 离线：`ConfigMapListTest` 6 例，全量 **505** 条 0 失败

覆盖：硬编码排除两个名字；分页切片但 `total` 是全量；关键字命中时 `total` 不变；**匹配为空 ⇒ items=null**；
关键字去空格；集群失败 ⇒ 500 `configmap.get.list.failed`。

### 5.2 在线：真集群（造 `cm-alpha`/`cm-beta`/`cm-gamma` + 带 `ingress-lb` 标签的 `cm-ingress`）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | 无参数 | `200`；`total=3`、`kind=ConfigMapList`、items `[cm-alpha, cm-beta, cm-gamma]` ⇒ 带标签的 `cm-ingress` 与 `kube-root-ca.crt` **都被排除** |
| ② | `?page=1&size=2` | `total=3`（全量）但 items **2 条** ⇒ 分页 |
| ③ | `?keyword=alpha` | items `[cm-alpha]` 但 **`total=3`** ⇒ 第 3 节 1 实测确认 |
| ④ | `?keyword=zzz` | **`items: null`**、`total=3` ⇒ 第 3 节 2 实测确认 |
| ⑤ | `?keyword=" cm "` | 去空格 ⇒ `cm` ⇒ **3 条命中**（若没去空格会一条都不中）⇒ 第 3 节 3 实测确认 |

### 5.3 收尾

4 个探针 ConfigMap、临时 NodePort、后端进程全部回收；本轮不写库。
