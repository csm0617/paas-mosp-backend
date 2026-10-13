# K2 续：`DELETE /v1/software/{id}`（删除软件）——四表顺序删除，响应与厂商一致

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 结果

| 项 | 实测 |
| --- | --- |
| 成功响应 | HTTP 200 `{"msg":"OK","code":0,"data":null}`，两侧一致 |
| 库内效果 | `g_software` / `g_role_software` / `g_service_port` 全部清零（两侧各删自己的探针） |
| 不存在 | HTTP 400 `code:400` `msg:"id指定软件不存在"`，两侧逐字一致 |

## 2. 删除顺序（照抄 `SoftwareController.removeSoftWare:388-395` + `SoftwareService.removeSoftware:1520-1533`）

```
g_role_software（按 app_id = 软件id）
  → g_software_app（按 software_id）
  → g_service_port（按 software_id）
  → g_software（主行）
```

注意第一张表是按 **`app_id`** 删（不是 software_id），其余三张按 `software_id`——厂商这里命名不一致，
照抄时序比照抄字段名更要紧。已写入 UL 测试（`removeDeletesFourTablesInVendorOrder`，用 Mockito `InOrder` 断言顺序）。

## 3. 两处声明

1. **我们与厂商的一处收紧**：厂商这条删除链路**没有事务**（controller 与 service 都没打 `@Transactional`），
   中途失败会留下"删了一半"的软件。我们把整个用例包进一个事务。这是**收紧**，不会让任何成功请求的结果与厂商不同。
2. **未实现的副作用**：厂商在删掉 `g_service_port` 行（`delete > 0`）时会调 `notificationGateway()`
   起线程通知网关重载规则；我们尚未接管网关（与 `findings/39` 同一条待办）。

## 4. 一处**没有被端到端验证**的路径（如实记录）

厂商还删了 `g_software_app`。这个环境里 `g_software_app` 是**空表**，我尝试插一行探针时因该表
`id` 不是自增（或约束不满足）而没插进去，所以**这条 delete 语句在本轮没有真正执行过**。
它与其他三条是同一个形状（`DELETE ... WHERE software_id = ?`），风险低，但我把它标成
"未端到端验证"，而不是含糊地说"全部验证通过"。

## 5. 测试与清理

- 新增用例测试 4 条（新增时"先插行再按 key 关联战位"、无 key 时不碰关联、删除四表顺序、删除前先校验存在性）；
- 全量 `mvn test` **152 条全绿**（上一轮 148）；
- 探针软件、关联、端口、白名单全部删除，库回到 `software=0 / white_list=0 / role_software=0 / service_port=0 / user=1 / role=2`。

## 6. 顺便说明：`GET /v1/software/{id}` 仍然没做（原因见 `findings/39` §5）

它要 `licence`（K3 JWT 验签/机器码）、`uploadStoreRecord`（上传记录）、`status`/`serviceList`（K8s 现场态）三样东西。
本轮我本来打算先做其中"没有激活码 → `LicenceInfo.expired(...)`"那一支（该支不需要密码学），
读代码后确认它成立（`LicenceInfo.expired(code)` = `{end:-1, endIso8601:null, code:code, expired:true}`，
其中 `code = 机器码 + Base64(key)`）；但**在软件详情端点本身做出来之前，这段逻辑没有调用方**，
写进去就是死代码，所以本轮没有写。下一轮连同详情端点一起做。
