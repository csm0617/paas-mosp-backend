# `PUT /v1/software/order/{orderNum}` 规格 + 实现（第 138 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:379-384` + `SoftwareService.orderSoftWare:3816-3832` 逐行读）
+〔运行实测〕（真集群 + 真库）。2026-10-14。

## 1. 端点

```java
@PutMapping("/order/{orderNum}")
public Response<Boolean> orderSoftWare(@PathVariable Integer orderNum, @RequestBody SoftwareIdForm softwareIdForm)
```
`SoftwareIdForm{softwareIds: List<String>}`；`orderNum` 在**路径**上。

## 2. 流程

```
if (form != null && !CollectionUtils.isEmpty(form.softwareIds)):
    for id in softwareIds:
        software = selectByPrimaryKey(id)
        不存在 ⇒ 400 `software.id.not.found`（**带该 id 做参数**）
        setOrderTop(orderNum)；setOrderTime(**System.currentTimeMillis()**)；updateByPrimaryKey
    return true
return false          ← HTTP 200 + `data:false`（form 为 null 或列表为空），**不碰库**
```

## 3. 两处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`order_time` 是"每个 id 各取一次"** | 循环体内 `System.currentTimeMillis()`（不是循环外取一次）⇒ 多 id 时时间戳会差几毫秒（实测 `…645` vs `…654`） |
| 2 | **边遍历边写** | 第 2 个 id 不存在时抛 400，但**第 1 个 id 已经写进库了**（实测确认）。照抄，不加事务 |

另外：`order_top`/`order_time` 两列的**默认值是 0**（不是 NULL）——新建软件的置顶状态就是 0。

## 4. 实现与验证

**实现**：`ISoftwareCommandService.orderSoftware` + 控制器端点 + `SoftwareIdFormDTO`；
DAO/仓储新增 `updateSoftwareOrderTop`（只写这两列——**不能**用 `updateByPrimaryKey`，那条不写 `updated_at` 但会写很多别的列，
而这里厂商的整行更新净效果就是这两列）。

### 4.1 离线：`SoftwareOrderTest` 5 例，全量 **442** 条 0 失败

覆盖：空/null 列表 ⇒ `false` 且不碰库；逐个写 `order_top`+`order_time`；`orderNum` 可为 null；
某个 id 不存在 ⇒ 400 且**参数是那个 id**、且**前面的 id 已写**；写入顺序（先查后写、逐个推进）。

### 4.2 在线：真集群 + 真库（seed 两行 `sw-o1`/`sw-o2`，`order_top`/`order_time` 均为默认 0）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `PUT /order/1` `{"softwareIds":["sw-o1","sw-o2"]}` | `200 data:true`；两行 `order_top=1`，`order_time` 分别为 `…645` 与 `…654`（**每个 id 各取一次时间**） |
| ② | `PUT /order/0` `{"softwareIds":[]}` | `200 data:false`；`order_time` **未变** ⇒ 没碰库 |
| ③ | `PUT /order/9` `{"softwareIds":["sw-o1","ghost"]}` | `400 置顶错误,找不到软件id为 ghost`；而 **`sw-o1` 的 `order_top` 已经变成 9** ⇒ "边遍历边写"实测确认 |

### 4.3 收尾

探针 `g_software` 行已删（`g_software`=0）、临时 NodePort 与后端进程已回收；实验室其余数据未动。
