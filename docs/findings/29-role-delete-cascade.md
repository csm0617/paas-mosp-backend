# 切片 3b 写路径：删战位的 6 表级联（事务边界在 Case 层）

**证据等级**：〔运行实测〕+〔代码事实〕。2026-10-14。

## 1. 契约

| 项 | 厂商（实测/源码） | 我们 |
| --- | --- | --- |
| 入口 | `DELETE /v1/role?roleId=a,b,c`（**query 参数、逗号分隔**） | 同 |
| 成功响应 | `{"msg":"OK","code":0,"data":null}`（实测） | **逐字节相同**（实测） |
| 不存在 | HTTP 400 / `code:400` / `msg:"找不到该席位"`（实测） | **逐字节相同**（实测） |
| 内置战位 | HTTP 400 / `code:400` / `msg:"无法删除该席位"`（实测） | **逐字节相同**（实测） |

`msg` 是消息包文案（`role.missing.storage` / `role.cannot.delete.storage`），不是错误码——见 `docs/findings/24-error-contract.md`。

## 2. 删除顺序与事务边界（本轮真正的工作）

厂商 `RoleService.deleteRoleRelation`（`RoleService.java:707-730`）：**6 张表按固定顺序删**，且 `@Transactional`
打在**包含 `forEach` 的整个方法**上：

```
g_sub_role_role → g_user_audit → g_user_role → g_user_login → g_role_software → g_role
```

**顺序是硬约束，不是风格**：`g_user_role`、`g_sub_role_role` 上各挂 2 条**没有 `ON DELETE CASCADE`** 的外键
（实库实测，见 `docs/findings/23-k1-live-schema-and-unique-constraints.md`），先删 `g_role` 会被数据库直接拒绝。

**事务边界必须在 Case 层**（`paas-core-case`）：厂商的语义是"删三个战位、第三个是内置战位 → **整体回滚**"。
若把 `@Transactional` 下沉到单表 DAO 或单个 repository 方法，前两个会被提交，行为就与厂商不同。
本轮为此把 `paas-core-case` 从空壳变成了真正的一层（`IRoleDeleteCase` + `RoleDeleteCaseImpl`），
并给它加了 `spring-tx` 依赖。

## 3. 对真实库的级联验证（不是只跑 H2）

探针流程（**全部可回收，已确认清理干净**）：

```bash
V=<kubectl proxy 到 gaop-be>; M=http://127.0.0.1:8080
# 1) 用**厂商**建一个探针战位
RID=$(curl -s -X POST "$V/v1/role" -H 'Content-Type: application/json' \
      -d '{"key":"probe-del-20261014","name":"探针战位","type":1}' | jq -r .data.id)
# 2) 往关系表插行（g_sub_role_role 那行会因外键失败——正好证明该外键存在）
# 3) 用**我们的后端**删
curl -s -X DELETE "$M/v1/role?roleId=$RID"      # → {"msg":"OK","code":0,"data":null}
# 4) 逐表核对：g_role / g_user_role / g_user_login / g_user_audit / g_role_software 全为 0
```

结果：删除前 5 张表各 1 行（`g_sub_role_role` 因 `g_sub_role` 外键插入失败，**说明该外键真的在**），
删除后**全部为 0**。这说明我们的删除顺序能在**真实外键约束**下跑通——H2 里没有外键，光跑单测证明不了这一点。

**顺带确认**：厂商删除成功响应 `{"msg":"OK","code":0,"data":null}` 与我们**逐字节相同**；探针删完后
`/v1/role` 恢复为原始 2 行（`admin`、`user`）。

## 4. 照抄而不是"修好"的一处

厂商 `RoleConstant` 里声明了 4 个保留字（`admin`/`dev`/`cicd`/`root`），但**删除路径只护 `admin`/`dev`**
（`RoleService.java:716`），`cicd`/`root` 实际**可以被删掉**。重写版照抄该行为并写进单测
（`RoleCommandServiceImplTest.cicdAndRootAreNotProtectedInVendorBehavior`）——
要"修好"必须先挑战判断，因为它会改变与厂商的行为一致性（见 `docs/findings/22` §1.8 的 R5）。

## 5. 未实现 / 未定论

- 厂商删战位时还会删 i18n 自动名（`deleteAutoNameMessage("role", "en_US"/"fr_FR", key)`）。本环境 `g_lang`
  无覆盖数据，该调用是 no-op；**待切片 5（国际化）回补**。
- `DELETE /v1/role` 是否需要会话：厂商该端点不调 `getCurrentUser`，实测匿名可达；我们同样不加应用级校验
  （与 `/v1/role/{roleId}/software` 要会话形成对比）。
- 前端在什么条件下会发这个请求（选中行 + 点删除）尚未实测触发——本轮的证据是**接口级**的，
  页面级 e2e 留给下一次跑 `/usermanage/role` 时顺带覆盖。
