# `POST /v1/software/exportConfigmap` 规格 + 实现（第 141 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:746-750` + `SoftwareService.exportConfigMapBySoftwareOrService:3915-4018` +
`exportConfigMap:3453-3477` 逐行读）+〔运行实测〕（真集群 ConfigMap/Deployment + **实测那条非法 selector**）。2026-10-14。

## 1. 端点

```java
@PostMapping("/exportConfigmap")
public void exportConfigBySoftwareOrService(@RequestBody ExportConfig exportConfig, HttpServletResponse response)
```
响应**不是 JSON 信封**，而是一个附件：`Content-Type: application/octet-stream`、
`Content-Disposition: attachment; filename*=UTF-8''configmap.json`，内容是**ConfigMap 对象数组**的紧凑 JSON。

## 2. 流程

```
1. 收集 ConfigMap 名（与 existConfigmap 同一套，但有一处不同）：
     type == "software"：逐个 key（软件 id）→ g_software_app.app_name → 逐个 Deployment 收集 volumes[*].configMap.name
                          （Deployment 读不到 ⇒ **记日志跳过**）
     type == "service" ：逐个 key（Deployment 名）直接收集
                          （Deployment 读不到 ⇒ **抛 400 `service.get.service.config.identifier.exception`（带 key）**）
2. 按名读 ConfigMap（**读不到的跳过**），得到列表
3. Jackson 序列化（`JsonConfig.JACKSON` 未开缩进 ⇒ 紧凑）→ 写临时文件 → 作为附件写回
4. 写 JSON 失败（IOException）⇒ 400 `sync.get.config.group.data.exception`
```

## 3. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **service 分支与 software 分支对"读不到 Deployment"的处理相反** | 前者 **400**、后者**跳过**——同一个端点里两种态度 |
| 2 | **名字集合是 `HashSet` ⇒ 顺序不确定** | 所以导出文件的元素顺序**不可依赖**（对比时只能比集合） |
| 3 | **"不带 key ⇒ 列全部 ConfigMap"是死代码** | `key != null \|\| key.size() != 0` 的 `\|\|` bug 让该分支**不可达**（`key != null` 为真时已进第一支；`key` 为 null 时 `key.size()` 直接 NPE）。⇒ 导出恒为"按 key 收集"的结果 |

> ⚠️ **更正（第 149 轮）**：本文件早前写的是"该分支的 selector `!ingress-lb` **非法** ⇒ 导出恒为 `[]`"——**这是错的**。
> 厂商把它传在 `listNamespacedConfigMap` 的**第 6 个参数**（`labelSelector`），而 `!key` 是**合法的标签选择器语法**
> （"该键不存在"）；我早前用 `kubectl --field-selector='!ingress-lb'` 验证，那是**字段选择器**，才报非法。
> 实测对照：`kubectl -n gaop-namespace get cm -l '!ingress-lb'` **正常返回** `kube-root-ca.crt`。
> 结论修正为：该分支**不可达**（`||` bug），但**若可达**，它会列出"不带 `ingress-lb` 标签"的全部 ConfigMap（不是空）。
> 我们仍按"没有 key ⇒ 空列表"处理（B22 不变），只是**理由**从"selector 非法"改为"分支不可达"。

## 4. 实现与验证

**实现**：`IServiceQueryService.collectConfigMapsForExport` + 控制器端点（写附件）+ `ExportConfigMapWriter`（Jackson 紧凑序列化）；
新增端口 `readDeploymentConfigMapNamesOrThrow`（读不到就抛，供 service 分支）与 `readConfigMaps`（按名读、读不到跳过）。

### 4.1 离线：`SoftwareExportConfigMapTest` 6 例，全量 **458** 条 0 失败

覆盖：software 分支去重收集（`LinkedHashSet`）；service 分支走"读不到就抛"的变体；
**service 分支缺 Deployment ⇒ 400（带 key）且不读 ConfigMap**；software 分支缺 Deployment ⇒ 跳过（不抛）；
空 key / 未知 type ⇒ 空列表；名字原样传给端口（"读不到就跳过"由端口负责）。

### 4.2 在线：真集群（造 `cm-e1`/`cm-e2` + `exp-dep`（两个 configMap 卷）+ seed `g_software{sw-exp}` 关联它）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `{"type":"service","key":["exp-dep"]}` | `200`；响应头 `Content-Disposition: attachment; filename*=UTF-8''configmap.json` + `Content-Type: application/octet-stream`；正文 JSON 数组 = `[cm-e1, cm-e2]` |
| ② | `{"type":"software","key":["sw-exp"]}` | `200`；同样两个 ConfigMap（经 `g_software_app` 走到 `exp-dep`） |
| ③ | `{"type":"service","key":["ghost"]}` | `400 获取服务标识ghost的配置信息出现异常，导出配置组出错` |
| ④ | `{"type":"software","key":[]}` | `200`，正文 **`[]`** ⇒ 与第 3 节第 3 点（死分支 + 非法 selector ⇒ 恒空）一致 |

### 4.3 收尾（含一次"命令失败但效果已发生"）

探针 ConfigMap/Deployment 与 DB 行已删、临时 NodePort 与后端进程已回收（复核：`g_software`=0、`g_software_app`=0、残留探针资源 0）。

**环境记录**：本轮准备阶段集群又抖（etcd `request timed out`），但**命令报错的两次 ConfigMap 创建其实服务端已经写成功**，
重试时报 `already exists`；DB 的两条 INSERT 同样"报错但已写入"。⇒ 对这类抖动，**重试 + 复核实际状态**比反复重跑更可靠。
