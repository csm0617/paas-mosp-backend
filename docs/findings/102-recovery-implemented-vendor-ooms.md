# `POST /v1/user/recovery` 实现完成；但**厂商侧这两条路径都拿不到有效答案**（其中一条是 OOM）

**证据等级**：〔运行实测〕（对拉尝试 + 库内状态核对）+〔代码事实〕。2026-10-14。

## 1. 我们的实现（照抄"空实现"）

厂商 `handleRecoveryExcel`（`UserService.java:947-948`）**方法体为空** ⇒ 该端点只做 Excel 格式校验，然后 `Response.ok(null)`。
我们照抄：MIME 探测（Tika，接受 4 种类型，见 `ValidateUtils.isExcelFile`）→ 非 Excel 抛
`global.import.supported.file.type` → 通过则返回 `null`，**不写任何数据**。

## 2. 实测结果（两条路径都对不上，原因各不相同）

| 用例 | vendor | mine | 说明 |
| --- | --- | --- | --- |
| A：传**非 Excel** 文本文件 | **500 系统错误** | 400「只支持xls,xlsx文件的导入」 | 厂商**没有**走它自己写的那条 400 分支 |
| B：传**合法 xls**（用上一轮下载的厂商模板） | **500**：`OutOfMemoryError: Metaspace`（栈里是 `NestedServletException`） | 200 `{code:0,data:null,msg:OK}` | **厂商进程本身挂了**（Metaspace 耗尽）——这不是契约答案 |

**库内核对**：调用前后 `g_user` 都是 **1 行** ⇒ 我们这条路径**确实没有写库**（与"空实现"一致）✅。

## 3. 怎么看待这两条"对不上"

- **B 是环境故障**：`OutOfMemoryError: Metaspace` 是厂商 JVM 的问题（该 pod 内存限制很小），
  与契约无关 ⇒ **该用例当前无法对拉**，需要等厂商进程恢复后重跑；
- **A 也不是"值得追的差异"——它同样是 OOM**（**第 102 轮更正**）：重跑并打印完整响应体后看到
  `NestedServletException: … java.lang.OutOfMemoryError: Metaspace`。
  ⇒ **两条用例都是厂商 JVM 故障**，没有任何契约结论可下。我上一版写的"400 分支没走到、大概率是 Tika 抛错"
  **是错的**——真因是**进程已经坏了**，根本走不到业务代码。

  **厂商 pod 的资源限制**（实测）：`limits.memory=640Mi`、`JAVA_TOOL_OPTIONS=-Xmx320m -XX:MaxMetaspaceSize=128m`。
  **Metaspace 只有 128m**，而 POI/Tika 这类库会大量加载类 ⇒ 这很可能是**被我这两次文件类请求打爆的**
  （时间点吻合：模板端点用 POI、恢复端点用 Tika，之后就开始 500）。

  ⇒ **结论（环境限制，A 类）**：在这个 pod 规格下，**厂商的文件类端点无法可靠对拉**——
  一调用就可能把它的 Metaspace 打满。要么给厂商 pod 调大 Metaspace 再验，要么这些端点只做"代码路径级"实现 + 离线单测。

**不要**因为"厂商 500 而我们 400"就改我们的实现——那会把"厂商的 bug"抄成"我们的 bug"。
正确做法是**先查清厂商 500 的原因**，再决定我们是否要复现它。

## 4. 状态

端点映射 **63 → 64**；测试 **41 类 294 条全绿**；临时 NodePort 已删、进程清零、工作树干净。
