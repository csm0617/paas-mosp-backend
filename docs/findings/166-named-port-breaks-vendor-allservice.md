# 166 · 命名端口让厂商 `allService` 全链 500（我们自己的清单踩的坑）

**轮次**：2026-11-09（用户报障）
**触发**：用户在浏览器打开 `http://172.16.209.170/api/gaop/v1/software/overView`，拿到
`{msg:"系统错误", code:-1}`，`trace` 是
`java.lang.NullPointerException at TenxService.lambda$allService$49(TenxService.java:3179)`，
末帧 `TenxService.allService(TenxService.java:3182)`。

## 1. 结论先说

**不是平台装坏了，是我们自己的 `paas-web` / `paas-web-preview` 清单里用了命名端口**
（`probe.httpGet.port: http`、`Service.spec.ports[].targetPort: http`）。
厂商把集群对象用 **Jackson 反序列化到自带旧模型**，而该模型把这两个字段声明成
`Integer`（`IoK8sApiCoreV1HTTPGetAction.port`、`IoK8sApiCoreV1ServicePort.targetPort`），
遇到合法字符串就抛 `InvalidFormatException`；异常被 catch 后**把整个工作负载置 null**，
下一行做 `o.getDeployment().getSpec().getReplicas()` 时 NPE，异常上抛到全局处理器
⇒ 前端看到 `系统错误 / code:-1`。

命名端口本身是**合法 k8s**（`containerPort` 声明了 `name: http` 就能被引用），
所以这是厂商侧的健壮性缺陷（记为 `deviations.md` **A14**），但并存期是我们拖崩了厂商控制台，
清单侧改数字端口。

## 2. 调用链与两处缺陷（代码事实 + 运行实测）

```
SoftwareController.getOverViewInfo:1202
└─ SoftwareService.getOverViewInfo:1907
   └─ TenxCloudImpl.allPageService:924        // 分页壳，翻页反复调 allService
      └─ TenxCloudImpl.allService:357
         └─ TenxService.allService            // ① 转换失败 → 元素 deployment=null
            ├─ convertObj(...)  :3152         // ← 3152 抛 InvalidFormatException，被 catch 吞
            └─ services.stream()
                 .filter(o -> o.getDeployment().getSpec().getReplicas() > 0)  // ← :3179 NPE
                 .count()                                                // ← :3182
```

**① 反序列化失配**。`TenxService.convertObj`（反编译 `TenxService.java:483`）是
`GSON.toJson(obj)` → `JACKSON.readValue(json, 旧模型)`。实测异常（gaop-be 日志）：

```
com.fasterxml.jackson.databind.exc.InvalidFormatException:
  Cannot deserialize value of type `java.lang.Integer` from String "http": not a valid Integer value
  (through reference chain: IoK8sApiExtensionsV1beta1Deployment["spec"]->...DeploymentSpec["template"]
   ->...PodTemplateSpec["spec"]->...PodSpec["containers"]->ArrayList[0]->...Container["livenessProbe"]
   ->...Probe["httpGet"]->...HTTPGetAction["port"])
    at TenxService.convertObj(TenxService.java:483)
    at TenxService.allService(TenxService.java:3152)
```

`allService` 里两处转换都是**吞异常**写法（反编译 2311-2317、2329-2335）：
`catch(Exception e){ log.error("转换失败", e); }` ⇒ `beta1Deployment` 保持 null ⇒
`servicesItem.setDeployment(null)`。同一个 `ServicesItem` 的 `service` 字段也有同样的坑
（`serviceResponse` 失败**直接 `return null`**，反编译 506-518），日志里紧跟着的另一条
`转换类型失败` 就是 `Service.spec.ports[].targetPort: "http"`。

**② 后续不做空判**。`filter` 只写 `o.getDeployment().getSpec().getReplicas() > 0`，
三层都可能为 null，且 `getReplicas()` 是 `Integer`，`> 0` 处还会拆箱。

`javap` 逐指令对上了这一行（证据等级：**代码事实**，且证明线上 jar 与离线包同一份）：

```
$ unzip -p gaop-be-0.0.1.jar BOOT-INF/classes/.../TenxService.class > TenxService.class
$ javap -p -c -l TenxService.class | grep -A4 'lambda\$allService\$49'
  private static boolean lambda$allService$49(ServicesItem);
       0: aload_0
       1: invokevirtual  ServicesItem.getDeployment()          // ← deployment 为 null 就在这炸
       4: invokevirtual  IoK8sApiExtensionsV1beta1Deployment.getSpec()
       7: invokevirtual  IoK8sApiExtensionsV1beta1DeploymentSpec.getReplicas()  // Integer
      10: invokevirtual  Integer.intValue()
    LineNumberTable: line 3179: 0
```

栈帧里 `ReferencePipeline.count → LongPipeline.sum` 是 JDK 的 `count()` 实现
（`ReduceOps.makeLongCounting()`），说明**谓词是在 `count()` 里执行的**，
和反编译源码 `(int) services.stream().filter(...).count()` 完全对得上。

## 3. 影响面（比"概览页打不开"大得多）

`allPageService` 是**服务列表的总入口**，被至少 6 处调用；任意一个工作负载带命名端口，
这些端点**全部** 500：

| 调用点（反编译行号） | 端点 |
| --- | --- |
| `SoftwareService:1907` | `GET /v1/software/overView`（本次报障） |
| `ServiceController:1570 / 1766 / 1948 / 3495` | `GET /v1/service` 列表及其变体 |
| `PublicController:445` | `GET /v1/pub/service_status`（大方屏/状态轮询，**免登录**） |
| `DataBaseController:395`、`ModeServiceImpl:229`、`DatabaseService:1687` | 库/模式列表 |

也就是说：**任何租户把自己的服务写成 `port: http`，厂商控制台的服务列表就整体不可用** ——
这条对厂商是产品级缺陷，不只是本环境问题。

## 4. 修复与验证（运行实测）

清单改成数字端口（`containerPort` 的 `name: http` 保留，不影响）：

- `paas-web/deploy/k8s/paas-web.yaml`：`readinessProbe/livenessProbe.httpGet.port: 80`、`Service.targetPort: 80`
- `paas-web/deploy/preview/paas-web-preview.yaml`：同上

`kubectl apply --validate=false`（apiserver 的 openapi 下载超时，故关掉客户端校验）
→ 两个 Deployment 滚动更新成功、探针通过。验证：

```bash
$ curl -s --noproxy '*' "http://172.16.209.170/api/gaop/v1/pub/service_status"
HTTP 200
{"msg":"OK","code":0,"data":{"service":[
  {"serviceName":"paas-web-preview","status":"RUNNING"}, {"serviceName":"paas-web","status":"RUNNING"},
  {"serviceName":"gaop-auth",...}, ... 共 11 个]}}
```

这条路径与 overView 共用 `allPageService → allService → count()`，返回 200 且列全 11 个服务
（含我们两个）就说明 3179 不再 NPE。修复后再抓 gaop-be 日志，`转换失败 / 转换类型失败 /
lambda$allService$49` 的最后一条停在 `21:23:29`（报障时刻），修复后零新增。

## 5. `paas-core` 侧不受影响，但要记一笔

- 我们走 **fabric8 原生模型**，`IntOrString` 天然支持命名端口，没有 GSON→Jackson 的二次转换；
- `ServiceQueryServiceImpl` 的 running 计数是 `s.getReplicas() != null && s.getReplicas() > 0`（262、2398、2438 行），空值安全；
- 进 `deviations.md` **A14**：厂商缺陷不复制，且不把"转换失败"降级成 null 继续往下跑。

## 6. 教训（并存期硬约束）

**凡是我们自己往 `gaop-namespace` 塞的工作负载/Service，端口一律写数字。**
厂商旧模型解析不了的字段不止端口一处（同类：任何 `IntOrString` 字段、任何新版本 k8s 字段），
只是这一处必定触发。排查这类"页面级 500"时，先看 gaop-be 日志里有没有
`转换失败`（伴随 `InvalidFormatException` 的 reference chain 会直接指出是哪个对象哪个字段）。

另：本机 shell `grep` 在该证据根目录下偶发**静默返回空**（本轮又踩一次，
`grep 3179` 明显应命中却返回空），查仓库/反编译事实一律用 Grep 工具或 `rg`。
