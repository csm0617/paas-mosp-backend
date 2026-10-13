# K2 收尾：`PUT /v1/software/{id}`（修改软件）——软件域读写闭环

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 结果

| 项 | 实测 |
| --- | --- |
| 成功响应 | HTTP 200，**23 键**（与 `GET /v1/software/{id}` 同形的 `SoftwareInfo`），键数一致 |
| 变更生效 | `type` `springboot`→`CS`、`execName` 改名、`icon` 改名，两侧一致 |
| `desc` 传空白 | 两侧都落 **null** |
| 表单**没带** `location` | 两侧都变成 **null**（不是"保持原值"） |
| 不存在 id | HTTP 400 `"id指定软件不存在"`，两侧逐字一致 |

## 2. 三处必须照抄的古怪（其中一条我第一版写错了）

1. **`BeanUtils.copyProperties` 连 null 一起拷** ⇒ 表单没带的字段会被**置成 null**（含 `routerKey`）。
   实测确认：我只传了 key/name/type/framework/execName/desc/icon，结果两侧的 `location` 都变成 null。
2. **`desc` 空白在两条路径上落库值相反**：
   - **新增**：`softwareDescLimit` 先 `sf.setDesc(null)`，但**返回原值**，调用方又 `setDesc(desc)` 覆盖 ⇒ 落库是**空串**；
   - **修改**：同一个 helper 的返回值被**丢弃** ⇒ 落库是 **null**。
3. **name 与别人重复时，用的 i18n 键和 key 重复时是同一个**：`software.key.identifier.existed`
   （controller 两个分支写了同一个键）。照抄，并在用例里钉住。

### 我第一版写错的那条

我在实现前**读出了**"修改时空白 desc → null"，也把它写进了 javadoc，**但代码里写的是 `.desc(form.getDesc())`**
——注释说 null、代码给空串。是对拉把它照出来的（vendor `None` vs mine `''`）。
**记录教训：注释里写对了不等于代码写对了；凡是"读代码得到的结论"，最后必须有一条实测或断言把它钉住。**

## 3. 未实现（记录）

- CS 类型改 `execName` 后的 WebSocket 广播（`convertAndSend("/topic/software", ...)`）；
- BS 类型改 key 后对已关联服务做 `unRelateServices`/`relateServices`（要 K8s 现场态与 `g_software_app` 映射）。
  我们不做这一步，所以 BS 软件改 key 后**不会**重写 `router_key`（厂商会）。
- i18n 自动名（`saveAutoNameMessage`）本环境是 no-op；响应的 `nameUs`/`nameFr` 按"表单值非空则用之，否则回退 name"实现，与厂商在本环境的表现一致。

## 4. 工具层教训：同一个坑第三次踩

我又用 `python3 - <<'PY'` 里嵌 `"""` 的方式批量改文件 —— **解析直接报错，整段补丁静默不执行**。
更糟的是前面几行 `print("patched")` 让我误以为成功；是编译错误（接口有方法、实现类没有）才暴露。
**改成用 edit 工具逐处改，并且每步用 `grep -c` 验证是否真的落地**（本轮的 `grep -c` 当场查出 4 个补丁全都没落地）。
这是同一个坑的第三次；写进纪律：**批量改代码不用会吞异常的脚本，改完必须逐项验证。**

## 5. 测试

新增 4 条域测试（空白 desc 两路径相反、表单没带字段被置 null、key/name 重复同一个 i18n 键、不存在 id），
1 条用例测试（更新后返回详情且 `nameUs` 取自表单）。全量 `mvn test` **162 条全绿**（上一轮 157）。
探针软件/关联全部删除，库回到 `software=0 / role_software=0 / upload_store_record=0 / user=1 / role=2`。

## 6. 软件域现在的状态

| 端点 | 状态 |
| --- | --- |
| `GET /v1/software`（列表） | ✅ 全分支对拉一致 |
| `POST /v1/software`（新增） | ✅ 23 键 + 3 条错误路径一致 |
| `GET /v1/software/{id}`（详情） | ✅ 23 键一致（含 `licence.code`） |
| `PUT /v1/software/{id}`（修改） | ✅ 23 键 + 空白 desc + 缺字段置 null 一致 |
| `DELETE /v1/software/{id}`（删除） | ✅ 响应与错误一致，四表顺序删除 |
| `POST /v1/software/release`（放行/禁止） | ✅ 四种情形一致 |

**软件这个域的 CRUD + 发布态已经闭环。** 剩下未做的是与其它域耦合的部分：
`/auth` 授权列表、CS 上传（要文件/远端 SSH）、导入导出（要文件）、详情里 BS 框架的 `serviceList`（要 K8s 现场态）。
