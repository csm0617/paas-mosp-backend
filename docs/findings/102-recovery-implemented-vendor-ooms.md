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
- **A 值得追**：厂商代码里明明写着"非 Excel → 400 `global.import.supported.file.type`"，实际却 500
  ⇒ 大概率是 `ValidateUtils.isExcelFile` 内部**先抛了别的异常**（例如 Tika 在该输入上抛错，
  或 `MediaType.application("x-tika-msoffice")` 构造异常），于是 400 分支根本没走到。
  **下一轮可查**：厂商日志里这条请求的异常栈（`kubectl logs`）。

**不要**因为"厂商 500 而我们 400"就改我们的实现——那会把"厂商的 bug"抄成"我们的 bug"。
正确做法是**先查清厂商 500 的原因**，再决定我们是否要复现它。

## 4. 状态

端点映射 **63 → 64**；测试 **41 类 294 条全绿**；临时 NodePort 已删、进程清零、工作树干净。
