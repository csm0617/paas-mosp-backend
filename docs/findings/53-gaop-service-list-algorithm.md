# `GET /v1/service/gaop` 算法与常量全量摘录（下一轮可直接开工，无需再读源码）

**证据等级**：〔代码事实〕（逐行摘录）+〔运行实测〕（该端点确实返回 200）。2026-10-14。
本轮**没有写业务代码**：把开工需要的一切一次摘全，下一轮直接写。

## 1. 端点签名与校验

```
GET /v1/service/gaop?page(默认1)&size(默认20)&type(**必填**: 1=框架系统服务 2=基础服务)&keyword(可选)
```
- `page/size` 走厂商的 `SqlUtils.normalizePage/normalizeSize`（我们的 `PageUtils` 已实现同款怪癖）；
- `keyword` 长度 **> 45** → 400 `service.invalid.service.name.length`。

## 2. 过滤算法（逐行）

对 `allPageService(null)` 得到的每个 `ServicesItem`：

```
1) 若 e / e.deployment / metadata / labels 任一为 null → **丢弃**
2) appType = deployment.metadata.labels["system/appType"]
3) 若 appType != null:
       type==1 → appType == "gaop-core"
       type==2 → appType == "gaop-base"
   否则（**没有 appType 标签**）:
       type==1 → name ∈ PASS_FRAME_SERVER_NAME_SET
       type==2 → name ∈ PASS_FRAME_SERVER_NAME_SET   ← 注意！见 §5 的缺陷
4) 命中则 e.setFixedName(gaopName.get(name))
5) 再尝试追加两个"伪服务"：gis-server、gis-jb
   —— 条件是 tenxCloud.existService(...) 为 true **且** tenxCloud.service(...).success()
      本环境 existService 恒 false（findings/51）⇒ **这两条分支不可达**
```

## 3. 常量全量（厂商 `constant/GaopName.java`，48 行）

`gaopName`（18 条，用于 `fixedName` 显示名）：

| name | 显示名 | name | 显示名 |
| --- | --- | --- | --- |
| gaop-be | 框架后端 | gaop-fdfs | 文件存储 |
| gaop-web | 框架前端 | gaop-image-uploader | 镜像服务 |
| gaop-mysql | 框架数据库 | zk | zookeepper |
| gateway | 网关 | gaop-radondb-mysql-leader | 业务数据库 |
| gaop-redis | 框架redis | mysql | 业务数据库 |
| gaop-auth | 框架授权 | redis | 业务redis |
| gaop-startup | 框架分批启动控制 | kafka | kafka |
| gaop-gateway-auth | 网关 | gis-server | 地图服务 |
| gaop-login | 统一登录 | gis-jb | 军标服务 |

`PASS_FRAME_SERVER_NAME_SET`（9）：`gaop-be, gaop-web, gaop-redis, gateway, gaop-mysql, gaop-auth, gaop-startup, gaop-gateway-auth, gaop-login`
`BASE_SERVER_NAME_SET`（7）：`gaop-fdfs, gaop-image-uploader, zk, gaop-radondb-mysql-leader, redis, kafka, mysql`
`AppTypeLabelsEnum`：`GAOP_BASE("gaop-base")`、`GAOP_CORE("gaop-core")`、`BUSINESS("business")`，标签名常量 `system/appType`。

## 4. 关键词与分页

```
keyword != null:
   先按 deployment.metadata.name.contains(keyword) 过滤
   若结果为空 → 改用 ServiceController.filterServiceName(keyword, e) 再过滤一次
分页：pagination(collect, page, size)
```
（`filterServiceName` 的规则下一轮需要补读，它是 keyword 的**兜底**匹配。）

## 5. 顺带发现一个**厂商缺陷**（要照抄，但必须记下来）

```java
} else if (type == 1) { result = GaopName.serviceInPassFrame(name); }
else if (type == 2)  { result = GaopName.serviceInPassFrame(name); }   // ← 应该是 serviceInBaseService
```

**没有 `system/appType` 标签的服务**，无论要 type=1 还是 type=2，走的都是**同一套 PASS_FRAME 集合**。
后果：`BASE_SERVER_NAME_SET` 与已经写好的 `serviceInBaseService()` 在这条路径上**从未被使用**（死代码）。
也就是说——**在本环境（工作负载没有 `system/appType` 标签）里，`type=2` 查"基础服务"实际返回的是框架服务**。

这条和之前那些"照抄不修好"是同一类：**能一眼看出是复制粘贴错误，但它就是当前线上行为**，
删掉它反而会与厂商不一致。

## 6. 下一轮的开工程序（无需再读源码）

1. 把 §3 的常量搬进域层（`GaopName` 等价物：`gaopName` 映射 + 两个 name set）；
2. 实现 `filter`（§2）+ `fixedName`；**type=2 走 PASS_FRAME**（照抄 §5 的缺陷，并加注释与用例钉住）；
3. keyword 两段式（§4，兜底规则需先补读 `filterServiceName`）+ 复用 `PageUtils` 分页；
4. 形状直接复用已对拉的 `ServiceList`（`{runningDeployment,total,count,services}`）；
5. 对拉：`type=1`、`type=2`、带 `keyword`、`keyword>45` 四种情形。

## 7. 状态

本轮零业务代码改动；只读探测；全量用例仍 **34 类 220 条全绿**。

## 8. 本轮已把 §2/§3 落成代码（可执行规格）

- `domain/software/valobj/GaopServiceNames.java`：18 条显示名 + 两个名单 + 标签常量（照抄厂商 `GaopName`）；
- `domain/software/valobj/GaopServiceFilter.java`：`matches(type, name, appType)` 逐行照抄 §2（含 §5 的缺陷）；
- `GaopServiceFilterTest` **5 条**，其中一条专门把缺陷钉成**可执行证据**：
  `baseOnlyServicesAreNeverReturnedWhenLabelIsAbsent()` —— 遍历基础名单，断言在 `type=2` + 无标签时**全部查不出来**，
  同时又断言 `serviceInBaseService("gaop-fdfs") == true`（名单本身没问题，是**调用点**用错了）。
  这样将来若有人"顺手修好"这个缺陷，测试会**立刻红**，而不是等到对拉时才发现。

**一处仍待对拉确认**：厂商 `fixedName` 是 `gaopName.get(name)`（取不到就是 **null**），
我实现成**回退原名**。下一轮接控制器时要拿一个"不在名单里的服务"对拉确认前端读的是哪个。
